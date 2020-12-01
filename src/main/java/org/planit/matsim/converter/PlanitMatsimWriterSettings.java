package org.planit.matsim.converter;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.utils.math.Precision;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.PredefinedModeType;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;

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
public class PlanitMatsimWriterSettings {
  
  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimWriterSettings.class.getCanonicalName());    
  
  /** provides the default mapping from planit modes ((predefined) mode name)  to MATSIM mode (string) */
  protected static final Map<String, String> DEFAULT_PLANIT2MATSIM_MODE_MAPPING;
  
  /** track the PLANit modes that we include in the network to write */
  protected static final Set<String> DEFAULT_ACTIVATED_MODES;  

  /** provides the mapping from planit modes ((predefined) mode name)  to MATSIM mode (string) */
  protected final Map<String, String> planit2MatsimModeMapping;
  
  /** track the PLANit modes that we include in the network to write */
  protected final Set<String> activatedPlanitModes;
  
  /** the coordinate reference system used for writing entities of this network */
  protected CoordinateReferenceSystem destinationCoordinateReferenceSystem = null;
    
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
   * number of decimals to use, default is {@link Precision.DEFAULT_DECIMAL_FORMAT}
   */
  protected DecimalFormat decimalFormat = Precision.DEFAULT_DECIMAL_FORMAT; 
  
  /** when set to tru a separate detailed geometry file is generated that provides the detailed geometry of each link
   * it can be used in the VIA viewer to enhance the look of the network which otherwise only depicts the end and start node, default is false
   */
  protected boolean generateDetailedLinkGeometryFile = false;
  
  /** the country */
  protected String countryName;  
    
  /**
   * initialise the predefined PLANit modes to MATSIM mode mapping, based on the (predefined) mode names. MATSIM
   * seems not to have any predefined modes, so any name can be given to them. We therefore apply
   * the PLANit's name attribute as the id for the mapping to MATSIM mode
   */
  protected static Map<String, String> createDefaultPredefinedModeMapping() {
    Map<String, String> thePlanit2MatsimModeMapping = new HashMap<String, String>();
    for(PredefinedModeType modeType : PredefinedModeType.getPredefinedModeTypes(PredefinedModeType.CUSTOM /* exclude */)) {
      thePlanit2MatsimModeMapping.put(modeType.value(), modeType.value());  
    }
    return thePlanit2MatsimModeMapping;
  }  
  
  /** create the default activate PLANit modes that the MATSIM write will include when writing the network (if
   * they are available). By default all predefined PLANit modes are activated.
   * 
   * @return default activate planit modes (by name)
   */
  protected static Set<String> createDefaultActivatedPlanitModes() {
    Set<String> theActivatedPlanitModes = new HashSet<String>();
    for(PredefinedModeType modeType : PredefinedModeType.getPredefinedModeTypes(PredefinedModeType.CUSTOM /* exclude */)) {
      theActivatedPlanitModes.add(modeType.value());  
    }
    return theActivatedPlanitModes;
  }  
  
  
  /* initialise defaults */
  static {
    DEFAULT_PLANIT2MATSIM_MODE_MAPPING = createDefaultPredefinedModeMapping();
    DEFAULT_ACTIVATED_MODES = createDefaultActivatedPlanitModes();
  }  
  
  /** constructor 
   */
  public PlanitMatsimWriterSettings(){
    this.planit2MatsimModeMapping = new HashMap<String, String>(DEFAULT_PLANIT2MATSIM_MODE_MAPPING);
    this.activatedPlanitModes = new HashSet<String>(DEFAULT_ACTIVATED_MODES);
  }
  
  /** Overwrite a mapping from a predefined planit mode to a particular matsim mode
   * @param planitModeType planit mode
   * @param matsimMode the new matsim mode string to use
   */
  public void overwritePredefinedModeMapping(PredefinedModeType planitModeType, String matsimMode){
    String planitModeKey = planitModeType.value();
    if(planit2MatsimModeMapping.containsKey(planitModeKey)) {
      LOGGER.info(String.format("overwriting mode mapping: PLANit mode %s mapped to MATSIM mode %s",planitModeType, matsimMode));
    }
    planit2MatsimModeMapping.put(planitModeKey, matsimMode);
  } 
  
  /** remove the provided predefined mode from the activated modes listed for inclusion in the MATSIM network (in mapped form)
   * @param planitModeType to deactivate
   */
  public void deactivatedPredefinedMode(PredefinedModeType planitModeType) {
    if(activatedPlanitModes.contains(planitModeType.value())) {
      LOGGER.info(String.format("deactivating PLANit mode %s for MATSIM network writer", planitModeType));
      activatedPlanitModes.remove(planitModeType.value()); 
    }
  }
  
  /** activate the provided predefined mode from the activated modes listed for inclusion in the MATSIM network (in mapped form). By default all
   * PLANit modes are active, so this is only needed when a mode has been deactivated earlier
   * 
   * @param planitModeType to activate
   */
  public void activatePredefinedMode(PredefinedModeType planitModeType) {
    if(!activatedPlanitModes.contains(planitModeType.value())) {
      LOGGER.info(String.format("activating PLANit mode %s for MATSIM network writer", planitModeType));
      activatedPlanitModes.add(planitModeType.value()); 
    }
  }     
  
  
  /**
   * Convenience method to log all the current settings
   */
  public void logSettings() {
    LOGGER.info(String.format("Decimal fidelity set to %s", decimalFormat.getMaximumFractionDigits()));
    if(getDestinationCoordinateReferenceSystem() != null) {
      LOGGER.info(String.format("Destination Coordinate Reference System set to: %s", getDestinationCoordinateReferenceSystem().getName()));
    }
    
    for(String planitMode : activatedPlanitModes) {
      LOGGER.info(String.format("[ACTIVATED] PLANit mode:%s -> MATSIM mode:%s", planitMode, planit2MatsimModeMapping.get(planitMode)));
    }
  }
  
  // getters-setters
  
  /** collect number of decimals used in writing coordinates
   * @return number of decimals used
   */
  public DecimalFormat getDecimalFormat() {
    return decimalFormat;
  }

  /** set number of decimals used in writing coordinates
   * 
   * @param coordinateDecimals number of decimals
   */
  public void setDecimalFormat(DecimalFormat decimalFormat) {
    this.decimalFormat = decimalFormat;
  }
  
  /**
   * allow the user to provide their own function on how to populate the nt_category field of a MATSIM link
   * based on the link segment that is provided to it
   * 
   * @param linkNtCategoryfunction to apply
   */
  public void setNtCategoryFunction(Function<MacroscopicLinkSegment,String> linkNtCategoryfunction) {
    this.linkNtCategoryfunction = linkNtCategoryfunction;
  }
  
  /**
   * allow the user to provide their own function on how to populate the nt_type field of a MATSIM link
   * based on the link segment that is provided to it
   * 
   * @param linkNtTypefunction to apply
   */
  public void setNtTypeFunction(Function<MacroscopicLinkSegment,String> linkNtTypefunction) {
    this.linkNtTypefunction = linkNtTypefunction;
  }  
  
  /**
   * allow the user to provide their own function on how to populate the type field of a MATSIM link
   * based on the link segment that is provided to it
   * 
   * @param linkNtTypefunction to apply
   */
  public void setTypeFunction(Function<MacroscopicLinkSegment,String> linkTypefunction) {
    this.linkTypefunction = linkTypefunction;
  }

  /** creating a mapping from actual PLANit modes in the network to the MATSIM mode mapping as per the configuration
   * in this class instance 
   * 
   * @param network the network
   * @return the mapped PLANit mode instances to MATSIM modes (string)
   */
  public Map<Mode, String> createPlanitModeToMatsimModeMapping(MacroscopicNetwork network) {
    Map<Mode, String> modeToMatsimMapping = new HashMap<Mode, String>();
    for(Mode mode : network.modes) {
      if(activatedPlanitModes.contains(mode.getName())) {
        modeToMatsimMapping.put(mode, planit2MatsimModeMapping.get(mode.getName()));
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

  /** set the choice for whether or not a detailed geometry file for each link is created. this geometry is extracted from the PLANit link geometry
   * 
   * @param generateDetailedLinkGeometryFile
   */
  public void setGenerateDetailedLinkGeometryFile(boolean generateDetailedLinkGeometryFile) {
    this.generateDetailedLinkGeometryFile = generateDetailedLinkGeometryFile;
  }  
  
  /** Collect the currently used CRS for writing the output geometries
   * 
   * @return crs
   */
  public CoordinateReferenceSystem getDestinationCoordinateReferenceSystem() {
    return destinationCoordinateReferenceSystem;
  }

  /** Explicitly set a particular crs for writing geometries
   * @param destinationCoordinateReferenceSystem
   */
  public void setDestinationCoordinateReferenceSystem(CoordinateReferenceSystem destinationCoordinateReferenceSystem) {
    this.destinationCoordinateReferenceSystem = destinationCoordinateReferenceSystem;
  }  
  
  /**
   * set the country
   * 
   * @param country to use
   */
  public void setCountry(String countryName) {
    this.countryName = countryName;
  }  

  /**
   * the country name set 
   * 
   * @return countryName used
   */
  public String getCountry() {
    return countryName;
  }  
  
}
