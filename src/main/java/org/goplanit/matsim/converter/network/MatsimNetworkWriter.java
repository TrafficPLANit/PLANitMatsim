package org.goplanit.matsim.converter.network;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.goplanit.converter.idmapping.IdMapperFunctionFactory;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.matsim.util.MatsimNetworkWriterUtils;
import org.goplanit.matsim.xml.MatsimAttributes;
import org.goplanit.matsim.xml.MatsimTransitAttributes;
import org.goplanit.utils.graph.directed.BannedMovement;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.converter.idmapping.NetworkIdMapper;
import org.goplanit.converter.network.NetworkWriter;
import org.goplanit.matsim.xml.MatsimNetworkAttributes;
import org.goplanit.matsim.xml.MatsimNetworkElements;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.LayeredNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.unit.Unit;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

/**
 * A class that takes a PLANit network and writes it as a MATSIM network. 
 * 
 * @author markr
  */
public class MatsimNetworkWriter extends MatsimWriter<LayeredNetwork<?,?>> implements NetworkWriter{
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimNetworkWriter.class.getCanonicalName());
  
  /** when external ids are used for mapping, they need not be unique, in Matsim ids must be unique, we use this
   * map to track for duplicates, if found, we append unique identifier */
  private Map<String,LongAdder> usedExternalMatsimLinkIds = new HashMap<>();
  
  /** track number of MATSim nodes persisted */
  private final LongAdder matsimNodeCounter = new LongAdder();
  
  /** track number of MATSim links persisted */
  private final LongAdder matsimLinkCounter = new LongAdder();

  /** track number of MATSim turn restrictions persisted */
  private final LongAdder matsimTurnRestrictionCounter = new LongAdder();

  /**
   * Construct the json type string that MATSim adopts for banned movements
   * Note that we currently only support bans with a single exit not with via setups
   *
   * @param bannedMovementsOfFromSegment  banned movements for from segment to convert
   * @param planitModeToMatsimModeMapping to use
   * @return json string, e.g. {"car":[["exitAId"],["exitViaDId","toExitEId"]]}
   */
  private String constructBannedMovementJSon(
      List<BannedMovement> bannedMovementsOfFromSegment, Map<Mode, String> planitModeToMatsimModeMapping) {

    StringBuilder jsonBuilder = new StringBuilder();
    jsonBuilder.append("{");
    // for now we ban a movement for all supported modes, as we do not support mode specific bans yet in PLANit
    planitModeToMatsimModeMapping.values().stream().distinct().forEach(matsimMode ->
    {
      jsonBuilder.append("\"").append(matsimMode).append("\":");
      jsonBuilder.append("[");
      boolean firstTo = true;
      for(var bannedMovement : bannedMovementsOfFromSegment){
        if(!firstTo){
          jsonBuilder.append(",");
        }
        jsonBuilder.append("[");
        var matsimToSegmentId = MatsimNetworkWriterUtils.produceMappedMatsimLinkId(
            (MacroscopicLinkSegment) bannedMovement.getSegmentTo(),
            getIdMapperType(),
            getComponentIdMappers().getNetworkIdMappers(),
            usedExternalMatsimLinkIds);
        jsonBuilder.append("\"").append(matsimToSegmentId).append("\"");
        jsonBuilder.append("]");
        firstTo = false;
      }
      jsonBuilder.append("],");
    });
    jsonBuilder.deleteCharAt(jsonBuilder.length()-1);
    jsonBuilder.append("}");

    return jsonBuilder.toString();
  }
                
  /**
   * validate the settings making sure minimal output information is available
   */
  private boolean validateSettings() {
    if(StringUtils.isNullOrBlank(getSettings().getOutputDirectory())) {
      LOGGER.severe("Matsim network output directory not set on settings, unable to persist network");
      return false;
    }
    if(StringUtils.isNullOrBlank(getSettings().getFileName())) {
      LOGGER.severe("Matsim network output file name not set on settings, unable to persist network");
      return false;
    }    
        
    return true;
  }  

  /** write a MATSIM link for given PLANit link segment
   * @param xmlWriter to use
   * @param linkSegment link segment to write
   * @param planitModeToMatsimModeMapping quick mapping from PLANit mode to MATSIM mode string
   * @param bannedMovementsOfFromSegment the banned movements for this from segment (can be null)
   */
  private void writeMatsimLink(
      XMLStreamWriter xmlWriter, 
      MacroscopicLinkSegment linkSegment, 
      Map<Mode, String> planitModeToMatsimModeMapping,
      List<BannedMovement> bannedMovementsOfFromSegment){

    if(Collections.disjoint(planitModeToMatsimModeMapping.keySet(), linkSegment.getAllowedModes())) {
      /* link segment has no modes that are activated on the MATSIM network -> ignore */
      return;
    }

    boolean hasInteralAttributeElements =
        bannedMovementsOfFromSegment != null && !bannedMovementsOfFromSegment.isEmpty();
    
    try {
      if(hasInteralAttributeElements){
        writeStartElement(xmlWriter, MatsimAttributes.LINK, true /* ++index */);
      }else {
        PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimAttributes.LINK, getIndentLevel());
      }
      matsimLinkCounter.increment();
      
      /* attributes  of element*/
      {
        /* GEOGRAPHY **/
        {
          var networkIdMappers = getComponentIdMappers().getNetworkIdMappers();
          /* ID */
          String matsimLinkId = MatsimNetworkWriterUtils.produceMappedMatsimLinkId(
              linkSegment, getIdMapperType(), networkIdMappers, usedExternalMatsimLinkIds);

          xmlWriter.writeAttribute(MatsimAttributes.ID, matsimLinkId);
    
          /* FROM node */
          xmlWriter.writeAttribute(
              MatsimNetworkAttributes.FROM,
              networkIdMappers.getVertexIdMapper().apply(linkSegment.getUpstreamVertex()));
          
          /* TO node */
          xmlWriter.writeAttribute(
              MatsimNetworkAttributes.TO,
              networkIdMappers.getVertexIdMapper().apply(linkSegment.getDownstreamVertex()));
          
          /* LENGTH */
          xmlWriter.writeAttribute(MatsimNetworkAttributes.LENGTH,
              String.format("%.2f",Unit.KM.convertTo(Unit.METER, linkSegment.getParent().getLengthKm())));
        }
        
        if(linkSegment.getLinkSegmentType() == null) {
          throw new PlanItRunTimeException(String.format(
              "MATSim requires link segment type to be available on link segment (id:%d)",linkSegment.getId()));
        }
                
        /* MODELLING PARAMETERS **/
        {
          /* SPEED */
          double linkSpeedLimit = linkSegment.getPhysicalSpeedLimitKmH();
          if(getSettings().isRestrictLinkSpeedBySupportedModes()) {
            double minModeSpeed =
                planitModeToMatsimModeMapping.keySet().stream().map(
                    Mode::getMaximumSpeedKmH).sorted().findFirst().orElse(linkSpeedLimit);
            linkSpeedLimit = Math.min(linkSpeedLimit, minModeSpeed);
          }
          xmlWriter.writeAttribute(MatsimNetworkAttributes.FREESPEED_METER_SECOND, 
              String.format("%.2f",Unit.KM_HOUR.convertTo(Unit.METER_SECOND, linkSpeedLimit)));
          
          /* CAPACITY */
          xmlWriter.writeAttribute(
              MatsimNetworkAttributes.CAPACITY_HOUR, String.format("%.1f",linkSegment.getCapacityOrDefaultPcuH()));
          
          /* PERMLANES */
          xmlWriter.writeAttribute(MatsimNetworkAttributes.PERMLANES, String.valueOf(linkSegment.getNumberOfLanes()));
          
          /* MODES */
          Set<String> matsimModes = new TreeSet<String>();
          for(Mode planitMode : linkSegment.getAllowedModes()) {
            if(planitModeToMatsimModeMapping.containsKey(planitMode)) {
              matsimModes.add(planitModeToMatsimModeMapping.get(planitMode));
            }
          }
          String allowedModes = String.join(",", matsimModes);
          xmlWriter.writeAttribute(MatsimNetworkAttributes.MODES,allowedModes);
        }
        
        /* OTHER **/
        {
          /* VOLUME not yet supported */
          
          /* ORIG ID */
          Object originalExternalId =
              linkSegment.getExternalId() != null ? linkSegment.getExternalId() : linkSegment.getParent().getExternalId();
          if(originalExternalId!= null) {
            xmlWriter.writeAttribute(MatsimNetworkAttributes.ORIGID, String.valueOf(originalExternalId));
          }
          
          /* USER DEFINED **/
          
          /* NT_CATEGORY */
          if(settings.linkNtCategoryfunction != null) {
            xmlWriter.writeAttribute(
                MatsimNetworkAttributes.NT_CATEGORY, settings.linkNtCategoryfunction.apply(linkSegment));
          }
          
          /* NT_CATEGORY */
          if(settings.linkNtTypefunction != null) {
            xmlWriter.writeAttribute(
                MatsimNetworkAttributes.NT_TYPE, settings.linkNtTypefunction.apply(linkSegment));
          }
          
          /* TYPE */
          if(settings.linkTypefunction != null) {
            xmlWriter.writeAttribute(
                MatsimNetworkAttributes.NT_TYPE, settings.linkTypefunction.apply(linkSegment));
          }  
          
        }                     

      }
      
      PlanitXmlWriterUtils.writeNewLine(xmlWriter);

      if(hasInteralAttributeElements){
        // <ATTRIBUTES>
        writeStartElementNewLine(xmlWriter, MatsimNetworkElements.ATTRIBUTES, true /*++indent*/);

        // add in attributes for this type
        if(bannedMovementsOfFromSegment != null && !bannedMovementsOfFromSegment.isEmpty()){
          writeStartElement(xmlWriter, MatsimNetworkElements.ATTRIBUTE, true /*++indent*/);
          xmlWriter.writeAttribute(
              MatsimAttributes.NAME, MatsimNetworkAttributes.DISALLOWED_NEXT_LINKS);
          xmlWriter.writeAttribute(
              MatsimTransitAttributes.CLASS, MatsimNetworkAttributes.DISALLOWED_NEXT_LINKS_CLASS_VALUE);
          //PlanitXmlWriterUtils.writeNewLine(xmlWriter);
          //PlanitXmlWriterUtils.writeValueWithIndentationPrefix(
          xmlWriter.writeCharacters(
              constructBannedMovementJSon(bannedMovementsOfFromSegment, planitModeToMatsimModeMapping));
          //PlanitXmlWriterUtils.writeNewLine(xmlWriter);
          xmlWriter.writeEndElement();
          decreaseIndentation();
          PlanitXmlWriterUtils.writeNewLine(xmlWriter);
          matsimTurnRestrictionCounter.add(bannedMovementsOfFromSegment.size());
        }
        // </ATTRIBUTES>
        writeEndElementNewLine(xmlWriter, true /*--indent*/);
        // </link>
        writeEndElementNewLine(xmlWriter, true /*--indent*/);
      }

    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException(
          String.format("error while writing MATSim link XML element %s (id:%d)",
              linkSegment.getExternalId(), linkSegment.getId()));
    }
  }

  /**
   * Write a PLANit link with one or two link segments as MATSIM link(s)
   *
   * @param xmlWriter                     to use
   * @param link                          to extract MATSIM link(s) from
   * @param planitModeToMatsimModeMapping quick mapping from PLANit mode to MATSIM mode string
   * @param bannedMovementsByFromSegment banned movements by from segment
   */
  private void writeMatsimLink(
      XMLStreamWriter xmlWriter,
      Link link,
      Map<Mode, String> planitModeToMatsimModeMapping,
      Map<EdgeSegment, List<BannedMovement>> bannedMovementsByFromSegment){
    
    /* A --> B */
    if(link.hasEdgeSegmentAb()) {
      var bannedMovementsOnLinkSegment = bannedMovementsByFromSegment.get(link.getEdgeSegmentAb());
      writeMatsimLink(
          xmlWriter,
          (MacroscopicLinkSegment) link.getEdgeSegmentAb(),
          planitModeToMatsimModeMapping,
          bannedMovementsOnLinkSegment);
    }
    
    /* A <-- B */
    if(link.hasEdgeSegmentBa()) {
      var bannedMovementsOnLinkSegment = bannedMovementsByFromSegment.get(link.getEdgeSegmentBa());
      writeMatsimLink(
          xmlWriter,
          (MacroscopicLinkSegment) link.getEdgeSegmentBa(),
          planitModeToMatsimModeMapping,
          bannedMovementsOnLinkSegment);
    }
    
  }  

  /** write the links
   * 
   * @param xmlWriter to use
   * @param networkLayer to extract from
   */
  private void writeMatsimLinks(
      XMLStreamWriter xmlWriter, 
      MacroscopicNetworkLayerImpl networkLayer) {
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkElements.LINKS, true /* ++indent */);
      
      Map<Mode, String> planitModeToMatsimModeMapping =
          settings.collectActivatedPlanitModeToMatsimModeMapping(networkLayer);

      var bannedMovementsByFromSegment =
          networkLayer.getBannedMovements().stream().collect(Collectors.groupingBy(BannedMovement::getSegmentFrom));

      /* write link(segments) one by one */
      for(Link link: networkLayer.getLinks()) {
        writeMatsimLink(xmlWriter, link, planitModeToMatsimModeMapping, bannedMovementsByFromSegment);
      }
      
      writeEndElementNewLine(xmlWriter, true /*-- indent */); // LINKS
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("error while writing MATSim link XML element");
    }    
  }


  /** Write a PLANit node as MATSIM node 
   * @param xmlWriter to use
   * @param node to write
   */
  private void writeMatsimNode(XMLStreamWriter xmlWriter, Node node){
    try {
      PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimNetworkElements.NODE, getIndentLevel());           
      matsimNodeCounter.increment();
      
      /* attributes  of element*/
      {
        /* ID */
        xmlWriter.writeAttribute(
            MatsimAttributes.ID, getComponentIdMappers().getNetworkIdMappers().getVertexIdMapper().apply(node));
        
        /* geometry of the node (optional) */
        Coordinate nodeCoordinate = extractDestinationCrsCompatibleCoordinate(node.getPosition());
        if(nodeCoordinate != null) {        
          /* X */
          xmlWriter.writeAttribute(MatsimAttributes.X, settings.getDecimalFormat().format(nodeCoordinate.x));
          /* Y */
          xmlWriter.writeAttribute(MatsimAttributes.Y, settings.getDecimalFormat().format(nodeCoordinate.y));
          /* Z coordinate not yet supported */
        }
        
        /* TYPE not yet supported */
        
        /* ORIGID not yet supported */
      }
      
      PlanitXmlWriterUtils.writeNewLine(xmlWriter);
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException(
          "Error while writing MATSim node XML element %s (id:%d)",node.getExternalId(), node.getId());
    }
  }  
  
  /** write the nodes
   * @param xmlWriter to use
   * @param networkLayer to extract from
   * @throws PlanItException thrown if error
   */
  private void writeMatsimNodes(
      XMLStreamWriter xmlWriter, MacroscopicNetworkLayerImpl networkLayer) throws PlanItException {
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkElements.NODES, true /* ++indent */);
      
      /* write nodes one by one */
      for(Node node : networkLayer.getNodes()) {
        writeMatsimNode(xmlWriter, node);
      }
      
      writeEndElementNewLine(xmlWriter, true /*-- indent */); // NODES
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSim nodes XML element");
    }
  }  
  

  /** write the body of the MATSIM network XML file based on the PLANit network contents
   * 
   * @param xmlWriter the writer
   * @param networkLayer to persist
   * @throws PlanItException thrown if error
   */
  private void writeMatsimNetworkXML(
      XMLStreamWriter xmlWriter, MacroscopicNetworkLayerImpl networkLayer) throws PlanItException {
    try {
      writeStartElementNewLine(xmlWriter,MatsimNetworkElements.NETWORK, true /* add indentation*/);

      /* nodes */
      writeMatsimNodes(xmlWriter, networkLayer);
      
      /* links */
      writeMatsimLinks(xmlWriter, networkLayer);

      writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // NETWORK
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error while writing MATSim network XML element");
    }
  }

  /**
   * Log some aggregate stats on the MATSim writer regarding the number of elements persisted
   */
  private void logWriterStats() {
    LOGGER.info(String.format("[STATS] created %d nodes",matsimNodeCounter.longValue()));
    LOGGER.info(String.format("[STATS] created %d links",matsimLinkCounter.longValue()));
    LOGGER.info(String.format("[STATS] created %d turn restrictions",matsimTurnRestrictionCounter.longValue()));
  }

  /**
   * MATSIM writer settings 
   */
  protected final MatsimNetworkWriterSettings settings;
  
  /**
   * write the xml MATSIM network
   * 
   * @param networkLayer to draw from
   * @param asGZip flag indicating whther to write out as gzipped XML or not
   */
  protected void writeXmlNetworkFile(MacroscopicNetworkLayerImpl networkLayer, boolean asGZip){
    Path matsimNetworkPath =
        Paths.get(getSettings().getOutputDirectory(), getSettings().getFileName().concat(DEFAULT_FILE_NAME_EXTENSION));
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair =
        PlanitXmlWriterUtils.createXMLWriter(matsimNetworkPath, asGZip);
    
    try {
      /* start */
      PlanitXmlWriterUtils.startXmlDocument(xmlFileWriterPair.first(), NETWORK_DOCTYPE);
      
      /* body */
      writeMatsimNetworkXML(xmlFileWriterPair.first(), networkLayer);
      
      /* end */
      PlanitXmlWriterUtils.endXmlDocument(xmlFileWriterPair);
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException(String.format("error while persisting MATSIM network to %s", matsimNetworkPath));
    }
  }  
  
  /**
   * Create detailed geometry file compatible with VIA viewer
   * 
   * @param networkLayer to draw from
   */
  protected void writeDetailedGeometryFile(MacroscopicNetworkLayerImpl networkLayer){
    Path matsimNetworkGeometryPath =
        Paths.get(getSettings().getOutputDirectory(),
            DEFAULT_NETWORK_GEOMETRY_FILE_NAME.concat(DEFAULT_NETWORK_GEOMETRY_FILE_NAME_EXTENSION)).toAbsolutePath();
    LOGGER.info(String.format("persisting MATSIM network geometry to: %s",matsimNetworkGeometryPath.toString()));
    
    try {
      CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(matsimNetworkGeometryPath.toFile()), CSVFormat.TDF);      
      csvPrinter.printRecord("LINK_ID", "GEOMETRY");
      
      Function<MacroscopicLinkSegment, String> linkIdMapping =
          IdMapperFunctionFactory.createLinkSegmentIdMappingFunction(getIdMapperType());
      for(MacroscopicLinkSegment linkSegment : networkLayer.getLinkSegments()) {
        
        /* extract geometry to write */
        LineString destinationCrsGeometry = null;
        if(getDestinationCrsTransformer()!=null) {
          destinationCrsGeometry =
              ((LineString)JTS.transform(linkSegment.getParent().getGeometry(), getDestinationCrsTransformer()));
        }else {
          destinationCrsGeometry = linkSegment.getParent().getGeometry();
        }        
        if(destinationCrsGeometry==null) {
          LOGGER.severe(String.format(
              "geometry unavailable for link (segment id:%d) even though request for detailed geometry is made, " +
                  "link ignored",linkSegment.getId()));
          continue;
        }
        
        /* get correct coordinate sequence, reverse when segment is reverse direction */
        Coordinate[] coordinates =
            linkSegment.isDirectionAb() ? destinationCrsGeometry.getCoordinates() :
                destinationCrsGeometry.reverse().getCoordinates();
        
        /* only when it has internal coordinates */
        if(coordinates.length > 2) {
          StringBuilder lineStringString = new StringBuilder("LINESTRING (");
          int firstInternal = 1;
          int lastInternal = coordinates.length-1;
          for(int index = firstInternal ; index < lastInternal; ++index) {
            Coordinate coordinate = coordinates[index];           
            if(index>firstInternal) {
              lineStringString.append(",");
            }         
            lineStringString.append(String.format("%s %s",
                settings.getDecimalFormat().format(coordinate.x), settings.getDecimalFormat().format(coordinate.y)));
          }
          lineStringString.append(")");
          csvPrinter.printRecord(linkIdMapping.apply(linkSegment), lineStringString.toString());
        }
      }
      csvPrinter.close();
    } catch (IOException | TransformException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Unable to write detailed gemoetry file %d an error occured during writing", e);
    }
  }  

  /**
   * default names used for MATSIM network file that is being generated
   */
  public static final String DEFAULT_NETWORK_GEOMETRY_FILE_NAME_EXTENSION = ".txt";      
  
  /**
   * default names used for MATSIM network file that is being generated
   */
  public static final String DEFAULT_NETWORK_GEOMETRY_FILE_NAME = "network_geometry";

  public static final String DEFAULT_PT_STOPS_FILE_NAME = "ptStops";

  public static final String DEFAULT_PT_STOPS_FILE_NAME_EXTENSION = ".csv";

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
  public void write(LayeredNetwork<?,?> network) {
    PlanItRunTimeException.throwIfNull(network,
        "network is null, cannot write undefined network to MATSIM format");
    
    boolean networkValid = validateNetwork(network);
    if(!networkValid) {
      return;
    }
    boolean settingsValid = validateSettings();
    if(!settingsValid) {
      return;
    }
    
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) network;

    /* id mapping */
    getComponentIdMappers().populateMissingIdMappers(getIdMapperType());

    /* CRS */
    prepareCoordinateReferenceSystem(
        macroscopicNetwork.getCoordinateReferenceSystem(),
        getSettings().getDestinationCoordinateReferenceSystem(),
        getSettings().getCountry());

    /* log settings */
    settings.logSettings(macroscopicNetwork);
    
    /* write */
    final MacroscopicNetworkLayerImpl macroscopicPhysicalNetworkLayer =
        (MacroscopicNetworkLayerImpl)macroscopicNetwork.getTransportLayers().getFirst();
    
    writeXmlNetworkFile(macroscopicPhysicalNetworkLayer, getSettings().isWriteAsGZip());
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
    matsimNodeCounter.reset();
    matsimLinkCounter.reset();
    matsimTurnRestrictionCounter.reset();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MatsimNetworkWriterSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public NetworkIdMapper getPrimaryIdMapper() {
    return getComponentIdMappers().getNetworkIdMappers();
  }
}
