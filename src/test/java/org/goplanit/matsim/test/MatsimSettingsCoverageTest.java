package org.goplanit.matsim.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.goplanit.matsim.converter.MatsimIntermodalWriterSettings;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterSettings;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterYamlKeyedSettings;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterYamlKeyedSettings;
import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingYamlKeyedSettings;
import org.goplanit.utils.settings.SettingsCoverage;
import org.junit.jupiter.api.Test;

/**
 * Tests that the annotations of every MATSim writer settings class cover those settings: every method changing
 * settings is a key, is covered by one, or is marked as no key; every getter of nested settings is marked; every key
 * has a read; and every key a convenience is covered by exists. The settings classes whose keys sit in a YAML keyed
 * class keep them apart
 * <p>
 * The intermodal writer settings reach the network, zoning and public transport services settings through the files
 * they name; the discrete demands settings, with their mode collapse rules, are reached on their own.
 * </p>
 *
 * @author markr
 */
public class MatsimSettingsCoverageTest {

  /** the coverage of every settings class the MATSim writers reach */
  private static final SettingsCoverage COVERAGE =
      SettingsCoverage.of(MatsimIntermodalWriterSettings.class, MatsimDiscreteDemandsWriterSettings.class);

  /** each settings class with keys and methods for code, and the YAML keyed class it extends that holds its keys */
  private static final Map<Class<?>, Class<?>> YAML_KEYED_CLASS_BY_SETTINGS_CLASS = Map.of(
      PlanitMatsimWriterModeMappingSettings.class, PlanitMatsimWriterModeMappingYamlKeyedSettings.class,
      MatsimNetworkWriterSettings.class, MatsimNetworkWriterYamlKeyedSettings.class,
      MatsimDiscreteDemandsWriterSettings.class, MatsimDiscreteDemandsWriterYamlKeyedSettings.class);

  /** Every method changing settings has exactly one marker */
  @Test
  public void everyMethodChangingSettingsHasExactlyOneMarker() {
    assertEquals(List.of(), COVERAGE.findUnmarkedMethodsChangingSettings());
  }

  /** Every getter of nested settings is a section, a file reference, the read of a key, or marked as no key */
  @Test
  public void everyGetterOfNestedSettingsIsMarked() {
    assertEquals(List.of(), COVERAGE.findUnmarkedGettersOfNestedSettings());
  }

  /** Every key has a read of the same key, so settings can be written out */
  @Test
  public void everyKeyHasARead() {
    assertEquals(List.of(), COVERAGE.findKeysWithoutRead());
  }

  /**
   * Every key a convenience is covered by exists, a key of nested settings found through the key naming their file
   */
  @Test
  public void everyCoveringKeyExists() {
    assertEquals(List.of(), COVERAGE.findUnknownCoveringKeys());
  }

  /**
   * Each settings class with a YAML keyed class extends it directly, declares and overrides no key or section, and
   * its YAML keyed class declares only keys and sections, its reset and its logSettings(int)
   */
  @Test
  public void yamlKeyedClassesHoldOnlyTheKeys() {
    assertEquals(List.of(), SettingsCoverage.findSettingsClassesNotExtendingTheirYamlKeyedClassDirectly(
        YAML_KEYED_CLASS_BY_SETTINGS_CLASS));
    assertEquals(List.of(),
        SettingsCoverage.findKeysAndSectionsDeclaredOnSettingsClasses(YAML_KEYED_CLASS_BY_SETTINGS_CLASS));
    assertEquals(List.of(),
        SettingsCoverage.findKeysAndSectionsOverriddenBySettingsClasses(YAML_KEYED_CLASS_BY_SETTINGS_CLASS));
    assertEquals(List.of(),
        SettingsCoverage.findMethodsOfYamlKeyedClassesNotKeysOrSections(YAML_KEYED_CLASS_BY_SETTINGS_CLASS));
  }
}
