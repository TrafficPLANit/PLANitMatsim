package org.goplanit.matsim.converter;

import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.goplanit.converter.IdMapperFunctionFactory;
import org.goplanit.matsim.xml.MatsimTransitAttributes;
import org.goplanit.matsim.xml.MatsimTransitElements;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.goplanit.utils.zoning.Connectoid;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.DirectedConnectoids;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.opengis.referencing.operation.TransformException;

/**
 * Class that takes on the responsibility of writing all PT XML based files for a given PLANit Zoning writer
 * 
 * @author markr
 *
 */
class MatsimPtXmlWriter {
  
  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimPtXmlWriter.class.getCanonicalName());
  
  /** the zoning writer used for the MATSim pt component*/
  private final MatsimZoningWriter zoningWriter; 
    

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
      zoningWriter.writeStartElementNewLine(xmlWriter,MatsimTransitElements.TRANSIT_STOPS, true /* add indentation*/);
           
      /* directed connectoids as stop facilities */      
      writeMatsimStopFacilities(xmlWriter, zoning.transferConnectoids, stopFacilityIdMapping, linkSegmentReferenceIdMapping);
                  
      zoningWriter.writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // transit schedule
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
      PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimTransitElements.STOP_FACILITY, zoningWriter.getIndentLevel());           
            
      /* attributes  of element*/
      {
        /* ID */
        xmlWriter.writeAttribute(MatsimTransitAttributes.ID, stopFacilityIdMapping.apply(transferConnectoid));
        
        LinkSegment accessLinkSegment = transferConnectoid.getAccessLinkSegment();
        if(accessLinkSegment == null) {
          LOGGER.severe(String.format("DISCARD: stop facility represented by directed connectoid (%d) has no access link segment available",transferConnectoid.getId()));
          return;
        }
        
        /* for now we use the downstream vertex of the access link segment as the stop location */
        Point stopFacilityLocation = accessLinkSegment.getDownstreamVertex().getPosition();
        
        Coordinate nodeCoordinate = zoningWriter.extractDestinationCrsCompatibleCoordinate(stopFacilityLocation);
        if(nodeCoordinate != null) {        
          /* X */
          xmlWriter.writeAttribute(MatsimTransitAttributes.X, zoningWriter.getNetworkWriterSettings().getDecimalFormat().format(nodeCoordinate.x));
          /* Y */
          xmlWriter.writeAttribute(MatsimTransitAttributes.Y, zoningWriter.getNetworkWriterSettings().getDecimalFormat().format(nodeCoordinate.y));
          /* Z coordinate (v2) not supported */
        }
        
        /* LINK REF ID */
        xmlWriter.writeAttribute(MatsimTransitAttributes.LINK_REF_ID, linkSegmentIdMapping.apply((MacroscopicLinkSegment)accessLinkSegment));        
        
        /* NAME - based on the transfer zone names if any */
        String stopFacilityName = "";
        for(Zone transferZone : transferConnectoid.getAccessZones())
          if(transferZone.hasName()) {
            if(!stopFacilityName.isBlank()) {
              stopFacilityName.concat("-");
            }
            stopFacilityName = stopFacilityName.concat(transferZone.getName());
        }
        if(!stopFacilityName.isBlank()) {
          xmlWriter.writeAttribute(MatsimTransitAttributes.NAME, stopFacilityName);
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

  /** Starting point for persisting the MATSim transit schedule file
   * 
   * @param zoning to persist
   * @throws PlanItException  thrown if error
   */
  protected void writeXmlTransitScheduleFile(Zoning zoning) throws PlanItException {
    Path matsimNetworkPath =  Paths.get(zoningWriter.getSettings().getOutputDirectory(), zoningWriter.getSettings().getOutputFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));    
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair = PlanitXmlWriterUtils.createXMLWriter(matsimNetworkPath);
    LOGGER.info(String.format("Persisting MATSIM public transport schedule to: %s",matsimNetworkPath.toString()));
    
    try {
      /* start */
      PlanitXmlWriterUtils.startXmlDocument(xmlFileWriterPair.first(), MatsimZoningWriter.DOCTYPE);
      
      /* body */
      writeTransitScheduleXML(xmlFileWriterPair.first(), zoning, zoningWriter.getSettings().getReferenceNetwork().getTransportLayers().getFirst());
      
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
  
  /** convert the PLANit public transport infrastructure to MATSim transit schedule XML
   * @param xmlWriter to use
   * @param zoning to use
   * @param networkLayer to use
   * @throws PlanItException thrown if error
   */
  protected void writeTransitScheduleXML(XMLStreamWriter xmlWriter, Zoning zoning, MacroscopicNetworkLayer networkLayer) throws PlanItException {
    try {
      zoningWriter.writeStartElementNewLine(xmlWriter,MatsimTransitElements.TRANSIT_SCHEDULE, true /* add indentation*/);
      
      /* mapping for how to generated id's for various entities */
      Function<Connectoid, String> stopFacilityIdMapping = IdMapperFunctionFactory.createConnectoidIdMappingFunction(zoningWriter.getIdMapperType());
      Function<MacroscopicLinkSegment, String> linkSegmentReferenceIdMapping = IdMapperFunctionFactory.createLinkSegmentIdMappingFunction(zoningWriter.getIdMapperType());
      
      /* directed connectoids as stop facilities */
      writeMatsimTransitStops(xmlWriter, zoning, stopFacilityIdMapping, linkSegmentReferenceIdMapping);
                  
      zoningWriter.writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // transit schedule
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSIM transitSchedule XML element");
    }
  }

  /**
   * Constructor 
   * 
   * @param zoningWriter to use
   */
  public MatsimPtXmlWriter(final MatsimZoningWriter zoningWriter) {
    this.zoningWriter = zoningWriter;
  }
}
