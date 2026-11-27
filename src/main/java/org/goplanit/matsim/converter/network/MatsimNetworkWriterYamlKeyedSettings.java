package org.goplanit.matsim.converter.network;

import java.util.logging.Logger;

import org.goplanit.matsim.util.PlanitMatsimWriterModeMappingSettings;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The MATSim network writer settings a settings file can set: only the methods of its keys, each write with the read
 * of the same key. {@link MatsimNetworkWriterSettings} extends it with what code needs on top.
 * <p>
 * A network writer's settings file gives these keys beside those every MATSim writer takes and the modes, while the
 * functions filling a link's attributes, which no file can hold, stay on the class code uses
 * </p>
 *
 * @author markr
 */
public abstract class MatsimNetworkWriterYamlKeyedSettings extends PlanitMatsimWriterModeMappingSettings {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimNetworkWriterYamlKeyedSettings.class.getCanonicalName());

  /** when true, a separate file with the detailed geometry of each link is written, for the VIA viewer, which otherwise
   * only shows each link's start and end node. Default false
   */
  private boolean generateDetailedLinkGeometryFile = DEFAULT_GENERATE_DETAILED_LINK_GEOMETRY;

  /** When true a link's speed limit is the minimum of its physical and mode speed limit, which matters when only modes
   * slower than the speed limit use it, such as buses in a bus only network. When false the physical speed limit is
   * used
   */
  private boolean restrictLinkSpeedBySupportedModes = DEFAULT_RESTRICT_SPEED_LIMIT_BY_SUPPORTED_MODE;

  /** Minimum length in meters written for any MATSim link. A length can be zero, for example for a transfer connectoid
   * whose stop sits on its access node, or a physical link whose geometry collapsed. MATSim derives travel times by
   * dividing by the length, so a zero length is unusable and is raised to this minimum. PLANit lengths are in km, so
   * apply it only after conversion to meters.
   */
  private double minimumLinkLengthMeters = DEFAULT_MINIMUM_LINK_LENGTH_METERS;

  /** reduce the capacity of each MATSim link by the effective capacity factor of its link segment, see
   * {@link #setReduceLinkExitCapacityByEffectiveCapacityFactor(boolean)} */
  private boolean reduceLinkExitCapacityByEffectiveCapacityFactor =
      DEFAULT_REDUCE_LINK_EXIT_CAPACITY_BY_EFFECTIVE_CAPACITY_FACTOR;

  /**
   * Constructor
   *
   * @param outputDirectory to use
   * @param outputFileName to use
   * @param countryName to use
   */
  protected MatsimNetworkWriterYamlKeyedSettings(String outputDirectory, String outputFileName, String countryName) {
    super(outputDirectory, outputFileName, countryName);
  }

  /**
   * Default setting for reducing the capacity of each MATSim link by the effective capacity factor of its link segment
   * is false
   */
  public static final boolean DEFAULT_REDUCE_LINK_EXIT_CAPACITY_BY_EFFECTIVE_CAPACITY_FACTOR = false;

  /**
   * Default setting for generating detailed link geometry file is false
   */
  public static final Boolean DEFAULT_GENERATE_DETAILED_LINK_GEOMETRY = false;

  /**
   * Default setting for restricting a link's max speed by its supported mode max speeds if more restricting
   */
  public static final Boolean DEFAULT_RESTRICT_SPEED_LIMIT_BY_SUPPORTED_MODE = false;

  /**
   * Default minimum link length in meters, a metre being short enough not to distort routing while still giving
   * MATSim a usable travel time
   */
  public static final double DEFAULT_MINIMUM_LINK_LENGTH_METERS = 1.0;

  /** Check if a detailed geometry file is generated
   *
   * @return true when active, false otherwise
   */
  @SettingsRead(key = "generateDetailedLinkGeometryFile")
  public boolean isGenerateDetailedLinkGeometryFile() {
    return generateDetailedLinkGeometryFile;
  }

  /** Set the choice for whether a detailed geometry file for each link is created.
   * This geometry is extracted from the PLANit link geometry.
   *
   * @param generateDetailedLinkGeometryFile flag indicating to generate geometry file or not
   */
  @SettingsWrite(key = "generateDetailedLinkGeometryFile", level = SettingsKeyLevel.UNCOMMON,
      description = "Also write a file with the detailed geometry of each link, taken from its PLANit geometry")
  public void setGenerateDetailedLinkGeometryFile(boolean generateDetailedLinkGeometryFile) {
    this.generateDetailedLinkGeometryFile = generateDetailedLinkGeometryFile;
  }

  /** Check if link speed is to be restricted by the supported modes' max speed (if more restrictive)
   *
   * @return true when active, false otherwise
   */
  @SettingsRead(key = "restrictLinkSpeedBySupportedModes")
  public boolean isRestrictLinkSpeedBySupportedModes() {
    return restrictLinkSpeedBySupportedModes;
  }

  /**
   * Set if link speed is to be restricted by the supported modes' max speed (if more restrictive)
   *
   * @param restrictLinkSpeedBySupportedModes flag to set
   */
  @SettingsWrite(key = "restrictLinkSpeedBySupportedModes", level = SettingsKeyLevel.UNCOMMON,
      description = "Limit the speed written for each link to the lowest maximum speed of the modes allowed on it, "
          + "when that is lower")
  public void setRestrictLinkSpeedBySupportedModes(boolean restrictLinkSpeedBySupportedModes) {
    this.restrictLinkSpeedBySupportedModes = restrictLinkSpeedBySupportedModes;
  }

  /** Collect the minimum length in meters written for any MATSim link
   *
   * @return minimum link length in meters
   */
  @SettingsRead(key = "minimumLinkLengthMeters")
  public double getMinimumLinkLengthMeters() {
    return minimumLinkLengthMeters;
  }

  /** Set the minimum length in meters written for any MATSim link. Provided in meters rather than km to match the
   * unit MATSim itself uses for a link length
   *
   * @param minimumLinkLengthMeters to use
   */
  @SettingsWrite(key = "minimumLinkLengthMeters", level = SettingsKeyLevel.UNCOMMON,
      description = "The shortest length written for a MATSim link, in metres")
  public void setMinimumLinkLengthMeters(double minimumLinkLengthMeters) {
    this.minimumLinkLengthMeters = minimumLinkLengthMeters;
  }

  /** Check if the capacity of each MATSim link is reduced by the effective capacity factor of its link segment
   *
   * @return true when reduced, false otherwise
   */
  @SettingsRead(key = "reduceLinkExitCapacityByEffectiveCapacityFactor")
  public boolean isReduceLinkExitCapacityByEffectiveCapacityFactor() {
    return reduceLinkExitCapacityByEffectiveCapacityFactor;
  }

  /** Set if the capacity of each MATSim link, the rate at which vehicles can leave it, is reduced by the effective
   * capacity factor of its link segment: that of its approach of an intersection, of any control type, or of its
   * internal segment with a signal, see
   * {@link org.goplanit.utils.network.layer.macroscopic.intersection.Intersection#getEffectiveCapacityFactorOf}. For
   * example an approach of 3,600 pcu/h with a green ratio of 0.44 is written with 1,584 pcu/h. Values on movements are
   * not used: a MATSim link has one capacity for all the traffic on it. Off by default
   *
   * @param reduceLinkExitCapacityByEffectiveCapacityFactor flag to set
   */
  @SettingsWrite(key = "reduceLinkExitCapacityByEffectiveCapacityFactor", level = SettingsKeyLevel.UNCOMMON,
      description = "Reduce the capacity of each MATSim link, the rate at which vehicles can leave it, by the "
          + "effective capacity factor of its link segment at an intersection, such as 3,600 pcu/h with a green ratio "
          + "of 0.44 written as 1,584 pcu/h")
  public void setReduceLinkExitCapacityByEffectiveCapacityFactor(
      boolean reduceLinkExitCapacityByEffectiveCapacityFactor) {
    this.reduceLinkExitCapacityByEffectiveCapacityFactor = reduceLinkExitCapacityByEffectiveCapacityFactor;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    super.logSettings(level);
    LOGGER.info(LoggingUtils.settingsValue("Generate detailed geometry", isGenerateDetailedLinkGeometryFile(), level));
    LOGGER.info(LoggingUtils.settingsValue(
        "Restrict link speed by supported modes", isRestrictLinkSpeedBySupportedModes(), level));
    LOGGER.info(LoggingUtils.settingsValue("Minimum link length (m)", getMinimumLinkLengthMeters(), level));
    LOGGER.info(LoggingUtils.settingsValue("Reduce link exit capacity by effective capacity factor",
        isReduceLinkExitCapacityByEffectiveCapacityFactor(), level));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    setFileName(DEFAULT_NETWORK_FILE_NAME);
    this.generateDetailedLinkGeometryFile = DEFAULT_GENERATE_DETAILED_LINK_GEOMETRY;
    this.restrictLinkSpeedBySupportedModes = DEFAULT_RESTRICT_SPEED_LIMIT_BY_SUPPORTED_MODE;
    this.minimumLinkLengthMeters = DEFAULT_MINIMUM_LINK_LENGTH_METERS;
    this.reduceLinkExitCapacityByEffectiveCapacityFactor =
        DEFAULT_REDUCE_LINK_EXIT_CAPACITY_BY_EFFECTIVE_CAPACITY_FACTOR;
  }
}
