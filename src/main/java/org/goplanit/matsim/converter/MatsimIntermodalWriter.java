package org.goplanit.matsim.converter;

import java.util.logging.Logger;

import org.goplanit.converter.IdMapperType;
import org.goplanit.converter.intermodal.IntermodalWriter;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.zoning.Zoning;

/**
 * A class that takes a PLANit intermodal network and writes it as a MATSim intermodal network.
 * Since an intermodal mapper requires transit elements to reference network elements, the only valid id mapping that
 * we allow is either PLANit internal ids (default), or PLANit XML ids. External ids cannot be used since they cannot be guaranteed to be unique
 * causing problems with references between links and stop facility link references. If the user still wants to check against the original extrnal ids
 * in MATSim, we still write then as origids.   
 * 
 * @author markr
 *
 */
public class MatsimIntermodalWriter implements IntermodalWriter {
  
  /** the logger */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(MatsimIntermodalWriter.class.getCanonicalName());
        
  /** Intermodal settings to use */
  protected final MatsimIntermodalWriterSettings settings;
  
  /**
   * the id mapper to use
   */
  protected IdMapperType idMapper;
      
  /** Default constructor using all default settings for underlying writers 
   */
  protected MatsimIntermodalWriter() {
    this(new MatsimIntermodalWriterSettings());    
  }  
      
  /** Constructor 
   *
   * @param settings to use
   */
  protected MatsimIntermodalWriter(MatsimIntermodalWriterSettings settings) {  
    setIdMapperType(IdMapperType.ID);
    this.settings = settings;
  }  
      
  /**
   * Persist the PLANit network and zoning and a MATSIM compatible network to disk
   * 
   * @param infrastructureNetwork to persist as MATSIM network
   * @param zoning to extract public transport infratructure from (poles, platforms, stations)
   * 
   */
  @Override
  public void write(final MacroscopicNetwork infrastructureNetwork, final Zoning zoning) throws PlanItException {
    PlanItException.throwIfNull(infrastructureNetwork, "network is null when persisting Matsim intermodal network");
    PlanItException.throwIfNull(zoning, "zoning is null when persisting Matsim intermodal network");
    PlanItException.throwIf(!(infrastructureNetwork instanceof MacroscopicNetwork), "Matsim intermodal writer only supports macroscopic networks");
    
    MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork)infrastructureNetwork;
    
    /* make sure destination country is consistent for both outputs */
    PlanItException.throwIf(!getSettings().getNetworkSettings().getCountry().equals(getSettings().getZoningSettings().getCountry()), 
        String.format(
            "Destination country for intermodal writer should be identical for both network and zoning writer, but found %s and %s instead",
            getSettings().getNetworkSettings().getCountry(), getSettings().getZoningSettings().getCountry()));
    
    /* network writer */
    MatsimNetworkWriter networkWriter = 
        MatsimNetworkWriterFactory.create(getSettings().getNetworkSettings());    

    /* write network */
    networkWriter.setIdMapperType(idMapper);
    networkWriter.write(infrastructureNetwork);
        
    /* zoning writer */
    MatsimZoningWriter zoningWriter = 
        MatsimZoningWriterFactory.create(getSettings().getNetworkSettings(), macroscopicNetwork);   
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
    settings.reset();
  }

  /**
   * {@inheritDoc}
   */    
  @Override
  public MatsimIntermodalWriterSettings getSettings() {
    return settings;
  }

}
