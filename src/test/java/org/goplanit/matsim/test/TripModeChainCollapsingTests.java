package org.goplanit.matsim.test;

import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.demands.discrete.tour.ActivitySchedule;
import org.goplanit.demands.discrete.trip.Trip;
import org.goplanit.demands.discrete.util.DirectionBound;
import org.goplanit.logging.Logging;
import org.goplanit.matsim.util.AggregateParticipantTourView;
import org.goplanit.matsim.util.AggregateTourView;
import org.goplanit.matsim.util.AggregateTripView;
import org.goplanit.matsim.util.ModeChainCollapseRule;
import org.goplanit.matsim.util.ScheduleCollapsingUtils;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedMode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.zoning.Zoning;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test if the mode chain collapsing logic holds
 */
public class TripModeChainCollapsingTests {

  /** The logger instance. */
  private static Logger LOGGER = null;

  /** Predefined test purpose string tokens. */
  private static final String PURPOSE_WORK = "work";

  /** The modes of the network the tests build */
  private static final PredefinedModeType[] MODE_TYPES = {
      PredefinedModeType.CAR, PredefinedModeType.BUS, PredefinedModeType.TRAIN, PredefinedModeType.PEDESTRIAN};

  /**
   * The collapse rules of the tests, each a main mode with its from and to connector modes.
   */
  private static final List<ModeChainCollapseRule> TRANSIT_RULES = List.of(
      new ModeChainCollapseRule(
          PredefinedModeType.BUS,
          List.of(PredefinedModeType.PEDESTRIAN),
          List.of(PredefinedModeType.PEDESTRIAN)
      ),
      new ModeChainCollapseRule(
          PredefinedModeType.TRAIN,
          List.of(PredefinedModeType.PEDESTRIAN),
          List.of(PredefinedModeType.PEDESTRIAN)
      )
  );

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logger.getLogger(TripModeChainCollapsingTests.class.getCanonicalName());
    }
  }

  /**
   * Create the logger once for all tests of the class
   *
   * @throws Exception when the log file cannot be opened
   */
  @BeforeAll
  public static void setUpLogger() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(TripModeChainCollapsingTests.class);
    }
  }

  /** Close the logger after all tests of the class */
  @AfterAll
  public static void tearDownLogger() {
    Logging.closeLogger(LOGGER);
  }

  @AfterEach
  public void afterTest() {
    IdGenerator.reset();
    System.gc();
  }

  /**
   * Create the zones the tests use, on a network with the {@link #MODE_TYPES} and one layer
   *
   * @param network the active macroscopic network model the demands use
   * @param zoning the active zoning container linked to the graph
   * @return a validated, initialized DiscreteDemands database reference
   */
  private DiscreteDemands createBaseMemoryDemandModel(MacroscopicNetwork network, Zoning zoning) {
    // Allocate 5 distinct spatial zones to trace multi-stop handovers
    zoning.getOdZones().getFactory().registerNew();
    zoning.getOdZones().getFactory().registerNew();
    zoning.getOdZones().getFactory().registerNew();
    zoning.getOdZones().getFactory().registerNew();
    zoning.getOdZones().getFactory().registerNew();

    var discreteDemands = new DiscreteDemands(network.getIdGroupingToken());
    discreteDemands.getTimePeriods().getFactory().registerNew(
        "all day", 0, (24 * 3600) - 1);

    var household0 = discreteDemands.getHouseholds().getFactory().registerNew();
    household0.setZone(zoning.getOdZones().get(0));
    var person0 = discreteDemands.getPersons().getFactory().registerNew(household0);
    person0.setInitialPurpose("home");

    return discreteDemands;
  }


  /**
   * A chain matching a rule exactly, walk -> bus -> walk, collapses into a single AggregateTripView inside the
   * AggregateTourView.
   */
  @Test
  public void testValidStandardChainCollapsesCorrectly() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1),
        LocalTime.of(8, 0), LocalTime.of(17, 30), true);
    tour.setPurpose(PURPOSE_WORK);

    // Build chronological sequence: walk (access) -> bus (main) -> walk (egress)
    var trip1 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip1.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip1.setOrigin(zoning.getOdZones().get(0));
    trip1.setDestination(zoning.getOdZones().get(2)); // bus stop A
    trip1.setStartTime(LocalTime.of(8, 0));

    var trip2 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip2.setMode(network.getModes().get(PredefinedModeType.BUS));
    trip2.setOrigin(zoning.getOdZones().get(2));
    trip2.setDestination(zoning.getOdZones().get(3)); // bus stop B
    trip2.setStartTime(LocalTime.of(8, 15));

    var trip3 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip3.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip3.setOrigin(zoning.getOdZones().get(3));
    trip3.setDestination(zoning.getOdZones().get(1)); // work destination
    trip3.setStartTime(LocalTime.of(8, 45));

    assertTrue(ScheduleCollapsingUtils.requiresScheduleCollapsing(person.getSchedule(), TRANSIT_RULES),
        "Pre-screen aggregator should confirm multi-trip chain presence via unrolled depth evaluation.");

    var collapsed = ScheduleCollapsingUtils.collapseContiguousTripChainsByModeRules(
        person.getSchedule(), TRANSIT_RULES);

    // Verify top-level structure container details
    assertEquals(1, collapsed.size(),
        "Top level list must contain exactly 1 tour element wrapper.");
    assertTrue(collapsed.get(0) instanceof AggregateParticipantTourView,
        "Top level component must wrap inside an AggregateParticipantTourView.");

    var tourView = (AggregateParticipantTourView) collapsed.get(0);
    var innerSchedule = tourView.getSchedule();

    // Verify inner leaf metrics utilizing sizeUnrolled(true)
    assertEquals(1, innerSchedule.sizeUnrolled(true),
        "The 3 legs should collapse down into exactly 1 aggregate leaf trip view.");
    assertTrue(innerSchedule.get(0) instanceof AggregateTripView,
        "The resulting inner element must be an AggregateTripView.");

    var view = (AggregateTripView) innerSchedule.get(0);
    assertEquals(PredefinedModeType.BUS, view.getMode().getPredefinedModeType(),
        "Dominant mode must be BUS.");
    assertEquals(LocalTime.of(8, 0), view.getStartTime(),
        "Start time must draw from the initial walk leg.");
    assertEquals(zoning.getOdZones().get(0), view.getOrigin(false),
        "Origin must stitch to initial walk origin.");
    assertEquals(zoning.getOdZones().get(1), view.getDestination(false),
        "Destination must stitch to final walk destination.");
  }

  /**
   * Trips matching the mode pattern but switching direction, such as an outbound walk followed by an inbound bus, are
   * not collapsed together.
   */
  @Test
  public void testEdgeCaseDirectionSwitchPreventsCollapse() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1),
        LocalTime.of(8, 0), LocalTime.of(17, 30), true);

    var trip1 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip1.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip1.setStartTime(LocalTime.of(8, 0));

    // Direction flip mid-sequence to violate matching
    var trip2 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.INBOUND, true);
    trip2.setMode(network.getModes().get(PredefinedModeType.BUS));
    trip2.setStartTime(LocalTime.of(8, 15));

    var trip3 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip3.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip3.setStartTime(LocalTime.of(8, 45));

    assertFalse(ScheduleCollapsingUtils.requiresScheduleCollapsing(person.getSchedule(), TRANSIT_RULES),
        "Pre-screen should return false because directional flows do not match the rule criteria.");

    var collapsed = ScheduleCollapsingUtils.collapseContiguousTripChainsByModeRules(
        person.getSchedule(), TRANSIT_RULES);

    // Because requiresScheduleCollapsing returns false, the fast-path immediately returns person.getSchedule()
    assertEquals(1, collapsed.size(), "Top level should contain exactly 1 tour element.");

    var tourElement = ((org.goplanit.demands.discrete.tour.ParticipantTour) collapsed.get(0)).getTour();
    var innerSchedule = tourElement.getSchedule();

    // Verify that none of the 3 internal trips collapsed
    assertEquals(3, innerSchedule.sizeUnrolled(true),
        "The 3 internal trips must remain completely un-collapsed as raw standalone elements.");

    assertEquals(PredefinedModeType.PEDESTRIAN, ((Trip) innerSchedule.get(0)).getMode().getPredefinedModeType());
    assertEquals(PredefinedModeType.BUS, ((Trip) innerSchedule.get(1)).getMode().getPredefinedModeType());
    assertEquals(PredefinedModeType.PEDESTRIAN, ((Trip) innerSchedule.get(2)).getMode().getPredefinedModeType());
  }


  /**
   * A chain only partly matching a three trip rule, such as walk -> train without the walk after it, does not match
   * the {@link #TRANSIT_RULES} and is not collapsed.
   */
  @Test
  public void testEdgeCasePartialSequenceMatchesShortRule() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1),
        LocalTime.of(8, 0), LocalTime.of(17, 30), true);

    // Sequence construction: walk -> train (length 2, fails 3-stage rule requirements)
    var trip1 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip1.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip1.setStartTime(LocalTime.of(8, 0));

    var trip2 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip2.setMode(network.getModes().get(PredefinedModeType.TRAIN));
    trip2.setStartTime(LocalTime.of(8, 15));

    assertFalse(ScheduleCollapsingUtils.requiresScheduleCollapsing(person.getSchedule(), TRANSIT_RULES),
        "Pre-screen should return false because the sequence length is too short to" +
            " fulfill any 3-stage configuration rules.");

    var collapsed = ScheduleCollapsingUtils.collapseContiguousTripChainsByModeRules(
        person.getSchedule(), TRANSIT_RULES);

    // Verify top-level structure preserves the original reference wrapper
    assertEquals(1, collapsed.size(),
        "Top level should contain exactly 1 tour element.");
    assertFalse(collapsed.get(0) instanceof AggregateParticipantTourView,
        "Top level component must not be an AggregateParticipantTourView wrapper.");

    var tourElement =
        ((org.goplanit.demands.discrete.tour.ParticipantTour) collapsed.get(0)).getTour();
    var innerSchedule = tourElement.getSchedule();

    // Verify that both trip elements passed through natively as un-collapsed leaves
    assertEquals(2, innerSchedule.sizeUnrolled(true),
        "The 2 trips must remain completely un-collapsed inside the native tour timeline.");

    assertEquals(PredefinedModeType.PEDESTRIAN, ((Trip) innerSchedule.get(0)).getMode().getPredefinedModeType());
    assertEquals(PredefinedModeType.TRAIN, ((Trip) innerSchedule.get(1)).getMode().getPredefinedModeType());
  }

  /**
   * AggregateTripView and AggregateTourView are read-only: changing them throws an exception.
   */
  @Test
  public void testInvalidContextMutationThrowsException() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1), LocalTime.of(8, 0), LocalTime.of(17, 30), true);

    var trip = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.OUTBOUND, true);
    trip.setMode(network.getModes().get(PredefinedModeType.CAR));

    // Verify Mutator Safety Barrier on AggregateTripView
    var tripView = new AggregateTripView(List.of(trip), trip);
    assertThrows(UnsupportedOperationException.class, () -> tripView.setPurpose("gym"),
        "Mutations on the aggregate trip view layer must throw UnsupportedOperationException.");
    assertThrows(UnsupportedOperationException.class, () -> tripView.setOrigin(zoning.getOdZones().get(4)),
        "Mutations on the aggregate trip view layer must throw UnsupportedOperationException.");

    // Verify Mutator Safety Barrier on AggregateTourView
    var mockSchedule = new org.goplanit.demands.discrete.tour.ActivitySchedule();
    var tourView = new AggregateTourView(tour, mockSchedule);
    assertThrows(UnsupportedOperationException.class, () -> tourView.setPurpose("leisure"),
        "Mutations on the aggregate tour view layer must throw UnsupportedOperationException.");
    assertThrows(UnsupportedOperationException.class, () -> tourView.addParticipant(null, null),
        "Mutations on the aggregate tour view layer must throw UnsupportedOperationException.");
  }


  /**
   * Invalid rule arguments, and an empty trip list given to a view, throw an exception.
   */
  @Test
  public void testInvalidContextMalformedConstructorAssertions() {
    // Verify rule definition validation boundaries
    assertThrows(NullPointerException.class, () -> new ModeChainCollapseRule(null, List.of(), List.of()),
        "Null main mode should fail instantly with a NullPointerException.");
    assertThrows(NullPointerException.class, () -> new ModeChainCollapseRule(
        PredefinedModeType.BUS, null, List.of()),
        "Null from-connectors should fail instantly with a NullPointerException.");

    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1),
        LocalTime.of(8, 0), LocalTime.of(17, 30), true);
    var trip = discreteDemands.getTrips().getFactory().registerNew(tour, DirectionBound.OUTBOUND, true);
    trip.setMode(network.getModes().get(PredefinedModeType.CAR));

    // Verify aggregate view constructor validation boundaries
    assertThrows(IllegalArgumentException.class, () -> new AggregateTripView(List.of(), trip),
        "Empty lists must fail initialization requirements with an IllegalArgumentException.");
  }

  /**
   * Overlapping chains, walk -> taxi -> walk -> bus, give exactly two aggregates, TAXI and BUS, both holding the shared
   * walk.
   */
  @Test
  public void testEdgeCaseStrictThreeStageOverlapPreservesRemainingBus() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1),
        LocalTime.of(8, 0), LocalTime.of(17, 30), true);

    var taxiMode = network.getModes().getFactory().registerNew(PredefinedModeType.TAXI);

    // Strictly define ONLY the primary 3-stage rules with no 2-stage cleaners
    List<ModeChainCollapseRule> strictRules = List.of(
        new ModeChainCollapseRule(
            PredefinedModeType.BUS,
            List.of(PredefinedModeType.PEDESTRIAN),
            List.of(PredefinedModeType.PEDESTRIAN)),
        new ModeChainCollapseRule(
            PredefinedModeType.TAXI,
            List.of(PredefinedModeType.PEDESTRIAN),
            List.of(PredefinedModeType.PEDESTRIAN))
    );

    // Sequence construction: walk1 -> taxi -> walk2 -> bus
    var trip1 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip1.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip1.setStartTime(LocalTime.of(8, 0));

    var trip2 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip2.setMode(taxiMode);
    trip2.setStartTime(LocalTime.of(8, 10));

    var trip3 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip3.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip3.setStartTime(LocalTime.of(8, 25));

    var trip4 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip4.setMode(network.getModes().get(PredefinedModeType.BUS));
    trip4.setStartTime(LocalTime.of(8, 40));

    assertTrue(ScheduleCollapsingUtils.requiresScheduleCollapsing(person.getSchedule(), strictRules),
        "Pre-screen should confirm collapsing is needed for the initial taxi block.");

    // Execute the collapsing utility over the schedule container
    var collapsed = ScheduleCollapsingUtils.collapseContiguousTripChainsByModeRules(
        person.getSchedule(), strictRules);

    // Verification Assertions
    assertEquals(1, collapsed.size(), "The schedule contains 1 main tour.");
    var processedTour = ((org.goplanit.demands.discrete.tour.ParticipantTour) collapsed.get(0)).getTour();
    assertTrue(processedTour instanceof AggregateTourView,
        "The tour must be wrapped inside an AggregateTourView.");

    var tourSchedule = processedTour.getSchedule();

    // Verify unrolled leaf elements equal exactly 2 (TAXI View + Standalone BUS Trip)
    assertEquals(2, tourSchedule.sizeUnrolled(true),
        "Should produce exactly 2 leaf elements inside the tour schedule.");

    // First element must be the collapsed TAXI view
    assertTrue(tourSchedule.get(0) instanceof AggregateTripView, "First element must be an AggregateTripView.");
    var taxiView = (AggregateTripView) tourSchedule.get(0);
    assertEquals(PredefinedModeType.TAXI, taxiView.getMode().getPredefinedModeType());

    // Second element is the un-collapsed bus leg passed through natively
    assertTrue(tourSchedule.get(1) instanceof Trip, "Second element must remain a native standalone Trip.");
    var remainingBus = (Trip) tourSchedule.get(1);
    assertEquals(PredefinedModeType.BUS, remainingBus.getMode().getPredefinedModeType());
  }

  /**
   * A chain with transfers, walk1 -> bus1 -> walk2 -> train -> walk3 -> bus2 -> walk4, collapses into exactly three
   * AggregateTripViews, BUS -> TRAIN -> BUS, with three trip rules only: a walk between two transit trips is in both
   * their aggregates.
   */
  @Test
  public void testComplexMultiTransferChainWithBackwardPeeking() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1),
        LocalTime.of(8, 0), LocalTime.of(17, 30), true);

    // Sequence construction: walk1 -> bus1 -> walk2 -> train -> walk3 -> bus2 -> walk4
    var trip1 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip1.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip1.setStartTime(LocalTime.of(8, 0));

    var trip2 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip2.setMode(network.getModes().get(PredefinedModeType.BUS));
    trip2.setStartTime(LocalTime.of(8, 10));

    var trip3 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip3.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip3.setStartTime(LocalTime.of(8, 25));

    var trip4 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip4.setMode(network.getModes().get(PredefinedModeType.TRAIN));
    trip4.setStartTime(LocalTime.of(8, 40));

    var trip5 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip5.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip5.setStartTime(LocalTime.of(9, 10));

    var trip6 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip6.setMode(network.getModes().get(PredefinedModeType.BUS));
    trip6.setStartTime(LocalTime.of(9, 25));

    var trip7 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip7.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip7.setStartTime(LocalTime.of(9, 45));

    assertTrue(ScheduleCollapsingUtils.requiresScheduleCollapsing(person.getSchedule(), TRANSIT_RULES),
        "Pre-screen aggregator must confirm collapsing requirements for this complex multi-stage layout.");

    // Execute the collapsing utility over the schedule container
    var collapsed = ScheduleCollapsingUtils.collapseContiguousTripChainsByModeRules(
        person.getSchedule(), TRANSIT_RULES);

    // Verification Assertions
    assertEquals(1, collapsed.size(), "The schedule contains 1 main tour.");
    var processedTour = ((org.goplanit.demands.discrete.tour.ParticipantTour) collapsed.get(0)).getTour();
    assertTrue(processedTour instanceof AggregateTourView,
        "The tour must be wrapped inside an AggregateTourView.");

    var tourSchedule = processedTour.getSchedule();

    // Verify unrolled leaf elements equal exactly 3 (BUS view -> TRAIN view -> BUS view)
    assertEquals(3, tourSchedule.sizeUnrolled(true),
        "The 7 original trips must condense into exactly 3 unrolled leaf elements.");

    // First element: walk1 -> bus1 -> walk2 collapsed to BUS view via standard match
    assertTrue(tourSchedule.get(0) instanceof AggregateTripView,
        "First element must be an AggregateTripView.");
    var busView1 = (AggregateTripView) tourSchedule.get(0);
    assertEquals(PredefinedModeType.BUS, busView1.getMode().getPredefinedModeType());
    assertEquals(3, busView1.getCollapsedTrips().size());

    // Second element: train collapsed to TRAIN view by peeking backwards to borrow walk2 and looking ahead to walk3
    assertTrue(tourSchedule.get(1) instanceof AggregateTripView,
        "Second element must be an AggregateTripView.");
    var trainView = (AggregateTripView) tourSchedule.get(1);
    assertEquals(PredefinedModeType.TRAIN, trainView.getMode().getPredefinedModeType());
    assertEquals(3, trainView.getCollapsedTrips().size());
    assertEquals(trip3, trainView.getCollapsedTrips().get(0),
        "The TRAIN view must successfully borrow trip3 (walk2) from the previous history entry.");

    // Third element: walk3 -> bus2 -> walk4 collapsed to BUS view via peeking backwards to borrow walk3
    assertTrue(tourSchedule.get(2) instanceof AggregateTripView,
        "Third element must be an AggregateTripView.");
    var busView2 = (AggregateTripView) tourSchedule.get(2);
    assertEquals(PredefinedModeType.BUS, busView2.getMode().getPredefinedModeType());
    assertEquals(3, busView2.getCollapsedTrips().size());
    assertEquals(trip5, busView2.getCollapsedTrips().get(0),
        "The final BUS view must successfully borrow trip5 (walk3) from the preceding TRAIN entry.");
  }

  /**
   * AggregateTourView is read-only: changing it throws an exception.
   */
  @Test
  public void testInvalidContextTourMutationThrowsException() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1),
        LocalTime.of(8, 0), LocalTime.of(17, 30), true);

    var mockSchedule = new org.goplanit.demands.discrete.tour.ActivitySchedule();
    var view = new AggregateTourView(tour, mockSchedule);

    // Verify all core mutator boundaries block edits cleanly
    assertThrows(UnsupportedOperationException.class, () -> view.setPurpose("leisure"),
        "Mutations on the aggregate tour view layer must throw UnsupportedOperationException.");
    assertThrows(UnsupportedOperationException.class, () -> view.addParticipant(null, null),
        "Mutations on the aggregate tour view layer must throw UnsupportedOperationException.");
    assertThrows(UnsupportedOperationException.class, () -> view.setOrigin(zoning.getOdZones().get(3)),
        "Mutations on the aggregate tour view layer must throw UnsupportedOperationException.");
  }

  /**
   * A rule with allowedFromMissingIfToPresent collapses a transit chain without an access connector, provided the
   * egress connector is present.
   */
  @Test
  public void testAllowedFromMissingIfToPresentCollapseRule() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1),
        LocalTime.of(8, 0), LocalTime.of(17, 30), true);

    // Sequence construction: bus1 -> walk2 (Missing preceding access walk connector)
    var trip1 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip1.setMode(network.getModes().get(PredefinedModeType.BUS));
    trip1.setStartTime(LocalTime.of(8, 10));

    var trip2 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip2.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip2.setStartTime(LocalTime.of(8, 25));

    // Define a rule where BUS requires a preceding walk and succeeding walk,
    // but the preceding walk is allowed to be missing since the succeeding walk is present.
    var conditionalRule = new ModeChainCollapseRule(
        PredefinedModeType.BUS,
        List.of(PredefinedModeType.PEDESTRIAN),
        List.of(PredefinedModeType.PEDESTRIAN),
        true,  // allowedFromMissingIfToPresent
        false  // allowedToMissingIfFromPresent
    );
    List<ModeChainCollapseRule> rules = List.of(conditionalRule);

    assertTrue(ScheduleCollapsingUtils.requiresScheduleCollapsing(person.getSchedule(), rules),
        "Pre-screen aggregator must confirm collapsing requirements when missing access is permitted by rule.");

    // Execute the collapsing utility over the schedule container
    var collapsed = ScheduleCollapsingUtils.collapseContiguousTripChainsByModeRules(
        person.getSchedule(), rules);

    // Verification Assertions
    assertEquals(1, collapsed.size(), "The schedule contains 1 main tour.");
    var processedTour = ((org.goplanit.demands.discrete.tour.ParticipantTour) collapsed.get(0)).getTour();
    assertTrue(processedTour instanceof AggregateTourView,
        "The tour must be wrapped inside an AggregateTourView.");

    var tourSchedule = processedTour.getSchedule();

    // Verify unrolled leaf elements condense into 1 AggregateTripView containing the main bus and the egress walk
    assertEquals(1, tourSchedule.sizeUnrolled(true),
        "The 2 trips must condense into exactly 1 unrolled leaf element.");

    assertTrue(tourSchedule.get(0) instanceof AggregateTripView,
        "First element must be an AggregateTripView.");
    var busView = (AggregateTripView) tourSchedule.get(0);
    assertEquals(PredefinedModeType.BUS, busView.getMode().getPredefinedModeType());
    assertEquals(2, busView.getCollapsedTrips().size(),
        "The collapsed view must contain the main bus trip and the egress walk connector.");
    assertEquals(trip1, busView.getCollapsedTrips().get(0));
    assertEquals(trip2, busView.getCollapsedTrips().get(1));
  }

  /**
   * A rule with allowedToMissingIfFromPresent collapses a transit chain without an egress connector, provided the
   * access connector is present.
   */
  @Test
  public void testAllowedToMissingIfFromPresentCollapseRule() {
    var network = NetworkLayerTestUtils.createCartesianNetworkWithOneLayer(MODE_TYPES);
    var zoning = new Zoning(network.getIdGroupingToken(), network.getNetworkGroupingTokenId());
    var discreteDemands = createBaseMemoryDemandModel(network, zoning);

    var person = discreteDemands.getPersons().get(0);
    var tour = discreteDemands.getTours().getFactory().registerNew(
        person, zoning.getOdZones().get(0), zoning.getOdZones().get(1),
        LocalTime.of(8, 0), LocalTime.of(17, 30), true);

    // Sequence construction: walk1 -> bus1 (Missing succeeding egress walk connector)
    var trip1 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip1.setMode(network.getModes().get(PredefinedModeType.PEDESTRIAN));
    trip1.setStartTime(LocalTime.of(8, 0));

    var trip2 = discreteDemands.getTrips().getFactory().registerNew(
        tour, DirectionBound.OUTBOUND, true);
    trip2.setMode(network.getModes().get(PredefinedModeType.BUS));
    trip2.setStartTime(LocalTime.of(8, 10));

    // Define a rule where BUS requires a preceding walk and succeeding walk,
    // but the succeeding walk is allowed to be missing since the preceding walk is present.
    var conditionalRule = new ModeChainCollapseRule(
        PredefinedModeType.BUS,
        List.of(PredefinedModeType.PEDESTRIAN),
        List.of(PredefinedModeType.PEDESTRIAN),
        false, // allowedFromMissingIfToPresent
        true   // allowedToMissingIfFromPresent
    );
    List<ModeChainCollapseRule> rules = List.of(conditionalRule);

    assertTrue(ScheduleCollapsingUtils.requiresScheduleCollapsing(person.getSchedule(), rules),
        "Pre-screen aggregator must confirm collapsing requirements when missing egress is permitted by rule.");

    // Execute the collapsing utility over the schedule container
    var collapsed = ScheduleCollapsingUtils.collapseContiguousTripChainsByModeRules(
        person.getSchedule(), rules);

    // Verification Assertions
    assertEquals(1, collapsed.size(), "The schedule contains 1 main tour.");
    var processedTour = ((org.goplanit.demands.discrete.tour.ParticipantTour) collapsed.get(0)).getTour();
    assertTrue(processedTour instanceof AggregateTourView,
        "The tour must be wrapped inside an AggregateTourView.");

    var tourSchedule = processedTour.getSchedule();

    // Verify unrolled leaf elements condense into 1 AggregateTripView containing the access walk and main bus
    assertEquals(1, tourSchedule.sizeUnrolled(true),
        "The 2 trips must condense into exactly 1 unrolled leaf element.");

    assertTrue(tourSchedule.get(0) instanceof AggregateTripView,
        "First element must be an AggregateTripView.");
    var busView = (AggregateTripView) tourSchedule.get(0);
    assertEquals(PredefinedModeType.BUS, busView.getMode().getPredefinedModeType());
    assertEquals(2, busView.getCollapsedTrips().size(),
        "The collapsed view must contain the access walk connector and the main bus trip.");
    assertEquals(trip1, busView.getCollapsedTrips().get(0));
    assertEquals(trip2, busView.getCollapsedTrips().get(1));
  }

}
