package org.goplanit.matsim.util;

import org.apache.commons.collections4.IterableUtils;
import org.goplanit.demands.discrete.tour.ActivitySchedule;
import org.goplanit.demands.discrete.tour.ScheduleElement;
import org.goplanit.demands.discrete.tour.Tour;
import org.goplanit.demands.discrete.trip.Trip;
import org.goplanit.demands.discrete.trip.TripImpl;
import org.goplanit.utils.mode.PredefinedModeType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utility pipeline orchestrator designed to evaluate an agent's active schedule timeline
 * and generate a non-destructive aggregated view profile list leveraging type-safe {@link Trip} implementations.
 * <p>
 * It compresses multi-stage transit access/egress chunks down to a single travel leg
 * according to user-defined {@link ModeChainCollapseRule} parameters. Spatial boundaries and
 * departure start times are automatically resolved from the composite chain, completely
 * bypassing memory mutation risks against the shared core PLANit model.
 * </p>
 *
 * @author markr
 */
public class ScheduleCollapsingUtils {

  /** dummy */
  private ScheduleCollapsingUtils(){}

  /**
   * Helper record or evaluation result holder to encapsulate matched connector lengths and validation state.
   */
  private static final class RuleMatchResult {
    final ModeChainCollapseRule rule;
    final int fromMatchedLen;
    final int toMatchedLen;
    final boolean fromPresent;
    final boolean toPresent;
    final boolean borrowsFromHistory;

    /**
     * Helper record or evaluation result holder to encapsulate matched connector lengths and validation state.
     */
    RuleMatchResult(ModeChainCollapseRule rule, int fromMatchedLen, int toMatchedLen,
                    boolean fromPresent, boolean toPresent, boolean borrowsFromHistory) {
      this.rule = rule;
      this.fromMatchedLen = fromMatchedLen;
      this.toMatchedLen = toMatchedLen;
      this.fromPresent = fromPresent;
      this.toPresent = toPresent;
      this.borrowsFromHistory = borrowsFromHistory;
    }

    /**
     * Validates whether the matched connector lengths satisfy the rule's criteria,
     * taking into account conditional missing connector flags.
     *
     * @return true if the match satisfies the rule requirements, false otherwise
     */
    boolean isValid() {
      boolean allowFromMissing = rule.isAllowedFromMissingIfToPresent();
      boolean allowToMissing = rule.isAllowedToMissingIfFromPresent();

      if (!allowFromMissing && !allowToMissing) {
        return fromPresent && toPresent;
      } else if (allowFromMissing && !allowToMissing) {
        // If from is allowed to be missing when to is present,
        // then we only strictly need toPresent to be true (from can be absent or present).
        return toPresent;
      } else if (!allowFromMissing && allowToMissing) {
        // If to is allowed to be missing when from is present,
        // then we only strictly need fromPresent to be true (to can be absent or present).
        return fromPresent;
      } else {
        // Both flags true: At least one must be present
        return fromPresent || toPresent;
      }
    }
  }

  /**
   * Retrieves the absolute final leaf trip component of a schedule element.
   *
   * @param element the element to inspect
   * @return the final trip leaf
   */
  private static Trip getLastLeafTrip(ScheduleElement element) {
    if (element instanceof AggregateTripView) {
      List<Trip> collapsed = ((AggregateTripView) element).getCollapsedTrips();
      return collapsed.get(collapsed.size() - 1);
    }
    return (Trip) element;
  }

  /**
   * Evaluates a specific trip against a collapse rule, validating history/future sequences
   * and applying conditional missing connector logic.
   *
   * @param schedule the full activity schedule container being evaluated
   * @param index the current index of the active trip
   * @param activeTrip the active core vehicle trip being analyzed
   * @param buffer the buffer containing processed elements preceding the active trip
   * @param rule the specific collapse rule blueprint being tested
   * @return a {@link RuleMatchResult} containing matched lengths and validity state
   */
  private static RuleMatchResult evaluateRuleForTrip(
      ActivitySchedule schedule,
      int index,
      Trip activeTrip,
      List<ScheduleElement> buffer,
      ModeChainCollapseRule rule) {

    List<PredefinedModeType> fromReqs = rule.getAllowedFromConnectors();
    List<PredefinedModeType> toReqs = rule.getAllowedToConnectors();
    int fromLen = fromReqs.size();
    int toLen = toReqs.size();
    int totalElements = schedule.size();

    // A. Dynamic Backward Check (Scans history)
    boolean fromPresent = false;
    boolean localBorrows = false;
    int currentFromMatchedLen = 0;

    if (fromLen > 0 && buffer.size() >= fromLen) {
      boolean sequenceOk = true;
      boolean tempBorrows = false;

      for (int f = 0; f < fromLen; f++) {
        PredefinedModeType expectedFromMode = fromReqs.get(fromLen - 1 - f);
        int historyIndex = buffer.size() - 1 - f;

        if (historyIndex >= 0) {
          var historyElement = buffer.get(historyIndex);
          Trip lastLeaf = getLastLeafTrip(historyElement);

          if (lastLeaf.getMode().getPredefinedModeType() == expectedFromMode &&
              lastLeaf.getDirection() == activeTrip.getDirection()) {
            if (historyElement instanceof AggregateTripView) {
              tempBorrows = true;
            }
            continue;
          }
        }
        sequenceOk = false;
        break;
      }

      if (sequenceOk) {
        fromPresent = true;
        localBorrows = tempBorrows;
        currentFromMatchedLen = fromLen;
      }
    }

    // B. Dynamic Forward Look-Ahead Check (Scans future)
    boolean toPresent = false;
    int currentToMatchedLen = 0;

    if (toLen > 0 && index + toLen < totalElements) {
      boolean sequenceOk = true;
      for (int t = 0; t < toLen; t++) {
        var forwardElement = schedule.get(index + 1 + t);
        if (!(forwardElement instanceof Trip)) {
          sequenceOk = false;
          break;
        }
        Trip fTrip = (Trip) forwardElement;
        if (fTrip.getMode().getPredefinedModeType() != toReqs.get(t) ||
            fTrip.getDirection() != activeTrip.getDirection()) {
          sequenceOk = false;
          break;
        }
      }
      if (sequenceOk) {
        toPresent = true;
        currentToMatchedLen = toLen;
      }
    }

    return new RuleMatchResult(rule, currentFromMatchedLen, currentToMatchedLen, fromPresent, toPresent, localBorrows);
  }

