package org.goplanit.matsim.converter;

import org.goplanit.network.MacroscopicNetwork;

/**
 * Factory for creating PLANitMatsim zoning writers.
 * 
 * @author markr
 *
 */
public class MatsimZoningWriterFactory {
  
  /** Create a PLANitMatsimZoningWriter (pt output) with defaults. It is expected the user sets the appropriate properties
   * afterwards as required for this particular type of writer
   * 
   * @param networkWriterSettings to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create MATSim zoning (pt) writer
   */
  public static MatsimZoningWriter create(MatsimNetworkWriterSettings networkWriterSettings, MacroscopicNetwork referenceNetwork) {
    return create(
        new MatsimZoningWriterSettings(
            networkWriterSettings.getOutputDirectory(),
            MatsimZoningWriterSettings.DEFAULT_TRANSIT_SCHEDULE_FILE_NAME,
            networkWriterSettings.getCountry(),
            referenceNetwork),
          networkWriterSettings);    
  }   
      
  /** Create a PLANitMatsimWriter
   * 
   * @param zoningWriterSettings to use
   * @param networkWriterSettings to use
   * @return create MATSim writer
   */
  public static MatsimZoningWriter create(MatsimZoningWriterSettings zoningWriterSettings, MatsimNetworkWriterSettings networkWriterSettings) {
    return new MatsimZoningWriter(zoningWriterSettings, networkWriterSettings);    
  }  
   
  
}
