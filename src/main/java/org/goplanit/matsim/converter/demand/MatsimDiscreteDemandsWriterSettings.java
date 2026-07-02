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
   * @param referenceNetwork provided for reference
   */
  @Override
  public void logSettings(MacroscopicNetwork referenceNetwork) {

    Path matsimPath =  Paths.get(getOutputDirectory(),
        getFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));
    LOGGER.info(String.format("Persisting MATSim plans to: %s", matsimPath));

    LOGGER.info(String.format("Decimal fidelity set to %s", decimalFormat.getMaximumFractionDigits()));
    LOGGER.info(String.format("Persisting XML as GZip: %s", this.writeAsGZip));

    super.logSettings(referenceNetwork);
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
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
  }  
  
}
