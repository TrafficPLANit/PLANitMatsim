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
   * @param referenceZoning as these contain the transfer zones (stops) that MATSim requires
   * @return created MatsimRoutedServicesWriter
   */
  public static MatsimRoutedServicesWriter create(Zoning referenceZoning) {
    return create(".", referenceZoning);
  }

  /** Create a MatsimRoutedServicesWriter which persists PLANit routed services in a public transport schedule in MATSIM format in given  directory
   *
   * @param outputDirectory to use
   * @param referenceZoning as these contain the transfer zones (stops) that MATSim requires
   * @return createMATSimMatsimRoutedServicesWriter
   */
  public static MatsimRoutedServicesWriter create(String outputDirectory, Zoning referenceZoning) {
    return create(outputDirectory, CountryNames.GLOBAL, referenceZoning);
  }

  /** Create a MatsimRoutedServicesWriter which persists PLANit routed services in a public transport schedule in MATSIM format in given  directory for a given country
   *
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults
   * @param referenceZoning as these contain the transfer zones (stops) that MATSim requires
   * @return created MatsimRoutedServicesWriter
   */
  public static MatsimRoutedServicesWriter create(String outputDirectory, String countryName, Zoning referenceZoning) {
    return create(new MatsimPtServicesWriterSettings(outputDirectory, countryName), referenceZoning);
  }

  /** Create a MatsimRoutedServicesWriter which persists PLANit routed services in a public transport schedule in MATSIM format based on settings provided
   *
   * @param settings to use
   * @param referenceZoning as these contain the transfer zones (stops) that MATSim requires
   * @return created MatsimRoutedServicesWriter
   */
  public static MatsimRoutedServicesWriter create(MatsimPtServicesWriterSettings settings, Zoning referenceZoning) {
    return create(settings,
        new MatsimNetworkWriterSettings(settings.getOutputDirectory(), settings.getCountry()),
        new MatsimZoningWriterSettings(settings.getOutputDirectory(), settings.getCountry()),
        referenceZoning);
  }

  /** Create a MatsimRoutedServicesWriter which persists PLANit routed services in a public transport schedule in MATSIM format based on settings provided
   *
   * @param settings to use
   * @param referenceZoning as these contain the transfer zones (stops) that MATSim requires
   * @return created MatsimRoutedServicesWriter
   */
  public static MatsimRoutedServicesWriter create(MatsimIntermodalWriterSettings settings, Zoning referenceZoning) {
    return create(settings.getPtServicesSettings(), settings.getNetworkSettings(), settings.getZoningSettings(), referenceZoning);
  }

  /** Create a PLANitMatsimRoutedServicesWriter (pt output) with defaults. It is expected the user sets the appropriate properties
   * afterwards as required for this particular type of writer
   *
   * @param networkWriterSettings to use
   * @param zoningWriterSettings to use
   * @param routedServicesSettings to use
   * @param referenceZoning as these contain the transfer zones (stops) that MATSim requires
   * @return create MATSim zoning (pt) writer
   */
  public static MatsimRoutedServicesWriter create(
      MatsimPtServicesWriterSettings routedServicesSettings,
      MatsimNetworkWriterSettings networkWriterSettings,
      MatsimZoningWriterSettings zoningWriterSettings,
      Zoning referenceZoning) {
    return new MatsimRoutedServicesWriter(routedServicesSettings, networkWriterSettings, zoningWriterSettings, referenceZoning);
  }

  
}
