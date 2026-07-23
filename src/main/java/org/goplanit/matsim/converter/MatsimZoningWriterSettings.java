package org.goplanit.matsim.converter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.LoggingUtils;

/**
 * Settings specific to writing the zoning related output in MATSim format (pt)
 * 
 * @author markr
 *
 */
public class MatsimZoningWriterSettings extends PlanitMatsimWriterSettings{
  
  /** settings to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimZoningWriterSettings.class.getCanonicalName());

  /**
   * While persisting generate the input files for the MATSim PtMatrixRouter contribution as per
   * <a href="https://github.com/matsim-org/matsim-libs/tree/master/contribs/matrixbasedptrouter">MATSim matrix
   * based Pt router</a>
   */
  protected boolean generateMatrixBasedPtRouterFiles = DEFAULT_GENERATE_MATRIX_BASED_PT_ROUTER_FILES;

  /** flag indicating the default for whether transit routes are blocking at their stop facilities */
  protected boolean ptBlockingAtStopFacility = PT_BLOCKING_AT_STOP_DEFAULT;
  
  /**
   * Log settings
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("MATSim zoning writer settings"));
    super.logSettings(level);
    LOGGER.info(LoggingUtils.settingsValue(
        "Generate MATSim Matrix based PT routing file", isGenerateMatrixBasedPtRouterFiles(), level));
  }

  /**
   * Default setting for generating files required to run MATSim matrix based pt router
   */
  public static final boolean DEFAULT_GENERATE_MATRIX_BASED_PT_ROUTER_FILES = true;

  /** default value aligned with MATSim default */
  public static final boolean PT_BLOCKING_AT_STOP_DEFAULT = false;
  
  /**
   * Default constructor using default output file name and Global country name
   */
  public MatsimZoningWriterSettings() {
    this(CountryNames.GLOBAL);
  }
  
  /**
   * Partial Copy constructor
   * 
   *@param matsimWriterSettings to copy shared content from, e.g. a Network or Zoning writer settings
   */
  public MatsimZoningWriterSettings(final PlanitMatsimWriterSettings matsimWriterSettings) {
    this(matsimWriterSettings.getOutputDirectory(),
        DEFAULT_TRANSIT_SCHEDULE_FILE_NAME,
        matsimWriterSettings.getCountry());
    this.setDestinationCoordinateReferenceSystem(matsimWriterSettings.getDestinationCoordinateReferenceSystem());
    this.setDecimalFormat(matsimWriterSettings.getDecimalFormat());
  }   
     
  
  /**
   * Constructor
   * 
   * @param outputDirectory to use
   * @param countryName to use
   */
  public MatsimZoningWriterSettings(final String outputDirectory, final String countryName) {
    super(outputDirectory, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
  }

  /**
   * Constructor
   * 
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  public MatsimZoningWriterSettings(
      final String outputDirectory,
      final String outputFileName,
      final String countryName) {
    super(outputDirectory, outputFileName, countryName);
  }

  /**
   * Default constructor
   *
   *@param countryName to use
   */
  public MatsimZoningWriterSettings(final String countryName) {
    this(null, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
  }

  /** Collect the flag indicating if MATSim matrix based pt routing is supported by generating its files
   * @return flag, when true activated when false not activated
   */
  public boolean isGenerateMatrixBasedPtRouterFiles() {
    return generateMatrixBasedPtRouterFiles;
  }

  /** Set the flag to indicate if MATSim matrix based pt routing is to be supported by generating its files
   * @param generateMatrixBasedPtRouterFiles when true activate, when false do not
   */
  public void setGenerateMatrixBasedPtRouterFiles(boolean generateMatrixBasedPtRouterFiles) {
    this.generateMatrixBasedPtRouterFiles = generateMatrixBasedPtRouterFiles;
  }  
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    // TODO Auto-generated method stub    
  }

  /**
   * flag whether pt is blocking at the stop facility
   *
   * @return is pt blocking flag
   */
  public boolean isPtBlockingAtStopFacility() {
    return ptBlockingAtStopFacility;
  }

  /**
   * when set to true all transit lines by default will be blocking at their stop facilities
   *
   * @param ptBlockingAtStopFacility set flag
   */
  public void setPtBlockingAtStopFacility(boolean ptBlockingAtStopFacility) {
    this.ptBlockingAtStopFacility = ptBlockingAtStopFacility;
  }
      
}
