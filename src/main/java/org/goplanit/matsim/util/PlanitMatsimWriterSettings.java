package org.goplanit.matsim.util;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.converter.FileBasedConverterWriterSettings;
import org.goplanit.converter.SingleFileBasedConverterWriterSettings;
import org.goplanit.utils.math.Precision;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.settings.SettingsSchema;
import org.goplanit.utils.settings.YamlWriteSettingsSupport;
import org.goplanit.utils.settings.annotations.SettingsHeader;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

import java.text.DecimalFormat;
import java.util.logging.Logger;

/**
 * Base writer settings class to be used by all available matsim writer settings classes.
 * Contains the output directory and destination country name used
 * <p>
 * Each MATSim writer has a settings file of its own, headed {@code planitMatsimWriterSettings: 1}, holding every key
 * at the top level, those of every MATSim writer and its own, such as
 * </p>
 * <pre>
 * planitMatsimWriterSettings: 1
 * outputDirectory: out
 * country: Australia
 * fileName: roads
 * writeAsGZip: false
 * </pre>
 * <p>
 * One header serves every MATSim writer, so a file of the keys several writers share can be stacked under each
 * writer's own file. {@link org.goplanit.matsim.converter.MatsimWriterSettingsFactory} makes the settings of each
 * writer from such files.
 * </p>
 *
 * @author markr
 *
 */
@SettingsHeader(key = "planitMatsimWriterSettings", version = 1)
public abstract class PlanitMatsimWriterSettings extends SingleFileBasedConverterWriterSettings
    implements ConverterWriterSettings, YamlWriteSettingsSupport {

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
   * The schema of every MATSim writer settings source: the header of these settings,
   * {@code planitMatsimWriterSettings: 1}, and every key of a writer at the top level, or, for the intermodal writer,
   * the settings files of the writers it holds. One schema serves every MATSim writer, the discrete demands writer
   * included; a key another writer takes is unknown to a writer that does not
   */
  public static final SettingsSchema SCHEMA = new SettingsSchema(PlanitMatsimWriterSettings.class);

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

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    this.decimalFormat = Precision.DEFAULT_DECIMAL_FORMAT;
    this.writeAsGZip = DEFAULT_WRITE_GZIP;
  }

  /** Collect number of decimals used in writing coordinates
   *
   * @return number of decimals used
   */
  @SettingsRead(key = "decimalFormat")
  public DecimalFormat getDecimalFormat() {
    return decimalFormat;
  }

  /** Set number of decimals used in writing coordinates
   *
   * @param decimalFormat format to use
   */
  @SettingsWrite(key = "decimalFormat", level = SettingsKeyLevel.UNCOMMON,
      description = "The pattern numbers are written with, such as #.###, as Java's DecimalFormat reads it")
  public void setDecimalFormat(DecimalFormat decimalFormat) {
    this.decimalFormat = decimalFormat;
  }

  /**
   * Write XML as gzipped xml
   * @param flag flag
   */
  @SettingsWrite(key = "writeAsGZip", level = SettingsKeyLevel.STANDARD,
      description = "Write the MATSim files gzipped, adding .gz to their names")
  public void setWriteAsGZip(boolean flag) {
    this.writeAsGZip = flag;
  }

  /**
   * Write XML as gzipped xml
   * @return flag
   */
  @SettingsRead(key = "writeAsGZip")
  public boolean isWriteAsGZip() {
    return writeAsGZip;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SettingsSchema getSettingsSchema() {
    return SCHEMA;
  }
}
