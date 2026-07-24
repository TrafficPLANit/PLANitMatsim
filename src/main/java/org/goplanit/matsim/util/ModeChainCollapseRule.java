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

  /** The primary core travel mode representing the main vehicle/line asset. */
  private final PredefinedModeType mainMode;

  /** The chronological sequence of preceding access or transfer connector modes required. */
  private final List<PredefinedModeType> allowedFromConnectors;

  /** The chronological sequence of succeeding egress or transfer connector modes required. */
  private final List<PredefinedModeType> allowedToConnectors;

  /**
   * Constructor.
   *
   * @param mainMode the primary mode carrying the vehicle profile (e.g. BUS, TRAIN)
   * @param allowedFromConnectors the preceding structural connectors (e.g. PEDESTRIAN)
   * @param allowedToConnectors the succeeding structural connectors (e.g. PEDESTRIAN)
   */
  public ModeChainCollapseRule(
      PredefinedModeType mainMode,
      List<PredefinedModeType> allowedFromConnectors,
      List<PredefinedModeType> allowedToConnectors) {
    this.mainMode = Objects.requireNonNull(mainMode,
        "Main mode cannot be null");
    this.allowedFromConnectors = List.copyOf(Objects.requireNonNull(allowedFromConnectors,
        "From-connectors cannot be null"));
    this.allowedToConnectors = List.copyOf(Objects.requireNonNull(allowedToConnectors,
        "To-connectors cannot be null"));
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
}
