package org.goplanit.matsim.converter.demand;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
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


  /** default we use */
  public static LocationGeneratorType DEFAULT_LOCATION_GENERATOR_TYPE =
      LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED;

  /**
   * Convenience method to log all the current settings
   *
   * @param referenceNetwork provided for reference
   */
  @Override
  public void logSettings(MacroscopicNetwork referenceNetwork, int level) {
    LOGGER.info(LoggingUtils.settingsHeader("MATSim Plans (Discrete Demands) Writer Settings"));
    super.logSettings(referenceNetwork, level);
    LOGGER.info(LoggingUtils.settingsValue("Location generation type", locationGeneratorType, level));
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
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    this.locationGeneratorType = DEFAULT_LOCATION_GENERATOR_TYPE;
  }  
  
}
