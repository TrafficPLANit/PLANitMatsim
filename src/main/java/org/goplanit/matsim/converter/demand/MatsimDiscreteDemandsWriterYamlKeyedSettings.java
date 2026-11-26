package org.goplanit.matsim.converter.demand;

import static org.goplanit.utils.mode.PredefinedModeType.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.goplanit.matsim.util.ModeChainCollapseRule;
import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.utils.exceptions.ExceptionUtils;
import org.goplanit.utils.misc.CloneUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The MATSim discrete demands writer settings a settings file can set: only the methods of its keys, each write with
 * the read of the same key. {@link MatsimDiscreteDemandsWriterSettings} extends it with what code needs on top.
 * <p>
 * Besides the keys every MATSim writer takes and the modes, a file sets how activities are placed, whether public
 * transport trips are written disaggregated, and the mode collapse rules with whether they apply, such as
 * </p>
 * <pre>
 * collapseModeChains: true
 * modeCollapseRules:
 *   TRAIN: {fromConnectors: [PEDESTRIAN], toConnectors: [PEDESTRIAN]}
 * </pre>
 * <p>
 * Code changes the rules one at a time on the class code uses, through the rules key.
 * </p>
 *
 * @author markr
 */
public abstract class MatsimDiscreteDemandsWriterYamlKeyedSettings extends PlanitMatsimWriterModeMappingSettings {

  /** logger to use */
  private static final Logger LOGGER =
      Logger.getLogger(MatsimDiscreteDemandsWriterYamlKeyedSettings.class.getCanonicalName());

  /** The chosen activity location generation strategy */
  private LocationGeneratorType locationGeneratorType = DEFAULT_LOCATION_GENERATOR_TYPE;

  /** Rule for mode chain collapsing. Only relevant when useDisaggregateTransitModes is set to false */
  private final Map<PredefinedModeType, ModeChainCollapseRule> modeCollapseRules =
      CloneUtils.deepCloneFromTo(DEFAULT_MODE_COLLAPSE_RULES, new TreeMap<>(), ModeChainCollapseRule::deepClone);

  /** Whether chains of trips are collapsed by the mode collapse rules, when transit modes are not disaggregated */
  private boolean collapseModeChains = DEFAULT_COLLAPSE_MODE_CHAINS;

  /**
   * Constructor
   *
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  protected MatsimDiscreteDemandsWriterYamlKeyedSettings(String outputDirectory, String outputFileName,
      String countryName) {
    super(outputDirectory, outputFileName, countryName);
  }

  /** default used =  LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED */
  public static LocationGeneratorType DEFAULT_LOCATION_GENERATOR_TYPE =
      LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED;

  /** Default Rule hierarchy to collapse multi-stage public transport loops for aggregate modeling, applied when mode
   * chains are collapsed ({@link #setCollapseModeChains(boolean)}, off by default).
   * Default is that bus and train allow for walk access/egress and we collapse that into bus and train as a single
   * leg. Consecutive bus/train legs are not merged, as the activity between them may be a stop rather than a
   * transfer */
  public static final Map<PredefinedModeType, ModeChainCollapseRule> DEFAULT_MODE_COLLAPSE_RULES = Map.of(
      BUS, new ModeChainCollapseRule(BUS, List.of(PEDESTRIAN), List.of(PEDESTRIAN)),
      TRAIN, new ModeChainCollapseRule(TRAIN, List.of(PEDESTRIAN), List.of(PEDESTRIAN))
  );

  /** Default for collapsing chains of trips by the mode collapse rules: false, every trip a leg of its own */
  public static final boolean DEFAULT_COLLAPSE_MODE_CHAINS = false;

  /**
   * Get the active strategy used to generate MATSim activity spatial references
   * @return active location generator type
   */
  @SettingsRead(key = "locationGeneratorType")
  public LocationGeneratorType getLocationGeneratorType() {
    return locationGeneratorType;
  }

  /**
   * Set the strategy used to generate MATSim activity spatial references
   * @param locationGeneratorType to apply
   */
  @SettingsWrite(key = "locationGeneratorType", level = SettingsKeyLevel.STANDARD,
      description = "How each activity is placed in its zone: on a link of the zone, drawn by link length, or on the "
          + "centroid of the zone")
  public void setLocationGeneratorType(LocationGeneratorType locationGeneratorType) {
    if (locationGeneratorType == null) {
      throw new IllegalArgumentException("Location generator type cannot be null");
    }
    this.locationGeneratorType = locationGeneratorType;
  }

  /**
   * Whether public transport trips are written disaggregated, see {@link #setUseDisaggregateTransitModes(boolean)}
   *
   * @return true when disaggregated, false otherwise
   */
  @Override
  @SettingsRead(key = "useDisaggregateTransitModes")
  public boolean isUseDisaggregateTransitModes() {
    return super.isUseDisaggregateTransitModes();
  }

