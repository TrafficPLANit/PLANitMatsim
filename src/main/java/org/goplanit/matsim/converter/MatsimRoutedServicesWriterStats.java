package org.goplanit.matsim.converter;

/**
 * Collects statistics during MATSim routed services writing, being the transit schedule holding the lines, their
 * routes, and the stop facilities they call at.
 */
public class MatsimRoutedServicesWriterStats {

  /**
   * Constructor.
   */
  public MatsimRoutedServicesWriterStats() {}

  /**
   * Statistics of the public transport output written from the routed services.
   */
  private final MatsimPtWriterStats ptWriterStats = new MatsimPtWriterStats();

  /**
   * The statistics of the public transport output written from the routed services
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
    return String.format("MATSim routed services writer statistics%n%s", getPtWriterStats().toString());
  }

  /**
   * Reset
   */
  public void reset() {
    ptWriterStats.reset();
  }
}
