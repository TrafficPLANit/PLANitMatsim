package org.goplanit.matsim.converter;

import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.network.MacroscopicNetwork;

/**
 * Factory for creating PLANitMatsim zoning writers.
 * 
 * @author markr
 *
 */
class MatsimZoningWriterFactory {

  /** Create a PLANitMatsimZoningWriter (pt output) using provided settings. It is expected the user sets
   * the appropriate properties on those settings as required for this particular type of writer. In case the
   * zoningWriter settings do not yet have the reference network registered, it is registered here on the instance
   *
   * @param zoningWriterSettings to use
   * @param networkWriterSettings to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create MATSim zoning (pt) writer
   */
  static MatsimZoningWriter create(
      MatsimZoningWriterSettings zoningWriterSettings,
      MatsimNetworkWriterSettings networkWriterSettings,
      MacroscopicNetwork referenceNetwork) {
    if(zoningWriterSettings.getReferenceNetwork() == null){
      zoningWriterSettings.setReferenceNetwork(referenceNetwork);
    }
    return create( zoningWriterSettings, networkWriterSettings);
  }

  /** Create a PLANitMatsimZoningWriter (pt output) with defaults. It is expected the user sets the appropriate
   * properties afterwards as required for this particular type of writer
   * 
   * @param networkWriterSettings to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create MATSim zoning (pt) writer
   */
  static MatsimZoningWriter create(
      MatsimNetworkWriterSettings networkWriterSettings, MacroscopicNetwork referenceNetwork) {
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
  static MatsimZoningWriter create(
      MatsimZoningWriterSettings zoningWriterSettings, MatsimNetworkWriterSettings networkWriterSettings) {
    return new MatsimZoningWriter(zoningWriterSettings, networkWriterSettings);    
  }  
   
  
}
