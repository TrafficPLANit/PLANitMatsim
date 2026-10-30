package org.goplanit.matsim.util;

import org.goplanit.utils.mode.PredefinedModeType;
import java.util.List;
import java.util.Objects;

/**
 * Defines a versatile configuration rule that bounds a target primary vehicle mode
 * by a list of required preceding and succeeding connector modes.
 *
 * @author markr
 * @since 1.0.0
 */
public final class ModeChainCollapseRule {

  /** Default value for allowedFromMissingIfToPresent. */
  public static final boolean DEFAULT_ALLOWED_FROM_MISSING_IF_TO_PRESENT = false;

  /** Default value for allowedToMissingIfFromPresent. */
  public static final boolean DEFAULT_ALLOWED_TO_MISSING_IF_FROM_PRESENT = false;

  /** The primary core travel mode representing the main vehicle/line asset. */
  private final PredefinedModeType mainMode;

  /** The chronological sequence of preceding access or transfer connector modes required. */
  private final List<PredefinedModeType> allowedFromConnectors;

  /** The chronological sequence of succeeding egress or transfer connector modes required. */
  private final List<PredefinedModeType> allowedToConnectors;

  /** Flag indicating whether preceding access connectors are allowed to be missing if the to-connector is present. */
  private boolean allowedFromMissingIfToPresent;

  /** Flag indicating whether succeeding egress connectors are allowed to be missing if the from-connector is present. */
  private boolean allowedToMissingIfFromPresent;

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
    this(mainMode, allowedFromConnectors, allowedToConnectors, DEFAULT_ALLOWED_FROM_MISSING_IF_TO_PRESENT, DEFAULT_ALLOWED_TO_MISSING_IF_FROM_PRESENT);
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
  public List<PredefinedModeType> getAllowedFromConnectors() {
    return allowedFromConnectors;
  }

  /**
   * Access allowed to-connectors.
   * @return list of predefined mode types
   */
  public List<PredefinedModeType> getAllowedToConnectors() {
    return allowedToConnectors;
  }

  /**
   * Checks if from-connectors are allowed to be missing if to-connector is present.
   * @return true if allowed to be missing, false otherwise
   */
  public boolean isAllowedFromMissingIfToPresent() {
    return allowedFromMissingIfToPresent;
  }

  /**
   * set flag
   *
   * @param flag to set
   */
  public void setAllowedFromMissingIfToPresent(boolean flag) {
    this.allowedFromMissingIfToPresent = flag;
  }

  /**
   * Checks if to-connectors are allowed to be missing if from-connector is present.
   * @return true if allowed to be missing, false otherwise
   */
  public boolean isAllowedToMissingIfFromPresent() {
    return allowedToMissingIfFromPresent;
  }

  /**
   * set flag
   *
   * @param flag to set
   */
  public void setAllowedToMissingIfFromPresent(boolean flag) {
    this.allowedToMissingIfFromPresent = flag;
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