package org.goplanit.matsim.converter;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.zoning.Zoning;

/**
 * Factory for creating PLANitMatsimIntermodalWriters that creates a writer that supports both
 * #{@link MatsimIntermodalWriter#write(MacroscopicNetwork, Zoning)} as well as
 * #{@link MatsimIntermodalWriter#writeWithServices(MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices)}
 *
 * @author markr
 *
 */
public class MatsimIntermodalWriterFactory {

  /**
   * Constructor
   */
  public MatsimIntermodalWriterFactory(){}

  /** Create a MatsimIntermodalWriter which persists PLANit networks and their pt infrastructure and services
   * in MATSIM network format, using all defaults,
   * requires the user to set output directory afterwards
   * 
   * @return created MATSim writer
   */
  public static MatsimIntermodalWriter create() {
    return create(null, CountryNames.WORLD);    
  }  
  
  /** Create a MatsimIntermodalWriter which persists PLANit networks and their pt infrastructure and services
   * in MATSim network format  with default mode mapping
   * 
   * @param outputDirectory to use
   * @return created MATSim writer
   */
  public static MatsimIntermodalWriter create(String outputDirectory) {
    return create(outputDirectory, CountryNames.WORLD);    
  }
  
  /** Create a PLANitMatsimWriter which persists PLANit networks and their pt infrastructure (and possibly services)
   * in MATSim network format with default mode mapping
   * 
   * @param outputDirectory to use
   * @param countryName country which the data represents, used to determine defaults in case not specifically
   *                    specified, when left blank global defaults will be used based on a right hand driving approach
   * @return created MATSim writer
   */
  public static MatsimIntermodalWriter create(String outputDirectory, String countryName) {
    MatsimIntermodalWriterSettings settings = new MatsimIntermodalWriterSettings(outputDirectory, countryName);
    return new MatsimIntermodalWriter(settings);    
  }

  /** create  a MatsimIntermodalWriter which persists PLANit networks and their pt infrastructure
   * (and possibly services) in MATSim network format using the settings provided.
   *
   * @param settings to use
   * @return created MATSim writer
   */
  public static MatsimIntermodalWriter create(MatsimIntermodalWriterSettings settings) {
    return new MatsimIntermodalWriter(settings);
  }  
      
}
