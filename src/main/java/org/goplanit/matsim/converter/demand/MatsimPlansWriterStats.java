package org.goplanit.matsim.converter.demand;

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
   * Increment the number of legs written.
   */
  public void incrementLegsWritten() {
    legsWritten++;
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
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return String.format(
        "MATSim plans writer statistics%n" +
            "  Persons processed              : %d%n" +
            "  Persons written                : %d%n" +
            "  Persons skipped (no tours)     : %d%n" +
            "  Activities written             : %d%n" +
            "  Legs written                   : %d%n",
        personsProcessed,
        personsWritten,
        personsSkippedNoTours,
        activitiesWritten,
        legsWritten);
  }
}