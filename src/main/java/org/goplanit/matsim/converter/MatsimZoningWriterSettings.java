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

  /** Flag indicating whether to write the access to transfer zones as MATSim nodes and links. Without it a rail or
   * ferry stop is an island in the MATSim network and nobody can reach it on foot, so this is on by default.
   */
  protected boolean writeTransferZoneAccess = DEFAULT_WRITE_TRANSFER_ZONE_ACCESS;

  /** Free speed in km/h written on the MATSim links providing access to a transfer zone. These links stand in for
   * walking between the stop and the network rather than for real infrastructure, so a single walking speed applies
   * regardless of which modes the underlying connectoid entry allows. Kept as a setting so it is visible and can be
   * made more granular later if that turns out to matter.
   */
  protected double transferZoneAccessSpeedKmH = DEFAULT_TRANSFER_ZONE_ACCESS_SPEED_KM_H;

  /**
   * Log settings
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("MATSim zoning writer settings"));
    super.logSettings(level);
    LOGGER.info(LoggingUtils.settingsValue(
        "Generate MATSim Matrix based PT routing file", isGenerateMatrixBasedPtRouterFiles(), level));
    LOGGER.info(LoggingUtils.settingsValue("Write transfer zone access", isWriteTransferZoneAccess(), level));
    LOGGER.info(LoggingUtils.settingsValue(
        "Transfer zone access speed (km/h)", getTransferZoneAccessSpeedKmH(), level));
  }

  /**
   * Default setting for generating files required to run MATSim matrix based pt router
   */
  public static final boolean DEFAULT_GENERATE_MATRIX_BASED_PT_ROUTER_FILES = true;

  /** default value aligned with MATSim default */
  public static final boolean PT_BLOCKING_AT_STOP_DEFAULT = false;

  /**
   * Default for writing transfer zone access is true, since a pt network that cannot be walked into is not useful
   */
  public static final boolean DEFAULT_WRITE_TRANSFER_ZONE_ACCESS = true;

  /** Default free speed on transfer zone access links in km/h, an average walking pace */
  public static final double DEFAULT_TRANSFER_ZONE_ACCESS_SPEED_KM_H = 5.0;
  
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
    this.generateMatrixBasedPtRouterFiles = DEFAULT_GENERATE_MATRIX_BASED_PT_ROUTER_FILES;
    this.ptBlockingAtStopFacility = PT_BLOCKING_AT_STOP_DEFAULT;
    this.writeTransferZoneAccess = DEFAULT_WRITE_TRANSFER_ZONE_ACCESS;
    this.transferZoneAccessSpeedKmH = DEFAULT_TRANSFER_ZONE_ACCESS_SPEED_KM_H;
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

  /** Collect the flag indicating if the access to transfer zones is written as MATSim nodes and links
   *
   * @return flag, when true transfer zone access is written, when false it is not
   */
  public boolean isWriteTransferZoneAccess() {
    return writeTransferZoneAccess;
  }

  /** Set the flag indicating if the access to transfer zones is to be written as MATSim nodes and links. When
   * disabled, stops that are not already on the road network cannot be reached on foot in MATSim
   *
   * @param writeTransferZoneAccess when true activate, when false do not
   */
  public void setWriteTransferZoneAccess(boolean writeTransferZoneAccess) {
    this.writeTransferZoneAccess = writeTransferZoneAccess;
  }

  /** Collect the free speed written on transfer zone access links
   *
   * @return speed in km/h
   */
  public double getTransferZoneAccessSpeedKmH() {
    return transferZoneAccessSpeedKmH;
  }

  /** Set the free speed written on transfer zone access links, applied regardless of the modes allowed on the
   * underlying connectoid entry since these links represent walking to and from the stop
   *
   * @param transferZoneAccessSpeedKmH to use, in km/h
   */
  public void setTransferZoneAccessSpeedKmH(double transferZoneAccessSpeedKmH) {
    this.transferZoneAccessSpeedKmH = transferZoneAccessSpeedKmH;
  }

}
