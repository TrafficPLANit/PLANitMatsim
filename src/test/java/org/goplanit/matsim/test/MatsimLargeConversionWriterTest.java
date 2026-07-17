package org.goplanit.matsim.test;

import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.demands.discrete.util.DirectionBound;
import org.goplanit.io.converter.demands.PlanitDiscreteDemandsReaderFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderFactory;
import org.goplanit.logging.Logging;
import org.goplanit.matsim.converter.MatsimIntermodalWriterFactory;
import org.goplanit.matsim.converter.demand.LocationGeneratorType;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterFactory;
import org.goplanit.matsim.util.MatsimAssertionUtils;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.zoning.Zoning;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

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

  private static final Path PLANIT_INPUT_PATH = Path.of("src", "test", "resources", "planit","sydneygma");

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(MatsimLargeConversionWriterTest.class);
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

      var carMode = planitNetwork.getModes().get(PredefinedModeType.CAR);
      var taxiMode = planitNetwork.getModes().getFactory().registerNew(PredefinedModeType.TAXI);
      var rideShareMode = planitNetwork.getModes().getFactory().registerNew(PredefinedModeType.RIDE_SHARE);
      var hovMode = planitNetwork.getModes().getFactory().registerNew(PredefinedModeType.CAR_HIGH_OCCUPANCY);
      planitNetwork.getTransportLayers().stream().flatMap( l ->
          l.getLinkSegmentTypes().stream()).forEach(lt -> {
            if(lt.isModeAllowed(carMode)) {
              var ag = lt.getAccessProperties(carMode);
              lt.registerModeOnAccessGroup(taxiMode, ag);
              lt.registerModeOnAccessGroup(rideShareMode, ag);
              lt.registerModeOnAccessGroup(hovMode, ag);
            }
      });

      // writer for infrastructure
      var matsimInfraWriter = MatsimIntermodalWriterFactory.create(MATSIM_OUTPUT_DIR.toAbsolutePath().toString());
      matsimInfraWriter.write(planitNetwork, planitZoning);


      // reader for demand
      var planitDemandReader = PlanitDiscreteDemandsReaderFactory.create(
          PLANIT_INPUT_PATH.toAbsolutePath().toString(), planitNetwork, planitZoning);
      var planitDiscreteDemands = planitDemandReader.read();

      // writer for demand - utilising network and zoning for reference
      var plansWriter =
          MatsimDiscreteDemandsWriterFactory.create(planitNetwork, planitZoning);
      // map plans to physical locations based on distance weighted random draws within the zone of the activity
      plansWriter.getSettings().setLocationGeneratorType(LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED);
      plansWriter.write(planitDiscreteDemands);

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe(e.getMessage());
      fail(e.getMessage());
    }
  }
}
