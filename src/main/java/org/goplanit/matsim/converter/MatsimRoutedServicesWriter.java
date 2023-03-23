package org.goplanit.matsim.converter;

import org.goplanit.converter.IdMapperType;
import org.goplanit.converter.service.RoutedServicesWriter;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.network.layer.service.ServiceNetworkLayerImpl;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.zoning.Zoning;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.logging.Logger;

/**
 * A class that takes a PLANit routed services (and its reference service network, zoning and physical network) to extract and writes the MATSIM public transport information to disk.
 * 
 * @author markr
 *
 */
public class MatsimRoutedServicesWriter extends MatsimWriter<RoutedServices> implements RoutedServicesWriter {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimRoutedServicesWriter.class.getCanonicalName());

  /** the routed services writer settings used for the MATSim pt component*/
  private final MatsimPtServicesWriterSettings routedServicesWriterSettings;

  /** the network settings to use required to sync our services references to zoning information */
  protected final MatsimNetworkWriterSettings networkSettings;

  /** the zoning settings to use required to sync our services references to zoning information */
  protected final MatsimZoningWriterSettings zoningSettings;

  /** reference zoning to extract pt stops from (rather than service nodes) */
  protected final Zoning referenceZoning;

  /**
   * Validate the service network to make sure it is compatible with MATSim
   *
   * @param parentNetwork to validate
   * @return true when compatible, false otherwise
   */
  private boolean validateServiceNetwork(ServiceNetwork parentNetwork) {
    if(parentNetwork == null) {
      LOGGER.severe("PLANit service network to extract from is null");
      return false;
    }

    if (!(parentNetwork instanceof ServiceNetwork)) {
      LOGGER.severe("MATSim writer currently only supports writing vanilla service networks");
      return false;
    }

    if(parentNetwork.getTransportLayers().isEachLayerEmpty()) {
      LOGGER.severe("PLANit service network to persist is empty");
      return false;
    }

    if(parentNetwork.getTransportLayers().size()!=1) {
      LOGGER.severe(String.format("MATSim routed services writer currently only supports service networks with a single layer, the provided service network has %d",parentNetwork.getTransportLayers().size()));
      return false;
    }
    if(!(parentNetwork.getTransportLayers().getFirst() instanceof ServiceNetworkLayerImpl)) {
      LOGGER.severe(String.format("MATSim only supports vanilla service network layers, the provided layer is of a different type"));
      return false;
    }

    return true;
  }

  /** Constructor.
   *
   * @param routedServicesWriterSettings to use
   *
   * @param routedServicesWriterSettings actual settings related to what user has configured for this
   * @param networkSettings used to make sure references are synced with network, not used to expose/change settings
   * @param zoningSettings used to make sure references are synced with zoning, not used to expose/change settings
   */
  protected MatsimRoutedServicesWriter(
      MatsimPtServicesWriterSettings routedServicesWriterSettings, MatsimNetworkWriterSettings networkSettings, MatsimZoningWriterSettings zoningSettings,
      Zoning referenceZoning) {
    super(IdMapperType.ID);
    this.routedServicesWriterSettings = routedServicesWriterSettings;
    this.networkSettings = networkSettings;
    this.zoningSettings = zoningSettings;
    this.referenceZoning = referenceZoning;
  }


  /**
   * extract public transport information from PLANit zoning and use it to persist as much  of the MATSim public transport
   * XML as possible
   * 
   * @param routedServices to use for MATSim pt persistence
   */  
  @Override
  public void write(RoutedServices routedServices) throws PlanItException {
    if(!validateServiceNetwork(routedServices.getParentNetwork()) || !validateNetwork(routedServices.getParentNetwork().getParentNetwork())) {
      return;
    }

    //validateSettings();
    
    /* log settings */
    getSettings().logSettingsWithoutModeMapping();

    // todo: likely can be removed as no geo information is used during persistence to MATSim for PT services
    /* CRS */
    CoordinateReferenceSystem destinationCrs = 
        prepareCoordinateReferenceSystem(routedServices.getParentNetwork().getParentNetwork(), getSettings().getCountry(), getSettings().getDestinationCoordinateReferenceSystem());
    getSettings().setDestinationCoordinateReferenceSystem(destinationCrs);
            
    /* write stops */    
    new MatsimPtXmlWriter(this).writeXmlTransitScheduleFile(
        referenceZoning, zoningSettings, routedServices, getSettings(), networkSettings);

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    getSettings().reset();
  }
  
  /** Collect the settings
   * 
   * @return settings
   */
  public MatsimPtServicesWriterSettings getSettings() {
    return routedServicesWriterSettings;
  }

}
