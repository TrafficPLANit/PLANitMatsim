package org.goplanit.matsim.converter.demand;

import org.goplanit.converter.demands.DiscreteDemandsWriter;
import org.goplanit.converter.idmapping.DiscreteDemandsIdMapper;
import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.demands.discrete.person.Person;
import org.goplanit.demands.discrete.tour.ScheduleElement;
import org.goplanit.demands.discrete.tour.Tour;
import org.goplanit.demands.discrete.trip.Trip;
import org.goplanit.matsim.converter.MatsimWriter;
import org.goplanit.matsim.xml.MatsimAttributes;
import org.goplanit.matsim.xml.MatsimPlansAttributes;
import org.goplanit.matsim.xml.MatsimPlansElements;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.goplanit.zoning.Zoning;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
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
   * Process a schedule element
   *
   * @param xmlWriter              to use
   * @param scheduleElement        to process
   * @param person                 for this element
   * @param periodStartTimeSeconds period start time
   * @param periodEndTimeSeconds   period end time
   */
  private void processScheduleElement(
      XMLStreamWriter xmlWriter,
      ScheduleElement scheduleElement,
      Person person,
      long periodStartTimeSeconds,
      long periodEndTimeSeconds) {
    var startTimeSeconds = scheduleElement.getStartTime().toSecondOfDay();
    if(startTimeSeconds < periodStartTimeSeconds || startTimeSeconds > periodEndTimeSeconds){
      //ignore outside of time period
      return;
    }

    try{

      if(scheduleElement instanceof Trip){
        // leaf
        var tripElement = (Trip) scheduleElement;
        // trips are always a travel leg, the activities come from the tours

      }else if(scheduleElement instanceof Tour){
        // nest
        var tourElement = (Tour) scheduleElement;

        if(tourElement.hasSchedule()){
          processScheduleElement(xmlWriter, tourElement, person, periodStartTimeSeconds, periodEndTimeSeconds);
        }

        writeActivityElement(xmlWriter, person, tourElement.getPurpose(), tourElement.getEndTime());


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
  }

  /**
   * Write the plan element (selected=yes) and content for a given person. We currently only write out a single
   * assumed selected plan per person
   *
   * @param xmlWriter to use
   * @param person to write the selected plan for
   * @param discreteDemands to use
   */
  private void writePersonPlan(XMLStreamWriter xmlWriter, Person person, DiscreteDemands discreteDemands) {
    var timePeriod = discreteDemands.getTimePeriods().getFirst();
    long periodStartTimeSeconds = timePeriod.getStartTimeSeconds();
    long periodEndTimeSeconds = periodStartTimeSeconds + timePeriod.getDurationSeconds();
    var homeZone = person.getHousehold().getZone();

    try{
      // plan
      writeStartElement(xmlWriter, MatsimPlansElements.PLAN, true /* add indentation*/);
      // selected=yes
      xmlWriter.writeAttribute(MatsimPlansAttributes.SELECTED, "yes");
      writeNewLine(xmlWriter);

      // track the schedule of the person to extract acitivities and travel leg information in MATSim format
      ScheduleElement previousScheduleElement = null;
      Long previousScheduleElementEndTimeSeconds = null;
      for(var scheduleElement : person.getSchedule()){

        // bootstrap initial activity, this should start with a tour
        if(scheduleElement instanceof Trip){
          LOGGER.severe(String.format("Each initial schedule element of a person (%s) should be a tour " +
              "found trip, skip, should not happen", person.getIdsAsString()));
          return;
        }
        // MATSim initial activity end time is start time of the tour (runs from start of simulation to start of tour
        // the purpose of this cannot be obtained from this initial tour's purpose, instead we use the inbound
        // trip purpose
        var initialTour = (Tour)scheduleElement;
        Trip finalInboundTrip = (Trip) initialTour.getSchedule().getLast(true /*flattened*/);
        continue here, make sure trips also have a purpose, e.g. the activity following arrival
        writeActivityElement(xmlWriter, person, finalInboundTrip.getPurpose(), initialTour.getStartTime());

        processScheduleElement(xmlWriter, scheduleElement, person, periodStartTimeSeconds, periodEndTimeSeconds);

      }

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
    try{

      for(var person : discreteDemands.getPersons()){
        if(person.getHousehold() == null){
          LOGGER.warning(String.format(
              "Currently MATSim plan writer requires person (%s) to have a household, missing, skip",
              person.getIdsAsString()));
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
        writePersonPlan(xmlWriter, person, discreteDemands);

        writeEndElementNewLine(xmlWriter, true /* undo indentation */ );
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
