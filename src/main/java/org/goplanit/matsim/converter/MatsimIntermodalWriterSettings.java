package org.goplanit.matsim.converter;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.matsim.util.MatsimWriterSettingsSchema;
import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.settings.SettingsSchema;
import org.goplanit.utils.settings.YamlWriteSettingsSupport;
import org.goplanit.utils.settings.annotations.DelegatedToSyncedInternalSettingsKeys;
import org.goplanit.utils.settings.annotations.SettingsFileReference;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;

import java.text.DecimalFormat;
import java.util.logging.Logger;

/**
 * Settings specific to writing the intermodal related outputs in MATSim format, i.e., network and pt
 * infrastructure and/or services
 * <p>
 * It holds the settings of the three writers the intermodal writer uses: network, zoning and public transport
 * services. Its setters pass a value all three share, such as the output directory, to each of them.
 * </p>
 * <p>
 * Its settings file names the settings files of the writers it holds, each relative to it, and holds none of their
 * keys. A part takes one file or a list of files, applied in order, a later file winning, so the keys the parts share
 * can be given once, in a file each part names first, such as
 * </p>
 * <pre>
 * planitMatsimWriterSettings: 1
 * network: [matsim_output.yaml, matsim_network.yaml]
 * zoning: matsim_output.yaml
 * ptServices: matsim_output.yaml
 * </pre>
 * <p>
 * A writer whose file is not named keeps its defaults. Written out, it writes its file and one file per writer next
 * to it, named after the key.
 * </p>
 *
 * @author markr
 *
 */
public class MatsimIntermodalWriterSettings implements ConverterWriterSettings, YamlWriteSettingsSupport {

  private static final Logger LOGGER = Logger.getLogger(MatsimIntermodalWriterSettings.class.getCanonicalName());
  
  /** the network and zoning settings to use in case we are writing without services */
  protected final MatsimNetworkWriterSettings networkSettings;

  /** zoning settings to use in case we do not have services but we do have pt infrastructure like platforms),
   * mutually exclusive to
   * ptServicesSettings */
  protected final MatsimZoningWriterSettings zoningSettings;

  /** the routed services settings to use, mutual exclusive to zoning settings */
  protected final MatsimPtServicesWriterSettings ptServicesSettings;

  /**
   * Constructor taking the settings of each part
   *
   *  @param networkWriterSettings writer settings to use
   *  @param zoningWriterSettings writer settings to use
   * @param ptServicesSettings writer settings to use
   */
  protected MatsimIntermodalWriterSettings(
      final MatsimNetworkWriterSettings networkWriterSettings,
      final MatsimZoningWriterSettings zoningWriterSettings,
      final MatsimPtServicesWriterSettings ptServicesSettings) {
    this.networkSettings = networkWriterSettings;
    this.zoningSettings = zoningWriterSettings;
    this.ptServicesSettings = ptServicesSettings;
  }

  /**
   * Default constructor, using the global country
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
    this(
        outputDirectory,
        countryName,
        MatsimNetworkWriterSettings.DEFAULT_NETWORK_FILE_NAME,
        PlanitMatsimWriterSettings.DEFAULT_TRANSIT_SCHEDULE_FILE_NAME);
  }  
  
  /**
   * Constructor 
   * 
   * @param outputDirectory to use
   * @param countryName to use
   * @param networkOutputFileName to use
   * @param ptOutputFileName to use
   */
  public MatsimIntermodalWriterSettings(
      final String outputDirectory,
      final String countryName,
      final String networkOutputFileName,
      final String ptOutputFileName) {

      this(new MatsimNetworkWriterSettings(outputDirectory, networkOutputFileName, countryName),
          new MatsimZoningWriterSettings(outputDirectory, ptOutputFileName, countryName),
          new MatsimPtServicesWriterSettings(outputDirectory, ptOutputFileName, countryName));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level){
    LOGGER.info(LoggingUtils.settingsHeader("MATSim intermodal writer settings"));
    networkSettings.logSettings(level);
    zoningSettings.logSettings(level);
    ptServicesSettings.logSettings(level);
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
  @SettingsFileReference(key = "zoning", level = SettingsKeyLevel.UNCOMMON,
      description = "The settings file of the zoning writer, relative to this settings file")
  public MatsimZoningWriterSettings getZoningSettings() {
    return zoningSettings;
  }

  /** Collect routedServicesWriterSettings
   * @return routedServicesWriterSettings
   */
  @SettingsFileReference(key = "ptServices", level = SettingsKeyLevel.UNCOMMON,
      description = "The settings file of the public transport services writer, relative to this settings file")
  public MatsimPtServicesWriterSettings getPtServicesSettings() {
    return ptServicesSettings;
  }

  /** Collect network settings
   * @return network settings
   */
  @SettingsFileReference(key = "network", level = SettingsKeyLevel.STANDARD,
      description = "The settings file of the network writer, relative to this settings file")
  public  MatsimNetworkWriterSettings getNetworkSettings() {
    return networkSettings;
  }


  /**
   * Set the output directory of the network, zoning and services settings
   *
   * @param outputDirectory to use
   */
  @DelegatedToSyncedInternalSettingsKeys("outputDirectory")
  public void setOutputDirectory(String outputDirectory) {
    getNetworkSettings().setOutputDirectory(outputDirectory);
    getZoningSettings().setOutputDirectory(outputDirectory);
    getPtServicesSettings().setOutputDirectory(outputDirectory);
  }

  /**
   * Set the country of the network, zoning and services settings
   *
   * @param countryName to use
   */
  @DelegatedToSyncedInternalSettingsKeys("country")
  public void setCountry(String countryName) {
      getNetworkSettings().setCountry(countryName);
      getZoningSettings().setCountry(countryName);
      getPtServicesSettings().setCountry(countryName);
  }


  /**
   * Set the destination coordinate reference system of the network, zoning and services settings
   *
   * @param destinationCoordinateReferenceSystem to use
   */
  @DelegatedToSyncedInternalSettingsKeys("destinationCrs")
  public void setDestinationCoordinateReferenceSystem(CoordinateReferenceSystem destinationCoordinateReferenceSystem) {
    getNetworkSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    getZoningSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
    getPtServicesSettings().setDestinationCoordinateReferenceSystem(destinationCoordinateReferenceSystem);
  }

  /**
   * Set the decimal format of the network, zoning and services settings
   *
   * @param decimalFormat to use
   */
  @DelegatedToSyncedInternalSettingsKeys("decimalFormat")
  public void setDecimalFormat(DecimalFormat decimalFormat) {
    getNetworkSettings().setDecimalFormat(decimalFormat);
    getZoningSettings().setDecimalFormat(decimalFormat);
    getPtServicesSettings().setDecimalFormat(decimalFormat);
  }

  /**
   * Set whether the network, zoning and services files are written as GZip
   *
   * @param flag when true, written as GZip
   */
  @DelegatedToSyncedInternalSettingsKeys("writeAsGZip")
  public void setWriteAsGZip(boolean flag) {
    getNetworkSettings().setWriteAsGZip(flag);
    getZoningSettings().setWriteAsGZip(flag);
    getPtServicesSettings().setWriteAsGZip(flag);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SettingsSchema getSettingsSchema() {
    return MatsimWriterSettingsSchema.SCHEMA;
  }
}
