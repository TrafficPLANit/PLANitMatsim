package org.planit.matsim.converter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.planit.network.converter.NetworkWriter;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;

/**
 * A class that takes a PLANit network and writes it as a MATSIM network
 * 
 * @author markr
  */
public class PlanitMatsimWriter implements NetworkWriter {
  
  /**
   * the logger of this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitMatsimWriter.class.getCanonicalName());
  
  /**
   * create the writer
   * @param matsimNetworkPath to create the writer for
   * @return created xml writer
   * @throws PlanItException thrown if error
   */
  private Pair<XMLStreamWriter,FileWriter> createXMLWriter(Path matsimNetworkPath) throws PlanItException {
    LOGGER.info(String.format("persiting MATSIM network to: %s",matsimNetworkPath));
    
    try {    
      FileWriter theWriter = new FileWriter(matsimNetworkPath.toFile());
      XMLOutputFactory xMLOutputFactory = XMLOutputFactory.newInstance();
      return new Pair<XMLStreamWriter,FileWriter>(xMLOutputFactory.createXMLStreamWriter(theWriter),theWriter);
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
    xmlFileWriterPair.getFirst().writeStartDocument();
  }  
  
  /** write the body of the MATSIM network XML file based on the PLANit network contents
   * 
   * @param xmlWriter the writer
   * @param network to persist
   * @param network 
   */
  private void writeMatsimNetworkXML(XMLStreamWriter xmlWriter, MacroscopicNetwork network) {
    // TODO Auto-generated method stub    
  }  
  
  /**
   * the output directory on where to persist the MATSIM network
   */
  protected final String outputDirectory;
  
  /**
   * the output file name to use, default is set to DEFAULT_NETWORK_FILE_NAME
   */
  protected String outputFileName = DEFAULT_NETWORK_FILE_NAME;
  
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
    this.outputDirectory = outputDirectory;
  }

  /**
   * {@inheritDoc}
   * @throws PlanItException 
   */
  @Override
  public void write(MacroscopicNetwork network) throws PlanItException {
    Path matsimNetworkPath =  Paths.get(outputDirectory, outputFileName);    
    Pair<XMLStreamWriter,FileWriter> xmlFileWriterPair = createXMLWriter(matsimNetworkPath);
            
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


}
