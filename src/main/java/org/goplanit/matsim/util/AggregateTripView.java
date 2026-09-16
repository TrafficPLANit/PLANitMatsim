package org.goplanit.matsim.util;

import org.goplanit.demands.discrete.tour.ActivitySchedule;
import org.goplanit.demands.discrete.tour.Tour;
import org.goplanit.demands.discrete.tour.TourImpl;
import org.goplanit.demands.discrete.trip.Trip;
import org.goplanit.demands.discrete.util.DirectionBound;
import org.goplanit.utils.id.IdAble;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.zoning.OdZone;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * A composite view that groups multiple underlying contiguous {@link Trip} segments
 * into a single unified virtual {@link Trip} implementation for aggregate demand models.
 * <p>
 * This maintains perfect chronological and spatial alignment for aggregate models by
 * dynamically drawing boundaries from the head and tail of the chain without mutating
 * the source PLANit memory model.
 * </p>
 *
 * @author markr
 */
public final class AggregateTripView implements Trip {

  /** The complete chronological sequence of collapsed trips forming this view. */
  private final List<Trip> collapsedTrips;

  /** The primary designated trip carrying the main mode/vehicle asset context. */
  private final Trip primaryVehicleTrip;

  /**
   * Constructs a new composite aggregate trip view chain.
   *
   * @param collapsedTrips the ordered list of all trips being merged (must not be empty)
   * @param primaryVehicleTrip the core vehicle trip providing the primary mode context
   * @throws IllegalArgumentException if the collapsedTrips list is empty or does not contain the primary trip
   */
  public AggregateTripView(List<Trip> collapsedTrips, Trip primaryVehicleTrip) {
    this.collapsedTrips = List.copyOf(
        Objects.requireNonNull(collapsedTrips, "Collapsed trips list cannot be null"));
    this.primaryVehicleTrip =
        Objects.requireNonNull(primaryVehicleTrip, "Primary vehicle trip cannot be null");

    if (this.collapsedTrips.isEmpty()) {
      throw new IllegalArgumentException("An aggregate trip view chain must contain at least one trip.");
    }
    if (!this.collapsedTrips.contains(primaryVehicleTrip)) {
      throw new IllegalArgumentException("The collapsed trips chain must include the designated primary vehicle trip.");
    }
  }

  /**
   * Accesses the first trip in the sequence chain.
   *
   * @return the head of the chain
   */
  private Trip getHead() {
    return collapsedTrips.get(0);
  }

  /**
   * Accesses the final trip in the sequence chain.
   *
   * @return the tail of the chain
   */
  private Trip getTail() {
    return collapsedTrips.get(collapsedTrips.size() - 1);
  }

  /**
   * Dynamic Start Time Extraction: Draws from the first leg to prevent timeline leaks.
   *
   * @return the absolute start time of the initial departure leg
   */
  @Override
  public LocalTime getStartTime() {
    return getHead().getStartTime();
  }

  /**
   * Dynamic Spatial Origin Extraction: Draws from the absolute beginning of the chain.
   *
   * @param useTourIfNotSet if true evaluate parent tour fallback steps
   * @return the entry zone of the initial leg
   */
  @Override
  public OdZone getOrigin(boolean useTourIfNotSet) {
    return getHead().getOrigin(useTourIfNotSet);
  }

  /**
   * Dynamic Spatial Destination Extraction: Draws from the absolute termination of the chain.
   *
   * @param useTourIfNotSet if true evaluate parent tour fallback steps
   * @return the exit zone of the final leg
   */
  @Override
  public OdZone getDestination(boolean useTourIfNotSet) {
    return getTail().getDestination(useTourIfNotSet);
  }

  /**
   * Standardizes the travel mode to match the primary transit or vehicle asset context.
   *
   * @return the dominant travel mode reference
   */
  @Override
  public Mode getMode() {
    return primaryVehicleTrip.getMode();
  }

  /**
   * Retrieves the raw list of all internal trip components encapsulated by this view.
   *
   * @return unmodifiable list of underlying trip segments
   */
  public List<Trip> getCollapsedTrips() {
    return collapsedTrips;
  }

  // --- Read-Only Functional Interface Implementations passing to Primary Context ---
  @Override public String getPurpose() { return primaryVehicleTrip.getPurpose(); }
  @Override public Tour getTour() { return primaryVehicleTrip.getTour(); }
  @Override public DirectionBound getDirection() { return primaryVehicleTrip.getDirection(); }
  @Override public ActivitySchedule getSchedule() { return null; }
  @Override public long getId() { return primaryVehicleTrip.getId(); }
  @Override public Class<? extends IdAble> getIdClass() { return primaryVehicleTrip.getIdClass(); }
  @Override public String getExternalId() { return primaryVehicleTrip.getExternalId(); }
  @Override public String getXmlId() { return primaryVehicleTrip.getXmlId(); }

  // --- Mutator Safety Exceptions for Immutable Views ---
  @Override public void setPurpose(String purpose) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public void setMode(Mode mode) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public void setTour(Tour tour) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public void setDirection(DirectionBound direction) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public void setStartTime(LocalTime startTime) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public void setOrigin(OdZone origin) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public void setDestination(OdZone destination) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public void setExternalId(String externalId) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public void setXmlId(String xmlId) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public long recreateManagedIds(IdGroupingToken tokenId) {
    throw new UnsupportedOperationException("Mutations forbidden on immutable view layers"); }
  @Override public AggregateTripView shallowClone() {
    return new AggregateTripView(this.collapsedTrips, this.primaryVehicleTrip); }
  @Override public AggregateTripView deepClone() {
    return shallowClone(); }
}
