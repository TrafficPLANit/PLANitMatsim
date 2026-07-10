package org.goplanit.matsim.converter.demand;

import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
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

import java.util.*;
import java.util.logging.Logger;

/**
 * Utility class to handle spatial location generation and length-weighted link sampling
 * for MATSim activity locations based on PLANit zoning and network layers.
 * * @author markr
 */
public class LocationGeneratorUtils {

  private static final Logger LOGGER = Logger.getLogger(LocationGeneratorUtils.class.getCanonicalName());

  /**
   * Pre-index a mapping from Zone to an array of intersecting Link Segments, using your PlanitJtsCrsUtils
   * to extract precise real-world metric lengths for clipped boundary intersections.
   *
   * @param network reference network containing links
   * @param odZones reference zoning containing zone geometries
   * @param crsUtils utility instance matching the native geometry coordinate space
   * @return A map where the key is the Zone ID, and the value is the calculated ZoneLinkWeights
   */
  public static Map<Long, ZoneLinkWeights> populateZoneLinkWeightsIndex(
      MacroscopicNetwork network, OdZones odZones, PlanitJtsCrsUtils crsUtils) {

    Map<Long, ZoneLinkWeights> zoneWeightsMap = new HashMap<>();

    // safeguard
    if(network.getTransportLayers().size()!=1){
      throw new PlanItRunTimeException("Currently zone link weights only support a single network layer, abort");
    }
    var layer = network.getTransportLayers().getFirst();

    for (var zone : odZones) {
      var zoneGeom = zone.getGeometry();
      if (zoneGeom == null) {
        LOGGER.warning(String.format("Zone (%s) is missing geometry structure. Skipping weight index.",
            zone.getIdsAsString()));
        continue;
      }

      List<MacroscopicLinkSegment> intersectingSegments = new ArrayList<>();
      List<Double> cumulativeLengths = new ArrayList<>();
      double runningTotalLength = 0.0;

      for (MacroscopicLink link : layer.getLinks()) {
        var linkGeom = link.getGeometry();
        if (linkGeom == null) {
          continue;
        }

        // Fast topological intersection evaluate check in native coordinates
        if (zoneGeom.intersects(linkGeom)) {
          double lengthInsideZoneKm;

          if (zoneGeom.contains(linkGeom)) {
            // Fully enclosed - use the master link attribute directly without calculation
            lengthInsideZoneKm = link.getLengthKm();
          } else {
            // Cut across boundary - extract the intersection fragment geometry
            Geometry internalIntersection = zoneGeom.intersection(linkGeom);

            if (internalIntersection != null && !internalIntersection.isEmpty()) {
              lengthInsideZoneKm = 0.0;

              // Handle standard LineStrings
              if (internalIntersection instanceof LineString) {
                lengthInsideZoneKm = crsUtils.getDistanceInKilometres((LineString) internalIntersection);
              }
              // Handle fragmented MultiLineStrings resulting from complex boundary cuts
              else if (internalIntersection instanceof MultiLineString) {
                MultiLineString mls = (MultiLineString) internalIntersection;
                for (int i = 0; i < mls.getNumGeometries(); i++) {
                  lengthInsideZoneKm += crsUtils.getDistanceInKilometres((LineString) mls.getGeometryN(i));
                }
              }
            } else {
              lengthInsideZoneKm = 0.0;
            }
          }

          // Buffer slightly against minor precision noise
          if (lengthInsideZoneKm > 0.0001) {
            for (var segment : link.getLinkSegments()) {
              if (segment != null) {
                runningTotalLength += lengthInsideZoneKm;
                intersectingSegments.add(segment);
                cumulativeLengths.add(runningTotalLength);
              }
            }
          }
        }
      }

      if (!intersectingSegments.isEmpty()) {
        zoneWeightsMap.put(
            zone.getId(), new ZoneLinkWeights(intersectingSegments, cumulativeLengths, runningTotalLength));
      }
    }

    return zoneWeightsMap;
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
