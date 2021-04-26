package org.planit.matsim.converter;

import org.planit.converter.ConverterWriterSettings;
import org.planit.utils.locale.CountryNames;

/**
 * Settings specific to writing the intermodal related outputs in Matsim format (network and pt)
 * 
 * @author markr
 *
 */
public class PlanitMatsimIntermodalWriterSettings implements ConverterWriterSettings {
  
  /** the network settings to use */
  protected final PlanitMatsimNetworkWriterSettings networkSettings;
  
  /** the zoning settings to use */
  protected final PlanitMatsimZoningWriterSettings zoningSettings;  
  
  /**
   * Default constructor 
   */
  public PlanitMatsimIntermodalWriterSettings() {
    this(null, CountryNames.GLOBAL);
  }
      
  /**
   * Constructor 
   * @param outputDirectory to use
   * @param countryName to use
   */
  public PlanitMatsimIntermodalWriterSettings(final String outputDirectory, final String countryName) {
    this(new PlanitMatsimNetworkWriterSettings(outputDirectory, countryName), new PlanitMatsimZoningWriterSettings(outputDirectory, countryName));
  }  
  
  /**
   * Constructor 
   * @param outputDirectory to use
   * @param countryName to use
   * @param networkOutputFileName to use
   * @param ptOutputFileName to use
   */
  public PlanitMatsimIntermodalWriterSettings(final String outputDirectory, final String countryName, final String networkOutputFileName, final String ptOutputFileName) {
    this(
        new PlanitMatsimNetworkWriterSettings(outputDirectory, networkOutputFileName, countryName), 
        new PlanitMatsimZoningWriterSettings(outputDirectory, ptOutputFileName, countryName));
  }    
  
  /**
   * Constructor
   * 
   *  @param network writer settings to use
   *  @param zoning writer settings to use
   */
  public PlanitMatsimIntermodalWriterSettings(final PlanitMatsimNetworkWriterSettings networkWriterSettings, final PlanitMatsimZoningWriterSettings zoningWriterSettings) {    
    this.networkSettings = networkWriterSettings;
    this.zoningSettings = zoningWriterSettings;
  }  
  


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    getNetworkSettings().reset();
    getZoningSettings().reset();
  }

  /** Collect zoning settings
   * @return zoning settings
   */
  public PlanitMatsimZoningWriterSettings getZoningSettings() {
    return zoningSettings;
  }

  /** Collect network settings
   * @return network settings
   */
  public  PlanitMatsimNetworkWriterSettings getNetworkSettings() {
    return networkSettings;
  }   
 
}
