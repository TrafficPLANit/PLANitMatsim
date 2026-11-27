package org.goplanit.matsim.test;

import static org.goplanit.utils.mode.PredefinedModeType.BICYCLE;
import static org.goplanit.utils.mode.PredefinedModeType.BUS;
import static org.goplanit.utils.mode.PredefinedModeType.CAR;
import static org.goplanit.utils.mode.PredefinedModeType.FERRY;
import static org.goplanit.utils.mode.PredefinedModeType.LIGHTRAIL;
import static org.goplanit.utils.mode.PredefinedModeType.PEDESTRIAN;
import static org.goplanit.utils.mode.PredefinedModeType.TRAIN;
import static org.goplanit.utils.mode.PredefinedModeType.TRAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.goplanit.matsim.converter.MatsimIntermodalWriterSettings;
import org.goplanit.matsim.converter.MatsimPtServicesWriterSettings;
import org.goplanit.matsim.converter.MatsimWriterSettingsFactory;
import org.goplanit.matsim.converter.MatsimZoningWriterSettings;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterSettings;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.matsim.util.ModeChainCollapseRule;
import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.SettingsFileContentType;
import org.goplanit.utils.settings.SettingsSource;
import org.goplanit.utils.settings.YamlWriteSettingsSupport;
import org.goplanit.utils.settings.problem.SettingsProblemKind;
import org.goplanit.utils.test.SettingsFileAssertionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of the MATSim writer settings, made in code and from settings files. In code, the PLANit modes written to
 * MATSim keep a default MATSim mode each, which follows the choice of disaggregating transit modes, a MATSim mode given
 * by the user, and whether the mode is switched on; what a writer writes is checked through the MATSim mode each
 * predefined PLANit mode is written as. The discrete demands writer's mode collapse rules are held by each settings
 * object of its own, from the default bus and train rules
 *
 * @author markr
 */
public class MatsimSettingsFilesTest {

  /** the folder of the settings files of each MATSim writer, absolute so a relative output directory resolves to one */
  private static final Path SETTINGS_DIR = Path.of("src", "test", "resources", "matsimyaml").toAbsolutePath();

  /** the header of the MATSim writer settings */
  private static final String HEADER = "planitMatsimWriterSettings: 1\n";

  /**
   * The settings file of a MATSim writer with every key not at its default
   *
   * @param writer the file is of, as its name has it, such as network or pt_services
   * @return the file
   */
  private static Path everyKeyNotDefaultFileOf(String writer) {
    return SETTINGS_DIR.resolve("matsim_" + writer + "_writer_every_key_not_default.yaml");
  }

  /**
   * The settings file of a MATSim writer at its global defaults
   *
   * @param writer the file is of, as its name has it, such as network or pt_services
   * @return the file
   */
  private static Path globalDefaultsFileOf(String writer) {
    return SETTINGS_DIR.resolve("matsim_" + writer + "_writer_global_defaults.yaml");
  }

  /**
   * A source of MATSim writer settings given as text
   *
   * @param keys of the settings, without the header
   * @return the source
   */
  private static SettingsSource text(String keys) {
    return SettingsSource.ofText(PlanitMatsimWriterSettings.SCHEMA, keys);
  }

  /**
   * Verify making settings from keys throws one settings problem: a key the settings do not have
   *
   * @param fromYaml makes the settings from sources
   * @param keys to make the settings from, without the header
   * @param unknownKey the one problem names
   */
  private static void assertKeyUnknown(
      Function<List<SettingsSource>, ?> fromYaml, String keys, String unknownKey) {
    var problem = SettingsFileAssertionUtils.assertThrowsOneSettingsProblem(SettingsProblemKind.UNKNOWN_KEY,
        () -> fromYaml.apply(List.of(text(keys))));
    assertEquals(unknownKey, problem.getSubject());
  }

  /**
   * The mode collapse rule of a main mode
   *
   * @param settings holding the rules
   * @param mainMode of the rule
   * @return the rule
   */
  private static ModeChainCollapseRule ruleOf(
      MatsimDiscreteDemandsWriterSettings settings, PredefinedModeType mainMode) {
    return Objects.requireNonNull(settings.getModeCollapseRules().get(mainMode));
  }

  /**
   * The main modes of the mode collapse rules
   *
   * @param settings holding the rules
   * @return the main modes
   */
  private static Set<PredefinedModeType> mainModesOf(MatsimDiscreteDemandsWriterSettings settings) {
    return settings.getModeCollapseRules().keySet();
  }

