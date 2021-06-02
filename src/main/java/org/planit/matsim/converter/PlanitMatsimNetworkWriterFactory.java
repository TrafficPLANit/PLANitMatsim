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
   * @return created MATSim writer
   */
  public static PlanitMatsimNetworkWriter create() {
    return new PlanitMatsimNetworkWriter();    
  }  
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @return createMATSim MATSim writer
   */
  public static PlanitMatsimNetworkWriter create(String outputDirectory) {
    return create(outputDirectory, CountryNames.GLOBAL);    
  }
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return created MATSim writer
   */
  public static PlanitMatsimNetworkWriter create(String outputDirectory, String countryName) {
    return create(new PlanitMatsimNetworkWriterSettings(outputDirectory, countryName));    
  }  
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param networkSettings to use
   * @return created MATSim writer
   */
  public static PlanitMatsimNetworkWriter create(PlanitMatsimNetworkWriterSettings networkSettings) {
    return new PlanitMatsimNetworkWriter(networkSettings);    
  }  
    
}
