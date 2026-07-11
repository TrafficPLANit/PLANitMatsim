package org.goplanit.matsim.converter;

import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.network.MacroscopicNetwork;

/**
 * Factory for creating MatsimZoningWriters.
 * 
 * @author markr
 *
 */
class MatsimZoningWriterFactory {

  /** Create a MatsimZoningWriter (pt output) with defaults. It is expected the user sets the appropriate
   * properties afterward as required for this particular type of writer
   * 
   * @param networkWriterSettings to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create MATSim zoning (pt) writer
   */
  static MatsimZoningWriter create(
      MacroscopicNetwork referenceNetwork, MatsimNetworkWriterSettings networkWriterSettings) {
    return create(
        new MatsimZoningWriterSettings(networkWriterSettings),
        referenceNetwork);
  }   
      
  /** Create a MatsimZoningWriter
   * 
   * @param zoningWriterSettings to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create MATSim writer
   */
  static MatsimZoningWriter create(
      MatsimZoningWriterSettings zoningWriterSettings,
      MacroscopicNetwork referenceNetwork) {
    return new MatsimZoningWriter(zoningWriterSettings, referenceNetwork);
  }  
   
  
}
