package org.goplanit.matsim.test;

import org.goplanit.converter.intermodal.IntermodalConverterFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderFactory;
import org.goplanit.logging.Logging;
import org.goplanit.matsim.converter.MatsimIntermodalWriterFactory;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterStats;
import org.goplanit.matsim.util.MatsimAssertionUtils;
import org.goplanit.matsim.util.MatsimBuiltInMode;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionSegmentKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  /**
   * The link segments written with a reduced exit capacity, by kind: 125 junction approaches at 0.44, 39 at 0.28 and 17
   * at 0.94, 28 approaches at a crossing only at 0.9 and 3 approaches without a signal at 0.5. The road between the two
   * crossings side by side on Castlereagh Street at Market Street is internal to their one crossing intersection, so
   * it is not reduced
   *
   * @param networkStats of the MATSim network writer
   */
  private static void assertLinkExitCapacityReductions(MatsimNetworkWriterStats networkStats) {
    assertEquals(212, networkStats.getLinkSegmentsWithReducedExitCapacity());
    assertEquals(181,
        networkStats.getLinkSegmentsWithReducedExitCapacity(IntersectionSegmentKind.APPROACH_WITH_SIGNAL_AT_JUNCTION));
    assertEquals(28, networkStats.getLinkSegmentsWithReducedExitCapacity(
        IntersectionSegmentKind.APPROACH_WITH_SIGNAL_AT_CROSSING_ONLY));
    assertEquals(3,
        networkStats.getLinkSegmentsWithReducedExitCapacity(IntersectionSegmentKind.APPROACH_WITHOUT_SIGNAL, 0.5));
  }

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
   * Test case which parses a PLANit network without services, loads it into PLANit memory model and persists it as a
   * MATSim network without pt services
   * <p>
   * Source: PLANit inputs of the network are sourced from the results of running
   * GtfsToPlanitSydneyTest.testGtfsIntermodalReaderWithPreExistingPlanitTransferZones
   * </p>
   */
  @Test
  public void testPlanit2MatsimNoServicesDefault() {

    final Path MATSIM_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases", "sydney", "without_services");
    final Path MATSIM_REF_DIR =  Path.of(RESOURCE_PATH.toString(),"matsim","sydney", "without_services");

    try {

      var planitReader = PlanitIntermodalReaderFactory.create(SYDNEYCBD_PLANIT.toAbsolutePath().toString());

      var matsimWriter = MatsimIntermodalWriterFactory.create(
          MATSIM_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.AUSTRALIA);
      matsimWriter.getSettings().getNetworkSettings().setGenerateDetailedLinkGeometryFile(true);
      matsimWriter.getSettings().getNetworkSettings().setReduceLinkExitCapacityByEffectiveCapacityFactor(true);
      matsimWriter.getSettings().setWriteAsGZip(false);

      /* perform the conversion*/
      IntermodalConverterFactory.create(planitReader, matsimWriter).convert();

      MatsimAssertionUtils.assertNetworkFilesSimilar(MATSIM_OUTPUT_DIR, MATSIM_REF_DIR);
      assertTrue(MatsimAssertionUtils.isNetworkGeometryFilesSimilar(MATSIM_OUTPUT_DIR,MATSIM_REF_DIR));
      assertTrue(MatsimAssertionUtils.isPtStopsFilesSimilar(MATSIM_OUTPUT_DIR,MATSIM_REF_DIR));
      MatsimAssertionUtils.assertTransitScheduleFilesSimilar(MATSIM_OUTPUT_DIR, MATSIM_REF_DIR);

      var networkStats = matsimWriter.getNetworkWriterStats();
      assertEquals(1_107, networkStats.getNodesWritten());
      assertEquals(1_889, networkStats.getLinkSegmentsWritten());
      assertEquals(544, networkStats.getLinkSegmentsSkippedNoActivatedMode());
      assertEquals(44, networkStats.getTurnRestrictionsWritten());
      assertLinkExitCapacityReductions(networkStats);

      /* the stops are written, their services are not, so the schedule holds no lines to run along them */
      var ptStats = matsimWriter.getZoningWriterStats().getPtWriterStats();
      assertEquals(98, ptStats.getStopFacilitiesWritten());
      assertEquals(0, ptStats.getTransitLinesWritten());
      assertEquals(0, ptStats.getTransitRoutesWritten());

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail(e.getMessage());
    }
  }

  /**
   * Test case which parses a PLANit network with services, loads it into PLANit memory model and persists it as a
   * MATSim network with pt services
   * <p>
   * Source: PLANit inputs of the network are sourced from the results of running
   * GtfsToPlanitSydneyTest.testGtfsIntermodalReaderWithPreExistingPlanitTransferZones
   * </p>
   */
  @Test
  public void testPlanit2MatsimWithServicesDefault() {
    
    final Path MATSIM_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases", "sydney", "with_services");
    final Path MATSIM_REF_DIR =  Path.of(RESOURCE_PATH.toString(),"matsim","sydney", "with_services");

    try {

      var planitReader = PlanitIntermodalReaderFactory.create(SYDNEYCBD_PLANIT.toAbsolutePath().toString());

      var matsimWriter = MatsimIntermodalWriterFactory.create(
              MATSIM_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.AUSTRALIA);
      matsimWriter.getSettings().getNetworkSettings().setGenerateDetailedLinkGeometryFile(true);
      matsimWriter.getSettings().getNetworkSettings().setReduceLinkExitCapacityByEffectiveCapacityFactor(true);
      matsimWriter.getSettings().setWriteAsGZip(false);

      /* perform the conversion*/
      IntermodalConverterFactory.create(planitReader, matsimWriter).convertWithServices();

      MatsimAssertionUtils.assertNetworkFilesSimilar(MATSIM_OUTPUT_DIR, MATSIM_REF_DIR);
      MatsimAssertionUtils.assertTransitScheduleFilesSimilar(MATSIM_OUTPUT_DIR, MATSIM_REF_DIR);
      assertTrue(MatsimAssertionUtils.isNetworkGeometryFilesSimilar(MATSIM_OUTPUT_DIR,MATSIM_REF_DIR));

      var networkStats = matsimWriter.getNetworkWriterStats();
      assertEquals(1_107, networkStats.getNodesWritten());
      assertEquals(1_889, networkStats.getLinkSegmentsWritten());
      assertEquals(544, networkStats.getLinkSegmentsSkippedNoActivatedMode());
      assertEquals(44, networkStats.getTurnRestrictionsWritten());
      assertLinkExitCapacityReductions(networkStats);

      /* the services are written alongside the stops, so the schedule holds the lines running along them */
      var ptStats = matsimWriter.getRoutedServicesWriterStats().getPtWriterStats();
      assertEquals(98, ptStats.getStopFacilitiesWritten());
      assertEquals(68, ptStats.getTransitLinesWritten());
      assertEquals(255, ptStats.getTransitRoutesWritten());
      assertEquals(255, ptStats.getTransitRoutesWritten(MatsimBuiltInMode.PT.getValue()));
      assertEquals(0, ptStats.getTransitRoutesWritten(MatsimBuiltInMode.CAR.getValue()));

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail(e.getMessage());
    }
  }
  
}