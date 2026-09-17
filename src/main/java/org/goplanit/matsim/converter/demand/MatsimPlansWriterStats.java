package org.goplanit.matsim.converter.demand;

import org.goplanit.utils.misc.LoggingUtils;

import java.util.Map;
import java.util.TreeMap;

/**
 * Collects statistics during MATSim plans writing.
 */
public class MatsimPlansWriterStats {

  /**
   * Constructor.
   */
  public MatsimPlansWriterStats() {}

  /**
   * Number of persons processed.
   */
  private long personsProcessed;

  /**
   * Number of persons for which a MATSim plan was written.
   */
  private long personsWritten;

  /**
   * Number of persons skipped because they do not contain any tours.
   */
  private long personsSkippedNoTours;

  /**
   * Number of activities written.
   */
  private long activitiesWritten;

  /**
   * Number of legs written.
   */
  private long legsWritten;

  /**
   * Number of legs written for a person carried on a tour owned by another.
   */
  private long passengerLegsWritten;

  /**
   * Number of participations in a tour owned by another that were left out of the plan.
   */
  private long accompanyingParticipationsSkipped;

  /**
   * Number of activities written without a description.
   */
  private long activitiesWithoutDescription;

  /**
   * Number of activities placed on the centroid of their zone because it offered no link to place them on.
   */
  private long centroidFallbackActivities;

  /**
   * Number of households for which a dwelling location was established.
   */
  private long dwellingsPinned;

  /**
   * Number of distinct activity locations established outside of a dwelling.
   */
  private long activityLocationsPinned;

  /**
   * Number of activities written directly after another activity, leaving no leg between the two.
   */
  private long activitiesFollowingAnActivity;

  /**
   * Number of legs written per MATSim mode.
   */
  private Map<String, Long> legsWrittenByMode = new TreeMap<>();

  /**
   * Increment the number of persons processed.
   */
  public void incrementPersonsProcessed() {
    personsProcessed++;
  }

  /**
   * Increment the number of persons written.
   */
  public void incrementPersonsWritten() {
    personsWritten++;
  }

  /**
   * Increment the number of persons skipped because they do not contain any tours.
   */
  public void incrementPersonsSkippedNoTours() {
    personsSkippedNoTours++;
  }

  /**
   * Increment the number of activities written.
   */
  public void incrementActivitiesWritten() {
    activitiesWritten++;
  }

  /**
   * Increment the number of legs written, recording the MATSim mode it was written for.
   *
   * @param matsimMode the leg was written for
   */
  public void incrementLegsWritten(String matsimMode) {
    legsWritten++;
    if (matsimMode != null) {
      legsWrittenByMode.merge(matsimMode, 1L, Long::sum);
    }
  }

  /**
   * Increment the number of legs written for a person carried on a tour owned by another.
   */
  public void incrementPassengerLegsWritten() {
    passengerLegsWritten++;
  }

  /**
   * Increment the number of participations in a tour owned by another that were left out of the plan.
   */
  public void incrementAccompanyingParticipationsSkipped() {
    accompanyingParticipationsSkipped++;
  }

  /**
   * Increment the number of activities written without a description.
   */
  public void incrementActivitiesWithoutDescription() {
    activitiesWithoutDescription++;
  }

  /**
   * Increment the number of activities placed on the centroid of their zone.
   */
  public void incrementCentroidFallbackActivities() {
    centroidFallbackActivities++;
  }

  /**
   * Increment the number of households for which a dwelling location was established.
   */
  public void incrementDwellingsPinned() {
    dwellingsPinned++;
  }

  /**
   * Increment the number of distinct activity locations established outside of a dwelling.
   */
  public void incrementActivityLocationsPinned() {
    activityLocationsPinned++;
  }

  /**
   * Increment the number of activities written directly after another activity.
   */
  public void incrementActivitiesFollowingAnActivity() {
    activitiesFollowingAnActivity++;
  }

  /**
   * Get the value of personsProcessed.
   *
   * @return value of personsProcessed
   */
  public long getPersonsProcessed() {
    return personsProcessed;
  }

  /**
   * Get the value of personsWritten.
   *
   * @return value of personsWritten
   */
  public long getPersonsWritten() {
    return personsWritten;
  }

  /**
   * Get the value of personsSkippedNoTours.
   *
   * @return value of personsSkippedNoTours
   */
  public long getPersonsSkippedNoTours() {
    return personsSkippedNoTours;
  }

  /**
   * Get the value of activitiesWritten.
   *
   * @return value of activitiesWritten
   */
  public long getActivitiesWritten() {
    return activitiesWritten;
  }

  /**
   * Get the value of legsWritten.
   *
   * @return value of legsWritten
   */
  public long getLegsWritten() {
    return legsWritten;
  }

