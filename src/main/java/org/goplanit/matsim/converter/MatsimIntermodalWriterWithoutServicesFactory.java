package org.goplanit.matsim.converter;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.zoning.Zoning;

/**
 * Factory for creating PLANitMatsimIntermodalWriters that creates a writer that only supports #{@link MatsimIntermodalWriter#write(MacroscopicNetwork, Zoning)} but not
 * #{@link MatsimIntermodalWriter#writeWithServices(MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices)}
 *
 * @author markr
 *
 */
public class MatsimIntermodalWriterWithoutServicesFactory {

  private static boolean SUPPORT_PT_SERVICES = false;
  
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
    MatsimIntermodalWriterSettings settings= new MatsimIntermodalWriterSettings(outputDirectory, countryName, SUPPORT_PT_SERVICES);
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
