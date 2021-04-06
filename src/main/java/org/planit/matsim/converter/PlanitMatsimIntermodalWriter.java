package org.planit.matsim.converter;

import java.util.logging.Logger;

import org.planit.converter.IdMapperType;
import org.planit.converter.intermodal.IntermodalWriter;
import org.planit.network.InfrastructureNetwork;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.zoning.Zoning;

/**
 * A class that takes a PLANit intermodal network and writes it as a MATSIM intermodal network.
 * Since an intermodal mapper requires transit elements to reference network elements, the only valid id mapping that
 * we allow is either planit internal ids (default), or planit xml ids. External ids cannot be used since they cannot be guaranteed to be unique
 * causing problems with references between links and stop facility link references. If the user still wants to check against the original extrnal ids
 * in Matsim, we still write then as origids.   
 * 
 * @author markr
 *
 */
public class PlanitMatsimIntermodalWriter implements IntermodalWriter {
  
  /** the logger */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimIntermodalWriter.class.getCanonicalName());
    
  /** the network writer to use */
  protected PlanitMatsimNetworkWriter networkWriter = null;
  
  /** the zoning writer to use */
  protected PlanitMatsimZoningWriter zoningWriter = null;  
  
  /** the output directory to use */
  protected final String outputDirectory;
  
  /** network settings to use */
  protected final PlanitMatsimNetworkWriterSettings networkSettings;
  
  /**
   * the id mapper to use
   */
  protected IdMapperType idMapper;
  
  /** initialise network writer
   */
  protected void initialiseNetworkWriter() {
    networkWriter = PlanitMatsimNetworkWriterFactory.create(outputDirectory, networkSettings);
  } 
  
  /** initialise zoning writer
   * 
   * @param infrastructureNetwork to use
   * @param networkWriterSettings to use
   */
  protected void initialiseZoningWriter(MacroscopicNetwork infrastructureNetwork, PlanitMatsimNetworkWriterSettings networkWriterSettings) {
    zoningWriter = PlanitMatsimZoningWriterFactory.create(outputDirectory, infrastructureNetwork, networkWriterSettings);
  }    
    
  /** Constructor 
   * @param outputdirectory to use
   * @param countryName to use
   * @param networkSettings to use
   */
  protected PlanitMatsimIntermodalWriter(String outputDirectory, PlanitMatsimNetworkWriterSettings networkSettings) {
    this.outputDirectory = outputDirectory;  
    this.networkSettings = networkSettings;
    setIdMapperType(IdMapperType.ID);
  }
  
  /** Collect the network writer settings
   * 
   * @return settings of the network writer component
   */
  public PlanitMatsimNetworkWriterSettings getNetworkSettings() {
    return networkSettings;
  }
  
  /**
   * Persist the PLANit network and zoning and a MATSIM compatible network to disk
   * 
   * @param infrastructureNetwork to persist as MATSIM network
   * @param zoning to extract public transport infratructure from (poles, platforms, stations)
   * 
   */
  @Override
  public void write(InfrastructureNetwork<?, ?> infrastructureNetwork, Zoning zoning) throws PlanItException {
    
    /* network writer */
    initialiseNetworkWriter();    

    /* write network */
    networkWriter.setIdMapperType(idMapper);
    networkWriter.write(infrastructureNetwork);
    
    /* zoning writer */
    initialiseZoningWriter((MacroscopicNetwork)infrastructureNetwork, networkWriter.getSettings());
    
    /* write zoning */
    zoningWriter.setIdMapperType(idMapper);
    zoningWriter.write(zoning);    
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IdMapperType getIdMapperType() {
    return idMapper;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setIdMapperType(IdMapperType idMapper) {
    this.idMapper = idMapper;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
    networkWriter.reset();
    zoningWriter.reset();
  }

}
