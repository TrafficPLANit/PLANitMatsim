package org.goplanit.matsim.converter;

import java.util.logging.Logger;

import org.goplanit.converter.idmapping.IdMapperType;
import org.goplanit.converter.idmapping.NetworkIdMapper;
import org.goplanit.converter.intermodal.IntermodalWriter;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
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
public class MatsimIntermodalWriter implements IntermodalWriter<ServiceNetwork, RoutedServices> {
  
  /** the logger */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(MatsimIntermodalWriter.class.getCanonicalName());
        
  /** Intermodal settings to use */
  protected final MatsimIntermodalWriterSettings settings;
  
  /**
   * the id mapper to use
   */
  protected IdMapperType idMapper;

  /**
   * Persist the PLANit network as a MATSIM network to disk
   *
   * @param infrastructureNetwork to persist as MATSIM network
   * @return the used network writer
   */
  private MatsimNetworkWriter writeMatsimNetwork(MacroscopicNetwork infrastructureNetwork) throws PlanItException {
    MatsimNetworkWriter networkWriter =
        MatsimNetworkWriterFactory.create(getSettings().getNetworkSettings());

    /* write network */
    networkWriter.setIdMapperType(idMapper);
    networkWriter.write(infrastructureNetwork);
    return networkWriter;
  }

  /**
   * Persist the PLANit zoning as a partial MATSIM pt schedule, only containing the stops infrastructure
   *
   * @param parentNetworkIdMapper to use
   * @param zoning                to extract stops information from
   * @param infrastructureNetwork to persist as MATSIM network
   */
  private void writeMatsimPartialPtSchedule(NetworkIdMapper parentNetworkIdMapper, Zoning zoning, MacroscopicNetwork infrastructureNetwork) throws PlanItException {
    /* zoning writer */
    MatsimZoningWriter zoningWriter =
        MatsimZoningWriterFactory.create(getSettings().getNetworkSettings(), infrastructureNetwork);

    /* prep */
    zoningWriter.setIdMapperType(idMapper);
    zoningWriter.setParentIdMappers(parentNetworkIdMapper);

    /* write zoning */
    zoningWriter.write(zoning);
  }

  /**
   * Persist the PLANit routed services, service network, and zoning combined as a full MATSIM pt schedule
   *
   * @param parentNetworkIdMapper to use
   * @param routedServices the services running on the service network
   * @param zoning to extract stops information from (transfer zones)
   *
   */
  private void writeMatsimFullPtSchedule(NetworkIdMapper parentNetworkIdMapper, RoutedServices routedServices, Zoning zoning) throws PlanItException {

    /* routed services writer */
    var routedServicesWriter = MatsimPublicTransportServicesWriterFactory.create(getSettings(), zoning);

    /* prep */
    routedServicesWriter.setIdMapperType(idMapper);
    routedServicesWriter.setParentIdMappers(parentNetworkIdMapper);

    /* write routed services */
    routedServicesWriter.write(routedServices);
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
   * Persist the PLANit network and zoning as a MATSim network to disk
   * 
   * @param infrastructureNetwork to persist as MATSim network
   * @param zoning to extract public transport infrastructure from (poles, platforms, stations)
   * 
   */
  @Override
  public void write(final MacroscopicNetwork infrastructureNetwork, final Zoning zoning) throws PlanItException {
    PlanItException.throwIfNull(infrastructureNetwork, "network is null when persisting MATSim intermodal network");
    PlanItException.throwIfNull(zoning, "zoning is null when persisting MATSim intermodal network");
    PlanItException.throwIf(!(infrastructureNetwork instanceof MacroscopicNetwork), "MATSim intermodal writer only supports macroscopic networks");

    /* make sure destination country is consistent for both outputs */
    PlanItException.throwIf(!getSettings().getNetworkSettings().getCountry().equals(getSettings().getZoningSettings().getCountry()), 
        String.format(
            "Destination country for intermodal writer should be identical for both network and zoning writer, but found %s and %s instead",
            getSettings().getNetworkSettings().getCountry(), getSettings().getZoningSettings().getCountry()));

    /* network writer */
    var networkWriter = writeMatsimNetwork(infrastructureNetwork);

    /* zoning writer, only persisting stops in absence of services */
    writeMatsimPartialPtSchedule(networkWriter.getPrimaryIdMapper(), zoning, infrastructureNetwork);
  }


  /**
   * Persist the PLANit network and zoning as a MATSIM compatible network to disk
   *
   * @param zoning to extract public transport infrastructure from (poles, platforms, stations)
   * @param routedServices to extract service routing information from
   *
   */
  @Override
  public void writeWithServices(MacroscopicNetwork infrastructureNetwork, Zoning zoning, ServiceNetwork serviceNetwork, RoutedServices routedServices) throws PlanItException {
    PlanItException.throwIfNull(serviceNetwork, "Service network is null when persisting MATSim intermodal network");
    PlanItException.throwIfNull(routedServices, "Routed services are null when persisting MATSim intermodal network");
    PlanItException.throwIfNull(zoning, "Zoning is null when persisting MATSim intermodal network");
    PlanItException.throwIfNull(infrastructureNetwork, "Infrastructure network is null when persisting MATSim intermodal network");

    /* network writer */
    var networkWriter = writeMatsimNetwork(infrastructureNetwork);

    /* persist PT stops, services and schedule*/
    writeMatsimFullPtSchedule(networkWriter.getPrimaryIdMapper(), routedServices, zoning);
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
    // do not reset settings as reset is meant to cleanup memory if possible on writer, not the settings
  }

  /**
   * {@inheritDoc}
   */    
  @Override
  public MatsimIntermodalWriterSettings getSettings() {
    return settings;
  }

}
