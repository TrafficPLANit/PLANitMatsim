package org.goplanit.matsim.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.goplanit.matsim.converter.network.MatsimNetworkWriter;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterFactory;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.layer.macroscopic.AccessGroupPropertiesFactory;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.goplanit.utils.network.layer.macroscopic.intersection.CapacityFactorOrigin;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionControlType;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionSegmentKind;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionSignalSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.goplanit.utils.network.layer.macroscopic.intersection.SpatialIntersection;
import org.goplanit.utils.network.layer.physical.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The capacity of a MATSim link, the rate at which vehicles can leave it, is reduced by the effective capacity factor
 * of its link segment when it is an approach or an internal segment with a signal, and only when the setting is on.
 * Every link segment carries 3,600 pcu/h. A signalised spatial junction over nodes 1 and 2 has the approach from node 0
 * with a signal of its own, the approach from node 4 without one, the plain internal segment from node 1 to node 2,
 * and the exit to node 3
 * <pre>
 *                  3
 *                  ^
 *                  |  exit
 *   0 ---------->  1 ----------> 2
 *      approach    ^  internal
 *                  |  approach without a signal of its own
 *                  4
 * </pre>
 *
 * @author markr
 */
public class MatsimLinkExitCapacityTest {

  /** the network written */
  private MacroscopicNetwork network;

  /** its layer */
  private MacroscopicNetworkLayer layer;

  /** the link segments of the junction */
  private MacroscopicLinkSegment approach, approachWithoutSignal, internal, exit;

  /** the junction over nodes 1 and 2 */
  private SpatialIntersection junction;

  /** register a node at a position with its id as XML id */
  private Node node(double x, double y) {
    var node = layer.getNodes().getFactory().registerNew();
    node.setPosition(PlanitJtsUtils.createPoint(x, y));
    node.setXmlId(String.valueOf(node.getId()));
    return node;
  }

  /** register a link from one node to another with one segment in that direction */
  private MacroscopicLinkSegment linkSegment(Node from, Node to, MacroscopicLinkSegmentType type) {
    var link = layer.getLinks().getFactory().registerNew(from, to, 0.1, true);
    link.setXmlId(String.valueOf(link.getId()));
    link.setGeometry(PlanitJtsUtils.createLineString(
        from.getPosition().getCoordinate(), to.getPosition().getCoordinate()));
    var segment = layer.getLinkSegments().getFactory().registerNew(link, true, true);
    segment.setXmlId(String.valueOf(segment.getId()));
    segment.setLinkSegmentType(type);
    return segment;
  }

  /** write the network, reducing link exit capacity or not, and return the writer with its statistics */
  private MatsimNetworkWriter write(boolean reduce) throws Exception {
    var writer = MatsimNetworkWriterFactory.create(outputDir.toAbsolutePath().toString());
    writer.getSettings().setWriteAsGZip(false);
    writer.getSettings().setReduceLinkExitCapacityByEffectiveCapacityFactor(reduce);
    writer.write(network);
    return writer;
  }

  /** write the network, reducing link exit capacity or not, and collect the capacity written for the link segment */
  private double capacityWrittenFor(MacroscopicLinkSegment segment, boolean reduce) throws Exception {
    write(reduce);

    var written = Files.readString(
        outputDir.resolve(MatsimNetworkWriterSettings.DEFAULT_NETWORK_FILE_NAME + ".xml"));
    var capacity =
        Pattern.compile("<link id=\"" + segment.getXmlId() + "\"[^>]*capacity=\"([^\"]*)\"").matcher(written);
    capacity.find();
    return Double.parseDouble(capacity.group(1));
  }

