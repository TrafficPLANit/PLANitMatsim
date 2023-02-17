package org.goplanit.matsim.converter;

import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.goplanit.converter.IdMapperFunctionFactory;
import org.goplanit.matsim.xml.MatsimTransitAttributes;
import org.goplanit.matsim.xml.MatsimTransitElements;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.collections.ListUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.service.routed.*;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.goplanit.utils.zoning.Connectoid;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.DirectedConnectoids;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.opengis.referencing.operation.TransformException;

/**
 * Class that takes on the responsibility of writing all PT XML based files for a given PLANit memory model
 *
 * <p>
 *   useful explanation of MATSim xml transit schedule format
 *   <a href="https://matsim.atlassian.net/wiki/spaces/MATPUB/pages/83099693/Transit+Tutorial?focusedCommentId=151191553">here</a>
 * </p>
 *
 * 
 * @author markr
 *
 */
class MatsimPtXmlWriter {
  
  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimPtXmlWriter.class.getCanonicalName());
  
  /** the zoning writer used for the MATSim pt component*/
  private final MatsimWriter<?> matsimWriter;
  
  /** track number of MATSim stop facilities persisted */
  private final LongAdder matsimStopFacilityCounter = new LongAdder();

  /** track number of MATSim transit lines persisted */
  private final LongAdder matsimTransitLineCounter = new LongAdder();

  /** track transit routes persisted by mapped MAtsim mode */
  private Map<String, LongAdder> transitRouteCountersByMode = new HashMap<>();

  /** track all id mappings by type of PLANit entity */
  private Map<Class<?>, Function<?, String>> managedIdMappings;

  /** track stop facility ids via this map */
  private Map<Integer, Integer> stopFacilityIdTracking = new HashMap<>();

  /* internal flag to avoid unnecessary repeat of warnings */
  private boolean loggedFrequencyTripWarning;

  private static final DateTimeFormatter HHmmssFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

  /** get the id mapping required
   *
   * @param clazz to collect for
   * @return id mapping function
   * @param <T> type of clazz
   */
  private <T> Function<T, String> getIdMapping(Class<T> clazz){
    return (Function<T, String>) managedIdMappings.get(clazz);
  }

  /** based on access link segment and whether the stop is up or downstream determine the stop facility id (which internally we create and track).
   * This is needed because only the combination of link segment and node determines a unique stop facility as we might have two stops on the same link segment (one up and one downstream)
   *
   * @param accessLinkSegment to use
   * @param nodeAccessDownstream to use
   */
  private int getStopFacilityId(LinkSegment accessLinkSegment, boolean nodeAccessDownstream) {
    int key = Math.toIntExact(nodeAccessDownstream ? accessLinkSegment.getId() : -accessLinkSegment.getId());
    Integer stopFacilityId = stopFacilityIdTracking.get(key);
    if(stopFacilityId == null){
      stopFacilityIdTracking.put(key, stopFacilityIdTracking.values().size());
      return stopFacilityIdTracking.get(key);
    }
    return stopFacilityId;
  }

  /**
   * Prepare the id mapping functionality for persistence based on on user chosen mapping approach
   */
  private void initialiseIdMappers(){
    managedIdMappings = new HashMap<>();
    managedIdMappings.put(Connectoid.class, IdMapperFunctionFactory.createConnectoidIdMappingFunction(matsimWriter.getIdMapperType()));
    managedIdMappings.put(MacroscopicLinkSegment.class, IdMapperFunctionFactory.createLinkSegmentIdMappingFunction(matsimWriter.getIdMapperType()));
    managedIdMappings.put(RoutedService.class, IdMapperFunctionFactory.createRoutedServiceIdMappingFunction(matsimWriter.getIdMapperType()));
    managedIdMappings.put(RoutedTripSchedule.class, IdMapperFunctionFactory.createRoutedTripScheduleIdMappingFunction(matsimWriter.getIdMapperType()));
  }

  /**
   * persisting MATSim transit route's route profile stop
   *
   * @param xmlWriter            to use
   * @param relLegTiming         to persist
   * @param cumulativeTravelTime to reach this stop
   * @param upstreamStop         indicates that stop to persist resides upstream of the service leg segment
   * @param servicesSettings     to use
   * @return true when success, false otherwise
   * @throws XMLStreamException when error
   */
  private boolean writeMatsimRouteProfileStop(
      XMLStreamWriter xmlWriter,
      RelativeLegTiming relLegTiming,
      LocalTime cumulativeTravelTime,
      boolean upstreamStop,
      MatsimPtServicesWriterSettings servicesSettings) throws XMLStreamException {

    if(!relLegTiming.hasParentLegSegment() || !relLegTiming.getParentLegSegment().hasPhysicalParentSegments()){
      LOGGER.warning("IGNORE: Found PLANit relative leg timing with missing service leg segment or missing underlying physical link segments, unable to create stop XML Element, should not happen");
      return false;
    }

    /* stop */
    matsimWriter.writeStartElement(xmlWriter, MatsimTransitElements.STOP, false);

    /* ref id <-- stop facility ref id is based on macroscopic link segment and node location, see #writeMatsimStopFacility */
    var physicalLinkSegmentsOfLeg = relLegTiming.getParentLegSegment().getPhysicalParentSegments();
    var accessLinkSegment = upstreamStop ? ListUtils.getFirst(physicalLinkSegmentsOfLeg) : ListUtils.getLastValue(physicalLinkSegmentsOfLeg);
    xmlWriter.writeAttribute(MatsimTransitAttributes.REF_ID, String.valueOf(getStopFacilityId(accessLinkSegment, upstreamStop)));

    /* arrivalOffset */
    if(!upstreamStop){
      /* only relevant for NOT the very first leg (first leg we assume is the only one corresponding to an upstream stop)*/
      xmlWriter.writeAttribute(MatsimTransitAttributes.ARRIVAL_OFFSET, cumulativeTravelTime.format(HHmmssFormat));
    }

    /* departureOffset */
    xmlWriter.writeAttribute(MatsimTransitAttributes.DEPARTURE_OFFSET, cumulativeTravelTime.plusNanos(relLegTiming.getDwellTime().toNanoOfDay()).format(HHmmssFormat));

    /* awaitDeparture */
    xmlWriter.writeAttribute(MatsimTransitAttributes.AWAIT_DEPARTURE, String.valueOf(servicesSettings.isAwaitDepartures()));
    xmlWriter.writeEndElement();

    PlanitXmlWriterUtils.writeNewLine(xmlWriter);
    return true;
  }

  /**
   * persisting MATSim transit route's route profile ( PLANit trip schedule of a routed service)
   *
   * @param xmlWriter        to use
   * @param tripSchedule     to persist
   * @param servicesSettings to use
   * @throws XMLStreamException when error
   */
  private boolean writeMatsimRouteProfile(XMLStreamWriter xmlWriter, RoutedTripSchedule tripSchedule, MatsimPtServicesWriterSettings servicesSettings) throws XMLStreamException {
    if(!tripSchedule.hasRelativeLegTimings()){
      LOGGER.warning("IGNORE: Found PLANit trip schedule without leg timings, unable to create routeProfile XML Element, should not happen");
      return false;
    }

    /* routeProfile*/
    matsimWriter.writeStartElementNewLine(xmlWriter, MatsimTransitElements.ROUTE_PROFILE, true);

    boolean first = true;
    boolean success = first;
    LocalTime cumulativeTravelTime = LocalTime.MIN;
    for(var timing : tripSchedule){
      if(first){
        success = writeMatsimRouteProfileStop(xmlWriter, timing, cumulativeTravelTime, first, servicesSettings);
        first = false;
      }
      cumulativeTravelTime = cumulativeTravelTime.plusNanos(timing.getDwellTime().toNanoOfDay()).plusNanos(timing.getDuration().toNanoOfDay());
      success = success && writeMatsimRouteProfileStop(xmlWriter, timing, cumulativeTravelTime, first, servicesSettings);
      if(!success){
        break;
      }
    }

    //TODO --> continue here with departures!!!!

    matsimWriter.writeEndElementNewLine(xmlWriter, true);
    return success;
  }

  /**
   * persisting MATSim transit route ( PLANit trip schedule of a routed service)
   *
   * @param xmlWriter           to use
   * @param routedServicesLayer to use
   * @param mode                mode of the schedule
   * @param tripsSchedule       to persist
   * @param servicesSettings    to use
   * @throws XMLStreamException when error
   */
  private boolean writeMatsimTransitRoute(XMLStreamWriter xmlWriter, RoutedServicesLayer routedServicesLayer, Mode mode, RoutedTripsSchedule tripsSchedule, MatsimPtServicesWriterSettings servicesSettings) throws XMLStreamException {
    var modeMapping = servicesSettings.getNetworkSettings().collectActivatedPlanitModeToMatsimModeMapping(
        (MacroscopicNetworkLayerImpl) routedServicesLayer.getParentLayer().getParentNetworkLayer());

    boolean success = true;
    var scheduleIdMapping = getIdMapping(RoutedTripSchedule.class);
    for(var tripSchedule : tripsSchedule){
      var mappedMode = modeMapping.get(mode);
      if(StringUtils.isNullOrBlank(mappedMode)){
        LOGGER.warning(String.format("trip schedule (id: %s external id: %s) has no mapped mode, ignore", tripSchedule.getXmlId(), tripSchedule.getExternalId()));
        continue;
      }

      /* transitRoute*/
      matsimWriter.writeStartElement(xmlWriter, MatsimTransitElements.TRANSIT_ROUTE, true);

      /*id */
      xmlWriter.writeAttribute(MatsimTransitAttributes.ID, scheduleIdMapping.apply(tripSchedule));
      PlanitXmlWriterUtils.writeNewLine(xmlWriter);

      /* transportMode */
      PlanitXmlWriterUtils.writeElementWithValueWithNewLine(xmlWriter, MatsimTransitElements.TRANSPORT_MODE, mappedMode ,matsimWriter.getIndentLevel());
      transitRouteCountersByMode.get(mappedMode).increment();

      /* routeProfile */
      success = writeMatsimRouteProfile(xmlWriter, tripSchedule, servicesSettings);

      matsimWriter.writeEndElementNewLine(xmlWriter, true);

      if(!success){
        break;
      }
    }
    return success;
  }

  /**
   * persisting MATSim transit lines
   *
   * @param xmlWriter           to use
   * @param routedServicesLayer to use
   * @param routedService       to persist
   * @param servicesSettings    to use
   */
  private void writeMatsimTransitLine(XMLStreamWriter xmlWriter, RoutedServicesLayer routedServicesLayer, RoutedService routedService, MatsimPtServicesWriterSettings servicesSettings) {
    if(!routedService.getTripInfo().hasScheduleBasedTrips() && !loggedFrequencyTripWarning){
      LOGGER.warning("Found frequency based PLANit routed services. These are ignored in persisting MATSim transit lines due to absence of schedule");
      loggedFrequencyTripWarning = true;
      return;
    }

    try {
      /* transitLine*/
      matsimWriter.writeStartElement(xmlWriter, MatsimTransitElements.TRANSIT_LINE, true);
      matsimTransitLineCounter.increment();

      /*id */
      xmlWriter.writeAttribute(MatsimTransitAttributes.ID, getIdMapping(RoutedService.class).apply(routedService));

      /* name */
      if(routedService.hasName() || routedService.hasNameDescription()){
        var name = routedService.hasName() ? routedService.getName() : routedService.getNameDescription();
        xmlWriter.writeAttribute(MatsimTransitAttributes.NAME, name);
      }

      PlanitXmlWriterUtils.writeNewLine(xmlWriter);

      /* transitRoute (PLANit trip schedule) */
      boolean success = writeMatsimTransitRoute(xmlWriter, routedServicesLayer, routedService.getMode(), routedService.getTripInfo().getScheduleBasedTrips(), servicesSettings);

      matsimWriter.writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // transit schedule
      if(!success){
        LOGGER.warning(String.format("Unable to complete a transitroute part transitLine %s as expected, XML likely incomplete or corrupted for this entry", getIdMapping(RoutedService.class).apply(routedService)));
      }
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error while writing MATSim transitLine XML element");
    }
  }

  /**
   * write the transit lines which we extract from the PLANit routed services
   *
   * @param xmlWriter               to use
   * @param routedServices          to use
    * @param servicesSettings       to use
   */
  private void writeMatsimTransitLines(XMLStreamWriter xmlWriter, RoutedServices routedServices, MatsimPtServicesWriterSettings servicesSettings) {
    transitRouteCountersByMode.clear();
    /* reset counters per mapped mode */
    routedServices.getLayers().forEach( layer ->
        servicesSettings.getNetworkSettings().collectActivatedPlanitModeToMatsimModeMapping(
            (MacroscopicNetworkLayerImpl) layer.getParentLayer().getParentNetworkLayer()).entrySet().forEach(
            e -> transitRouteCountersByMode.put(e.getValue(), new LongAdder())));

    for(var routedServicesLayer : routedServices.getLayers()){
      var supportedModes = routedServicesLayer.getSupportedModes();
      if(supportedModes == null){
        LOGGER.warning(String.format("IGNORE routed service layer %s has no supported modes", routedServicesLayer.getXmlId()));
        continue;
      }

      for(var mode : supportedModes){
        var servicesByMode = routedServicesLayer.getServicesByMode(mode);
        if(servicesByMode.isEmpty()){
          continue;
        }

        servicesByMode.forEach( rs -> writeMatsimTransitLine(xmlWriter, routedServicesLayer, rs, servicesSettings));
      }
    }

  }
    

  /**
   * write the transit stops (stop facilities) which we extract from the planit directed connectoids
   *
   * @param xmlWriter            to use
   * @param zoning               to use
   * @param zoningWriterSettings to use
   */
  private void writeMatsimTransitStops(XMLStreamWriter xmlWriter, Zoning zoning, MatsimZoningWriterSettings zoningWriterSettings) {
    try {
      matsimWriter.writeStartElementNewLine(xmlWriter,MatsimTransitElements.TRANSIT_STOPS, true /* add indentation*/);
           
      /* directed connectoids as stop facilities */      
      writeMatsimStopFacilities(xmlWriter, zoning.getTransferConnectoids(), zoningWriterSettings);
                  
      matsimWriter.writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // transit schedule
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error while writing MATSim transitStops XML element");
    }
  }

  /**
   * write the stop facilities based on the available planit transfer connectoids
   *
   * @param xmlWriter            to use
   * @param transferConnectoids  to convert to stop facilities
   * @param zoningWriterSettings to use
   */
  private void writeMatsimStopFacilities(
      XMLStreamWriter xmlWriter, DirectedConnectoids transferConnectoids, MatsimZoningWriterSettings zoningWriterSettings){
    for(var transferConnectoid : transferConnectoids) {
      writeMatsimStopFacility(xmlWriter, transferConnectoid, zoningWriterSettings);
      matsimStopFacilityCounter.increment();
    }
  }

  /**
   * write the stop facility based on the available planit transfer connectoids
   *
   * @param xmlWriter            to use
   * @param transferConnectoid   to convert to stop facility
   * @param zoningWriterSettings to use
   */
  private void writeMatsimStopFacility(XMLStreamWriter xmlWriter, DirectedConnectoid transferConnectoid, MatsimZoningWriterSettings zoningWriterSettings) {
    try {
      PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimTransitElements.STOP_FACILITY, matsimWriter.getIndentLevel());
            
      /* attributes  of element*/
      {
        MacroscopicLinkSegment accessLinkSegment = (MacroscopicLinkSegment) transferConnectoid.getAccessLinkSegment();
        if(accessLinkSegment == null) {
          LOGGER.severe(String.format("DISCARD: stop facility represented by directed connectoid (%d) has no access link segment available",transferConnectoid.getId()));
          return;
        }

        /* ID:
         * We map to tracked stop facility id based on link segment+node location. We can't use connectoid ids because multiple connectoids
         * might map to the same access link segment. We also cannot use transfer zone ids because there, the same id might access multiple stop facilities (connectoids).
         * We also cannot use a service network node because either we might not have those (in case we are persisting without services), or if we do, then we can have multiple
         * incoming link segments leading to a non-unique mapping to the underlying physical network which is required in a MATSim context. The only option is to use combination
         * of link segment + physical node location
         */
        xmlWriter.writeAttribute(MatsimTransitAttributes.ID, String.valueOf(getStopFacilityId(accessLinkSegment, transferConnectoid.isNodeAccessDownstream())));

        /* We use the indicated vertex of the access link segment as the stop location */
        var stopFacilityPhysicalReferenceNode = transferConnectoid.isNodeAccessDownstream() ? transferConnectoid.getAccessLinkSegment().getDownstreamNode() : transferConnectoid.getAccessLinkSegment().getUpstreamNode();
        Point stopFacilityLocation = stopFacilityPhysicalReferenceNode.getPosition();
        
        Coordinate nodeCoordinate = matsimWriter.extractDestinationCrsCompatibleCoordinate(stopFacilityLocation);
        if(nodeCoordinate != null) {        
          /* X */
          xmlWriter.writeAttribute(MatsimTransitAttributes.X, matsimWriter.getSettings().getDecimalFormat().format(nodeCoordinate.x));
          /* Y */
          xmlWriter.writeAttribute(MatsimTransitAttributes.Y, matsimWriter.getSettings().getDecimalFormat().format(nodeCoordinate.y));
          /* Z coordinate (v2) not supported */
        }
        
        /* LINK REF ID */
        xmlWriter.writeAttribute(MatsimTransitAttributes.LINK_REF_ID, getIdMapping(MacroscopicLinkSegment.class).apply(accessLinkSegment));
        
        /* NAME - based on the transfer zone names if any */
        String stopFacilityName = "";
        for(Zone transferZone : transferConnectoid.getAccessZones())
          if(transferZone.hasName()) {
            if(!stopFacilityName.isBlank()) {
              stopFacilityName.concat("-");
            }
            stopFacilityName = stopFacilityName.concat(transferZone.getName());
        }
        if(!stopFacilityName.isBlank()) {
          xmlWriter.writeAttribute(MatsimTransitAttributes.NAME, stopFacilityName);
        }
        
        /* STOP_AREA_ID (v2) - not supported yet in MATSIM I believe, when it is, we can use our transfer zone groups to map these */
        
        /* IS_BLOCKING - unknown information in PLANit at this point */
        xmlWriter.writeAttribute(MatsimTransitAttributes.IS_BLOCKING, String.valueOf(zoningWriterSettings.isPtBlockingAtStopFacility()));
      }
      
      PlanitXmlWriterUtils.writeNewLine(xmlWriter);
    } catch (XMLStreamException | TransformException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("error while writing MATSim stopFacility element id:%d",transferConnectoid.getId());
    }
  }

  /**
   * Log some aggregate stats on the MATSim writer regarding the number of elements persisted
   */
  private void logWriterStats() {
    LOGGER.info(String.format("[STATS] created %d stop facilities",matsimStopFacilityCounter.longValue()));
    LOGGER.info(String.format("[STATS] created %d transit lines", matsimTransitLineCounter.longValue()));
    for(var entry : transitRouteCountersByMode.entrySet()) {
      LOGGER.info(String.format("[STATS] created %d transit routes for mode: %s", entry.getValue().longValue(), entry.getKey()));
    }
  }   

  /** Starting point for persisting the MATSim transit schedule file (infrastructure, e.g., stops and stations, only)
   * 
   * @param zoning to persist
   * @param zoningWriterSettings to use
   * @param routedServices to persist (if not null)
   * @param routedServicesSettings to use
   */
  protected void writeXmlTransitScheduleFile(
      Zoning zoning, MatsimZoningWriterSettings zoningWriterSettings, RoutedServices routedServices, MatsimPtServicesWriterSettings routedServicesSettings) {

    /* prep */
    initialiseIdMappers();
    transitRouteCountersByMode.clear();
    matsimStopFacilityCounter.reset();
    matsimTransitLineCounter.reset();
    stopFacilityIdTracking.clear();

    Path matsimNetworkPath =  Paths.get(matsimWriter.getSettings().getOutputDirectory(), matsimWriter.getSettings().getOutputFileName().concat(MatsimWriter.DEFAULT_FILE_NAME_EXTENSION));
    Pair<XMLStreamWriter,Writer> xmlFileWriterPair = PlanitXmlWriterUtils.createXMLWriter(matsimNetworkPath);

    try {
      /* start */
      PlanitXmlWriterUtils.startXmlDocument(xmlFileWriterPair.first(), MatsimZoningWriter.TRANSIT_SCHEDULE_DOCTYPE);
      
      /* body */
      loggedFrequencyTripWarning = false;
      writeTransitScheduleXML(xmlFileWriterPair.first(), zoning, zoningWriterSettings, routedServices, routedServicesSettings);
      
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException(String.format("Error while persisting MATSIM public transit schedule to %s", matsimNetworkPath));
    }finally {
      
      /* end */
      try {
        PlanitXmlWriterUtils.endXmlDocument(xmlFileWriterPair);
      }catch(Exception e) {
        LOGGER.severe("Unable to finalise XML document after PLANit exception");
      }
    }
    
    logWriterStats();
  }

  /** convert the PLANit public transport infrastructure to MATSim transit schedule XML
   * @param xmlWriter to use
   * @param zoning to use
   * @param zoningWriterSettings to use
   * @param routedServices to use
   * @param servicesSettings to use
   */
  protected void writeTransitScheduleXML(
      XMLStreamWriter xmlWriter, Zoning zoning, MatsimZoningWriterSettings zoningWriterSettings, RoutedServices routedServices, MatsimPtServicesWriterSettings servicesSettings) {
    try {
      matsimWriter.writeStartElementNewLine(xmlWriter,MatsimTransitElements.TRANSIT_SCHEDULE, true /* add indentation*/);
      

      /* directed connectoids as stop facilities */
      writeMatsimTransitStops(xmlWriter, zoning, zoningWriterSettings);

      if(routedServices != null){
        writeMatsimTransitLines(xmlWriter, routedServices, servicesSettings);
      }
                  
      matsimWriter.writeEndElementNewLine(xmlWriter, true /* undo indentation */ ); // transit schedule
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("error while writing MATSIM transitSchedule XML element");
    }
  }

  /**
   * Constructor 
   * 
   * @param matsimWriter to use
   */
  public MatsimPtXmlWriter(final MatsimWriter<?> matsimWriter) {
    this.matsimWriter = matsimWriter;
  }
}
