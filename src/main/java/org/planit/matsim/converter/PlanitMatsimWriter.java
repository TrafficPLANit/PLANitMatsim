package org.planit.matsim.converter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.planit.matsim.xml.MatsimNetworkXmlAttributes;
import org.planit.matsim.xml.MatsimNetworkXmlElements;
import org.planit.network.converter.IdMapper;
import org.planit.network.converter.NetworkWriterImpl;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.id.IdGenerator;
import org.planit.utils.id.IdGroupingToken;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.unit.UnitUtils;
import org.planit.utils.unit.Units;

/**
 * A class that takes a PLANit network and writes it as a MATSIM network. 
 * MATSIM Id's are by default mapped based on the PLANit external id's.
 * 
 * @author markr
  */
public class PlanitMatsimWriter extends NetworkWriterImpl {
  
  /**
   * the logger of this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimWriter.class.getCanonicalName());
    
  /** id token to use to generate id's if required */
  private IdGroupingToken matsimWriterIdToken; 
  
  /** track indentation level */
  private int indentLevel = 0;  
      
  /** write a new line to the stream, e.g. "\n"
   * @param xmlWriter to use
   * @throws XMLStreamException thrown if error 
   */
  private void writeNewLine(XMLStreamWriter xmlWriter) throws XMLStreamException {
    xmlWriter.writeCharacters("\n");
  }
  
  /** add indentation to stream at current indentation level
   * @param xmlWriter to use
   * @throws XMLStreamException thrown if error
   */
  private void writeIndentation(XMLStreamWriter xmlWriter) throws XMLStreamException {
    for(int index=0;index<indentLevel;++index) {
      xmlWriter.writeCharacters("\t");
    }
  }  
  
  /** increase indentation level
   */
  private void increaseIndentation() throws XMLStreamException {
    ++indentLevel;
  }
  
  /** decrease indentation level
   */
  private void decreaseIndentation() throws XMLStreamException {
    --indentLevel;
  }
  
  /**
   * write a empty element (with indentation), e.g. {@code <xmlElementName/>}
   * 
   * @param xmlWriter to use
   * @param xmlElementName element to start tag, e.g. <xmlElementName>\n
   * @throws XMLStreamException thrown if error
   */
  private void writeEmptyElement(XMLStreamWriter xmlWriter, String xmlElementName) throws XMLStreamException {
    writeIndentation(xmlWriter);
    xmlWriter.writeEmptyElement(xmlElementName);
  }  
  
  /**
   * write a start element (with indentation) and add newline afterwards
   * 
   * @param xmlWriter to use
   * @param xmlElementName element to start tag, e.g. {@code <xmlElementName>}
   * @throws XMLStreamException thrown if error
   */
  private void writeStartElementNewLine(XMLStreamWriter xmlWriter, String xmlElementName) throws XMLStreamException {
    writeIndentation(xmlWriter);
    xmlWriter.writeStartElement(xmlElementName);
    writeNewLine(xmlWriter);
  }
  
  /**
   * write a start element and add newline afterwards
   * 
   * @param xmlWriter to use
   * @param xmlElementName element to start tag, e.g. {@code <xmlElementName>}
   * @param increaseIndentation when true, increase indentation after this element has been written
   * @throws XMLStreamException thrown if error
   */
  private void writeStartElementNewLine(XMLStreamWriter xmlWriter, String xmlElementName, boolean increaseIndentation) throws XMLStreamException {
    writeStartElementNewLine(xmlWriter, xmlElementName);
    if(increaseIndentation) {
      increaseIndentation();
    }
  }  
  
  /**
   * write an end element and add newline afterwards
   * 
   * @param xmlWriter to use
   * @throws XMLStreamException thrown if error
   */  
  private void writeEndElementNewLine(XMLStreamWriter xmlWriter) throws XMLStreamException {
    writeIndentation(xmlWriter);    
    xmlWriter.writeEndElement();
    writeNewLine(xmlWriter);
  }  
  
