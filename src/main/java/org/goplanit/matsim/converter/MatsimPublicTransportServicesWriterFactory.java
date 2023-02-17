package org.goplanit.matsim.converter;

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
      MatsimZoningWriterSettings zoningWriterSettings,
      Zoning referenceZoning) {
    return create(
        new MatsimPtServicesWriterSettings(
            networkWriterSettings,
            zoningWriterSettings,
            MatsimZoningWriterSettings.DEFAULT_TRANSIT_SCHEDULE_FILE_NAME,
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
