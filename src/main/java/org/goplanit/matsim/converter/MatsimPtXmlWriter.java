package org.goplanit.matsim.converter;

import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.goplanit.converter.IdMapperFunctionFactory;
import org.goplanit.matsim.xml.MatsimTransitAttributes;
import org.goplanit.matsim.xml.MatsimTransitElements;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.Pair;
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
 * Class that takes on the responsibility of writing all PT XML based files for a given PLANit memory model
 *
 * <p>
 *   useful explanation of MATSim xml transit schedule format
 *   <a href="https://matsim.atlassian.net/wiki/spaces/MATPUB/pages/83099693/Transit+Tutorial?focusedCommentId=151191553">here</a>
 * </p>
 *
 * 
 * @author markr
 *
 */
class MatsimPtXmlWriter {
  
  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimPtXmlWriter.class.getCanonicalName());
  
  /** the zoning writer used for the MATSim pt component*/
  private final MatsimWriter<?> matsimWriter;
  
  /** track number of MATSim stop facilities persisted */
  private final LongAdder matsimStopFacilityCounter = new LongAdder();

  /**
   * write the transit lines which we extract from the PLANit routed services
   *
   * @param xmlWriter               to use
   * @param routedServices          to use
    * @param servicesSettings       to use
   */
  private void writeMatsimTransitLines(XMLStreamWriter xmlWriter, RoutedServices routedServices, MatsimPublicTransportServicesWriterSettings servicesSettings) {
    throw new PlanItRunTimeException("CONTINUE HERE, not yet implemented");
  }
    

  /** write the transit stops (stop facilities) which we extract from the planit directed connectoids
   * 
   * @param xmlWriter to use
   * @param zoning to use
   * @param stopFacilityIdMapping function to generate ids for the stop facilities entries
   * @param linkSegmentReferenceIdMapping function to generate ids for the references link segments
   */
  private void writeMatsimTransitStops(XMLStreamWriter xmlWriter, Zoning zoning, Function<Connectoid, String> stopFacilityIdMapping, Function<MacroscopicLinkSegment, String> linkSegmentReferenceIdMapping) {
    try {
      matsimWriter.writeStartElementNewLine(xmlWriter,MatsimTransitElements.TRANSIT_STOPS, true /* add indentation*/);
           
      /* directed connectoids as stop facilities */      
      writeMatsimStopFacilities(xmlWriter, zoning.getTransferConnectoids(), stopFacilityIdMapping, linkSegmentReferenceIdMapping);
                  
      matsimWriter.writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // transit schedule
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error while writing MATSim transitStops XML element");
    }
  }

  /** write the stop facilities based on the available planit transfer connectoids
   * 
   * @param xmlWriter to use
   * @param transferConnectoids to convert to stop facilities
   * @param stopFacilityIdMapping function to generate ids for the stop facilities entries
   * @param linkSegmentIdMapping function to generate ids for the references link segments 
   */
  private void writeMatsimStopFacilities(
      XMLStreamWriter xmlWriter, DirectedConnectoids transferConnectoids, Function<Connectoid, String> stopFacilityIdMapping, Function<MacroscopicLinkSegment, String> linkSegmentIdMapping){
    for(var transferConnectoid : transferConnectoids) {
      writeMatsimStopFacility(xmlWriter, transferConnectoid, stopFacilityIdMapping, linkSegmentIdMapping);
      matsimStopFacilityCounter.increment();
    }
  }

  /** write the stop facility based on the available planit transfer connectoids
   * 
   * @param xmlWriter to use
   * @param transferConnectoid to convert to stop facility
   * @param stopFacilityIdMapping function to generate ids for the stop facilities entries
   * @param linkSegmentIdMapping function to generate ids for the references link segments  
   */
  private void writeMatsimStopFacility(XMLStreamWriter xmlWriter, DirectedConnectoid transferConnectoid, Function<Connectoid, String> stopFacilityIdMapping, Function<MacroscopicLinkSegment, String> linkSegmentIdMapping) {
    try {
      PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimTransitElements.STOP_FACILITY, matsimWriter.getIndentLevel());
            
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
        
        Coordinate nodeCoordinate = matsimWriter.extractDestinationCrsCompatibleCoordinate(stopFacilityLocation);
        if(nodeCoordinate != null) {        
          /* X */
          xmlWriter.writeAttribute(MatsimTransitAttributes.X, matsimWriter.getSettings().getDecimalFormat().format(nodeCoordinate.x));
          /* Y */
          xmlWriter.writeAttribute(MatsimTransitAttributes.Y, matsimWriter.getSettings().getDecimalFormat().format(nodeCoordinate.y));
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
        
        /* IS_BLOCKING - unknown information in PLANit at this point */
        
      }
      
      PlanitXmlWriterUtils.writeNewLine(xmlWriter);
    } catch (XMLStreamException | TransformException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("error while writing MATSIM stopFacility element id:%d",transferConnectoid.getId());
    }
  }
  
  /**
   * Log some aggregate stats on the MATSim writer regarding the number of elements persisted
   */
  private void logWriterStats() {
    LOGGER.info(String.format("[STATS] created %d stop facilities",matsimStopFacilityCounter.longValue()));
    throw new PlanItRunTimeException("HAVE TO ADD STATS FOR PT SERVICES HERE!!!");
  }   

  /** Starting point for persisting the MATSim transit schedule file (infrastructure, e.g., stops and stations, only)
   * 
   * @param zoning to persist
   */
  protected void writeXmlTransitScheduleFile(Zoning zoning, RoutedServices routedServices, MatsimPublicTransportServicesWriterSettings routedServicesSettings) {
    Path matsimNetworkPath =  Paths.get(matsimWriter.getSettings().getOutputDirectory(), matsimWriter.getSettings().getOutputFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair = PlanitXmlWriterUtils.createXMLWriter(matsimNetworkPath);
    LOGGER.info(String.format("Persisting MATSIM public transport schedule to: %s",matsimNetworkPath.toString()));
    
    try {
      /* start */
      PlanitXmlWriterUtils.startXmlDocument(xmlFileWriterPair.first(), MatsimZoningWriter.DOCTYPE);
      
      /* body */
      writeTransitScheduleXML(xmlFileWriterPair.first(), zoning, routedServices, routedServicesSettings);
      
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException(String.format("Error while persisting MATSIM public transit schedule to %s", matsimNetworkPath));
    }finally {
      
      /* end */
      try {
        PlanitXmlWriterUtils.endXmlDocument(xmlFileWriterPair);
      }catch(Exception e) {
        LOGGER.severe("Unable to finalise XML document after PLANit exception");
      }
    }
    
    logWriterStats();
  }

  /** convert the PLANit public transport infrastructure to MATSim transit schedule XML
   * @param xmlWriter to use
   * @param zoning to use
   */
  protected void writeTransitScheduleXML(XMLStreamWriter xmlWriter, Zoning zoning, RoutedServices routedServices, MatsimPublicTransportServicesWriterSettings servicesSettings) {
    try {
      matsimWriter.writeStartElementNewLine(xmlWriter,MatsimTransitElements.TRANSIT_SCHEDULE, true /* add indentation*/);
      
      /* mapping for how to generated id's for various entities */
      Function<Connectoid, String> stopFacilityIdMapping = IdMapperFunctionFactory.createConnectoidIdMappingFunction(matsimWriter.getIdMapperType());
      Function<MacroscopicLinkSegment, String> linkSegmentReferenceIdMapping = IdMapperFunctionFactory.createLinkSegmentIdMappingFunction(matsimWriter.getIdMapperType());

      //todo: transit vehicles we now also need!! Likely have to specify via a separate mapping
      
      /* directed connectoids as stop facilities */
      writeMatsimTransitStops(xmlWriter, zoning, stopFacilityIdMapping, linkSegmentReferenceIdMapping);

      if(routedServices != null){
        //TODO: CONTINUE HERE!!!! <-- make id mappers members
        writeMatsimTransitLines(xmlWriter, routedServices, servicesSettings);
      }
                  
      matsimWriter.writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // transit schedule
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("error while writing MATSIM transitSchedule XML element");
    }
  }

  /**
   * Constructor 
   * 
   * @param matsimWriter to use
   */
  public MatsimPtXmlWriter(final MatsimWriter<?> matsimWriter) {
    this.matsimWriter = matsimWriter;
  }
}
