package org.goplanit.matsim.converter.network;

import org.goplanit.utils.misc.LoggingUtils;

import java.util.concurrent.atomic.LongAdder;

/**
 * Collects statistics during MATSim network writing.
 */
public class MatsimNetworkWriterStats {

  /**
   * Constructor.
   */
  public MatsimNetworkWriterStats() {}

  /**
   * Number of nodes written.
   */
  private final LongAdder nodesWritten = new LongAdder();

  /**
   * Number of link segments written.
   */
  private final LongAdder linkSegmentsWritten = new LongAdder();

  /**
   * Number of link segments left out because none of the modes they allow is activated for writing.
   */
  private final LongAdder linkSegmentsSkippedNoActivatedMode = new LongAdder();

  /**
   * Number of turn restrictions written.
   */
  private final LongAdder turnRestrictionsWritten = new LongAdder();

  /**
   * Increment the number of nodes written.
   */
  public void incrementNodesWritten() {
    nodesWritten.increment();
  }

  /**
   * Increment the number of link segments written.
   */
  public void incrementLinkSegmentsWritten() {
    linkSegmentsWritten.increment();
  }

  /**
   * Increment the number of link segments left out for allowing no activated mode.
   */
  public void incrementLinkSegmentsSkippedNoActivatedMode() {
    linkSegmentsSkippedNoActivatedMode.increment();
  }

  /**
   * Add to the number of turn restrictions written.
   *
   * @param turnRestrictions to add
   */
  public void addTurnRestrictionsWritten(long turnRestrictions) {
    turnRestrictionsWritten.add(turnRestrictions);
  }

  /**
   * Get the value of nodesWritten.
   *
   * @return value of nodesWritten
   */
  public long getNodesWritten() {
    return nodesWritten.longValue();
  }

  /**
   * Get the value of linkSegmentsWritten.
   *
   * @return value of linkSegmentsWritten
   */
  public long getLinkSegmentsWritten() {
    return linkSegmentsWritten.longValue();
  }

  /**
   * Get the value of linkSegmentsSkippedNoActivatedMode.
   *
   * @return value of linkSegmentsSkippedNoActivatedMode
   */
  public long getLinkSegmentsSkippedNoActivatedMode() {
    return linkSegmentsSkippedNoActivatedMode.longValue();
  }

  /**
   * Get the value of turnRestrictionsWritten.
   *
   * @return value of turnRestrictionsWritten
   */
  public long getTurnRestrictionsWritten() {
    return turnRestrictionsWritten.longValue();
  }

  /**
   * The link segments offered for writing, being those written and those left out
   *
   * @return link segments considered
   */
  public long getLinkSegmentsConsidered() {
    return getLinkSegmentsWritten() + getLinkSegmentsSkippedNoActivatedMode();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return String.format(
        "MATSim network writer statistics%n" +
            "  Nodes written                  : %d%n" +
            "  Link segments considered       : %d%n" +
            "  - written                      : %s%n" +
            "  - no activated mode            : %s%n" +
            "  Turn restrictions written      : %d%n",
        getNodesWritten(),
        getLinkSegmentsConsidered(),
        LoggingUtils.countWithPercentage(getLinkSegmentsWritten(), getLinkSegmentsConsidered()),
        LoggingUtils.countWithPercentage(
            getLinkSegmentsSkippedNoActivatedMode(), getLinkSegmentsConsidered()),
        getTurnRestrictionsWritten());
  }

  /**
   * Reset
   */
  public void reset() {
    nodesWritten.reset();
    linkSegmentsWritten.reset();
    linkSegmentsSkippedNoActivatedMode.reset();
    turnRestrictionsWritten.reset();
  }
}
