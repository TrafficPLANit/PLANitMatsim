package org.goplanit.matsim.converter;

import org.goplanit.service.routed.RoutedServices;
import org.goplanit.zoning.Zoning;

/**
 * Factory for creating PLANitMatsim routed (PT) services writers.
 * 
 * @author markr
 *
 */
public class MatsimPublicTransportServicesWriterFactory {
  
  /** Create a PLANitMatsimRoutedServicesWriter (pt output) with defaults. It is expected the user sets the appropriate properties
   * afterwards as required for this particular type of writer
   * 
   * @param networkWriterSettings to use
   * @param referenceZoning to use
   * @return create MATSim zoning (pt) writer
   */
  public static MatsimRoutedServicesWriter create(
      MatsimNetworkWriterSettings networkWriterSettings,
      Zoning referenceZoning) {
    return create(
        new MatsimPublicTransportServicesWriterSettings(
            networkWriterSettings.getOutputDirectory(),
            MatsimZoningWriterSettings.DEFAULT_TRANSIT_SCHEDULE_FILE_NAME,
            networkWriterSettings.getCountry(),
            referenceZoning));
  }   
      
  /** Create a PLANitMatsimRoutedServicesWriter
   * 
   * @param routedServicesWriterSettings to use
   * @return create MATSim writer
   */
  public static MatsimRoutedServicesWriter create(MatsimPublicTransportServicesWriterSettings routedServicesWriterSettings) {
    return new MatsimRoutedServicesWriter(routedServicesWriterSettings);
  }  
   
  
}
