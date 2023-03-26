package org.goplanit.matsim.converter;

import java.util.logging.Logger;

import org.goplanit.converter.idmapping.IdMapperType;
import org.goplanit.converter.idmapping.PlanitComponentIdMapper;
import org.goplanit.converter.idmapping.ZoningIdMapper;
import org.goplanit.converter.zoning.ZoningWriter;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.zoning.Zoning;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * A class that takes a PLANit zoning and extracts and writes the MATSIM public transport information to disk. Since
 * a PLANit zoning only contains information about stops, a MATsim zoning writer is rather limited in outs outputs. It can only
 * support MATSim stops and a matrix based assignment on the MATSim side.
 * 
 * @author markr
 *
 */
public class MatsimZoningWriter extends MatsimWriter<Zoning> implements ZoningWriter{
  
  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimZoningWriter.class.getCanonicalName());
  
  /** the network writer settings used for the MATSim reference network */
  private final MatsimNetworkWriterSettings networkWriterSettings;
  
  /** the zoning writer settings used for the MATSim pt component*/
  private final MatsimZoningWriterSettings zoningWriterSettings;  
    
  /**
   * validate if settings are complete and if not try to salve by adopting settings from the network where possible
   */
  private void validateSettings() {
    if(getSettings().getOutputDirectory() == null || getSettings().getOutputDirectory().isBlank()) {
      getSettings().setOutputDirectory(networkWriterSettings.getOutputDirectory());
      if(networkWriterSettings.getOutputDirectory()!=null && !networkWriterSettings.getOutputDirectory().isBlank()) {
        LOGGER.info(String.format("Matsim zoning output directory not set, adopting network output directory %s instead", getSettings().getOutputDirectory()));
      }
    }
  }    
    

  /** constructor 
   * 
   * @param zoningWriterSettings to use
   * @param networkWriterSettings the network was configured by when persisting
   */
  protected MatsimZoningWriter(MatsimZoningWriterSettings zoningWriterSettings, MatsimNetworkWriterSettings networkWriterSettings) {
    super(IdMapperType.ID);
    this.networkWriterSettings = networkWriterSettings;
    this.zoningWriterSettings = zoningWriterSettings;
  }  

  
  MatsimNetworkWriterSettings getNetworkWriterSettings() {
    return networkWriterSettings;
  }


  MatsimZoningWriterSettings getZoningWriterSettings() {
    return zoningWriterSettings;
  }


  /**
   * extract public transport information from PLANit zoning and use it to persist as much  of the MATSim public transport
   * xml's as possible
   * 
   * @param zoning to use for MATSim pt persistence
   */  
  @Override
  public void write(Zoning zoning) throws PlanItException {
    PlanItRunTimeException.throwIfNull(zoning,"Unable to persist MATSim transit schedule file when PLANit zoning object is null");
    
    boolean networkValid = validateNetwork(getSettings().getReferenceNetwork());
    if(!networkValid) {
      return;
    }
    validateSettings();
    
    /* log settings */
    getSettings().logSettings();    
    
    /* CRS */
    CoordinateReferenceSystem destinationCrs = 
        prepareCoordinateReferenceSystem(getSettings().getReferenceNetwork(), getSettings().getCountry(), getSettings().getDestinationCoordinateReferenceSystem());
    getSettings().setDestinationCoordinateReferenceSystem(destinationCrs);
            
    /* results in writing stops only*/
    new MatsimPtXmlWriter(this).writeXmlTransitScheduleFile(
        zoning, getZoningWriterSettings(), null, null, null);
    
    if(getSettings().isGenerateMatrixBasedPtRouterFiles()) {
      new MatsimPtMatrixBasedRouterWriter(this).write(zoning);
    }
    
    
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    //TODO:
  }
  
  /** Collect the zoning writer settings
   * 
   * @return zoning writer settings
   */
  public MatsimZoningWriterSettings getSettings() {
    return zoningWriterSettings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ZoningIdMapper getPrimaryIdMapper() {
    return getComponentIdMappers().getZoningIdMappers();
  }
}
