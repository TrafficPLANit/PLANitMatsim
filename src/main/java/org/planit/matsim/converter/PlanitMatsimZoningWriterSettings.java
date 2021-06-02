package org.planit.matsim.converter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.planit.matsim.util.PlanitMatsimWriterSettings;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.utils.locale.CountryNames;

/**
 * Settings specific to writing the zoning related output in Matsim format (pt)
 * 
 * @author markr
 *
 */
public class PlanitMatsimZoningWriterSettings extends PlanitMatsimWriterSettings{
  
  /** settings to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimZoningWriterSettings.class.getCanonicalName());
  
  /** the reference network this zoning is supposed to be compatible with */
  protected MacroscopicNetwork referenceNetwork;  
  
  /**
   * the output file name to use for the transit schedule, default is set to DEFAULT_TRANSIT_SCHEDULE_FILE_NAME
   */
  protected String transitScheduleFileName = DEFAULT_TRANSIT_SCHEDULE_FILE_NAME;
  
  /**
   * Log settings
   */
  protected void logSettings() {
    Path matsimZoningPath =  Paths.get(getOutputDirectory(), getOutputFileName().concat(PlanitMatsimWriter.DEFAULT_FILE_NAME_EXTENSION));    
    LOGGER.info(String.format("Persisting MATSIM pt to: %s",matsimZoningPath.toString()));
  }    
  
  /** Collect the reference network used
   * 
   * @return reference network
   */
  protected MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
  }      
  
  /**
   * default names used for MATSIM public transport schedule file that is being generated
   */
  public static final String DEFAULT_TRANSIT_SCHEDULE_FILE_NAME = "transitschedule";
  
  /**
   * Default constructor using default output file name and Global country name
   */
  public PlanitMatsimZoningWriterSettings() {
    this(CountryNames.GLOBAL);
  }
  
  /**
   * Default constructor 
   * 
   *@param countryName to use
   */
  public PlanitMatsimZoningWriterSettings(final String countryName) {
    this(null, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
  }   
     
  
  /**
   * Constructor
   * 
   * @param outputDirectory to use
   * @param countryName to use
   */
  public PlanitMatsimZoningWriterSettings(final String outputDirectory, final String countryName) {
    super(outputDirectory, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
  }  
  
  /**
   * Constructor
   * 
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  public PlanitMatsimZoningWriterSettings(final String outputDirectory, final String outputFileName, final String countryName) {
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
  public PlanitMatsimZoningWriterSettings(
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
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    // TODO Auto-generated method stub    
  }
 
      
}
