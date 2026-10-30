package org.goplanit.matsim.test;

import static org.goplanit.matsim.test.NetworkLayerTestUtils.createCartesianNetworkWithOneLayer;
import static org.goplanit.matsim.test.NetworkLayerTestUtils.registerLinkSegmentTypeAllowing;
import static org.goplanit.matsim.test.NetworkLayerTestUtils.registerLinkWithOneSegment;
import static org.goplanit.matsim.test.NetworkLayerTestUtils.registerNodeAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.goplanit.matsim.converter.network.MatsimNetworkWriterFactory;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A banned turn is written to MATSim for the modes it bans only. The network is one link in, from node 0 to node 1,
 * and two links out of node 1, all allowing cars and buses, with cars mapped to MATSim's car and buses to its bus
 * <pre>
 *                  3
 *                  ^
 *                  |  link 1-3
 *   0 ---------->  1 ----------> 2
 *      link 0-1       link 1-2
 * </pre>
 *
 * @author markr
 */
public class MatsimBannedTurnModesTest {

  /** the network written */
  private MacroscopicNetwork network;

  /** its layer */
  private MacroscopicNetworkLayer layer;

  /** modes of the network */
  private Mode car, bus;

  /** the link segment in, and the two out */
  private MacroscopicLinkSegment in, straightOn, left;

  /** where the network is written */
  @TempDir
  Path outputDir;

  /** write the network and collect the disallowed next links written for the link segment, as JSON */
  private String disallowedNextLinksOf(MacroscopicLinkSegment segment) throws Exception {
    var writer = MatsimNetworkWriterFactory.create(outputDir.toAbsolutePath().toString());
    writer.getSettings().setWriteAsGZip(false);
    writer.getSettings().activatePredefinedMode(PredefinedModeType.BUS);
    writer.getSettings().updatePredefinedModeMapping(PredefinedModeType.CAR, "car");
    writer.getSettings().updatePredefinedModeMapping(PredefinedModeType.BUS, "bus");
    writer.write(network);

    var written = Files.readString(
        outputDir.resolve(MatsimNetworkWriterSettings.DEFAULT_NETWORK_FILE_NAME + ".xml"));
    var link = Pattern.compile("<link id=\"" + segment.getXmlId() + "\"[^>]*>(.*?)</link>", Pattern.DOTALL)
        .matcher(written);
    if (!link.find()) {
      return "";
    }
    var attribute = Pattern.compile("disallowedNextLinks[^>]*>([^<]*)<").matcher(link.group(1));
    return attribute.find() ? attribute.group(1) : "";
  }

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
    network = createCartesianNetworkWithOneLayer(PredefinedModeType.CAR, PredefinedModeType.BUS);
    car = network.getModes().get(PredefinedModeType.CAR);
    bus = network.getModes().get(PredefinedModeType.BUS);
    layer = network.getTransportLayers().getFirst();
    var type = registerLinkSegmentTypeAllowing(
        layer, "road", MacroscopicLinkSegmentType.DEFAULT_CAPACITY_PER_LANE, 50, car, bus);

    var n0 = registerNodeAt(layer, 0, 0);
    var n1 = registerNodeAt(layer, 100, 0);
    in = registerLinkWithOneSegment(layer, n0, n1, type);
    straightOn = registerLinkWithOneSegment(layer, n1, registerNodeAt(layer, 200, 0), type);
    left = registerLinkWithOneSegment(layer, n1, registerNodeAt(layer, 100, 100), type);
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** A banned turn for buses only is written for MATSim's bus mode only */
  @Test
  public void banForBusesOnly() throws Exception {
    layer.getBannedTurns().getFactory().registerNew(in, left, List.of(bus));

    var json = disallowedNextLinksOf(in);

    assertTrue(json.contains("\"bus\""), json);
    assertFalse(json.contains("\"car\""), json);
  }

  /** A banned turn naming no modes is written for every mapped mode */
  @Test
  public void banForEveryMode() throws Exception {
    layer.getBannedTurns().getFactory().registerNew(in, straightOn);

    var json = disallowedNextLinksOf(in);

    assertTrue(json.contains("\"bus\""), json);
    assertTrue(json.contains("\"car\""), json);
  }

  /** A banned turn for a mode not written to MATSim gives the link no disallowed next links */
  @Test
  public void banForAnUnmappedModeIsNotWritten() throws Exception {
    var pedestrian = network.getModes().getFactory().registerNew(PredefinedModeType.PEDESTRIAN);
    layer.getBannedTurns().getFactory().registerNew(in, left, List.of(pedestrian));

    assertEquals("", disallowedNextLinksOf(in));
  }
}
