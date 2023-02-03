package org.goplanit.matsim.converter;

import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
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
public class MatsimRoutedServicesWriterSettings extends PlanitMatsimWriterSettings{

  /** settings to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimRoutedServicesWriterSettings.class.getCanonicalName());

  /** the reference network this writer is supposed to be compatible with */
  protected MacroscopicNetwork referenceNetwork;

  /** the reference zoning this writer is supposed to be compatible with */
  protected Zoning referenceZoning;

  /** the reference service network this writer is supposed to be compatible with */
  protected ServiceNetwork referenceServiceNetwork;

  /**
   * the output file name to use for the transit schedule, default is set to DEFAULT_TRANSIT_SCHEDULE_FILE_NAME
   */
  protected String transitScheduleFileName = DEFAULT_TRANSIT_SCHEDULE_FILE_NAME;

  /**
   * Log settings
   */
  protected void logSettings() {
    Path matsimZoningPath =  Paths.get(getOutputDirectory(), getOutputFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));
    LOGGER.info(String.format("Persisting MATSIM public transport to: %s", matsimZoningPath));
  }

  /** Collect the reference network used
   *
   * @return reference network
   */
  protected MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
  }

  /** Collect the reference zoning used
   *
   * @return reference zoning
   */
  protected Zoning getReferenceZoning() {
    return referenceZoning;
  }

  /** Collect the reference service network used
   *
   * @return reference service network
   */
  protected ServiceNetwork getReferenceServiceNetwork() {
    return referenceServiceNetwork;
  }

  /**
   * Default constructor using default output file name and Global country name
   */
  public MatsimRoutedServicesWriterSettings() {
    this(CountryNames.GLOBAL);
  }

  /**
   * Default constructor
   *
   *@param countryName to use
   */
  public MatsimRoutedServicesWriterSettings(final String countryName) {
    this(null, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
  }


  /**
   * Constructor
   *
   * @param outputDirectory to use
   * @param countryName to use
   */
  public MatsimRoutedServicesWriterSettings(final String outputDirectory, final String countryName) {
    super(outputDirectory, DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, countryName);
  }

  /**
   * Constructor
   *
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  public MatsimRoutedServicesWriterSettings(final String outputDirectory, final String outputFileName, final String countryName) {
    this(outputDirectory, outputFileName, countryName, null, null, null);
  }

  /**
   * Constructor
   *
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   * @param referenceNetwork to use
   * @param referenceZoning to use
   */
  public MatsimRoutedServicesWriterSettings(
      final String outputDirectory,
      final String outputFileName,
      final String countryName,
      final MacroscopicNetwork referenceNetwork,
      final Zoning referenceZoning,
      final ServiceNetwork referenceServiceNetwork) {
    super(outputDirectory, outputFileName, countryName);
    setReferenceNetwork(referenceNetwork);
    setReferenceZoning(referenceZoning);
    setReferenceServiceNetwork(referenceServiceNetwork);
  }  
  
  /** Set the reference network to use
   * @param referenceNetwork to use
   */
  public void setReferenceNetwork(MacroscopicNetwork referenceNetwork) {
    this.referenceNetwork = referenceNetwork;
  }

  /** Set the reference zoning to use
   * @param referenceZoning to use
   */
  public void setReferenceZoning(Zoning referenceZoning) {
    this.referenceZoning = referenceZoning;
  }

  /** Set the reference service network to use
   * @param referenceServiceNetwork to use
   */
  public void setReferenceServiceNetwork(ServiceNetwork referenceServiceNetwork) {
    this.referenceServiceNetwork = referenceServiceNetwork;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    // TODO Auto-generated method stub    
  }
 
      
}
