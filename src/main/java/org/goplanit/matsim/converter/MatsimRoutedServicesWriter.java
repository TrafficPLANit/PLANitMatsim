package org.goplanit.matsim.converter;

import org.goplanit.converter.IdMapperType;
import org.goplanit.converter.service.RoutedServicesWriter;
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
  private final MatsimRoutedServicesWriterSettings routedServicesWriterSettings;

  /** constructor
   *
   * @param routedServicesWriterSettings to use
   */
  protected MatsimRoutedServicesWriter(MatsimRoutedServicesWriterSettings routedServicesWriterSettings) {
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
    
    boolean networkValid = validateNetwork(getSettings().getReferenceNetwork());
    if(!networkValid) {
      return;
    }

    //validateSettings();
    
    /* log settings */
    getSettings().logSettings();    
    
    /* CRS */
    CoordinateReferenceSystem destinationCrs = 
        prepareCoordinateReferenceSystem(getSettings().getReferenceNetwork(), getSettings().getCountry(), getSettings().getDestinationCoordinateReferenceSystem());
    getSettings().setDestinationCoordinateReferenceSystem(destinationCrs);
            
    /* write stops */    
    new MatsimPtXmlWriter(this).writeXmlTransitScheduleFile(routedServices, getSettings());

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
  public MatsimRoutedServicesWriterSettings getSettings() {
    return routedServicesWriterSettings;
  }

}
