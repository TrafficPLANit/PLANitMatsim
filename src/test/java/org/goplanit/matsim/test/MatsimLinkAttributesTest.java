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
import java.util.regex.Pattern;

import org.goplanit.matsim.converter.network.MatsimNetworkWriterFactory;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A MATSim link's original id and type are written as attribute elements inside the link, the way MATSim writes them
 * itself, so MATSim's analyses and viewers such as Via find them. The type comes from the link type function of the
 * MATSim network writer settings. The network is one link from node 0 to node 1, allowing cars
 * <pre>
 *   0 ----------> 1
 * </pre>
 *
 * @author markr
 */
public class MatsimLinkAttributesTest {

  /** the value of the attribute element of the given name inside the written link, null when there is none */
  private static String attributeElementValue(String writtenLink, String name) {
    var value = Pattern.compile(
        "<attribute name=\"" + name + "\" class=\"java.lang.String\">([^<]*)</attribute>").matcher(writtenLink);
    return value.find() ? value.group(1) : null;
  }

  /** the network written */
  private MacroscopicNetwork network;

  /** its only link segment */
  private MacroscopicLinkSegment linkSegment;

  /** where the network is written, not private as JUnit fills it in */
  @TempDir
  protected Path outputDir;

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
    network = createCartesianNetworkWithOneLayer(PredefinedModeType.CAR);
    var layer = network.getTransportLayers().getFirst();
    var type = registerLinkSegmentTypeAllowing(layer, "road", 3600, 50, network.getModes().get(PredefinedModeType.CAR));

    linkSegment = registerLinkWithOneSegment(layer, registerNodeAt(layer, 0, 0), registerNodeAt(layer, 100, 0), type);
    linkSegment.getParent().setExternalId("172474298");
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** A link segment of OSM way 172474298 that the type function gives "secondary" is written as a MATSim link with
   * attribute elements origid 172474298 and type "secondary", and neither on the link element itself */
  @Test
  public void origIdAndTypeAreWrittenAsAttributeElements() throws Exception {
    var writer = MatsimNetworkWriterFactory.create(outputDir.toAbsolutePath().toString());
    writer.getSettings().setWriteAsGZip(false);
    writer.getSettings().setTypeFunction(segment -> "secondary");
    writer.write(network);

    var written = Files.readString(
        outputDir.resolve(MatsimNetworkWriterSettings.DEFAULT_NETWORK_FILE_NAME + ".xml"));
    var link = Pattern.compile(
        "(<link id=\"" + linkSegment.getXmlId() + "\"[^>]*>)(.*?</link>)?", Pattern.DOTALL).matcher(written);
    assertTrue(link.find());
    var linkElement = link.group(1);
    var linkContent = link.group(2) == null ? "" : link.group(2);

    assertEquals("172474298", attributeElementValue(linkContent, "origid"));
    assertEquals("secondary", attributeElementValue(linkContent, "type"));
    assertFalse(Pattern.compile("\\s(origid|type)=").matcher(linkElement).find());
  }
}
