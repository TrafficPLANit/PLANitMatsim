package org.goplanit.matsim.converter.demand;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.util.ModeChainCollapseRule;
import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.mode.PredefinedModeType;

import static org.goplanit.utils.mode.PredefinedModeType.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/** Settings for the MATSIM discrete demands write, e.g., plans
 * 
 * @author markr
 *
 */
public class MatsimDiscreteDemandsWriterSettings extends PlanitMatsimWriterModeMappingSettings
    implements ConverterWriterSettings {

  private static final Logger LOGGER = Logger.getLogger(MatsimDiscreteDemandsWriterSettings.class.getCanonicalName());

  /** The chosen activity location generation strategy */
  private LocationGeneratorType locationGeneratorType = DEFAULT_LOCATION_GENERATOR_TYPE;

  /** rule for mode chain collapsing. Only relevant when useDisaggregateTransitModes is set to false */
  private Map<PredefinedModeType, ModeChainCollapseRule> modeCollapseRules = new TreeMap<>(DEFAULT_MODE_COLLAPSE_RULES);


  /** default used =  LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED */
  public static LocationGeneratorType DEFAULT_LOCATION_GENERATOR_TYPE =
      LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED;

  /** Unmodifiable rule hierarchy to collapse multi-stage public transport loops for aggregate modeling.
   * Default is that bus and train allow for walk access/egress and we collapse that into bus and train as a single
   * leg, the PLANit to MATSim mode mapping may then collapse that further into pt if it detects adjacent bus/train
   * legs that go in the same direction (if configure as such) */
  public static final Map<PredefinedModeType, ModeChainCollapseRule> DEFAULT_MODE_COLLAPSE_RULES = Map.of(
      BUS, new ModeChainCollapseRule(BUS, List.of(PEDESTRIAN), List.of(PEDESTRIAN)),
      TRAIN, new ModeChainCollapseRule(TRAIN, List.of(PEDESTRIAN), List.of(PEDESTRIAN))
  );


  /**
   * Convenience method to log all the current settings
   *
   * @param referenceNetwork provided for reference
   */
  @Override
  public void logSettings(MacroscopicNetwork referenceNetwork, int level) {
    LOGGER.info(LoggingUtils.settingsHeader("MATSim Plans (Discrete Demands) Writer Settings"));
    super.logSettings(referenceNetwork, level);
    LOGGER.info(LoggingUtils.settingsValue("Location generation type", getLocationGeneratorType(), level));

    if (!isUseDisaggregateTransitModes() && modeCollapseRules != null && !modeCollapseRules.isEmpty()) {
      LOGGER.info(LoggingUtils.settingsSection("Mode Chain Collapse Rules", level));
      for (ModeChainCollapseRule rule : modeCollapseRules.values()) {
        LOGGER.info(LoggingUtils.settingsValue(rule.getMainMode().toString(),
            String.format("fromConnectors=%s, toConnectors=%s, allowedFromAbsentIfToPresent=%b, " +
                    "allowedToAbsentIfFromPresent=%b",
                rule.getAllowedFromConnectors(), rule.getAllowedToConnectors(),
                rule.isAllowedFromMissingIfToPresent(), rule.isAllowedToMissingIfFromPresent()), level + 1));
      }
    }
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
    super(outputDirectory, outputFileName, "country_unused");
  }   
  
  // getters-setters

  /**
   * Get the active strategy used to generate MATSim activity spatial references
   * @return active location generator type
   */
  public LocationGeneratorType getLocationGeneratorType() {
    return locationGeneratorType;
  }

  /**
   * Set the strategy used to generate MATSim activity spatial references
   * @param locationGeneratorType to apply
   */
  public void setLocationGeneratorType(LocationGeneratorType locationGeneratorType) {
    if (locationGeneratorType == null) {
      throw new IllegalArgumentException("Location generator type cannot be null");
    }
    this.locationGeneratorType = locationGeneratorType;
  }

  /**
   * Get the modeCollapseRules.
   *
   * @return modeCollapseRules
   */
  public Collection<ModeChainCollapseRule> getModeCollapseRules() {
    return modeCollapseRules.values();
  }

  /**
   * Add a new mode chain collapse rule, or replace it if the main mode already exists.
   *
   * @param rule the rule to add
   */
  public void setModeCollapseRule(ModeChainCollapseRule rule) {
    if (rule != null) {
      this.modeCollapseRules.put(rule.getMainMode(), rule);
    }
  }

  /**
   * Remove a mode chain collapse rule given its main mode.
   * If the main mode does not exist, nothing happens.
   *
   * @param mainMode the main mode of the rule to remove
   */
  public void removeModeCollapseRule(PredefinedModeType mainMode) {
    if (mainMode != null && this.modeCollapseRules != null) {
      this.modeCollapseRules.remove(mainMode);
    }
  }

  /**
   * Add succeeding egress connectors ("to" modes) to an existing main mode rule.
   * If the main mode does not exist, nothing happens.
   *
   * @param mainMode the main mode identifying the rule
   * @param toConnectors the list of to-connectors to append
   */
  public void addToConnectorsToRule(PredefinedModeType mainMode, List<PredefinedModeType> toConnectors) {
    if (mainMode == null || toConnectors == null || toConnectors.isEmpty() || this.modeCollapseRules == null) {
      return;
    }
    ModeChainCollapseRule r = this.modeCollapseRules.get(mainMode);
    if (r != null) {
      r.getAllowedToConnectors().addAll(toConnectors);
    }
  }

  /**
   * Add preceding access connectors ("from" modes) to an existing main mode rule.
   * If the main mode does not exist, nothing happens.
   *
   * @param mainMode the main mode identifying the rule
   * @param fromConnectors the list of from-connectors to append
   */
  public void addFromConnectorsToRule(PredefinedModeType mainMode, List<PredefinedModeType> fromConnectors) {
    if (mainMode == null || fromConnectors == null || fromConnectors.isEmpty() || this.modeCollapseRules == null) {
      return;
    }
    ModeChainCollapseRule r = this.modeCollapseRules.get(mainMode);
    if (r != null) {
      r.getAllowedFromConnectors().addAll(fromConnectors);
    }
  }

  /**
   * Override the missing connector flags for an existing main mode rule.
   * If the main mode does not exist, nothing happens.
   *
   * @param mainMode the main mode identifying the rule
   * @param allowedFromMissingIfToPresent flag value to set
   * @param allowedToMissingIfFromPresent flag value to set
   */
  public void setConnectorFlagsForRule(
      PredefinedModeType mainMode,
      boolean allowedFromMissingIfToPresent,
      boolean allowedToMissingIfFromPresent) {
    if (mainMode == null || this.modeCollapseRules == null) {
      return;
    }
    ModeChainCollapseRule r = this.modeCollapseRules.get(mainMode);
    if (r != null) {
      r.setAllowedFromMissingIfToPresent(allowedFromMissingIfToPresent);
      r.setAllowedToMissingIfFromPresent(allowedToMissingIfFromPresent);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    this.locationGeneratorType = DEFAULT_LOCATION_GENERATOR_TYPE;
    this.modeCollapseRules = DEFAULT_MODE_COLLAPSE_RULES;
  }  
  
}
