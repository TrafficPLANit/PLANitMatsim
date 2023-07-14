package org.goplanit.matsim.test;

import org.goplanit.converter.intermodal.IntermodalConverterFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderFactory;
import org.goplanit.logging.Logging;
import org.goplanit.matsim.converter.MatsimIntermodalWriterFactory;
import org.goplanit.matsim.util.MatsimAssertionUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit test cases for converting networks from one format to another
 * 
 * @author markr
 *
 */
public class SydneyPlanit2MatsimTest {

  /** the logger */
  private static Logger LOGGER = null;

  private static final Path RESOURCE_PATH = Path.of("src","test","resources");

  private static final Path SYDNEYCBD_PLANIT = Path.of(RESOURCE_PATH.toString(),"planit","sydney");
 
  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(SydneyPlanit2MatsimTest.class);
    } 
  }

  /**
   * run garbage collection after each test as it apparently is not triggered properly within
   * Eclipse (or takes too long before being triggered)
   */
  @AfterEach
  public void afterTest() {
    IdGenerator.reset();
    System.gc();
  }

  @AfterAll
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
  }
  

  /**
   * Test case which parses a PLANit network with services, loads it into PLANit memory model and persists it as a MATSim network with pt services
   * <p>
   * Source: PLANit inputs of the network are sourced from the results of running SydneyOsmGtfs2PlanitTest.testGtfs2PlanitBasicIntermodalWithServices
   * </p>
   */
  @Test
  public void testPlanit2MatsimWithServicesDefault() {
    
    final Path MATSIM_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases", "sydney");
    final Path MATSIM_REF_DIR =  Path.of(RESOURCE_PATH.toString(),"matsim","sydney");

    try {

      var planitReader = PlanitIntermodalReaderFactory.create(SYDNEYCBD_PLANIT.toAbsolutePath().toString());

      var matsimWriter = MatsimIntermodalWriterFactory.create(MATSIM_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.AUSTRALIA);
      matsimWriter.getSettings().getNetworkSettings().setGenerateDetailedLinkGeometryFile(true);

      /* perform the conversion*/
      IntermodalConverterFactory.create(planitReader, matsimWriter).convertWithServices();

      MatsimAssertionUtils.assertNetworkFilesSimilar(MATSIM_OUTPUT_DIR, MATSIM_REF_DIR);
      MatsimAssertionUtils.assertTransitScheduleFilesSimilar(MATSIM_OUTPUT_DIR, MATSIM_REF_DIR);
      assertTrue(MatsimAssertionUtils.isNetworkGeometryFilesSimilar(MATSIM_OUTPUT_DIR,MATSIM_REF_DIR));

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail(e.getMessage());
    }
  }
  
}