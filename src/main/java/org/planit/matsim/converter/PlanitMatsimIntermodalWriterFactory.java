package org.planit.matsim.converter;

import org.planit.utils.locale.CountryNames;

/**
 * Factory for creating PLANitMatsimIntermodalWriters
 * @author markr
 *
 */
public class PlanitMatsimIntermodalWriterFactory {
  
  /** Create a PLANitMatsimIntermodalWriter which persists PLANit networks and pt infrastructure in MATSIM network format, using all defaults, 
   * requires the user to set output directory afterwards
   * 
   * @return created MATSim writer
   */
  public static PlanitMatsimIntermodalWriter create() {
    return create(null, CountryNames.WORLD);    
  }  
  
  /** Create a PLANitMatsimIntermodalWriter which persists PLANit networks and pt infrastructure in MATSIM network format
   * 
   * @param outputDirectory to use
   * @return created MATSim writer
   */
  public static PlanitMatsimIntermodalWriter create(String outputDirectory) {
    return create(outputDirectory, CountryNames.WORLD);    
  }
  
  /** Create a PLANitMatsimWriter which persists PLANit networks and pt infrastructure in MATSIM network format
   * 
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return created MATSim writer
   */
  public static PlanitMatsimIntermodalWriter create(String outputDirectory, String countryName) {
    PlanitMatsimIntermodalWriterSettings settings= new PlanitMatsimIntermodalWriterSettings(outputDirectory, countryName);
    return new PlanitMatsimIntermodalWriter(settings);    
  }

  /** create  a PLANitMatsimWriter which persists PLANit networks and pt infrastructure in MATSIM network format using the network and zoning
   * settings provided
   * @param networkWriterSettings to use
   * @param zoningWriterSettings to use
   * @return created MATSim writer
   */
  public static PlanitMatsimIntermodalWriter create(PlanitMatsimNetworkWriterSettings networkWriterSettings, PlanitMatsimZoningWriterSettings zoningWriterSettings) {
    return new PlanitMatsimIntermodalWriter(new PlanitMatsimIntermodalWriterSettings(networkWriterSettings, zoningWriterSettings));
  }  
      
}
