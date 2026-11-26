package org.goplanit.matsim.test;

import static org.goplanit.matsim.test.NetworkLayerTestUtils.createCartesianNetworkWithOneLayer;
import static org.goplanit.matsim.test.NetworkLayerTestUtils.registerLinkSegmentTypeAllowing;
import static org.goplanit.matsim.test.NetworkLayerTestUtils.registerLinkWithOneSegment;
import static org.goplanit.matsim.test.NetworkLayerTestUtils.registerNodeAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.goplanit.logging.Logging;
import org.goplanit.matsim.converter.MatsimIntermodalWriter;
import org.goplanit.matsim.converter.MatsimIntermodalWriterFactory;
import org.goplanit.matsim.converter.MatsimWriterSettingsFactory;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.FileUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.settings.SettingsSource;
import org.goplanit.utils.zoning.TransferZoneType;
import org.goplanit.utils.zoning.connectoid.ZoneConnectoidType;
import org.goplanit.zoning.Zoning;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

  /** the logger, giving the log output PLANit's format */
  private static Logger LOGGER = null;

  /**
   * The names of the files in a folder
   *
   * @param folder to look in
   * @return the names, sorted
   */
  private static Set<String> fileNamesIn(Path folder) {
    var fileNames = new TreeSet<String>();
    FileUtils.callForEachFileIn(folder.toString(), file -> fileNames.add(file.getName()));
    return fileNames;
  }

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

  /**
   * Create the logger once for all tests of the class
   *
   * @throws Exception when the log file cannot be opened
   */
  @BeforeAll
  public static void setUpLogger() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(MatsimTransferZoneRoadAccessTest.class);
    }
  }

  /** Close the logger after all tests of the class */
  @AfterAll
  public static void tearDownLogger() {
    Logging.closeLogger(LOGGER);
  }

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

  /**
   * Written with intermodal writer settings made from settings files, the terminal is written as with the settings
   * made in code: the same files, each with the same lines. The files name one file for all the nested settings,
   * setting the folder written to, relative to that file, and the files written as not gzipped
   *
   * @throws IOException when a settings file cannot be written or a file written cannot be read
   */
  @Test
  public void writtenWithSettingsFromFilesAsWithSettingsInCode() throws IOException {
    var inCode = Files.createDirectories(outputDir.resolve("inCode"));
    var writer = MatsimIntermodalWriterFactory.create(inCode.toString());
    writer.getSettings().setWriteAsGZip(false);
    writer.write(network, zoning);

    /* a conversion of its own, from the network and zoning built afresh */
    setUp();
    var fromFiles = Files.createDirectories(outputDir.resolve("fromFiles"));
    Files.writeString(outputDir.resolve("nested_settings.yaml"),
        "planitMatsimWriterSettings: 1\noutputDirectory: fromFiles\nwriteAsGZip: false\n");
    var intermodalFile = Files.writeString(outputDir.resolve("intermodal.yaml"), "planitMatsimWriterSettings: 1\n"
        + "network: nested_settings.yaml\nzoning: nested_settings.yaml\nptServices: nested_settings.yaml\n");
    MatsimIntermodalWriterFactory.create(MatsimWriterSettingsFactory.createIntermodalWriterSettingsFromYaml(
        List.of(SettingsSource.ofFile(intermodalFile)))).write(network, zoning);

    var fileNames = fileNamesIn(inCode);
    assertFalse(fileNames.isEmpty());
    assertEquals(fileNames, fileNamesIn(fromFiles));
    for (var fileName : fileNames) {
      assertEquals(Files.readAllLines(inCode.resolve(fileName)), Files.readAllLines(fromFiles.resolve(fileName)),
          fileName);
    }
  }
}
