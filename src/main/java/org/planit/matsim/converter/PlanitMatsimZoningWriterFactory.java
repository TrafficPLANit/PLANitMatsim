package org.planit.matsim.converter;

import org.planit.network.macroscopic.MacroscopicNetwork;

/**
 * Factory for creating PLANitMatsim zoning writers.
 * 
 * @author markr
 *
 */
public class PlanitMatsimZoningWriterFactory {
  
  /** Create a PLANitMatsimZoningWriter (pt output) with defaults. It is expected the user sets the appropriate properties
   * afterwards as required for this particular type of writer
   * 
   * @param networkWriterSettings to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create Matsim zoning (pt) writer
   */
  public static PlanitMatsimZoningWriter create(PlanitMatsimNetworkWriterSettings networkWriterSettings, MacroscopicNetwork referenceNetwork) {
    return create(new PlanitMatsimZoningWriterSettings(), networkWriterSettings, referenceNetwork);    
  }   
      
  /** Create a PLANitMAtsimWriter
   * 
   * @param zoningWriterSettings to use
   * @param networkWriterSettings to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create matsim writer
   */
  public static PlanitMatsimZoningWriter create(PlanitMatsimZoningWriterSettings zoningWriterSettings, PlanitMatsimNetworkWriterSettings networkWriterSettings, MacroscopicNetwork referenceNetwork) {
    return new PlanitMatsimZoningWriter(zoningWriterSettings, networkWriterSettings, referenceNetwork);    
  }  
   
  
}
