package org.goplanit.matsim.converter;

import com.sun.media.jai.util.Service;
import org.goplanit.converter.IdMapperType;
import org.goplanit.converter.service.RoutedServicesWriter;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.network.layer.service.ServiceNetworkLayerImpl;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItException;
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
  private final MatsimPublicTransportServicesWriterSettings routedServicesWriterSettings;

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

  /** constructor
   *
   * @param routedServicesWriterSettings to use
   */
  protected MatsimRoutedServicesWriter(MatsimPublicTransportServicesWriterSettings routedServicesWriterSettings) {
    super(IdMapperType.ID);
    this.routedServicesWriterSettings = routedServicesWriterSettings;
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
    getSettings().logSettings();    

    // todo: likely can be removed as no geo information is used during persistence to MATSim for PT services
    /* CRS */
    CoordinateReferenceSystem destinationCrs = 
        prepareCoordinateReferenceSystem(routedServices.getParentNetwork().getParentNetwork(), getSettings().getCountry(), getSettings().getDestinationCoordinateReferenceSystem());
    getSettings().setDestinationCoordinateReferenceSystem(destinationCrs);
            
    /* write stops */    
    new MatsimPtXmlWriter(this).writeXmlTransitScheduleFile(getSettings().getReferenceZoning(), routedServices, getSettings());

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    //TODO:
  }
  
  /** Collect the settings
   * 
   * @return settings
   */
  public MatsimPublicTransportServicesWriterSettings getSettings() {
    return routedServicesWriterSettings;
  }

}
