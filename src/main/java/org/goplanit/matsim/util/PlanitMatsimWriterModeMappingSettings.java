package org.goplanit.matsim.util;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;
import org.goplanit.utils.settings.annotations.NoSettingsKey;

import java.util.*;
import java.util.logging.Logger;

/**
 * Settings of the MATSim writers that write PLANit modes as MATSim modes: the network and discrete demands writers
 * <p>
 * The modes key a settings file sets is on {@link PlanitMatsimWriterModeMappingYamlKeyedSettings}. The methods here are
 * for code: they switch modes on and off and give MATSim modes one by one. A mode switched on without a MATSim mode is
 * written as its default MATSim mode, which depends on whether transit modes are disaggregated.
 * </p>
 *
 * @author markr
 */
public abstract class PlanitMatsimWriterModeMappingSettings
    extends PlanitMatsimWriterModeMappingYamlKeyedSettings implements ConverterWriterSettings {

  private static final Logger LOGGER = Logger.getLogger(
      PlanitMatsimWriterModeMappingSettings.class.getCanonicalName());

  /** switches between pt (aggregate) and bus/train mode mapping. It also decides how trip chains in one direction are
   * written: aggregate, a walk->bus->walk outbound chain of three trips collapses to one pt trip; disaggregate, the
   * plan keeps the three trips with an activity between each.
   */
  protected boolean useDisaggregateTransitModes = DEFAULT_USE_DISAGGREGATE_TRANSIT_MODES;

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
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    super.logSettings(level);
    LOGGER.info(LoggingUtils.settingsValue(
        "Use disaggregate transit modes ", isUseDisaggregateTransitModes(), level));
  }

  /**
   * Overwrite a mapping from a predefined PLANit mode to a particular MATSim mode
   *
   * @param planitModeType PLANit mode
   * @param matsimMode     the new MATSim mode string to use
   */
  @CoveredBySettingsKey("modeMappings")
  public void updatePredefinedModeMapping(PredefinedModeType planitModeType, String matsimMode) {
    var modeMapping = getPlanitToExternalModeMapping();
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
  @CoveredBySettingsKey("modeMappings")
  public void deactivatePredefinedMode(PredefinedModeType planitModeType) {
    var modeMapping = getPlanitToExternalModeMapping();
    if (modeMapping.isMapped(planitModeType)) {
      LOGGER.info(String.format("Deactivating PLANit mode %s for MATSIM network writer", planitModeType));
      modeMapping.deactivate(planitModeType);
    }
  }

  /**
   * Deactivate all currently activated modes
   */
  @CoveredBySettingsKey("modeMappings")
  public void deactivateAllModes() {
    getPlanitToExternalModeMapping().deactivateAll();
  }

  /**
   * Activate all default mapped modes
   */
  @CoveredBySettingsKey("modeMappings")
  public void activateAllDefaultMappedModes() {
    getPlanitToExternalModeMapping().activateAllDefaults();
  }

  /**
   * Switch on a predefined mode, so it is written to MATSim as the MATSim mode given for it, or else as its default
   * MATSim mode at the time of writing
   *
   * @param planitModeType to activate
   */
  @CoveredBySettingsKey("modeMappings")
  public void activatePredefinedMode(PredefinedModeType planitModeType) {
    var modeMapping = getPlanitToExternalModeMapping();
    if (!modeMapping.isMapped(planitModeType)) {
      LOGGER.info(String.format("Activating PLANit mode %s for MATSIM network writer", planitModeType));
      modeMapping.activate(planitModeType);
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
    var modeMapping = getPlanitToExternalModeMapping();
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

    var modeMapping = getPlanitToExternalModeMapping();
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
   * Whether the disaggregated default MATSim modes of the public transport modes are used
   *
   * @return true when bus and ferry default to bus and train, subway, tram and light rail to train, false when they all
   *     default to pt
   */
  public boolean isUseDisaggregateTransitModes() {
    return useDisaggregateTransitModes;
  }

  /**
   * Switch the default MATSim modes of the public transport modes: when true bus and ferry default to bus, and train,
   * subway, tram and light rail to train; when false they all default to pt. A default is used only when no MATSim
   * mode was given for the mode; which modes are switched on is unchanged
   *
   * @param useDisaggregateTransitModes true for the disaggregated default MATSim modes, false for pt
   */
  @NoSettingsKey(reason = "the MATSim modes are given by modeMappings")
  public void setUseDisaggregateTransitModes(boolean useDisaggregateTransitModes) {
    this.useDisaggregateTransitModes = useDisaggregateTransitModes;
    registerDefaultMappings(useDisaggregateTransitModes);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    this.useDisaggregateTransitModes = DEFAULT_USE_DISAGGREGATE_TRANSIT_MODES;
  }
}