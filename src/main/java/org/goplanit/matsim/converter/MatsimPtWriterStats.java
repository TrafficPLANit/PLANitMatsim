package org.goplanit.matsim.converter;

import org.goplanit.utils.misc.LoggingUtils;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects statistics during MATSim public transport writing, covering the transit schedule and the stop facilities
 * it refers to.
 */
public class MatsimPtWriterStats {

  /**
   * Constructor.
   */
  public MatsimPtWriterStats() {}

  /**
   * Number of stop facilities written.
   */
  private final LongAdder stopFacilitiesWritten = new LongAdder();

  /**
   * Number of transit lines written.
   */
  private final LongAdder transitLinesWritten = new LongAdder();

  /**
   * Number of transit routes written per MATSim mode.
   */
  private final Map<String, LongAdder> transitRoutesWrittenByMode = new TreeMap<>();

  /**
   * Make the given MATSim modes countable, so that a mode carrying no routes at all is still reported rather than
   * being absent. Any counts held for modes are discarded.
   *
   * @param matsimModes to count routes for
   */
  public void prepareRouteModes(Collection<String> matsimModes) {
    transitRoutesWrittenByMode.clear();
    matsimModes.forEach(mode -> transitRoutesWrittenByMode.put(mode, new LongAdder()));
  }

  /**
   * Increment the number of stop facilities written.
   */
  public void incrementStopFacilitiesWritten() {
    stopFacilitiesWritten.increment();
  }

  /**
   * Increment the number of transit lines written.
   */
  public void incrementTransitLinesWritten() {
    transitLinesWritten.increment();
  }

  /**
   * Increment the number of transit routes written for the given MATSim mode.
   *
   * @param matsimMode the route was written for
   */
  public void incrementTransitRoutesWritten(String matsimMode) {
    transitRoutesWrittenByMode.computeIfAbsent(matsimMode, mode -> new LongAdder()).increment();
  }

  /**
   * Get the value of stopFacilitiesWritten.
   *
   * @return value of stopFacilitiesWritten
   */
  public long getStopFacilitiesWritten() {
    return stopFacilitiesWritten.longValue();
  }

  /**
   * Get the value of transitLinesWritten.
   *
   * @return value of transitLinesWritten
   */
  public long getTransitLinesWritten() {
    return transitLinesWritten.longValue();
  }

  /**
   * Get the number of transit routes written for the given MATSim mode.
   *
   * @param matsimMode to collect the number of routes for
   * @return number of routes written for that mode, zero when none were
   */
  public long getTransitRoutesWritten(String matsimMode) {
    var counter = transitRoutesWrittenByMode.get(matsimMode);
    return counter != null ? counter.longValue() : 0;
  }

  /**
   * The total number of transit routes written across all modes
   *
   * @return transit routes written
   */
  public long getTransitRoutesWritten() {
    return transitRoutesWrittenByMode.values().stream().mapToLong(LongAdder::longValue).sum();
  }

  /**
   * Get the number of transit routes written per MATSim mode.
   *
   * @return transit routes written by mode
   */
  public Map<String, Long> getTransitRoutesWrittenByMode() {
    var result = new TreeMap<String, Long>();
    transitRoutesWrittenByMode.forEach((mode, counter) -> result.put(mode, counter.longValue()));
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    var builder = new StringBuilder();
    builder.append(String.format("MATSim public transport writer statistics%n"));
    builder.append(String.format("  Stop facilities written        : %d%n", getStopFacilitiesWritten()));
    builder.append(String.format("  Transit lines written          : %d%n", getTransitLinesWritten()));
    builder.append(String.format("  Transit routes written         : %d%n", getTransitRoutesWritten()));
    for (var entry : getTransitRoutesWrittenByMode().entrySet()) {
      builder.append(String.format("  - for mode %-20s: %s%n", entry.getKey(),
          LoggingUtils.countWithPercentage(entry.getValue(), getTransitRoutesWritten())));
    }
    return builder.toString();
  }

  /**
   * Reset
   */
  public void reset() {
    stopFacilitiesWritten.reset();
    transitLinesWritten.reset();
    transitRoutesWrittenByMode.clear();
  }
}
