package org.goplanit.matsim.converter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.geotools.geometry.jts.JTS;
import org.goplanit.converter.IdMapperFunctionFactory;
import org.goplanit.converter.IdMapperType;
import org.goplanit.converter.network.NetworkWriter;
import org.goplanit.matsim.xml.MatsimNetworkAttributes;
import org.goplanit.matsim.xml.MatsimNetworkElements;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.TransportLayerNetwork;
import org.goplanit.network.layer.MacroscopicNetworkLayerImpl;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.graph.Vertex;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.unit.Unit;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

/**
 * A class that takes a PLANit network and writes it as a MATSIM network. 
 * 
 * @author markr
  */
public class MatsimNetworkWriter extends MatsimWriter<TransportLayerNetwork<?,?>> implements NetworkWriter{
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimNetworkWriter.class.getCanonicalName());
  
  /** when external ids are used for mapping, they need not be unique, in Matsim ids must be unique, we use this map to track
   * for duplicates, if found, we append unique identifier */
  private Map<String,LongAdder> usedExternalMatsimLinkIds = new HashMap<String,LongAdder>();
  
  /** track number of MATSim nodes persisted */
  private final LongAdder matsimNodeCounter = new LongAdder();
  
  /** track number of MATSim links persisted */
  private final LongAdder matsimLinkCounter = new LongAdder();
                
  /**
   * validate the settings making sure minimal output information is available
   */
  private boolean validateSettings() {
    if(StringUtils.isNullOrBlank(getSettings().getOutputDirectory())) {
      LOGGER.severe("Matsim network output directory not set on settings, unable to persist network");
      return false;
    }
    if(StringUtils.isNullOrBlank(getSettings().getOutputFileName())) {
      LOGGER.severe("Matsim network output file name not set on settings, unable to persist network");
      return false;
    }    
        
    return true;
  }  

  /** Make sure that if external id is used that is is unique even if it is not originally
   * @param linkSegment to check for
   * @param matsimId to verify
   * @param usedExternalMatsimIds that are used already
   * @return unique externalId (if not external id then copy of original is returned
   */
  private String setUniqueExternalIdIfNeeded(MacroscopicLinkSegment linkSegment, final String matsimId, final Map<String, LongAdder> usedExternalMatsimIds) {    
    String uniqueExternalId = matsimId;
    if(getIdMapperType() == IdMapperType.EXTERNAL_ID) {
      if(usedExternalMatsimIds.containsKey(matsimId)) {
        LongAdder duplicateCount = usedExternalMatsimLinkIds.get(matsimId);
        uniqueExternalId = matsimId.concat(duplicateCount.toString());
        linkSegment.setExternalId(uniqueExternalId);
        duplicateCount.increment();
      }else {
        usedExternalMatsimIds.put(matsimId, new LongAdder());
      }
    } 
    return uniqueExternalId;
  }  
    
     
  
  /** write a MATSIM link for given PLANit link segment
   * @param xmlWriter to use
   * @param linkSegment link segment to write
   * @param planitModeToMatsimModeMapping quick mapping from PLANit mode to MATSIM mode string
   * @param linkIdMapping function to map PLANit link segment id to MATSIM link id
   * @param nodeIdMapping function to map PLANit node id to MATSIM node id
   * @throws PlanItException thrown if error
   */
  private void writeMatsimLink(
      XMLStreamWriter xmlWriter, 
      MacroscopicLinkSegment linkSegment, 
      Map<Mode, String> planitModeToMatsimModeMapping, 
      Function<MacroscopicLinkSegment, String> linkIdMapping, 
      Function<Vertex, String> nodeIdMapping) throws PlanItException {
        
    
    if(Collections.disjoint(planitModeToMatsimModeMapping.keySet(), linkSegment.getAllowedModes())) {
      /* link segment has no modes that are activated on the MATSIM network -> ignore */
      return;
    }
    
    try {
      PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimNetworkElements.LINK, getIndentLevel());           
      matsimLinkCounter.increment();
      
      /* attributes  of element*/
      {
        /** GEOGRAPHY **/
        {                    
          /* ID */
          String matsimLinkId = setUniqueExternalIdIfNeeded(linkSegment, linkIdMapping.apply(linkSegment), usedExternalMatsimLinkIds);

          xmlWriter.writeAttribute(MatsimNetworkAttributes.ID, matsimLinkId);
    
          /* FROM node */
          xmlWriter.writeAttribute(MatsimNetworkAttributes.FROM, nodeIdMapping.apply(((Node) linkSegment.getUpstreamVertex())));
          
          /* TO node */
          xmlWriter.writeAttribute(MatsimNetworkAttributes.TO, nodeIdMapping.apply(((Node) linkSegment.getDownstreamVertex())));
          
          /* LENGTH */
          xmlWriter.writeAttribute(MatsimNetworkAttributes.LENGTH, String.format("%.2f",Unit.KM.convertTo(Unit.METER, linkSegment.getParentLink().getLengthKm())));  
        }
        
        if(linkSegment.getLinkSegmentType() == null) {
          throw new PlanItException(String.format("MATSIM requires link segment type to be available on link segment (id:%d)",linkSegment.getId()));
        }
                
        /** MODELLING PARAMETERS **/
        {
          /* SPEED */
          double linkSpeedLimit = linkSegment.getPhysicalSpeedLimitKmH();
          if(getSettings().isRestrictLinkSpeedBySupportedModes()) {
            double minModeSpeed = planitModeToMatsimModeMapping.keySet().stream().map(m -> m.getMaximumSpeedKmH()).sorted().findFirst().orElse(linkSpeedLimit);
            linkSpeedLimit = Math.min(linkSpeedLimit, minModeSpeed);
          }
          xmlWriter.writeAttribute(MatsimNetworkAttributes.FREESPEED_METER_SECOND, 
              String.format("%.2f",Unit.KM_HOUR.convertTo(Unit.METER_SECOND, linkSpeedLimit)));
          
          /* CAPACITY */
          xmlWriter.writeAttribute(MatsimNetworkAttributes.CAPACITY_HOUR, String.format("%.1f",linkSegment.getCapacityOrDefaultPcuH()));
          
          /* PERMLANES */
          xmlWriter.writeAttribute(MatsimNetworkAttributes.PERMLANES, String.valueOf(linkSegment.getNumberOfLanes()));
          
          /* MODES */
          Set<String> matsimModes = new TreeSet<String>();
          for(Mode planitMode : linkSegment.getAllowedModes()) {
            if(planitModeToMatsimModeMapping.containsKey(planitMode)) {
              matsimModes.add(planitModeToMatsimModeMapping.get(planitMode));
            }
          }
          String allowedModes = matsimModes.stream().collect(Collectors.joining(","));
          xmlWriter.writeAttribute(MatsimNetworkAttributes.MODES,allowedModes);
        }
        
        /** OTHER **/
        {
          /* VOLUME not yet supported */
          
          /* ORIG ID */
          Object originalExternalId = linkSegment.getExternalId() != null ? linkSegment.getExternalId() : linkSegment.getParentLink().getExternalId();
          if(originalExternalId!= null) {
            xmlWriter.writeAttribute(MatsimNetworkAttributes.ORIGID, String.valueOf(originalExternalId));
          }
          
          /** USER DEFINED **/
          
          /* NT_CATEGORY */
          if(settings.linkNtCategoryfunction != null) {
            xmlWriter.writeAttribute(MatsimNetworkAttributes.NT_CATEGORY, settings.linkNtCategoryfunction.apply(linkSegment));
          }
          
          /* NT_CATEGORY */
          if(settings.linkNtTypefunction != null) {
            xmlWriter.writeAttribute(MatsimNetworkAttributes.NT_TYPE, settings.linkNtTypefunction.apply(linkSegment));
          }
          
          /* TYPE */
          if(settings.linkTypefunction != null) {
            xmlWriter.writeAttribute(MatsimNetworkAttributes.NT_TYPE, settings.linkTypefunction.apply(linkSegment));
          }  
          
        }                     

      }
      
      PlanitXmlWriterUtils.writeNewLine(xmlWriter);
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException(String.format("error while writing MATSIM link XML element %s (id:%d)",linkSegment.getExternalId(), linkSegment.getId()));
    }
  }    

  /** Write a PLANit link with one or two link segments as MATSIM link(s) 
   * 
   * @param xmlWriter to use
   * @param link to extract MATSIM link(s) from
   * @param planitModeIdToMatsimModeMapping quick mapping from PLANit mode to MATSIM mode string
   * @param linkIdMapping function to map PLANit link segment id to MATSIM link id
   * @param nodeIdMapping function to map PLANit node id to MATSIM node id
   * @throws PlanItException thrown if error
   */
  private void writeMatsimLink(
      XMLStreamWriter xmlWriter, 
      Link link, 
      Map<Mode, String> planitModeToMatsimModeMapping, 
      Function<MacroscopicLinkSegment, String> linkIdMapping, 
      Function<Vertex, String> nodeIdMapping) throws PlanItException {     
    
    /* A --> B */
    if(link.hasEdgeSegmentAb()) {
      writeMatsimLink(xmlWriter, (MacroscopicLinkSegment) link.getEdgeSegmentAb(), planitModeToMatsimModeMapping, linkIdMapping, nodeIdMapping);
    }
    
    /* A <-- B */
    if(link.hasEdgeSegmentBa()) {
      writeMatsimLink(xmlWriter, (MacroscopicLinkSegment) link.getEdgeSegmentBa(), planitModeToMatsimModeMapping, linkIdMapping, nodeIdMapping);
    }
    
  }  

  /** write the links
   * 
   * @param xmlWriter to use
   * @param networkLayer to extract from
   * @param linkIdMapping function to map PLANit link segment id to MATSIM link id
   * @param nodeIdMapping function to map PLANit node id to MATSIM node id 
   * @throws PlanItException thrown if error
   */
  private void writeMatsimLinks(
      XMLStreamWriter xmlWriter, 
      MacroscopicNetworkLayerImpl networkLayer, 
      Function<MacroscopicLinkSegment, String> linkIdMapping, 
      Function<Vertex, String> nodeIdMapping) throws PlanItException {   
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkElements.LINKS, true /* ++indent */);
      
      Map<Mode, String> planitModeToMatsimModeMapping = settings.collectActivatedPlanitModeToMatsimModeMapping(networkLayer);
      /* write link(segments) one by one */
      for(Link link: networkLayer.getLinks()) {
        writeMatsimLink(xmlWriter, link, planitModeToMatsimModeMapping, linkIdMapping, nodeIdMapping);
      }
      
      writeEndElementNewLine(xmlWriter, true /*-- indent */); // LINKS
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSIM nodes XML element");
    }    
  }
   
  
  /** Write a PLANit node as MATSIM node 
   * @param xmlWriter to use
   * @param node to write
   * @param nodeIdMapping apply to collect node id to write
   * @throws PlanItException 
   */
  private void writeMatsimNode(XMLStreamWriter xmlWriter, Node node, Function<Vertex, String> nodeIdMapping) throws PlanItException {
    try {
      PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimNetworkElements.NODE, getIndentLevel());           
      matsimNodeCounter.increment();
      
      /* attributes  of element*/
      {
        /* ID */
        xmlWriter.writeAttribute(MatsimNetworkAttributes.ID, nodeIdMapping.apply(node));
        
        /* geometry of the node (optional) */
        Coordinate nodeCoordinate = extractDestinationCrsCompatibleCoordinate(node.getPosition());
        if(nodeCoordinate != null) {        
          /* X */
          xmlWriter.writeAttribute(MatsimNetworkAttributes.X, settings.getDecimalFormat().format(nodeCoordinate.x));
          /* Y */
          xmlWriter.writeAttribute(MatsimNetworkAttributes.Y, settings.getDecimalFormat().format(nodeCoordinate.y));
          /* Z coordinate not yet supported */
        }
        
        /* TYPE not yet supported */
        
        /* ORIGID not yet supported */
      }
      
      PlanitXmlWriterUtils.writeNewLine(xmlWriter);
    } catch (XMLStreamException | TransformException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSIM node XML element %s (id:%d)",node.getExternalId(), node.getId());
    }
  }  
  
  /** write the nodes
   * @param xmlWriter to use
   * @param networkLayer to extract from
   * @param nodeIdMapping function to map PLANit node id to MATSIM node id
   * @throws PlanItException thrown if error
   */
  private void writeMatsimNodes(XMLStreamWriter xmlWriter, MacroscopicNetworkLayerImpl networkLayer, Function<Vertex, String> nodeIdMapping) throws PlanItException {
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkElements.NODES, true /* ++indent */);
      
      /* write nodes one by one */
      for(Node node : networkLayer.getNodes()) {
        writeMatsimNode(xmlWriter, node, nodeIdMapping);
      }
      
      writeEndElementNewLine(xmlWriter, true /*-- indent */); // NODES
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSIM nodes XML element");
    }
  }  
  

  /** write the body of the MATSIM network XML file based on the PLANit network contents
   * 
   * @param xmlWriter the writer
   * @param networkLayer to persist
   * @throws PlanItException thrown if error
   */
  private void writeMatsimNetworkXML(XMLStreamWriter xmlWriter, MacroscopicNetworkLayerImpl networkLayer) throws PlanItException {
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkElements.NETWORK, true /* add indentation*/);
      
      /* mapping for how to generated id's for various entities */
      Function<Vertex, String> nodeIdMapping = IdMapperFunctionFactory.createVertexIdMappingFunction(getIdMapperType());
      Function<MacroscopicLinkSegment, String> linkIdMapping = IdMapperFunctionFactory.createLinkSegmentIdMappingFunction(getIdMapperType());
      
      /* nodes */
      writeMatsimNodes(xmlWriter, networkLayer, nodeIdMapping);
      
      /* links */
      writeMatsimLinks(xmlWriter, networkLayer, linkIdMapping, nodeIdMapping);
      
      writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // NETWORK
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSIM network XML element");
    }
  }     
            
  /**
   * Log some aggregate stats on the MATSim writer regarding the number of elements persisted
   */
  private void logWriterStats() {
    LOGGER.info(String.format("[STATS] created %d nodes",matsimNodeCounter.longValue()));
    LOGGER.info(String.format("[STATS] created %d links",matsimLinkCounter.longValue()));
  }

  /**
   * MATSIM writer settings 
   */
  protected final MatsimNetworkWriterSettings settings;
  
  /**
   * write the xml MATSIM network
   * 
   * @param networkLayer to draw from
   * @throws PlanItException thrown if error
   */
  protected void writeXmlNetworkFile(MacroscopicNetworkLayerImpl networkLayer) throws PlanItException { 
    Path matsimNetworkPath =  Paths.get(getSettings().getOutputDirectory(), getSettings().getOutputFileName().concat(DEFAULT_FILE_NAME_EXTENSION));    
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair = PlanitXmlWriterUtils.createXMLWriter(matsimNetworkPath);
    
    try {
      /* start */
      PlanitXmlWriterUtils.startXmlDocument(xmlFileWriterPair.first(), DOCTYPE);
      
      /* body */
      writeMatsimNetworkXML(xmlFileWriterPair.first(), networkLayer);
      
      /* end */
      PlanitXmlWriterUtils.endXmlDocument(xmlFileWriterPair);
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException(String.format("error while persisting MATSIM network to %s", matsimNetworkPath));
    }
  }  
  
  /**
   * Create detailed geometry file compatible with VIA viewer
   * 
   * @param networkLayer to draw from
   * @throws PlanItException thrown if error
   */
  protected void writeDetailedGeometryFile(MacroscopicNetworkLayerImpl networkLayer) throws PlanItException {
    Path matsimNetworkGeometryPath =  Paths.get(getSettings().getOutputDirectory(), DEFAULT_NETWORK_GEOMETRY_FILE_NAME.concat(DEFAULT_NETWORK_GEOMETRY_FILE_NAME_EXTENSION)).toAbsolutePath();
    LOGGER.info(String.format("persisting MATSIM network geometry to: %s",matsimNetworkGeometryPath.toString()));
    
    try {
      CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(matsimNetworkGeometryPath.toFile()), CSVFormat.TDF);      
      csvPrinter.printRecord("LINK_ID", "GEOMETRY");
      
      Function<MacroscopicLinkSegment, String> linkIdMapping = IdMapperFunctionFactory.createLinkSegmentIdMappingFunction(getIdMapperType());
      for(MacroscopicLinkSegment linkSegment : networkLayer.getLinkSegments()) {
        
        /* extract geometry to write */
        LineString destinationCrsGeometry = null;
        if(getDestinationCrsTransformer()!=null) {
          destinationCrsGeometry = ((LineString)JTS.transform(linkSegment.getParentLink().getGeometry(), getDestinationCrsTransformer()));
        }else {
          destinationCrsGeometry = linkSegment.getParentLink().getGeometry();  
        }        
        if(destinationCrsGeometry==null) {
          LOGGER.severe(String.format("geometry unavailable for link (segment id:%d) even though request for detailed geometry is made, link ignored",linkSegment.getId()));
          continue;
        }
        
        /* get correct coordinate sequence, reverse when segment is reverse direction */
        Coordinate[] coordinates = linkSegment.isDirectionAb() ? destinationCrsGeometry.getCoordinates() : destinationCrsGeometry.reverse().getCoordinates();
        
        /* only when it has internal coordinates */
        if(coordinates.length > 2) {
          String lineStringString = "LINESTRING (";
          int firstInternal = 1;
          int lastInternal = coordinates.length-1;
          for(int index = firstInternal ; index < lastInternal; ++index) {
            Coordinate coordinate = coordinates[index];           
            if(index>firstInternal) {
              lineStringString += ",";
            }         
            lineStringString += String.format("%s %s", settings.getDecimalFormat().format(coordinate.x),settings.getDecimalFormat().format(coordinate.y));
          }
          lineStringString += ")";
          csvPrinter.printRecord(linkIdMapping.apply(linkSegment),lineStringString);          
        }
      }
      csvPrinter.close();
    } catch (IOException | TransformException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("unable to write detailed gemoetry file %d an error occured during writing", e);
    }
  }  
  
  
  /** the doc type of MATSIM network */
  public static final String DOCTYPE = "<!DOCTYPE network SYSTEM \"http://www.matsim.org/files/dtd/network_v2.dtd\">";  
        
  /**
   * default names used for MATSIM network file that is being generated
   */
  public static final String DEFAULT_NETWORK_GEOMETRY_FILE_NAME_EXTENSION = ".txt";      
  
  /**
   * default names used for MATSIM network file that is being generated
   */
  public static final String DEFAULT_NETWORK_GEOMETRY_FILE_NAME = "network_geometry";
  
  /**
   * Default constructor. Initialisng with default output directory and country name on the settings
   */
  public MatsimNetworkWriter() {
    this(new MatsimNetworkWriterSettings(null));
  }  
        
  /**
   * Constructor
   * 
   * @param networkSettings network settings to use
   */
  protected MatsimNetworkWriter(MatsimNetworkWriterSettings networkSettings) {
    super(IdMapperType.ID);        
    
    /* config settings for writer are found here */
    this.settings = networkSettings;
  }  


  /**
   * {@inheritDoc}
   */
  @Override
  public void write(TransportLayerNetwork<?,?> network) throws PlanItException {
    PlanItException.throwIfNull(network, "network is null, cannot write undefined network to MATSIM format");
    
    boolean networkValid = validateNetwork(network);
    if(!networkValid) {
      return;
    }
    boolean settingsValid = validateSettings();
    if(!settingsValid) {
      return;
    }
    
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) network;
    
    /* CRS */
    CoordinateReferenceSystem destinationCrs = 
        prepareCoordinateReferenceSystem(macroscopicNetwork, getSettings().getCountry(), getSettings().getDestinationCoordinateReferenceSystem());
    settings.setDestinationCoordinateReferenceSystem(destinationCrs);
    
    /* log settings */
    settings.logSettings(macroscopicNetwork);
    
    /* write */
    final MacroscopicNetworkLayerImpl macroscopicPhysicalNetworkLayer = (MacroscopicNetworkLayerImpl)macroscopicNetwork.getTransportLayers().getFirst();
    
    writeXmlNetworkFile(macroscopicPhysicalNetworkLayer);
    if(settings.isGenerateDetailedLinkGeometryFile()) {
      writeDetailedGeometryFile(macroscopicPhysicalNetworkLayer);
    }
    
    logWriterStats();
  }
    

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    getSettings().reset();
    matsimNodeCounter.reset();
    matsimLinkCounter.reset();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MatsimNetworkWriterSettings getSettings() {
    return settings;
  }

}