  /**
   * The MATSim mode each predefined PLANit mode is written as, for the modes that are written
   *
   * @param settings to ask
   * @return the MATSim mode by PLANit mode, sorted
   */
  private static Map<PredefinedModeType, String> modesWritten(PlanitMatsimWriterModeMappingSettings settings) {
    var written = new TreeMap<PredefinedModeType, String>();
    for (var type : PredefinedModeType.getPredefinedModeTypesWithout(PredefinedModeType.CUSTOM)) {
      var matsimMode = settings.getMappedMatsimMode(type);
      if (matsimMode != null) {
        written.put(type, matsimMode);
      }
    }
    return written;
  }

  /**
   * Verify a file of nested settings written by intermodal settings is the file the nested settings write when made on
   * their own from their file of every key not at its default
   *
   * @param fromYaml makes the nested settings from sources
   * @param writer of the nested settings, as the names of its settings files have it
   * @param nestedSettingsFile written by the intermodal settings
   * @throws IOException when a file cannot be written or read
   */
  private void assertNestedSettingsWrittenAsOnTheirOwn(
      Function<List<SettingsSource>, ? extends YamlWriteSettingsSupport> fromYaml, String writer,
      Path nestedSettingsFile) throws IOException {
    var onItsOwn = tempDir.resolve(writer + "_on_its_own.yaml");
    fromYaml.apply(List.of(SettingsSource.ofFile(everyKeyNotDefaultFileOf(writer))))
        .writeSettingsFile(onItsOwn, SettingsFileContentType.ALL_KEYS, true);
    assertEquals(Files.readAllLines(onItsOwn), Files.readAllLines(nestedSettingsFile), writer);
  }

  /** folder for the test files */
  @TempDir
  public Path tempDir;

  /**
   * For each writer, settings made from the file of every key not at its default write that file again, with every
   * key, with the keys differing from the defaults, and when made again from a file without descriptions
   */
  @Test
  public void fileWrittenAgainBySettingsMadeFromIt() throws IOException {
    SettingsFileAssertionUtils.assertFileWrittenAgainBySettingsMadeFromIt(
        MatsimWriterSettingsFactory::createNetworkWriterSettingsFromYaml, everyKeyNotDefaultFileOf("network"), tempDir);
    SettingsFileAssertionUtils.assertFileWrittenAgainBySettingsMadeFromIt(
        MatsimWriterSettingsFactory::createZoningWriterSettingsFromYaml, everyKeyNotDefaultFileOf("zoning"), tempDir);
    SettingsFileAssertionUtils.assertFileWrittenAgainBySettingsMadeFromIt(
        MatsimWriterSettingsFactory::createPtServicesWriterSettingsFromYaml, everyKeyNotDefaultFileOf("pt_services"),
        tempDir);
    SettingsFileAssertionUtils.assertFileWrittenAgainBySettingsMadeFromIt(
        MatsimWriterSettingsFactory::createDiscreteDemandsWriterSettingsFromYaml,
        everyKeyNotDefaultFileOf("discrete_demands"), tempDir);
  }

  /**
   * For each writer, the settings at their defaults write the file of the global defaults, and so do settings made from
   * it
   */
  @Test
  public void fileWrittenBySettingsAtTheirDefaults() throws IOException {
    SettingsFileAssertionUtils.assertFileWrittenBySettingsAtTheirDefaults(
        MatsimWriterSettingsFactory::createNetworkWriterSettingsFromYaml, new MatsimNetworkWriterSettings(),
        globalDefaultsFileOf("network"), tempDir);
    SettingsFileAssertionUtils.assertFileWrittenBySettingsAtTheirDefaults(
        MatsimWriterSettingsFactory::createZoningWriterSettingsFromYaml, new MatsimZoningWriterSettings(),
        globalDefaultsFileOf("zoning"), tempDir);
    SettingsFileAssertionUtils.assertFileWrittenBySettingsAtTheirDefaults(
        MatsimWriterSettingsFactory::createPtServicesWriterSettingsFromYaml, new MatsimPtServicesWriterSettings(),
        globalDefaultsFileOf("pt_services"), tempDir);
    SettingsFileAssertionUtils.assertFileWrittenBySettingsAtTheirDefaults(
        MatsimWriterSettingsFactory::createDiscreteDemandsWriterSettingsFromYaml,
        new MatsimDiscreteDemandsWriterSettings(), globalDefaultsFileOf("discrete_demands"), tempDir);
  }

