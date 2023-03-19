package org.goplanit.matsim.util;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.converter.MatsimNetworkWriterSettings;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Base writer settings class to be used by all available matsim writer settings classes.
 * Contains the output directory and destination country name used
 * 
 * @author markr
 *
 */
public abstract class PlanitMatsimWriterModeMappingSettings extends PlanitMatsimWriterSettings implements ConverterWriterSettings {

  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimWriterModeMappingSettings.class.getCanonicalName());

  /**
   * provides the default mapping from planit modes ((predefined) mode name)  to MATSIM mode (string)
   */
  protected static final Map<PredefinedModeType, String> DEFAULT_PLANIT2MATSIM_MODE_MAPPING;

  /**
   * track the PLANit modes that we include in the network to write
   */
  protected static final Set<PredefinedModeType> DEFAULT_ACTIVATED_MODES;

  /**
   * provides the mapping from PLANit modes ((predefined) mode name)  to MATSIM mode (string)
   */
  protected final Map<PredefinedModeType, String> planit2MatsimModeMapping;

  /**
   * track the PLANit modes that we include in the network to write
   */
  protected final Set<PredefinedModeType> activatedPlanitModes;

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
    for (PredefinedModeType modeType : predefinedModes) {
      thePlanit2MatsimModeMapping.put(modeType, getDefaultPredefinedModeMappings(modeType));
    }
    return thePlanit2MatsimModeMapping;
  }

  /**
   * Collect the default mapping from PLANit predefined mode to MATSim mode
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

  /**
   * Create the default activate PLANit modes that the MATSIM write will include when writing the network (if
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

    Modes planitModes = macroscopicNetwork.getModes();
    for (Mode planitMode : planitModes) {
      if (!planitMode.isPredefinedModeType()) {
        LOGGER.warning(String.format("[IGNORED] MATSim writer is only compatible with predefined PLANit modes, found custom mode with name %s, ignored", planitMode.getName()));
        continue;
      }

      if (!activatedPlanitModes.contains(planitMode.getPredefinedModeType())) {
        LOGGER.info(String.format("[DEACTIVATED] PLANit mode:%s", planitMode.getPredefinedModeType().value()));
      } else {
        String mappedMatsimMode = planit2MatsimModeMapping.get(planitMode.getPredefinedModeType());
        if (!StringUtils.isNullOrBlank(mappedMatsimMode)) {
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
   * Default setting for restricting a link's max speed by its supported mode max speeds if more restricting
   */
  public static final Boolean DEFAULT_RESTRICT_SPEED_LIMIT_BY_SUPPORTED_MODE = false;

  /**
   * default mode for all public transport modes in Matsim is pt, so that is what we use for initial mapping
   */
  public static final String DEFAULT_PUBLIC_TRANSPORT_MODE = "pt";

  /**
   * default mode for all private transport modes in Matsim is car, so that is what we use for initial mapping
   */
  public static final String DEFAULT_PRIVATE_TRANSPORT_MODE = "car";

  /**
   * Shallow copy constructor. Can be sued when mode mappings requires syncing across various settings classes that are used simulatneously
   *
   * @param other to create shallow copy (with respect to mode mappings)
   */
  protected PlanitMatsimWriterModeMappingSettings(final PlanitMatsimWriterModeMappingSettings other) {
    this(other.getOutputDirectory(), other.getFileName(), other.getCountry(), other.planit2MatsimModeMapping, other.activatedPlanitModes);
  }

  /**
   * constructor
   *
   * @param countryName to use
   */
  public PlanitMatsimWriterModeMappingSettings(String countryName) {
    this(null, countryName);
  }

  /**
   * constructor
   *
   * @param outputDirectory to use
   * @param countryName     to use
   */
  public PlanitMatsimWriterModeMappingSettings(String outputDirectory, String countryName) {
    this(outputDirectory, DEFAULT_NETWORK_FILE_NAME, countryName);
  }

  /**
   * constructor
   *
   * @param outputDirectory to use
   * @param outputFileName  to use
   * @param countryName     to use
   */
  public PlanitMatsimWriterModeMappingSettings(String outputDirectory, String outputFileName, String countryName) {
    this(outputDirectory, outputFileName, countryName, new HashMap<>(DEFAULT_PLANIT2MATSIM_MODE_MAPPING), new HashSet<>(DEFAULT_ACTIVATED_MODES));
  }

  /**
   * constructor
   *
   * @param outputDirectory to use
   * @param outputFileName  to use
   * @param countryName     to use
   * @param planit2MatsimModeMapping instead of using an internally created instance based on defaults, use the provided mapping as a starting point
   * @param activatedPlanitModes instead of using an internally created instance based on defaults, use the provided activated modes as a starting point
   */
  public PlanitMatsimWriterModeMappingSettings(
      String outputDirectory,
      String outputFileName,
      String countryName,
      final Map<PredefinedModeType, String> planit2MatsimModeMapping,
      final Set<PredefinedModeType> activatedPlanitModes) {
    super(outputDirectory, outputFileName, countryName);
    this.planit2MatsimModeMapping = planit2MatsimModeMapping;
    this.activatedPlanitModes = activatedPlanitModes;
  }

  /**
   * Overwrite a mapping from a predefined PLANit mode to a particular MATSim mode
   *
   * @param planitModeType PLANit mode
   * @param matsimMode     the new MATSim mode string to use
   */
  public void updatePredefinedModeMapping(PredefinedModeType planitModeType, String matsimMode) {
    if (planit2MatsimModeMapping.containsKey(planitModeType)) {
      LOGGER.info(String.format("Overwriting mode mapping: PLANit mode %s mapped to MATSIM mode %s", planitModeType.toString(), matsimMode));
    }
    planit2MatsimModeMapping.put(planitModeType, matsimMode);
  }

  /**
   * Remove the provided predefined mode from the activated modes listed for inclusion in the MATSIM network (in mapped form)
   *
   * @param planitModeType to deactivate
   */
  public void deactivatePredefinedMode(PredefinedModeType planitModeType) {
    if (activatedPlanitModes.contains(planitModeType)) {
      LOGGER.info(String.format("Deactivating PLANit mode %s for MATSIM network writer", planitModeType));
      activatedPlanitModes.remove(planitModeType);
    }
  }

  /**
   * Deactivate all currently activated modes
   */
  public void deactivateAllModes() {
    activatedPlanitModes.clear();
  }

  /**
   * Activate the provided predefined mode from the activated modes listed for inclusion in the MATSIM network (in mapped form). By default all
   * PLANit modes are active, so this is only needed when a mode has been deactivated earlier
   *
   * @param planitModeType to activate
   */
  public void activatePredefinedMode(PredefinedModeType planitModeType) {
    if (!activatedPlanitModes.contains(planitModeType)) {
      LOGGER.info(String.format("Activating PLANit mode %s for MATSIM network writer", planitModeType));
      activatedPlanitModes.add(planitModeType);
      planit2MatsimModeMapping.put(planitModeType, getDefaultPredefinedModeMappings(planitModeType));
    }
  }

  /**
   * Creating a mapping from actual PLANit modes in the network to the MATSIM mode mapping as per the configuration
   * in this class instance
   *
   * @param networkLayer the networkLayer
   * @return the mapped PLANit mode instances to MATSIM modes (string)
   */
  public Map<Mode, String> collectActivatedPlanitModeToMatsimModeMapping(MacroscopicNetworkLayerImpl networkLayer) {
    Map<Mode, String> modeToMatsimMapping = new HashMap<Mode, String>();
    for (Mode mode : networkLayer.getSupportedModes()) {
      if (!mode.isPredefinedModeType()) {
        LOGGER.info(String.format("[IGNORED] MATSim writer is only compatible with predefined PLANit modes, ignored custom mode with name %s", mode.getName()));
        continue;
      }

      if (activatedPlanitModes.contains(mode.getPredefinedModeType())) {
        if (planit2MatsimModeMapping.containsKey(mode.getPredefinedModeType())) {
          modeToMatsimMapping.put(mode, planit2MatsimModeMapping.get(mode.getPredefinedModeType()));
        } else {
          LOGGER.info(String.format("[IGNORED] Found activated PLANit mode %s without mapping to MATSim mode, please provide explicit mapping", mode.getPredefinedModeType().value()));
        }
      }
    }
    return modeToMatsimMapping;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
  }
}