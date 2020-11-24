package org.planit.matsim.converter;

/**
 * Factory for creating PLANitMatsimWriters
 * @author markr
 *
 */
public class PlanitMatsimWriterFactory {
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @return create matsim writer
   */
  public static PlanitMatsimWriter createWriter(String outputDirectory) {
    return new PlanitMatsimWriter(outputDirectory);    
  }
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return create matsim writer
   */
  public static PlanitMatsimWriter createWriter(String outputDirectory, String countryName) {
    return new PlanitMatsimWriter(outputDirectory, countryName);    
  }  
    
}
