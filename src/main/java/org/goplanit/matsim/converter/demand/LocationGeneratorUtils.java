package org.goplanit.matsim.converter.demand;

import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.id.ExternalIdAble;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
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
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utility class to handle spatial location generation and length-weighted link sampling
 * for MATSim activity locations based on PLANit zoning and network layers.
 * * @author markr
 */
public class LocationGeneratorUtils {

  private static final Logger LOGGER = Logger.getLogger(LocationGeneratorUtils.class.getCanonicalName());

  /** number of zones named when reporting a group of them, enough to go and look at without flooding the log */
  private static final int MAX_REPORTED_ZONES = 10;

  /**
   * Pre-index a mapping from zone to the length weighted link segments within it supporting each of the given modes.
   * <p>
   * Indexed across all modes in a single pass over the zones. The costly part is geometric, clipping each candidate
   * link to the zone to establish its length inside it, and that outcome is the same whatever mode is asked for. Only
   * segment eligibility differs per mode, so the geometry is done once per zone and the modes are separated out from
   * the result.
   * </p>
   *
   * @param modes to index for, any mode unsupported by the layer is omitted from the result
   * @param network reference network containing links
   * @param odZones reference zoning containing zone geometries
   * @param crsUtils utility instance matching the native geometry coordinate space
   * @return per mode a map of zone id to its weights, zones without a mode compatible link being absent
   */
  public static Map<Mode, Map<Long, ZoneLinkWeights>> populateZoneLinkWeightsIndex(
      Collection<Mode> modes, MacroscopicNetwork network, OdZones odZones, PlanitJtsCrsUtils crsUtils) {

    if (network.getTransportLayers().size() != 1) {
      throw new PlanItRunTimeException("Currently zone link weights only support a single network layer, abort");
    }
    var layer = network.getTransportLayers().getFirst();

    var eligibleModes = new ArrayList<Mode>(modes.size());
    for (var mode : modes) {
      if (layer.supports(mode)) {
        eligibleModes.add(mode);
      } else {
        LOGGER.warning(String.format("Unable to construct zone link weights because mode (%s) is not supported " +
            "on layer (%s)", mode.getIdsAsString(), layer.getIdsAsString()));
      }
    }

    var indexByMode = new HashMap<Mode, Map<Long, ZoneLinkWeights>>();
    var noModeCompatibleLinkZones = new HashMap<Mode, List<Zone>>();
    for (var mode : eligibleModes) {
      indexByMode.put(mode, new HashMap<>());
      noModeCompatibleLinkZones.put(mode, new ArrayList<>());
    }

    //Build the Spatial Index (R-Tree) over the links
    var linkSpatialIndex = layer.getLinks().createSpatialIndex();
    // Process Zones using Spatial Filtering
    PreparedGeometryFactory prepFactory = new PreparedGeometryFactory();

    /* the area the network actually covers. A zone beyond it has no links because the network stops there, which is
     * a choice of modelled area rather than anything to report, so such a zone is left out of what follows entirely
     * rather than counted against every mode it unsurprisingly lacks */
    var prepNetworkExtent = prepFactory.create(PlanitGraphGeoUtils.createConvexHull(layer.getNodes()));

    var noSpatialCandidateLinkZones = new ArrayList<Zone>();
    int beyondNetworkExtentZones = 0;
    for (var zone : odZones) {
      var zoneGeom = zone.getGeometry();
      if (zoneGeom == null) {
        LOGGER.warning(String.format("Zone (%s) is missing geometry structure. Skipping weight index.",
            zone.getIdsAsString()));
        continue;
      }
      if (!prepNetworkExtent.intersects(zoneGeom)) {
        ++beyondNetworkExtentZones;
        continue;
      }

      // prep for fast boolean lookups
      PreparedGeometry prepZoneGeom = prepFactory.create(zoneGeom);

      // Fast Bounding-Box Query: Returns only candidate links intersecting the Zone's envelope
      @SuppressWarnings("unchecked")
      List<MacroscopicLink> candidateLinks = linkSpatialIndex.query(zoneGeom.getEnvelopeInternal());
      if (candidateLinks.isEmpty()) {
        noSpatialCandidateLinkZones.add(zone);
        continue;
      }

      /* what each candidate link contributes to this zone regardless of mode, so the geometry below is only done
       * once however many modes are indexed */
      List<ZoneLinkPortion> zoneLinkPortions = new ArrayList<>();

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

            /* make sure the downstream end of the segment also falls in the zone, that being where an activity
             * drawing this segment is placed, since MATSim locates an agent performing an activity at the end of its
             * link. Mode compatibility is left to the per mode pass below, it being the only thing that varies */
            var segmentAb = link.hasLinkSegmentAb() && prepZoneGeom.contains(link.getVertexB().getPosition())
                ? link.getLinkSegmentAb() : null;
            var segmentBa = link.hasLinkSegmentBa() && prepZoneGeom.contains(link.getVertexA().getPosition())
                ? link.getLinkSegmentBa() : null;
            if (segmentAb != null || segmentBa != null) {
              zoneLinkPortions.add(new ZoneLinkPortion(segmentAb, segmentBa, lengthInsideZoneKm));
            }
          }
        }
      }

      /* separate out the modes, the portions above holding everything that does not depend on one */
      for (var mode : eligibleModes) {
        List<MacroscopicLinkSegment> intersectingSegments = new ArrayList<>();
        List<Double> segmentLengthsKm = new ArrayList<>();

        for (var portion : zoneLinkPortions) {
          for (var segment : portion.getSegmentsAllowing(mode)) {
            intersectingSegments.add(segment);
            segmentLengthsKm.add(portion.getLengthInZoneKm());
          }
        }

        if (!intersectingSegments.isEmpty()) {
          indexByMode.get(mode).put(zone.getId(), ZoneLinkWeights.of(intersectingSegments, segmentLengthsKm));
        } else {
          noModeCompatibleLinkZones.get(mode).add(zone);
        }
      }
    }

    if (beyondNetworkExtentZones > 0) {
      LOGGER.info(String.format(
          "Excluded %d zones falling beyond the network extent, being empty because the modelled area ends there",
          beyondNetworkExtentZones));
    }
    if (!noSpatialCandidateLinkZones.isEmpty()) {
      LOGGER.warning(String.format(
          "Found %d zones within the network extent yet without any spatial link coverage for any mode: %s",
          noSpatialCandidateLinkZones.size(), listFirstZones(noSpatialCandidateLinkZones)));
    }
    /* reported here, once and naming the modes, rather than per activity that later falls back on the zone centroid:
     * with no link at all to draw there is nothing the writer could have done differently. Modes that fail in exactly
     * the same zones are reported together, a zone without a road link failing every road mode identically and there
     * being nothing to learn from seeing that repeated */
    var modesByFailedZones = new LinkedHashMap<List<Zone>, List<Mode>>();
    for (var mode : eligibleModes) {
      var zones = noModeCompatibleLinkZones.get(mode);
      if (!zones.isEmpty()) {
        modesByFailedZones.computeIfAbsent(zones, z -> new ArrayList<>()).add(mode);
      }
    }

    /* lacking a mode's own links is only half the story: a zone that can still be walked in remains reachable, the
     * walk network being how anyone gets to what their own mode does not provide. Lacking both is what leaves a zone
     * with nothing to place on or reach from, so that is the share worth singling out */
    var pedestrianMode = eligibleModes.stream().filter(
        m -> m.getPredefinedModeType() == PredefinedModeType.PEDESTRIAN).findFirst();
    var noWalkLinkZones = pedestrianMode.isPresent()
        ? new HashSet<>(noModeCompatibleLinkZones.get(pedestrianMode.get())) : Collections.<Zone>emptySet();

    modesByFailedZones.forEach((zones, modesWithoutLinks) -> {
      var message = new StringBuilder(String.format(
          "Found %d zones within the network extent without any link supporting mode(s) (%s): %s",
          zones.size(),
          modesWithoutLinks.stream().map(Mode::getName).collect(Collectors.joining(",")),
          listFirstZones(zones)));
      if (!noWalkLinkZones.isEmpty() && !modesWithoutLinks.contains(pedestrianMode.get())) {
        message.append(String.format(
            " - %d/%d potentially problematic due to also lacking walk links as access mode",
            zones.stream().filter(noWalkLinkZones::contains).count(), zones.size()));
      }
      LOGGER.warning(message.toString());
    });

    return indexByMode;
  }

  /**
   * List the first handful of zones by their ids as (..),(..)
   *
   * @param zones to list
   * @return the listing, trailing with ... when not all zones are named
   */
  private static String listFirstZones(List<Zone> zones) {
    var listing = zones.stream().limit(MAX_REPORTED_ZONES).map(
        z -> "(" + z.getIdsAsString() + ")").collect(Collectors.joining(","));
    return zones.size() > MAX_REPORTED_ZONES ? listing + ",..." : listing;
  }

  /**
   * The part of a link that falls within a zone, holding what is the same for every mode so that the geometry behind
   * it is only established once. A segment is held only when it is the one an activity would be placed on, meaning
   * its downstream vertex falls within the zone.
   */
  private static class ZoneLinkPortion {
    private final MacroscopicLinkSegment segmentAb;
    private final MacroscopicLinkSegment segmentBa;
    private final double lengthInZoneKm;

    private ZoneLinkPortion(
        MacroscopicLinkSegment segmentAb, MacroscopicLinkSegment segmentBa, double lengthInZoneKm) {
      this.segmentAb = segmentAb;
      this.segmentBa = segmentBa;
      this.lengthInZoneKm = lengthInZoneKm;
    }

    /**
     * The segments of this portion that allow the given mode
     *
     * @param mode to allow
     * @return those segments, in a_b then b_a order
     */
    private List<MacroscopicLinkSegment> getSegmentsAllowing(Mode mode) {
      var allowed = new ArrayList<MacroscopicLinkSegment>(2);
      if (segmentAb != null && segmentAb.isModeAllowed(mode)) {
        allowed.add(segmentAb);
      }
      if (segmentBa != null && segmentBa.isModeAllowed(mode)) {
        allowed.add(segmentBa);
      }
      return allowed;
    }

    private double getLengthInZoneKm() {
      return lengthInZoneKm;
    }
  }

  /**
   * Helper class to store length weights per zone
   */
  public static class ZoneLinkWeights {
    private final List<MacroscopicLinkSegment> segments;
    private final List<Double> cumulativeLengths;
    private final double totalLength;

    private ZoneLinkWeights(
        List<MacroscopicLinkSegment> segments, List<Double> cumulativeLengths, double totalLength) {
      this.segments = segments;
      this.cumulativeLengths = cumulativeLengths;
      this.totalLength = totalLength;
    }

    /**
     * Create weights from the segments and the length each is to weigh with, cumulating them for the draw. The single
     * place that cumulation happens, so that a draw behaves the same however the weights were arrived at.
     *
     * @param segments to draw from
     * @param segmentLengthsKm the length each segment weighs with, matching the segments in order
     * @return the created weights
     */
    public static ZoneLinkWeights of(List<MacroscopicLinkSegment> segments, List<Double> segmentLengthsKm) {
      var cumulativeLengths = new ArrayList<Double>(segmentLengthsKm.size());
      double runningTotalLength = 0.0;
      for (var segmentLengthKm : segmentLengthsKm) {
        runningTotalLength += segmentLengthKm;
        cumulativeLengths.add(runningTotalLength);
      }
      return new ZoneLinkWeights(segments, cumulativeLengths, runningTotalLength);
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

    /**
     * Create weights over only those segments that pass the given filter, each keeping the length it weighs with
     * here. Retains this instance's ordering so that a draw from the result is as reproducible as one from this.
     *
     * @param filter the segments must pass
     * @return the filtered weights, null when no segment passes
     */
    public ZoneLinkWeights createFilteredCopy(Predicate<MacroscopicLinkSegment> filter) {
      var filteredSegments = new ArrayList<MacroscopicLinkSegment>();
      var filteredSegmentLengthsKm = new ArrayList<Double>();
      double precedingLength = 0.0;

      for (int index = 0; index < segments.size(); ++index) {
        /* the length of a segment is what it added to the running total when these weights were created */
        double segmentLengthKm = cumulativeLengths.get(index) - precedingLength;
        precedingLength = cumulativeLengths.get(index);

        var segment = segments.get(index);
        if (filter.test(segment)) {
          filteredSegments.add(segment);
          filteredSegmentLengthsKm.add(segmentLengthKm);
        }
      }

      return filteredSegments.isEmpty() ? null : of(filteredSegments, filteredSegmentLengthsKm);
    }
  }
}