  /**
   * write an end element and add newline afterwards
   * 
   * @param xmlWriter to use
   * @param decreaseIndentation when true decrease indentation level before this element has been written
   * @throws XMLStreamException thrown if error
   */  
  private void writeEndElementNewLine(XMLStreamWriter xmlWriter, boolean decreaseIndentation) throws XMLStreamException {
    if(decreaseIndentation) {
      decreaseIndentation(); 
    }
    writeEndElementNewLine(xmlWriter);
  }  
  
  /**
   * create the writer
   * @param matsimNetworkPath to create the writer for
   * @return created xml writer
   * @throws PlanItException thrown if error
   */
  private Pair<XMLStreamWriter,FileWriter> createXMLWriter(Path matsimNetworkPath) throws PlanItException {
    Path absoluteMatsimPath = matsimNetworkPath.toAbsolutePath();
    LOGGER.info(String.format("persisting MATSIM network to: %s",absoluteMatsimPath.toString()));
    
    
    /* create dir if not present */
    File directory = absoluteMatsimPath.getParent().toFile();
    if(!directory.exists()) {      
      if(!directory.mkdirs()) {
        throw new PlanItException(String.format("unable to create MATSIM network output directory %s",directory.toString()));
      }      
      LOGGER.info(String.format("created MATSIM network output directory %s",directory.toString()));
    }
    
    /* create writer */
    try {    
      FileWriter theWriter = new FileWriter(absoluteMatsimPath.toFile());
      XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
      return new Pair<XMLStreamWriter,FileWriter>(xmlOutputFactory.createXMLStreamWriter(theWriter),theWriter);
    } catch (XMLStreamException | IOException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Could not instantiate XML writer for MATSIM network",e);
    }  
  }  
  
  /** end the xml document and close the writers, streams etc.
   * 
   * @param xmlFileWriterPair writer pair
   * @throws XMLStreamException thrown if error
   * @throws IOException thrown if error
   */
  private void endXmlDocument(Pair<XMLStreamWriter, FileWriter> xmlFileWriterPair) throws XMLStreamException, IOException {
    XMLStreamWriter xmlWriter = xmlFileWriterPair.getFirst();
    FileWriter fileWriter = xmlFileWriterPair.getSecond();
    xmlWriter.writeEndDocument();
   
    xmlWriter.flush();
    xmlWriter.close();

    fileWriter.flush();
    fileWriter.close();
  }

  /** start xml document
   * @param xmlFileWriterPair the writer pair
   * @throws XMLStreamException thrown if exception
   */
  private void startXmlDocument(Pair<XMLStreamWriter, FileWriter> xmlFileWriterPair) throws XMLStreamException {
    XMLStreamWriter xmlWriter = xmlFileWriterPair.getFirst();
    
    /* <xml tag> */
    xmlWriter.writeStartDocument("UTF-8", "1.0");
    
    /* less ugly by adding new lines */
    writeNewLine(xmlWriter);
    
    /* DOCTYPE reference (MATSIM is based on dtd rather than schema)*/
    xmlWriter.writeDTD(DOCTYPE);
    writeNewLine(xmlWriter);
  }   
  
  /** create a function that takes a link segment and generates the appropriate MATSIM link id based on the user configuration
   * 
   * @return function that generates link (segment) id's for MATSIM link output
   * @throws PlanItException thrown if error
   */
  private Function<MacroscopicLinkSegment, String> createLinkSegmentIdValueGenerator() throws PlanItException {
    switch (getIdMapper()) {
    case ID:
      return (macroscopicLinkSegment) -> { return Long.toString(macroscopicLinkSegment.getId());};
    case EXTERNAL_ID:
      return (macroscopicLinkSegment) -> {
          /* when present on link segment use that external id, otherwise try link */
          if(macroscopicLinkSegment.getExternalId() != null) {
            return String.format("%s",macroscopicLinkSegment.getExternalId());          
          }else if(macroscopicLinkSegment.getParentLink() != null && macroscopicLinkSegment.getParentLink().getExternalId() != null) {
            return String.format("%s_%s",
                macroscopicLinkSegment.getParentLink().getExternalId(),
                macroscopicLinkSegment.isDirectionAb() ? "ab" : "ba");                
          }else {
            LOGGER.severe(String.format("unable to extract id for MATSIM link, PLANit link segment external id not available or parent link missing (id:%d)",macroscopicLinkSegment.getId()));
            return "-1";
          }
        };      
    case GENERATED:
      return (macroscopicLinkSegment) -> { return Long.toString(IdGenerator.generateId(matsimWriterIdToken, MacroscopicLinkSegment.class));};
    default:
      throw new PlanItException(String.format("unknown id mapping type found for MATSIM nodes %s",getIdMapper().toString()));
    }
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
      writeEmptyElement(xmlWriter, MatsimNetworkXmlElements.LINK);           
      
      /* attributes  of element*/
      {
        /** GEOGRAPHY **/
        {
          /* ID */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.ID, linkIdMapping.apply(linkSegment));
    
          /* FROM node */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.FROM, nodeIdMapping.apply((Node) linkSegment.getUpstreamVertex()));
          
          /* TO node */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.TO, nodeIdMapping.apply((Node) linkSegment.getDownstreamVertex()));
          
