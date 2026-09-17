package org.goplanit.matsim.util;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.goplanit.utils.graph.directed.DirectedVertex;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.zoning.connectoid.DirectedConnectoidAccessZoneEntry;
import org.goplanit.utils.zoning.connectoid.TransferConnectoid;
import org.goplanit.utils.zoning.connectoid.TransferConnectoids;
import org.goplanit.utils.zoning.connectoid.ZoneConnectoidType;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Helper class to generate and track mapping between PLANit and MATSim stop facilities
 */
public class MatsimStopFacilityIdHelper {

  private static final Logger LOGGER = Logger.getLogger(MatsimStopFacilityIdHelper.class.getCanonicalName());

  /**  key: node, edgesegment, mode --> value id : note it is expected that for pt stops the mode
   * is always explicitly specified, zone does not matter because it is about the stop location if it allows access to
   * multiple stops from that link then so be it, we do not need to know for MATSim */
  private final AtomicLong idGenerator = new AtomicLong(0);
  private final MultiKeyMap<?, Long> stopFacilityIdTracking = new MultiKeyMap<>();

  /**
   * For each access segment we need its own stop facility id since matsim does not allow stops at nodes but always on
   * links
   *
   * @param entry planit stop location entry with one or more access segments
   */
  private void generateStopFacilityIds(TransferConnectoid connectoid, DirectedConnectoidAccessZoneEntry entry){
    var allowedModes = entry.getExplicitlyAllowedModes();
    for(var mode : allowedModes) {
      entry.getAccessLinkSegments().forEach(accessSegment -> {
        if(!hasStopFacilityId(connectoid.getReferenceVertex(), accessSegment, mode)){
          stopFacilityIdTracking.put(new MultiKey(connectoid.getReferenceVertex(), accessSegment, mode),
              idGenerator.getAndIncrement());
        }
      });
    }
  }

  /**
   * Constructor
   *
   * @param transferConnectoids to base stop facilities on
   */
  public MatsimStopFacilityIdHelper(TransferConnectoids transferConnectoids){

      transferConnectoids.streamSortedBy(TransferConnectoid::getId).forEach( transferConnectoid -> {
        transferConnectoid.getAccessZoneEntriesStream(ZoneConnectoidType.PT_VEHICLE_STOP).forEach(ae -> {

          if(!ae.hasExplicitlyAllowedModes()){
            LOGGER.warning(String.format("Expected explicitly listed mode for PLANit pt stop location, but found none for" +
                " connectoid (%s), skip and verify correctness", transferConnectoid.getIdsAsString()));
            return;
          }

          /* ID:
           * We map to tracked stop facility id based on link segment+node location. We can't use connectoid ids
           * because multiple connectoids might map to the same access link segment. We also cannot use transfer
           * zone ids because there, the same id might access multiple stop facilities (connectoids).
           * We also cannot use a service network node because either we might not have those (in case we are
           * persisting without services), or if we do, then we can have multiple incoming link segments leading
           * to a non-unique mapping to the underlying physical network which is required in a MATSim context.
           * The only option is to use generate them on-the-fly uniquely and track a mapping so we can
           * relate them back to the combination transfer zone - connectoid access entry
           */
          // (in theory more than one per entry as MATSim is per link and entry is on node with potentially multiple
          // entry links and/or modes
          generateStopFacilityIds(transferConnectoid, (DirectedConnectoidAccessZoneEntry) ae);
        });
      });
  }

  /** check for presence of MATSim stop facility id we created
   *
   * @param node to check
   * @param accessLinkSegment to check
   * @param mode to check
   * @return true if present
   */
  public boolean hasStopFacilityId(DirectedVertex node, EdgeSegment accessLinkSegment, Mode mode){
    return stopFacilityIdTracking.containsKey(node, accessLinkSegment, mode);
  }

  /** obtain MATSim stop facility id we created, assuming it exists
   *
   * @param node to check
   * @param accessLinkSegment to check
   * @param mode to check
   * @return the id
   */
  public long getStopFacilityId(DirectedVertex node, EdgeSegment accessLinkSegment, Mode mode){
    return stopFacilityIdTracking.get(node, accessLinkSegment, mode);
  }

}
