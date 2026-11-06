package org.goplanit.matsim.util;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.converter.FileBasedConverterWriterSettings;
import org.goplanit.converter.SingleFileBasedConverterWriterSettings;
import org.goplanit.utils.math.Precision;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.goplanit.utils.misc.LoggingUtils;

import java.text.DecimalFormat;
import java.util.logging.Logger;

/**
 * Base writer settings class to be used by all available matsim writer settings classes.
 * Contains the output directory and destination country name used
 * 
 * @author markr
 *
 */
public abstract class PlanitMatsimWriterSettings extends SingleFileBasedConverterWriterSettings
    implements ConverterWriterSettings {

  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimWriterSettings.class.getCanonicalName());

  /**
   * number of decimals to use, default is Precision.DEFAULT_DECIMAL_FORMAT
   */
  protected DecimalFormat decimalFormat = Precision.DEFAULT_DECIMAL_FORMAT;

  /**
   * Choice on how to write MATSim XML files, as GZipped or not
   */
  protected boolean writeAsGZip = DEFAULT_WRITE_GZIP;

  /**
   * default names used for MATSIM public transport schedule file
   */
  public static final String DEFAULT_TRANSIT_SCHEDULE_FILE_NAME = "output_transitschedule";

  /**
   * default names used for MATSIM network file
   */
  public static final String DEFAULT_NETWORK_FILE_NAME = "output_network";

  /**
   * default names used for MATSIM plans file
   */
  public static final String DEFAULT_PLANS_FILE_NAME = "output_plans";

  /**
   * Default is to write XML files as gzipped
   */
  public static final boolean DEFAULT_WRITE_GZIP = true;

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
  public PlanitMatsimWriterSettings(
      final String outputDirectory, final String outputFileName, final String countryName) {
    super(outputDirectory, outputFileName, countryName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    super.logSettings(level);
    LOGGER.info(LoggingUtils.settingsValue("Decimal fidelity", decimalFormat.getMaximumFractionDigits(), level));
    LOGGER.info(LoggingUtils.settingsValue("Write as GZip", isWriteAsGZip(), level));
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

  /**
   * Write XML as gzipped xml
   * @param flag flag
   */
  public void setWriteAsGZip(boolean flag) {
    this.writeAsGZip = flag;
  }

  /**
   * Write XML as gzipped xml
   * @return flag
   */
  public boolean isWriteAsGZip() {
    return writeAsGZip;
  }
}
