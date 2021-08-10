package org.planit.matsim.converter;

import org.planit.utils.locale.CountryNames;

/**
 * Factory for creating PLANitMatsimIntermodalWriters
 * @author markr
 *
 */
public class MatsimIntermodalWriterFactory {
  
  /** Create a PLANitMatsimIntermodalWriter which persists PLANit networks and pt infrastructure in MATSIM network format, using all defaults, 
   * requires the user to set output directory afterwards
   * 
   * @return created MATSim writer
   */
  public static MatsimIntermodalWriter create() {
    return create(null, CountryNames.WORLD);    
  }  
  
  /** Create a PLANitMatsimIntermodalWriter which persists PLANit networks and pt infrastructure in MATSIM network format
   * 
   * @param outputDirectory to use
   * @return created MATSim writer
   */
  public static MatsimIntermodalWriter create(String outputDirectory) {
    return create(outputDirectory, CountryNames.WORLD);    
  }
  
  /** Create a PLANitMatsimWriter which persists PLANit networks and pt infrastructure in MATSIM network format
   * 
   * @param outputDirectory to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return created MATSim writer
   */
  public static MatsimIntermodalWriter create(String outputDirectory, String countryName) {
    MatsimIntermodalWriterSettings settings= new MatsimIntermodalWriterSettings(outputDirectory, countryName);
    return new MatsimIntermodalWriter(settings);    
  }

  /** create  a PLANitMatsimWriter which persists PLANit networks and pt infrastructure in MATSIM network format using the network and zoning
   * settings provided
   * @param networkWriterSettings to use
   * @param zoningWriterSettings to use
   * @return created MATSim writer
   */
  public static MatsimIntermodalWriter create(MatsimNetworkWriterSettings networkWriterSettings, MatsimZoningWriterSettings zoningWriterSettings) {
    return new MatsimIntermodalWriter(new MatsimIntermodalWriterSettings(networkWriterSettings, zoningWriterSettings));
  }  
      
}
