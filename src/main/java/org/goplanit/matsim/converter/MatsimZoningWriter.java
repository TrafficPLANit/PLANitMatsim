package org.goplanit.matsim.converter;

import java.util.logging.Logger;

import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.matsim.util.MatsimStopFacilityIdHelper;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.converter.idmapping.ZoningIdMapper;
import org.goplanit.converter.zoning.ZoningWriter;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.zoning.Zoning;

/**
 * A class that takes a PLANit zoning and extracts and writes the MATSIM public transport information to disk. Since
 * a PLANit zoning only contains information about stops, a MATsim zoning writer is rather limited in outs outputs.
 * It can only support MATSim stops and a matrix based assignment on the MATSim side.
 * 
 * @author markr
 *
 */
class MatsimZoningWriter extends MatsimWriter<Zoning> implements ZoningWriter{
  
  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimZoningWriter.class.getCanonicalName());

  /** reference network to use */
  private MacroscopicNetwork referenceNetwork;
  
  /** the zoning writer settings used for the MATSim pt component*/
  private final MatsimZoningWriterSettings zoningWriterSettings;  
    
  /**
   * validate if settings are complete and if not try to salve by adopting settings from the network where possible
   *
   * @return valid flag
   */
  private boolean validateSettings() {
    if(getSettings().getOutputDirectory() == null || getSettings().getOutputDirectory().isBlank()) {
      LOGGER.severe("MATSim zoning output directory not set, abort");
      return false;
    }
    return true;
  }    
    

  /** constructor 
   * 
   * @param zoningWriterSettings to use
   * @param referenceNetwork mandatory reference network
   */
  protected MatsimZoningWriter(
      final MatsimZoningWriterSettings zoningWriterSettings,
      final MacroscopicNetwork referenceNetwork) {
    super(IdMapperType.ID);
    this.referenceNetwork = referenceNetwork;
    this.zoningWriterSettings = zoningWriterSettings;
  }

  /**
   * Access to zoning writer settings
   * @return settings
   */
  MatsimZoningWriterSettings getZoningWriterSettings() {
    return zoningWriterSettings;
  }


  /**
   * extract public transport information from PLANit zoning and use it to persist as much  of the MATSim
   * public transport xml's as possible
   * 
   * @param zoning to use for MATSim pt persistence
   */  
  @Override
  public void write(Zoning zoning){
    PlanItRunTimeException.throwIfNull(zoning,"Unable to persist MATSim transit schedule file when PLANit " +
        "zoning object is null");
    
    boolean networkValid = validateNetwork(getReferenceNetwork());
    if(!networkValid) {
      return;
    }
    boolean settingsValid = validateSettings();
    if(!settingsValid){
      return;
    }
    
    /* log settings */
    getSettings().logSettings();    
    
    /* CRS */
    prepareCoordinateReferenceSystem(
        getReferenceNetwork().getCoordinateReferenceSystem(),
        getSettings().getDestinationCoordinateReferenceSystem(),
        getSettings().getCountry(),
        true);

    // builds a mapping from PLANit to MATSim stop facility ids to use
    var stopFacilityIdMapper = new MatsimStopFacilityIdHelper(zoning.getTransferConnectoids());

    /* results in writing stops only*/
    new MatsimPtXmlWriter(this, stopFacilityIdMapper).writeXmlTransitScheduleFileStopsOnly(
        zoning, getZoningWriterSettings());
    
    if(getSettings().isGenerateMatrixBasedPtRouterFiles()) {
      new MatsimPtMatrixBasedRouterWriter(this, stopFacilityIdMapper).write(zoning);
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

  /** Collect the reference network used
   *
   * @return reference network
   */
  protected MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
  }

  /** Set the reference network compatible with the zoning
   * @param referenceNetwork to use
   */
  public void setReferenceNetwork(MacroscopicNetwork referenceNetwork) {
    this.referenceNetwork = referenceNetwork;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ZoningIdMapper getPrimaryIdMapper() {
    return getComponentIdMappers().getZoningIdMappers();
  }
}
