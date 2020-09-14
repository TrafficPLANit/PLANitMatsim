package org.planit.matsim.converter;

/**
 * Factory for creating PLANitMatsimWriters
 * @author markr
 *
 */
public class PlanitMatsimWriterFactory {
  
  /** Create a PLANitMatsimWriter which persists PLANit networks in MATSIM network format
   * 
   * @param outputDirectory to use
   * @return create matsim writer
   */
  public static PlanitMatsimWriter createWriter(String outputDirectory) {
    return new PlanitMatsimWriter(outputDirectory);    
  }
    
}
