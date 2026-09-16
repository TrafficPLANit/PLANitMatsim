package org.goplanit.matsim.util;

import java.util.Arrays;
import java.util.Optional;

/**
 * Standard public enum representing the core built-in transport modes natively supported
 * and recognized within the MATSim simulation framework config and plan schemas.
 */
public enum MatsimBuiltInMode {

  /** Standard passenger car or private motorized vehicle simulated on physical road links */
  CAR("car"),

  /** Public transport leg (buses, trains, trams) handled via transit schedules */
  PT("pt"),

  /** bus */
  BUS("bus"),

  /** train */
  TRAIN("train"),

  /** Native pedestrian walk mode, typically handled via teleportation or sidewalk layers */
  WALK("walk"),

  /** Native cycling mode with distinct speed profiles and congestion scaling */
  BIKE("bike"),

  /** Standard freight or heavy goods vehicle mode */
  FREIGHT("freight"),

  /** Specialized ride-sharing or demand-responsive transport mode */
  DRT("drt");

  private final String value;

  /**
   * Constructor.
   *
   * @param value the exact string value matching MATSim specifications
   */
  MatsimBuiltInMode(String value) {
    this.value = value;
  }

  /**
   * Access the raw string value used in MATSim XML outputs.
   *
   * @return the string value representation
   */
  public String getValue() {
    return this.value;
  }

  /**
   * Helper factory to safely parse a raw string value into the matching enum instance.
   *
   * @param value the raw case-insensitive string value to search for
   * @return an Optional wrapping the matching enum token if discovered
   */
  public static Optional<MatsimBuiltInMode> fromValue(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String normalized = value.trim().toLowerCase();
    return Arrays.stream(values())
        .filter(mode -> mode.getValue().equals(normalized))
        .findFirst();
  }

  /**
   * Check if it is a type of pt or the pt group itself
   *
   * @return true when bus, train, or pt
   */
  public boolean isTypeOfPt(){
    switch (this){
      case PT:
      case BUS:
      case TRAIN:
        return true;
      default:
        return false;
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return this.value;
  }
}

