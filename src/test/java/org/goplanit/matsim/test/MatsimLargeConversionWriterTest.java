package org.goplanit.matsim.test;

import org.goplanit.io.converter.demands.PlanitDiscreteDemandsReaderFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderFactory;
import org.goplanit.logging.Logging;
import org.goplanit.matsim.converter.MatsimIntermodalWriterFactory;
import org.goplanit.matsim.converter.demand.LocationGeneratorType;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterFactory;
import org.goplanit.network.MacroscopicNetworkUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.mode.PredefinedModeType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.goplanit.matsim.util.MatsimBuiltInMode;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.zip.ZipUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit test cases for converting a complete PLANit to MATSim conversion (network, zoning, discrete demand (plans))
 *
 * @author markr
 *
 */
public class MatsimLargeConversionWriterTest {

  /**
   * the logger
   */
  private static Logger LOGGER = null;

  private static final Path RESOURCE_PATH = Path.of("src", "test", "resources");

  /** the PLANit inputs as held in the repository, each XML kept zipped to keep it a fraction of its raw size */
  private static final Path PLANIT_ZIPPED_INPUT_PATH = Path.of("src", "test", "resources", "planit","sydneygma");

  /** where the PLANit inputs are unpacked to before reading, the readers expecting plain files */
  private static final Path PLANIT_INPUT_PATH = Path.of("target", "testinput", "planit", "sydneygma");

  /** the PLANit inputs the conversion reads, each held zipped under the name of the file it contains */
  private static final List<String> ZIPPED_INPUT_FILE_NAMES =
      List.of("network.xml", "zoning.xml", "discrete_demands.xml");

