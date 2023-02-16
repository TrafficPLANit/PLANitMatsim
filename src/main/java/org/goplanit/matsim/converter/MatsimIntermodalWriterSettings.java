package org.goplanit.matsim.converter;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Settings specific to writing the intermodal related outputs in Matsim format (network and pt)
 * 
 * @author markr
 *
 */
public class MatsimIntermodalWriterSettings implements ConverterWriterSettings {
  
  /** the network settings to use */
  protected final MatsimNetworkWriterSettings networkSettings;
  
  /** the zoning settings to use, mutual exclusive to pt routed services settings */
  protected final MatsimZoningWriterSettings zoningSettings;

  /** the routed services settings to use, mutual exclusive to zoning settings */
  protected final MatsimPublicTransportServicesWriterSettings routedServicesWriterSettings;

  /**
   * Constructor for persisting only PT infrastructure via zoning settings
   *
   *  @param networkWriterSettings writer settings to use
   *  @param zoningWriterSettings writer settings to use
   *  @param routedServicesWriterSettings writer settings to use
   */
  protected MatsimIntermodalWriterSettings(final MatsimNetworkWriterSettings networkWriterSettings, final MatsimZoningWriterSettings zoningWriterSettings, final MatsimPublicTransportServicesWriterSettings routedServicesWriterSettings) {
    this.networkSettings = networkWriterSettings;
    this.zoningSettings = zoningWriterSettings;
    this.routedServicesWriterSettings = routedServicesWriterSettings;
  }

  /**
   * Constructor 
   * 
   * @param outputDirectory to use
   * @param countryName to use
   * @param supportServices when true routed service settings are created, otherwise infrastructure only zoning settings
   */
  public MatsimIntermodalWriterSettings(final String outputDirectory, final String countryName, boolean supportServices) {
    this(outputDirectory, countryName, MatsimNetworkWriterSettings.DEFAULT_NETWORK_FILE_NAME, PlanitMatsimWriterSettings.DEFAULT_TRANSIT_SCHEDULE_FILE_NAME, supportServices);
  }  
  
  /**
   * Constructor 
   * 
   * @param outputDirectory to use
   * @param countryName to use
   * @param networkOutputFileName to use
   * @param ptOutputFileName to use
   * @param supportServices when true routed service settings are created, otherwise infrastructure only zoning settings
   */
  public MatsimIntermodalWriterSettings(final String outputDirectory, final String countryName, final String networkOutputFileName, final String ptOutputFileName, boolean supportServices) {
    this(
        new MatsimNetworkWriterSettings(outputDirectory, networkOutputFileName, countryName),
        supportServices ? null : new MatsimZoningWriterSettings(outputDirectory, ptOutputFileName, countryName),
        supportServices ? new MatsimPublicTransportServicesWriterSettings(outputDirectory, ptOutputFileName, countryName) : null);
  }    
  
  /**
   * Constructor for persisting only PT infrastructure via zoning settings
   * 
   *  @param networkWriterSettings writer settings to use
   *  @param zoningWriterSettings writer settings to use
   */
  public MatsimIntermodalWriterSettings(final MatsimNetworkWriterSettings networkWriterSettings, final MatsimZoningWriterSettings zoningWriterSettings) {
    this(networkWriterSettings, zoningWriterSettings, null);
  }

  /**
   * Constructor for persisting both PT infrastructure and services via routedServicesSettings
   *
   *  @param networkWriterSettings writer settings to use
   *  @param routedServicesWriterSettings writer settings to use
   */
  public MatsimIntermodalWriterSettings(final MatsimNetworkWriterSettings networkWriterSettings, final MatsimPublicTransportServicesWriterSettings routedServicesWriterSettings) {
    this(networkWriterSettings, null, routedServicesWriterSettings);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    getNetworkSettings().reset();
    if(getZoningSettings() != null){
      getZoningSettings().reset();
    }
    if(getRoutedServicesSettings() != null){
      getRoutedServicesSettings().reset();
    }
  }

  /** Collect zoning settings (if present)
   * @return zoning settings
   */
  public MatsimZoningWriterSettings getZoningSettings() {
    return zoningSettings;
  }

  /** Collect routedServicesWriterSettings
   * @return routedServicesWriterSettings
   */
  public MatsimPublicTransportServicesWriterSettings getRoutedServicesSettings() {
    return routedServicesWriterSettings;
  }

  /** Collect network settings
   * @return network settings
   */
  public  MatsimNetworkWriterSettings getNetworkSettings() {
    return networkSettings;
  }   
  

  /** set the output directory to use on both network and zoning settings
   * @param outputDirectory to use
   */
  public void setOutputDirectory(String outputDirectory) {
    getNetworkSettings().setOutputDirectory(outputDirectory);
    if(getZoningSettings() != null){
      getZoningSettings().setOutputDirectory(outputDirectory);
    }
    if(getRoutedServicesSettings() != null){
      getRoutedServicesSettings().setOutputDirectory(outputDirectory);
    }
  }
  
  /** set the country to use on both network and zoning settings
   * @param countryName to use
   */
  public void setCountry(String countryName) {
    getNetworkSettings().setCountry(countryName);
    if(getZoningSettings() != null){
      getZoningSettings().setCountry(countryName);
    }
    if(getRoutedServicesSettings() != null){
      getRoutedServicesSettings().setCountry(countryName);
    }
  }  
  
  /** Explicitly set a particular crs for writing geometries for both zoning and network
   * @param destinationCoordinateReferenceSystem to use
   */
  public void setDestinationCoordinateReferenceSystem(CoordinateReferenceSystem destinationCoordinateReferenceSystem) {
    getNetworkSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    if(getZoningSettings() != null){
      getZoningSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    }
    if(getRoutedServicesSettings() != null){
      getRoutedServicesSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    }
  }      
 
}
