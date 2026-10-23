package org.goplanit.matsim.util;

import org.goplanit.demands.discrete.person.Person;
import org.goplanit.demands.discrete.tour.ActivitySchedule;
import org.goplanit.demands.discrete.tour.ParticipantTour;
import org.goplanit.demands.discrete.tour.ScheduleElement;
import org.goplanit.demands.discrete.tour.Tour;
import org.goplanit.demands.discrete.tour.TourParticipantRole;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.zoning.OdZone;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A non-destructive wrapper decorator view for a {@link Tour} instance to support
 * nested timeline schedule collapsing without mutating the underlying PLANit memory model
 * or triggering cloned tracking identity mismatches.
 * <p>
 * A tour is not itself a schedule element, a person's participation in it is, so this view is not placed on a
 * schedule directly. It is reached through {@link AggregateParticipantTourView}, whose delegation to
 * {@link ParticipantTour#getTour()} is what carries the collapsed schedule to the participation
 * </p>
 *
 * @author markr
 * @since 1.0.0
 */
public final class AggregateTourView implements Tour {

  /** The pristine original underlying tour reference being decorated. */
  private final Tour underlyingTour;

  /** The custom collapsed aggregate activity schedule tracking timeline layer. */
  private final ActivitySchedule collapsedSchedule;

  /**
   * Constructs a new virtual aggregate tour view.
   *
   * @param underlyingTour the real source tour to wrap
   * @param collapsedSchedule the pre-collapsed virtual schedule to substitute
   * @throws NullPointerException if either parameter is null
   */
  public AggregateTourView(Tour underlyingTour, ActivitySchedule collapsedSchedule) {
    this.underlyingTour = Objects.requireNonNull(underlyingTour, "Underlying tour cannot be null");
    this.collapsedSchedule = Objects.requireNonNull(collapsedSchedule, "Collapsed schedule cannot be null");
  }

  /**
   * Intercepts and returns the customized aggregated schedule timeline layout.
   *
   * @return the collapsed aggregate activity schedule
   */
  @Override
  public ActivitySchedule getSchedule() {
    return this.collapsedSchedule;
  }

  /**
   * Exposes whether this virtual view tracks an internal schedule timeline layout.
   *
   * @return true
   */
  @Override
  public boolean hasSchedule() {
    return true;
  }

  // --- Read-Only Functional Interface Implementations Delegated to Source ---
  @Override public List<ParticipantTour> getParticipantTours() { return underlyingTour.getParticipantTours(); }
  @Override public OdZone getOrigin() { return underlyingTour.getOrigin(); }
  @Override public OdZone getDestination() { return underlyingTour.getDestination(); }
  @Override public String getPurpose() { return underlyingTour.getPurpose(); }
  @Override public Tour getParentTour() { return underlyingTour.getParentTour(); }
  @Override public LocalTime getStartTime() { return underlyingTour.getStartTime(); }
  @Override public LocalTime getEndTime() { return underlyingTour.getEndTime(); }
  @Override public long getId() { return underlyingTour.getId(); }
  @Override public Class<? extends Tour> getIdClass() { return Tour.TOUR_ID_CLASS; }
  @Override public String getExternalId() { return underlyingTour.getExternalId(); }
  @Override public String getXmlId() { return underlyingTour.getXmlId(); }

  /**
   * Test the collapsed schedule against the given predicate. A tour is no longer a schedule element, so this no
   * longer overrides {@link ScheduleElement#testNested}, but it is kept for callers holding this view directly
   *
   * @param predicate to test against
   * @return true when the predicate holds for the collapsed schedule or anything nested in it
   */
  public boolean testNested(Predicate<ScheduleElement> predicate) {
    return collapsedSchedule.testNested(predicate);
  }

  @Override
  public Mode getOutboundMode() {
    return collapsedSchedule.getOutboundMode(); }
  @Override
  public Mode getInboundMode() { return collapsedSchedule.getInboundMode(); }

  // --- Mutator Safety Exceptions for Immutable Mock Views ---
  @Override public ParticipantTour addParticipant(Person person, TourParticipantRole role) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public boolean removeParticipant(ParticipantTour participantTour) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setOrigin(OdZone origin) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setDestination(OdZone destination) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setOriginDestination(OdZone origin, OdZone destination) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setPurpose(String purpose) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setParentTour(Tour parent) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setStartTime(LocalTime startTime) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setEndTime(LocalTime endTime) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setSchedule(ActivitySchedule schedule) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setExternalId(String externalId) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public void setXmlId(String xmlId) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public long recreateManagedIds(IdGroupingToken tokenId) {
    throw new UnsupportedOperationException("Mutations forbidden on aggregate mock view layers"); }
  @Override public AggregateTourView shallowClone() {
    return new AggregateTourView(this.underlyingTour, this.collapsedSchedule); }
  @Override public AggregateTourView deepClone() { return shallowClone(); }
}