  /**
   * Unpack the zipped PLANit inputs so the readers can be pointed at plain files. A file already unpacked and no
   * older than its archive is left alone, so the cost is paid once rather than on every run
   *
   * @throws Exception when an archive cannot be read or its content not written
   */
  private static void unpackPlanitInputs() throws Exception {
    Files.createDirectories(PLANIT_INPUT_PATH);
    for (var fileName : ZIPPED_INPUT_FILE_NAMES) {
      var zipFile = PLANIT_ZIPPED_INPUT_PATH.resolve(fileName.replace(".xml", ".zip"));
      var unpackedFile = PLANIT_INPUT_PATH.resolve(fileName);
      if (Files.isRegularFile(unpackedFile) &&
          Files.getLastModifiedTime(unpackedFile).compareTo(Files.getLastModifiedTime(zipFile)) >= 0) {
        continue;
      }
      LOGGER.info(String.format("Unpacking PLANit input %s", zipFile));
      try (var inputStream =
               ZipUtils.createZipEntryInputStream(
                   UrlUtils.createFromLocalAbsoluteOrRelativePath(zipFile.toAbsolutePath()), fileName, false)) {
        Files.copy(inputStream, unpackedFile, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(MatsimLargeConversionWriterTest.class);
    }
    unpackPlanitInputs();
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
   * Test case which parses all PLANit inputs and then construct full MATSim outputs (no config file, just the raw
   * components; network, and plans currently)
   * <p>
   * Sources:
   *  (i) zoning is based on the zones created in the PlanitGeoIO repo ZoningReaderTest,
   *  (ii) PLANit network is based on OSM network truncated by the boundary of the zones filtered by external id <7000,
   *  the OSM file used is in the SydneyGMA repo (not yet public), and fidelity is set to LOW
   *  (iii) PLANit Discrete demands are based on a converted synthetic ActivitySim sample from the
   *  PLANitActivitySim repo (not yet public)
   * </p>
   */
  @Test
  public void testPlanitToMatsimFull() {

    final Path MATSIM_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(), "testcases", "sydneygma");
    final Path MATSIM_REF_DIR = Path.of(RESOURCE_PATH.toString(), "matsim", "sydneygma");

    try {

      // reader for infrastructure
      var planitInfraReader = PlanitIntermodalReaderFactory.create(PLANIT_INPUT_PATH.toAbsolutePath().toString());
      var result = planitInfraReader.read();
      var planitNetwork = result.first();
      var planitZoning = result.second();

      MacroscopicNetworkUtils.expandModeSupport(
          planitNetwork,
          PredefinedModeType.CAR, // expand car with:
          PredefinedModeType.CAR_SHARE,
          PredefinedModeType.TAXI,
          PredefinedModeType.RIDE_SHARE,
          PredefinedModeType.CAR_HIGH_OCCUPANCY);

      // writer for infrastructure
      var matsimInfraWriter = MatsimIntermodalWriterFactory.create(MATSIM_OUTPUT_DIR.toAbsolutePath().toString());
      matsimInfraWriter.getSettings().getNetworkSettings().activateAllDefaultMappedModes();
      matsimInfraWriter.write(planitNetwork, planitZoning);


      // reader for demand
      var planitDemandReader = PlanitDiscreteDemandsReaderFactory.create(
          PLANIT_INPUT_PATH.toAbsolutePath().toString(), planitNetwork, planitZoning);
      var planitDiscreteDemands = planitDemandReader.read();

      // writer for demand - utilising network and zoning for reference
      var plansWriter =
          MatsimDiscreteDemandsWriterFactory.create(
              MATSIM_OUTPUT_DIR.toAbsolutePath().toString(), planitNetwork, planitZoning);
      plansWriter.getSettings().activateAllDefaultMappedModes();
      // map plans to physical locations based on distance weighted random draws within the zone of the activity
      plansWriter.getSettings().setLocationGeneratorType(LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED);

      plansWriter.getSettings().setConnectorFlagsForRule(
          PredefinedModeType.BUS, false, false);
      plansWriter.getSettings().setConnectorFlagsForRule(
          PredefinedModeType.TRAIN, false, false);

      plansWriter.write(planitDiscreteDemands);

      /* network */
      var networkStats = matsimInfraWriter.getNetworkWriterStats();
      assertEquals(415_372, networkStats.getNodesWritten());
      assertEquals(1_009_612, networkStats.getLinkSegmentsWritten());
      assertEquals(0, networkStats.getLinkSegmentsSkippedNoActivatedMode());
      assertEquals(12_127, networkStats.getTurnRestrictionsWritten());

      /* plans */
      var plansStats = plansWriter.getWriterStats();
      assertEquals(26_192, plansStats.getPersonsProcessed());
      assertEquals(21_228, plansStats.getPersonsWritten());
      assertEquals(4_964, plansStats.getPersonsSkippedNoTours());
      assertEquals(103_443, plansStats.getActivitiesWritten());
      assertEquals(82_215, plansStats.getLegsWritten());

      /* legs carry the mode the trip was made by */
      assertEquals(59_019, plansStats.getLegsWritten(MatsimBuiltInMode.CAR.getValue()));
      assertEquals(15_022, plansStats.getLegsWritten(MatsimBuiltInMode.WALK.getValue()));
      assertEquals(2_584, plansStats.getLegsWritten(MatsimBuiltInMode.PT.getValue()));
      assertEquals(669, plansStats.getLegsWritten(MatsimBuiltInMode.BIKE.getValue()));
      assertEquals(537, plansStats.getLegsWritten(MatsimBuiltInMode.DRT.getValue()));

      /* a participant carried on a tour owned by another is a passenger, which MATSim expresses as its ride mode,
       * so every such leg is teleported rather than placing a second vehicle on the network alongside the driver */
      assertEquals(4_384, plansStats.getLegsWritten(MatsimBuiltInMode.RIDE.getValue()));
      assertEquals(4_384, plansStats.getPassengerLegsWritten());
      assertEquals(0, plansStats.getAccompanyingParticipationsSkipped());

      /* a plan alternates activity and leg, an activity following another leaves out the movement reaching it */
      assertEquals(0, plansStats.getActivitiesFollowingAnActivity(),
          "plans must alternate activity and leg throughout");
      assertEquals(0, plansStats.getActivitiesWithoutDescription());

      /* a household lives in one dwelling and a purpose pursued in a zone happens in one place, so the number of
       * locations established is a property of the population rather than of the number of visits made to them */
      assertEquals(9_099, plansStats.getDwellingsPinned());
      assertEquals(48_869, plansStats.getActivityLocationsPinned());
      assertEquals(214, plansStats.getCentroidFallbackActivities());

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe(e.getMessage());
      fail(e.getMessage());
    }
  }
}
