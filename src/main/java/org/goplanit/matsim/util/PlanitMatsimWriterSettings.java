package org.goplanit.matsim.util;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.converter.FileBasedConverterWriterSettings;
import org.goplanit.converter.SingleFileBasedConverterWriterSettings;
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
public abstract class PlanitMatsimWriterSettings extends SingleFileBasedConverterWriterSettings implements ConverterWriterSettings {

  /**
   * number of decimals to use, default is Precision.DEFAULT_DECIMAL_FORMAT
   */
  protected DecimalFormat decimalFormat = Precision.DEFAULT_DECIMAL_FORMAT;

  /**
   * default names used for MATSIM public transport schedule file that is being generated
   */
  public static final String DEFAULT_TRANSIT_SCHEDULE_FILE_NAME = "output_transitschedule";

  /**
   * default names used for MATSIM network file that is being generated
   */
  public static final String DEFAULT_NETWORK_FILE_NAME = "output_network";

  /**
   * Default constructor 
   */
  public PlanitMatsimWriterSettings() {
    super();
  }
  
  /**
   * Constructor
   * 
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  public PlanitMatsimWriterSettings(final String outputDirectory, final String outputFileName, final String countryName) {
    super(outputDirectory, outputFileName, countryName);
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