  /**
   * Fast, non-allocating pre-screen to check if a schedule hierarchy contains any
   * contiguous transit transfer sequences matching structural rule criteria.
   *
   * @param schedule the active schedule container to inspect
   * @param collapseRules active configuration rules containing sequence blueprints to verify
   * @return true if a valid matching multi-trip chain block exists anywhere in this schedule hierarchy
   */
  /**
   * Fast, non-allocating pre-screen to check if a schedule hierarchy contains any
   * contiguous transit transfer sequences matching structural rule criteria.
   */
  public static boolean requiresScheduleCollapsing(
      ActivitySchedule schedule,
      Collection<ModeChainCollapseRule> collapseRules) {

    if (schedule == null || schedule.isEmpty() || collapseRules == null || collapseRules.isEmpty()) {
      return false;
    }

    if (schedule.sizeUnrolled(true) < 1) {
      return false;
    }

    List<ScheduleElement> evaluationBuffer = new ArrayList<>();
    int totalElements = schedule.size();

    for (int index = 0; index < totalElements; index++) {
      var currentElement = schedule.get(index);

      if (currentElement.hasSchedule() && currentElement.getSchedule() != null) {
        if (requiresScheduleCollapsing(currentElement.getSchedule(), collapseRules)) {
          return true;
        }
      }

      if (currentElement instanceof Trip) {
        Trip activeTrip = (Trip) currentElement;

        for (var rule : collapseRules) {
          if (activeTrip.getMode().getPredefinedModeType() != rule.getMainMode()) {
            continue;
          }

          RuleMatchResult matchResult = evaluateRuleForTrip(schedule, index, activeTrip, evaluationBuffer, rule);
          if (matchResult.isValid()) {
            return true;
          }
        }
      }
      evaluationBuffer.add(currentElement);
    }
    return false;
  }

  /**
   * Evaluates a schedule container and all of its recursively nested sub-tour schedules,
   * creating a non-destructive collapsed ActivitySchedule supporting arbitrary connector lengths
   * and conditional missing connector options.
   */
  public static ActivitySchedule collapseContiguousTripChainsByModeRules(
      ActivitySchedule originalSchedule, Collection<ModeChainCollapseRule> collapseRules) {

    if (originalSchedule == null || originalSchedule.isEmpty() || collapseRules == null || collapseRules.isEmpty()
        || !requiresScheduleCollapsing(originalSchedule, collapseRules)) {
      return originalSchedule;
    }

    List<ScheduleElement> buffer = new ArrayList<>();
    int total = originalSchedule.size();

    for (int i = 0; i < total; i++) {
      var el = originalSchedule.get(i);

      if (el instanceof Tour) {
        Tour tour = (Tour) el;
        if (tour.hasSchedule() && tour.getSchedule() != null) {
          var collapsedNested = collapseContiguousTripChainsByModeRules(tour.getSchedule(), collapseRules);
          buffer.add(tour.getSchedule() != collapsedNested ? new AggregateTourView(tour, collapsedNested) : tour);
          continue;
        }
      }

      if (!(el instanceof Trip)) {
        buffer.add(el);
        continue;
      }

      Trip activeTrip = (Trip) el;
      var activeMode = activeTrip.getMode().getPredefinedModeType();
      RuleMatchResult bestMatch = null;
      int maxMatchedLen = -1;

      for (var rule : collapseRules) {
        if (activeMode != rule.getMainMode()) {
          continue;
        }

        RuleMatchResult matchResult = evaluateRuleForTrip(originalSchedule, i, activeTrip, buffer, rule);
        if (!matchResult.isValid()) {
          continue;
        }

        int totalMatchedLen = matchResult.fromMatchedLen + matchResult.toMatchedLen;
        if (totalMatchedLen >= maxMatchedLen) {
          bestMatch = matchResult;
          maxMatchedLen = totalMatchedLen;
        }
      }

      if (bestMatch != null) {
        List<Trip> chain = new ArrayList<>();

        if (bestMatch.fromMatchedLen > 0) {
          if (!bestMatch.borrowsFromHistory) {
            for (int f = bestMatch.fromMatchedLen; f > 0; f--) {
              chain.add(0, (Trip) buffer.remove(buffer.size() - 1));
            }
          } else {
            var prevView = (AggregateTripView) buffer.get(buffer.size() - 1);
            chain.add(getLastLeafTrip(prevView));
          }
        }

        chain.add(activeTrip);

        for (int t = 0; t < bestMatch.toMatchedLen; t++) {
          chain.add((Trip) originalSchedule.get(i + 1 + t));
        }

        buffer.add(new AggregateTripView(chain, activeTrip));
        i += bestMatch.toMatchedLen;
      } else {
        buffer.add(activeTrip);
      }
    }

    var collapsedSchedule = new ActivitySchedule();
    buffer.forEach(collapsedSchedule::add);
    return collapsedSchedule;
  }
}
