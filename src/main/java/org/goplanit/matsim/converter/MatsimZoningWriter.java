package org.goplanit.matsim.converter;

import java.util.logging.Logger;

import org.goplanit.converter.IdMapperType;
import org.goplanit.converter.zoning.ZoningWriter;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.zoning.Zoning;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * A class that takes a PLANit zoning and extracts and writes the MATSIM public transport information to disk. 
 * 
 * 
 * @author markr
 *
 */
public class MatsimZoningWriter extends MatsimWriter<Zoning> implements ZoningWriter{
  
  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimZoningWriter.class.getCanonicalName());
  
  /** the network writer settings used for the matsim reference network */
  private final MatsimNetworkWriterSettings networkWriterSettings;
  
  /** the zoning writer settings used for the matsim pt component*/
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


  /** the doc type of MATSIM public transport schedule. For now we persist in v1 (v2 does exist but is not documented (yet) in Matsim manual) */
  public static final String DOCTYPE = "<!DOCTYPE network SYSTEM \"http://www.matsim.org/files/dtd/transitSchedule_v1.dtd\">";         

  /**
   * extract public transport information from PLANit zoning and use it to persist as much  of the MATSim public transport
   * xml's as possible
   * 
   * @param zoning to use for MATSim pt persistence
   */  
  @Override
  public void write(Zoning zoning) throws PlanItException {
    
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
            
    /* write stops */    
    new MatsimPtXmlWriter(this).writeXmlTransitScheduleFile(zoning);   
    
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

}
