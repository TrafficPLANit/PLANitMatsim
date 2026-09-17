package org.goplanit.matsim.util;

import org.goplanit.demands.discrete.person.Person;
import org.goplanit.demands.discrete.tour.ParticipantTour;
import org.goplanit.demands.discrete.tour.Tour;
import org.goplanit.demands.discrete.tour.TourParticipantRole;

import java.util.Objects;

/**
 * A non-destructive wrapper decorator view for a {@link ParticipantTour}, pairing the original participation with an
 * {@link AggregateTourView} of the tour it is a participation in, so a collapsed schedule can be put in a person's
 * schedule without touching the underlying PLANit memory model.
 * <p>
 * Only the tour is substituted. Everything a participation exposes beyond who takes part and in what role is defined
 * in terms of {@link ParticipantTour#getTour()}, so the collapsed schedule, the modes derived from it and the times
 * follow from replacing the tour alone
 * </p>
 *
 * @author markr
 */
public final class AggregateParticipantTourView implements ParticipantTour {

  /** The pristine original underlying participation being decorated */
  private final ParticipantTour underlyingParticipation;

  /** The view of the tour carrying the collapsed schedule */
  private final AggregateTourView aggregateTour;

  /**
   * Constructs a new virtual aggregate participation view
   *
   * @param underlyingParticipation the real source participation to wrap
   * @param aggregateTour the view of the tour holding the pre-collapsed schedule
   * @throws NullPointerException if either parameter is null
   */
  public AggregateParticipantTourView(ParticipantTour underlyingParticipation, AggregateTourView aggregateTour) {
    this.underlyingParticipation =
        Objects.requireNonNull(underlyingParticipation, "Underlying participation cannot be null");
    this.aggregateTour = Objects.requireNonNull(aggregateTour, "Aggregate tour view cannot be null");
  }

  /**
   * The tour as seen through its collapsed schedule rather than the underlying one
   *
   * @return aggregate tour view
   */
  @Override
  public Tour getTour() {
    return aggregateTour;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Person getPerson() {
    return underlyingParticipation.getPerson();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TourParticipantRole getRole() {
    return underlyingParticipation.getRole();
  }
}
