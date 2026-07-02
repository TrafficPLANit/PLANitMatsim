package org.goplanit.matsim.test;

import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.demands.discrete.util.DirectionBound;
import org.goplanit.logging.Logging;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterFactory;
import org.goplanit.matsim.util.MatsimAssertionUtils;
import org.goplanit.network.MacroscopicNetwork;
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
import java.time.temporal.TemporalUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit test cases for converting discrete demands to MATSim plans
 *
 * @author markr
 *
 */
public class MatsimSimpleDiscreteDemandWriterTest {

  /**
   * the logger
   */
  private static Logger LOGGER = null;

  private static final Path RESOURCE_PATH = Path.of("src", "test", "resources");

  private static final String PURPOSE_WORK = "work";
  private static final String PURPOSE_SHOPPING = "shopping";
  private static final String PURPOSE_GYM = "gym";

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(MatsimSimpleDiscreteDemandWriterTest.class);
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
  public void testMemoryModelToMatsimPlans() {

    final Path MATSIM_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(), "testcases", "synthetic", "plans");
    final Path MATSIM_REF_DIR = Path.of(RESOURCE_PATH.toString(), "matsim", "synthetic", "plans");

    try {

      var network = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken());
      var carMode = network.getModes().getFactory().registerNew(PredefinedModeType.CAR);
      var busMode = network.getModes().getFactory().registerNew(PredefinedModeType.BUS);
      var trainMode = network.getModes().getFactory().registerNew(PredefinedModeType.TRAIN);
      var walkMode = network.getModes().getFactory().registerNew(PredefinedModeType.PEDESTRIAN);

      var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
      var zone0 = zoning.getOdZones().getFactory().registerNew();
      var zone1 = zoning.getOdZones().getFactory().registerNew();
      var zone2 = zoning.getOdZones().getFactory().registerNew();
      var zone3 = zoning.getOdZones().getFactory().registerNew();
      var zone4 = zoning.getOdZones().getFactory().registerNew();

      var discreteDemands = new DiscreteDemands(network.getIdGroupingToken());

      var allDayTimePeriod = discreteDemands.getTimePeriods().getFactory().registerNew(
          "all day", 0, 24 * 3600);

      // 2 households
      var household0 = discreteDemands.getHouseholds().getFactory().registerNew();
      var household1 = discreteDemands.getHouseholds().getFactory().registerNew();

      // 4 people, 2:2 split across households
      var person0 = discreteDemands.getPersons().getFactory().registerNew(household0);
      var person1 = discreteDemands.getPersons().getFactory().registerNew(household0);
      var person2 = discreteDemands.getPersons().getFactory().registerNew(household1);
      var person3 = discreteDemands.getPersons().getFactory().registerNew(household1);

      // each person has a schedule with one or more (sequential) tours which in turn may contain nested tours
      // each tour origin == tour id, destination == tour id + 1
      boolean addToSchedule = true;
      var tour0_p0 = discreteDemands.getTours().getFactory().registerNew(
          person0, zone0, zone1, LocalTime.of(8, 0), LocalTime.of(17, 30), addToSchedule);
      tour0_p0.setPurpose(PURPOSE_WORK);
      var tour1_p1 = discreteDemands.getTours().getFactory().registerNew(
          person1, zone1, zone2, LocalTime.of(8, 0), LocalTime.of(17, 30), addToSchedule);
      tour1_p1.setPurpose(PURPOSE_WORK);
      var tour2_p2 = discreteDemands.getTours().getFactory().registerNew(
          person2, zone2, zone3, LocalTime.of(10, 0), LocalTime.of(13, 0), addToSchedule);
      tour2_p2.setPurpose(PURPOSE_SHOPPING);
      var tour3_p3 = discreteDemands.getTours().getFactory().registerNew(
          person3, zone3, zone4, LocalTime.of(15, 0), LocalTime.of(16, 0), addToSchedule);
      tour3_p3.setPurpose(PURPOSE_GYM);

