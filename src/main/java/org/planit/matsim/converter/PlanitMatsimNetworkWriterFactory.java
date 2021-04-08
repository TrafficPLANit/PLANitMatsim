package org.planit.matsim.converter;

import org.planit.utils.locale.CountryNames;

/**
 * Factory for creating PLANitMatsimWriters
 * @author markr
 *
 */
public class PlanitMatsimNetworkWriterFactory {
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @return create matsim writer
   */
  public static PlanitMatsimNetworkWriter create(String outputDirectory) {
    return create(outputDirectory, CountryNames.GLOBAL);    
  }
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return create matsim writer
   */
  public static PlanitMatsimNetworkWriter create(String outputDirectory, String countryName) {
    return create(outputDirectory, new PlanitMatsimNetworkWriterSettings(countryName));    
  }  
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param networkSettings to use
   * @return create matsim writer
   */
  public static PlanitMatsimNetworkWriter create(String outputDirectory, PlanitMatsimNetworkWriterSettings networkSettings) {
    return new PlanitMatsimNetworkWriter(outputDirectory, networkSettings);    
  }  
    
}