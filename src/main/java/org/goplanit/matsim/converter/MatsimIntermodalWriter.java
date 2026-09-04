package org.goplanit.matsim.converter;

import java.util.logging.Logger;

import org.goplanit.converter.idmapping.NetworkIdMapper;
import org.goplanit.converter.intermodal.IntermodalWriter;
import org.goplanit.matsim.converter.network.MatsimNetworkWriter;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterFactory;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.zoning.Zoning;

/**
 * A class that takes a PLANit intermodal network and writes it as a MATSim intermodal network.
 * Since an intermodal mapper requires transit elements to reference network elements, the only valid id mapping that
 * we allow is either PLANit internal ids (default), or PLANit XML ids. External ids cannot be used since they cannot
 * be guaranteed to be unique causing problems with references between links and stop facility link references. If
 * the user still wants to check against the original external ids in MATSim, we still write then as orig ids.
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
  private MatsimNetworkWriter writeMatsimNetwork(
      MacroscopicNetwork infrastructureNetwork, MatsimZoningWriter transferAccessWriter){
    MatsimNetworkWriter networkWriter =
        MatsimNetworkWriterFactory.create(getSettings().getNetworkSettings());

    /* write network */
    networkWriter.setIdMapperType(idMapper);
    networkWriter.setTransferAccessWriter(transferAccessWriter);
    networkWriter.write(infrastructureNetwork);
    return networkWriter;
  }

  /**
   * Create the zoning writer and let it work out the transfer zone access, so the network writer can pick it up while
   * writing its file. Without this the stops that are not already on the road network end up as islands nobody can
   * reach on foot
   *
   * @param infrastructureNetwork being persisted
   * @param zoning to extract the transfer zone access from
   * @return the zoning writer to use, null when there are no transfer zones and the network write is left untouched
   */
  private MatsimZoningWriter prepareTransferAccessWriter(
      MacroscopicNetwork infrastructureNetwork, Zoning zoning) {
    if(zoning.getTransferZones().isEmpty()) {
      return null;
    }

    MatsimZoningWriter zoningWriter =
        MatsimZoningWriterFactory.create(getSettings().getZoningSettings(), infrastructureNetwork);
    zoningWriter.setIdMapperType(idMapper);
    zoningWriter.prepareTransferAccess(
        zoning,
        (MacroscopicNetworkLayer) infrastructureNetwork.getTransportLayers().getFirst(),
        getSettings().getNetworkSettings().getMinimumLinkLengthMeters());
    return zoningWriter;
  }

  /**
   * Persist the PLANit routed services, service network, and zoning combined as a full MATSIM pt schedule
   *
   * @param parentNetworkIdMapper to use
   * @param routedServices the services running on the service network
   * @param zoning to extract stops information from (transfer zones)
   *
   */
  private void writeMatsimFullPtSchedule(
      NetworkIdMapper parentNetworkIdMapper, RoutedServices routedServices, Zoning zoning) {

    /* routed services writer */
    var routedServicesWriter =
        MatsimPublicTransportServicesWriterFactory.create(getSettings(), zoning);

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
  public void write(final MacroscopicNetwork infrastructureNetwork, final Zoning zoning) {
    PlanItRunTimeException.throwIfNull(infrastructureNetwork,
        "Network is null when persisting MATSim intermodal network");
    PlanItRunTimeException.throwIfNull(zoning,
        "Zoning is null when persisting MATSim intermodal network");
    PlanItRunTimeException.throwIf(!(infrastructureNetwork instanceof MacroscopicNetwork),
        "MATSim intermodal writer only supports macroscopic networks");

    /* make sure destination country is consistent for both outputs */
    PlanItRunTimeException.throwIf(
        !getSettings().getNetworkSettings().getCountry().equals(getSettings().getZoningSettings().getCountry()),
        String.format(
            "Destination country for intermodal writer should be identical for both network and zoning writer, " +
                "but found %s and %s instead",
            getSettings().getNetworkSettings().getCountry(), getSettings().getZoningSettings().getCountry()));

    /* the zoning writer goes first, the network file has to carry its transfer zone access */
    var zoningWriter = prepareTransferAccessWriter(infrastructureNetwork, zoning);

    /* network writer */
    var networkWriter = writeMatsimNetwork(infrastructureNetwork, zoningWriter);

    /* zoning writer, only persisting stops in absence of services */
    if(zoningWriter != null) {
      /* only obtainable now, the network writer establishes its id mappers as it writes */
      zoningWriter.setParentIdMappers(networkWriter.getPrimaryIdMapper());
      zoningWriter.write(zoning);
    }
  }


  /**
   * Persist the PLANit network and zoning as a MATSIM compatible network to disk
   *
   * @param zoning to extract public transport infrastructure from (poles, platforms, stations)
   * @param routedServices to extract service routing information from
   *
   */
  @Override
  public void writeWithServices(
      MacroscopicNetwork infrastructureNetwork,
      Zoning zoning,
      ServiceNetwork serviceNetwork,
      RoutedServices routedServices) throws PlanItException {

    PlanItException.throwIfNull(serviceNetwork,
        "Service network is null when persisting MATSim intermodal network");
    PlanItException.throwIfNull(routedServices,
        "Routed services are null when persisting MATSim intermodal network");
    PlanItException.throwIfNull(zoning,
        "Zoning is null when persisting MATSim intermodal network");
    PlanItException.throwIfNull(infrastructureNetwork,
        "Infrastructure network is null when persisting MATSim intermodal network");

    /* prepared purely to contribute the transfer zone access to the network file, the stops themselves are persisted
     * as part of the full schedule below */
    var transferAccessWriter = prepareTransferAccessWriter(infrastructureNetwork, zoning);

    /* network writer */
    var networkWriter = writeMatsimNetwork(infrastructureNetwork, transferAccessWriter);

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
    // do not reset settings as reset is meant to clean up memory if possible on writer, not the settings
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MatsimIntermodalWriterSettings getSettings() {
    return settings;
  }

}