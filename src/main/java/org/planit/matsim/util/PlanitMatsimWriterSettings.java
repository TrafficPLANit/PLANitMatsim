package org.planit.matsim.util;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.converter.ConverterWriterSettings;
import org.planit.utils.locale.CountryNames;

/**
 * Base writer settings class to be used by all available matsim writer settings classes.
 * Contains the output directory and destination country name used
 * 
 * @author markr
 *
 */
public abstract class PlanitMatsimWriterSettings implements ConverterWriterSettings {

  /**
   * the output directory on where to persist the MATSIM file(s)
   */
  protected String outputDirectory;
  
  /**
   * the output file name of the to be persisted the MATSIM file
   */
  protected String outputFileName;  
  
  /** the destination country this writer is configured for */
  protected String countryName;
  
  /** the coordinate reference system used for writing entities of this network */
  protected CoordinateReferenceSystem destinationCoordinateReferenceSystem = null;    
  
  /**
   * Default constructor 
   */
  public PlanitMatsimWriterSettings() {
    this(null, null, CountryNames.GLOBAL);
  }
  
  /**
   * Constructor
   * 
   * @param outputDirectory to use
   * @param countryName to use
   */
  public PlanitMatsimWriterSettings(final String outputDirectory, final String outputFileName, final String countryName) {
    this.outputDirectory = outputDirectory;
    this.outputFileName = outputFileName;
    this.countryName = countryName;
  }   
  
  
  /** the output directory to use
   * @return output directory
   */
  public String getOutputDirectory() {
    return this.outputDirectory;
  }

  /** set the output directory to use
   * @param outputDirectory to use
   */
  public void setOutputDirectory(String outputDirectory) {
    this.outputDirectory = outputDirectory;
  }
  
  /** collect the country name set
   * @return country name
   */
  public String getCountry() {
    return countryName;
  }

  /** set the country name to use
   * @param countryName to use
   */
  public void setCountry(String countryName) {
    this.countryName = countryName;
  }
  
  /** The output file name to use
   * @return output file name
   */
  public String getOutputFileName() {
    return outputFileName;
  }

  /** Set the output file name to use
   * @param outputFileName to use
   */
  public void setOutputFileName(String outputFileName) {
    this.outputFileName = outputFileName;
  }  
  
  /** Collect the currently used CRS for writing the output geometries
   * 
   * @return crs used
   */
  public CoordinateReferenceSystem getDestinationCoordinateReferenceSystem() {
    return destinationCoordinateReferenceSystem;
  }

  /** Explicitly set a particular crs for writing geometries
   * @param destinationCoordinateReferenceSystem to use
   */
  public void setDestinationCoordinateReferenceSystem(CoordinateReferenceSystem destinationCoordinateReferenceSystem) {
    this.destinationCoordinateReferenceSystem = destinationCoordinateReferenceSystem;
  }    
}
