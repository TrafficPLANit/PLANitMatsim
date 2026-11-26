package org.goplanit.matsim.util;

import org.goplanit.utils.settings.SettingsSchema;

/**
 * The layout of the MATSim writer settings sources: the header {@code planitMatsimWriterSettings: 1} and every key of
 * a writer at the top level, or, for the intermodal writer, the settings files of the writers it holds
 * <p>
 * For example, a source of a zoning writer:
 * </p>
 * <pre>
 * planitMatsimWriterSettings: 1
 * outputDirectory: out
 * destinationCrs: EPSG:28356
 * writeTransferZoneAccess: false
 * </pre>
 * <p>
 * The header and the keys are on the settings classes: the header and the keys every MATSim writer takes on
 * {@link PlanitMatsimWriterSettings}, and those of one writer on its own settings. The destination CRS is given by its
 * code and the decimal format by its pattern. One schema serves every MATSim writer, the discrete demands writer
 * included; a key another writer takes is unknown to a writer that does not.
 * </p>
 *
 * @author markr
 */
public final class MatsimWriterSettingsSchema {

  /** Not instantiable, everything is static */
  private MatsimWriterSettingsSchema() {
  }

  /** the schema of every MATSim writer settings source */
  public static final SettingsSchema SCHEMA = new SettingsSchema(PlanitMatsimWriterSettings.class);
}
