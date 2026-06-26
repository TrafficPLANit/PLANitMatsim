package org.goplanit.matsim.converter.demand;

import org.goplanit.converter.ConverterWriterSettings;
import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
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

  /**
   * Convenience method to log all the current settings
   *
   * @param discreteDemands provided for reference
   */
  //@Override <-- todo no longer overrides because we feed in discrete demands, this should not happen here but on writer
  //               these settings should only log the settings as is....
  public void logSettings(DiscreteDemands discreteDemands) {

    Path matsimPath =  Paths.get(getOutputDirectory(),
        getFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));
    LOGGER.info(String.format("Persisting MATSim plans to: %s", matsimPath));

    LOGGER.info(String.format("Decimal fidelity set to %s", decimalFormat.getMaximumFractionDigits()));
    LOGGER.info(String.format("Persisting XML as GZip: %s", this.writeAsGZip));

    // todo refactor --> should not rely on network for logging the mapping, it should also not do any checking
    throw new PlanItRunTimeException("TODO");
    //super.logSettings(discreteDemands);
  }


  /** constructor
   * @param countryName to use
   */
  public MatsimDiscreteDemandsWriterSettings(String countryName){
    this(null, countryName);
  }

  /** constructor
   *
   * @param outputDirectory to use
   * @param countryName to use
   */
  public MatsimDiscreteDemandsWriterSettings(String outputDirectory, String countryName){
    this(outputDirectory, DEFAULT_NETWORK_FILE_NAME, countryName);  }

  /** constructor
   *
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  public MatsimDiscreteDemandsWriterSettings(String outputDirectory, String outputFileName, String countryName){
    super(outputDirectory, outputFileName, countryName);
  }   
  
  // getters-setters

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    //todo
  }  
  
}
