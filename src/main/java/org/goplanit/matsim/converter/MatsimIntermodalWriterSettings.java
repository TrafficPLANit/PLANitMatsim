package org.goplanit.matsim.converter;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.utils.misc.Pair;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Settings specific to writing the intermodal related outputs in Matsim format (network and pt)
 * 
 * @author markr
 *
 */
public class MatsimIntermodalWriterSettings implements ConverterWriterSettings {
  
  /** the network and zoning settings to use in case we are writing without services */
  protected final Pair<MatsimNetworkWriterSettings, MatsimZoningWriterSettings> noServicesSettings;

  /** the routed services settings to use, mutual exclusive to zoning settings */
  protected final MatsimPtServicesWriterSettings matsimPtServicesWriterSettings;

  /**
   * Constructor for persisting only PT infrastructure via zoning settings
   *
   *  @param networkWriterSettings writer settings to use
   *  @param zoningWriterSettings writer settings to use
   *  @param routedServicesWriterSettings writer settings to use
   */
  protected MatsimIntermodalWriterSettings(final MatsimNetworkWriterSettings networkWriterSettings, final MatsimZoningWriterSettings zoningWriterSettings, final MatsimPtServicesWriterSettings routedServicesWriterSettings) {
    noServicesSettings = Pair.of(networkWriterSettings, zoningWriterSettings);
    this.matsimPtServicesWriterSettings = routedServicesWriterSettings;
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
    if(supportServices){
      this.matsimPtServicesWriterSettings = new MatsimPtServicesWriterSettings(outputDirectory, ptOutputFileName, countryName);
      this.noServicesSettings = Pair.of(null, null);
    }else{
      this.matsimPtServicesWriterSettings = null;
      this.noServicesSettings = Pair.of(
          new MatsimNetworkWriterSettings(outputDirectory, networkOutputFileName, countryName),
          new MatsimZoningWriterSettings(outputDirectory, ptOutputFileName, countryName));
    }
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
   *  @param routedServicesWriterSettings writer settings to use
   */
  public MatsimIntermodalWriterSettings(final MatsimPtServicesWriterSettings routedServicesWriterSettings) {
    this.matsimPtServicesWriterSettings = routedServicesWriterSettings;
    this.noServicesSettings = Pair.of(null, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    if(noServicesSettings.first() != null){
      noServicesSettings.first().reset();
    }
    if(noServicesSettings.second() != null){
      noServicesSettings.second().reset();
    }
    if(getPtServicesSettings() != null){
      getPtServicesSettings().reset();
    }
  }

  /** Collect zoning settings (if present)
   * @return zoning settings
   */
  public MatsimZoningWriterSettings getZoningSettings() {
    // when writing with services all settings are on ptservices settings, otherwise these are null and they are separately stored
    return noServicesSettings != null && noServicesSettings.secondNotNull() ? noServicesSettings.second() : getPtServicesSettings().getZoningSettings();
  }

  /** Collect routedServicesWriterSettings
   * @return routedServicesWriterSettings
   */
  public MatsimPtServicesWriterSettings getPtServicesSettings() {
    return matsimPtServicesWriterSettings;
  }

  /** Collect network settings
   * @return network settings
   */
  public  MatsimNetworkWriterSettings getNetworkSettings() {
    // when writing with services all settings are on ptservices settings, otherwise these are null and they are separately stored
    return noServicesSettings != null && noServicesSettings.firstNotNull() ? noServicesSettings.first() : getPtServicesSettings().getNetworkSettings();
  }


  /** set the output directory to use on both network and zoning settings
   * @param outputDirectory to use
   */
  public void setOutputDirectory(String outputDirectory) {
    if(getNetworkSettings() != null) {
      getNetworkSettings().setOutputDirectory(outputDirectory);
    }
    if(getZoningSettings() != null){
      getZoningSettings().setOutputDirectory(outputDirectory);
    }
    if(getPtServicesSettings() != null){
      getPtServicesSettings().setOutputDirectory(outputDirectory);
    }
  }
  
  /** set the country to use on both network and zoning settings
   * @param countryName to use
   */
  public void setCountry(String countryName) {
    if(getNetworkSettings() != null) {
      getNetworkSettings().setCountry(countryName);
    }
    if(getZoningSettings() != null){
      getZoningSettings().setCountry(countryName);
    }
    if(getPtServicesSettings() != null){
      getPtServicesSettings().setCountry(countryName);
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
    if(getPtServicesSettings() != null){
      getPtServicesSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    }
  }      
 
}
