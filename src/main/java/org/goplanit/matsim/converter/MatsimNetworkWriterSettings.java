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
public class MatsimNetworkWriterSettings extends PlanitMatsimWriterSettings {
  
  private static final Logger LOGGER = Logger.getLogger(MatsimNetworkWriterSettings.class.getCanonicalName());    
  
  /** provides the default mapping from planit modes ((predefined) mode name)  to MATSIM mode (string) */
  protected static final Map<PredefinedModeType, String> DEFAULT_PLANIT2MATSIM_MODE_MAPPING;
  
  /** track the PLANit modes that we include in the network to write */
  protected static final Set<PredefinedModeType> DEFAULT_ACTIVATED_MODES;  
    
  /** provides the mapping from PLANit modes ((predefined) mode name)  to MATSIM mode (string) */
  protected final Map<PredefinedModeType, String> planit2MatsimModeMapping;
  
  /** track the PLANit modes that we include in the network to write */
  protected final Set<PredefinedModeType> activatedPlanitModes;
          
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
   * Initialise the predefined PLANit modes to MATSIM mode mapping, based on the (predefined) mode names. MATSIM
   * seems not to have any predefined modes, so any name can be given to them. We therefore apply
   * the PLANit's name attribute as the id for the mapping to MATSIM mode
   * 
   * @return default mode mapping based on predefined modes
   */
  protected static Map<PredefinedModeType, String> createDefaultPredefinedModeMappings() {
    Map<PredefinedModeType, String> thePlanit2MatsimModeMapping = new HashMap<>();
    EnumSet<PredefinedModeType> predefinedModes = PredefinedModeType.getPredefinedModeTypesWithout(
        PredefinedModeType.CUSTOM, PredefinedModeType.BICYCLE, PredefinedModeType.PEDESTRIAN  /* exclude */);
    for(PredefinedModeType modeType : predefinedModes) {
      thePlanit2MatsimModeMapping.put(modeType, getDefaultPredefinedModeMappings(modeType));      
    }
    return thePlanit2MatsimModeMapping;
  }  
  
  /** Collect the default mapping from PLANit predefined mode to MATSim mode
   * 
   * @param modeType to get MATSim default mapping for
   * @return default mapping found
   */
  protected static String getDefaultPredefinedModeMappings(PredefinedModeType modeType) {
    switch (modeType) {
    case BUS:
      return DEFAULT_PUBLIC_TRANSPORT_MODE;
    case SUBWAY:
      return DEFAULT_PUBLIC_TRANSPORT_MODE;
    case TRAIN:
      return DEFAULT_PUBLIC_TRANSPORT_MODE;
    case TRAM:
      return DEFAULT_PUBLIC_TRANSPORT_MODE;
    case LIGHTRAIL:
      return DEFAULT_PUBLIC_TRANSPORT_MODE;
    /* all other modes are mapped to car for convenience*/
    default:
      return DEFAULT_PRIVATE_TRANSPORT_MODE;
    }
  }

  /** Create the default activate PLANit modes that the MATSIM write will include when writing the network (if
   * they are available). By default all predefined PLANit modes that could be reasonably mapped to motorised private
   * mode car (car) or public transport (pt) are activated.
   * 
   * @return default activate PLANit modes (by name)
   */
  protected static Set<PredefinedModeType> createDefaultActivatedPlanitModes() {
    return PredefinedModeType.getPredefinedModeTypesWithout(
        PredefinedModeType.CUSTOM, PredefinedModeType.BICYCLE, PredefinedModeType.PEDESTRIAN /* exclude */);
  }  
  
  
  /**
   * Convenience method to log all the current settings
   * 
   * @param macroscopicNetwork provided for reference 
   */
  protected void logSettings(MacroscopicNetwork macroscopicNetwork) {
  
    Path matsimNetworkPath =  Paths.get(getOutputDirectory(), getOutputFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));    
    LOGGER.info(String.format("Persisting MATSIM network to: %s", matsimNetworkPath));
    
    LOGGER.info(String.format("Decimal fidelity set to %s", decimalFormat.getMaximumFractionDigits()));
    if(getDestinationCoordinateReferenceSystem() != null) {
      LOGGER.info(String.format("Destination Coordinate Reference System set to: %s", getDestinationCoordinateReferenceSystem().getName()));
    }
    
