package org.goplanit.matsim.converter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.locale.CountryNames;

/**
 * Settings specific to writing the zoning related output in Matsim format (pt)
 * 
 * @author markr
 *
 */
public class MatsimZoningWriterSettings extends PlanitMatsimWriterSettings{
  
  /** settings to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimZoningWriterSettings.class.getCanonicalName());
  
  /** the reference network this zoning is supposed to be compatible with */
  protected MacroscopicNetwork referenceNetwork;  
  
  /**
   * While persisting generate the input files for the MATSim PtMatrixRouter contribution as per <a href="https://github.com/matsim-org/matsim-libs/tree/master/contribs/matrixbasedptrouter">MATSim matrix based Pt router</a> 
   */
  protected boolean generateMatrixBasedPtRouterFiles = DEFAULT_GENERATE_MATRIX_BASED_PT_ROUTER_FILES;
  
  /**
   * the output file name to use for the transit schedule, default is set to DEFAULT_TRANSIT_SCHEDULE_FILE_NAME
   */
  protected String transitScheduleFileName = DEFAULT_TRANSIT_SCHEDULE_FILE_NAME;

  /** flag indicating the default for whether transit routes are blocking at their stop facilities */
  protected boolean ptBlockingAtStopFacility = PT_BLOCKING_AT_STOP_DEFAULT;
  
  /**
   * Log settings
   */
  protected void logSettings() {
    Path matsimZoningPath =  Paths.get(getOutputDirectory(), getOutputFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));    
    LOGGER.info(String.format("Persisting MATSIM public transport to: %s",matsimZoningPath.toString()));
    LOGGER.info(String.format("MATSIM Matrix based PT routing file are %s generated",isGenerateMatrixBasedPtRouterFiles() ? "" : "not"));
  }    
  
  /** Collect the reference network used
   * 
   * @return reference network
   */
  protected MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
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
   * Default constructor 
   * 
   *@param countryName to use
   */
  public MatsimZoningWriterSettings(final String countryName) {
    this(null, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
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
  public MatsimZoningWriterSettings(final String outputDirectory, final String outputFileName, final String countryName) {
    this(outputDirectory, outputFileName, countryName, null);
  }  
  
  /**
   * Constructor
   * 
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   * @param referenceNetwork to use
   */
  public MatsimZoningWriterSettings(
      final String outputDirectory, final String outputFileName, final String countryName, final MacroscopicNetwork referenceNetwork) {
    super(outputDirectory, outputFileName, countryName);
    setReferenceNetwork(referenceNetwork);
  }  
  
  /** Set the reference network to use when mapping zoning entities to network entities
   * @param referenceNetwork to use
   */
  public void setReferenceNetwork(MacroscopicNetwork referenceNetwork) {
    this.referenceNetwork = referenceNetwork;
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