      // outbound trips of main tour - always starting point
      var tour0_outboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour0_p0, DirectionBound.OUTBOUND, addToSchedule);
      tour0_outboundTrip.setMode(carMode);
      tour0_outboundTrip.syncStartTimeToTourStartTime();
      var tour1_outboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour1_p1, DirectionBound.OUTBOUND, addToSchedule);
      tour1_outboundTrip.setMode(trainMode);
      tour1_outboundTrip.syncStartTimeToTourStartTime();
      var tour2_outboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour2_p2, DirectionBound.OUTBOUND, addToSchedule);
      tour2_outboundTrip.setMode(busMode);
      tour2_outboundTrip.syncStartTimeToTourStartTime();
      var tour3_outboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour3_p3, DirectionBound.OUTBOUND, addToSchedule);
      tour3_outboundTrip.setMode(walkMode);
      tour3_outboundTrip.syncStartTimeToTourStartTime();

      // for tour0: add a sub-tour (so nested at destination of original tour)
      var tour0_subtour0_p0 = discreteDemands.getTours().getFactory().registerNew(
          tour0_p0, tour0_p0.getDestination(), zone2,
          LocalTime.of(12, 30), LocalTime.of(13, 30), addToSchedule);
      tour0_subtour0_p0.setPurpose(PURPOSE_GYM);
      //  with inbound + outbound trip for subtour
      {
        var tour0_subtour0_outbound = discreteDemands.getTrips().getFactory().registerNew(
            tour0_subtour0_p0, DirectionBound.OUTBOUND, addToSchedule);
        tour0_subtour0_outbound.setMode(walkMode);
        tour0_subtour0_outbound.syncStartTimeToTourStartTime();
        var tour0_subtour0_inbound = discreteDemands.getTrips().getFactory().registerNew(
            tour0_subtour0_p0, DirectionBound.INBOUND, addToSchedule);
        tour0_subtour0_inbound.setMode(walkMode);
        tour0_subtour0_inbound.syncStartTimeToTourStartTime();
      }

      // inbound trips of main tour - back to origin
      var tour0_inboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour0_p0, DirectionBound.INBOUND, addToSchedule);
      tour0_inboundTrip.setMode(carMode);
      tour0_inboundTrip.syncStartTimeToTourWithNegativeOffset(Duration.of(30, ChronoUnit.MINUTES));
      var tour1_inboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour1_p1, DirectionBound.INBOUND, addToSchedule);
      tour1_inboundTrip.setMode(trainMode);
      tour1_inboundTrip.syncStartTimeToTourWithNegativeOffset(Duration.of(45, ChronoUnit.MINUTES));
      var tour2_inboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour2_p2, DirectionBound.INBOUND, addToSchedule);
      tour2_inboundTrip.setMode(walkMode); // walk back, even though we took bus to the destination
      tour2_inboundTrip.syncStartTimeToTourWithNegativeOffset(Duration.of(20, ChronoUnit.MINUTES));
      var tour3_inboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour3_p3, DirectionBound.INBOUND, addToSchedule);
      tour3_inboundTrip.setMode(walkMode);
      tour3_inboundTrip.syncStartTimeToTourWithNegativeOffset(Duration.of(20, ChronoUnit.MINUTES));

      // for tour0: add another sequential tour (so placed AFTER returning at origin from original tour)
      var tour_after_tour0_p0 = discreteDemands.getTours().getFactory().registerNew(
          person0, tour0_p0.getDestination(), zone3,
          LocalTime.of(18, 0), LocalTime.of(20, 30), addToSchedule);
      tour_after_tour0_p0.setPurpose(PURPOSE_SHOPPING);
      //  with inbound + outbound trip for this next tour
      {
        var tour_after_tour0_outbound = discreteDemands.getTrips().getFactory().registerNew(
            tour_after_tour0_p0, DirectionBound.OUTBOUND, addToSchedule);
        tour_after_tour0_outbound.setMode(walkMode);
        tour_after_tour0_outbound.syncStartTimeToTourStartTime();
        var tour_after_tour0_inbound = discreteDemands.getTrips().getFactory().registerNew(
            tour_after_tour0_p0, DirectionBound.INBOUND, addToSchedule);
        tour_after_tour0_inbound.setMode(walkMode);
        tour_after_tour0_inbound.syncStartTimeToTourWithNegativeOffset(Duration.of(10, ChronoUnit.MINUTES));
      }


      /* perform the conversion*/
      var matsimPlansWriter =
          MatsimDiscreteDemandsWriterFactory.create(MATSIM_OUTPUT_DIR.toAbsolutePath().toString(), network, zoning);
      matsimPlansWriter.getSettings().setWriteAsGZip(false);
      matsimPlansWriter.write(discreteDemands);

      MatsimAssertionUtils.assertPlansFilesSimilar(MATSIM_OUTPUT_DIR, MATSIM_REF_DIR);

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe(e.getMessage());
      fail(e.getMessage());
    }
  }
}
