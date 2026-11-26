package org.goplanit.matsim.util;

import java.util.Arrays;
import java.util.Optional;

import org.goplanit.utils.mode.PredefinedModeType;

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

  /** Specialized ride-sharing or demand-responsive transport mode. Its vehicles are drawn from a fleet
   * specification and driven by agents the simulation creates rather than persons in the population, so they occupy
   * the network and add to congestion. Suited to a commercial service whose driver is not among the travellers */
  DRT("drt"),

  /** Passenger in a vehicle driven by another person in the population. Teleported using car travel times by
   * default, placing no vehicle of its own on the network, so the driver's vehicle is the only one counted */
  RIDE("ride");

  private final String value;

  /**
   * Constructor.
   *
   * @param value the exact string value matching MATSim specifications
   */
  private MatsimBuiltInMode(String value) {
    this.value = value;
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
   * The default MATSim built-in mode of a PLANit PredefinedModeType, written when no MATSim mode is given for it.
   * <p>
   * Mapping behavior details:
   * </p>
   * <ul>
   *   <li><strong>BUS:</strong> Maps to {@link #BUS} when disaggregate transit modes are enabled, otherwise falls back
   *       to {@link #PT}.</li>
   *   <li><strong>SUBWAY, TRAM, LIGHTRAIL, TRAIN:</strong> Map to {@link #TRAIN} when disaggregate transit modes are
   *       enabled (grouping rail/fixed-guideway modes under train in standard practice), otherwise fall back to
   *       {@link #PT}.</li>
   *   <li><strong>FERRY:</strong> Maps to {@link #BUS} when disaggregate transit modes are enabled (treating waterborne
   *       public transport under road-based equivalents for core simulation compatibility), otherwise falls back to
   *       {@link #PT}.</li>
   *   <li><strong>PEDESTRIAN:</strong> Maps directly to {@link #WALK}.</li>
   *   <li><strong>BICYCLE:</strong> Maps directly to {@link #BIKE}.</li>
   *   <li><strong>GOODS_VEHICLE, HEAVY_GOODS_VEHICLE, LARGE_HEAVY_GOODS_VEHICLE:</strong> Map to {@link #FREIGHT}.</li>
   *   <li><strong>TAXI, RIDE_SHARE:</strong> Map to {@link #DRT} (Demand-Responsive Transport).</li>
   *   <li><strong>Default:</strong> Any unrecognised or private motorized modes fall back to {@link #CAR}.</li>
   * </ul>
   *
   * @param planitMode the PLANit {@link PredefinedModeType} to map
   * @param useDisaggregateTransitModes true for the disaggregated defaults of the public transport modes (bus, train),
   *     false for pt
   * @return the corresponding MATSim built-in mode
   */
  public static MatsimBuiltInMode getDefaultForPlanitMode(
      PredefinedModeType planitMode, boolean useDisaggregateTransitModes) {
    switch (planitMode) {
      case FERRY:
      case BUS:
        return useDisaggregateTransitModes ? BUS : PT;
      case SUBWAY:
      case TRAIN:
      case TRAM:
      case LIGHTRAIL:
        return useDisaggregateTransitModes ? TRAIN : PT;
      case PEDESTRIAN:
        return WALK;
      case BICYCLE:
        return BIKE;
      case GOODS_VEHICLE:
      case HEAVY_GOODS_VEHICLE:
      case LARGE_HEAVY_GOODS_VEHICLE:
        return FREIGHT;
      case TAXI:
      case RIDE_SHARE:
        return DRT;
      default:
        return CAR;
    }
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

