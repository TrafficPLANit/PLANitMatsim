package org.goplanit.matsim.converter.network;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.logging.Logger;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.settings.annotations.NoSettingsKey;

/** Settings for the MATSIM writer
 * 
 * By default the {@link #DEFAULT_ACTIVATED_MODES} are written, each as its default MATSim mode; other predefined modes
 * are written once switched on, and custom modes never.
 * 
 * The CRS used for the writer is based on the CRS defined in the settings, if this is not set, we utilise the
 * CRS corresponding to the provided country, if no country is provided, it will retain the CRS of the network
 * provided. If the network has no CRS an exception will be thrown
 * <p>
 * The keys a settings file can set are on {@link MatsimNetworkWriterYamlKeyedSettings}; the functions filling a link's
 * attributes are set in code only.
 * </p>
 *
 * @author markr
 *
 */
public class MatsimNetworkWriterSettings extends MatsimNetworkWriterYamlKeyedSettings
    implements ConverterWriterSettings {
  
  private static final Logger LOGGER = Logger.getLogger(MatsimNetworkWriterSettings.class.getCanonicalName());    

  /**
   * optional function used to populate the MATSIM link's nt_category field if set
   */
  protected Function<MacroscopicLinkSegment,String> linkNtCategoryfunction = null;

  /**
   * optional function used to populate the MATSIM link's nt_type field if set
   */  
  protected Function<MacroscopicLinkSegment,String> linkNtTypefunction = null;

  /**
   * optional function used to populate the MATSIM link's type field if set
   */    
  protected Function<MacroscopicLinkSegment,String> linkTypefunction = null;

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("MATSim Network Writer Settings"));
    super.logSettings(level);
    /* whether each function is set, not the function itself */
    LOGGER.info(LoggingUtils.settingsValue(
        "Link nt_category function", linkNtCategoryfunction == null ? null : "set", level));
    LOGGER.info(LoggingUtils.settingsValue("Link nt_type function", linkNtTypefunction == null ? null : "set", level));
    LOGGER.info(LoggingUtils.settingsValue("Link type function", linkTypefunction == null ? null : "set", level));
  }

  /** Default constructor, using the global country
   */
  public MatsimNetworkWriterSettings(){
    this(CountryNames.GLOBAL);
  }

  /** constructor
   * @param countryName to use
   */
  public MatsimNetworkWriterSettings(String countryName){
    this(null, countryName);  
  }
  
  /** constructor
   * 
   * @param outputDirectory to use
   * @param countryName to use
   */
  public MatsimNetworkWriterSettings(String outputDirectory, String countryName){
    this(outputDirectory, DEFAULT_NETWORK_FILE_NAME, countryName);  }  
  
  /** constructor
   * 
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  public MatsimNetworkWriterSettings(String outputDirectory, String outputFileName, String countryName){
    super(outputDirectory, outputFileName, countryName);
  }   
  
  // getters-setters

  /**
   * Allow the user to provide their own function on how to populate the nt_category field of a MATSIM link
   * based on the link segment that is provided to it
   * 
   * @param linkNtCategoryfunction to apply
   */
  @NoSettingsKey(reason = "a function cannot be written in a settings file")
  public void setNtCategoryFunction(Function<MacroscopicLinkSegment,String> linkNtCategoryfunction) {
    this.linkNtCategoryfunction = linkNtCategoryfunction;
  }
  
  /**
   * Allow the user to provide their own function on how to populate the nt_type field of a MATSIM link
   * based on the link segment that is provided to it
   * 
   * @param linkNtTypefunction to apply
   */
  @NoSettingsKey(reason = "a function cannot be written in a settings file")
  public void setNtTypeFunction(Function<MacroscopicLinkSegment,String> linkNtTypefunction) {
    this.linkNtTypefunction = linkNtTypefunction;
  }  
  
  /**
   * Allow the user to provide their own function on how to populate the type field of a MATSIM link
   * based on the link segment that is provided to it
   * 
   * @param linkTypeFunction to apply
   */
  @NoSettingsKey(reason = "a function cannot be written in a settings file")
  public void setTypeFunction(Function<MacroscopicLinkSegment,String> linkTypeFunction) {
    this.linkTypefunction = linkTypeFunction;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    this.linkNtCategoryfunction = null;
    this.linkNtTypefunction = null;
    this.linkTypefunction = null;
  }
  
}
