package org.planit.matsim.converter;

import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.planit.converter.BaseWriterImpl;
import org.planit.converter.IdMapperType;
import org.planit.geo.PlanitOpenGisUtils;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.utils.exceptions.PlanItException;

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
   * the output directory on where to persist the MATSIM file(s)
   */
  protected final String outputDirectory;  
  
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
  
  /** Constructor
   * @param idMapperType to use
   * @param outputDirectory to use
   */
  protected PlanitMatsimWriter(IdMapperType idMapperType, String outputDirectory) {
    super(idMapperType);
    
    this.outputDirectory = outputDirectory;
  }

  /** write a new line to the stream, e.g. "\n"
   * @param xmlWriter to use
   * @throws XMLStreamException thrown if error 
   */
  protected void writeNewLine(XMLStreamWriter xmlWriter) throws XMLStreamException {
    xmlWriter.writeCharacters("\n");
  }
  
  /** add indentation to stream at current indentation level
   * @param xmlWriter to use
   * @throws XMLStreamException thrown if error
   */
  protected void writeIndentation(XMLStreamWriter xmlWriter) throws XMLStreamException {
    for(int index=0;index<indentLevel;++index) {
      xmlWriter.writeCharacters("\t");
    }
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
   * write a empty element (with indentation), e.g. {@code <xmlElementName/>}
   * 
   * @param xmlWriter to use
   * @param xmlElementName element to start tag, e.g. <xmlElementName>\n
   * @throws XMLStreamException thrown if error
   */
  protected void writeEmptyElement(XMLStreamWriter xmlWriter, String xmlElementName) throws XMLStreamException {
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
  protected void writeStartElementNewLine(XMLStreamWriter xmlWriter, String xmlElementName) throws XMLStreamException {
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
  protected void writeStartElementNewLine(XMLStreamWriter xmlWriter, String xmlElementName, boolean increaseIndentation) throws XMLStreamException {
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
  protected void writeEndElementNewLine(XMLStreamWriter xmlWriter) throws XMLStreamException {
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
  protected void writeEndElementNewLine(XMLStreamWriter xmlWriter, boolean decreaseIndentation) throws XMLStreamException {
    if(decreaseIndentation) {
      decreaseIndentation(); 
    }
    writeEndElementNewLine(xmlWriter);
  }    
  
}