  /**
   * For each writer, settings made from the file of every key not at its default and then reset write every key as
   * settings made new do: a reset puts every key back to its default, the file name and the country included
   */
  @Test
  public void resetGivesSettingsAsMadeNew() throws IOException {
    SettingsFileAssertionUtils.assertResetGivesSettingsAsMadeNew(
        MatsimWriterSettingsFactory::createNetworkWriterSettingsFromYaml, MatsimNetworkWriterSettings::reset,
        everyKeyNotDefaultFileOf("network"), new MatsimNetworkWriterSettings(), tempDir);
    SettingsFileAssertionUtils.assertResetGivesSettingsAsMadeNew(
        MatsimWriterSettingsFactory::createZoningWriterSettingsFromYaml, MatsimZoningWriterSettings::reset,
        everyKeyNotDefaultFileOf("zoning"), new MatsimZoningWriterSettings(), tempDir);
    SettingsFileAssertionUtils.assertResetGivesSettingsAsMadeNew(
        MatsimWriterSettingsFactory::createPtServicesWriterSettingsFromYaml, MatsimPtServicesWriterSettings::reset,
        everyKeyNotDefaultFileOf("pt_services"), new MatsimPtServicesWriterSettings(), tempDir);
    SettingsFileAssertionUtils.assertResetGivesSettingsAsMadeNew(
        MatsimWriterSettingsFactory::createDiscreteDemandsWriterSettingsFromYaml,
        MatsimDiscreteDemandsWriterSettings::reset, everyKeyNotDefaultFileOf("discrete_demands"),
        new MatsimDiscreteDemandsWriterSettings(), tempDir);
  }

  /**
   * Intermodal settings made from a file naming the files of their nested settings of every key not at its default
   * write a file naming a file for each of their nested settings, the same as at their defaults, and each of those
   * files as the nested settings write it on their own; made from the files written, they hold the values of the
   * nested settings
   */
  @Test
  public void intermodalWrittenWithItsNestedSettings() throws IOException {
    var settings = MatsimWriterSettingsFactory.createIntermodalWriterSettingsFromYaml(
        List.of(SettingsSource.ofFile(everyKeyNotDefaultFileOf("intermodal"))));
    var file = tempDir.resolve("intermodal.yaml");
    settings.writeSettingsFile(file, SettingsFileContentType.ALL_KEYS, true);

    assertEquals(Files.readAllLines(globalDefaultsFileOf("intermodal")), Files.readAllLines(file));
    assertNestedSettingsWrittenAsOnTheirOwn(MatsimWriterSettingsFactory::createNetworkWriterSettingsFromYaml,
        "network", file.resolveSibling("intermodal_network.yaml"));
    assertNestedSettingsWrittenAsOnTheirOwn(MatsimWriterSettingsFactory::createZoningWriterSettingsFromYaml,
        "zoning", file.resolveSibling("intermodal_zoning.yaml"));
    assertNestedSettingsWrittenAsOnTheirOwn(MatsimWriterSettingsFactory::createPtServicesWriterSettingsFromYaml,
        "pt_services", file.resolveSibling("intermodal_ptServices.yaml"));

    var readBack =
        MatsimWriterSettingsFactory.createIntermodalWriterSettingsFromYaml(List.of(SettingsSource.ofFile(file)));
    assertEquals(2.5, readBack.getNetworkSettings().getMinimumLinkLengthMeters());
    assertEquals(4.0, readBack.getZoningSettings().getTransferZoneAccessSpeedKmH());
    assertTrue(readBack.getPtServicesSettings().isAwaitDepartures());
  }

