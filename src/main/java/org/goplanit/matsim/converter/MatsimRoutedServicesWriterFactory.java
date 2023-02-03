package org.goplanit.matsim.converter;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.zoning.Zoning;

/**
 * Factory for creating PLANitMatsim routed (PT) services writers.
 * 
 * @author markr
 *
 */
public class MatsimRoutedServicesWriterFactory {
  
  /** Create a PLANitMatsimRoutedServicesWriter (pt output) with defaults. It is expected the user sets the appropriate properties
   * afterwards as required for this particular type of writer
   * 
   * @param networkWriterSettings to use
   * @param referenceNetwork to use
   * @param referenceZoning to use
   * @param referenceServiceNetwork to use
   * @return create MATSim zoning (pt) writer
   */
  public static MatsimRoutedServicesWriter create(
      MatsimNetworkWriterSettings networkWriterSettings,
      MacroscopicNetwork referenceNetwork,
      Zoning referenceZoning,
      ServiceNetwork referenceServiceNetwork) {
    return create(
        new MatsimRoutedServicesWriterSettings(
            networkWriterSettings.getOutputDirectory(),
            MatsimZoningWriterSettings.DEFAULT_TRANSIT_SCHEDULE_FILE_NAME,
            networkWriterSettings.getCountry(),
            referenceNetwork,
            referenceZoning,
            referenceServiceNetwork));
  }   
      
  /** Create a PLANitMatsimRoutedServicesWriter
   * 
   * @param routedServicesWriterSettings to use
   * @return create MATSim writer
   */
  public static MatsimRoutedServicesWriter create(MatsimRoutedServicesWriterSettings routedServicesWriterSettings) {
    return new MatsimRoutedServicesWriter(routedServicesWriterSettings);
  }  
   
  
}
