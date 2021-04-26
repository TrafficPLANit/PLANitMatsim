package org.planit.matsim.converter;

import org.planit.matsim.util.PlanitMatsimWriterSettings;
import org.planit.network.macroscopic.MacroscopicNetwork;

/**
 * Settings specific to writing the zoning related output in Matsim format (pt)
 * 
 * @author markr
 *
 */
public class PlanitMatsimZoningWriterSettings extends PlanitMatsimWriterSettings{
  
  /** the reference network this zoning is supposed to be compatible with */
  protected MacroscopicNetwork referenceNetwork;  
  
  /**
   * the output file name to use for the transit schedule, default is set to DEFAULT_TRANSIT_SCHEDULE_FILE_NAME
   */
  protected String transitScheduleFileName = DEFAULT_TRANSIT_SCHEDULE_FILE_NAME;
  
  /** collect the reference network used
   * @return
   */
  protected MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
  }  
  
  /**
   * default names used for MATSIM public transport schedule file that is being generated
   */
  public static final String DEFAULT_TRANSIT_SCHEDULE_FILE_NAME = "transitschedule";
  
  /**
   * Default constructor 
   */
  public PlanitMatsimZoningWriterSettings() {
    super();
  }
  
  /**
   * Default constructor 
   * 
   *@param countryName to use
   */
  public PlanitMatsimZoningWriterSettings(final String countryName) {
    super(null, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
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
    super(outputDirectory, outputFileName, countryName);
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
