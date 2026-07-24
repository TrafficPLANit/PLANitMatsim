package org.goplanit.matsim.util;

import org.apache.commons.collections4.IterableUtils;
import org.goplanit.demands.discrete.tour.ActivitySchedule;
import org.goplanit.demands.discrete.tour.ScheduleElement;
import org.goplanit.demands.discrete.tour.Tour;
import org.goplanit.demands.discrete.trip.Trip;
import org.goplanit.demands.discrete.trip.TripImpl;
import org.goplanit.utils.mode.PredefinedModeType;

import java.util.ArrayList;
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

//  /**
//   * Evaluates an active schedule timeline to determine if it contains any contiguous,
//   * same-direction trip chains that explicitly match your configuration rules.
//   *
//   * @param schedule the active person schedule container to inspect
//   * @param collapseRules active configuration rule matrices containing the mode patterns to verify
//   * @return true if a valid multi-trip chain exists and a collapsing operation is required
//   */
//  public static boolean requiresScheduleCollapsing(
//      ActivitySchedule schedule,
//      List<ModeChainCollapseRule> collapseRules) {
//
//    if (schedule == null || schedule.isEmpty() || collapseRules == null || collapseRules.isEmpty()) {
//      return false;
//    }
//
//    int totalElements = schedule.size();
//
//    // Iterate through the schedule to find the start of any potential matching chain
//    for (int index = 0; index < totalElements; index++) {
//      var currentElement = schedule.get(index);
//
//      // Recursive check: If this element contains its own nested schedule (e.g., a Tour), inspect it first
//      if (currentElement.hasSchedule() && currentElement.getSchedule() != null) {
//        if (requiresScheduleCollapsing(currentElement.getSchedule(), collapseRules)) {
//          return true; // Found a valid chain deep inside a nested tour structure
//        }
//      }
//
//      if (!(currentElement instanceof Trip)) {
//        continue;
//      }
//
//      Trip firstTripInChain = (Trip) currentElement;
//
//      // Scan through all active rules to see if a valid match begins at this index
//      for (var rule : collapseRules) {
//        List<PredefinedModeType> ruleModeSequence = rule.getModeSequence();
//        int ruleSequenceLength = ruleModeSequence.size();
//
//        // Array index boundary protection check
//        if (index + ruleSequenceLength - 1 >= totalElements) {
//          continue;
//        }
//
//        boolean sequenceMatches = true;
//        for (int offset = 0; offset < ruleSequenceLength; offset++) {
//          var lookAheadElement = schedule.get(index + offset);
//          if (!(lookAheadElement instanceof Trip)) {
//            sequenceMatches = false;
//            break;
//          }
//
//          Trip lookAheadTrip = (Trip) lookAheadElement;
//          PredefinedModeType tripPredefinedMode = lookAheadTrip.getMode().getPredefinedModeType();
//
//          // Verify both predefined mode enum match and continuous directional flow continuity
//          if (ruleModeSequence.get(offset) != tripPredefinedMode
//              || lookAheadTrip.getDirection() != firstTripInChain.getDirection()) {
//            sequenceMatches = false;
//            break;
//          }
//        }
//
//        // If even a single rule sequence matches completely, the schedule requires collapsing
//        if (sequenceMatches) {
//          return true;
//        }
//      }
//    }
//
//    return false;
//  }

  /**
   * Fast, non-allocating pre-screen to check if a schedule hierarchy contains any
   * contiguous transit transfer sequences matching structural rule criteria.
   *
   * @param schedule the active schedule container to inspect
   * @param collapseRules active configuration rules containing sequence blueprints to verify
   * @return true if a valid matching multi-trip chain block exists anywhere in this schedule hierarchy
   */
  public static boolean requiresScheduleCollapsing(
      ActivitySchedule schedule,
      List<ModeChainCollapseRule> collapseRules) {

    if (schedule == null || schedule.isEmpty() || collapseRules == null || collapseRules.isEmpty()) {
      return false;
    }

    // Verify unrolled leaf count carries enough nodes to satisfy minimum sequence rules
    if (schedule.sizeUnrolled(true) < 2) {
      return false;
    }

    int totalElements = schedule.size();
    for (int index = 0; index < totalElements; index++) {
      var currentElement = schedule.get(index);

      // Deep recursive check for nested tours
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

          List<PredefinedModeType> fromReqs = rule.getAllowedFromConnectors();
          List<PredefinedModeType> toReqs = rule.getAllowedToConnectors();

          // 1. Evaluate from-connectors backwards
          boolean fromMatches = true;
          int fromLen = fromReqs.size();
          for (int f = 0; f < fromLen; f++) {
            PredefinedModeType expectedFromMode = fromReqs.get(fromLen - 1 - f);
            int backIndex = index - 1 - f;

            if (backIndex >= 0) {
              var backElement = schedule.get(backIndex);
              // In pre-screen, we evaluate the raw input sequence path
              if (backElement instanceof Trip && ((Trip) backElement).getMode().getPredefinedModeType() == expectedFromMode
                  && ((Trip) backElement).getDirection() == activeTrip.getDirection()) {
                continue;
              }
              // Or check if it bridges an adjacent valid main mode to verify overlapping chains
              if (backElement instanceof Trip && ((Trip) backElement).getDirection() == activeTrip.getDirection()) {
                PredefinedModeType backMode = ((Trip) backElement).getMode().getPredefinedModeType();
                if (backMode != PredefinedModeType.PEDESTRIAN) {
                  continue; // Implicit connection point match
                }
              }
            }
            fromMatches = false;
            break;
          }

          if (!fromMatches) continue;

          // 2. Evaluate to-connectors forward
          boolean toMatches = true;
          int toLen = toReqs.size();
          if (index + toLen >= totalElements) continue;

          for (int t = 0; t < toLen; t++) {
            PredefinedModeType expectedToMode = toReqs.get(t);
            var forwardElement = schedule.get(index + 1 + t);

            if (!(forwardElement instanceof Trip)) {
              toMatches = false;
              break;
            }

            Trip forwardTrip = (Trip) forwardElement;
            if (forwardTrip.getMode().getPredefinedModeType() != expectedToMode
                || forwardTrip.getDirection() != activeTrip.getDirection()) {
              toMatches = false;
              break;
            }
          }

          if (toMatches) {
            return true;
          }
        }
      }
    }
    return false;
  }


  /**
   * Evaluates a schedule container and all of its recursively nested sub-tour schedules,
   * creating a non-destructive collapsed ActivitySchedule supporting arbitrary connector lengths.
   *
   * @param originalSchedule pristine, un-mutated sequential schedule container
   * @param collapseRules active configuration rule matrices containing the From/Main/To blueprints
   * @return a clean, memory-safe ActivitySchedule containing aggregated views
   */
  public static ActivitySchedule collapseContiguousTripChainsByModeRules(
      ActivitySchedule originalSchedule, List<ModeChainCollapseRule> collapseRules) {

    if (originalSchedule == null || originalSchedule.isEmpty() || collapseRules == null || collapseRules.isEmpty()
        || !requiresScheduleCollapsing(originalSchedule, collapseRules)) {
      return originalSchedule;
    }

    List<ScheduleElement> buffer = new ArrayList<>();
    int total = originalSchedule.size();

    for (int i = 0; i < total; i++) {
      var el = originalSchedule.get(i);

      // Recursive Sub-Tour Routing Guard
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
      ModeChainCollapseRule matched = null;
      int matchedToLength = 0;
      boolean borrowsFromHistory = false;

      // Generic Rule Discovery (Supports dynamic list lengths)
      for (var rule : collapseRules) {
        if (activeMode != rule.getMainMode()) {
          continue;
        }

        List<PredefinedModeType> fromReqs = rule.getAllowedFromConnectors();
        List<PredefinedModeType> toReqs = rule.getAllowedToConnectors();
        int fromLen = fromReqs.size();
        int toLen = toReqs.size();

        // A. Dynamic Backward Check (Scans full history depth required by rule)
        boolean fromMatches = true;
        boolean localBorrows = false;

        for (int f = 0; f < fromLen; f++) {
          PredefinedModeType expectedFromMode = fromReqs.get(fromLen - 1 - f);
          int historyIndex = buffer.size() - 1 - f;

          if (historyIndex >= 0) {
            var historyElement = buffer.get(historyIndex);
            Trip lastLeaf = getLastLeafTrip(historyElement);

            if (lastLeaf.getMode().getPredefinedModeType() == expectedFromMode &&
                lastLeaf.getDirection() == activeTrip.getDirection()) {
              if (historyElement instanceof AggregateTripView) {
                localBorrows = true;
              }
              continue;
            }
          }
          fromMatches = false;
          break;
        }

        if (!fromMatches) {
          continue;
        }

        // B. Dynamic Forward Look-Ahead Check (Scans full future range required by rule)
        boolean toMatches = (i + toLen < total);
        if (toMatches) {
          for (int t = 0; t < toLen; t++) {
            var forwardElement = originalSchedule.get(i + 1 + t);
            if (!(forwardElement instanceof Trip)) {
              toMatches = false;
              break;
            }
            Trip fTrip = (Trip) forwardElement;
            if (fTrip.getMode().getPredefinedModeType() != toReqs.get(t) ||
                fTrip.getDirection() != activeTrip.getDirection()) {
              toMatches = false;
              break;
            }
          }
        }

        // Greedy matching: track the longest valid matching rule sequence
        if (toMatches && toLen >= matchedToLength) {
          matched = rule;
          matchedToLength = toLen;
          borrowsFromHistory = localBorrows;
        }
      }

      // 3. Composite Slice Assembly
      if (matched != null) {
        List<Trip> chain = new ArrayList<>();
        int fromLen = matched.getAllowedFromConnectors().size();

        if (!borrowsFromHistory) {
          // Pop all standalone access legs chronologically out of the history buffer
          for (int f = fromLen; f > 0; f--) {
            chain.add(0, (Trip) buffer.remove(buffer.size() - 1));
          }
        } else {
          // Re-use the shared overlapping connector leaf out of the preceding view
          var prevView = (AggregateTripView) buffer.get(buffer.size() - 1);
          chain.add(getLastLeafTrip(prevView));
        }

        // Ingest the active main vehicle trip
        chain.add(activeTrip);

        // Ingest all forward to-connectors and safely fast-forward the loop iterator
        for (int t = 0; t < matchedToLength; t++) {
          chain.add((Trip) originalSchedule.get(i + 1 + t));
        }

        buffer.add(new AggregateTripView(chain, activeTrip));
        i += matchedToLength; // Slide index pointer completely past all forward connectors
      } else {
        buffer.add(activeTrip);
      }
    }

    var collapsedSchedule = new ActivitySchedule();
    buffer.forEach(collapsedSchedule::add);
    return collapsedSchedule;
  }
}
