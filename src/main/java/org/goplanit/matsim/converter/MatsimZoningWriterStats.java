package org.goplanit.matsim.converter;

/**
 * Collects statistics during MATSim zoning writing, being the public transport infrastructure contributed by the
 * zoning, namely its stop facilities.
 */
public class MatsimZoningWriterStats {

  /**
   * Constructor.
   */
  public MatsimZoningWriterStats() {}

  /**
   * Statistics of the public transport output written from the zoning.
   */
  private final MatsimPtWriterStats ptWriterStats = new MatsimPtWriterStats();

  /**
   * The statistics of the public transport output written from the zoning
   *
   * @return public transport writer statistics
   */
  public MatsimPtWriterStats getPtWriterStats() {
    return ptWriterStats;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return String.format("MATSim zoning writer statistics%n%s", getPtWriterStats().toString());
  }

  /**
   * Reset
   */
  public void reset() {
    ptWriterStats.reset();
  }
}
