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
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.planit.converter.IdMapperFunctionFactory;
import org.planit.converter.IdMapperType;
import org.planit.converter.zoning.ZoningWriter;
import org.planit.matsim.xml.MatsimTransitXmlAttributes;
import org.planit.matsim.xml.MatsimTransitXmlElements;
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
  
  /** the network writer settings used for the matsim reference network */
  PlanitMatsimNetworkWriterSettings networkWriterSettings;
  
  /** the zoning writer settings used for the matsim pt component*/
  PlanitMatsimZoningWriterSettings zoningWriterSettings;  
    
  /**
   * validate if settings are complete and if not try to salve by adopting settings from the network where possible
   */
  private void validateSettings() {
    if(getSettings().getOutputDirectory() == null || getSettings().getOutputDirectory().isBlank()) {
      getSettings().setOutputDirectory(networkWriterSettings.getOutputDirectory());
      if(networkWriterSettings.getOutputDirectory()!=null && !networkWriterSettings.getOutputDirectory().isBlank()) {
        LOGGER.info(String.format("Matsim zoning output directory not set, adopting network output directory %s instead", getSettings().getOutputDirectory()));
      }
    }
  }  
    
  /** Starting point for persisting the matsim transit schedule file
   * 
   * @param zoning to persist
   * @throws PlanItException  thrown if error
   */
  protected void writeXmlTransitScheduleFile(Zoning zoning) throws PlanItException {
    Path matsimNetworkPath =  Paths.get(getSettings().getOutputDirectory(), getSettings().getOutputFileName().concat(DEFAULT_FILE_NAME_EXTENSION));    
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair = PlanitXmlWriterUtils.createXMLWriter(matsimNetworkPath);
    LOGGER.info(String.format("Persisting MATSIM public transport schedule to: %s",matsimNetworkPath.toString()));
    
    try {
      /* start */
      PlanitXmlWriterUtils.startXmlDocument(xmlFileWriterPair.first(), DOCTYPE);
      
      /* body */
      writeTransitScheduleXML(xmlFileWriterPair.first(), zoning, getSettings().getReferenceNetwork().transportLayers.getFirst());
      
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
   * @param zoningWriterSettings to use
   * @param networkWriterSettings the network was configured by when persisting
   */
  protected PlanitMatsimZoningWriter(PlanitMatsimZoningWriterSettings zoningWriterSettings, PlanitMatsimNetworkWriterSettings networkWriterSettings) {
    super(IdMapperType.ID);
    this.networkWriterSettings = networkWriterSettings;
    this.zoningWriterSettings = zoningWriterSettings;
  }  

  
  /** the doc type of MATSIM public transport schedule. For now we persist in v1 (v2 does exist but is not documented (yet) in Matsim manual) */
  public static final String DOCTYPE = "<!DOCTYPE network SYSTEM \"http://www.matsim.org/files/dtd/transitSchedule_v1.dtd\">";         

  /**
   * extract public transport information from planit zoning and use it to persist as much  of the matsim public transport
   * xml's as possible
   * 
   * @param zoning to use for matsim pt persistence
   */  
  @Override
  public void write(Zoning zoning) throws PlanItException {
    
    boolean networkValid = validateNetwork(getSettings().getReferenceNetwork());
    if(!networkValid) {
      return;
    }
    validateSettings();
    
    /* log settings */
    getSettings().logSettings();    
    
    /* CRS */
    CoordinateReferenceSystem destinationCrs = 
        prepareCoordinateReferenceSystem(getSettings().getReferenceNetwork(), getSettings().getCountry(), getSettings().getDestinationCoordinateReferenceSystem());
    getSettings().setDestinationCoordinateReferenceSystem(destinationCrs);
            
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
  
  /** Collect the zoning writer settings
   * 
   * @return zoning writer settings
   */
  public PlanitMatsimZoningWriterSettings getSettings() {
    return zoningWriterSettings;
  }

}
