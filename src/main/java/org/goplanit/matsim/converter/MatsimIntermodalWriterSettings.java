package org.goplanit.matsim.converter;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.misc.Pair;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.text.DecimalFormat;

/**
 * Settings specific to writing the intermodal related outputs in MATSim format, i.e., network and pt infrastructure and/or services
 * 
 * @author markr
 *
 */
public class MatsimIntermodalWriterSettings extends PlanitMatsimWriterSettings implements ConverterWriterSettings {
  
  /** the network and zoning settings to use in case we are writing without services */
  protected final MatsimNetworkWriterSettings networkSettings;

  protected final MatsimZoningWriterSettings zoningSettings;

  /** the routed services settings to use, mutual exclusive to zoning settings */
  protected final MatsimPtServicesWriterSettings ptServicesSettings;

  /**
   * Constructor based on settings for zoning and network from which pt services settings will be created (sharing the mode mapping from the network)
   *
   *  @param networkWriterSettings writer settings to use
   *  @param zoningWriterSettings writer settings to use
   */
  protected MatsimIntermodalWriterSettings(final MatsimNetworkWriterSettings networkWriterSettings, final MatsimZoningWriterSettings zoningWriterSettings) {
    this.networkSettings = networkWriterSettings;
    this.zoningSettings = zoningWriterSettings;
    this.ptServicesSettings = new MatsimPtServicesWriterSettings(networkWriterSettings); // shallow copy to share mode mappings between the two, future prepping for option to persist this separately
  }

  /**
   * Constructor 
   * 
   * @param outputDirectory to use
   * @param countryName to use
   */
  public MatsimIntermodalWriterSettings(final String outputDirectory, final String countryName) {
    this(outputDirectory, countryName, MatsimNetworkWriterSettings.DEFAULT_NETWORK_FILE_NAME, PlanitMatsimWriterSettings.DEFAULT_TRANSIT_SCHEDULE_FILE_NAME);
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
      this(new MatsimNetworkWriterSettings(outputDirectory, networkOutputFileName, countryName),
          new MatsimZoningWriterSettings(outputDirectory, ptOutputFileName, countryName));
  }    

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    networkSettings.reset();
    zoningSettings.reset();
    ptServicesSettings.reset();
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
  public MatsimPtServicesWriterSettings getPtServicesSettings() {
    return ptServicesSettings;
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
    getPtServicesSettings().setOutputDirectory(outputDirectory);
  }
  
  /** set the country to use on both network and zoning settings
   * @param countryName to use
   */
  @Override
  public void setCountry(String countryName) {
      getNetworkSettings().setCountry(countryName);
      getZoningSettings().setCountry(countryName);
      getPtServicesSettings().setCountry(countryName);
  }

  
  /** Explicitly set a particular crs for writing geometries for both zoning and network
   * @param destinationCoordinateReferenceSystem to use
   */
  public void setDestinationCoordinateReferenceSystem(CoordinateReferenceSystem destinationCoordinateReferenceSystem) {
    getNetworkSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    getZoningSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    getPtServicesSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
  }

  /** Set number of decimals used in writing coordinates
   *
   * @param decimalFormat format to use
   */
  public void setDecimalFormat(DecimalFormat decimalFormat) {
    getNetworkSettings().setDecimalFormat(decimalFormat);
    getZoningSettings().setDecimalFormat(decimalFormat);
    getPtServicesSettings().setDecimalFormat(decimalFormat);
  }

}
