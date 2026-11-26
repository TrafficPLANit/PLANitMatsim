package org.goplanit.matsim.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.goplanit.converter.utils.PlanitToExternalModeMapping;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.ExceptionUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.annotations.NoSettingsKey;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The settings of the MATSim writers writing PLANit modes as MATSim modes that a settings file can set: the modes key,
 * its write with its read. {@link PlanitMatsimWriterModeMappingSettings} extends it with what code needs on top.
 * <p>
 * A file lists the PLANit modes written to MATSim, each with the MATSim mode it is written as, such as
 * </p>
 * <pre>
 * modeMappings: {BUS: pt, CAR: car, PEDESTRIAN: walk}
 * </pre>
 * <p>
 * and a mode not listed is not written. By default the {@link #DEFAULT_ACTIVATED_MODES} are written, each as its
 * default MATSim mode. Code switches modes on and off and gives MATSim modes one by one, keeping a mode's default
 * MATSim mode apart from one given, which the key cannot, so the class code uses reaches the mapping itself through
 * {@link #getPlanitToExternalModeMapping()}.
 * </p>
 *
 * @author markr
 */
public abstract class PlanitMatsimWriterModeMappingYamlKeyedSettings extends PlanitMatsimWriterSettings {

  /** logger to use */
  private static final Logger LOGGER =
      Logger.getLogger(PlanitMatsimWriterModeMappingYamlKeyedSettings.class.getCanonicalName());

  /** Delegate handling the internal PLANit to MATSim mappings, made anew by each reset */
  private PlanitToExternalModeMapping modeMapping;

  /**
   * Start a new mapping, as settings are made with it: each predefined PLANit mode with its default MATSim mode,
   * without disaggregated transit modes, and the {@link #DEFAULT_ACTIVATED_MODES} switched on
   */
  private void resetModeMapping() {
    modeMapping = new PlanitToExternalModeMapping();
    registerDefaultMappings(DEFAULT_USE_DISAGGREGATE_TRANSIT_MODES);
    DEFAULT_ACTIVATED_MODES.forEach(modeMapping::activate);
  }

  /**
   * Constructor
   *
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  protected PlanitMatsimWriterModeMappingYamlKeyedSettings(
      String outputDirectory, String outputFileName, String countryName) {
    super(outputDirectory, outputFileName, countryName);
    resetModeMapping();
  }

  /**
   * The mapping of PLANit modes to MATSim modes, which keeps a mode's default MATSim mode, a MATSim mode given and
   * whether the mode is switched on apart, for the methods of code that work on them one by one
   *
   * @return the mapping
   */
  protected PlanitToExternalModeMapping getPlanitToExternalModeMapping() {
    return modeMapping;
  }

  /**
   * Register the default MATSim mode of every predefined PLANit mode on the mapping in use. The MATSim modes given by
   * the user and the modes switched on stay as they are
   *
   * @param useDisaggregateTransitModes true for the disaggregated defaults of the public transport modes (bus, train),
   *     false for pt
   */
  protected void registerDefaultMappings(boolean useDisaggregateTransitModes) {
    for (var modeType : PredefinedModeType.getPredefinedModeTypesWithout(PredefinedModeType.CUSTOM)) {
      modeMapping.addDefaultMapping(modeType,
          MatsimBuiltInMode.getDefaultForPlanitMode(modeType, useDisaggregateTransitModes).getValue());
    }
  }

  /** default used is false */
  public static final boolean DEFAULT_USE_DISAGGREGATE_TRANSIT_MODES = false;

  /** The PLANit modes written to MATSim by default: car, bus, train, light rail and ferry */
  public static final Set<PredefinedModeType> DEFAULT_ACTIVATED_MODES = Collections.unmodifiableSet(
      EnumSet.of(PredefinedModeType.CAR, PredefinedModeType.BUS, PredefinedModeType.TRAIN, PredefinedModeType.LIGHTRAIL,
          PredefinedModeType.FERRY));

  /**
   * Write exactly the PLANit modes given to MATSim, each as the MATSim mode given for it: every mode is switched off
   * and every MATSim mode given before is cleared, then each mode given is switched on with its MATSim mode
   *
   * @param modeMappings MATSim mode by PLANit mode
   * @throws IllegalArgumentException for a custom PLANit mode or a blank MATSim mode, leaving the modes as they were
   */
  @SettingsWrite(key = "modeMappings", level = SettingsKeyLevel.STANDARD,
      description = "The PLANit modes written to MATSim, each with the MATSim mode it is written as, such as BUS: pt; "
          + "a mode not listed is not written")
  public void setModeMappings(Map<PredefinedModeType, String> modeMappings) {
    modeMappings.forEach((planitMode, matsimMode) -> {
      ExceptionUtils.throwIllegalArgumentIf(planitMode == PredefinedModeType.CUSTOM,
          "MATSim writers write predefined PLANit modes only, not %s", planitMode);
      ExceptionUtils.throwIllegalArgumentIf(StringUtils.isNullOrBlank(matsimMode),
          "No MATSim mode given for PLANit mode %s", planitMode);
    });
    modeMapping.getOverriddenPlanitModes().forEach(modeMapping::clearOverride);
    modeMapping.deactivateAll();
    modeMappings.forEach(modeMapping::overrideMapping);
  }

  /**
   * The PLANit modes written to MATSim, each with the MATSim mode it is written as, whether given or its default
   *
   * @return MATSim mode by PLANit mode, sorted by the name of the PLANit mode
   */
  @SettingsRead(key = "modeMappings")
  public Map<PredefinedModeType, String> getModeMappings() {
    var modeMappings = new TreeMap<PredefinedModeType, String>(Comparator.comparing(PredefinedModeType::name));
    modeMapping.getActivePlanitModes()
        .forEach(planitMode -> modeMappings.put(planitMode, modeMapping.getMappedMode(planitMode)));
    return modeMappings;
  }

  /**
   * Log the settings, then each mode of the network with the MATSim mode it is written as, or that it is not written,
   * for which the network's modes are needed
   *
   * @param macroscopicNetwork whose modes are logged
   * @param level indentation level to apply
   */
  @NoSettingsKey(reason = "logs the settings")
  public void logSettings(MacroscopicNetwork macroscopicNetwork, int level) {
    logSettings(level);

    LOGGER.info(LoggingUtils.settingsSection("Mode mappings", level));
    Modes planitModes = macroscopicNetwork.getModes();
    for (Mode planitMode : planitModes) {
      if (!planitMode.isPredefinedModeType()) {
        LOGGER.warning(String.format("[IGNORED] MATSim writer is only compatible with predefined PLANit modes, " +
            "found custom mode with name %s, ignored", planitMode.getName()));
        continue;
      }

      PredefinedModeType type = planitMode.getPredefinedModeType();
      if (!modeMapping.isMapped(type)) {
        LOGGER.info(LoggingUtils.settingsMapping(
            "PLANit mode: "+type.value(), "[DEACTIVATED]", level + 1));
      } else {
        LOGGER.info(LoggingUtils.settingsMapping(
            "PLANit mode: "+type.value(), "MATSIM mode: " + modeMapping.getMappedMode(type), level + 1));
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    resetModeMapping();
  }
}
