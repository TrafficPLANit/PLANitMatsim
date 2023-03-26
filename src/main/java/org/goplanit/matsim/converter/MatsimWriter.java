package org.goplanit.matsim.converter;

import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.geotools.geometry.jts.JTS;
import org.goplanit.converter.BaseWriterImpl;
import org.goplanit.converter.idmapping.IdMapperType;
import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.LayeredNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

/**
 * Base class from which all matsim writers derive
 * 
 * @author markr
 *
 */
public abstract class MatsimWriter<T> extends BaseWriterImpl<T> {

  /**
   * The logger of this class
   */
  private static final Logger LOGGER = Logger.getLogger(MatsimWriter.class.getCanonicalName());
      
  /** track indentation level */
  private int indentLevel = 0;
  
  /** when the destination CRS differs from the network CRS all geometries require transforming, for which this transformer will be initialised */
  private MathTransform destinationCrsTransformer = null;
    
  /**
   * Validate the network instance available, throw or log when issues are found
   * 
   * @param referenceNetwork to use for persisting
   * @return true when valid, false otherwise
   * @throws PlanItException thrown if invalid
   */
  protected boolean validateNetwork(LayeredNetwork<?,?> referenceNetwork) throws PlanItException {
    if(referenceNetwork == null) {
      LOGGER.severe("MATSim macroscopic planit network to extract from is null");
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
      LOGGER.severe(String.format("MATSim writer currently only supports networks with a single layer, the provided network has %d",referenceNetwork.getTransportLayers().size()));
      return false;
    }   
    if(!(referenceNetwork.getTransportLayers().getFirst() instanceof MacroscopicNetworkLayerImpl)) {
      LOGGER.severe(String.format("MATSim only supports macroscopic physical network layers, the provided network is of a different type"));
      return false;
    }
    
    return true;
  }  
   
  
  /** Prepare the Crs transformer (if any) based on the user configuration settings. If no destinationCrs is provided than we use the country to try and infer the
   * most appropriate desintation crs. In case the identified destination crs differs from the network one, we also creata destination transformer which is registered on the instance
   * of the class
   * 
   * @param network the network extract current Crs if no user specific settings can be found
   * @param destinationCountry of the network
   * @param destinationCrs that we would like to use, maybe null
   * @return identified destinationCrs to use
   * @throws PlanItException thrown if error
   */
  protected CoordinateReferenceSystem prepareCoordinateReferenceSystem(MacroscopicNetwork network, String destinationCountry, CoordinateReferenceSystem destinationCrs) throws PlanItException {
    /* CRS and transformer (if needed) */
    CoordinateReferenceSystem identifiedDestinationCrs = identifyDestinationCoordinateReferenceSystem( destinationCrs, destinationCountry, network.getCoordinateReferenceSystem());    
    PlanItException.throwIfNull(identifiedDestinationCrs, "destination Coordinate Reference System is null, this is not allowed");
    
    /* configure crs transformer if required, to be able to convert geometries to preferred CRS while writing */
    if(!identifiedDestinationCrs.equals(network.getCoordinateReferenceSystem())) {
      destinationCrsTransformer = PlanitJtsUtils.findMathTransform(network.getCoordinateReferenceSystem(), identifiedDestinationCrs);
    }
    
    return identifiedDestinationCrs;
  }   
  
  /** Using the destination crs and its transformer extract the coordinate from the position in the desired crs
   * 
   * @param location to extract destination crs compatible coordinate for
   * @return coordinate created
   * @throws TransformException thrown if error
   * @throws MismatchedDimensionException thrown if error 
   */
  protected Coordinate extractDestinationCrsCompatibleCoordinate(Point location) throws MismatchedDimensionException, TransformException {
    /* geometry of the node (optional) */
    Coordinate coordinate = null;
    if(getDestinationCrsTransformer()!=null) {
      coordinate = ((Point)JTS.transform(location, getDestinationCrsTransformer())).getCoordinate();
    }else {
      coordinate = location.getCoordinate();  
    }
    return coordinate;
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
   * write a start element and add newline afterwards
   *
   * @param xmlWriter to use
   * @param xmlElementName element to start tag, e.g. {@code <xmlElementName>}
   * @param increaseIndentation when true, increase indentation after this element has been written
   * @throws XMLStreamException thrown if error
   */
  protected void writeStartElement(XMLStreamWriter xmlWriter, String xmlElementName, boolean increaseIndentation) throws XMLStreamException {
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
  protected void writeStartElementNewLine(XMLStreamWriter xmlWriter, String xmlElementName, boolean increaseIndentation) throws XMLStreamException {
    PlanitXmlWriterUtils.writeStartElementNewLine(xmlWriter, xmlElementName, indentLevel);
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
  protected void writeEndElementNewLine(XMLStreamWriter xmlWriter, boolean decreaseIndentation) throws XMLStreamException {
    if(decreaseIndentation) {
      decreaseIndentation(); 
    }
    PlanitXmlWriterUtils.writeEndElementNewLine(xmlWriter, indentLevel);
  }


  /** Constructor
   * 
   * @param idMapperType to use
   */
  protected MatsimWriter(IdMapperType idMapperType) {
    super(idMapperType);
  }


  int getIndentLevel() {
    return indentLevel;
  }
  
  MathTransform getDestinationCrsTransformer() {
    return destinationCrsTransformer;
  }


  /** any settings to configure the writer can be configured by collecting these settings
   * @return the settings to configure the writer
   */
  @Override
  public abstract PlanitMatsimWriterSettings getSettings();

  /** the doc type of MATSIM public transport schedule. */
  public static final String TRANSIT_SCHEDULE_DOCTYPE = "<!DOCTYPE transitSchedule SYSTEM \"https://www.matsim.org/files/dtd/transitSchedule_v2.dtd\">";

  /** the doc type of MATSIM public transport schedule. */
  public static final String NETWORK_DOCTYPE = "<!DOCTYPE network SYSTEM \"https://www.matsim.org/files/dtd/network_v2.dtd\">";

  /**
   * default extension for xml files generated
   */
  public static final String DEFAULT_FILE_NAME_EXTENSION = ".xml";  
  
}
