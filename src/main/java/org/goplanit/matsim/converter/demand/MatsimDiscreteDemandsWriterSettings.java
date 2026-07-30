package org.goplanit.matsim.converter.demand;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.matsim.util.ModeChainCollapseRule;
import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.misc.LoggingUtils;

import static org.goplanit.utils.mode.PredefinedModeType.*;

import java.util.List;
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

  //todo: needs options to change this beyond current fixed defaults for testing
  /** rule for mode chain collapsing. Only relevant when useDisaggregateTransitModes is set to false */
  private List<ModeChainCollapseRule> modeCollapseRules = DEFAULT_MODE_COLLAPSE_RULES;


  /** default used =  LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED */
  public static LocationGeneratorType DEFAULT_LOCATION_GENERATOR_TYPE =
      LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED;

  /** Unmodifiable rule hierarchy to collapse multi-stage public transport loops for aggregate modeling.
   * Default is that bus and train allow for walk access/egress and we collapse that into bus and train as a single
   * leg, the PLANit to MATSim mode mapping may then collapse that further into pt if it detects adjacent bus/train
   * legs that go in the same direction (if configure as such) */
  public static final List<ModeChainCollapseRule> DEFAULT_MODE_COLLAPSE_RULES = List.of(
      new ModeChainCollapseRule(BUS, List.of(PEDESTRIAN),List.of(PEDESTRIAN)),
      new ModeChainCollapseRule(TRAIN, List.of(PEDESTRIAN), List.of(PEDESTRIAN))
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
   * Get the value of modeCollapseRules.
   *
   * @return value of modeCollapseRules
   */
  public List<ModeChainCollapseRule> getModeCollapseRules() {
    return modeCollapseRules;
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