  /**
   * Switch to the disaggregated default MATSim modes of the public transport modes, or back, as the mode mapping
   * settings do, and with it how public transport trips are written: when true each as a leg of its own, with an
   * interaction activity between legs; when false the mode collapse rules can apply, when collapsing is switched on
   *
   * @param useDisaggregateTransitModes true to write public transport trips disaggregated, false otherwise
   */
  @Override
  @SettingsWrite(key = "useDisaggregateTransitModes", level = SettingsKeyLevel.STANDARD,
      description = "True switches to the disaggregated default MATSim modes (bus, train) for modes without a MATSim "
          + "mode given explicitly, and writes each public transport trip as a leg of its own, with an interaction "
          + "activity between legs; false lets the mode collapse rules apply, when collapseModeChains is on")
  public void setUseDisaggregateTransitModes(boolean useDisaggregateTransitModes) {
    super.setUseDisaggregateTransitModes(useDisaggregateTransitModes);
  }

  /**
   * Whether chains of trips are collapsed by the mode collapse rules, when transit modes are not disaggregated
   *
   * @return true when collapsed, false when every trip is written as a leg of its own
   */
  @SettingsRead(key = "collapseModeChains")
  public boolean isCollapseModeChains() {
    return collapseModeChains;
  }

  /**
   * Choose whether chains of trips are collapsed by the mode collapse rules, such as a walk to the station, the train
   * and a walk on written as one train leg. The rules apply only when transit modes are not disaggregated
   *
   * @param collapseModeChains true to collapse, false to write every trip as a leg of its own
   */
  @SettingsWrite(key = "collapseModeChains", level = SettingsKeyLevel.UNCOMMON,
      description = "Collapse chains of trips by the mode collapse rules, such as a walk to the station, the train and "
          + "a walk on written as one train leg; applies only when useDisaggregateTransitModes is false")
  public void setCollapseModeChains(boolean collapseModeChains) {
    this.collapseModeChains = collapseModeChains;
  }

  /**
   * Replace every mode collapse rule by those given, each stored as a rule of the main mode it is given for
   *
   * @param modeCollapseRules rule by main mode, the main modes of the rules themselves not used
   * @throws IllegalArgumentException when a main mode is given no rule
   */
  @SettingsWrite(key = "modeCollapseRules", level = SettingsKeyLevel.UNCOMMON,
      description = "The mode collapse rules by main PLANit mode, replacing the defaults, such as TRAIN: "
          + "{fromConnectors: [PEDESTRIAN], toConnectors: [PEDESTRIAN]}: fromConnectors and toConnectors give, in "
          + "order, the modes of the trips before and after a trip by the main mode, merged into its leg; "
          + "allowedFromMissingIfToPresent and allowedToMissingIfFromPresent let the trips before, or after, be "
          + "missing when the others are there; {} leaves none")
  public void setModeCollapseRules(Map<PredefinedModeType, ModeChainCollapseRule> modeCollapseRules) {
    modeCollapseRules.forEach((mainMode, rule) ->
        ExceptionUtils.throwIllegalArgumentIf(rule == null, "No mode collapse rule given for main mode %s", mainMode));
    this.modeCollapseRules.clear();
    modeCollapseRules.forEach((mainMode, rule) -> this.modeCollapseRules.put(mainMode, new ModeChainCollapseRule(
        mainMode, rule.getAllowedFromConnectors(), rule.getAllowedToConnectors(),
        rule.isAllowedFromMissingIfToPresent(), rule.isAllowedToMissingIfFromPresent())));
  }

  /**
   * The mode collapse rules by their main mode
   *
   * @return the rules, a map that cannot be changed
   */
  @SettingsRead(key = "modeCollapseRules")
  public Map<PredefinedModeType, ModeChainCollapseRule> getModeCollapseRules() {
    return Collections.unmodifiableMap(modeCollapseRules);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    super.logSettings(level);
    LOGGER.info(LoggingUtils.settingsValue("Location generation type", getLocationGeneratorType(), level));
    LOGGER.info(LoggingUtils.settingsValue("Collapse mode chains", isCollapseModeChains(), level));

    if (!isUseDisaggregateTransitModes() && isCollapseModeChains() && !getModeCollapseRules().isEmpty()) {
      LOGGER.info(LoggingUtils.settingsSection("Mode Chain Collapse Rules", level));
      for (ModeChainCollapseRule rule : getModeCollapseRules().values()) {
        LOGGER.info(LoggingUtils.settingsValue(rule.getMainMode().toString(),
            String.format("fromConnectors=%s, toConnectors=%s, allowedFromAbsentIfToPresent=%b, " +
                    "allowedToAbsentIfFromPresent=%b",
                rule.getAllowedFromConnectors(), rule.getAllowedToConnectors(),
                rule.isAllowedFromMissingIfToPresent(), rule.isAllowedToMissingIfFromPresent()), level + 1));
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    setFileName(DEFAULT_PLANS_FILE_NAME);
    this.locationGeneratorType = DEFAULT_LOCATION_GENERATOR_TYPE;
    this.collapseModeChains = DEFAULT_COLLAPSE_MODE_CHAINS;
    this.modeCollapseRules.clear();
    CloneUtils.deepCloneFromTo(DEFAULT_MODE_COLLAPSE_RULES, modeCollapseRules, ModeChainCollapseRule::deepClone);
  }
}
