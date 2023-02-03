package org.goplanit.matsim.util;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.utils.math.Precision;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.text.DecimalFormat;

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
   * number of decimals to use, default is Precision.DEFAULT_DECIMAL_FORMAT
   */
  protected DecimalFormat decimalFormat = Precision.DEFAULT_DECIMAL_FORMAT;

  /**
   * default names used for MATSIM public transport schedule file that is being generated
   */
  public static final String DEFAULT_TRANSIT_SCHEDULE_FILE_NAME = "transitschedule";

  /**
   * Default constructor 
   */
  public PlanitMatsimWriterSettings() {
    this(null, null, null);
  }
  
  /**
   * Constructor
   * 
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  public PlanitMatsimWriterSettings(final String outputDirectory, final String outputFileName, final String countryName) {
    this.outputDirectory = outputDirectory;
    this.outputFileName = outputFileName;
    this.countryName = countryName;
  }   
  
  
  /** The output directory to use
   * 
   * @return output directory
   */
  public String getOutputDirectory() {
    return this.outputDirectory;
  }

  /** Set the output directory to use
   * 
   * @param outputDirectory to use
   */
  public void setOutputDirectory(String outputDirectory) {
    this.outputDirectory = outputDirectory;
  }
  
  /** Collect the country name set
   * 
   * @return country name
   */
  public String getCountry() {
    return countryName;
  }

  /** Set the country name to use
   * 
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

  /** Collect number of decimals used in writing coordinates
   *
   * @return number of decimals used
   */
  public DecimalFormat getDecimalFormat() {
    return decimalFormat;
  }

  /** Set number of decimals used in writing coordinates
   *
   * @param decimalFormat format to use
   */
  public void setDecimalFormat(DecimalFormat decimalFormat) {
    this.decimalFormat = decimalFormat;
  }
}
