package org.planit.matsim.converter;

import org.planit.network.macroscopic.MacroscopicNetwork;

/**
 * Factory for creating PLANitOSM zoning Readers. For now OSM zoning reader require the presence of an OSM network reader as
 * those settings and subsequent reference network (that it is expected to populate) are inputs to the factory method.
 * 
 * @author markr
 *
 */
public class PlanitMatsimZoningWriterFactory {    
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param networkWriterSettings to use
   * @return create osm reader
   */
  public static PlanitMatsimZoningWriter create(String outputDirectory, MacroscopicNetwork referenceNetwork, PlanitMatsimNetworkWriterSettings networkWriterSettings) {
    return new PlanitMatsimZoningWriter(outputDirectory, referenceNetwork, networkWriterSettings);    
  }  
   
  
}
