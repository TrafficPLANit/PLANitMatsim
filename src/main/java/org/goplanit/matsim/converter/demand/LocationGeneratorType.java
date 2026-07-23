package org.goplanit.matsim.converter.demand;

/**
 * Defines the strategy used to allocate geographic locations (coordinates or links)
 * for activities in the MATSim plans writer.
 * * @author markr
 */
public enum LocationGeneratorType {
  /** Draw a link within the zone, weighted by link length, fallback if no links in zone is the geometry's
   * dynamically generated centroid */
  ZONE_LINKS_DISTANCE_WEIGHTED,

  /** Fall back directly to the Zone's geometric centroid coordinates if available, fallback is the dynamically
   * generated centroid of the zone's geometry if available */
  ZONE_CENTROID
}
