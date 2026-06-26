package org.goplanit.matsim.converter.demand;

import org.goplanit.converter.demands.DiscreteDemandsWriter;
import org.goplanit.converter.idmapping.DemandsIdMapper;
import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.goplanit.zoning.Zoning;

import javax.xml.stream.XMLStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * A class that takes a PLANit DiscreteDemands and writes it as a MATSIM plans file.
 * 
 * @author markr
  */
public class MatsimDiscreteDemandsWriter extends MatsimWriter<DiscreteDemands> implements DiscreteDemandsWriter{

  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimDiscreteDemandsWriter.class.getCanonicalName());

  /**
   * validate the settings making sure minimal output information is available
   */
  private boolean validateSettings() {
    if(StringUtils.isNullOrBlank(getSettings().getOutputDirectory())) {
      LOGGER.severe("Matsim plans output directory not set on settings, unable to persist network");
      return false;
    }
    if(StringUtils.isNullOrBlank(getSettings().getFileName())) {
      LOGGER.severe("Matsim plans output file name not set on settings, unable to persist network");
      return false;
    }

    return true;
  }

  /**
   * Log some aggregate stats on the MATSim writer regarding the number of elements persisted
   */
  private void logWriterStats() {
    // todo
    //LOGGER.info(String.format("[STATS] created %d plans",matsimNodeCounter.longValue()));
  }

  /**
   * MATSIM writer settings
   */
  protected final MatsimDiscreteDemandsWriterSettings settings;

  /**
   * write the xml MATSIM network
   *
   * @param demands to draw from
   * @param asGZip flag indicating whether to write out as gzipped XML or not
   */
  protected void writeXmlPlansFile(DiscreteDemands demands, boolean asGZip){
    Path matsimPlansPath =
        Paths.get(getSettings().getOutputDirectory(), getSettings().getFileName().concat(DEFAULT_FILE_NAME_EXTENSION));
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair =
        PlanitXmlWriterUtils.createXMLWriter(matsimPlansPath, asGZip);

    try {
      /* start */
      //todo
      //PlanitXmlWriterUtils.startXmlDocument(xmlFileWriterPair.first(), PLANS_DOCTYPE);

      /* body */
      //todo
      //writeMatsimPlansXML(xmlFileWriterPair.first(), demands);

      /* end */
      PlanitXmlWriterUtils.endXmlDocument(xmlFileWriterPair);
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException(String.format("error while persisting MATSIM plans to %s", matsimPlansPath));
    }
  }

  /**
   * Validate
   * @param demands to validate
   * @return true when ok, false otherwise
   */
  private boolean validateDemands(DiscreteDemands demands) {
    throw new PlanItRunTimeException("TODO");
  }

  /**
   * Default constructor. Initializing with default output directory and country name on the settings
   */
  public MatsimDiscreteDemandsWriter() {
    this(new MatsimDiscreteDemandsWriterSettings(null));
  }

  /**
   * Constructor
   *
   * @param settings to use
   */
  protected MatsimDiscreteDemandsWriter(MatsimDiscreteDemandsWriterSettings settings) {
    super(IdMapperType.ID);        
    
    /* config settings for writer are found here */
    this.settings = settings;
  }  


  /**
   * {@inheritDoc}
   */
  @Override
  public void write(DiscreteDemands demands) {
    PlanItRunTimeException.throwIfNull(demands,
        "discrete demands are null, cannot write undefined demands to MATSIM format");
    
    boolean valid = validateDemands(demands);
    if(!valid) {
      return;
    }
    boolean settingsValid = validateSettings();
    if(!settingsValid) {
      return;
    }

    /* id mapping */
    getComponentIdMappers().populateMissingIdMappers(getIdMapperType());

    /* log settings */
    settings.logSettings();
    
    /* write */
    writeXmlPlansFile(demands, getSettings().isWriteAsGZip());

    logWriterStats();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
//    matsimNodeCounter.reset();
//    matsimLinkCounter.reset();
//    matsimTurnRestrictionCounter.reset();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MatsimDiscreteDemandsWriterSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DemandsIdMapper getPrimaryIdMapper() {
    return getComponentIdMappers().getDemandsIdMapper();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setReferenceZoning(Zoning referenceZoning) {
    throw new PlanItRunTimeException("should be done via factory! not here, that is ugly");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Zoning getReferenceZoning() {
    throw new PlanItRunTimeException("TODO");
  }
}
