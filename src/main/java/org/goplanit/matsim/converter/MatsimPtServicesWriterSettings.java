package org.goplanit.matsim.converter;

import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.zoning.Zoning;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Settings specific to writing the routed services and related output in MATSim format (pt), including the service network information
 * 
 * @author markr
 *
 */
public class MatsimPtServicesWriterSettings extends PlanitMatsimWriterModeMappingSettings {

  /** settings to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimPtServicesWriterSettings.class.getCanonicalName());

  /** the reference zoning this writer is supposed to be compatible with */
  protected Zoning referenceZoning;

  /**
   * the output file name to use for the transit schedule, default is set to DEFAULT_TRANSIT_SCHEDULE_FILE_NAME
   */
  protected String transitScheduleFileName = DEFAULT_TRANSIT_SCHEDULE_FILE_NAME;

  /** flag indicating the default for transit routes awaiting departure based on their schedule */
  private boolean awaitDepartures = AWAIT_DEPARTURE_DEFAULT;

  /**
   * Log settings but do not use parent class log settings as it is assumed this writer is always used
   * in conjunction with MATsim network writer and we shared the mode mapping with these settings which will
   * already be logged.
   *
   * todo: in future if there is a use case for the pt services to be persisted stand alone, then we can because we already
   * have the mode mapping as part of its settings, but then we would want to log this mapping to the user in which case this
   * method needs adjusting to be configurable, either do or do not log the mode mapping depending on the use case. Currently
   * this mapping is simply not logged.
   *
   */
  protected void logSettingsWithoutModeMapping() {
    Path matsimZoningPath =  Paths.get(getOutputDirectory(), getFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));
    LOGGER.info(String.format("Persisting MATSim public transport to: %s", matsimZoningPath));
  }

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
  public MatsimPtServicesWriterSettings(final String outputDirectory, final String outputFileName, final String countryName) {
    this(outputDirectory, outputFileName, countryName, null);
  }

  /**
   * Constructor
   *
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   * @param referenceZoning to use
   */
  public MatsimPtServicesWriterSettings(
      final String outputDirectory,
      final String outputFileName,
      final String countryName,
      final Zoning referenceZoning) {
    super(outputDirectory, outputFileName, countryName);
    setReferenceZoning(referenceZoning);
  }

  /**
   * Copy Constructor based on PlanitMatsimWriterModeMappingSettings
   *
   * @param settings to apply
   */
  public MatsimPtServicesWriterSettings(final PlanitMatsimWriterModeMappingSettings settings){
    super(settings);
  }

  /** Collect the reference zoning used
   *
   * @return reference zoning
   */
  protected Zoning getReferenceZoning() {
    return referenceZoning;
  }

  /** Set the reference zoning to use
   * @param referenceZoning to use
   */
  public void setReferenceZoning(Zoning referenceZoning) {
    this.referenceZoning = referenceZoning;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    // TODO
  }

  // getters-settings

  /**
   *
   * @return await departures flag
   */
  public boolean isAwaitDepartures() {
    return awaitDepartures;
  }

  /**
   * when set to true all transit lines by default will be flagged to await departure based on their schedule
   *
   * @param awaitDepartures set await departures flag
   */
  public void setAwaitDepartures(boolean awaitDepartures) {
    this.awaitDepartures = awaitDepartures;
  }

}
