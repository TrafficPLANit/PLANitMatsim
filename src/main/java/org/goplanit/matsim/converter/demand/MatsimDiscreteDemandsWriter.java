package org.goplanit.matsim.converter.demand;

import org.goplanit.converter.demands.DiscreteDemandsWriter;
import org.goplanit.converter.idmapping.DiscreteDemandsIdMapper;
import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.demands.discrete.person.Person;
import org.goplanit.demands.discrete.tour.ScheduleElement;
import org.goplanit.demands.discrete.tour.Tour;
import org.goplanit.demands.discrete.trip.Trip;
import org.goplanit.demands.discrete.util.DirectionBound;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.matsim.xml.MatsimAttributes;
import org.goplanit.matsim.xml.MatsimPlansAttributes;
import org.goplanit.matsim.xml.MatsimPlansElements;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.goplanit.zoning.Zoning;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A class that takes a PLANit DiscreteDemands and writes it as a MATSIM plans (v5) file.
 * 
 * @author markr
  */
public class MatsimDiscreteDemandsWriter extends MatsimWriter<DiscreteDemands> implements DiscreteDemandsWriter{

  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimDiscreteDemandsWriter.class.getCanonicalName());

  /** ref network */
  private final MacroscopicNetwork referenceNetwork;

  /** ref zoning */
  private final Zoning referenceZoning;

  /** track stats */
  private MatsimPlansWriterStats writerStats = new MatsimPlansWriterStats();

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
   * Log some aggregate stats on the MATSim writer regarding the number of elements persisted
   */
  private void logWriterStats() {
    // todo
    //LOGGER.info(String.format("[STATS] created %d plans",matsimNodeCounter.longValue()));
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
   * @param xmlWriter to use
   * @param currentElement  to use
   * @param precedingElement to use
   * @param person to use
   * @throws XMLStreamException if error
   */
  private void checkForActivityElementBetweenScheduleElements(
      XMLStreamWriter xmlWriter,
      ScheduleElement currentElement,
      ScheduleElement precedingElement,
      Person person)
      throws XMLStreamException {

    // Case 1: outbound trip followed by inbound trip --> activity occurs in between the two trips now
    //         NOTE: we check this in this way to allow for chained outbound trips
    //         this exhausts the use of the trip's tour purpose in one go - no further nesting
    if((currentElement instanceof Trip) && (precedingElement instanceof Trip) &&
        ((Trip)precedingElement).getDirection() == DirectionBound.OUTBOUND &&
        ((Trip)currentElement).getDirection() == DirectionBound.INBOUND){
      var upcomingTrip = ((Trip)currentElement);
      writeActivityElement(xmlWriter, person, upcomingTrip.getTour().getPurpose(), upcomingTrip.getStartTime());
      writeIndentation(xmlWriter);
    }

    // Case 2: sub tour: activity from preceding tour occurs in between arrival from inbound trip and
    //                   start of this tour. Arrival at this location is based on
    //                   parent tour (this is interspersed), the location resides at the parent tour's
    //                   destination so use that purpose
    if((currentElement instanceof Tour) && (precedingElement instanceof Trip) &&
        ((Trip)precedingElement).getDirection() == DirectionBound.OUTBOUND){
      var currTour = ((Tour)currentElement);
      // purpose from parent, if no parent, we have to assume this is the op level tour, so locale is person's initial
      // purpose instead
      var thePurpose = currTour.hasParentTour() ? currTour.getParentTour().getPurpose() : person.getInitialPurpose();
      writeActivityElement(xmlWriter, person, thePurpose, currentElement.getStartTime());
      writeIndentation(xmlWriter);
    }

    // Case 3: returning back to origin from destination location AFTER just coming back to destination location from
    //         subtour --> purpose is inbound trip's tour purpose, and end time of activity at tour's destination is
    //         the trip's start time
    if((currentElement instanceof Trip) && ((Trip)currentElement).getDirection() == DirectionBound.INBOUND
        && (precedingElement instanceof Tour)){
      var inboundTripItsTour = ((Trip) currentElement).getTour();
      // purpose from inbound trip its tour
      writeActivityElement(xmlWriter, person, inboundTripItsTour.getPurpose(), currentElement.getStartTime());
      writeIndentation(xmlWriter);
    }

    // Case 4: starting a new tour after a preceding tour has finished fully and we have spent time waiting
    //         in between. In that case, the purpose is that of the parent, and the end time of the activity is the
    //         start time of the outbound trip
    if((currentElement instanceof Tour) && (precedingElement instanceof Tour)){
      var currTour = ((Tour)currentElement);
      var thePurpose = currTour.hasParentTour() ?
          currTour.getParentTour().getPurpose() : person.getInitialPurpose();
      writeActivityElement(xmlWriter, person, thePurpose, currTour.getStartTime());
      writeIndentation(xmlWriter);
    }

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
    var startTimeSeconds = scheduleElement.getStartTime().toSecondOfDay();
    if(startTimeSeconds < periodStartTimeSeconds || startTimeSeconds > periodEndTimeSeconds){
      //ignore outside of time period
      return;
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

            checkForActivityElementBetweenScheduleElements(xmlWriter, tourScheduleElement, prevTourScheduleElement, person);

            // delegate one level deeper
            processScheduleElement(
                xmlWriter, tourScheduleElement, person, modeMapping, periodStartTimeSeconds, periodEndTimeSeconds);

            prevTourScheduleElement = tourScheduleElement;

          }

//          // purpose from parent, if no parent, we have to assume this is the op level tour,
//          // so locale is person's initial purpose instead
//          boolean isTopLevel = !currTour.hasParentTour();
//          var thePurpose = isTopLevel? person.getInitialPurpose(): currTour.getParentTour().getPurpose();
//          // end time is (i) period end time when top level and this is last tour of person, (ii) tour end time
//          // otherwise
//          var theEndTime = currTour.getEndTime();
//          if(isTopLevel){
//            if(person.getSchedule().getLast(false).equals(currTour)) {
//              theEndTime = LocalTime.ofSecondOfDay(periodEndTimeSeconds - 1);
//            }else{
//
//            }
//          }
//          writeActivityElement(
//              xmlWriter,
//              person,
//              thePurpose,
//              theEndTime);
//          if(!isTopLevel){
//            writeIndentation(xmlWriter);
//          }

        }

      }else{
        LOGGER.severe(String.format("Unsupported person schedule element type, skip, " +
            "should not happen, and may result in invalid plan for person (%s)", person.getIdsAsString()));
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
   * @param endTime end time of activity
   * @throws XMLStreamException if error
   */
  private void writeActivityElement(
      XMLStreamWriter xmlWriter, Person person, String activityDescription, LocalTime endTime)
      throws XMLStreamException {
    if(activityDescription == null || activityDescription.isBlank()){
      LOGGER.warning(String.format("Description for activity has no content (for person (%s))",
          person.getIdsAsString()));
    }
    // activity end point
    xmlWriter.writeEmptyElement(MatsimPlansElements.ACTIVITY);
    // purpose -> activity type
    xmlWriter.writeAttribute(MatsimAttributes.TYPE, activityDescription);
    // end time of activity
    xmlWriter.writeAttribute(
        MatsimPlansAttributes.END_TIME, endTime.format(MatsimWriter.HHmmssFormat));

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

    try{
      // plan
      writeStartElement(xmlWriter, MatsimPlansElements.PLAN, true /* add indentation*/);
      // selected=yes
      xmlWriter.writeAttribute(MatsimPlansAttributes.SELECTED, "yes");
      writeNewLine(xmlWriter);
      writeIndentation(xmlWriter);

      var initialActivity = person.getSchedule().getFirst();
      writeActivityElement(
          xmlWriter,
          person,
          person.getInitialPurpose(),
          initialActivity.getStartTime() /* end time of idle activity */);
      writeIndentation(xmlWriter);

      // track the schedule of the person to extract activities and travel leg information in MATSim format
      ScheduleElement prevElement = null;
      for(int index=0;index< person.getSchedule().size(); index++){
        var scheduleElement = person.getSchedule().get(index);

        checkForActivityElementBetweenScheduleElements(xmlWriter, scheduleElement, prevElement, person);

        /* now we proceed with each travel involved activity */
        processScheduleElement(
            xmlWriter, scheduleElement, person, modeMapping, periodStartTimeSeconds, periodEndTimeSeconds);

        prevElement = scheduleElement;
      }

      writeActivityElement(
            xmlWriter, person, person.getInitialPurpose(), LocalTime.ofSecondOfDay(periodEndTimeSeconds - 1));

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
        {
          // attributes
          writeStartElementNewLine(xmlWriter, MatsimPlansElements.ATTRIBUTES, true /* add indentation*/);
          //todo: support custom attributes that we can pass through but have no functional meaning in PLANit
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
      throw new PlanItRunTimeException(String.format("error while persisting MATSIM plans to %s", matsimPlansPath));
    }
  }

  /**
   * Validate
   * @param demands to validate
   * @return true when ok, false otherwise
   */
  private boolean validateDemands(DiscreteDemands demands) {
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
    boolean valid = validateDemands(demands);
    if(!valid) {
      return;
    }
    boolean settingsValid = validateSettings();
    if(!settingsValid) {
      return;
    }

    /* id mapping */
    getComponentIdMappers().populateMissingIdMappers(getIdMapperType());

    /* log settings */
    settings.logSettings(getReferenceNetwork());
    
    /* write */
    writeXmlPlansFile(demands, getSettings().isWriteAsGZip());

    logWriterStats();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
//    matsimNodeCounter.reset();
//    matsimLinkCounter.reset();
//    matsimTurnRestrictionCounter.reset();
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
  public MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
  }
}
