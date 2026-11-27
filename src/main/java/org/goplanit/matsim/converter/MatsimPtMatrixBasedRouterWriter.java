package org.goplanit.matsim.converter;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.geotools.api.geometry.MismatchedDimensionException;
import org.goplanit.matsim.util.MatsimStopFacilityIdHelper;
import org.goplanit.matsim.xml.MatsimAttributes;
import org.goplanit.matsim.xml.MatsimTransitAttributes;
import org.goplanit.utils.misc.CharacterUtils;
import org.goplanit.utils.zoning.connectoid.DirectedConnectoidAccessZoneEntry;
import org.goplanit.utils.zoning.connectoid.ZoneConnectoidType;
import org.goplanit.zoning.Zoning;

/**
 * Writes additional files for simplified PT modelling in MATSim, in the output directory of the zoning writer:
 * <ul>
 *   <li>full teleportation through the MatrixBasedPtRouter contrib, the lightweight option</li>
 *   <li>partial teleportation through the ptMatrix support of MATSim core, replacing only the pt vehicle
 *   simulation</li>
 * </ul>
 * <p>
 *   For the MatrixBasedPtRouter this writes a simplified stop facilities csv, {@code ptStops.csv}. Access/egress need
 *   not be correct or even present, as the entire pt journey is teleported, currently based on a free flow times a
 *   factor matrix that MATSim generates. See the matrix based router's
 *   <a href="https://github.com/matsim-org/matsim-libs/tree/master/contribs/matrixbasedptrouter">page</a>.
 * </p>
 * <p>
 *   Partial teleportation, the better option, uses the full stop facilities in XML that the PLANit MATSim pt writer
 *   writes as usual, plus a csv "matrix" of travel time and distance between stops so stop to stop legs can be
 *   teleported.
 *   That matrix is not yet written here, and this setup does not use {@code ptStops.csv}.
 * </p>
 *
 * @author markr
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
    var dm = zoningWriter.getSettings().getDecimalFormat();

    /* content */
    var csvContent = new ArrayList<String>();

    /* header id, x, y */
    sb.append(MatsimAttributes.ID).append(CharacterUtils.COMMA).append(
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
    LOGGER.info(String.format("Persisting MATSim %s to: %s",PT_STOPS_FILE_NAME, ptStopsFilePath));
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