  /**
   * Intermodal settings at their defaults write a file naming a file for each of their nested settings, and each of
   * those files as the global defaults of those nested settings; made from those files, they write them again
   */
  @Test
  public void intermodalAtItsDefaultsWrittenWithItsNestedSettings() throws IOException {
    var file = tempDir.resolve("intermodal.yaml");
    new MatsimIntermodalWriterSettings().writeSettingsFile(file, SettingsFileContentType.ALL_KEYS, true);

    assertEquals(Files.readAllLines(globalDefaultsFileOf("intermodal")), Files.readAllLines(file));
    assertEquals(Files.readAllLines(globalDefaultsFileOf("network")),
        Files.readAllLines(file.resolveSibling("intermodal_network.yaml")));
    assertEquals(Files.readAllLines(globalDefaultsFileOf("zoning")),
        Files.readAllLines(file.resolveSibling("intermodal_zoning.yaml")));
    assertEquals(Files.readAllLines(globalDefaultsFileOf("pt_services")),
        Files.readAllLines(file.resolveSibling("intermodal_ptServices.yaml")));

    var again = tempDir.resolve("again").resolve("intermodal.yaml");
    MatsimWriterSettingsFactory.createIntermodalWriterSettingsFromYaml(List.of(SettingsSource.ofFile(file)))
        .writeSettingsFile(again, SettingsFileContentType.ALL_KEYS, true);
    for (var name : List.of(
        "intermodal.yaml", "intermodal_network.yaml", "intermodal_zoning.yaml", "intermodal_ptServices.yaml")) {
      assertEquals(Files.readAllLines(file.resolveSibling(name)), Files.readAllLines(again.resolveSibling(name)), name);
    }
  }

  /** A PLANit writer's file given to a MATSim writer is one problem, of its header */
  @Test
  public void planitIoWriterFileRefused() {
    SettingsFileAssertionUtils.assertThrowsOneSettingsProblem(SettingsProblemKind.WRONG_HEADER,
        () -> MatsimWriterSettingsFactory.createNetworkWriterSettingsFromYaml(
            List.of(SettingsSource.ofText("planitIoWriterSettings: 1\nfileName: roads\n"))));
  }

  /** Sources stack in order, the last one giving a key winning */
  @Test
  public void stackedFilesTheLaterWinning() {
    var settings = MatsimWriterSettingsFactory.createNetworkWriterSettingsFromYaml(List.of(
        text("fileName: roads\nminimumLinkLengthMeters: 2.0\n"), text("minimumLinkLengthMeters: 3.0\n")));
    assertEquals("roads", settings.getFileName());
    assertEquals(3.0, settings.getMinimumLinkLengthMeters());
  }

  /** A key of another writer, the zoning writer's here, is a problem naming the key for the network writer */
  @Test
  public void keyOfAnotherWriterRefused() {
    assertKeyUnknown(MatsimWriterSettingsFactory::createNetworkWriterSettingsFromYaml,
        "writeTransferZoneAccess: false\n", "writeTransferZoneAccess");
  }

  /**
   * The mode keys are refused where they would do nothing: the modes by the services writer, which writes with the
   * network writer's modes, and disaggregating transit modes by the network writer, whose file lists every MATSim mode
   */
  @Test
  public void modeKeysRefusedWhereTheyDoNothing() {
    assertKeyUnknown(MatsimWriterSettingsFactory::createPtServicesWriterSettingsFromYaml,
        "modeMappings: {BUS: pt}\n", "modeMappings");
    assertKeyUnknown(MatsimWriterSettingsFactory::createNetworkWriterSettingsFromYaml,
        "useDisaggregateTransitModes: true\n", "useDisaggregateTransitModes");
  }

  /**
   * An intermodal file naming, for the network, a file all its nested settings share followed by the network's own
   * file, and the shared file for the zoning and the services: each of the nested settings takes the shared keys and
   * the network its own too, a key in both files taken from the later
   */
  @Test
  public void sharedFileAndThePartsOwnFileAsAList() throws IOException {
    Files.writeString(tempDir.resolve("matsim_output.yaml"),
        HEADER + "outputDirectory: out\ncountry: Australia\nwriteAsGZip: false\n");
    Files.writeString(tempDir.resolve("matsim_network.yaml"), HEADER + "fileName: roads\nwriteAsGZip: true\n");
    var file = Files.writeString(tempDir.resolve("intermodal.yaml"), HEADER
        + "network: [matsim_output.yaml, matsim_network.yaml]\n"
        + "zoning: matsim_output.yaml\nptServices: matsim_output.yaml\n");

    var settings =
        MatsimWriterSettingsFactory.createIntermodalWriterSettingsFromYaml(List.of(SettingsSource.ofFile(file)));
    var network = settings.getNetworkSettings();
    assertEquals(tempDir.resolve("out"), Path.of(network.getOutputDirectory()));
    assertEquals(CountryNames.AUSTRALIA, network.getCountry());
    assertEquals("roads", network.getFileName());
    assertTrue(network.isWriteAsGZip());
    assertEquals(tempDir.resolve("out"), Path.of(settings.getZoningSettings().getOutputDirectory()));
    assertFalse(settings.getZoningSettings().isWriteAsGZip());
    assertEquals(CountryNames.AUSTRALIA, settings.getPtServicesSettings().getCountry());
  }

