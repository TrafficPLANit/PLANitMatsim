package org.goplanit.matsim.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.goplanit.matsim.converter.MatsimIntermodalWriterSettings;
import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterSettings;
import org.goplanit.utils.settings.SettingsCoverage;
import org.junit.jupiter.api.Test;

/**
 * Tests that the annotations of every MATSim writer settings class cover those settings: every method changing
 * settings is a key, is covered by one, or is marked as no key; every getter of nested settings is marked; every key
 * has a read; and every key a convenience is covered by exists
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

  /** Every key a convenience is covered by exists, a key of a part found through the key naming the part's file */
  @Test
  public void everyCoveringKeyExists() {
    assertEquals(List.of(), COVERAGE.findUnknownCoveringKeys());
  }
}
