package org.goplanit.matsim.converter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;

/** Settings for the MATSIM writer
 * 
 * By default the MATSIM writer will activate all available predefined PLANit modes for writing. In case the user wants
 * to include custom modes as well, then they must be added manually via the class' available functionality. In case the user wants to exclude certain modes that
 * are available in the network that is provided, they must be removed manually here as well.
 * 
 * The CRS used for the writer is based on the CRS defined in the settings, if this is not set, we utilise the CRS corresponding to 
 * the provided country, if no country is provided, it will retain the CRS of the network provided. If the network has no CRS an exception will be 
 * thrown
 * 
 * 
 * @author markr
 *
 */
public class MatsimNetworkWriterSettings extends PlanitMatsimWriterModeMappingSettings implements ConverterWriterSettings {
  
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

  /** when set to true, a separate detailed geometry file is generated that provides the detailed geometry of each link
   * it can be used in the VIA viewer to enhance the look of the network which otherwise only depicts the end and start node, default is false
   */
  protected boolean generateDetailedLinkGeometryFile = DEFAULT_GENERATE_DETAILED_LINK_GEOMETRY;
  
  /** Flag that indicates if a link's physical speed limit is to be reduced in case only modes with a lower top speed than the speed limit 
   * are included on this link. for example when a bus only network is generated, the bus max speed might be lower than the link speed limit.
   * when set to true the speed limit is the minimum of the physical and mode speed limit. When false the physical speed limit it used.
   */
  protected boolean restrictLinkSpeedBySupportedModes = DEFAULT_RESTRICT_SPEED_LIMIT_BY_SUPPORTED_MODE;

  /**
   * Convenience method to log all the current settings
   * 
   * @param macroscopicNetwork provided for reference 
   */
  @Override
  protected void logSettings(MacroscopicNetwork macroscopicNetwork) {
  
    Path matsimNetworkPath =  Paths.get(getOutputDirectory(), getOutputFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));    
    LOGGER.info(String.format("Persisting MATSim network to: %s", matsimNetworkPath));
    
    LOGGER.info(String.format("Decimal fidelity set to %s", decimalFormat.getMaximumFractionDigits()));
    if(getDestinationCoordinateReferenceSystem() != null) {
      LOGGER.info(String.format("Destination Coordinate Reference System set to: %s", getDestinationCoordinateReferenceSystem().getName()));
    }

    super.logSettings(macroscopicNetwork);
  }

  /**
   * Default setting for generating detailed link geometry file is false
   */
  public static final Boolean DEFAULT_GENERATE_DETAILED_LINK_GEOMETRY = false;
  
  /**
   * Default setting for restricting a link's max speed by its supported mode max speeds if more restricting
   */
  public static final Boolean DEFAULT_RESTRICT_SPEED_LIMIT_BY_SUPPORTED_MODE = false;
  
  /** default mode for all public transport modes in Matsim is pt, so that is what we use for initial mapping */
  public static final String DEFAULT_PUBLIC_TRANSPORT_MODE = "pt";
  
  /** default mode for all private transport modes in Matsim is car, so that is what we use for initial mapping */
  public static final String DEFAULT_PRIVATE_TRANSPORT_MODE = "car";    
  
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
  public void setNtCategoryFunction(Function<MacroscopicLinkSegment,String> linkNtCategoryfunction) {
    this.linkNtCategoryfunction = linkNtCategoryfunction;
  }
  
  /**
   * Allow the user to provide their own function on how to populate the nt_type field of a MATSIM link
   * based on the link segment that is provided to it
   * 
   * @param linkNtTypefunction to apply
   */
  public void setNtTypeFunction(Function<MacroscopicLinkSegment,String> linkNtTypefunction) {
    this.linkNtTypefunction = linkNtTypefunction;
  }  
  
  /**
   * Allow the user to provide their own function on how to populate the type field of a MATSIM link
   * based on the link segment that is provided to it
   * 
   * @param linkTypefunction to apply
   */
  public void setTypeFunction(Function<MacroscopicLinkSegment,String> linkTypefunction) {
    this.linkTypefunction = linkTypefunction;
  }

  /** Check if a detailed geometry file is generated
   * 
   * @return true when active, false otherwise
   */
  public boolean isGenerateDetailedLinkGeometryFile() {
    return generateDetailedLinkGeometryFile;
  }

  /** Set the choice for whether or not a detailed geometry file for each link is created. this geometry is extracted from the PLANit link geometry
   * 
   * @param generateDetailedLinkGeometryFile flag indicating to generate geometry file or not
   */
  public void setGenerateDetailedLinkGeometryFile(boolean generateDetailedLinkGeometryFile) {
    this.generateDetailedLinkGeometryFile = generateDetailedLinkGeometryFile;
  }
  
  /** Check if link speed is to be restricted by the supported modes' max speed (if more restrictive)
   * 
   * @return true when active, false otherwise
   */
  public boolean isRestrictLinkSpeedBySupportedModes() {
    return restrictLinkSpeedBySupportedModes;
  }

  /**
   * Set if link speed is to be restricted by the supported modes' max speed (if more restrictive)
   * 
   * @param restrictLinkSpeedBySupportedModes flag to set
   */
  public void setRestrictLinkSpeedBySupportedModes(boolean restrictLinkSpeedBySupportedModes) {
    this.restrictLinkSpeedBySupportedModes = restrictLinkSpeedBySupportedModes;
  }  
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    //todo
  }  
  
}
