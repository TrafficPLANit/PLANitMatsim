package org.planit.matsim.converter;

import org.planit.utils.locale.CountryNames;

/**
 * Factory for creating PLANitMatsimIntermodalWriters
 * @author markr
 *
 */
public class PlanitMatsimIntermodalWriterFactory {
  
  /** Create a PLANitMatsimIntermodalWriter which persists PLANit networks and pt infrastructure in MATSIM network format
   * 
   * @param outputDirectory to use
   * @return create matsim writer
   */
  public static PlanitMatsimIntermodalWriter create(String outputDirectory) {
    return create(outputDirectory, CountryNames.WORLD);    
  }
  
  /** Create a PLANitMatsimWriter which persists PLANit networks and pt infrastructure in MATSIM network format
   * 
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return create matsim writer
   */
  public static PlanitMatsimIntermodalWriter create(String outputDirectory, String countryName) {
    return new PlanitMatsimIntermodalWriter(outputDirectory, new PlanitMatsimNetworkWriterSettings(countryName));    
  }  
  
  /** Create a PLANitMatsimWriter which persists PLANit networks and pt infrastructure in MATSIM network format
   * 
   * @param outputDirectory to use
   * @param networkSettings to use
   * @return create matsim writer
   */
  public static PlanitMatsimIntermodalWriter create(String outputDirectory, PlanitMatsimNetworkWriterSettings networkSettings) {
    return new PlanitMatsimIntermodalWriter(outputDirectory, networkSettings);    
  }   
    
}
