package org.goplanit.matsim.converter;

import org.goplanit.utils.locale.CountryNames;

/**
 * Factory for creating PLANitMatsimWriters
 * @author markr
 *
 */
public class MatsimNetworkWriterFactory {
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format in current working directory
   * 
   * @return created MATSim writer
   */
  public static MatsimNetworkWriter create() {
    return create(".");
  }  
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @return createMATSim MATSim writer
   */
  public static MatsimNetworkWriter create(String outputDirectory) {
    return create(outputDirectory, CountryNames.GLOBAL);    
  }
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return created MATSim writer
   */
  public static MatsimNetworkWriter create(String outputDirectory, String countryName) {
    return create(new MatsimNetworkWriterSettings(outputDirectory, countryName));    
  }  
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param networkSettings to use
   * @return created MATSim writer
   */
  public static MatsimNetworkWriter create(MatsimNetworkWriterSettings networkSettings) {
    return new MatsimNetworkWriter(networkSettings);    
  }  
    
}
