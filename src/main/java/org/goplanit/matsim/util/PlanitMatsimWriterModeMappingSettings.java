package org.goplanit.matsim.util;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.converter.utils.PlanitToExternalModeMapping;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;

import java.util.*;
import java.util.logging.Logger;

/**
 * Base writer settings class to be used by all available matsim writer settings classes.
 * Contains the output directory and destination country name used
 * 
 * @author markr
 *
 */
public abstract class PlanitMatsimWriterModeMappingSettings
    extends PlanitMatsimWriterSettings implements ConverterWriterSettings {

  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimWriterModeMappingSettings.class.getCanonicalName());

  /**
   * Initializes a default mapping delegate populated with standard modes.
   *
   * @return Populated mapping instance
   */
  protected static PlanitToExternalModeMapping createInitialDefaultMapping() {
    PlanitToExternalModeMapping mapping = new PlanitToExternalModeMapping();
    EnumSet<PredefinedModeType> predefinedModes =
        PredefinedModeType.getPredefinedModeTypesWithout(PredefinedModeType.CUSTOM);

    for (PredefinedModeType modeType : predefinedModes) {
      mapping.addDefaultMapping(modeType, getDefaultPredefinedModeMappings(modeType));
    }
    return mapping;
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
      case SUBWAY:
      case TRAIN:
      case TRAM:
      case LIGHTRAIL:
      case FERRY:
        return MatsimBuiltInMode.PT.getValue();
      case PEDESTRIAN:
        return MatsimBuiltInMode.WALK.getValue();
      case BICYCLE:
        return MatsimBuiltInMode.BIKE.getValue();
      case GOODS_VEHICLE:
      case HEAVY_GOODS_VEHICLE:
      case LARGE_HEAVY_GOODS_VEHICLE:
        return MatsimBuiltInMode.FREIGHT.getValue();
      case TAXI:
      case RIDE_SHARE:
        return MatsimBuiltInMode.DRT.getValue();
      default:
        return MatsimBuiltInMode.CAR.getValue();
    }
  }

  /** Delegate handling the internal PLANit to MATSim mappings. */
  protected final PlanitToExternalModeMapping modeMapping;

  /**
   * Convenience method to log all the current settings
   *
   * @param macroscopicNetwork provided for reference
   */
  protected void logSettings(MacroscopicNetwork macroscopicNetwork, int level) {
    super.logSettings(level);

    Modes planitModes = macroscopicNetwork.getModes();
    for (Mode planitMode : planitModes) {
      if (!planitMode.isPredefinedModeType()) {
        LOGGER.warning(String.format("[IGNORED] MATSim writer is only compatible with predefined PLANit modes, " +
            "found custom mode with name %s, ignored", planitMode.getName()));
        continue;
      }

      PredefinedModeType type = planitMode.getPredefinedModeType();
      if (!modeMapping.isMapped(type)) {
        LOGGER.info(String.format("[DEACTIVATED] PLANit mode:%s", type.value()));
      } else {
        LOGGER.info(LoggingUtils.settingsMapping(
            "PLANit mode: "+type.value(), "MATSIM mode: "+modeMapping.getMappedMode(type), level + 1));
      }
    }
  }

  /**
   * Default setting for restricting a link's max speed by its supported mode max speeds if more restricting
   */
  public static final Boolean DEFAULT_RESTRICT_SPEED_LIMIT_BY_SUPPORTED_MODE = false;

  /**
   * Shallow copy constructor. Can be sued when mode mappings requires syncing across various settings classes that
   * are used simultaneously
   *
   * @param other to create shallow copy (with respect to mode mappings)
   */
  protected PlanitMatsimWriterModeMappingSettings(final PlanitMatsimWriterModeMappingSettings other) {
    super(other.getOutputDirectory(), other.getFileName(), other.getCountry());
    this.modeMapping = other.modeMapping;
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
    super(outputDirectory, outputFileName, countryName);
    this.modeMapping = createInitialDefaultMapping();

    modeMapping.activate(PredefinedModeType.CAR);
    modeMapping.activate(PredefinedModeType.BUS);
    modeMapping.activate(PredefinedModeType.TRAIN);
  }

  /**
   * constructor
   *
   * @param outputDirectory to use
   * @param outputFileName  to use
   * @param countryName     to use
   * @param planit2MatsimModeMapping instead of using an internally created instance based on defaults, use
   *                                 the provided mapping as a starting point
   * @param activatedPlanitModes instead of using an internally created instance based on defaults, use the
   *                             provided activated modes as a starting point
   */
  public PlanitMatsimWriterModeMappingSettings(
      String outputDirectory,
      String outputFileName,
      String countryName,
      final Map<PredefinedModeType, String> planit2MatsimModeMapping,
      final Set<PredefinedModeType> activatedPlanitModes) {
    super(outputDirectory, outputFileName, countryName);
    this.modeMapping = new PlanitToExternalModeMapping();

    // Convert old structures into the new delegate instance
    if (planit2MatsimModeMapping != null) {
      planit2MatsimModeMapping.forEach(this.modeMapping::addDefaultMapping);
    }
    if (activatedPlanitModes != null) {
      activatedPlanitModes.forEach(this.modeMapping::activate);
    }
  }

  /**
   * Overwrite a mapping from a predefined PLANit mode to a particular MATSim mode
   *
   * @param planitModeType PLANit mode
   * @param matsimMode     the new MATSim mode string to use
   */
  public void updatePredefinedModeMapping(PredefinedModeType planitModeType, String matsimMode) {
    if (modeMapping.isMapped(planitModeType)) {
      LOGGER.info(String.format("Overwriting mode mapping: PLANit mode %s mapped to MATSIM mode %s",
          planitModeType, matsimMode));
    }
    modeMapping.overrideMapping(planitModeType, matsimMode);
  }

  /**
   * Remove the provided predefined mode from the activated modes listed for inclusion in the MATSIM network
   * (in mapped form)
   *
   * @param planitModeType to deactivate
   */
  public void deactivatePredefinedMode(PredefinedModeType planitModeType) {
    if (modeMapping.isMapped(planitModeType)) {
      LOGGER.info(String.format("Deactivating PLANit mode %s for MATSIM network writer", planitModeType));
      modeMapping.deactivate(planitModeType);
    }
  }

  /**
   * Deactivate all currently activated modes
   */
  public void deactivateAllModes() {
    modeMapping.deactivateAll();
  }

  /**
   * Activate all default mapped modes
   */
  public void activateAllDefaultMappedModes() {
    modeMapping.activateAllDefaults();
  }

  /**
   * Activate the provided predefined mode from the activated modes listed for inclusion in the MATSIM network
   * (in mapped form). By default all PLANit modes are active, so this is only needed when a mode has been
   * deactivated earlier
   *
   * @param planitModeType to activate
   */
  public void activatePredefinedMode(PredefinedModeType planitModeType) {
    if (!modeMapping.isMapped(planitModeType)) {
      LOGGER.info(String.format("Activating PLANit mode %s for MATSIM network writer", planitModeType));
      modeMapping.overrideMapping(planitModeType, getDefaultPredefinedModeMappings(planitModeType));
    }
  }

  /**
   * Creating a mapping from PLANit modes in the network to the MATSIM mode mapping as per the configuration
   * in this class instance
   *
   * @param networkLayer the networkLayer
   * @return the mapped PLANit mode instances to MATSIM modes (string)
   */
  public Map<Mode, String> collectActivatedPlanitModeToMatsimModeMapping(MacroscopicNetworkLayer networkLayer) {
    var modeToMatsimMapping = new HashMap<Mode, String>();
    for (Mode mode : networkLayer.getSupportedModes()) {
      if (!mode.isPredefinedModeType()) {
        LOGGER.info(String.format("[IGNORED] MATSim writer is only compatible with predefined PLANit modes, " +
            "ignored custom mode with name %s", mode.getName()));
        continue;
      }

      PredefinedModeType type = mode.getPredefinedModeType();
      if (modeMapping.isMapped(type)) {
        String mappedMatsimMode = modeMapping.getMappedMode(type);
        if (!StringUtils.isNullOrBlank(mappedMatsimMode)) {
          modeToMatsimMapping.put(mode, mappedMatsimMode);
        } else {
          LOGGER.info(String.format("[IGNORED] Found activated PLANit mode %s without mapping to MATSim mode, " +
              "please provide explicit mapping", type.value()));
        }
      }
    }
    return modeToMatsimMapping;
  }

  /**
   * Directly obtains the mapped MATSim mode string for a given PLANit predefined mode type
   * based on the active configuration mapping registry.
   *
   * @param type the predefined PLANit mode type to look up
   * @return the mapped MATSim mode string, or null if unmapped or blank
   */
  public String getMappedMatsimMode(PredefinedModeType type) {
    if (type == null) {
      return null;
    }

    if (modeMapping.isMapped(type)) {
      String mappedMatsimMode = modeMapping.getMappedMode(type);
      if (!StringUtils.isNullOrBlank(mappedMatsimMode)) {
        return mappedMatsimMode;
      }

      LOGGER.info(String.format(
          "[IGNORED] Found activated PLANit mode %s without mapping to MATSim mode, " +
              "please provide explicit mapping", type.value()));
    }

    return null;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
  }
}