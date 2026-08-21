package org.goplanit.matsim.converter.demand;

import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.id.ExternalIdAble;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.zoning.OdZones;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utility class to handle spatial location generation and length-weighted link sampling
 * for MATSim activity locations based on PLANit zoning and network layers.
 * * @author markr
 */
public class LocationGeneratorUtils {

  private static final Logger LOGGER = Logger.getLogger(LocationGeneratorUtils.class.getCanonicalName());

  /**
   * Pre-index a mapping from Zone to an array of intersecting Link Segments that support the provided mode,
   * using PlanitJtsCrsUtils to extract metric lengths considering any clipped boundary intersections.
   *
   * @param mode to filter by such that we only consider link segments that are mode compatible
   * @param network reference network containing links
   * @param odZones reference zoning containing zone geometries
   * @param crsUtils utility instance matching the native geometry coordinate space
   * @return A map where the key is the Zone ID, and the value is the calculated ZoneLinkWeights
   */
  public static Map<Long, ZoneLinkWeights> populateZoneLinkWeightsIndex(
      Mode mode, MacroscopicNetwork network, OdZones odZones, PlanitJtsCrsUtils crsUtils) {

    if (network.getTransportLayers().size() != 1) {
      throw new PlanItRunTimeException("Currently zone link weights only support a single network layer, abort");
    }
    var zoneWeightsIndex = new HashMap<Long, ZoneLinkWeights>();
    var layer = network.getTransportLayers().getFirst();
    if(!layer.supports(mode)){
      LOGGER.warning(String.format("Unable to construct zone link weights because mode (%s) is not supported " +
              "on layer (%s)", mode.getIdsAsString(), layer.getIdsAsString() ));
      return null;
    }

    //Build the Spatial Index (R-Tree) over the links
    var linkSpatialIndex = layer.getLinks().createSpatialIndex();
    // Process Zones using Spatial Filtering
    PreparedGeometryFactory prepFactory = new PreparedGeometryFactory();
    var noSpatialCandidateLinkZones = new ArrayList<Zone>(odZones.size());
    var noModeSpecificCandidateLinkZones = new ArrayList<Zone>(odZones.size());
    for (var zone : odZones) {
      var zoneGeom = zone.getGeometry();
      if (zoneGeom == null) {
        LOGGER.warning(String.format("Zone (%s) is missing geometry structure. Skipping weight index.",
            zone.getIdsAsString()));
        continue;
      }

      // prep for fast boolean lookups
      PreparedGeometry prepZoneGeom = prepFactory.create(zoneGeom);

      // Fast Bounding-Box Query: Returns only candidate links intersecting the Zone's envelope
      @SuppressWarnings("unchecked")
      List<MacroscopicLink> candidateLinks = linkSpatialIndex.query(zoneGeom.getEnvelopeInternal());
      if(candidateLinks.isEmpty()){
        noSpatialCandidateLinkZones.add(zone);
        continue;
      }

      List<MacroscopicLinkSegment> intersectingSegments = new ArrayList<>();
      List<Double> cumulativeLengths = new ArrayList<>();
      double runningTotalLength = 0.0;

      for (MacroscopicLink link : candidateLinks) {
        var linkGeom = link.getGeometry(); // Guaranteed non-null from step 2

        // Exact topological intersection check
        if (prepZoneGeom.intersects(linkGeom)) {
          double lengthInsideZoneKm;

          // use in full when contained, otherwise portion within
          if (prepZoneGeom.contains(linkGeom)) {
            lengthInsideZoneKm = link.getLengthKm();
          } else {
            Geometry internalIntersection = zoneGeom.intersection(linkGeom);

            if (internalIntersection != null && !internalIntersection.isEmpty()) {
              lengthInsideZoneKm = 0.0;

              if (internalIntersection instanceof LineString) {
                lengthInsideZoneKm = crsUtils.getDistanceInKilometres((LineString) internalIntersection);
              } else if (internalIntersection instanceof MultiLineString) {
                MultiLineString mls = (MultiLineString) internalIntersection;
                int numGeoms = mls.getNumGeometries();
                for (int i = 0; i < numGeoms; i++) {
                  lengthInsideZoneKm += crsUtils.getDistanceInKilometres((LineString) mls.getGeometryN(i));
                }
              }
            } else {
              lengthInsideZoneKm = 0.0;
            }
          }

          if (lengthInsideZoneKm > 0.0001) {

            // mode compatibility check + make sure start point of segment also falls in zone
            if(link.hasLinkSegmentAb() && link.getLinkSegmentAb().isModeAllowed(mode) &&
                prepZoneGeom.contains(link.getVertexA().getPosition())){
              runningTotalLength += lengthInsideZoneKm;
              intersectingSegments.add(link.getLinkSegmentAb());
              cumulativeLengths.add(runningTotalLength);
            }
            if(link.hasLinkSegmentBa() && link.getLinkSegmentBa().isModeAllowed(mode) &&
                prepZoneGeom.contains(link.getVertexB().getPosition())){
              runningTotalLength += lengthInsideZoneKm;
              intersectingSegments.add(link.getLinkSegmentBa());
              cumulativeLengths.add(runningTotalLength);
            }
          }
        }
      }

      if (!intersectingSegments.isEmpty()) {
        zoneWeightsIndex.put(
            zone.getId(), new ZoneLinkWeights(intersectingSegments, cumulativeLengths, runningTotalLength));
      }else{
        noModeSpecificCandidateLinkZones.add(zone);
      }
    }

    if(!noSpatialCandidateLinkZones.isEmpty()) {
      LOGGER.warning(String.format("Found %d Zones without any spatial link coverage for any mode. ",
          noSpatialCandidateLinkZones.size()));
    }
    if(!noModeSpecificCandidateLinkZones.isEmpty()) {
      LOGGER.warning(String.format("Found Zones (%d) without any mode compatible links. Excluded from weight index.",
          noModeSpecificCandidateLinkZones.size()));
    }

    return zoneWeightsIndex;
  }

  /**
   * Helper class to store length weights per zone
   */
  public static class ZoneLinkWeights {
    private final List<MacroscopicLinkSegment> segments;
    private final List<Double> cumulativeLengths;
    private final double totalLength;

    public ZoneLinkWeights(
        List<MacroscopicLinkSegment> segments, List<Double> cumulativeLengths, double totalLength) {
      this.segments = segments;
      this.cumulativeLengths = cumulativeLengths;
      this.totalLength = totalLength;
    }

    /**
     * Perform a binary search length-weighted random draw to extract an internal link segment
     * using a seed-stable SplittableRandom instance.
     */
    public MacroscopicLinkSegment drawRandomSegment(SplittableRandom random) {
      // nextDouble() returns a pseudorandom double between 0.0 (inclusive) and 1.0 (exclusive)
      double target = random.nextDouble() * totalLength;

      // finds -(desired insertion_point) - 1  where desired insertion_point is the return value
      // it is negative because it most likely is unmatched
      // when exact match, it is positive and finds desired insertion_point directly
      int index = Collections.binarySearch(cumulativeLengths, target);
      if (index < 0) {
        index = -index - 1;
      }
      return segments.get(Math.min(index, segments.size() - 1));
    }
  }
}
