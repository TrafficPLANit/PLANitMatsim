package org.goplanit.matsim.converter;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.goplanit.converter.CrsWriterImpl;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.LayeredNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

/**
 * Base class from which all MATSIM writers derive
 * 
 * @param <T> type of converter
 * @author markr
 */
public abstract class MatsimWriter<T> extends CrsWriterImpl<T> {

  /**
   * The logger of this class
   */
  private static final Logger LOGGER = Logger.getLogger(MatsimWriter.class.getCanonicalName());
  public static final DateTimeFormatter HHmmssFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

  /** track indentation level */
  private int indentLevel = 0;

  /**
   * Validate the network instance available, throw or log when issues are found
   * 
   * @param referenceNetwork to use for persisting
   * @return true when valid, false otherwise
   */
  protected boolean validateNetwork(LayeredNetwork<?,?> referenceNetwork){
    if(referenceNetwork == null) {
      LOGGER.severe("MATSim macroscopic PLANit network to extract from is null");
      return false;
    }

    if (!(referenceNetwork instanceof MacroscopicNetwork)) {
      LOGGER.severe("MATSim writer currently only supports writing macroscopic networks");
      return false;
    }

    if(referenceNetwork.getTransportLayers().isEachLayerEmpty()) {
      LOGGER.severe("PLANit Network to persist is empty");
      return false;
    }

    if(referenceNetwork.getTransportLayers().size()!=1) {
      LOGGER.severe(String.format("MATSim writer currently only supports networks with a single layer, " +
          "the provided network has %d",referenceNetwork.getTransportLayers().size()));
      return false;
    }
    if(!(referenceNetwork.getTransportLayers().getFirst() instanceof MacroscopicNetworkLayerImpl)) {
      LOGGER.severe("MATSim only supports macroscopic physical network layers, the " +
          "provided network is of a different type");
      return false;
    }
    
    return true;
  }  

  /** Using the destination crs and its transformer extract the coordinate from the position in the desired crs
   *
   * @param location to extract destination crs compatible coordinate for
   * @return coordinate created
   */
  protected Coordinate extractDestinationCrsCompatibleCoordinate(Point location){
    return createTransformedCoordinate(location.getCoordinate());
  }

  /** Add indentation to stream at current indentation level
   * 
   * @param xmlWriter to use
   * @throws XMLStreamException thrown if error
   */
  protected void writeIndentation(XMLStreamWriter xmlWriter) throws XMLStreamException {
    PlanitXmlWriterUtils.writeIndentation(xmlWriter, indentLevel);
  }


  /** Increase indentation level
   * @return updated indentation level
   */
  protected int increaseIndentation() {
    ++indentLevel;
    return getIndentLevel();
  }


  /** Decrease indentation level
   * @return updated indentation level
   */
  protected int decreaseIndentation() {
    --indentLevel;
    return getIndentLevel();
  }

  /**
   * write a start element
   *
   * @param xmlWriter to use
   * @param xmlElementName element to start tag, e.g. {@code <xmlElementName>}
   * @param increaseIndentation when true, increase indentation after this element has been written
   * @throws XMLStreamException thrown if error
   */
  protected void writeStartElement(
      XMLStreamWriter xmlWriter, String xmlElementName, boolean increaseIndentation) throws XMLStreamException {
    PlanitXmlWriterUtils.writeStartElement(xmlWriter, xmlElementName, indentLevel);
    if(increaseIndentation) {
      increaseIndentation();
    }
  }


  /**
   * write a start element and add newline afterwards
   * 
   * @param xmlWriter to use
   * @param xmlElementName element to start tag, e.g. {@code <xmlElementName>}
   * @param increaseIndentation when true, increase indentation after this element has been written
   * @throws XMLStreamException thrown if error
   */
  protected void writeStartElementNewLine(
      XMLStreamWriter xmlWriter, String xmlElementName, boolean increaseIndentation) throws XMLStreamException {
    PlanitXmlWriterUtils.writeStartElementNewLine(xmlWriter, xmlElementName, indentLevel);
    if(increaseIndentation) {
      increaseIndentation();
    }
  }

  /**
   * write a start element, apply callback then add newline afterwards
   *
   * @param xmlWriter to use
   * @param xmlElementName element to start tag, e.g. {@code <xmlElementName>}
   * @param increaseIndentation when true, increase indentation after this element has been written
   * @param callbackBeforeNewLine to apply before new lin is called
   * @throws XMLStreamException thrown if error
   */
  protected void writeStartElementNewLine(
      XMLStreamWriter xmlWriter,
      String xmlElementName,
      boolean increaseIndentation,
      Consumer<XMLStreamWriter> callbackBeforeNewLine) throws XMLStreamException {
    PlanitXmlWriterUtils.writeStartElement(xmlWriter, xmlElementName, indentLevel);
    callbackBeforeNewLine.accept(xmlWriter);
    PlanitXmlWriterUtils.writeNewLine(xmlWriter);
    if(increaseIndentation) {
      increaseIndentation();
    }
  }


  /**
   * write an end element and add newline afterwards
   * 
   * @param xmlWriter to use
   * @param decreaseIndentation when true decrease indentation level before this element has been written
   * @throws XMLStreamException thrown if error
   */  
  protected void writeEndElementNewLine(
      XMLStreamWriter xmlWriter, boolean decreaseIndentation) throws XMLStreamException {
    if(decreaseIndentation) {
      decreaseIndentation(); 
    }
    PlanitXmlWriterUtils.writeEndElementNewLine(xmlWriter, indentLevel);
  }

  /**
   * PAss through to write a new line
   *
   * @param xmlWriter to use
   * @throws XMLStreamException thrown if error
   */
  protected void writeNewLine(XMLStreamWriter xmlWriter) throws XMLStreamException {
    PlanitXmlWriterUtils.writeNewLine(xmlWriter);
  }


  /** Constructor
   * 
   * @param idMapperType to use
   */
  protected MatsimWriter(IdMapperType idMapperType) {
    super(idMapperType);
  }


  /**
   * Current indent level
   * @return indent level
   */
  protected int getIndentLevel() {
    return indentLevel;
  }

  /** any settings to configure the writer can be configured by collecting these settings
   * @return the settings to configure the writer
   */
  @Override
  public abstract PlanitMatsimWriterSettings getSettings();

  /** the doc type of MATSIM public transport schedule. */
  public static final String TRANSIT_SCHEDULE_DOCTYPE =
      "<!DOCTYPE transitSchedule SYSTEM \"https://www.matsim.org/files/dtd/transitSchedule_v2.dtd\">";

  /** the doc type of MATSIM public transport schedule. */
  public static final String NETWORK_DOCTYPE =
      "<!DOCTYPE network SYSTEM \"https://www.matsim.org/files/dtd/network_v2.dtd\">";

  /** the doc type of MATSim population/plans. */
  public static final String PLANS_DOCTYPE =
      "<!DOCTYPE population SYSTEM \"https://www.matsim.org/files/dtd/population_v5.dtd\">";

  /**
   * default extension for xml files generated
   */
  public static final String DEFAULT_FILE_NAME_EXTENSION = ".xml";  
  
}
