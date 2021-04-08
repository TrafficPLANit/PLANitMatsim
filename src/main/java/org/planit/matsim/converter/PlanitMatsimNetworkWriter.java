package org.planit.matsim.converter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.geotools.geometry.jts.JTS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.planit.converter.IdMapperFunctionFactory;
import org.planit.converter.IdMapperType;
import org.planit.converter.network.NetworkWriter;
import org.planit.matsim.xml.MatsimNetworkXmlAttributes;
import org.planit.matsim.xml.MatsimNetworkXmlElements;
import org.planit.network.InfrastructureNetwork;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.unit.UnitUtils;
import org.planit.utils.unit.Units;
import org.planit.utils.xml.PlanitXmlWriterUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

/**
 * A class that takes a PLANit network and writes it as a MATSIM network. 
 * 
 * @author markr
  */
public class PlanitMatsimNetworkWriter extends PlanitMatsimWriter<InfrastructureNetwork<?,?>> implements NetworkWriter{
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimNetworkWriter.class.getCanonicalName());
  
  /** when external ids are used for mapping, they need not be unqiue, in Matsim ids must be unique, we use this map to track
   * for duplicates, if found, we append unique identifier */
  private Map<String,LongAdder> usedExternalMatsimLinkIds = new HashMap<String,LongAdder>();
      
        
  /** validate the network, log or throw when issues are found
   * 
   * @param network to validate
   * @throws PlanItException thrown if invalid
   */
  private void validateNetwork(InfrastructureNetwork<?, ?> network) throws PlanItException {
    if (!(network instanceof MacroscopicNetwork)) {
      throw new PlanItException("Matsim writer currently only supports writing macroscopic networks");
    }
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) network;
    
    if(macroscopicNetwork.infrastructureLayers.size()!=1) {
      throw new PlanItException(String.format("Matsim writer currently only supports networks with a single layer, the provided network has %d",network.infrastructureLayers.size()));
    }
    
    if(!(network.infrastructureLayers.getFirst() instanceof MacroscopicPhysicalNetwork)) {
      throw new PlanItException(String.format("Matsim only supports macroscopic physical network layers, the provided network is of a different type"));
    } 
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
      Function<Node, String> nodeIdMapping) throws PlanItException {
        
    
    if(Collections.disjoint(planitModeToMatsimModeMapping.keySet(), linkSegment.getAllowedModes())) {
      /* link segment has no modes that are activated on the MATSIM network -> ignore */
      return;
    }
    
    try {
      PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimNetworkXmlElements.LINK, indentLevel);           
      
      /* attributes  of element*/
      {
        /** GEOGRAPHY **/
        {
          /* ID */
          String matsimLinkId = setUniqueExternalIdIfNeeded(linkSegment, linkIdMapping.apply(linkSegment), usedExternalMatsimLinkIds);

          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.ID, matsimLinkId);
    
          /* FROM node */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.FROM, nodeIdMapping.apply(((Node) linkSegment.getUpstreamVertex())));
          
          /* TO node */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.TO, nodeIdMapping.apply(((Node) linkSegment.getDownstreamVertex())));
          
          /* LENGTH */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.LENGTH, String.format("%.2f",UnitUtils.convert(Units.KM, Units.METER, linkSegment.getParentLink().getLengthKm())));  
        }
        
        if(linkSegment.getLinkSegmentType() == null) {
          throw new PlanItException(String.format("MATSIM requires link segment type to be available on link segment (id:%d)",linkSegment.getId()));
        }
                
        /** MODELLING PARAMETERS **/
        {
          /* SPEED */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.FREESPEED_METER_SECOND, 
              String.format("%.2f",UnitUtils.convert(Units.KM_HOUR, Units.METER_SECOND, linkSegment.getPhysicalSpeedLimitKmH())));
          
          /* CAPACITY */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.CAPACITY_HOUR, String.format("%.1f",linkSegment.computeCapacityPcuH()));
          
          /* PERMLANES */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.PERMLANES, String.valueOf(linkSegment.getNumberOfLanes()));
          
          /* MODES */
          String allowedModes = linkSegment.getAllowedModes().stream().map(mode -> planitModeToMatsimModeMapping.get(mode)).collect(Collectors.joining(","));
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.MODES,allowedModes);
        }
        
        /** OTHER **/
        {
          /* VOLUME not yet supported */
          
          /* ORIG ID */
          Object originalExternalId = linkSegment.getExternalId() != null ? linkSegment.getExternalId() : linkSegment.getParentLink().getExternalId();
          if(originalExternalId!= null) {
            xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.ORIGID, String.valueOf(originalExternalId));
          }
          
          /** USER DEFINED **/
          
          /* NT_CATEGORY */
          if(settings.linkNtCategoryfunction != null) {
            xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.NT_CATEGORY, settings.linkNtCategoryfunction.apply(linkSegment));
          }
          
          /* NT_CATEGORY */
          if(settings.linkNtTypefunction != null) {
            xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.NT_TYPE, settings.linkNtTypefunction.apply(linkSegment));
          }
          
          /* TYPE */
          if(settings.linkTypefunction != null) {
            xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.NT_TYPE, settings.linkTypefunction.apply(linkSegment));
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
      Function<Node, String> nodeIdMapping) throws PlanItException {     
    
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
      MacroscopicPhysicalNetwork networkLayer, 
      Function<MacroscopicLinkSegment, String> linkIdMapping, 
      Function<Node, String> nodeIdMapping) throws PlanItException {   
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkXmlElements.LINKS, true /* ++indent */);
      
      Map<Mode, String> planitModeToMatsimModeMapping = settings.createPlanitModeToMatsimModeMapping(networkLayer);
      /* write link(segments) one by one */
      for(Link link: networkLayer.links) {
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
  private void writeMatsimNode(XMLStreamWriter xmlWriter, Node node, Function<Node, String> nodeIdMapping) throws PlanItException {
    try {
      PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimNetworkXmlElements.NODE, indentLevel);           
            
      /* attributes  of element*/
      {
        /* ID */
        xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.ID, nodeIdMapping.apply(node));
        
        /* geometry of the node (optional) */
        Coordinate nodeCoordinate = extractDestinationCrsCompatibleCoordinate(node.getPosition());
        if(nodeCoordinate != null) {        
          /* X */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.X, settings.getDecimalFormat().format(nodeCoordinate.x));
          /* Y */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.Y, settings.getDecimalFormat().format(nodeCoordinate.y));
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
  private void writeMatsimNodes(XMLStreamWriter xmlWriter, MacroscopicPhysicalNetwork networkLayer, Function<Node, String> nodeIdMapping) throws PlanItException {
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkXmlElements.NODES, true /* ++indent */);
      
      /* write nodes one by one */
      for(Node node : networkLayer.nodes) {
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
  private void writeMatsimNetworkXML(XMLStreamWriter xmlWriter, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkXmlElements.NETWORK, true /* add indentation*/);
      
      /* mapping for how to generated id's for various entities */
      Function<Node, String> nodeIdMapping = IdMapperFunctionFactory.createNodeIdMappingFunction(getIdMapperType());
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
   * the output file name to use, default is set to DEFAULT_NETWORK_FILE_NAME
   */
  protected String outputFileName = DEFAULT_NETWORK_FILE_NAME;
        
  /**
   * MATSIM writer settings 
   */
  protected final PlanitMatsimNetworkWriterSettings settings;
  
  /**
   * write the xml MATSIM network
   * 
   * @param networkLayer to draw from
   * @throws PlanItException thrown if error
   */
  protected void writeXmlNetworkFile(MacroscopicPhysicalNetwork networkLayer) throws PlanItException { 
    Path matsimNetworkPath =  Paths.get(outputDirectory, outputFileName.concat(DEFAULT_FILE_NAME_EXTENSION));    
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair = PlanitXmlWriterUtils.createXMLWriter(matsimNetworkPath);
    LOGGER.info(String.format("Persisting MATSIM network to: %s",matsimNetworkPath.toString()));
    
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
   * @throws PlanItException 
   */
  protected void writeDetailedGeometryFile(MacroscopicPhysicalNetwork networkLayer) throws PlanItException {
    Path matsimNetworkGeometryPath =  Paths.get(outputDirectory, DEFAULT_NETWORK_GEOMETRY_FILE_NAME.concat(DEFAULT_NETWORK_GEOMETRY_FILE_NAME_EXTENSION)).toAbsolutePath();
    LOGGER.info(String.format("persisting MATSIM network geometry to: %s",matsimNetworkGeometryPath.toString()));
    
    try {
      CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(matsimNetworkGeometryPath.toFile()), CSVFormat.TDF);      
      csvPrinter.printRecord("LINK_ID", "GEOMETRY");
      
      Function<MacroscopicLinkSegment, String> linkIdMapping = IdMapperFunctionFactory.createLinkSegmentIdMappingFunction(getIdMapperType());
      for(MacroscopicLinkSegment linkSegment : networkLayer.linkSegments) {
        
        /* extract geometry to write */
        LineString destinationCrsGeometry = null;
        if(destinationCrsTransformer!=null) {
          destinationCrsGeometry = ((LineString)JTS.transform(linkSegment.getParentLink().getGeometry(), destinationCrsTransformer));
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
  public static final String DEFAULT_NETWORK_FILE_NAME = "network";
    
  /**
   * default names used for MATSIM network file that is being generated
   */
  public static final String DEFAULT_NETWORK_GEOMETRY_FILE_NAME_EXTENSION = ".txt";      
  
  /**
   * default names used for MATSIM network file that is being generated
   */
  public static final String DEFAULT_NETWORK_GEOMETRY_FILE_NAME = "network_geometry";     
        
  /**
   * Constructor
   * 
   * @param outputDirectory to use
   * @param networkSettings network settings to use
   */
  protected PlanitMatsimNetworkWriter(String outputDirectory, PlanitMatsimNetworkWriterSettings networkSettings) {
    super(IdMapperType.ID, outputDirectory);        
    
    /* config settings for writer are found here */
    this.settings = networkSettings;
  }  

  /**
   * {@inheritDoc}
   * @throws PlanItException 
   */
  @Override
  public void write(InfrastructureNetwork<?,?> network) throws PlanItException {
    PlanItException.throwIfNull(network, "network is null, cannot write undefined network to MATSIM format");
    
    validateNetwork(network);  
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) network;
    
    /* CRS */
    CoordinateReferenceSystem destinationCrs = prepareCoordinateReferenceSystem(macroscopicNetwork, settings.getCountry(), settings.getDestinationCoordinateReferenceSystem());
    settings.setDestinationCoordinateReferenceSystem(destinationCrs);
    
    /* log settings */
    settings.logSettings();
    
    /* write */
    final MacroscopicPhysicalNetwork macroscopicPhysicalNetworkLayer = (MacroscopicPhysicalNetwork)macroscopicNetwork.infrastructureLayers.getFirst();
    
    writeXmlNetworkFile(macroscopicPhysicalNetworkLayer);
    if(settings.isGenerateDetailedLinkGeometryFile()) {
      writeDetailedGeometryFile(macroscopicPhysicalNetworkLayer);
    }
  }
    

  /** Collect the settings
   * 
   * @return settings for configuration
   */
  public PlanitMatsimNetworkWriterSettings getSettings() {
    return this.settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    //TODO
  }

}