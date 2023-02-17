package org.goplanit.matsim.converter;

import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.zoning.Zoning;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Settings specific to writing the routed services and related output in MATSim format (pt), including the service network information
 * 
 * @author markr
 *
 */
public class MatsimPtServicesWriterSettings extends PlanitMatsimWriterSettings{

  /** settings to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimPtServicesWriterSettings.class.getCanonicalName());

  /** the reference zoning this writer is supposed to be compatible with */
  protected Zoning referenceZoning;

  /**
   * the output file name to use for the transit schedule, default is set to DEFAULT_TRANSIT_SCHEDULE_FILE_NAME
   */
  protected String transitScheduleFileName = DEFAULT_TRANSIT_SCHEDULE_FILE_NAME;

  /** reference to network settings to use, required due to mode mapping present, that we want to keep in sync */
  protected final MatsimNetworkWriterSettings networkSettings;

  /** reference to zoning settings to use, required due to how to write stop facilities */
  protected final MatsimZoningWriterSettings zoningSettings;

  /** flag indicating the default for transit routes awaiting departure based on their schedule */
  private boolean awaitDepartures = AWAIT_DEPARTURE_DEFAULT;

  /**
   * Log settings
   */
  protected void logSettings() {
    Path matsimZoningPath =  Paths.get(getOutputDirectory(), getOutputFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));
    LOGGER.info(String.format("Persisting MATSim public transport to: %s", matsimZoningPath));
  }

  /** package access to network writer settings */
  MatsimNetworkWriterSettings getNetworkSettings(){
    return networkSettings;
  }

  /** package access to network writer settings */
  MatsimZoningWriterSettings getZoningSettings(){
    return zoningSettings;
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
   * @param networkSettings to use
   * @param zoningSettings to use
   * @param transitScheduleFileName to use
   * @param referenceZoning to use
   */
  public MatsimPtServicesWriterSettings(
      final MatsimNetworkWriterSettings networkSettings,
      final MatsimZoningWriterSettings zoningSettings,
      final String transitScheduleFileName,
      final Zoning referenceZoning) {
    super(networkSettings.getOutputDirectory(), transitScheduleFileName, networkSettings.getCountry());
    setReferenceZoning(referenceZoning);
    this.networkSettings = networkSettings;
    this.zoningSettings = zoningSettings;
    setDecimalFormat(networkSettings.getDecimalFormat());
    setDestinationCoordinateReferenceSystem(networkSettings.getDestinationCoordinateReferenceSystem());
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
    this.networkSettings = new MatsimNetworkWriterSettings(outputDirectory, countryName); // default mode mapping
    this.zoningSettings = new MatsimZoningWriterSettings(outputDirectory, countryName); // default zoning settings
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
  public void setOutputDirectory(String outputDirectory) {
    super.setOutputDirectory(outputDirectory);
    getNetworkSettings().setOutputDirectory(outputDirectory);
    getZoningSettings().setOutputDirectory(outputDirectory);
  }

  /**
   * {@inheritDoc}
   */
  public void setCountry(String countryName) {
    super.setCountry(countryName);
    getNetworkSettings().setCountry(countryName);
    getZoningSettings().setCountry(countryName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setDestinationCoordinateReferenceSystem(CoordinateReferenceSystem destinationCoordinateReferenceSystem) {
    super.setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    getNetworkSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    getZoningSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    // TODO Auto-generated method stub    
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
