package org.goplanit.matsim.util;

import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

import java.util.List;
import java.util.Objects;

/**
 * A rule naming a main mode with the connector modes required before and after it.
 * <p>
 * In a settings file a rule is a value of the discrete demands writer's {@code modeCollapseRules} key, under its main
 * mode, such as {@code TRAIN: {fromConnectors: [PEDESTRIAN], toConnectors: [PEDESTRIAN]}}; what it leaves out keeps
 * the defaults of a rule made without arguments.
 * </p>
 *
 * @author markr
 * @since 1.0.0
 */
public final class ModeChainCollapseRule {

  /** The primary core travel mode representing the main vehicle/line asset. */
  private final PredefinedModeType mainMode;

  /** The chronological sequence of preceding access or transfer connector modes required. */
  private List<PredefinedModeType> allowedFromConnectors;

  /** The chronological sequence of succeeding egress or transfer connector modes required. */
  private List<PredefinedModeType> allowedToConnectors;

  /** Flag indicating whether preceding access connectors are allowed to be missing if the to-connector is present. */
  private boolean allowedFromMissingIfToPresent;

  /** Flag indicating whether succeeding egress connectors are allowed to be missing if the from-connector is present. */
  private boolean allowedToMissingIfFromPresent;

  /**
   * Copy constructor, giving a full copy: the connector lists cannot be changed, so the copy shares them
   *
   * @param other rule to copy
   */
  private ModeChainCollapseRule(ModeChainCollapseRule other) {
    this.mainMode = other.mainMode;
    this.allowedFromConnectors = other.allowedFromConnectors;
    this.allowedToConnectors = other.allowedToConnectors;
    this.allowedFromMissingIfToPresent = other.allowedFromMissingIfToPresent;
    this.allowedToMissingIfFromPresent = other.allowedToMissingIfFromPresent;
  }

  /** Default value for allowedFromMissingIfToPresent. */
  public static final boolean DEFAULT_ALLOWED_FROM_MISSING_IF_TO_PRESENT = false;

  /** Default value for allowedToMissingIfFromPresent. */
  public static final boolean DEFAULT_ALLOWED_TO_MISSING_IF_FROM_PRESENT = false;

  /**
   * Constructor with explicit control flags for missing connectors.
   *
   * @param mainMode the primary mode carrying the vehicle profile (e.g. BUS, TRAIN)
   * @param allowedFromConnectors the preceding structural connectors (e.g. PEDESTRIAN)
   * @param allowedToConnectors the succeeding structural connectors (e.g. PEDESTRIAN)
   * @param allowedFromMissingIfToPresent true if access can be missing when to-connector is present, false otherwise
   * @param allowedToMissingIfFromPresent true if egress can be missing when from-connector is present, false otherwise
   */
  public ModeChainCollapseRule(
      PredefinedModeType mainMode,
      List<PredefinedModeType> allowedFromConnectors,
      List<PredefinedModeType> allowedToConnectors,
      boolean allowedFromMissingIfToPresent,
      boolean allowedToMissingIfFromPresent) {
    this.mainMode = Objects.requireNonNull(mainMode,
        "Main mode cannot be null");
    this.allowedFromConnectors = List.copyOf(Objects.requireNonNull(allowedFromConnectors,
        "From-connectors cannot be null"));
    this.allowedToConnectors = List.copyOf(Objects.requireNonNull(allowedToConnectors,
        "To-connectors cannot be null"));
    this.allowedFromMissingIfToPresent = allowedFromMissingIfToPresent;
    this.allowedToMissingIfFromPresent = allowedToMissingIfFromPresent;
  }

  /**
   * Constructor defaulting missing connector flags to standard defaults.
   *
   * @param mainMode the primary mode carrying the vehicle profile (e.g. BUS, TRAIN)
   * @param allowedFromConnectors the preceding structural connectors (e.g. PEDESTRIAN)
   * @param allowedToConnectors the succeeding structural connectors (e.g. PEDESTRIAN)
   */
  public ModeChainCollapseRule(
      PredefinedModeType mainMode,
      List<PredefinedModeType> allowedFromConnectors,
      List<PredefinedModeType> allowedToConnectors) {
    this(mainMode, allowedFromConnectors, allowedToConnectors, DEFAULT_ALLOWED_FROM_MISSING_IF_TO_PRESENT,
        DEFAULT_ALLOWED_TO_MISSING_IF_FROM_PRESENT);
  }

