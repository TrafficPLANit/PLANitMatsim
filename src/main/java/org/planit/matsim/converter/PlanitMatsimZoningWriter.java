package org.planit.matsim.converter;

import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.opengis.referencing.operation.TransformException;
import org.planit.converter.IdMapperFunctionFactory;
import org.planit.converter.IdMapperType;
import org.planit.converter.zoning.ZoningWriter;
import org.planit.matsim.xml.MatsimTransitXmlAttributes;
import org.planit.matsim.xml.MatsimTransitXmlElements;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.utils.network.physical.LinkSegment;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.xml.PlanitXmlWriterUtils;
import org.planit.utils.zoning.Connectoid;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.DirectedConnectoids;
import org.planit.utils.zoning.Zone;
import org.planit.zoning.Zoning;

/**
 * A class that takes a PLANit zoning and extracts and writes the MATSIM public transport information to disk. 
 * 
 * 
 * @author markr
 *
 */
public class PlanitMatsimZoningWriter extends PlanitMatsimWriter<Zoning> implements ZoningWriter{
  
  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimZoningWriter.class.getCanonicalName());

  /** the reference network this zoning is compatible with */
  protected final MacroscopicNetwork referenceNetwork;
  
  /** the network sriter settings used for the matsim reference network */
  PlanitMatsimNetworkWriterSettings networkWriterSettings;
  
  /**
   * validate the network instance available, throw or log when issues are found
   * @throws PlanItException thrown if invalid
   */
  private void validateNetwork() throws PlanItException {
    PlanItException.throwIfNull(referenceNetwork, "Matsim zoning writer's macroscopic planit network is null");
    if(referenceNetwork.infrastructureLayers.size()!=1) {
      throw new PlanItException(String.format("Matsim zoning writer currently only supports networks with a single layer, the provided network has %d",referenceNetwork.infrastructureLayers.size()));
    }   
    if(!(referenceNetwork.infrastructureLayers.getFirst() instanceof MacroscopicPhysicalNetwork)) {
      throw new PlanItException(String.format("Matsim only supports macroscopic physical network layers, the provided network is of a different type"));
    }
  }
  
  /**
   * the output file name to use for the transit schedule, default is set to DEFAULT_TRANSIT_SCHEDULE_FILE_NAME
   */
  protected String outputTransitScheduleFileName = DEFAULT_TRANSIT_SCHEDULE_FILE_NAME;  
  
  /** Starting point for persisting the matsim transit schedule file
   * 
   * @param zoning to persist
   * @throws PlanItException 
   */
  protected void writeXmlTransitScheduleFile(Zoning zoning) throws PlanItException {
    Path matsimNetworkPath =  Paths.get(outputDirectory, outputTransitScheduleFileName.concat(DEFAULT_FILE_NAME_EXTENSION));    
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair = PlanitXmlWriterUtils.createXMLWriter(matsimNetworkPath);
    LOGGER.info(String.format("Persisting MATSIM public transport schedule to: %s",matsimNetworkPath.toString()));
    
    try {
      /* start */
      PlanitXmlWriterUtils.startXmlDocument(xmlFileWriterPair.first(), DOCTYPE);
      
      /* body */
      writeTransitScheduleXML(xmlFileWriterPair.first(), zoning, (MacroscopicPhysicalNetwork)referenceNetwork.infrastructureLayers.getFirst());
      
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException(String.format("error while persisting MATSIM public transit schedule to %s", matsimNetworkPath));
    }finally {
      
      /* end */
      try {
        PlanitXmlWriterUtils.endXmlDocument(xmlFileWriterPair);
      }catch(Exception e) {
        LOGGER.severe("Unable to finalise Xml document after planit exception");  
      }
    }
  }  
  
  /** convert the planit public transport infrastructure to matsim transit schedule xml
   * @param xmlWriter to use
   * @param zoning to use
   * @param networkLayer to use
   * @throws PlanItException thrown if error
   */
  protected void writeTransitScheduleXML(XMLStreamWriter xmlWriter, Zoning zoning, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {
    try {
      writeStartElementNewLine(xmlWriter,MatsimTransitXmlElements.TRANSIT_SCHEDULE, true /* add indentation*/);
      
      /* mapping for how to generated id's for various entities */
      Function<Connectoid, String> stopFacilityIdMapping = IdMapperFunctionFactory.createConnectoidIdMappingFunction(getIdMapperType());
      Function<MacroscopicLinkSegment, String> linkSegmentReferenceIdMapping = IdMapperFunctionFactory.createLinkSegmentIdMappingFunction(getIdMapperType());
      
      /* directed connectoids as stop facilities */
      writeMatsimTransitStops(xmlWriter, zoning, stopFacilityIdMapping, linkSegmentReferenceIdMapping);
                  
      writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // transit schedule
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSIM transitSchedule XML element");
    }
  }

  /** write the transit stops (stop facilities) which we extract from the planit directed connectoids
   * 
   * @param xmlWriter to use
   * @param zoning to use
   * @param stopFacilityIdMapping function to generate ids for the stop facilities entries
   * @param linkSegmentReferenceIdMapping function to generate ids for the references link segments
   * @throws PlanItException thrown if error
   */
  private void writeMatsimTransitStops(XMLStreamWriter xmlWriter, Zoning zoning, Function<Connectoid, String> stopFacilityIdMapping, Function<MacroscopicLinkSegment, String> linkSegmentReferenceIdMapping) throws PlanItException {
    try {
      writeStartElementNewLine(xmlWriter,MatsimTransitXmlElements.TRANSIT_STOPS, true /* add indentation*/);
           
      /* directed connectoids as stop facilities */      
      writeMatsimStopFacilities(xmlWriter, zoning.transferConnectoids, stopFacilityIdMapping, linkSegmentReferenceIdMapping);
                  
      writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // transit schedule
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSIM transitStops XML element");
    }
  }

  /** write the stop facilities based on the available planit transfer connectoids
   * 
   * @param xmlWriter to use
   * @param transferConnectoids to convert to stop facilities
   * @param stopFacilityIdMapping function to generate ids for the stop facilities entries
   * @param linkSegmentIdMapping function to generate ids for the references link segments 
   * @throws PlanItException thrown if error
   */
  private void writeMatsimStopFacilities(
      XMLStreamWriter xmlWriter, DirectedConnectoids transferConnectoids, Function<Connectoid, String> stopFacilityIdMapping, Function<MacroscopicLinkSegment, String> linkSegmentIdMapping) throws PlanItException {
    for(DirectedConnectoid transferConnectoid : transferConnectoids) {
      writeMatsimStopFacility(xmlWriter, transferConnectoid, stopFacilityIdMapping, linkSegmentIdMapping);
    }
  }

  /** write the stop facility based on the available planit transfer connectoids
   * 
   * @param xmlWriter to use
   * @param transferConnectoid to convert to stop facility
   * @param stopFacilityIdMapping function to generate ids for the stop facilities entries
   * @param linkSegmentIdMapping function to generate ids for the references link segments  
   * @throws PlanItException 
   */  
  private void writeMatsimStopFacility(XMLStreamWriter xmlWriter, DirectedConnectoid transferConnectoid, Function<Connectoid, String> stopFacilityIdMapping, Function<MacroscopicLinkSegment, String> linkSegmentIdMapping) throws PlanItException {
    try {
      PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimTransitXmlElements.STOP_FACILITY, indentLevel);           
            
      /* attributes  of element*/
      {
        /* ID */
        xmlWriter.writeAttribute(MatsimTransitXmlAttributes.ID, stopFacilityIdMapping.apply(transferConnectoid));
        
        LinkSegment accessLinkSegment = transferConnectoid.getAccessLinkSegment();
        if(accessLinkSegment == null) {
          LOGGER.severe(String.format("DISCARD: stop facility represented by directed connectoid (%d) has no access link segment available",transferConnectoid.getId()));
          return;
        }
        
        /* for now we use the downstream vertex of the access link segment as the stop location */
        Point stopFacilityLocation = accessLinkSegment.getDownstreamVertex().getPosition();
        
        Coordinate nodeCoordinate = extractDestinationCrsCompatibleCoordinate(stopFacilityLocation);
        if(nodeCoordinate != null) {        
          /* X */
          xmlWriter.writeAttribute(MatsimTransitXmlAttributes.X, networkWriterSettings.getDecimalFormat().format(nodeCoordinate.x));
          /* Y */
          xmlWriter.writeAttribute(MatsimTransitXmlAttributes.Y, networkWriterSettings.getDecimalFormat().format(nodeCoordinate.y));
          /* Z coordinate (v2) not supported */
        }
        
        /* LINK REF ID */
        xmlWriter.writeAttribute(MatsimTransitXmlAttributes.LINK_REF_ID, linkSegmentIdMapping.apply((MacroscopicLinkSegment)accessLinkSegment));        
        
        /* NAME - based on the transfer zone names if any */
        String stopFacilityName = "";
        for(Zone transferZone : transferConnectoid.getAccessZones())
          if(transferZone.hasName()) {
            if(!stopFacilityName.isBlank()) {
              stopFacilityName.concat("-");
            }
            stopFacilityName.concat(transferZone.getName());
        }
        if(!stopFacilityName.isBlank()) {
          xmlWriter.writeAttribute(MatsimTransitXmlAttributes.NAME, stopFacilityName);
        }
        
        /* STOP_AREA_ID (v2) - not supported yet in MATSIM I believe, when it is, we can use our transfer zone groups to map these */
        
        /* IS_BLOCKING - unknown information in planit at this point */
        
      }
      
      PlanitXmlWriterUtils.writeNewLine(xmlWriter);
    } catch (XMLStreamException | TransformException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSIM stopFacility element id:%d",transferConnectoid.getId());
    }
  }

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
  
  /** the doc type of MATSIM public transport schedule. For now we persist in v1 (v2 does exist but is not documented (yet) in Matsim manual) */
  public static final String DOCTYPE = "<!DOCTYPE network SYSTEM \"http://www.matsim.org/files/dtd/transitSchedule_v1.dtd\">";  
    
  /**
   * default names used for MATSIM public transport schedule file that is being generated
   */
  public static final String DEFAULT_TRANSIT_SCHEDULE_FILE_NAME = "transitschedule";
   

  /**
   * extract public transport information from planit zoning and use it to persist as much  of the matsim public transport
   * xml's as possible
   * 
   * @param zoning to use for matsim pt persistence
   */  
  @Override
  public void write(Zoning zoning) throws PlanItException {
    
    validateNetwork();       
    
    /* CRS */
    prepareCoordinateReferenceSystem(referenceNetwork, networkWriterSettings.getCountry(), networkWriterSettings.getDestinationCoordinateReferenceSystem());
            
    /* write stops */    
    writeXmlTransitScheduleFile(zoning);   
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    //TODO:
  }

}
