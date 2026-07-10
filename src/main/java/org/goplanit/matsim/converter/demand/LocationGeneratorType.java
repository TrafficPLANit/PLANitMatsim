package org.goplanit.matsim.converter.demand;

/**
 * Defines the strategy used to allocate geographic locations (coordinates or links)
 * for activities in the MATSim plans writer.
 * * @author markr
 */
public enum LocationGeneratorType {
  /** Draw a random link within the zone, weighted by link length */
  ZONE_LINKS_DISTANCE_WEIGHTED,

  /** Fall back directly to the Zone's geometric centroid coordinates */
  ZONE_CENTROID
}