  /** Only the modes listed are written, each as the MATSim mode given */
  @Test
  public void onlyTheModesListedWritten() {
    var settings = MatsimWriterSettingsFactory.createNetworkWriterSettingsFromYaml(
        List.of(text("modeMappings: {CAR: car, PEDESTRIAN: walk, TRAM: rail}\n")));
    assertEquals(Map.of(CAR, "car", PEDESTRIAN, "walk", TRAM, "rail"), modesWritten(settings));
  }

  /** A custom mode, and a blank MATSim mode, are each one problem naming the modes key */
  @Test
  public void customModeAndBlankMatsimModeRefused() {
    for (var keys : List.of("modeMappings: {CUSTOM: car}\n", "modeMappings: {CAR: ''}\n")) {
      var problem = SettingsFileAssertionUtils.assertThrowsOneSettingsProblem(SettingsProblemKind.VALUE_REFUSED,
          () -> MatsimWriterSettingsFactory.createNetworkWriterSettingsFromYaml(List.of(text(keys))));
      assertEquals("modeMappings", problem.getSubject(), keys);
    }
  }

  /**
   * Written out, the modes key lists every mode switched on in code with the MATSim mode it is written as, so the file
   * read back writes the same modes
   */
  @Test
  public void modesWrittenOutAsSwitchedOn() throws IOException {
    var settings = new MatsimNetworkWriterSettings();
    settings.activatePredefinedMode(PEDESTRIAN);
    settings.deactivatePredefinedMode(FERRY);
    settings.updatePredefinedModeMapping(CAR, "auto");
    assertEquals(Map.of(BUS, "pt", CAR, "auto", LIGHTRAIL, "pt", PEDESTRIAN, "walk", TRAIN, "pt"),
        modesWritten(settings));

    var file = tempDir.resolve("network.yaml");
    settings.writeSettingsFile(file, SettingsFileContentType.KEYS_DIFFERING_FROM_DEFAULTS, false);
    var readBack =
        MatsimWriterSettingsFactory.createNetworkWriterSettingsFromYaml(List.of(SettingsSource.ofFile(file)));
    assertEquals(modesWritten(settings), modesWritten(readBack));
  }

  /** Collapsing switched on in a file, and no rule left by an empty map of rules */
  @Test
  public void collapsingSwitchedOnWithNoRules() {
    var settings = MatsimWriterSettingsFactory.createDiscreteDemandsWriterSettingsFromYaml(
        List.of(text("collapseModeChains: true\nmodeCollapseRules: {}\n")));
    assertTrue(settings.isCollapseModeChains());
    assertTrue(settings.getModeCollapseRules().isEmpty());
  }

  /** One rule given with fields left out replaces the default rules, the lists left out empty, the flags false */
  @Test
  public void oneRuleGivenWithFieldsLeftOut() {
    var settings = MatsimWriterSettingsFactory.createDiscreteDemandsWriterSettingsFromYaml(
        List.of(text("modeCollapseRules:\n  TRAM: {fromConnectors: [PEDESTRIAN]}\n")));
    assertEquals(Set.of(TRAM), mainModesOf(settings));
    var rule = ruleOf(settings, TRAM);
    assertEquals(List.of(PEDESTRIAN), rule.getAllowedFromConnectors());
    assertEquals(List.of(), rule.getAllowedToConnectors());
    assertFalse(rule.isAllowedFromMissingIfToPresent());
    assertFalse(rule.isAllowedToMissingIfFromPresent());
  }

  /** Disaggregating transit modes in code writes bus as MATSim mode bus and train as train, and pt again when undone */
  @Test
  public void testDisaggregateTransitModesInCode() {
    var settings = new MatsimNetworkWriterSettings(CountryNames.WORLD);
    assertEquals("pt", settings.getMappedMatsimMode(BUS));

    settings.setUseDisaggregateTransitModes(true);
    assertEquals("bus", settings.getMappedMatsimMode(BUS));
    assertEquals("train", settings.getMappedMatsimMode(TRAIN));

    settings.setUseDisaggregateTransitModes(false);
    assertEquals("pt", settings.getMappedMatsimMode(BUS));
  }

