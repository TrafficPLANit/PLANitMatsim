package org.planit.matsim.converter;

/**
 * Settings specific to writing the zoning related output in Matsim format (pt)
 * 
 * @author markr
 *
 */
public class PlanitMatsimZoningWriterSettings {

  /**
   * the output directory on where to persist the MATSIM file(s)
   */
  protected String outputDirectory;
  
  /**
   * the output file name to use for the transit schedule, default is set to DEFAULT_TRANSIT_SCHEDULE_FILE_NAME
   */
  protected String transitScheduleFileName = DEFAULT_TRANSIT_SCHEDULE_FILE_NAME;    
  
  /**
   * default names used for MATSIM public transport schedule file that is being generated
   */
  public static final String DEFAULT_TRANSIT_SCHEDULE_FILE_NAME = "transitschedule";
  
  /**
   * Default constructor 
   */
  public PlanitMatsimZoningWriterSettings() {
    this.outputDirectory = null;
  }
  
  /**
   * Constructor
   * 
   *  @param outputDirectory to use
   */
  public PlanitMatsimZoningWriterSettings(String outputDirectory) {
    this.outputDirectory = outputDirectory;
  }  
    
  /** the output directory to use
   * @return output directory
   */
  public String getOutputDirectory() {
    return outputDirectory;
  }

  /** set the output directory to use
   * @param outputDirector to use
   */
  public void setOutputDirectory(String outputDirectory) {
    this.outputDirectory = outputDirectory;
  }
  
  /** collect the transitScheduleFileName name used
   * @return transitScheduleFileName
   */
  public String getTransitScheduleFileName() {
    return transitScheduleFileName;
  }

  /** Set the transitScheduleFileName used
   * @param transitScheduleFileName to use
   */
  public void setTrnasitScheduleFileName(String transitScheduleFileName) {
    this.transitScheduleFileName = transitScheduleFileName;
  }  
}
