package org.goplanit.matsim.converter.demand;

import org.goplanit.converter.demands.DiscreteDemandsWriter;
import org.goplanit.converter.idmapping.DiscreteDemandsIdMapper;
import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.demands.discrete.person.Person;
import org.goplanit.demands.discrete.person.PersonUtils;
import org.goplanit.demands.discrete.tour.ActivitySchedule;
import org.goplanit.demands.discrete.tour.ScheduleElement;
import org.goplanit.demands.discrete.tour.Tour;
import org.goplanit.demands.discrete.trip.Trip;
import org.goplanit.demands.discrete.util.DirectionBound;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.matsim.util.MatsimBuiltInMode;
import org.goplanit.matsim.util.ScheduleCollapsingUtils;
import org.goplanit.matsim.xml.*;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.time.LocalTimeUtils;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.goplanit.utils.zoning.OdZone;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Coordinate;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.goplanit.utils.time.LocalTimeUtils.SECONDS_IN_DAY;

/**
 * A class that takes a PLANit DiscreteDemands and writes it as a MATSIM plans (v5) file.
 *
 * @author markr
 */
public class MatsimDiscreteDemandsWriter extends MatsimWriter<DiscreteDemands> implements DiscreteDemandsWriter{

  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimDiscreteDemandsWriter.class.getCanonicalName());

  /** ref network */
  private MacroscopicNetwork referenceNetwork;

  /** ref zoning */
  private Zoning referenceZoning;

  /** track stats */
  private MatsimPlansWriterStats writerStats = new MatsimPlansWriterStats();

  /** Default simulation seed for reproducible allocations */
  private static final long DEFAULT_SIMULATION_SEED = 42L;

  /** LocationGenerator:ZONE_LINKS_DISTANCE_WEIGHTED requires
   * Pre-indexed mapping of zone weights by mode for sampling (if we use link weighted sampling within zone) */
  private Map<Mode, Map<Long, LocationGeneratorUtils.ZoneLinkWeights>> zoneLinkWeightsIndexByMode;

  /** LocationGenerator:ZONE_LINKS_DISTANCE_WEIGHTED requires
   * Reproducible random generation stream */
  private SplittableRandom randomEngine;

  /** contains the Persons' schedules that we will ignore. This is populated based on having schedules that are not
   * based on the settings provided, e.g., contains modes deactivated in the mapping for example */
  private Set<Person> personSchedulesToIgnore;

  /**
   * validate the settings making sure minimal output information is available
   */
  private boolean validateSettings() {
    if(StringUtils.isNullOrBlank(getSettings().getOutputDirectory())) {
      LOGGER.severe("Matsim plans output directory not set on settings, unable to persist demands");
      return false;
    }
    if(StringUtils.isNullOrBlank(getSettings().getFileName())) {
      LOGGER.severe("Matsim plans output file name not set on settings, unable to persist demands");
      return false;
    }

    return true;
  }

  /**
   * MATSIM writer settings
   */
  protected final MatsimDiscreteDemandsWriterSettings settings;

  /**
   * Check for activity to intersperse travel legs:
   * <ul>
   *   <li>activity in between end of outbound trips and before first inbound trip</li>
   *   <li>activity in between start of subtour but after arrival of parent tour at destination </li>
   *   <li>activity in between start of next tour but after arrival of previous tour back at origin </li>
   * </ul>
   *
   * @param xmlWriter               to use
   * @param currentElement          to use
   * @param precedingElement        to use
   * @param person                  to use
   * @param periodStartTimeSeconds  to use
   * @param periodEndTimeSeconds    to use
   * @return flag indicating if we detected a pair of collapsing schedule elements (true). If so, the current
   * element should  no longer be written out into the plan as it is already covered by the preceding element
   * @throws XMLStreamException if error
   */
  private boolean checkForActivityElementBetweenScheduleElements(
      XMLStreamWriter xmlWriter,
      ScheduleElement currentElement,
      ScheduleElement precedingElement,
      Person person,
      long periodStartTimeSeconds,
      long periodEndTimeSeconds)
      throws XMLStreamException {

    final boolean COLLAPSE = true;
    final boolean DO_NOT_COLLAPSE = false;
    OdZone homeZone = person.getHousehold().getZone();

    // in MATSim 24h+ format
    long startTimeSecondsUnbounded = currentElement.getStartTime().toSecondOfDay();
    if (startTimeSecondsUnbounded < periodStartTimeSeconds) {
      startTimeSecondsUnbounded += SECONDS_IN_DAY;
    }

    // Case 1: outbound trip followed by inbound trip --> activity occurs in between the two trips now
    //         NOTE: we check this in this way to allow for chained outbound trips
    //         this exhausts the use of the trip's tour purpose in one go - no further nesting
    if ((currentElement instanceof Trip) && (precedingElement instanceof Trip) &&
        ((Trip) precedingElement).getDirection() == DirectionBound.OUTBOUND &&
        ((Trip) currentElement).getDirection() == DirectionBound.INBOUND) {
      var upcomingTrip = ((Trip) currentElement);
      var precedingTrip = ((Trip) precedingElement);
      var thePurpose = upcomingTrip.getTour().getPurpose();

      // activity @ tour destination
      writeActivityElement(
          xmlWriter, person, thePurpose, LocalTimeUtils.formatHhMmSs(startTimeSecondsUnbounded),
          precedingTrip.getTour().getDestination(), precedingTrip.getMode(), upcomingTrip.getMode());
      writeIndentation(xmlWriter);
      return DO_NOT_COLLAPSE;
    }

    // Case 2a: multiple consecutive Inbound or Outbound legs of trips of the same mode, either we collapse them into
    //         a single activity or we intersperse them with activities based on the trip purpose rather than the
    //         tour purpose
    // Case 2b: same only now different modes between the chain of InBound/Outbound (pt walk access for example). In
    //          that case we also either collapse or not depending on configuration
    if ((currentElement instanceof Trip) && (precedingElement instanceof Trip)) {
      Trip prevTrip = (Trip) precedingElement;
      Trip currTrip = (Trip) currentElement;

      if (prevTrip.getDirection() == currTrip.getDirection()) {

        // Resolve both the underlying PLANit types and the target MATSim destination strings
        PredefinedModeType prevPlanitMode = prevTrip.getMode().getPredefinedModeType();
        PredefinedModeType currPlanitMode = currTrip.getMode().getPredefinedModeType();

        String prevMatsimMode = getSettings().getMappedMatsimMode(prevPlanitMode);
        String currMatsimMode = getSettings().getMappedMatsimMode(currPlanitMode);

        // Identify if the current travel segments belong to the Public Transport network layer
        boolean isPrevPt = MatsimBuiltInMode.fromValue(prevMatsimMode).map(
            MatsimBuiltInMode::isTypeOfPt).orElse(false);
        boolean isCurrPt = MatsimBuiltInMode.fromValue(currMatsimMode).map(
            MatsimBuiltInMode::isTypeOfPt).orElse(false);

        // =====================================================================
        // OPTION A: DISAGGREGATE SIMULATION LAYOUT (Detailed physical routing)
        // =====================================================================
        String thePurpose = prevTrip.getPurpose();
        if (getSettings().isUseDisaggregateTransitModes()) {

          // Every single public transport segment swap or mode shift requires an explicit interaction facility marker
          if (isPrevPt || isCurrPt || prevPlanitMode != currPlanitMode) {

            // more specific, MATSim compliant
            if (isCurrPt || isPrevPt) {
              thePurpose = MatsimPredefinedActivityTypes.PT_INTERACTION;
            }

            writeActivityElement(
                xmlWriter, person, thePurpose, LocalTimeUtils.formatHhMmSs(startTimeSecondsUnbounded),
                prevTrip.getDestination(true), prevTrip.getMode(), currTrip.getMode());
            writeIndentation(xmlWriter);
            return DO_NOT_COLLAPSE;
          }

          writeActivityElement(
              xmlWriter, person, thePurpose, LocalTimeUtils.formatHhMmSs(startTimeSecondsUnbounded),
              prevTrip.getDestination(true), prevTrip.getMode(), currTrip.getMode());
          writeIndentation(xmlWriter);
          return DO_NOT_COLLAPSE;
        }else {
          // AGGREGATE - so collapse if needed

          // Transit Transfers: Any consecutive public transport legs collapse completely into a single block
          if (isPrevPt && isCurrPt) {
            // Collapse (Write Nothing) - activity will be picked up after the last one in chain
            // flag to skip trip leg writing until chain ends
            return COLLAPSE;
          }

          writeActivityElement(
              xmlWriter, person, thePurpose, LocalTimeUtils.formatHhMmSs(startTimeSecondsUnbounded),
              prevTrip.getDestination(true), prevTrip.getMode(), currTrip.getMode());
          writeIndentation(xmlWriter);
          return DO_NOT_COLLAPSE;
        }
      }
    }

    // Case 3: sub tour: activity from preceding tour occurs in between arrival from inbound trip and
    //                   start of this tour. Arrival at this location is based on
    //                   parent tour (this is interspersed), the location resides at the parent tour's
    //                   destination so use that purpose
    if((currentElement instanceof Tour) && (precedingElement instanceof Trip) &&
        ((Trip)precedingElement).getDirection() == DirectionBound.OUTBOUND){
      var currTour = ((Tour)currentElement);
      // purpose from parent, if no parent, we have to assume this is the op level tour, so locale is person's initial
      // purpose instead
      boolean hasParent = currTour.hasParentTour();
      var thePurpose = hasParent ? currTour.getParentTour().getPurpose() : person.getInitialPurpose();
      writeActivityElement(xmlWriter, person, thePurpose, LocalTimeUtils.formatHhMmSs(startTimeSecondsUnbounded),
          hasParent ? currTour.getParentTour().getDestination() : homeZone,
          ((Trip) precedingElement).getMode(), currTour.getOutboundMode());
      writeIndentation(xmlWriter);
      return DO_NOT_COLLAPSE;
    }

    // Case 4: returning back to origin from destination location AFTER just coming back to destination location from
    //         subtour --> purpose is inbound trip's tour purpose, and end time of activity at tour's destination is
    //         the trip's start time, location is the destination location of the trip it's tour (or its preceding
    //         tour's origin)
    if((currentElement instanceof Trip) && ((Trip)currentElement).getDirection() == DirectionBound.INBOUND
        && (precedingElement instanceof Tour)){
      var inboundTripItsTour = ((Trip) currentElement).getTour();
      var thePurpose = inboundTripItsTour.getPurpose();
      // purpose from inbound trip its tour
      writeActivityElement(xmlWriter, person, thePurpose, LocalTimeUtils.formatHhMmSs(startTimeSecondsUnbounded),
          inboundTripItsTour.getDestination(), precedingElement.getInboundMode(), currentElement.getOutboundMode()); // zone @ trip tour's origin
      writeIndentation(xmlWriter);
      return DO_NOT_COLLAPSE;
    }

    // Case 5: starting a new tour after a preceding tour has finished fully and we have spent time waiting
    //         in between. In that case, the purpose is that of the parent, and the end time of the activity is the
    //         start time of the outbound trip
    if((currentElement instanceof Tour) && (precedingElement instanceof Tour)){
      var currTour = ((Tour)currentElement);
      var thePurpose = currTour.hasParentTour() ?
          currTour.getParentTour().getPurpose() : person.getInitialPurpose();
      writeActivityElement(
          xmlWriter, person, thePurpose, LocalTimeUtils.formatHhMmSs(startTimeSecondsUnbounded),
          currTour.getOrigin(), precedingElement.getInboundMode(), currTour.getOutboundMode());
      writeIndentation(xmlWriter);
      return DO_NOT_COLLAPSE;
    }

    return DO_NOT_COLLAPSE;
  }

  /**
   * Process a schedule element
   *
   * @param xmlWriter              to use
   * @param scheduleElement        to process
   * @param person                 for this element
   * @param modeMapping            to use
   * @param periodStartTimeSeconds period start time
   * @param periodEndTimeSeconds   period end time
   */
  private void processScheduleElement(
      XMLStreamWriter xmlWriter,
      ScheduleElement scheduleElement,
      Person person,
      Map<Mode, String> modeMapping,
      long periodStartTimeSeconds,
      long periodEndTimeSeconds) {
    if(scheduleElement.getStartTime() == null){
      throw new PlanItRunTimeException(
          "Start time of schedule element is null for person (%s), this should not happen",
          person.getIdsAsString());
    }

    var startTimeSeconds = scheduleElement.getStartTime().toSecondOfDay();
    if(startTimeSeconds < periodStartTimeSeconds || startTimeSeconds > periodEndTimeSeconds){
      //ignore outside of time period, unless exactly at midnight in which case we also test the alternative
      // representation to be sure
      if(startTimeSeconds==0){
        int startTimeSecondsOffset = startTimeSeconds + (int) SECONDS_IN_DAY;
        if(startTimeSecondsOffset < periodStartTimeSeconds || startTimeSecondsOffset > periodEndTimeSeconds){
          return;
        }
      }else{
        return;
      }

    }

    try{

      if(scheduleElement instanceof Trip){
        // trips are always a travel leg, the activities come from the tours
        writeLegElement(xmlWriter, (Trip) scheduleElement, modeMapping);
        writeIndentation(xmlWriter);

      }else if(scheduleElement instanceof Tour){
        // nest
        var currTour = (Tour) scheduleElement;

        if(!currTour.hasSchedule()){
          LOGGER.warning(String.format("Found tour (%s) without a schedule (trips, or sub-tours), should not happen",
              currTour.getIdsAsString()));
          return;
        }else{

          ScheduleElement prevTourScheduleElement = null;
          for(var tourScheduleElement : currTour.getSchedule()){

            // add in activity, or in case trips collapse due to mode/trip chain aggregation, flag skip
            boolean currentElementCollapsed = checkForActivityElementBetweenScheduleElements(
                xmlWriter,
                tourScheduleElement,
                prevTourScheduleElement,
                person,
                periodStartTimeSeconds,
                periodEndTimeSeconds);

            // delegate one level deeper
            if(!currentElementCollapsed) {
              processScheduleElement(
                  xmlWriter, tourScheduleElement, person, modeMapping, periodStartTimeSeconds, periodEndTimeSeconds);
            }

            prevTourScheduleElement = tourScheduleElement;

          }

        }

      }else{
        LOGGER.severe(String.format("Unsupported person schedule element type (%s), skip, " +
            "should not happen, and may result in invalid plan for person (%s)",
            scheduleElement.getClass().getCanonicalName(), person.getIdsAsString()));
      }

    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error while writing MATSim person (%s) plan XML element",
          person.getIdsAsString());
    }
  }


  /**
   * Write an activity element
   * @param xmlWriter writer
   * @param person the person doing the activity
   * @param activityDescription description of activity
   * @param endTime MATSim formatted end time of activity
   * @param activeZone the zone the activity relates to
   * @param arrivalMode mode by which the arrival occurs (may be null if first)
   * @param departureMode mode by which the departure occurs (may be null if last)
   * @throws XMLStreamException if error
   */
  private void writeActivityElement(
      XMLStreamWriter xmlWriter,
      Person person,
      String activityDescription,
      String endTime,
      OdZone activeZone,
      Mode arrivalMode,
      Mode departureMode)
      throws XMLStreamException {
    if(activityDescription == null || activityDescription.isBlank()){
      LOGGER.warning(String.format("Description for activity has no content (for person (%s))",
          person.getIdsAsString()));
    }

    // Default coordinates sourced directly from centroid definitions
    boolean zoneHasPos = activeZone.hasCentroid() && activeZone.getCentroid().hasPosition();
    double finalX = zoneHasPos ? activeZone.getCentroid().getPosition().getX() : Double.NaN;
    double finalY = zoneHasPos ? activeZone.getCentroid().getPosition().getY() : Double.NaN;
    String matchedLinkSegmentId = null;

    // Apply distance-weighted link sampling strategy if active
    if (getSettings().getLocationGeneratorType() == LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED
        && this.zoneLinkWeightsIndexByMode != null && this.zoneLinkWeightsIndexByMode.containsKey(arrivalMode)) {

      LocationGeneratorUtils.ZoneLinkWeights weights =
          this.zoneLinkWeightsIndexByMode.get(arrivalMode).get(activeZone.getId());
      if (weights != null) {
        var selectedSegment = weights.drawRandomSegment(this.randomEngine);
        if (selectedSegment== null || selectedSegment.getUpstreamVertex() == null ||
            selectedSegment.getUpstreamVertex().getPosition() == null) {
          LOGGER.severe(String.format("Drawn link segment (%s) has no upstream vertex, ignore",
              selectedSegment.getIdsAsString()));
        }else {
          matchedLinkSegmentId =
              getComponentIdMappers().getNetworkIdMappers().getMacroscopicLinkSegmentIdMapper().apply(selectedSegment);
          Coordinate startPoint = selectedSegment.getUpstreamVertex().getPosition().getCoordinate();
          finalX = startPoint.x;
          finalY = startPoint.y;
        }
      } else if(activeZone.hasGeometry()){
        // fallback use centroid derived location
        var startPoint = PlanitJtsUtils.extractPolygonCentre(activeZone.getGeometry());
        if(startPoint != null){
          finalX = startPoint.getX();
          finalY = startPoint.getY();
        }
      }
    }else if(getSettings().getLocationGeneratorType().equals(LocationGeneratorType.ZONE_CENTROID)){
      //auto-populated already
    }

    if(Double.isNaN(finalX) || Double.isNaN(finalY)){
      LOGGER.severe(String.format(
          "Zone (%s) has no location to fall back on, unable to provide spatial reference for activity %s of person (%s)",
          activeZone.getIdsAsString(), activityDescription, person.getIdsAsString()));
    }

    // activity end point
    xmlWriter.writeEmptyElement(MatsimPlansElements.ACTIVITY);
    // purpose -> activity type
    xmlWriter.writeAttribute(MatsimAttributes.TYPE, activityDescription);
    // location
    xmlWriter.writeAttribute(MatsimAttributes.X, String.valueOf(finalX));
    xmlWriter.writeAttribute(MatsimAttributes.Y, String.valueOf(finalY));

    if (matchedLinkSegmentId != null) {
      xmlWriter.writeAttribute(MatsimAttributes.LINK, matchedLinkSegmentId);
    }

    // end time of activity
    xmlWriter.writeAttribute(MatsimPlansAttributes.END_TIME, endTime);

    writeNewLine(xmlWriter);

    writerStats.incrementActivitiesWritten();
  }

  /**
   * Write a leg element which corresponds one to one with a PLANit trip
   *
   * @param xmlWriter   to use
   * @param trip        trip containing leg info
   * @param modeMapping to use
   */
  private void writeLegElement(
      XMLStreamWriter xmlWriter, Trip trip, Map<Mode, String> modeMapping) throws XMLStreamException {

    // leg
    xmlWriter.writeEmptyElement(MatsimPlansElements.LEG);

    String matsimMode = modeMapping.get(trip.getMode());
    if(StringUtils.isNullOrBlank(matsimMode)){
      throw new PlanItRunTimeException(
          "PLANit trip (%s) with PLANit mode (%s) has no mapped MATSim mode available, update mode mapping!",
          trip.getIdsAsString(), trip.getMode());
    }
    // mode=
    xmlWriter.writeAttribute(MatsimPlansAttributes.MODE, matsimMode);

    writeNewLine(xmlWriter);

    writerStats.incrementLegsWritten();
  }

  /**
   * Write the plan element (selected=yes) and content for a given person. We currently only write out a single
   * assumed selected plan per person
   *
   * @param xmlWriter       to use
   * @param person          to write the selected plan for
   * @param modeMapping     to use
   * @param discreteDemands to use
   */
  private void writePersonPlan(
      XMLStreamWriter xmlWriter, Person person, Map<Mode, String> modeMapping, DiscreteDemands discreteDemands) {
    var timePeriod = discreteDemands.getTimePeriods().getFirst();
    long periodStartTimeSeconds = timePeriod.getStartTimeSeconds();
    long periodEndTimeSeconds = periodStartTimeSeconds + timePeriod.getDurationSeconds();

    if(person.getHousehold() == null){
      LOGGER.severe(String.format("Expecting each person to have a household, skipping person (%s)",
          person.getHousehold()));
      return;
    }
    OdZone homeZone = person.getHousehold().getZone();
    if(homeZone == null){
      LOGGER.severe(String.format("Expecting household (%s) to have a home zone, " +
              "unable to complete person (%s) schedule, skip",
          person.getHousehold().getIdsAsString(), person.getIdsAsString()));
      return;
    }

    try{
      // plan
      writeStartElement(xmlWriter, MatsimPlansElements.PLAN, true /* add indentation*/);
      // selected=yes
      xmlWriter.writeAttribute(MatsimPlansAttributes.SELECTED, "yes");
      writeNewLine(xmlWriter);
      writeIndentation(xmlWriter);

      var initialActivity = person.getSchedule().getFirst();
      long startTimeSecondsUnbounded = initialActivity.getStartTime().toSecondOfDay();
      if(startTimeSecondsUnbounded < periodStartTimeSeconds){
        startTimeSecondsUnbounded += SECONDS_IN_DAY;
      }

      ActivitySchedule processedSchedule;
      if (getSettings().isUseDisaggregateTransitModes()
          || !ScheduleCollapsingUtils.requiresScheduleCollapsing(
              person.getSchedule(), getSettings().getModeCollapseRules())) {
        // pass-through: reuses the original reference
        processedSchedule = person.getSchedule();
      } else {
        // Groups complex (multi-)transfer chains non-destructively via peeking views
        processedSchedule = ScheduleCollapsingUtils.collapseContiguousTripChainsByModeRules(
            person.getSchedule(), getSettings().getModeCollapseRules());
      }

      writeActivityElement(
          xmlWriter,
          person,
          person.getInitialPurpose(),
          LocalTimeUtils.formatHhMmSs(startTimeSecondsUnbounded) /* end time of idle activity */,
          homeZone,
          processedSchedule.getOutboundMode(), // initial activity as no incoming mode, simply adopt outbound one
          processedSchedule.getOutboundMode());
      writeIndentation(xmlWriter);

      // track the schedule of the person to extract activities and travel leg information in MATSim format
      ScheduleElement prevElement = null;
      for(int index=0;index< processedSchedule.size(); index++){
        var scheduleElement = processedSchedule.get(index);

        // add in activity, or in case trips collapse due to mode/trip chain aggregation, flag skip
        boolean currentElementCollapsed = checkForActivityElementBetweenScheduleElements(
            xmlWriter, scheduleElement, prevElement, person, periodStartTimeSeconds, periodEndTimeSeconds);

        /* now we proceed with each travel involved activity */
        if(!currentElementCollapsed) {
          processScheduleElement(
              xmlWriter, scheduleElement, person, modeMapping, periodStartTimeSeconds, periodEndTimeSeconds);
        }

        prevElement = scheduleElement;
      }

      var thePurpose = person.getInitialPurpose();
      writeActivityElement(
          xmlWriter,
          person,
          thePurpose,
          LocalTimeUtils.formatHhMmSs(periodEndTimeSeconds - 1),
          homeZone,
          processedSchedule.getInboundMode(),
          processedSchedule.getInboundMode()); // last activity has no departing trip, simply reuse inbound mode

      writeEndElementNewLine(xmlWriter, true /*decrease indent */);
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error while writing MATSim person (%s) plan XML element",
          person.getIdsAsString());
    }
  }

  /**
   * Write persons element and then trigger writing all individual persons
   *
   * @param xmlWriter to use
   * @param discreteDemands to use
   */
  protected void writeMatsimPersons(XMLStreamWriter xmlWriter, DiscreteDemands discreteDemands) {
    var modeMapping = getSettings().collectActivatedPlanitModeToMatsimModeMapping(
        getReferenceNetwork().getTransportLayers().getFirst());

    try{

      for(var person : discreteDemands.getPersons()){
        writerStats.incrementPersonsProcessed();

        if(personSchedulesToIgnore.contains(person)){
          continue;
        }

        if(person.getHousehold() == null){
          LOGGER.warning(String.format(
              "Currently MATSim plan writer requires person (%s) to have a household, missing, skip",
              person.getIdsAsString()));
        }
        var initialActivity = person.getSchedule().getFirst();
        if(initialActivity == null){
          writerStats.incrementPersonsSkippedNoTours();
          continue;
        }

        // person
        writeStartElement(xmlWriter, MatsimPlansElements.PERSON, true /* add indentation*/);

        // id
        xmlWriter.writeAttribute(MatsimAttributes.ID, getPrimaryIdMapper().getPersonClassIdMapper().apply(person));
        writeNewLine(xmlWriter);

        if(person.hasExternalId()){
          // attributes - Inside the person block right before initializing the <plan> tag
          writeStartElementNewLine(xmlWriter, MatsimElements.ATTRIBUTES, true /* add indentation*/);

          // external id
          writeMatsimCustomAttributeEntry(
              xmlWriter, "externalId", "java.lang.String", person.getExternalId());

          writeEndElementNewLine(xmlWriter, true /* undo indentation */ );
        }

        // plan
        writePersonPlan(xmlWriter, person, modeMapping, discreteDemands);

        writeEndElementNewLine(xmlWriter, true /* undo indentation */ );

        writerStats.incrementPersonsWritten();
      }
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error while writing MATSim population XML element");
    }
  }

  /**
   * Write top level population elements and then the plans per person. For now we only support a single plan per
   * person
   *
   * @param xmlWriter       write to use
   * @param discreteDemands demands to extract from
   */
  protected void writeMatsimPopulationXML(XMLStreamWriter xmlWriter, DiscreteDemands discreteDemands){

    try{
      // population
      writeStartElementNewLine(xmlWriter, MatsimPlansElements.POPULATION, true /* add indentation*/);

      //todo: add CRS attribute

      writeMatsimPersons(xmlWriter, discreteDemands);

      writeEndElementNewLine(xmlWriter, true /* undo indentation */ );
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error while writing MATSim population XML element");
    }
  }

  /**
   * write the xml MATSIM network
   *
   * @param discreteDemands to draw from
   * @param asGZip flag indicating whether to write out as gzipped XML or not
   */
  protected void writeXmlPlansFile(DiscreteDemands discreteDemands, boolean asGZip){
    Path matsimPlansPath =
        Paths.get(getSettings().getOutputDirectory(), getSettings().getFileName().concat(DEFAULT_FILE_NAME_EXTENSION));
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair =
        PlanitXmlWriterUtils.createXMLWriter(matsimPlansPath, asGZip);

    try {
      /* start */
      PlanitXmlWriterUtils.startXmlDocument(xmlFileWriterPair.first(), PLANS_DOCTYPE);

      /* body */
      writeMatsimPopulationXML(xmlFileWriterPair.first(), discreteDemands);

      /* end */
      PlanitXmlWriterUtils.endXmlDocument(xmlFileWriterPair);
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      throw new PlanItRunTimeException(String.format("error while persisting MATSIM plans to %s", matsimPlansPath));
    }
  }

  /**
   * Validate
   * @param demands to validate
   * @return true when ok, false otherwise
   */
  private boolean validate(DiscreteDemands demands) {
    if(getReferenceNetwork() == null){
      LOGGER.severe("Matsim plans require a reference PLANit network, not available,  unable to persist demands");
      return false;
    }
    if(getReferenceNetwork().getTransportLayers().size() != 1){
      LOGGER.severe(String.format("Matsim plans require a PLANit network with a single layer, but found (%d), " +
          "unable to persist demands", getReferenceNetwork().getTransportLayers().size()));
      return false;
    }

    if(getReferenceZoning() == null){
      LOGGER.severe("Matsim plans require a reference PLANit zoning, not available,  unable to persist demands");
      return false;
    }

    if(demands == null){
      LOGGER.severe("Matsim plans require PLANit discrete demands to be non-null,  " +
          "unable to persist demands");
      return false;
    }

    if(demands.getPersons().isEmpty()){
      LOGGER.severe("Matsim plans require at least one person present in PLANit discrete demands,  " +
          "unable to persist demands");
      return false;
    }

    if(demands.getTimePeriods().isEmpty()){
      LOGGER.severe("Matsim plans require a time period to be set in PLANit discrete demands,  " +
          "unable to persist demands");
      return false;
    }

    if(demands.getTimePeriods().size()>1){
      LOGGER.severe("Matsim plans require at most one time period to be set in PLANit discrete demands,  " +
          "unable to persist demands");
      return false;
    }

    return true;
  }

  /**
   * Initialise before writing, prep the CRS and prep the strategy on how to map activities spatially and temporally,
   * also identify the modes which may be present in the network but are not activated for persisting, so we can
   * prune those schedules from the output
   *
   * @param demands to work with
   */
  private void initialise(DiscreteDemands demands) {

    /* CRS - we allow for conversion but this requires source crs of both zoning and network to be known and set*/
    LOGGER.info(String.format("Network CRS set to     : %s",referenceNetwork.getCoordinateReferenceSystem().getName()));
    if(referenceZoning.getCoordinateReferenceSystem() == null){
      LOGGER.warning(String.format(
          "Zoning has no coordinate reference system set, assuming source is same as Network CRS (%s)",
          referenceNetwork.getCoordinateReferenceSystem().getName()));
      referenceZoning.setCoordinateReferenceSystem(referenceNetwork.getCoordinateReferenceSystem());
    }
    LOGGER.info(String.format("Zoning CRS set to     : %s", referenceZoning.getCoordinateReferenceSystem().getName()));
    prepareCoordinateReferenceSystem(
        referenceZoning.getCoordinateReferenceSystem(),
        getSettings().getDestinationCoordinateReferenceSystem(),
        getSettings().getCountry(),
        true);

    // identify persons to exclude based on deactivated mode legs
    var deactivatedModes = findDeactivatedModesAvailableInNetwork();
    if(!deactivatedModes.isEmpty()){
      this.personSchedulesToIgnore =
          PersonUtils.findPersonsWithScheduleElementsContaining(demands.getPersons(), deactivatedModes);
      if(!personSchedulesToIgnore.isEmpty()) {
        LOGGER.info(String.format("Excluding %d persons with at least one trip with a deactivated mode",
            personSchedulesToIgnore.size()));
      }
    }else{
      this.personSchedulesToIgnore = Collections.emptySet();
    }

    // Pre-populate length weights tracking if distance weighting is chosen
    if (getSettings().getLocationGeneratorType() == LocationGeneratorType.ZONE_LINKS_DISTANCE_WEIGHTED) {

      var activatedAvailableModes = findActivatedModesAvailableInNetwork();
      if(!activatedAvailableModes.isEmpty()){
        LOGGER.info("[START] Pre-indexing structural metric zone-to-link length arrays for random allocations...");
        zoneLinkWeightsIndexByMode = new TreeMap<>();
        for(var currMode : activatedAvailableModes){
          LOGGER.info(String.format("[%s] Pre-indexing...", currMode.getPredefinedModeType()));
          this.randomEngine = new SplittableRandom(DEFAULT_SIMULATION_SEED);
          var zoneLinkWeightsIndex = LocationGeneratorUtils.populateZoneLinkWeightsIndex(
              currMode, referenceNetwork, referenceZoning.getOdZones(), getGeoUtils());
          this.zoneLinkWeightsIndexByMode.put(currMode, zoneLinkWeightsIndex);
        }
        LOGGER.info("[DONE] Pre-indexed structural metric zone-to-link length arrays for random allocations...");
      }
    }

  }

  /**
   * Find modes in network that are not activated for persistence
   *
   * @return modes not activated for persistence
   */
  private Set<Mode> findDeactivatedModesAvailableInNetwork() {
    var modeMappings = getSettings().collectActivatedPlanitModeToMatsimModeMapping(
        getReferenceNetwork().getTransportLayers().getFirst());
    return getReferenceNetwork().getModes().stream().filter(
        m -> m.isPredefinedModeType() && !modeMappings.containsKey(m)).collect(Collectors.toSet());
  }

  /**
   * Find modes in network that are activated for persistence
   *
   * @return modes activated for persistence that are also available in the network
   */
  private Set<Mode> findActivatedModesAvailableInNetwork() {
    var modeMappings = getSettings().collectActivatedPlanitModeToMatsimModeMapping(
        getReferenceNetwork().getTransportLayers().getFirst());
    return getReferenceNetwork().getModes().stream().filter(
        m -> m.isPredefinedModeType() && modeMappings.containsKey(m)).collect(Collectors.toSet());
  }

  /**
   * Constructor
   *
   * @param settings to use
   * @param network network
   * @param zoning zoning
   */
  protected MatsimDiscreteDemandsWriter(
      MatsimDiscreteDemandsWriterSettings settings, final MacroscopicNetwork network, final Zoning zoning) {
    super(IdMapperType.ID);

    /* config settings for writer are found here */
    this.settings = settings;
    this.referenceNetwork = network;
    this.referenceZoning = zoning;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void write(DiscreteDemands demands) {
    boolean valid = validate(demands);
    if(!valid) {
      return;
    }
    boolean settingsValid = validateSettings();
    if(!settingsValid) {
      return;
    }

    /* id mapping */
    getComponentIdMappers().populateMissingIdMappers(getIdMapperType());

    /* log configuration */
    settings.logSettings(getReferenceNetwork(), 0);

    /* prep */
    initialise(demands);

    /* write */
    writeXmlPlansFile(demands, getSettings().isWriteAsGZip());

    LOGGER.info(this.writerStats.toString());
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    writerStats.reset();
    zoneLinkWeightsIndexByMode.clear();
    randomEngine = null;
    personSchedulesToIgnore.clear();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MatsimDiscreteDemandsWriterSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DiscreteDemandsIdMapper getPrimaryIdMapper() {
    return getComponentIdMappers().getDiscreteDemandsIdMapper();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Zoning getReferenceZoning() {
    return referenceZoning;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setReferenceNetwork(MacroscopicNetwork referenceNetwork) {
    this.referenceNetwork = referenceNetwork;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setReferenceZoning(Zoning referenceZoning) {
    this.referenceZoning = referenceZoning;
  }
}