  /**
   * A mode switched on in code is written as its default MATSim mode at the time it is written, so tram switched on and
   * then transit modes disaggregated is written as train
   */
  @Test
  public void testModeSwitchedOnBeforeDisaggregating() {
    var settings = new MatsimNetworkWriterSettings(CountryNames.WORLD);
    settings.activatePredefinedMode(TRAM);
    settings.setUseDisaggregateTransitModes(true);
    assertEquals("train", settings.getMappedMatsimMode(TRAM));
  }

  /**
   * Resetting the settings gives the modes they are made with: bus, car, ferry, light rail and train at their default
   * MATSim modes, whatever was switched on, off or given a MATSim mode before
   */
  @Test
  public void testResetGivesTheModesSettingsAreMadeWith() {
    assertEquals(Map.of(BUS, "pt", CAR, "car", FERRY, "pt", LIGHTRAIL, "pt", TRAIN, "pt"),
        modesWritten(new MatsimNetworkWriterSettings(CountryNames.WORLD)));

    var settings = new MatsimNetworkWriterSettings(CountryNames.WORLD);
    settings.setUseDisaggregateTransitModes(true);
    settings.activateAllDefaultMappedModes();
    settings.updatePredefinedModeMapping(CAR, "other");
    settings.deactivatePredefinedMode(BUS);
    settings.reset();
    assertEquals(Map.of(BUS, "pt", CAR, "car", FERRY, "pt", LIGHTRAIL, "pt", TRAIN, "pt"), modesWritten(settings));
  }

  /** A rule changed on one settings object changes no other: settings made after it hold the default train rule */
  @Test
  public void testRuleChangedOnOneSettingsObjectChangesNoOther() {
    var changed = new MatsimDiscreteDemandsWriterSettings();
    changed.setConnectorFlagsForRule(TRAIN, false, true);
    assertTrue(ruleOf(changed, TRAIN).isAllowedToMissingIfFromPresent());

    var madeAfter = new MatsimDiscreteDemandsWriterSettings();
    assertFalse(ruleOf(madeAfter, TRAIN).isAllowedToMissingIfFromPresent());
  }

  /**
   * A reset gives the default bus and train rules with collapsing switched off, and the rules can be changed after it
   */
  @Test
  public void testRulesChangedAfterReset() {
    var settings = new MatsimDiscreteDemandsWriterSettings();
    settings.setCollapseModeChains(true);
    settings.removeAllModeCollapseRules();
    settings.reset();
    assertFalse(settings.isCollapseModeChains());
    assertEquals(Set.of(BUS, TRAIN), mainModesOf(settings));

    settings.removeModeCollapseRule(BUS);
    settings.setModeCollapseRule(new ModeChainCollapseRule(TRAM, List.of(PEDESTRIAN), List.of(PEDESTRIAN)));
    assertEquals(Set.of(TRAIN, TRAM), mainModesOf(settings));
  }

  /** Connectors added to a rule are allowed besides those it had */
  @Test
  public void testConnectorsAddedToARule() {
    var settings = new MatsimDiscreteDemandsWriterSettings();
    settings.addFromConnectorsToRule(BUS, List.of(BICYCLE));
    settings.addToConnectorsToRule(BUS, List.of(BICYCLE));
    assertEquals(List.of(PEDESTRIAN, BICYCLE), ruleOf(settings, BUS).getAllowedFromConnectors());
    assertEquals(List.of(PEDESTRIAN, BICYCLE), ruleOf(settings, BUS).getAllowedToConnectors());
  }

  /** A reset sets transit routes back to not awaiting their scheduled departures */
  @Test
  public void testResetServicesSettings() {
    var settings = new MatsimPtServicesWriterSettings();
    settings.setAwaitDepartures(true);
    settings.reset();
    assertEquals(MatsimPtServicesWriterSettings.AWAIT_DEPARTURE_DEFAULT, settings.isAwaitDepartures());
  }

  /** Settings made with no arguments use the global country, in each of the nested intermodal settings too */
  @Test
  public void testSettingsMadeWithNoArguments() {
    assertEquals(CountryNames.GLOBAL, new MatsimNetworkWriterSettings().getCountry());
    var intermodal = new MatsimIntermodalWriterSettings();
    assertEquals(CountryNames.GLOBAL, intermodal.getNetworkSettings().getCountry());
    assertEquals(CountryNames.GLOBAL, intermodal.getZoningSettings().getCountry());
    assertEquals(CountryNames.GLOBAL, intermodal.getPtServicesSettings().getCountry());
  }
}
