package org.goplanit.matsim.converter.demand;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.util.ModeChainCollapseRule;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Settings for the MATSIM discrete demands write, e.g., plans
 * <p>
 * The keys a settings file can set are on {@link MatsimDiscreteDemandsWriterYamlKeyedSettings}; the methods here change
 * the mode collapse rules one rule at a time, for code, through the rules key.
 * </p>
 *
 * @author markr
 *
 */
public class MatsimDiscreteDemandsWriterSettings extends MatsimDiscreteDemandsWriterYamlKeyedSettings
    implements ConverterWriterSettings {

  private static final Logger LOGGER = Logger.getLogger(MatsimDiscreteDemandsWriterSettings.class.getCanonicalName());

  /**
   * Change the rules, setting them through their key with a changed copy of them
   *
   * @param change to make to the copy of the rules
   */
  private void changeModeCollapseRules(Consumer<Map<PredefinedModeType, ModeChainCollapseRule>> change) {
    var rules = new TreeMap<>(getModeCollapseRules());
    change.accept(rules);
    setModeCollapseRules(rules);
  }

  /**
   * Change the rule of a main mode, setting the rules through their key with a changed copy of that rule; nothing
   * happens when the main mode has no rule
   *
   * @param mainMode of the rule to change
   * @param change to make to the copy of the rule
   */
  private void changeModeCollapseRule(PredefinedModeType mainMode, Consumer<ModeChainCollapseRule> change) {
    changeModeCollapseRules(rules -> rules.computeIfPresent(mainMode, (ruleMainMode, rule) -> {
      var changed = rule.deepClone();
      change.accept(changed);
      return changed;
    }));
  }

  /** The MATSim subpopulation every written person belongs to. MATSim only replans a person with the strategies
   * configured for that person's subpopulation, so the strategies in the MATSim config must name this value */
  public static final String DEFAULT_PERSON_SUBPOPULATION = "person";


  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("MATSim Plans (Discrete Demands) Writer Settings"));
    super.logSettings(level);
  }


  /** constructor
   */
  public MatsimDiscreteDemandsWriterSettings(){
    this(null);
  }

  /** constructor
   *
   * @param outputDirectory to use
   */
  public MatsimDiscreteDemandsWriterSettings(String outputDirectory){
    this(outputDirectory, DEFAULT_PLANS_FILE_NAME);  }

  /** constructor
   *
   * @param outputDirectory to use
   * @param outputFileName to use
   */
  public MatsimDiscreteDemandsWriterSettings(String outputDirectory, String outputFileName){
    super(outputDirectory, outputFileName, CountryNames.GLOBAL);
  }   
  
  // getters-setters

  /**
   * Add a new mode chain collapse rule, or replace it if the main mode already exists.
   *
   * @param rule the rule to add
   */
  @CoveredBySettingsKey("modeCollapseRules")
  public void setModeCollapseRule(ModeChainCollapseRule rule) {
    if (rule != null) {
      changeModeCollapseRules(rules -> rules.put(rule.getMainMode(), rule));
    }
  }

  /**
   * Remove a mode chain collapse rule given its main mode.
   * If the main mode does not exist, nothing happens.
   *
   * @param mainMode the main mode of the rule to remove
   */
  @CoveredBySettingsKey("modeCollapseRules")
  public void removeModeCollapseRule(PredefinedModeType mainMode) {
    if (mainMode != null) {
      changeModeCollapseRules(rules -> rules.remove(mainMode));
    }
  }

  /**
   * Remove all mode chain collapse rules. This ensures that no chains get collapsed. This can be useful if
   * the original input has more detailed main modes but it already has collapsed its access/egress for example, so no
   * further collapsing is required but the mapping to a more aggregate PT mode still needs to happen.
   *
   */
  @CoveredBySettingsKey("modeCollapseRules")
  public void removeAllModeCollapseRules() {
    setModeCollapseRules(Map.of());
  }

  /**
   * Add succeeding egress connectors ("to" modes) to an existing main mode rule.
   * If the main mode does not exist, nothing happens.
   *
   * @param mainMode the main mode identifying the rule
   * @param toConnectors the list of to-connectors to append
   */
  @CoveredBySettingsKey("modeCollapseRules")
  public void addToConnectorsToRule(PredefinedModeType mainMode, List<PredefinedModeType> toConnectors) {
    if (mainMode == null || toConnectors == null || toConnectors.isEmpty()) {
      return;
    }
    changeModeCollapseRule(mainMode, rule -> rule.setAllowedToConnectors(
        Stream.concat(rule.getAllowedToConnectors().stream(), toConnectors.stream()).collect(Collectors.toList())));
  }

  /**
   * Add preceding access connectors ("from" modes) to an existing main mode rule.
   * If the main mode does not exist, nothing happens.
   *
   * @param mainMode the main mode identifying the rule
   * @param fromConnectors the list of from-connectors to append
   */
  @CoveredBySettingsKey("modeCollapseRules")
  public void addFromConnectorsToRule(PredefinedModeType mainMode, List<PredefinedModeType> fromConnectors) {
    if (mainMode == null || fromConnectors == null || fromConnectors.isEmpty()) {
      return;
    }
    changeModeCollapseRule(mainMode, rule -> rule.setAllowedFromConnectors(
        Stream.concat(rule.getAllowedFromConnectors().stream(), fromConnectors.stream()).collect(Collectors.toList())));
  }

  /**
   * Override the missing connector flags for an existing main mode rule.
   * If the main mode does not exist, nothing happens.
   *
   * @param mainMode the main mode identifying the rule
   * @param allowedFromMissingIfToPresent flag value to set
   * @param allowedToMissingIfFromPresent flag value to set
   */
  @CoveredBySettingsKey("modeCollapseRules")
  public void setConnectorFlagsForRule(
      PredefinedModeType mainMode,
      boolean allowedFromMissingIfToPresent,
      boolean allowedToMissingIfFromPresent) {
    if (mainMode == null) {
      return;
    }
    changeModeCollapseRule(mainMode, rule -> {
      rule.setAllowedFromMissingIfToPresent(allowedFromMissingIfToPresent);
      rule.setAllowedToMissingIfFromPresent(allowedToMissingIfFromPresent);
    });
  }

}