    Modes planitModes = macroscopicNetwork.getModes();
    for(Mode planitMode : planitModes) {
      if(!planitMode.isPredefinedModeType()) {
        LOGGER.warning(String.format("[IGNORED] MATSim writer is only compatible with pedefined PLANit modes, found custom mode with name %s, ignored",planitMode.getName()));
        continue;
      }
      
      if(!activatedPlanitModes.contains(planitMode.getPredefinedModeType())) {
        LOGGER.info(String.format("[DEACTIVATED] PLANit mode:%s", planitMode.getPredefinedModeType().value()));
      }else {
        String mappedMatsimMode = planit2MatsimModeMapping.get(planitMode.getPredefinedModeType());
        if(!StringUtils.isNullOrBlank(mappedMatsimMode)) {
          LOGGER.info(String.format("[ACTIVATED] PLANit mode:%s -> MATSIM mode:%s", planitMode.getPredefinedModeType().value(), planit2MatsimModeMapping.get(planitMode.getPredefinedModeType()))); 
        }
      }
    }   
  }


  /* initialise defaults */
  static {
    DEFAULT_PLANIT2MATSIM_MODE_MAPPING = createDefaultPredefinedModeMappings();
    DEFAULT_ACTIVATED_MODES = createDefaultActivatedPlanitModes();
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
    this.planit2MatsimModeMapping = new HashMap<>(DEFAULT_PLANIT2MATSIM_MODE_MAPPING);
    this.activatedPlanitModes = new HashSet<>(DEFAULT_ACTIVATED_MODES);
    setOutputDirectory(outputDirectory);
    setCountry(countryName);
    setOutputFileName(outputFileName);
  }   
  
  /** Overwrite a mapping from a predefined PLANit mode to a particular MATSim mode
   * @param planitModeType PLANit mode
   * @param matsimMode the new MATSim mode string to use
   */
  public void updatePredefinedModeMapping(PredefinedModeType planitModeType, String matsimMode){
    if(planit2MatsimModeMapping.containsKey(planitModeType)) {
      LOGGER.info(String.format("overwriting mode mapping: PLANit mode %s mapped to MATSIM mode %s",planitModeType.toString(), matsimMode));
    }
    planit2MatsimModeMapping.put(planitModeType, matsimMode);
  } 
  
  /** Remove the provided predefined mode from the activated modes listed for inclusion in the MATSIM network (in mapped form)
   * 
   * @param planitModeType to deactivate
   */
  public void deactivatePredefinedMode(PredefinedModeType planitModeType) {
    if(activatedPlanitModes.contains(planitModeType)) {
      LOGGER.info(String.format("deactivating PLANit mode %s for MATSIM network writer", planitModeType));
      activatedPlanitModes.remove(planitModeType); 
    }
  }
  
  /**
   * Deactivate all currently activated modes
   */
  public void deactivateAllModes() {
    activatedPlanitModes.clear();    
  }

  /** Activate the provided predefined mode from the activated modes listed for inclusion in the MATSIM network (in mapped form). By default all
   * PLANit modes are active, so this is only needed when a mode has been deactivated earlier
   * 
   * @param planitModeType to activate
   */
  public void activatePredefinedMode(PredefinedModeType planitModeType) {
    if(!activatedPlanitModes.contains(planitModeType)) {
      LOGGER.info(String.format("activating PLANit mode %s for MATSIM network writer", planitModeType));
      activatedPlanitModes.add(planitModeType);
      planit2MatsimModeMapping.put(planitModeType, getDefaultPredefinedModeMappings(planitModeType));
    }
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

  /** Creating a mapping from actual PLANit modes in the network to the MATSIM mode mapping as per the configuration
   * in this class instance 
   * 
   * @param networkLayer the networkLayer
   * @return the mapped PLANit mode instances to MATSIM modes (string)
   */
  public Map<Mode, String> collectActivatedPlanitModeToMatsimModeMapping(MacroscopicNetworkLayerImpl networkLayer) {
    Map<Mode, String> modeToMatsimMapping = new HashMap<Mode, String>();
    for(Mode mode : networkLayer.getSupportedModes()) {
      if(!mode.isPredefinedModeType()) {
        LOGGER.info(String.format("[IGNORED] MATSim writer is only compatible with pedefined PLANit modes, ignored custom mode with name %s",mode.getName()));
        continue;
      }
      
      if(activatedPlanitModes.contains(mode.getPredefinedModeType())){
        if(planit2MatsimModeMapping.containsKey(mode.getPredefinedModeType())) {
          modeToMatsimMapping.put(mode, planit2MatsimModeMapping.get(mode.getPredefinedModeType()));
        }else{
          LOGGER.info(String.format("[IGNORED] Found activated PLANit mode %s without mapping to MATSim mode, please provide explicit mapping",mode.getPredefinedModeType().value()));
        }
      }
    }
    return modeToMatsimMapping;
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
    // TODO
    
  }  
  
}