  /**
   * Constructor of a rule filled in afterwards, as from a settings file: no connectors and the flags at their defaults.
   * It has no main mode, the main mode being the key the rule is stored under
   */
  public ModeChainCollapseRule() {
    this.mainMode = null;
    this.allowedFromConnectors = List.of();
    this.allowedToConnectors = List.of();
    this.allowedFromMissingIfToPresent = DEFAULT_ALLOWED_FROM_MISSING_IF_TO_PRESENT;
    this.allowedToMissingIfFromPresent = DEFAULT_ALLOWED_TO_MISSING_IF_FROM_PRESENT;
  }

  /**
   * Access the main mode.
   * @return main predefined mode type
   */
  public PredefinedModeType getMainMode() {
    return mainMode;
  }

  /**
   * Access allowed from-connectors.
   * @return list of predefined mode types
   */
  @SettingsRead(key = "fromConnectors")
  public List<PredefinedModeType> getAllowedFromConnectors() {
    return allowedFromConnectors;
  }

  /**
   * Access allowed to-connectors.
   * @return list of predefined mode types
   */
  @SettingsRead(key = "toConnectors")
  public List<PredefinedModeType> getAllowedToConnectors() {
    return allowedToConnectors;
  }

  /**
   * Set the modes allowed before the main mode, in order
   *
   * @param allowedFromConnectors the modes
   */
  @SettingsWrite(key = "fromConnectors", level = SettingsKeyLevel.STANDARD,
      description = "The modes of the trips before a trip by the main mode, in order, merged into its leg")
  public void setAllowedFromConnectors(List<PredefinedModeType> allowedFromConnectors) {
    this.allowedFromConnectors = List.copyOf(Objects.requireNonNull(allowedFromConnectors,
        "From-connectors cannot be null"));
  }

  /**
   * Set the modes allowed after the main mode, in order
   *
   * @param allowedToConnectors the modes
   */
  @SettingsWrite(key = "toConnectors", level = SettingsKeyLevel.STANDARD,
      description = "The modes of the trips after a trip by the main mode, in order, merged into its leg")
  public void setAllowedToConnectors(List<PredefinedModeType> allowedToConnectors) {
    this.allowedToConnectors = List.copyOf(Objects.requireNonNull(allowedToConnectors,
        "To-connectors cannot be null"));
  }

  /**
   * Checks if from-connectors are allowed to be missing if to-connector is present.
   * @return true if allowed to be missing, false otherwise
   */
  @SettingsRead(key = "allowedFromMissingIfToPresent")
  public boolean isAllowedFromMissingIfToPresent() {
    return allowedFromMissingIfToPresent;
  }

  /**
   * set flag
   *
   * @param flag to set
   */
  @SettingsWrite(key = "allowedFromMissingIfToPresent", level = SettingsKeyLevel.UNCOMMON,
      description = "The trips before may be missing when those after are there")
  public void setAllowedFromMissingIfToPresent(boolean flag) {
    this.allowedFromMissingIfToPresent = flag;
  }

  /**
   * Checks if to-connectors are allowed to be missing if from-connector is present.
   * @return true if allowed to be missing, false otherwise
   */
  @SettingsRead(key = "allowedToMissingIfFromPresent")
  public boolean isAllowedToMissingIfFromPresent() {
    return allowedToMissingIfFromPresent;
  }

  /**
   * set flag
   *
   * @param flag to set
   */
  @SettingsWrite(key = "allowedToMissingIfFromPresent", level = SettingsKeyLevel.UNCOMMON,
      description = "The trips after may be missing when those before are there")
  public void setAllowedToMissingIfFromPresent(boolean flag) {
    this.allowedToMissingIfFromPresent = flag;
  }

  /**
   * A full copy of this rule: its main mode, connectors and flags. The rule holds no entity with an id, so the copy
   * shares nothing that can be changed
   *
   * @return the copy
   */
  public ModeChainCollapseRule deepClone() {
    return new ModeChainCollapseRule(this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return String.format(
        "ModeChainCollapseRule{mainMode=%s, allowedFromConnectors=%s, allowedToConnectors=%s, " +
            "allowedFromMissingIfToPresent=%b, allowedToMissingIfFromPresent=%b}",
        mainMode, allowedFromConnectors, allowedToConnectors,
        allowedFromMissingIfToPresent, allowedToMissingIfFromPresent);
  }
}