  /** where the network is written, not private as JUnit fills it in */
  @TempDir
  protected Path outputDir;

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
    network = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken());
    network.setCoordinateReferenceSystem(PlanitJtsCrsUtils.CARTESIANCRS);
    var car = network.getModes().getFactory().registerNew(PredefinedModeType.CAR);
    layer = network.getTransportLayers().getFactory().registerNew(network.getModes());
    var type = layer.getLinkSegmentTypes().getFactory().registerNew("road", 3600, 180);
    type.setXmlId(String.valueOf(type.getId()));
    AccessGroupPropertiesFactory.createOnLinkSegmentType(type, 50, List.of(car));

    var n1 = node(100, 0);
    var n2 = node(200, 0);
    approach = linkSegment(node(0, 0), n1, type);
    approachWithoutSignal = linkSegment(node(100, -100), n1, type);
    internal = linkSegment(n1, n2, type);
    exit = linkSegment(n1, node(100, 100), type);

    junction = layer.getIntersections().getFactory().registerNewSpatial(
        List.of(n1, n2), IntersectionControlType.SIGNALISED, List.of(IntersectionType.JUNCTION));
    junction.addNewApproach(approach, true);
    junction.addNewApproach(approachWithoutSignal, false);
    junction.addNewInternalSegment(internal);
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** An approach of 3,600 pcu/h with green ratio 0.44 is written with 1,584 pcu/h; the internal segment without a
   * signal and the exit keep 3,600 */
  @Test
  public void approachWithSignalIsWrittenWithItsEffectiveCapacity() throws Exception {
    ((IntersectionSignalSegment) junction.getApproach(approach)).setGreenRatio(0.44, CapacityFactorOrigin.SET_DIRECTLY);

    assertEquals(1584, capacityWrittenFor(approach, true), 1e-6);
    assertEquals(3600, capacityWrittenFor(internal, true), 1e-6);
    assertEquals(3600, capacityWrittenFor(exit, true), 1e-6);
  }

  /** An approach without a signal of its own, with a saturation flow factor of 0.5, is written with half its
   * capacity */
  @Test
  public void approachWithoutSignalIsWrittenWithItsSaturationFlowFactor() throws Exception {
    junction.getApproach(approachWithoutSignal).setSaturationFlowFactor(0.5, CapacityFactorOrigin.SET_DIRECTLY);

    assertEquals(1800, capacityWrittenFor(approachWithoutSignal, true), 1e-6);
  }

  /** A right turn of 0.15 on an approach of 0.44 does not change the 1,584 pcu/h written for the approach's link: a
   * MATSim link has one capacity for all the traffic on it */
  @Test
  public void movementValueDoesNotChangeTheLinkCapacity() throws Exception {
    ((IntersectionSignalSegment) junction.getApproach(approach)).setGreenRatio(0.44, CapacityFactorOrigin.SET_DIRECTLY);
    var rightTurn = layer.getSpatialMovements().getFactory().registerNew(approach, exit, 0.01);
    junction.addMovement(rightTurn);
    rightTurn.setGreenRatio(0.15);

    assertEquals(1584, capacityWrittenFor(approach, true), 1e-6);
  }

  /** With default settings every link keeps its full capacity, whatever the values on the junction */
  @Test
  public void defaultSettingsKeepEveryLinksCapacity() throws Exception {
    ((IntersectionSignalSegment) junction.getApproach(approach)).setGreenRatio(0.44, CapacityFactorOrigin.SET_DIRECTLY);
    junction.getApproach(approachWithoutSignal).setSaturationFlowFactor(0.5, CapacityFactorOrigin.SET_DIRECTLY);

    var writer = MatsimNetworkWriterFactory.create(outputDir.toAbsolutePath().toString());
    assertEquals(false, writer.getSettings().isReduceLinkExitCapacityByEffectiveCapacityFactor());
    for (var segment : List.of(approach, approachWithoutSignal, internal, exit)) {
      assertEquals(3600, capacityWrittenFor(segment, false), 1e-6);
    }
  }

  /** Each link segment written with a reduced capacity is counted by its kind of intersection segment and its factor:
   * the approach with a signal at 0.44, the approach without a signal at 0.5 and the internal segment, given a signal,
   * at 0.6; the exit is not counted */
  @Test
  public void reducedLinkSegmentsAreCountedByKindAndFactor() throws Exception {
    ((IntersectionSignalSegment) junction.getApproach(approach)).setGreenRatio(0.44, CapacityFactorOrigin.SET_DIRECTLY);
    junction.getApproach(approachWithoutSignal).setSaturationFlowFactor(0.5, CapacityFactorOrigin.SET_DIRECTLY);
    junction.addNewInternalSegmentWithSignal(internal);
    ((IntersectionSignalSegment) junction.getIntersectionInternalSegment(internal))
        .setGreenRatio(0.6, CapacityFactorOrigin.SET_DIRECTLY);

    var stats = write(true).getWriterStats();

    assertEquals(3, stats.getLinkSegmentsWithReducedExitCapacity());
    assertEquals(1,
        stats.getLinkSegmentsWithReducedExitCapacity(IntersectionSegmentKind.APPROACH_WITH_SIGNAL_AT_JUNCTION));
    assertEquals(1,
        stats.getLinkSegmentsWithReducedExitCapacity(IntersectionSegmentKind.APPROACH_WITH_SIGNAL_AT_JUNCTION, 0.44));
    assertEquals(1, stats.getLinkSegmentsWithReducedExitCapacity(IntersectionSegmentKind.APPROACH_WITHOUT_SIGNAL, 0.5));
    assertEquals(1,
        stats.getLinkSegmentsWithReducedExitCapacity(IntersectionSegmentKind.INTERNAL_SEGMENT_WITH_SIGNAL, 0.6));
    assertEquals(0,
        stats.getLinkSegmentsWithReducedExitCapacity(IntersectionSegmentKind.APPROACH_WITH_SIGNAL_AT_CROSSING_ONLY));
  }

  /** An approach with green ratio 0.44 that allows no mode activated for MATSim is left out, and so not counted */
  @Test
  public void leftOutLinkSegmentIsNotCounted() throws Exception {
    ((IntersectionSignalSegment) junction.getApproach(approach)).setGreenRatio(0.44, CapacityFactorOrigin.SET_DIRECTLY);
    var withoutModes = layer.getLinkSegmentTypes().getFactory().registerNew("no modes", 3600, 180);
    withoutModes.setXmlId(String.valueOf(withoutModes.getId()));
    approach.setLinkSegmentType(withoutModes);

    var stats = write(true).getWriterStats();

    assertEquals(1, stats.getLinkSegmentsSkippedNoActivatedMode());
    assertEquals(0, stats.getLinkSegmentsWithReducedExitCapacity());
  }

  /** With the setting off no link segment is counted as reduced */
  @Test
  public void nothingIsCountedWithTheSettingOff() throws Exception {
    ((IntersectionSignalSegment) junction.getApproach(approach)).setGreenRatio(0.44, CapacityFactorOrigin.SET_DIRECTLY);
    junction.getApproach(approachWithoutSignal).setSaturationFlowFactor(0.5, CapacityFactorOrigin.SET_DIRECTLY);

    assertEquals(0, write(false).getWriterStats().getLinkSegmentsWithReducedExitCapacity());
  }
}
