package org.goplanit.matsim.converter;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.utils.locale.CountryNames;
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
  
  /** the zoning settings to use */
  protected final MatsimZoningWriterSettings zoningSettings;  
  
  /**
   * Default constructor 
   */
  public MatsimIntermodalWriterSettings() {
    this(null, CountryNames.GLOBAL);
  }
      
  /**
   * Constructor 
   * 
   * @param outputDirectory to use
   * @param countryName to use
   */
  public MatsimIntermodalWriterSettings(final String outputDirectory, final String countryName) {
    this(new MatsimNetworkWriterSettings(outputDirectory, countryName), new MatsimZoningWriterSettings(outputDirectory, countryName));
  }  
  
  /**
   * Constructor 
   * 
   * @param outputDirectory to use
   * @param countryName to use
   * @param networkOutputFileName to use
   * @param ptOutputFileName to use
   */
  public MatsimIntermodalWriterSettings(final String outputDirectory, final String countryName, final String networkOutputFileName, final String ptOutputFileName) {
    this(
        new MatsimNetworkWriterSettings(outputDirectory, networkOutputFileName, countryName), 
        new MatsimZoningWriterSettings(outputDirectory, ptOutputFileName, countryName));
  }    
  
  /**
   * Constructor
   * 
   *  @param networkWriterSettings writer settings to use
   *  @param zoningWriterSettings writer settings to use
   */
  public MatsimIntermodalWriterSettings(final MatsimNetworkWriterSettings networkWriterSettings, final MatsimZoningWriterSettings zoningWriterSettings) {    
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
  public MatsimZoningWriterSettings getZoningSettings() {
    return zoningSettings;
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
    getZoningSettings().setOutputDirectory(outputDirectory);
  }
  
  /** set the country to use on both network and zoning settings
   * @param countryName to use
   */
  public void setCountry(String countryName) {
    getNetworkSettings().setCountry(countryName);
    getZoningSettings().setCountry(countryName);
  }  
  
  /** Explicitly set a particular crs for writing geometries for both zoning and network
   * @param destinationCoordinateReferenceSystem to use
   */
  public void setDestinationCoordinateReferenceSystem(CoordinateReferenceSystem destinationCoordinateReferenceSystem) {
    getNetworkSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    getZoningSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
  }      
 
}
