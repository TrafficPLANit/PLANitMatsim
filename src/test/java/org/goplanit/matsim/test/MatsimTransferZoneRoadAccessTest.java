package org.goplanit.matsim.test;

import static org.goplanit.matsim.test.NetworkLayerTestUtils.createCartesianNetworkWithOneLayer;
import static org.goplanit.matsim.test.NetworkLayerTestUtils.registerLinkSegmentTypeAllowing;
import static org.goplanit.matsim.test.NetworkLayerTestUtils.registerLinkWithOneSegment;
import static org.goplanit.matsim.test.NetworkLayerTestUtils.registerNodeAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.goplanit.matsim.converter.MatsimIntermodalWriter;
import org.goplanit.matsim.converter.MatsimIntermodalWriterFactory;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
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
    registerLinkWithOneSegment(layer, terminalNode, registerNodeAt(layer, 0, 100),
        registerLinkSegmentTypeAllowing(layer, "road", 1000, 20, mode));
  }

  /** where the files are written, not private as JUnit fills it in */
  @TempDir
  protected Path outputDir;

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
    network = createCartesianNetworkWithOneLayer(PredefinedModeType.FERRY);
    var ferry = network.getModes().get(PredefinedModeType.FERRY);
    layer = network.getTransportLayers().getFirst();

    terminalNode = registerNodeAt(layer, 0, 0);
    var ferryLinkSegment = registerLinkWithOneSegment(layer, registerNodeAt(layer, 100, 0), terminalNode,
        registerLinkSegmentTypeAllowing(layer, "ferry", 1000, 20, ferry));

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