  /**
   * Get the value of passengerLegsWritten.
   *
   * @return value of passengerLegsWritten
   */
  public long getPassengerLegsWritten() {
    return passengerLegsWritten;
  }

  /**
   * Get the value of accompanyingParticipationsSkipped.
   *
   * @return value of accompanyingParticipationsSkipped
   */
  public long getAccompanyingParticipationsSkipped() {
    return accompanyingParticipationsSkipped;
  }

  /**
   * Get the value of activitiesWithoutDescription.
   *
   * @return value of activitiesWithoutDescription
   */
  public long getActivitiesWithoutDescription() {
    return activitiesWithoutDescription;
  }

  /**
   * Get the value of centroidFallbackActivities.
   *
   * @return value of centroidFallbackActivities
   */
  public long getCentroidFallbackActivities() {
    return centroidFallbackActivities;
  }

  /**
   * Get the value of dwellingsPinned.
   *
   * @return value of dwellingsPinned
   */
  public long getDwellingsPinned() {
    return dwellingsPinned;
  }

  /**
   * Get the value of activityLocationsPinned.
   *
   * @return value of activityLocationsPinned
   */
  public long getActivityLocationsPinned() {
    return activityLocationsPinned;
  }

  /**
   * Get the value of activitiesFollowingAnActivity.
   *
   * @return value of activitiesFollowingAnActivity
   */
  public long getActivitiesFollowingAnActivity() {
    return activitiesFollowingAnActivity;
  }

  /**
   * Get the number of legs written for the given MATSim mode.
   *
   * @param matsimMode to collect the number of legs for
   * @return number of legs written for that mode, zero when none were
   */
  public long getLegsWritten(String matsimMode) {
    return legsWrittenByMode.getOrDefault(matsimMode, 0L);
  }

  /**
   * Get the number of legs written per MATSim mode.
   *
   * @return legs written by mode
   */
  public Map<String, Long> getLegsWrittenByMode() {
    return Map.copyOf(legsWrittenByMode);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    var builder = new StringBuilder();
    builder.append(String.format("MATSim plans writer statistics%n"));
    builder.append(String.format("  Persons processed              : %d%n", getPersonsProcessed()));
    builder.append(String.format("  Persons written                : %s%n",
        LoggingUtils.countWithPercentage(getPersonsWritten(), getPersonsProcessed())));
    builder.append(String.format("  Persons skipped (no tours)     : %s%n",
        LoggingUtils.countWithPercentage(getPersonsSkippedNoTours(), getPersonsProcessed())));
    builder.append(String.format("  Activities written             : %d%n", getActivitiesWritten()));
    builder.append(String.format("  - without a description        : %s%n",
        LoggingUtils.countWithPercentage(getActivitiesWithoutDescription(), getActivitiesWritten())));
    builder.append(String.format("  - placed on their zone centroid: %s%n",
        LoggingUtils.countWithPercentage(getCentroidFallbackActivities(), getActivitiesWritten())));
    builder.append(String.format("  - directly after another       : %s%n",
        LoggingUtils.countWithPercentage(getActivitiesFollowingAnActivity(), getActivitiesWritten())));
    builder.append(String.format("  Legs written                   : %d%n", getLegsWritten()));
    for (var entry : legsWrittenByMode.entrySet()) {
      builder.append(String.format("  - as mode %-21s: %s%n", entry.getKey(),
          LoggingUtils.countWithPercentage(entry.getValue(), getLegsWritten())));
    }
    builder.append(String.format("  - as a carried passenger       : %s%n",
        LoggingUtils.countWithPercentage(getPassengerLegsWritten(), getLegsWritten())));
    builder.append(String.format("  Accompanying participations left out : %d%n",
        getAccompanyingParticipationsSkipped()));
    builder.append(String.format("  Dwellings established          : %s%n",
        LoggingUtils.countWithPercentage(getDwellingsPinned(), getPersonsWritten())));
    builder.append(String.format("  Other activity locations       : %s%n",
        LoggingUtils.countWithPercentage(getActivityLocationsPinned(), getActivitiesWritten())));
    return builder.toString();
  }

  /**
   * Reset
   */
  public void reset() {
    personsProcessed = 0;
    personsWritten = 0;
    personsSkippedNoTours = 0;
    activitiesWritten = 0;
    legsWritten = 0;
    passengerLegsWritten = 0;
    accompanyingParticipationsSkipped = 0;
    activitiesWithoutDescription = 0;
    centroidFallbackActivities = 0;
    dwellingsPinned = 0;
    activityLocationsPinned = 0;
    activitiesFollowingAnActivity = 0;
    legsWrittenByMode = new TreeMap<>();
  }
}
