package org.goplanit.matsim.test;

import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.demands.discrete.tour.ParticipantTour;
import org.goplanit.demands.discrete.tour.Tour;
import org.goplanit.demands.discrete.tour.TourParticipantRole;
import org.goplanit.demands.discrete.util.DirectionBound;
import org.goplanit.logging.Logging;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriter;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterFactory;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterSettings;
import org.goplanit.matsim.util.MatsimAssertionUtils;
import org.goplanit.matsim.util.MatsimBuiltInMode;
import org.goplanit.matsim.xml.MatsimAttributes;
import org.goplanit.matsim.xml.MatsimPlansAttributes;
import org.goplanit.matsim.xml.MatsimPlansElements;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.zoning.OdZone;
import org.goplanit.zoning.Zoning;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

  /**
   * A value of each element with the given tag in the plan of one person in a written plans file, in the order they
   * are written. The plans file's document type is not loaded, so nothing is fetched online
   *
   * @param plansFile to read
   * @param personId the person is written with
   * @param tag of the elements to collect a value of
   * @param valueOf gives the value of an element
   * @return the values, empty when the person is not in the file
   * @throws Exception thrown if the file cannot be read
   */
  private static List<String> collectPlanElementValues(
      Path plansFile, String personId, String tag, Function<Element, String> valueOf) throws Exception {
    var documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    var persons = documentBuilderFactory.newDocumentBuilder().parse(plansFile.toFile())
        .getElementsByTagName(MatsimPlansElements.PERSON);

    var values = new ArrayList<String>();
    for (int personIndex = 0; personIndex < persons.getLength(); ++personIndex) {
      var person = (Element) persons.item(personIndex);
      if (personId.equals(person.getAttribute(MatsimAttributes.ID))) {
        var elements = person.getElementsByTagName(tag);
        for (int elementIndex = 0; elementIndex < elements.getLength(); ++elementIndex) {
          values.add(valueOf.apply((Element) elements.item(elementIndex)));
        }
      }
    }
    return values;
  }

  /**
   * The modes of the legs in the plan of one person in a written plans file, in the order they are written
   *
   * @param plansFile to read
   * @param personId the person is written with
   * @return leg modes, empty when the person is not in the file
   * @throws Exception thrown if the file cannot be read
   */
  private static List<String> collectLegModes(Path plansFile, String personId) throws Exception {
    return collectPlanElementValues(plansFile, personId, MatsimPlansElements.LEG,
        leg -> leg.getAttribute(MatsimPlansAttributes.MODE));
  }

  /**
   * The locations of the activities in the plan of one person in a written plans file, each as its x and y, in the
   * order they are written
   *
   * @param plansFile to read
   * @param personId the person is written with
   * @return activity locations, empty when the person is not in the file
   * @throws Exception thrown if the file cannot be read
   */
  private static List<String> collectActivityLocations(Path plansFile, String personId) throws Exception {
    return collectPlanElementValues(plansFile, personId, MatsimPlansElements.ACTIVITY,
        activity -> activity.getAttribute(MatsimAttributes.X) + "," + activity.getAttribute(MatsimAttributes.Y));
  }

  /**
   * Create a network supporting car, bus, train and walking, and a zoning of five zones with their centroids placed
   * on a diagonal, zone i at (i,i)
   *
   * @return network and zoning
   */
  private static Pair<MacroscopicNetwork, Zoning> createNetworkAndZoning() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(
        PredefinedModeType.CAR, PredefinedModeType.BUS, PredefinedModeType.TRAIN, PredefinedModeType.PEDESTRIAN);

    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    for (int index = 0; index < 5; ++index) {
      zoning.getOdZones().getFactory().registerNew();
    }
    LongAdder counter = new LongAdder();
    zoning.getOdZones().forEach(z -> {
          z.getCentroid().setPosition(PlanitJtsUtils.createPoint(counter.doubleValue(),counter.doubleValue()));
          counter.increment();
        });
    return Pair.of(network, zoning);
  }

  /**
   * Create a plans writer writing uncompressed plans in the network's coordinate reference system, with car, bus,
   * train and walking activated and mapped to MATSim's private_car, pt, pt and walk respectively
   *
   * @param outputDir to write to
   * @param network to use
   * @param zoning to use
   * @return plans writer
   */
  private static MatsimDiscreteDemandsWriter createPlansWriter(Path outputDir, MacroscopicNetwork network, Zoning zoning) {
    var matsimPlansWriter =
        MatsimDiscreteDemandsWriterFactory.create(outputDir.toAbsolutePath().toString(), network, zoning);
    // settings/config
    matsimPlansWriter.getSettings().setDestinationCoordinateReferenceSystem(network.getCoordinateReferenceSystem());
    matsimPlansWriter.getSettings().setWriteAsGZip(false);
    // explicitly activate mode mapping
    matsimPlansWriter.getSettings().activatePredefinedMode(PredefinedModeType.CAR);
    matsimPlansWriter.getSettings().activatePredefinedMode(PredefinedModeType.BUS);
    matsimPlansWriter.getSettings().activatePredefinedMode(PredefinedModeType.TRAIN);
    matsimPlansWriter.getSettings().activatePredefinedMode(PredefinedModeType.PEDESTRIAN);
    // explicitly map to MATSim modes as preferred
    matsimPlansWriter.getSettings().updatePredefinedModeMapping(PredefinedModeType.CAR, "private_car");
    matsimPlansWriter.getSettings().updatePredefinedModeMapping(PredefinedModeType.BUS, "pt");
    matsimPlansWriter.getSettings().updatePredefinedModeMapping(PredefinedModeType.TRAIN, "pt");
    matsimPlansWriter.getSettings().updatePredefinedModeMapping(PredefinedModeType.PEDESTRIAN, "walk");
    return matsimPlansWriter;
  }

  /**
   * Create demands of a single person living in zone0 with a work tour to zone2 from 08:00 to 17:00, without trips
   *
   * @param network to use
   * @param zoning to use
   * @return demands and the work tour
   */
  private static Pair<DiscreteDemands, Tour> createWorkTourDemands(MacroscopicNetwork network, Zoning zoning) {
    var discreteDemands = new DiscreteDemands(network.getIdGroupingToken());
    discreteDemands.getTimePeriods().getFactory().registerNew("all day", 0, (24 * 3600)-1);
    var tour = registerPersonWithWorkTour(discreteDemands, zoning.getOdZones().get(0), zoning.getOdZones().get(2));
    return Pair.of(discreteDemands, tour);
  }

  /**
   * Register a person in a household of their own living in the home zone, with a work tour to the work zone from
   * 08:00 to 17:00, without trips
   *
   * @param discreteDemands to register on
   * @param homeZone the household lives in
   * @param workZone the person works in
   * @return the work tour
   */
  private static Tour registerPersonWithWorkTour(DiscreteDemands discreteDemands, OdZone homeZone, OdZone workZone) {
    var household = discreteDemands.getHouseholds().getFactory().registerNew();
    household.setZone(homeZone);
    var person = discreteDemands.getPersons().getFactory().registerNew(household);
    person.setInitialPurpose("home");

    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, homeZone, workZone, LocalTime.of(8, 0), LocalTime.of(17, 0), true);
    tour.setPurpose(PURPOSE_WORK);
    return tour;
  }

  /**
   * Create a network supporting car, bus, train and walking, and a zoning of two square zones of 1 km with their
   * polygons, zone0 west of zone1. Each zone is crossed by a two-way road of eight links at y 400, away from its
   * centroid at y 500, so an activity placed on a link of its zone can be told from one placed on the centroid
   *
   * @return network and zoning
   */
  private static Pair<MacroscopicNetwork, Zoning> createNetworkAndZoningWithRoads() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(
        PredefinedModeType.CAR, PredefinedModeType.BUS, PredefinedModeType.TRAIN, PredefinedModeType.PEDESTRIAN);
    var layer = network.getTransportLayers().getFirst();
    var roadType = NetworkLayerTestUtils.registerLinkSegmentTypeAllowing(layer, "road", 1000, 50,
        network.getModes().get(PredefinedModeType.CAR), network.getModes().get(PredefinedModeType.PEDESTRIAN));

    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    for (int zoneIndex = 0; zoneIndex < 2; ++zoneIndex) {
      double west = zoneIndex * 1000.0;
      var zone = zoning.getOdZones().getFactory().registerNew();
      zone.setGeometry(PlanitJtsUtils.create2DPolygon(
          List.of(west, 0.0, west + 1000, 0.0, west + 1000, 1000.0, west, 1000.0, west, 0.0)));
      zone.getCentroid().setPosition(PlanitJtsUtils.createPoint(west + 500, 500));

      var previous = NetworkLayerTestUtils.registerNodeAt(layer, west + 100, 400);
      for (int nodeIndex = 2; nodeIndex <= 9; ++nodeIndex) {
        var next = NetworkLayerTestUtils.registerNodeAt(layer, west + 100 * nodeIndex, 400);
        NetworkLayerTestUtils.registerLinkWithOneSegment(layer, previous, next, roadType);
        NetworkLayerTestUtils.registerLinkWithOneSegment(layer, next, previous, roadType);
        previous = next;
      }
    }
    return Pair.of(network, zoning);
  }

  /**
   * Register the trip to work at the start of a work tour and the trip home half an hour before its end
   *
   * @param discreteDemands to register the trips on
   * @param tour work tour, see {@link #createWorkTourDemands(MacroscopicNetwork, Zoning)}
   * @param toWorkMode mode of the trip to work
   * @param toHomeMode mode of the trip home
   */
  private static void registerTripsToWorkAndHome(
      DiscreteDemands discreteDemands, Tour tour, Mode toWorkMode, Mode toHomeMode) {
    boolean addToSchedule = true;
    var toWork = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.OUTBOUND, addToSchedule);
    toWork.setMode(toWorkMode);
    toWork.syncStartTimeToTourStartTime();
    var toHome = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.INBOUND, addToSchedule);
    toHome.setMode(toHomeMode);
    toHome.syncStartTimeToTourEndWithNegativeOffset(Duration.of(30, ChronoUnit.MINUTES));
  }

  /**
   * Add a companion from the primary participant's household to the tour, accompanying them
   *
   * @param discreteDemands to register the companion in
   * @param tour to add the companion to
   * @return the companion's participation, without a driver value
   */
  private static ParticipantTour addCompanion(DiscreteDemands discreteDemands, Tour tour) {
    var companion =
        discreteDemands.getPersons().getFactory().registerNew(tour.getPrimaryParticipant().getHousehold());
    companion.setInitialPurpose("home");
    var companionParticipation = tour.addParticipant(companion, TourParticipantRole.ACCOMPANYING, null);
    companion.getSchedule().add(companionParticipation);
    return companionParticipation;
  }

  /**
   * Test case which parses a PLANit network without services, loads it into PLANit memory model and persists it as a
   * MATSim network without pt services
   * <p>
   * Source: PLANit inputs of the network are sourced from the results of running
   * GtfsToPlanitSydneyTest.testGtfsIntermodalReaderWithPreExistingPlanitTransferZones
   * </p>
   */
  private void runPlansWriterTest(String outputSubDir, String referenceSubDir) {

    final Path MATSIM_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(), "testcases", "synthetic", outputSubDir);
    final Path MATSIM_REF_DIR = Path.of(RESOURCE_PATH.toString(), "matsim", "synthetic", referenceSubDir);

    try {

      var networkAndZoning = createNetworkAndZoning();
      var network = networkAndZoning.first();
      var zoning = networkAndZoning.second();

      var carMode = network.getModes().get(PredefinedModeType.CAR);
      var busMode = network.getModes().get(PredefinedModeType.BUS);
      var trainMode = network.getModes().get(PredefinedModeType.TRAIN);
      var walkMode = network.getModes().get(PredefinedModeType.PEDESTRIAN);

      var zone0 = zoning.getOdZones().get(0);
      var zone1 = zoning.getOdZones().get(1);
      var zone2 = zoning.getOdZones().get(2);
      var zone3 = zoning.getOdZones().get(3);
      var zone4 = zoning.getOdZones().get(4);

      var discreteDemands = new DiscreteDemands(network.getIdGroupingToken());

      // P0: (as a worked example)
      //      HOME (zone0)
      //          |
      //          +-- Trip OUTBOUND [car] 08:00
      //          |
      //          +-- TOUR: WORK
      //          |    zone0 -> zone1
      //          |    08:00 - 17:30
      //          |
      // |    WORK activity @ zone1
      // |    |
      // |    +-- Trip OUTBOUND [walk] 12:30
      //          |    |
      // |    +-- SUBTOUR: GYM
      //          |    |    zone1 -> zone2 -> zone1
      //          |    |    12:30 - 13:30
      //          |    |
      // |    +-- Trip INBOUND [walk] 13:10
      //          |    |
      // |    +-- Resume WORK activity @ zone1
      // |
      //      +-- Trip INBOUND [car] 17:00
      //          |
      //          HOME (zone0)
      //          |
      //          +-- Trip OUTBOUND [walk] 18:00
      //          |
      //          +-- TOUR: SHOPPING
      //          |    zone0 -> zone1 18:00
      //          |    zone1 -> zone3 19:00
      //          |    18:00 - 20:30
      //          |
      //          +-- Trip INBOUND [walk] 20:20
      //          |
      //          END

      // time period
      discreteDemands.getTimePeriods().getFactory().registerNew(
          "all day", 0, (24 * 3600)-1);

      // 2 households
      var household0 = discreteDemands.getHouseholds().getFactory().registerNew();
      household0.setZone(zone0);
      var household1 = discreteDemands.getHouseholds().getFactory().registerNew();
      household1.setZone(zone2);

      // 4 people, 2:2 split across households
      var person0 = discreteDemands.getPersons().getFactory().registerNew(household0);
      var person1 = discreteDemands.getPersons().getFactory().registerNew(household0);
      var person2 = discreteDemands.getPersons().getFactory().registerNew(household1);
      var person3 = discreteDemands.getPersons().getFactory().registerNew(household1);
      discreteDemands.getPersons().forEach(p -> p.setInitialPurpose("home"));

      // each person has a schedule with one or more (sequential) tours which in turn may contain nested tours
      // each tour origin == tour id, destination == tour id + 1
      boolean addToSchedule = true;
      var tour0_p0 = discreteDemands.getTours().getFactory().registerNew(
          person0, zone0, zone1, LocalTime.of(8, 0), LocalTime.of(17, 30), addToSchedule);
      tour0_p0.setPurpose(PURPOSE_WORK);
      var tour1_p1 = discreteDemands.getTours().getFactory().registerNew(
          person1, zone0, zone2, LocalTime.of(8, 0), LocalTime.of(17, 30), addToSchedule);
      tour1_p1.setPurpose(PURPOSE_WORK);
      var tour2_p2 = discreteDemands.getTours().getFactory().registerNew(
          person2, zone2, zone3, LocalTime.of(10, 0), LocalTime.of(13, 0), addToSchedule);
      tour2_p2.setPurpose(PURPOSE_SHOPPING);
      var tour3_p3 = discreteDemands.getTours().getFactory().registerNew(
          person3, zone2, zone4, LocalTime.of(15, 0), LocalTime.of(16, 0), addToSchedule);
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
        tour0_subtour0_inbound.syncStartTimeToTourEndWithNegativeOffset(Duration.of(20, ChronoUnit.MINUTES));
      }

      // inbound trips of main tour - back to origin
      var tour0_inboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour0_p0, DirectionBound.INBOUND, addToSchedule);
      tour0_inboundTrip.setMode(carMode);
      tour0_inboundTrip.syncStartTimeToTourEndWithNegativeOffset(Duration.of(30, ChronoUnit.MINUTES));
      var tour1_inboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour1_p1, DirectionBound.INBOUND, addToSchedule);
      tour1_inboundTrip.setMode(trainMode);
      tour1_inboundTrip.syncStartTimeToTourEndWithNegativeOffset(Duration.of(45, ChronoUnit.MINUTES));
      var tour2_inboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour2_p2, DirectionBound.INBOUND, addToSchedule);
      tour2_inboundTrip.setMode(walkMode); // walk back, even though we took bus to the destination
      tour2_inboundTrip.syncStartTimeToTourEndWithNegativeOffset(Duration.of(20, ChronoUnit.MINUTES));
      var tour3_inboundTrip = discreteDemands.getTrips().getFactory().registerNew(
          tour3_p3, DirectionBound.INBOUND, addToSchedule);
      tour3_inboundTrip.setMode(walkMode);
      tour3_inboundTrip.syncStartTimeToTourEndWithNegativeOffset(Duration.of(20, ChronoUnit.MINUTES));

      // for tour0: add another sequential tour (so placed AFTER returning at origin from original tour)
      var tour_after_tour0_p0 = discreteDemands.getTours().getFactory().registerNew(
          person0, tour0_p0.getOrigin(), zone3,
          LocalTime.of(18, 0), LocalTime.of(20, 30), addToSchedule);
      tour_after_tour0_p0.setPurpose(PURPOSE_SHOPPING);
      //  with inbound + outbound trip for this next tour
      {
        var tour_after_tour0_outbound1 = discreteDemands.getTrips().getFactory().registerNew(
            tour_after_tour0_p0, DirectionBound.OUTBOUND, addToSchedule);
        tour_after_tour0_outbound1.setPurpose("shopping_part1");
        tour_after_tour0_outbound1.setDestination(zone1);
        tour_after_tour0_outbound1.setMode(walkMode);
        tour_after_tour0_outbound1.syncStartTimeToTourStartTime();
        var tour_after_tour0_outbound2 = discreteDemands.getTrips().getFactory().registerNew(
            tour_after_tour0_p0, DirectionBound.OUTBOUND, addToSchedule);
        tour_after_tour0_outbound2.setPurpose("shopping_part2");
        tour_after_tour0_outbound2.setOrigin(zone1);
        tour_after_tour0_outbound2.setMode(walkMode);
        tour_after_tour0_outbound2.setStartTime(LocalTime.of(19,0));
        var tour_after_tour0_inbound = discreteDemands.getTrips().getFactory().registerNew(
            tour_after_tour0_p0, DirectionBound.INBOUND, addToSchedule);
        tour_after_tour0_inbound.setMode(walkMode);
        tour_after_tour0_inbound.syncStartTimeToTourEndWithNegativeOffset(Duration.of(10, ChronoUnit.MINUTES));
      }

      // a shared tour is held once and carries one set of trips, and everyone else taking part in it comes along on
      // those same trips. Two shared tours are set up, differing in the mode they are made on

      // tour0 is made by car, so person1 is carried by person0 and travels it as a passenger
      person1.getSchedule().add(tour0_p0.addParticipant(person1, TourParticipantRole.ACCOMPANYING));

      // tour2 goes by bus and returns on foot, neither of which anyone is carried on, so person3 travels it exactly
      // as person2 does
      person3.getSchedule().add(tour2_p2.addParticipant(person3, TourParticipantRole.ACCOMPANYING));

      // person4 has no travel of their own, so accompanying person2 is the whole of their day
      var person4 = discreteDemands.getPersons().getFactory().registerNew(household1);
      person4.setInitialPurpose("home");
      person4.getSchedule().add(tour2_p2.addParticipant(person4, TourParticipantRole.ACCOMPANYING));

      var matsimPlansWriter = createPlansWriter(MATSIM_OUTPUT_DIR, network, zoning);

      // convert
      matsimPlansWriter.write(discreteDemands);

      MatsimAssertionUtils.assertPlansFilesSimilar(MATSIM_OUTPUT_DIR, MATSIM_REF_DIR);

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe(e.getMessage());
      fail(e.getMessage());
    }
  }

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(MatsimSimpleDiscreteDemandWriterTest.class);
    }
  }

  @AfterAll
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
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

  /**
   * Everyone taking part in a shared tour travels it. On a mode they are carried on they are a passenger, written
   * with MATSim's ride mode so that the tour still puts a single vehicle on the network; on any other mode they
   * travel by that mode in their own right
   */
  @Test
  public void testMemoryModelToMatsimPlansWithAccompanyingParticipants() {
    runPlansWriterTest("plans_joint", "plans_joint");
  }

  /**
   * Two public transport trips in a row with an activity between them are two legs with that activity between them,
   * as the activity shows the stop is not a transfer. Only the collapse rules merge trips, and with none set nothing
   * is merged. Aggregate transit modes map train and bus to MATSim's pt alike
   * <pre>
   *   HOME (zone0) --train--> SHOPPING (zone1) --bus--> WORK (zone2) --walk--> HOME (zone0)
   *        08:00                   08:45                     16:30
   * </pre>
   *
   * @param outputDir to write the plans to
   */
  @Test
  public void testConsecutivePublicTransportTripsKeepActivityInBetween(@TempDir Path outputDir) {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var tour = demandsAndTour.second();

    boolean addToSchedule = true;
    var toShops = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.OUTBOUND, addToSchedule);
    toShops.setPurpose(PURPOSE_SHOPPING);
    toShops.setDestination(zoning.getOdZones().get(1));
    toShops.setMode(network.getModes().get(PredefinedModeType.TRAIN));
    toShops.syncStartTimeToTourStartTime();
    var toWork = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.OUTBOUND, addToSchedule);
    toWork.setOrigin(zoning.getOdZones().get(1));
    toWork.setMode(network.getModes().get(PredefinedModeType.BUS));
    toWork.setStartTime(LocalTime.of(8, 45));
    var toHome = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.INBOUND, addToSchedule);
    toHome.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    toHome.syncStartTimeToTourEndWithNegativeOffset(Duration.of(30, ChronoUnit.MINUTES));

    var matsimPlansWriter = createPlansWriter(outputDir, network, zoning);
    matsimPlansWriter.getSettings().setUseDisaggregateTransitModes(false);
    matsimPlansWriter.getSettings().removeAllModeCollapseRules();
    matsimPlansWriter.write(discreteDemands);

    var plansStats = matsimPlansWriter.getWriterStats();
    assertEquals(3, plansStats.getLegsWritten());
    assertEquals(2, plansStats.getLegsWritten("pt"));
    assertEquals(1, plansStats.getLegsWritten("walk"));
    /* home, shopping, work and home again */
    assertEquals(4, plansStats.getActivitiesWritten());
    assertEquals(0, plansStats.getActivitiesFollowingAnActivity());
    assertEquals(0, plansStats.getTripsCollapsedByModeChainRules());
  }

  /**
   * A walk to the station, the train and a walk on from the station form a single train journey, which the default
   * collapse rules write as one pt leg. The two trips merged into it are counted
   * <pre>
   *   HOME (zone0) --walk--> (zone3) --train--> (zone4) --walk--> WORK (zone2) --walk--> HOME (zone0)
   *        08:00                 08:10               08:40                16:30
   * </pre>
   *
   * @param outputDir to write the plans to
   */
  @Test
  public void testTripsCollapsedByModeChainRulesCounted(@TempDir Path outputDir) {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var tour = demandsAndTour.second();
    var walkMode = network.getModes().get(PredefinedModeType.PEDESTRIAN);

    boolean addToSchedule = true;
    var toStation = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.OUTBOUND, addToSchedule);
    toStation.setDestination(zoning.getOdZones().get(3));
    toStation.setMode(walkMode);
    toStation.syncStartTimeToTourStartTime();
    var byTrain = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.OUTBOUND, addToSchedule);
    byTrain.setOrigin(zoning.getOdZones().get(3));
    byTrain.setDestination(zoning.getOdZones().get(4));
    byTrain.setMode(network.getModes().get(PredefinedModeType.TRAIN));
    byTrain.setStartTime(LocalTime.of(8, 10));
    var fromStation = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.OUTBOUND, addToSchedule);
    fromStation.setOrigin(zoning.getOdZones().get(4));
    fromStation.setMode(walkMode);
    fromStation.setStartTime(LocalTime.of(8, 40));
    var toHome = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.INBOUND, addToSchedule);
    toHome.setMode(walkMode);
    toHome.syncStartTimeToTourEndWithNegativeOffset(Duration.of(30, ChronoUnit.MINUTES));

    var matsimPlansWriter = createPlansWriter(outputDir, network, zoning);
    matsimPlansWriter.getSettings().setUseDisaggregateTransitModes(false);
    matsimPlansWriter.write(discreteDemands);

    var plansStats = matsimPlansWriter.getWriterStats();
    assertEquals(2, plansStats.getTripsCollapsedByModeChainRules());
    assertEquals(2, plansStats.getLegsWritten());
    assertEquals(1, plansStats.getLegsWritten("pt"));
    /* home, work and home again */
    assertEquals(3, plansStats.getActivitiesWritten());
  }

  /**
   * Every person gets the default subpopulation, also a person without an external id, who has no other attributes
   * <pre>
   *   HOME (zone0) --walk--> WORK (zone2) --walk--> HOME (zone0)
   *        08:00                  16:30
   * </pre>
   *
   * @param outputDir to write the plans to
   * @throws IOException thrown if the written plans cannot be read
   */
  @Test
  public void testPersonGetsDefaultSubpopulation(@TempDir Path outputDir) throws IOException {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var tour = demandsAndTour.second();
    var walkMode = network.getModes().get(PredefinedModeType.PEDESTRIAN);
    registerTripsToWorkAndHome(discreteDemands, tour, walkMode, walkMode);

    createPlansWriter(outputDir, network, zoning).write(discreteDemands);

    var plansFile = outputDir.resolve(MatsimDiscreteDemandsWriterSettings.DEFAULT_PLANS_FILE_NAME + ".xml");
    assertTrue(Files.readString(plansFile).contains(String.format(
        "<attribute name=\"%s\" class=\"java.lang.String\">%s</attribute>", MatsimPlansAttributes.SUBPOPULATION,
        MatsimDiscreteDemandsWriterSettings.DEFAULT_PERSON_SUBPOPULATION)));
  }

  /**
   * A person who does not drive their own tour is carried on its car trip, written with MATSim's ride mode, while
   * their walk trip on it stays a walk
   * <pre>
   *   HOME (zone0) --car, carried--> WORK (zone2) --walk--> HOME (zone0)
   *        08:00                          16:30
   * </pre>
   *
   * @param outputDir to write the plans to
   */
  @Test
  public void testOwnTourNotDrivenIsCarriedOnItsCarTripOnly(@TempDir Path outputDir) {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var tour = demandsAndTour.second();
    registerTripsToWorkAndHome(discreteDemands, tour,
        network.getModes().get(PredefinedModeType.CAR), network.getModes().get(PredefinedModeType.PEDESTRIAN));
    tour.getPrimaryParticipation().setDriver(false);

    var matsimPlansWriter = createPlansWriter(outputDir, network, zoning);
    matsimPlansWriter.write(discreteDemands);

    var plansStats = matsimPlansWriter.getWriterStats();
    assertEquals(2, plansStats.getLegsWritten());
    assertEquals(1, plansStats.getLegsWritten(MatsimBuiltInMode.RIDE.getValue()));
    assertEquals(1, plansStats.getLegsWritten("walk"));
  }

  /**
   * On a joint car tour driven by its accompanying participant, that participant's legs are written by car and the
   * primary participant's as ride
   * <pre>
   *   HOME (zone0) --car--> WORK (zone2) --car--> HOME (zone0)
   *        08:00                 16:30
   *   primary participant carried, accompanying participant driving
   * </pre>
   *
   * @param outputDir to write the plans to
   * @throws Exception thrown if the written plans cannot be read
   */
  @Test
  public void testJointTourDrivenByItsAccompanyingParticipant(@TempDir Path outputDir) throws Exception {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var tour = demandsAndTour.second();
    var carMode = network.getModes().get(PredefinedModeType.CAR);
    registerTripsToWorkAndHome(discreteDemands, tour, carMode, carMode);

    var primary = tour.getPrimaryParticipant();
    var companionParticipation = addCompanion(discreteDemands, tour);
    tour.setDriver(companionParticipation);

    var matsimPlansWriter = createPlansWriter(outputDir, network, zoning);
    matsimPlansWriter.write(discreteDemands);

    var plansFile = outputDir.resolve(MatsimDiscreteDemandsWriterSettings.DEFAULT_PLANS_FILE_NAME + ".xml");
    var personIdMapper = matsimPlansWriter.getPrimaryIdMapper().getPersonClassIdMapper();
    var ride = MatsimBuiltInMode.RIDE.getValue();
    assertEquals(List.of(ride, ride), collectLegModes(plansFile, personIdMapper.apply(primary)));
    assertEquals(List.of("private_car", "private_car"),
        collectLegModes(plansFile, personIdMapper.apply(companionParticipation.getPerson())));
  }

  /**
   * An own car tour without a driver value is driven by its person, the mode being driven by one of the people
   * travelling, and the tour is kept for the collated warning about missing driver values
   * <pre>
   *   HOME (zone0) --car--> WORK (zone2) --car--> HOME (zone0)
   *        08:00                 16:30
   *   no driver value
   * </pre>
   *
   * @param outputDir to write the plans to
   */
  @Test
  public void testOwnCarTourWithoutDriverValueIsDrivenAndKept(@TempDir Path outputDir) {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var tour = demandsAndTour.second();
    var carMode = network.getModes().get(PredefinedModeType.CAR);
    registerTripsToWorkAndHome(discreteDemands, tour, carMode, carMode);

    var matsimPlansWriter = createPlansWriter(outputDir, network, zoning);
    matsimPlansWriter.write(discreteDemands);

    assertEquals(2, matsimPlansWriter.getWriterStats().getLegsWritten("private_car"));
    assertEquals(Set.of(tour), matsimPlansWriter.getSelfDrivenToursWithoutDriverValue());
  }

  /**
   * On a joint car tour without driver values its primary participant drives, so the tour keeps one car, and the
   * other is carried; the tour is kept for the collated warning about missing driver values
   * <pre>
   *   HOME (zone0) --car--> WORK (zone2) --car--> HOME (zone0)
   *        08:00                 16:30
   *   primary participant and accompanying participant, no driver values
   * </pre>
   *
   * @param outputDir to write the plans to
   * @throws Exception thrown if the written plans cannot be read
   */
  @Test
  public void testJointCarTourWithoutDriverValuesIsDrivenByItsPrimaryAndKept(@TempDir Path outputDir)
      throws Exception {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var tour = demandsAndTour.second();
    var carMode = network.getModes().get(PredefinedModeType.CAR);
    registerTripsToWorkAndHome(discreteDemands, tour, carMode, carMode);
    var companionParticipation = addCompanion(discreteDemands, tour);

    var matsimPlansWriter = createPlansWriter(outputDir, network, zoning);
    matsimPlansWriter.write(discreteDemands);

    var plansFile = outputDir.resolve(MatsimDiscreteDemandsWriterSettings.DEFAULT_PLANS_FILE_NAME + ".xml");
    var personIdMapper = matsimPlansWriter.getPrimaryIdMapper().getPersonClassIdMapper();
    var ride = MatsimBuiltInMode.RIDE.getValue();
    assertEquals(List.of("private_car", "private_car"),
        collectLegModes(plansFile, personIdMapper.apply(tour.getPrimaryParticipant())));
    assertEquals(List.of(ride, ride),
        collectLegModes(plansFile, personIdMapper.apply(companionParticipation.getPerson())));
    assertEquals(Set.of(tour), matsimPlansWriter.getSelfDrivenToursWithoutDriverValue());
  }

  /**
   * A walk tour without a driver value has nothing anyone drives, so it is not kept for the warning
   *
   * @param outputDir to write the plans to
   */
  @Test
  public void testWalkTourWithoutDriverValueIsNotKept(@TempDir Path outputDir) {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var walkMode = network.getModes().get(PredefinedModeType.PEDESTRIAN);
    registerTripsToWorkAndHome(discreteDemands, demandsAndTour.second(), walkMode, walkMode);

    var matsimPlansWriter = createPlansWriter(outputDir, network, zoning);
    matsimPlansWriter.write(discreteDemands);

    assertTrue(matsimPlansWriter.getSelfDrivenToursWithoutDriverValue().isEmpty());
  }

  /**
   * A person carried to work who drives on their at-work sub tour is written driving it, as the demand says, and the
   * sub tour is kept for the collated warning, since they would not have the car with them at work
   * <pre>
   *   HOME (zone0) --car, carried--> WORK (zone2) --car, driving--> LUNCH (zone3) --car, driving--> WORK (zone2)
   *        08:00                          12:00                           12:50
   *   WORK (zone2) --car, carried--> HOME (zone0)
   *        16:30
   * </pre>
   *
   * @param outputDir to write the plans to
   */
  @Test
  public void testSubTourDrivenWhileCarriedOnItsParentTourIsKept(@TempDir Path outputDir) {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var workTour = demandsAndTour.second();
    var carMode = network.getModes().get(PredefinedModeType.CAR);

    boolean addToSchedule = true;
    var toWork =
        discreteDemands.getTrips().getFactory().registerNew(workTour, DirectionBound.OUTBOUND, addToSchedule);
    toWork.setMode(carMode);
    toWork.syncStartTimeToTourStartTime();
    var lunchTour = discreteDemands.getTours().getFactory().registerNew(workTour, workTour.getDestination(),
        zoning.getOdZones().get(3), LocalTime.of(12, 0), LocalTime.of(13, 0), addToSchedule);
    lunchTour.setPurpose(PURPOSE_SHOPPING);
    var toLunch =
        discreteDemands.getTrips().getFactory().registerNew(lunchTour, DirectionBound.OUTBOUND, addToSchedule);
    toLunch.setMode(carMode);
    toLunch.syncStartTimeToTourStartTime();
    var backToWork =
        discreteDemands.getTrips().getFactory().registerNew(lunchTour, DirectionBound.INBOUND, addToSchedule);
    backToWork.setMode(carMode);
    backToWork.syncStartTimeToTourEndWithNegativeOffset(Duration.of(10, ChronoUnit.MINUTES));
    var toHome = discreteDemands.getTrips().getFactory().registerNew(workTour, DirectionBound.INBOUND, addToSchedule);
    toHome.setMode(carMode);
    toHome.syncStartTimeToTourEndWithNegativeOffset(Duration.of(30, ChronoUnit.MINUTES));
    workTour.getPrimaryParticipation().setDriver(false);
    lunchTour.getPrimaryParticipation().setDriver(true);

    var matsimPlansWriter = createPlansWriter(outputDir, network, zoning);
    matsimPlansWriter.write(discreteDemands);

    var plansStats = matsimPlansWriter.getWriterStats();
    assertEquals(2, plansStats.getLegsWritten(MatsimBuiltInMode.RIDE.getValue()));
    assertEquals(2, plansStats.getLegsWritten("private_car"));
    assertEquals(Set.of(lunchTour), matsimPlansWriter.getSubToursDrivenWhileCarriedOnParentTour());
  }

  /**
   * A person's locations do not depend on the other persons written. Two commuters by car from households of their own
   * in zone0 to work in zone1, the first written first, are written twice, the second time with the first person's
   * day cleared. Every location of the second person is on a road and the same both times
   * <pre>
   *   HOME (zone0) --car--> WORK (zone1) --car--> HOME (zone0)
   *        08:00                 16:30
   *   both persons alike, the first person's day cleared in the second write
   * </pre>
   *
   * @param outputDir to write the plans to
   * @throws Exception thrown if the written plans cannot be read
   */
  @Test
  public void testLocationsDoNotDependOnOtherPersons(@TempDir Path outputDir) throws Exception {
    var networkAndZoning = createNetworkAndZoningWithRoads();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    var carMode = network.getModes().get(PredefinedModeType.CAR);
    var homeZone = zoning.getOdZones().get(0);
    var workZone = zoning.getOdZones().get(1);

    var discreteDemands = new DiscreteDemands(network.getIdGroupingToken());
    discreteDemands.getTimePeriods().getFactory().registerNew("all day", 0, (24 * 3600)-1);
    var firstTour = registerPersonWithWorkTour(discreteDemands, homeZone, workZone);
    registerTripsToWorkAndHome(discreteDemands, firstTour, carMode, carMode);
    var secondTour = registerPersonWithWorkTour(discreteDemands, homeZone, workZone);
    registerTripsToWorkAndHome(discreteDemands, secondTour, carMode, carMode);

    var bothDir = Files.createDirectories(outputDir.resolve("both"));
    var bothWriter = createPlansWriter(bothDir, network, zoning);
    bothWriter.write(discreteDemands);
    discreteDemands.getDiscreteDemandsModifier().clearPersonSchedule(firstTour.getPrimaryParticipant());
    var secondOnlyDir = Files.createDirectories(outputDir.resolve("second_only"));
    createPlansWriter(secondOnlyDir, network, zoning).write(discreteDemands);

    var plansFileName = MatsimDiscreteDemandsWriterSettings.DEFAULT_PLANS_FILE_NAME + ".xml";
    var secondId = bothWriter.getPrimaryIdMapper().getPersonClassIdMapper().apply(secondTour.getPrimaryParticipant());
    var locationsWithBoth = collectActivityLocations(bothDir.resolve(plansFileName), secondId);
    assertEquals(3, locationsWithBoth.size());
    assertTrue(locationsWithBoth.stream().allMatch(location -> location.endsWith(",400.0")));
    assertEquals(locationsWithBoth, collectActivityLocations(secondOnlyDir.resolve(plansFileName), secondId));
  }

  /**
   * A network holding a custom mode is refused, as the writer collapses trips into mode chains by their predefined
   * mode type, which every custom mode shares
   *
   * @param outputDir to write the plans to
   */
  @Test
  public void testNetworkWithCustomModeIsRefused(@TempDir Path outputDir) {
    var networkAndZoning = createNetworkAndZoning();
    var network = networkAndZoning.first();
    var zoning = networkAndZoning.second();
    network.getModes().getFactory().registerNewCustomMode("van", 100, 1.5, null, null);
    var demandsAndTour = createWorkTourDemands(network, zoning);
    var discreteDemands = demandsAndTour.first();
    var tour = demandsAndTour.second();
    var walkMode = network.getModes().get(PredefinedModeType.PEDESTRIAN);
    registerTripsToWorkAndHome(discreteDemands, tour, walkMode, walkMode);

    var matsimPlansWriter = createPlansWriter(outputDir, network, zoning);
    assertThrows(PlanItRunTimeException.class, () -> matsimPlansWriter.write(discreteDemands));
  }
}
