package org.goplanit.matsim.converter.network;

import org.goplanit.utils.math.RoundUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionSegmentKind;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Collects statistics during MATSim network writing.
 */
public class MatsimNetworkWriterStats {

  /** number of decimals a factor is rounded to, as counted and logged */
  private static final int FACTOR_DECIMALS = 2;

  /**
   * What a kind of intersection segment is and why its factor reduces the exit capacity, as logged
   *
   * @param kind to describe
   * @return the description
   */
  private static String describe(IntersectionSegmentKind kind) {
    switch (kind) {
      case APPROACH_WITH_SIGNAL_AT_JUNCTION:
        return "approach with a signal at a junction, green for its phase only";
      case APPROACH_WITH_SIGNAL_AT_CROSSING_ONLY:
        return "approach with a signal at a crossing only, green unless pedestrians cross";
      case APPROACH_WITHOUT_SIGNAL:
        return "approach without a signal of its own, giving way to signalised traffic";
      case INTERNAL_SEGMENT_WITH_SIGNAL:
        return "internal segment with a signal, values set directly";
      default:
        return "internal segment without a signal";
    }
  }

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
   * Number of link segments written with a reduced exit capacity, by the kind of intersection segment they are and by
   * factor rounded to two decimals.
   */
  private final Map<IntersectionSegmentKind, SortedMap<Double, Long>> linkSegmentsWithReducedExitCapacity =
      new EnumMap<>(IntersectionSegmentKind.class);

  /**
   * Constructor.
   */
  public MatsimNetworkWriterStats() {}

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
   * Count a link segment written with a reduced exit capacity.
   *
   * @param kind of intersection segment it is
   * @param factor its capacity was multiplied by, below 1
   */
  public void addLinkSegmentWithReducedExitCapacity(IntersectionSegmentKind kind, double factor) {
    linkSegmentsWithReducedExitCapacity.computeIfAbsent(kind, newKind -> new TreeMap<>())
        .merge(RoundUtils.simpleDoubleRound(factor, FACTOR_DECIMALS), 1L, Long::sum);
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
   * The link segments written with a reduced exit capacity, of any kind
   *
   * @return number of link segments
   */
  public long getLinkSegmentsWithReducedExitCapacity() {
    return linkSegmentsWithReducedExitCapacity.keySet().stream()
        .mapToLong(this::getLinkSegmentsWithReducedExitCapacity).sum();
  }

  /**
   * The link segments written with a reduced exit capacity that are of a kind of intersection segment
   *
   * @param kind of intersection segment
   * @return number of link segments
   */
  public long getLinkSegmentsWithReducedExitCapacity(IntersectionSegmentKind kind) {
    return linkSegmentsWithReducedExitCapacity.getOrDefault(kind, Collections.emptySortedMap()).values().stream()
        .mapToLong(Long::longValue).sum();
  }

  /**
   * The link segments written with a reduced exit capacity that are of a kind of intersection segment and had their
   * capacity multiplied by a factor, compared rounded to two decimals
   *
   * @param kind of intersection segment
   * @param factor the capacity was multiplied by
   * @return number of link segments
   */
  public long getLinkSegmentsWithReducedExitCapacity(IntersectionSegmentKind kind, double factor) {
    return linkSegmentsWithReducedExitCapacity.getOrDefault(kind, Collections.emptySortedMap())
        .getOrDefault(RoundUtils.simpleDoubleRound(factor, FACTOR_DECIMALS), 0L);
  }

  /**
   * The link segments written with a reduced exit capacity, one line per kind of intersection segment that can reduce
   * it, saying what the kind means and how many got each factor, for example "approach without a signal of its own,
   * giving way to signalised traffic: 3, factor 0.50 on 3"
   *
   * @return the summary
   */
  public String getLinkExitCapacityReductionSummary() {
    var summary = new StringBuilder(String.format("Link exit capacity reduced by the effective capacity factor on %s%n",
        LoggingUtils.countWithPercentage(
            getLinkSegmentsWithReducedExitCapacity(), getLinkSegmentsWritten(), "link segments written")));
    for (var kind : IntersectionSegmentKind.values()) {
      if (!kind.holdsEffectiveCapacityFactor()) {
        continue;
      }
      summary.append(String.format("  %s: %d", describe(kind), getLinkSegmentsWithReducedExitCapacity(kind)));
      var byFactor = linkSegmentsWithReducedExitCapacity.getOrDefault(kind, Collections.emptySortedMap());
      if (!byFactor.isEmpty()) {
        summary.append(byFactor.entrySet().stream().map(entry -> String.format("%.2f on %d", entry.getKey(),
            entry.getValue())).collect(Collectors.joining(", ", ", factor ", "")));
      }
      summary.append(String.format("%n"));
    }
    return summary.toString();
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
    linkSegmentsWithReducedExitCapacity.clear();
  }
}
