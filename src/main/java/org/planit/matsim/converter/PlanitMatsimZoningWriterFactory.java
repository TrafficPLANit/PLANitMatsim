package org.planit.matsim.converter;

import org.planit.network.macroscopic.MacroscopicNetwork;

/**
 * Factory for creating PLANitMatsim zoning writers.
 * 
 * @author markr
 *
 */
public class PlanitMatsimZoningWriterFactory {
    
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param zoningWriterSettings to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param networkWriterSettings to use
   * @return create osm reader
   */
  public static PlanitMatsimZoningWriter create(PlanitMatsimZoningWriterSettings zoningWriterSettings, MacroscopicNetwork referenceNetwork, PlanitMatsimNetworkWriterSettings networkWriterSettings) {
    return new PlanitMatsimZoningWriter(zoningWriterSettings, referenceNetwork, networkWriterSettings);    
  }  
   
  
}
