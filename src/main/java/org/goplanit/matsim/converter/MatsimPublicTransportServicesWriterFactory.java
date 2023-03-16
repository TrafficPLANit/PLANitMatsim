package org.goplanit.matsim.converter;

import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.zoning.Zoning;

/**
 * Factory for creating PLANitMatsim routed (PT) services writers.
 * 
 * @author markr
 *
 */
public class MatsimPublicTransportServicesWriterFactory {

  /** Create a MatsimRoutedServicesWriter which persists PLANit routed services in a public transport schedule in MATSIM format in current working directory
   *
   * @return created MatsimRoutedServicesWriter
   */
  public static MatsimRoutedServicesWriter create() {
    return create(".");
  }

  /** Create a MatsimRoutedServicesWriter which persists PLANit routed services in a public transport schedule in MATSIM format in given  directory
   *
   * @param outputDirectory to use
   * @return createMATSimMatsimRoutedServicesWriter
   */
  public static MatsimRoutedServicesWriter create(String outputDirectory) {
    return create(outputDirectory, CountryNames.GLOBAL);
  }

  /** Create a MatsimRoutedServicesWriter which persists PLANit routed services in a public transport schedule in MATSIM format in given  directory for a given country
   *
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults
   * @return created MatsimRoutedServicesWriter
   */
  public static MatsimRoutedServicesWriter create(String outputDirectory, String countryName) {
    return create(new MatsimPtServicesWriterSettings(outputDirectory, countryName));
  }

  /** Create a MatsimRoutedServicesWriter which persists PLANit routed services in a public transport schedule in MATSIM format based on settings provided
   *
   * @param settings to use
   * @return created MatsimRoutedServicesWriter
   */
  public static MatsimRoutedServicesWriter create(MatsimPtServicesWriterSettings settings) {
    return new MatsimRoutedServicesWriter(settings);
  }

  // CONTINUE HERE --> STREAMLINE THIS FACTORY --> PT SERVICES REQUIRES INFO FROM ZONING/NETWORK SETTINGS, this however should be stored
  // on the writer not on the settings of this writer, this is what needs to be changed....

  /** Create a PLANitMatsimRoutedServicesWriter (pt output) with defaults. It is expected the user sets the appropriate properties
   * afterwards as required for this particular type of writer
   * 
   * @param networkWriterSettings to use
   * @param referenceZoning to use
   * @return create MATSim zoning (pt) writer
   */
  public static MatsimRoutedServicesWriter create(
      MatsimNetworkWriterSettings networkWriterSettings,
      MatsimZoningWriterSettings zoningWriterSettings,
      Zoning referenceZoning) {
    return create(
        networkWriterSettings,
        zoningWriterSettings,
        new MatsimPtServicesWriterSettings(
            zoningWriterSettings.getOutputFileName(),
            referenceZoning));
  }   
      
  /** Create a PLANitMatsimRoutedServicesWriter
   * 
   * @param routedServicesWriterSettings to use
   * @return create MATSim writer
   */
  public static MatsimRoutedServicesWriter create(MatsimPtServicesWriterSettings routedServicesWriterSettings) {
    return new MatsimRoutedServicesWriter(routedServicesWriterSettings);
  }  
   
  
}
