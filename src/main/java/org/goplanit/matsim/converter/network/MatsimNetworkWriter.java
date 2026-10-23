package org.goplanit.matsim.converter.network;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.goplanit.converter.idmapping.IdMapperFunctionFactory;
import org.goplanit.matsim.converter.MatsimTransferAccessWriter;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.matsim.util.MatsimNetworkWriterUtils;
import org.goplanit.matsim.xml.*;
import org.goplanit.utils.geo.PlanitCrsUtils;
import org.goplanit.utils.graph.directed.BannedTurn;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.converter.idmapping.NetworkIdMapper;
import org.goplanit.converter.network.NetworkWriter;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.LayeredNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicBannedTurn;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.LinkSegment;
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
  
  /** track stats */
  private final MatsimNetworkWriterStats writerStats = new MatsimNetworkWriterStats();

  /** contributes the transfer zone access to this network file, null when there is nothing to contribute. Wired up by
   * the intermodal writer rather than by a user, since only a combined network and zoning write has transfer zones in
   * the first place */
  private MatsimTransferAccessWriter transferAccessWriter;

  /**
   * Verify if a banned turn bans a PLANit mode. A banned turn of a macroscopic layer names the modes it bans, or bans
   * every mode when it names none; any other banned turn bans every mode
   *
   * @param bannedTurn to verify
   * @param mode to verify
   * @return true when banned for the mode, false otherwise
   */
  private static boolean isBannedFor(BannedTurn bannedTurn, Mode mode) {
    return !(bannedTurn instanceof MacroscopicBannedTurn) || ((MacroscopicBannedTurn) bannedTurn).isModeBanned(mode);
  }

  /**
   * Verify if a banned turn bans a PLANit mode mapped to a MATSim mode
   *
   * @param bannedTurn to verify
   * @param matsimMode to verify, null for any mapped MATSim mode
   * @param planitModeToMatsimModeMapping to use
   * @return true when it bans a PLANit mode mapped to the MATSim mode, false otherwise
   */
  private static boolean isBannedForMatsimMode(
      BannedTurn bannedTurn, String matsimMode, Map<Mode, String> planitModeToMatsimModeMapping) {
    return planitModeToMatsimModeMapping.entrySet().stream().anyMatch(mapped ->
        (matsimMode == null || matsimMode.equals(mapped.getValue())) && isBannedFor(bannedTurn, mapped.getKey()));
  }

  /**
   * Construct the json type string that MATSim adopts for banned turns, per MATSim mode the banned turns that ban a
   * PLANit mode mapped to it. A MATSim mode no banned turn applies to is left out.
   * Note that we currently only support bans with a single exit not with via setups
   *
   * @param bannedTurnsOfFromSegment  banned turns for from segment to convert
   * @param planitModeToMatsimModeMapping to use
   * @return json string, e.g. {"car":[["exitAId"],["exitViaDId","toExitEId"]]}
   */
  private String constructBannedTurnJSon(
      List<BannedTurn> bannedTurnsOfFromSegment, Map<Mode, String> planitModeToMatsimModeMapping) {

    StringBuilder jsonBuilder = new StringBuilder();
    jsonBuilder.append("{");
    planitModeToMatsimModeMapping.values().stream().distinct().forEach(matsimMode ->
    {
      var bannedTurnsOfMatsimMode = bannedTurnsOfFromSegment.stream().filter(
          bannedTurn -> isBannedForMatsimMode(bannedTurn, matsimMode, planitModeToMatsimModeMapping))
          .collect(Collectors.toList());
      if(bannedTurnsOfMatsimMode.isEmpty()){
        return;
      }
      jsonBuilder.append("\"").append(matsimMode).append("\":");
      jsonBuilder.append("[");
      boolean firstTo = true;
      for(var bannedTurn : bannedTurnsOfMatsimMode){
        if(!firstTo){
          jsonBuilder.append(",");
        }
        jsonBuilder.append("[");
        var matsimToSegmentId = MatsimNetworkWriterUtils.produceMappedMatsimLinkId(
            (MacroscopicLinkSegment) bannedTurn.getSegmentTo(),
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
   * @param bannedTurnsOfFromSegment the banned turns for this from segment (can be null)
   * @param linkExitCapacityFactor the capacity written is multiplied by, 1 to write the link segment's capacity as is
   */
  private void writeMatsimLink(
      XMLStreamWriter xmlWriter,
      MacroscopicLinkSegment linkSegment,
      Map<Mode, String> planitModeToMatsimModeMapping,
      List<BannedTurn> bannedTurnsOfFromSegment,
      double linkExitCapacityFactor){

    if(Collections.disjoint(planitModeToMatsimModeMapping.keySet(), linkSegment.getAllowedModes())) {
      /* link segment has no modes that are activated on the MATSIM network -> ignore */
      writerStats.incrementLinkSegmentsSkippedNoActivatedMode();
      return;
    }

    /* only the banned turns that apply to a mode written to MATSim */
    if(bannedTurnsOfFromSegment != null){
      bannedTurnsOfFromSegment = bannedTurnsOfFromSegment.stream().filter(
          bannedTurn -> isBannedForMatsimMode(bannedTurn, null, planitModeToMatsimModeMapping))
          .collect(Collectors.toList());
    }
    boolean hasInteralAttributeElements =
        bannedTurnsOfFromSegment != null && !bannedTurnsOfFromSegment.isEmpty();
    
    try {
      if(hasInteralAttributeElements){
        writeStartElement(xmlWriter, MatsimAttributes.LINK, true /* ++index */);
      }else {
        PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimAttributes.LINK, getIndentLevel());
      }
      writerStats.incrementLinkSegmentsWritten();
      
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
          /* PLANit tracks length in km, MATSim in meters, so convert first and only then guard the result: a zero
           * (or negative) length link makes MATSim divide by zero when deriving a travel time. A real length is left
           * untouched, however short, since only the absence of one is the problem */
          double lengthMeters = Unit.KM.convertTo(Unit.METER, linkSegment.getParent().getLengthKm());
          if(lengthMeters <= 0.0) {
            lengthMeters = getSettings().getMinimumLinkLengthMeters();
          }
          xmlWriter.writeAttribute(MatsimNetworkAttributes.LENGTH, String.format("%.2f", lengthMeters));
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
          xmlWriter.writeAttribute(MatsimNetworkAttributes.CAPACITY_HOUR,
              String.format("%.1f",linkSegment.getCapacityOrDefaultPcuH() * linkExitCapacityFactor));
          
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
        if(bannedTurnsOfFromSegment != null && !bannedTurnsOfFromSegment.isEmpty()){
          writeStartElement(xmlWriter, MatsimNetworkElements.ATTRIBUTE, true /*++indent*/);
          xmlWriter.writeAttribute(
              MatsimAttributes.NAME, MatsimNetworkAttributes.DISALLOWED_NEXT_LINKS);
          xmlWriter.writeAttribute(
              MatsimTransitAttributes.CLASS, MatsimNetworkAttributes.DISALLOWED_NEXT_LINKS_CLASS_VALUE);
          //PlanitXmlWriterUtils.writeNewLine(xmlWriter);
          //PlanitXmlWriterUtils.writeValueWithIndentationPrefix(
          xmlWriter.writeCharacters(
              constructBannedTurnJSon(bannedTurnsOfFromSegment, planitModeToMatsimModeMapping));
          //PlanitXmlWriterUtils.writeNewLine(xmlWriter);
          xmlWriter.writeEndElement();
          decreaseIndentation();
          PlanitXmlWriterUtils.writeNewLine(xmlWriter);
          writerStats.addTurnRestrictionsWritten(bannedTurnsOfFromSegment.size());
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
   * @param bannedTurnsByFromSegment banned turns by from segment
   * @param linkExitCapacityFactorOf the factor the capacity written for a link segment is multiplied by
   */
  private void writeMatsimLink(
      XMLStreamWriter xmlWriter,
      Link link,
      Map<Mode, String> planitModeToMatsimModeMapping,
      Map<EdgeSegment, List<BannedTurn>> bannedTurnsByFromSegment,
      ToDoubleFunction<LinkSegment> linkExitCapacityFactorOf){

    /* A --> B */
    if(link.hasEdgeSegmentAb()) {
      var bannedTurnsOnLinkSegment = bannedTurnsByFromSegment.get(link.getEdgeSegmentAb());
      writeMatsimLink(
          xmlWriter,
          (MacroscopicLinkSegment) link.getEdgeSegmentAb(),
          planitModeToMatsimModeMapping,
          bannedTurnsOnLinkSegment,
          linkExitCapacityFactorOf.applyAsDouble(link.getLinkSegmentAb()));
    }

    /* A <-- B */
    if(link.hasEdgeSegmentBa()) {
      var bannedTurnsOnLinkSegment = bannedTurnsByFromSegment.get(link.getEdgeSegmentBa());
      writeMatsimLink(
          xmlWriter,
          (MacroscopicLinkSegment) link.getEdgeSegmentBa(),
          planitModeToMatsimModeMapping,
          bannedTurnsOnLinkSegment,
          linkExitCapacityFactorOf.applyAsDouble(link.getLinkSegmentBa()));
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

      var bannedTurnsByFromSegment =
          networkLayer.getBannedTurns().stream().collect(Collectors.groupingBy(BannedTurn::getSegmentFrom));

      /* the capacity written is the rate at which vehicles can leave the link, reduced at an intersection when asked */
      ToDoubleFunction<LinkSegment> linkExitCapacityFactorOf =
          getSettings().isReduceLinkExitCapacityByEffectiveCapacityFactor() ?
              networkLayer.getIntersections()::getEffectiveCapacityFactorOf : linkSegment -> 1;

      /* write link(segments) one by one */
      for(Link link: networkLayer.getLinks()) {
        writeMatsimLink(
            xmlWriter, link, planitModeToMatsimModeMapping, bannedTurnsByFromSegment, linkExitCapacityFactorOf);
      }

      /* the connections to the stops, every node they refer to having been written already */
      if(transferAccessWriter != null) {
        transferAccessWriter.writeTransferAccessLinks(
            xmlWriter, getIndentLevel(), planitModeToMatsimModeMapping,
            getComponentIdMappers().getNetworkIdMappers().getVertexIdMapper());
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
      writerStats.incrementNodesWritten();
      
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

      /* the stops themselves, which only the zoning side knows how to express */
      if(transferAccessWriter != null) {
        transferAccessWriter.writeTransferAccessNodes(
            xmlWriter, getIndentLevel(), this::extractDestinationCrsCompatibleCoordinate,
            settings.getDecimalFormat());
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
      writeStartElement(xmlWriter,MatsimNetworkElements.NETWORK, true /* add indentation*/);
      writeNewLine(xmlWriter);
      // write a separate attributes/attribute element for the CRS on top
      if(getDestinationCoordinateReferenceSystem()!=null){
        writeStartElementNewLine(xmlWriter, MatsimElements.ATTRIBUTES, true /* add indentation*/);

        // crs
        writeMatsimCustomAttributeEntry(
            xmlWriter, "coordinateSystem", "java.lang.String",
            PlanitCrsUtils.extractSrsName(getDestinationCoordinateReferenceSystem()));

        writeEndElementNewLine(xmlWriter, true /* undo indentation */ );
      }


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
    LOGGER.info(this.writerStats.toString());
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

    /* the statistics describe the write about to happen rather than every write this instance has performed */
    writerStats.reset();

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
        getSettings().getCountry(),
        true);

    /* log settings */
    settings.logSettings(macroscopicNetwork, 0);
    
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
    // the statistics of the last write are deliberately kept, they hold no more than a handful of counts
  }

  /**
   * The statistics collected over the most recent write, covering what was written and what was left out
   *
   * @return the statistics of the most recent write
   */
  public MatsimNetworkWriterStats getWriterStats() {
    return writerStats;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MatsimNetworkWriterSettings getSettings() {
    return settings;
  }

  /** Set the writer contributing the transfer zone access to this network file. This is internal wiring between the
   * MATSim writers, done by the intermodal writer, rather than something to be set by a user
   *
   * @param transferAccessWriter to delegate to, null to write the physical network only
   */
  public void setTransferAccessWriter(MatsimTransferAccessWriter transferAccessWriter) {
    this.transferAccessWriter = transferAccessWriter;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public NetworkIdMapper getPrimaryIdMapper() {
    return getComponentIdMappers().getNetworkIdMappers();
  }
}
