package org.planit.matsim.converter;

import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.planit.converter.BaseWriterImpl;
import org.planit.converter.IdMapperType;
import org.planit.geo.PlanitOpenGisUtils;
import org.planit.network.InfrastructureNetwork;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.xml.PlanitXmlWriterUtils;

/**
 * Base class from which all matsim writers derive
 * 
 * @author markr
 *
 */
public abstract class PlanitMatsimWriter<T> extends BaseWriterImpl<T> {

  /**
   * the logger of this class
   */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimWriter.class.getCanonicalName());
      
  /** track indentation level */
  protected int indentLevel = 0;
  
  /** when the destination CRS differs from the network CRS all geometries require transforming, for which this transformer will be initialised */
  protected MathTransform destinationCrsTransformer = null;
  
  /**
   * validate the network instance available, throw or log when issues are found
   * 
   * @param referenceNetwork to use for persisting
   * @throws PlanItException thrown if invalid
   */
  protected void validateNetwork(InfrastructureNetwork<?,?> referenceNetwork) throws PlanItException {
    PlanItException.throwIfNull(referenceNetwork, "Matsim macroscopic planit network to extract from is null");
    
    if (!(referenceNetwork instanceof MacroscopicNetwork)) {
      throw new PlanItException("Matsim writer currently only supports writing macroscopic networks");
    }    

    if(referenceNetwork.infrastructureLayers.size()!=1) {
      throw new PlanItException(String.format("Matsim zoning writer currently only supports networks with a single layer, the provided network has %d",referenceNetwork.infrastructureLayers.size()));
    }   
    if(!(referenceNetwork.infrastructureLayers.getFirst() instanceof MacroscopicPhysicalNetwork)) {
      throw new PlanItException(String.format("Matsim only supports macroscopic physical network layers, the provided network is of a different type"));
    }
  }  
   
  
  /** prepare the Crs transformer (if any) based on the user configuration settings. If no destinationCrs is provided than we use the country to try and infer the
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
      destinationCrsTransformer = PlanitOpenGisUtils.findMathTransform(network.getCoordinateReferenceSystem(), identifiedDestinationCrs);
    }
    
    return identifiedDestinationCrs;
  }   
  
  /** using the destination crs and its transformer extract the coordinate from the position in the desired crs
   * 
   * @param location to extract destination crs compatible coordinate for
   * @return coordinate created
   * @throws TransformException thrown if error
   * @throws MismatchedDimensionException thrown if error 
   */
  protected Coordinate extractDestinationCrsCompatibleCoordinate(Point location) throws MismatchedDimensionException, TransformException {
    /* geometry of the node (optional) */
    Coordinate coordinate = null;
    if(destinationCrsTransformer!=null) {
      coordinate = ((Point)JTS.transform(location, destinationCrsTransformer)).getCoordinate();
    }else {
      coordinate = location.getCoordinate();  
    }
    return coordinate;
  }  
  
  /** Constructor
   * @param idMapperType to use
   */
  protected PlanitMatsimWriter(IdMapperType idMapperType) {
    super(idMapperType);
  }
  
  /** add indentation to stream at current indentation level
   * @param xmlWriter to use
   * @throws XMLStreamException thrown if error
   */
  protected void writeIndentation(XMLStreamWriter xmlWriter) throws XMLStreamException {
    PlanitXmlWriterUtils.writeIndentation(xmlWriter, indentLevel);
  }  
  
  /** increase indentation level
   */
  protected void increaseIndentation() throws XMLStreamException {
    ++indentLevel;
  }
  
  /** decrease indentation level
   */
  protected void decreaseIndentation() throws XMLStreamException {
    --indentLevel;
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
  
  /**
   * default extension for xml files generated
   */
  public static final String DEFAULT_FILE_NAME_EXTENSION = ".xml";  
  
}
