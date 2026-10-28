package org.goplanit.matsim.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.goplanit.matsim.converter.MatsimIntermodalWriter;
import org.goplanit.matsim.converter.MatsimIntermodalWriterFactory;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.layer.macroscopic.AccessGroupPropertiesFactory;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.TransferZoneType;
import org.goplanit.utils.zoning.connectoid.ZoneConnectoidType;
import org.goplanit.zoning.Zoning;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A stop must be reachable from the road network only when a road based mode, walking included, is written to MATSim:
 * otherwise nobody needs to reach it by road. A ferry terminal at node 0 has a connectoid on the ferry link from node 1
 * for ferries only; in the second case a road for cars runs from node 0 to node 2
 * <pre>
 *   2
 *   ^
 *   |  road, cars only (second case)
 *   0 <---------- 1
 *      ferry link
 * </pre>
 *
 * @author markr
 */
public class MatsimTransferZoneRoadAccessTest {

  /** the network written */
  private MacroscopicNetwork network;

  /** its layer */
  private MacroscopicNetworkLayer layer;

  /** the zoning with the ferry terminal */
  private Zoning zoning;

  /** the node at the ferry terminal */
  private Node terminalNode;

  /** register a node at a position with its id as XML id */
  private Node node(double x, double y) {
    var node = layer.getNodes().getFactory().registerNew();
    node.setPosition(PlanitJtsUtils.createPoint(x, y));
    node.setXmlId(String.valueOf(node.getId()));
    return node;
  }

  /** register a link segment type for one mode */
  private MacroscopicLinkSegmentType typeFor(String name, Mode mode) {
    var type = layer.getLinkSegmentTypes().getFactory().registerNew(name, 1000, 180);
    type.setXmlId(String.valueOf(type.getId()));
    AccessGroupPropertiesFactory.createOnLinkSegmentType(type, 20, List.of(mode));
    return type;
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

  /** write the network and zoning to MATSim, with the given modes activated on top of the defaults, and return the
   * writer */
  private MatsimIntermodalWriter write(PredefinedModeType... activatedModes) {
    var writer = MatsimIntermodalWriterFactory.create(outputDir.toAbsolutePath().toString());
    writer.getSettings().setWriteAsGZip(false);
    for (var modeType : activatedModes) {
      writer.getSettings().getNetworkSettings().activatePredefinedMode(modeType);
    }
    writer.write(network, zoning);
    return writer;
  }

  /** add a road from the terminal node for the mode, written to MATSim */
  private void addRoadFromTerminalFor(PredefinedModeType modeType) {
    var mode = network.getModes().getFactory().registerNew(modeType);
    layer.registerSupportedMode(mode);
    linkSegment(terminalNode, node(0, 100), typeFor("road", mode));
  }

  /** where the files are written, not private as JUnit fills it in */
  @TempDir
  protected Path outputDir;

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
    network = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken());
    network.setCoordinateReferenceSystem(PlanitJtsCrsUtils.CARTESIANCRS);
    var ferry = network.getModes().getFactory().registerNew(PredefinedModeType.FERRY);
    layer = network.getTransportLayers().getFactory().registerNew(network.getModes());

    terminalNode = node(0, 0);
    var ferryLinkSegment = linkSegment(node(100, 0), terminalNode, typeFor("ferry", ferry));

    zoning = new Zoning(IdGroupingToken.collectGlobalToken(), layer.getLayerIdGroupingToken());
    var terminal = zoning.getTransferZones().getFactory().registerNew(TransferZoneType.PLATFORM, true);
    terminal.setGeometry(PlanitJtsUtils.createPoint(0, 0));
    var connectoid = zoning.getTransferConnectoids().getFactory().registerNewWithDirectedEntry(
        terminal, true, ferryLinkSegment, ZoneConnectoidType.PT_VEHICLE_STOP);
    connectoid.setXmlId(String.valueOf(connectoid.getId()));
    connectoid.getAccessZoneEntry(terminal, ZoneConnectoidType.PT_VEHICLE_STOP).addExplicitAllowedMode(ferry);
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** With only ferries written, the terminal needs no road access and is written as a stop */
  @Test
  public void stopWithoutRoadAccessIsWrittenWhenNoRoadModeIsWritten() {
    assertEquals(1, write().getZoningWriterStats().getPtWriterStats().getStopFacilitiesWritten());
  }

  /** With cars written as well, the terminal without road access means no stop can be reached, so writing aborts */
  @Test
  public void stopWithoutRoadAccessAbortsWhenRoadModeIsWritten() {
    addRoadFromTerminalFor(PredefinedModeType.CAR);

    assertThrows(PlanItRunTimeException.class, () -> write());
  }

  /** Walking counts as a road based mode: with pedestrians written as well, writing aborts too */
  @Test
  public void stopWithoutRoadAccessAbortsWhenWalkingIsWritten() {
    addRoadFromTerminalFor(PredefinedModeType.PEDESTRIAN);

    assertThrows(PlanItRunTimeException.class, () -> write(PredefinedModeType.PEDESTRIAN));
  }
}
