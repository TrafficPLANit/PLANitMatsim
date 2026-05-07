package org.goplanit.matsim.converter;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.geotools.api.geometry.MismatchedDimensionException;
import org.goplanit.matsim.util.MatsimStopFacilityIdHelper;
import org.goplanit.matsim.xml.MatsimTransitAttributes;
import org.goplanit.utils.misc.CharacterUtils;
import org.goplanit.utils.zoning.connectoid.DirectedConnectoidAccessZoneEntry;
import org.goplanit.utils.zoning.connectoid.ZoneConnectoidType;
import org.goplanit.zoning.Zoning;

/**
 * Class that support producing additional files for MATSim that allow for simplified PT modelling, namely:
 * <ul>
 *   <li> lightweight full teleportation through MatrixBasedPtRouter contrib</li>
 *   <li> partial teleportation through ptMatrix core support replacing only pt vehicle simulation
 *   with teleportation</li>
 * </ul>
 *
 * <p>
 *   For the lightweight MatrixBasedPtRouter, we can generate a simplified stop facilities csv: `ptStops.csv`
 *   This does not require correct access/egress options nor those portions being presen, the entire pt journey
 *   is teleported based on (currently assumed by MATSim generated free flow * factor based matrix.
 *   More information on the lightweight MATSim matrix based router can be found
 *   <a href="https://github.com/matsim-org/matsim-libs/tree/master/contribs/matrixbasedptrouter">here</a>.
 * </p>
 * <p>
 *   For the "better" alternative, we relay on a proper stop facility definition in XML form (to be generated through
 *   the normal supporting PlanitMATSim pt writer as per usual. However, to allow for the stop to stop to be teleported
 *   you need to provide a csv based "matrix" for travel time and distance between stops. This can be generated here
 *   (todo: not yet implemented).
 *   NOTE: In this setup the lightweight csv on stop facilities is not used so it does not need to be generated
 * </p>
 * Files are generated in the designated output directory of the zoning writer
 *
 * @author markr
 *
 */
class MatsimPtMatrixBasedRouterWriter {
  
  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimPtMatrixBasedRouterWriter.class.getCanonicalName());
  
  /** the zoning writer used for the MATSim pt component*/
  private final MatsimZoningWriter zoningWriter;

  /** mapping between PLANit and MATSim stop facility ids */
  MatsimStopFacilityIdHelper stopFacilityIdHelper;
  
  /**
   * The stops CSV file contains the stop id and its coordinates, based on example
   * in <a href="https://github.com/matsim-org/matsim-libs/blob/master/contribs/matrixbasedptrouter/src/main/resources/example/ptStops.csv">ptstops.csv</a>
   * @param zoning to use
   */
  private void writeSimplifiedMatrixBasedPtRouterContribStopsCsvFile(Zoning zoning) {
    var sb = new StringBuilder();
    var dm = zoningWriter.getNetworkWriterSettings().getDecimalFormat();
    
    /* content */
    var csvContent = new ArrayList<String>();
    
    /* header id, x, y */
    sb.append(MatsimTransitAttributes.ID).append(CharacterUtils.COMMA).append(
        MatsimTransitAttributes.X).append(CharacterUtils.COMMA).append(MatsimTransitAttributes.Y);
    csvContent.add(sb.toString());
    
    try {
      for(var transferConnectoid : zoning.getTransferConnectoids()) {
        transferConnectoid.getAccessZoneEntriesStream(ZoneConnectoidType.PT_VEHICLE_STOP).forEach( entry ->
        {
          var stopEntry = (DirectedConnectoidAccessZoneEntry)entry;
          for(var mode : stopEntry.getExplicitlyAllowedModes()){
            for(var stopEntryAccessSegment : stopEntry.getAccessLinkSegments()){
              sb.delete(0, Integer.MAX_VALUE);
              // needs a point where the stop location is (unrelated to link)
              var coord = zoningWriter.extractDestinationCrsCompatibleCoordinate(
                  transferConnectoid.getReferenceVertex().getPosition());
              sb.append(stopFacilityIdHelper.getStopFacilityId(
                      transferConnectoid.getReferenceVertex(), stopEntryAccessSegment, mode)).
                  append(CharacterUtils.COMMA).
                  append(dm.format(coord.x)).
                  append(CharacterUtils.COMMA).
                  append(dm.format(coord.y));
              csvContent.add(sb.toString());
            }
          }
        });
      }
    } catch (MismatchedDimensionException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("Unable to transform pt stop locations to desired coordinate format for MATSim aborting");
      return;
    }
    
    if(csvContent.isEmpty()) {
      LOGGER.warning("No stops to persist, verify this is intended behaviour");
      return;
    }
    
    /* write */
    var ptStopsFilePath = Path.of(zoningWriter.getSettings().getOutputDirectory(),PT_STOPS_FILE_NAME);
    LOGGER.info(String.format("Persisting MATSIM %s to: %s",PT_STOPS_FILE_NAME, ptStopsFilePath));
    try (PrintWriter pw = new PrintWriter(ptStopsFilePath.toFile())) {
      csvContent.forEach(pw::println);
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Unable to persist %s file in output dir %s, aborting",
          PT_STOPS_FILE_NAME, zoningWriter.getSettings().getOutputDirectory()));
    }
  }
  
  /** file name for the stops CSV */
  public static String PT_STOPS_FILE_NAME = "ptStops.csv";

  /**
   * Constructor 
   * 
   * @param zoningWriter to use
   */
  public MatsimPtMatrixBasedRouterWriter(
      final MatsimZoningWriter zoningWriter, final MatsimStopFacilityIdHelper stopFacilityIdHelper) {
    this.zoningWriter = zoningWriter;
    this.stopFacilityIdHelper = stopFacilityIdHelper;
  }

  /**
   * Write the files to support Matrix based pt routing
   * 
   * @param zoning to use
   */
  public void write(final Zoning zoning) {

    /* For now, we only generate a stops file. From the MATSim code it appears, the stop-stop travel time
    matrix can be created on the fly within MATSim */
    writeSimplifiedMatrixBasedPtRouterContribStopsCsvFile(zoning);
  }  
}