          /* LENGTH */
          xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.LENGTH, String.format("%.2f",linkSegment.getParentLink().getLengthKm()*1000));  
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
      
      writeNewLine(xmlWriter);
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
   * @param network to extract from
   * @param linkIdMapping function to map PLANit link segment id to MATSIM link id
   * @param nodeIdMapping function to map PLANit node id to MATSIM node id 
   * @throws PlanItException thrown if error
   */
  private void writeMatsimLinks(
      XMLStreamWriter xmlWriter, MacroscopicNetwork network, Function<MacroscopicLinkSegment, String> linkIdMapping, Function<Node, String> nodeIdMapping) throws PlanItException {   
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkXmlElements.LINKS, true /* ++indent */);
      
      Map<Mode, String> planitModeToMatsimModeMapping = settings.createPlanitModeToMatsimModeMapping(network);
      /* write link(segments) one by one */
      for(Link link : network.links) {
        writeMatsimLink(xmlWriter, link, planitModeToMatsimModeMapping, linkIdMapping, nodeIdMapping);
      }
      
      writeEndElementNewLine(xmlWriter, true /*-- indent */); // LINKS
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSIM nodes XML element");
    }    
  }
  

  /** create a function that takes a node and generates the appropriate id based on the user configuration
   * 
   * @return function that generates node id's for MATSIM node output
   * @throws PlanItException thrown if error
   */
  private Function<Node, String> createNodeIdValueGenerator() throws PlanItException {
    switch (getIdMapper()) {
    case ID:
      return (node) -> { return Long.toString(node.getId());};
    case EXTERNAL_ID:
      return (node) -> { return String.format("%s",node.getExternalId());};      
    case GENERATED:
      return (node) -> { return Long.toString(IdGenerator.generateId(matsimWriterIdToken, Node.class));};
    default:
      throw new PlanItException(String.format("unknown id mapping type found for MATSIM nodes %s",getIdMapper().toString()));
    }
  }    
  
  /** Write a PLANit node as MATSIM node 
   * @param xmlWriter to use
   * @param node to write
   * @param nodeIdGenerator apply to collect node id to write
   * @throws PlanItException 
   */
  private void writeMatsimNode(XMLStreamWriter xmlWriter, Node node, Function<Node, String> nodeIdGenerator) throws PlanItException {
    try {
      writeEmptyElement(xmlWriter, MatsimNetworkXmlElements.NODE);           
            
      /* attributes  of element*/
      {
        /* ID */
        xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.ID, nodeIdGenerator.apply(node));
        
        double[]  nodeCoordinate = node.getCentrePointGeometry().getDirectPosition().getCoordinate();
        /* X */
        xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.X, String.format(coordinateDecimalFormat,nodeCoordinate[0]));
        /* Y */
        xmlWriter.writeAttribute(MatsimNetworkXmlAttributes.Y, String.format(coordinateDecimalFormat,nodeCoordinate[1]));
        /* Z coordinate not yet supported */
        
        /* TYPE not yet supported */
        
        /* ORIGID not yet supported */
      }
      
      writeNewLine(xmlWriter);
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException(String.format("error while writing MATSIM node XML element %s (id:%d)",node.getExternalId(), node.getId()));
    }
  }  
  
  /** write the nodes
   * @param xmlWriter to use
   * @param network to extract from
   * @param nodeIdMapping function to map PLANit node id to MATSIM node id
   * @throws PlanItException thrown if error
   */
  private void writeMatsimNodes(XMLStreamWriter xmlWriter, MacroscopicNetwork network, Function<Node, String> nodeIdMapping) throws PlanItException {
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkXmlElements.NODES, true /* ++indent */);
      
      /* write nodes one by one */
      for(Node node : network.nodes) {
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
   * @param network to persist
   * @throws PlanItException thrown if error
   */
  private void writeMatsimNetworkXML(XMLStreamWriter xmlWriter, MacroscopicNetwork network) throws PlanItException {
      try {
        writeStartElementNewLine(xmlWriter,MatsimNetworkXmlElements.NETWORK, true /* add indentation*/);
        
        /* mapping for how to generated id's for various entities */
        Function<Node, String> nodeIdMapping = createNodeIdValueGenerator();
        Function<MacroscopicLinkSegment, String> linkIdMapping = createLinkSegmentIdValueGenerator();
        
        /* nodes */
        writeMatsimNodes(xmlWriter, network, nodeIdMapping);
        
        /* links */
        writeMatsimLinks(xmlWriter, network, linkIdMapping, nodeIdMapping);
        
        writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // NETWORK
      } catch (XMLStreamException e) {
        LOGGER.severe(e.getMessage());
        throw new PlanItException("error while writing MATSIM network XML element");
      }
  }  
  
  /**
   * the output directory on where to persist the MATSIM network
   */
  protected final String outputDirectory;  
  
  /**
   * the decimal format to apply to coordinates
   */
  protected String coordinateDecimalFormat;  
  
  /**
   * the output file name to use, default is set to DEFAULT_NETWORK_FILE_NAME
   */
  protected String outputFileName = DEFAULT_NETWORK_FILE_NAME;
        
  /**
   * MATSIM writer settings 
   */
  protected final PlanitMatsimWriterSettings settings;
  
  /** the doc type of MATSIM network */
  public static final String DOCTYPE = "<!DOCTYPE network SYSTEM \"http://www.matsim.org/files/dtd/network_v2.dtd\">";  
    
  /**
   * default names used for MATSIM network file that is being generated
   */
  public static final String DEFAULT_NETWORK_FILE_NAME = "network.xml";  
      
  
  /**
   * Constructor
   * 
   * @param outputDirectory to use
   */
  public PlanitMatsimWriter(String outputDirectory) {
    super(IdMapper.EXTERNAL_ID);    
    this.matsimWriterIdToken = IdGenerator.createIdGroupingToken(PlanitMatsimWriter.class.getCanonicalName());    
    this.outputDirectory = outputDirectory;
    
    /* config settings for writer are found here */
    this.settings = new PlanitMatsimWriterSettings();
  }

  /**
   * {@inheritDoc}
   * @throws PlanItException 
   */
  @Override
  public void write(MacroscopicNetwork network) throws PlanItException {
    PlanItException.throwIfNull(network, "network is null, cannot write undefined network to MATSIM format");
    Path matsimNetworkPath =  Paths.get(outputDirectory, outputFileName);    
    Pair<XMLStreamWriter,FileWriter> xmlFileWriterPair = createXMLWriter(matsimNetworkPath);
    
    coordinateDecimalFormat = String.format("%%.%df", settings.getCoordinateDecimals());
    
    settings.logSettings();            
    try {
      /* start */
      startXmlDocument(xmlFileWriterPair);
      
      /* body */
      writeMatsimNetworkXML(xmlFileWriterPair.getFirst(), network);
      
      /* end */
      endXmlDocument(xmlFileWriterPair);
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException(String.format("error while persisting MATSIM network to %s", matsimNetworkPath));
    }
  }
   
  
  /** Collect the settings
   * 
   * @return settings for configuration
   */
  public PlanitMatsimWriterSettings getSettings() {
    return this.settings;
  }

}
