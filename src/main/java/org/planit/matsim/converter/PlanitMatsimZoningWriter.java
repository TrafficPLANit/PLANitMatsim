package org.planit.matsim.converter;

import org.planit.converter.IdMapperType;
import org.planit.converter.zoning.ZoningWriter;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.zoning.Zoning;

/**
 * A class that takes a PLANit zoning and extracts and writes the MATSIM public transport information to disk. 
 * 
 * 
 * @author markr
 *
 */
public class PlanitMatsimZoningWriter extends PlanitMatsimWriter<Zoning> implements ZoningWriter{

  /** the reference network this zoning is compatible with */
  protected final MacroscopicNetwork referenceNetwork;
  
  /** the network sriter settings used for the matsim reference network */
  PlanitMatsimNetworkWriterSettings networkWriterSettings;
  
  /** constructor 
   * 
   * @param outputDirectory to persist to
   * @param referenceNetwork the zoning is based on
   * @param networkWriterSettings the network was configured by when persisting
   */
  protected PlanitMatsimZoningWriter(String outputDirectory, MacroscopicNetwork referenceNetwork, PlanitMatsimNetworkWriterSettings networkWriterSettings) {
    super(IdMapperType.EXTERNAL_ID, outputDirectory);
    this.referenceNetwork = referenceNetwork;
    this.networkWriterSettings = networkWriterSettings;
  }

  @Override
  public void write(Zoning zoning) throws PlanItException {
    
    /* CRS */
    prepareCoordinateReferenceSystem(referenceNetwork, networkWriterSettings.getCountry(), networkWriterSettings.getDestinationCoordinateReferenceSystem());
  }

  @Override
  public void reset() {
    //TODO:
  }

}
