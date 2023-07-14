package org.goplanit.matsim.converter;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.goplanit.matsim.xml.MatsimTransitAttributes;
import org.goplanit.utils.misc.CharacterUtils;
import org.goplanit.zoning.Zoning;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;

/**
 * Class that takes on the responsibility of writing all PT MAtrix based routing files for a given PLANit Zoning writer.
 * Since a MATSim matrix based run requires explicit travel times between all stops. We create shortest paths between all stops in the network in the prespecified CSV format.
 * <p>
 * Currently these costs are based on free-flow travel times multiplied by a given factor (optional)
 * <p>
 * More information on the MATSim matrix based router can be found <a href="https://github.com/matsim-org/matsim-libs/tree/master/contribs/matrixbasedptrouter">here</a>.
 * <p>
 * The naming for the generated files is predetermined at:
 * <ul>
 * <li>ptStops.csv</li>
 * <li>ptTravelInfo.csv</li>
 * </ul>
 * Where the former contains the stop information while the latter contains the travel times (in seconds). 
 * <p>
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
  
  /**
   * The stops CSV file contains the stop id and its coordinates, based on example in <a href="https://github.com/matsim-org/matsim-libs/blob/master/contribs/matrixbasedptrouter/src/main/resources/example/ptStops.csv">ptstops.csv</a>
   * @param zoning to use
   */
  private void writeStopsCsvFile(Zoning zoning) {
    var sb = new StringBuilder();
    var dm = zoningWriter.getNetworkWriterSettings().getDecimalFormat();
    
    /* content */
    var csvContent = new ArrayList<String>();
    
    /* header */
    sb.append(MatsimTransitAttributes.ID).append(CharacterUtils.COMMA).append(MatsimTransitAttributes.X).append(CharacterUtils.COMMA).append(MatsimTransitAttributes.Y);
    csvContent.add(sb.toString());
    
    try {
      for(var transferConnectoid : zoning.getTransferConnectoids()) {
        sb.delete(0, Integer.MAX_VALUE);
        var coord = zoningWriter.extractDestinationCrsCompatibleCoordinate(transferConnectoid.getAccessNode().getPosition());
        sb.append(transferConnectoid.getXmlId()).append(CharacterUtils.COMMA).append(dm.format(coord.x)).append(CharacterUtils.COMMA).append(dm.format(coord.y));
        csvContent.add(sb.toString());
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
    LOGGER.info(String.format("Persisting MATSIM %s to: %s",PT_STOPS_FILE_NAME, ptStopsFilePath.toString()));
    try (PrintWriter pw = new PrintWriter(ptStopsFilePath.toFile())) {
      csvContent.stream().forEach(pw::println);
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Unable to persist %s file in output dir %s, aborting",PT_STOPS_FILE_NAME, zoningWriter.getSettings().getOutputDirectory()));
    }
  }
  
  /** file name for the stops CSV */
  public static String PT_STOPS_FILE_NAME = "ptStops.csv";

  /** file name for the travel time matrix (seconds) CSV */
  public static String PT_TRAVEL_INFO_FILE_NAME = "ptTravelInfo.csv";

  /**
   * Constructor 
   * 
   * @param zoningWriter to use
   */
  public MatsimPtMatrixBasedRouterWriter(final MatsimZoningWriter zoningWriter) {
    this.zoningWriter = zoningWriter;
  }

  /**
   * Write the files to support Matrix based pt routing
   * 
   * @param zoning to use
   */
  public void write(final Zoning zoning) {

    /* For now, we only generate a stops file. From the MATSim code it appears, the stop-stop travel time matrix can be created on the fly within MATSim */
    writeStopsCsvFile(zoning);
  }  
}
