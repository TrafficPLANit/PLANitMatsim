package org.goplanit.matsim.converter;

import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;
import org.goplanit.zoning.Zoning;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Settings specific to writing the routed services and related output in MATSim format (pt),
 * including the service network information
 * 
 * @author markr
 *
 */
public class MatsimPtServicesWriterSettings extends PlanitMatsimWriterSettings {

  /** settings to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimPtServicesWriterSettings.class.getCanonicalName());

  /** flag indicating the default for transit routes awaiting departure based on their schedule */
  private boolean awaitDepartures = AWAIT_DEPARTURE_DEFAULT;

  /** default value aligned with MATSim default */
  public static final boolean AWAIT_DEPARTURE_DEFAULT = false;

  /**
   * Default constructor using default output file name and Global country name
   */
  public MatsimPtServicesWriterSettings() {
    this(CountryNames.GLOBAL);
  }

  /**
   * Default constructor
   *
   *@param countryName to use
   */
  public MatsimPtServicesWriterSettings(final String countryName) {
    this(null, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
  }

  /**
   * Constructor
   *
   * @param outputDirectory to use
   * @param countryName to use
   */
  public MatsimPtServicesWriterSettings(final String outputDirectory, final String countryName) {
    this(outputDirectory, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
  }

  /**
   * Constructor
   *
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  public MatsimPtServicesWriterSettings(
      final String outputDirectory,
      final String outputFileName,
      final String countryName) {
    super(outputDirectory, outputFileName, countryName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    setFileName(DEFAULT_TRANSIT_SCHEDULE_FILE_NAME);
    this.awaitDepartures = AWAIT_DEPARTURE_DEFAULT;
  }

  /**
   * Log the settings under their own heading. The MATSim modes of the transit routes are not among them: they come
   * from the network writer settings, which log them.
   *
   * @param level to log at
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("MATSim public transport services settings"));
    super.logSettings(level);
  }

  // getters-settings

  /**
   *
   * @return await departures flag
   */
  @SettingsRead(key = "awaitDepartures")
  public boolean isAwaitDepartures() {
    return awaitDepartures;
  }

  /**
   * when set to true all transit lines by default will be flagged to await departure based on their schedule
   *
   * @param awaitDepartures set await departures flag
   */
  @SettingsWrite(key = "awaitDepartures", level = SettingsKeyLevel.UNCOMMON,
      description = "Make every transit route wait at each stop until its scheduled departure time")
  public void setAwaitDepartures(boolean awaitDepartures) {
    this.awaitDepartures = awaitDepartures;
  }

}
