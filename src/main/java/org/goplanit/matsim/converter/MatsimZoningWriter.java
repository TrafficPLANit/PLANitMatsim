package org.goplanit.matsim.converter;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.matsim.util.MatsimStopFacilityIdHelper;
import org.goplanit.matsim.xml.MatsimAttributes;
import org.goplanit.matsim.xml.MatsimNetworkAttributes;
import org.goplanit.matsim.xml.MatsimNetworkElements;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.Vertex;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.xml.PlanitXmlWriterUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.goplanit.utils.id.IdMapperType;

import org.goplanit.converter.idmapping.ZoningIdMapper;
import org.goplanit.converter.zoning.ZoningWriter;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.connectoid.TransferConnectoid;
import org.goplanit.zoning.Zoning;

/**
 * A class that takes a PLANit zoning and extracts and writes the MATSIM public transport information to disk. Since
 * a PLANit zoning only contains information about stops, a MATsim zoning writer is rather limited in outs outputs.
 * It can only support MATSim stops and a matrix based assignment on the MATSim side.
 *
 * @author markr
 *
 */
class MatsimZoningWriter extends MatsimWriter<Zoning> implements ZoningWriter, MatsimTransferAccessWriter{

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(MatsimZoningWriter.class.getCanonicalName());


  /**
   * Identify the transfer zones that cannot be reached by road, which MATSim needs to get anyone to or from a stop
   * once pt is enabled.
   * <p>
   * Rail platforms and ferry terminals are reachable only when access/egress entries towards the road network were
   * created for them. Bus stops always are, their pt vehicle stop entry being on a road. An entry's modes tell which
   * network it attaches to, so the network is not traversed.
   * </p>
   * <p>
   * Some stops not being connected is normal in parsed data: a stop near the edge of an extract may have no road
   * within reach, so those are reported and skipped. None being connected means the connections were never made, and
   * there is no point writing a pt network nobody can enter.
   * </p>
   * <p>
   * The missing connections are not created here: PLANit can create them, so that stays with whoever builds the
   * zoning.
   * </p>
   *
   * @param zoning to inspect
   * @return the transfer zones without road based access, to be left out when writing
   */
  private static Set<TransferZone> identifyTransferZonesWithoutRoadAccess(Zoning zoning) {
    var connectoidsByAccessZone = zoning.getTransferConnectoids().createIndexByAccessZone();
    var unconnected = connectoidsByAccessZone.entrySet().stream()
        .filter(e -> e.getKey() instanceof TransferZone)
        .filter(e -> e.getValue().stream().noneMatch(MatsimZoningWriter::hasRoadBasedAccess))
        .map(e -> (TransferZone) e.getKey()).collect(Collectors.toSet());
    if(unconnected.isEmpty()) {
      return unconnected;
    }

    /* bracketed per zone, each of which lists multiple ids itself, so where one ends and the next starts is clear */
    final String unconnectedIds = unconnected.stream().map(
        tz -> String.format("(%s)", tz.getIdsAsString())).collect(Collectors.joining(","));
    if(unconnected.size() == connectoidsByAccessZone.size()) {
      LOGGER.severe(String.format("None of the %d transfer zones has access to road based infrastructure, so no stop " +
          "can be reached, unable to write a MATSim network fit for pt", unconnected.size()));
      LOGGER.severe("Connect them while reading via setConnectRailBasedStopsToPassengerNetwork, " +
          "setConnectFerryStopsToNearbyLandNetwork and setConnectBusBasedStopsToPassengerNetwork, or afterwards via " +
          "ZoningUtils.injectTransferZoneAccessEgress(network, zoning, settings)");
      throw new PlanItRunTimeException("No transfer zone has road based access, abort");
    }

    LOGGER.warning(String.format("%d of %d transfer zones have no access to road based infrastructure and are " +
            "excluded, pt trips to or from them are not possible: %s",
        unconnected.size(), connectoidsByAccessZone.size(), unconnectedIds));
    return unconnected;
  }

  /** Verify whether any access zone entry of the connectoid grants a road based mode, meaning the stop can be
   * reached from the road network
   *
   * @param connectoid to check
   * @return true when road based access is present
   */
  private static boolean hasRoadBasedAccess(TransferConnectoid connectoid) {
    for(var entry : connectoid) {
      if(!entry.hasExplicitlyAllowedModes()) {
        /* nothing is restricted, so road based modes are permitted too */
        return true;
      }
      if(entry.getExplicitlyAllowedModes().stream().anyMatch(Mode::isRoadMode)) {
        return true;
      }
    }
    return false;
  }

  /** reference network to use */
  private MacroscopicNetwork referenceNetwork;

  /** the zoning writer settings used for the MATSim pt component*/
  private final MatsimZoningWriterSettings zoningWriterSettings;

  /** track stats */
  private final MatsimZoningWriterStats writerStats = new MatsimZoningWriterStats();

  /** describes the transfer zone access to contribute to a MATSim network file, null when there is none to write */
  private TransferAccessBuilder transferAccessBuilder;

  /** whether stops must be reachable from the road network, which is not so when the modes written hold no road
   * based mode, walking included, see {@link #prepareTransferAccess} */
  private boolean stopsMustBeReachableByRoad = true;

  /**
   * validate if settings are complete and if not try to salve by adopting settings from the network where possible
   *
   * @return valid flag
   */
  private boolean validateSettings() {
    if(getSettings().getOutputDirectory() == null || getSettings().getOutputDirectory().isBlank()) {
      LOGGER.severe("MATSim zoning output directory not set, abort");
      return false;
    }
    return true;
  }

  /**
   * Prepare the transfer zone access for a MATSim network file about to be written.
   * <p>
   * A separate step because the network file is written before the zoning, so its content must be known ahead of
   * {@link #write(Zoning)}. Calling this again rebuilds the description; it holds no other state.
   * </p>
   * <p>
   * Stops need road access only when a road based mode, walking included, is written. When none is, such as a ferry
   * only network, no access is built, and stops without road access are neither left out nor a reason to abort.
   * </p>
   *
   * @param zoning to derive the transfer zone access from
   * @param networkLayer the layer being written, stops attached outside of it are left out
   * @param modesWritten the modes of the layer written to MATSim
   * @param minimumLinkLengthMeters to fall back on for a transfer link without a usable length
   */
  protected void prepareTransferAccess(
      Zoning zoning, MacroscopicNetworkLayer networkLayer, Collection<Mode> modesWritten,
      double minimumLinkLengthMeters) {
    this.transferAccessBuilder = null;
    this.stopsMustBeReachableByRoad = modesWritten.stream().anyMatch(Mode::isRoadMode);
    if(!stopsMustBeReachableByRoad) {
      LOGGER.info("No road based mode, walking included, is written, so stops need no access from the road network");
      return;
    }
    if(!getSettings().isWriteTransferZoneAccess()) {
      return;
    }

    /* the ids written here come from the zoning mappers, which the zoning write has not had a chance to set up yet
     * since the network file is written first. Only those are established, deliberately not everything, because the
     * parent network mappers do not exist until the network writer has run and would only be replaced again */
    if(getComponentIdMappers().getZoningIdMappers() == null) {
      getComponentIdMappers().setDedicatedIdMapper(new ZoningIdMapper(getIdMapperType()));
    }

    var excludedTransferZones = identifyTransferZonesWithoutRoadAccess(zoning);
    this.transferAccessBuilder = new TransferAccessBuilder(
        zoning,
        networkLayer,
        excludedTransferZones,
        getSettings(),
        minimumLinkLengthMeters,
        new PlanitJtsCrsUtils(getReferenceNetwork().getCoordinateReferenceSystem()));
  }

  /**
   * Access to zoning writer settings
   * @return settings
   */
  protected MatsimZoningWriterSettings getZoningWriterSettings() {
    return zoningWriterSettings;
  }

  /** constructor
   *
   * @param zoningWriterSettings to use
   * @param referenceNetwork mandatory reference network
   */
  protected MatsimZoningWriter(
      final MatsimZoningWriterSettings zoningWriterSettings,
      final MacroscopicNetwork referenceNetwork) {
    super(IdMapperType.ID);
    this.referenceNetwork = referenceNetwork;
    this.zoningWriterSettings = zoningWriterSettings;
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
  public void writeTransferAccessNodes(
      XMLStreamWriter xmlWriter,
      int indentLevel,
      Function<Point, Coordinate> toDestinationCrsCoordinate,
      DecimalFormat coordinateFormat) {
    if(transferAccessBuilder == null) {
      return;
    }

    try {
      var transferZoneIdMapper = getPrimaryIdMapper().getTransferZoneIdMapper();
      for(var transferNode : transferAccessBuilder.getTransferNodes()) {
        PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimNetworkElements.NODE, indentLevel);
        xmlWriter.writeAttribute(
            MatsimAttributes.ID, transferAccessBuilder.resolveNodeId(transferNode, transferZoneIdMapper));

        var coordinate = toDestinationCrsCoordinate.apply(transferNode.position);
        if(coordinate != null) {
          xmlWriter.writeAttribute(MatsimAttributes.X, coordinateFormat.format(coordinate.x));
          xmlWriter.writeAttribute(MatsimAttributes.Y, coordinateFormat.format(coordinate.y));
        }
        PlanitXmlWriterUtils.writeNewLine(xmlWriter);
      }
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error while writing MATSim transfer zone node XML element");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void writeTransferAccessLinks(
      XMLStreamWriter xmlWriter,
      int indentLevel,
      Map<Mode, String> planitModeToMatsimModeMapping,
      Function<Vertex, String> vertexIdMapper) {
    if(transferAccessBuilder == null) {
      return;
    }

    try {
      var transferZoneIdMapper = getPrimaryIdMapper().getTransferZoneIdMapper();
      for(var transferLink : transferAccessBuilder.getTransferLinks()) {
        PlanitXmlWriterUtils.writeEmptyElement(xmlWriter, MatsimAttributes.LINK, indentLevel);

        xmlWriter.writeAttribute(
            MatsimAttributes.ID,
            transferAccessBuilder.resolveLinkId(transferLink, vertexIdMapper, transferZoneIdMapper));
        xmlWriter.writeAttribute(
            MatsimNetworkAttributes.FROM,
            transferAccessBuilder.resolveFromNodeId(transferLink, vertexIdMapper, transferZoneIdMapper));
        xmlWriter.writeAttribute(
            MatsimNetworkAttributes.TO,
            transferAccessBuilder.resolveToNodeId(transferLink, vertexIdMapper, transferZoneIdMapper));
        xmlWriter.writeAttribute(
            MatsimNetworkAttributes.LENGTH, String.format("%.2f", transferLink.lengthMeters));
        xmlWriter.writeAttribute(
            MatsimNetworkAttributes.FREESPEED_METER_SECOND,
            String.format("%.2f", transferLink.freeSpeedMeterPerSecond));
        xmlWriter.writeAttribute(
            MatsimNetworkAttributes.CAPACITY_HOUR,
            String.format("%.1f", TransferAccessBuilder.TRANSFER_LINK_CAPACITY_PCU_H));
        xmlWriter.writeAttribute(
            MatsimNetworkAttributes.PERMLANES, String.valueOf(TransferAccessBuilder.TRANSFER_LINK_LANES));
        xmlWriter.writeAttribute(
            MatsimNetworkAttributes.MODES,
            String.join(",", transferAccessBuilder.resolveMatsimModes(transferLink, planitModeToMatsimModeMapping)));

        PlanitXmlWriterUtils.writeNewLine(xmlWriter);
      }
    } catch (XMLStreamException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error while writing MATSim transfer zone link XML element");
    }
  }


  /**
   * extract public transport information from PLANit zoning and use it to persist as much  of the MATSim
   * public transport xml's as possible
   *
   * @param zoning to use for MATSim pt persistence
   */
  @Override
  public void write(Zoning zoning){
    PlanItRunTimeException.throwIfNull(zoning,"Unable to persist MATSim transit schedule file when PLANit " +
        "zoning object is null");

    boolean networkValid = validateNetwork(getReferenceNetwork());
    if(!networkValid) {
      return;
    }
    boolean settingsValid = validateSettings();
    if(!settingsValid){
      return;
    }

    /* verified before writing anything, since a pt network nobody can enter is not worth persisting. Skipped when the
     * transfer access was already prepared, as that ran the same check and reporting it twice only confuses, and when
     * no road based mode is written, as then nobody reaches a stop by road */
    if(transferAccessBuilder == null && stopsMustBeReachableByRoad) {
      identifyTransferZonesWithoutRoadAccess(zoning);
    }

    /* log settings */
    getSettings().logSettings();

    /* CRS */
    prepareCoordinateReferenceSystem(
        getReferenceNetwork().getCoordinateReferenceSystem(),
        getSettings().getDestinationCoordinateReferenceSystem(),
        getSettings().getCountry(),
        true);

    // builds a mapping from PLANit to MATSim stop facility ids to use
    var stopFacilityIdMapper = new MatsimStopFacilityIdHelper(zoning.getTransferConnectoids());

    /* results in writing stops only*/
    new MatsimPtXmlWriter(this, stopFacilityIdMapper, writerStats.getPtWriterStats())
        .writeXmlTransitScheduleFileStopsOnly(zoning, getZoningWriterSettings());

    if(getSettings().isGenerateMatrixBasedPtRouterFiles()) {
      new MatsimPtMatrixBasedRouterWriter(this, stopFacilityIdMapper).write(zoning);
    }


  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    // the statistics of the last write are deliberately kept, they hold no more than a handful of counts
  }

  /**
   * The statistics collected over the most recent write, covering the public transport infrastructure written
   *
   * @return the statistics of the most recent write
   */
  public MatsimZoningWriterStats getWriterStats() {
    return writerStats;
  }

  /** Collect the zoning writer settings
   *
   * @return zoning writer settings
   */
  public MatsimZoningWriterSettings getSettings() {
    return zoningWriterSettings;
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
  public ZoningIdMapper getPrimaryIdMapper() {
    return getComponentIdMappers().getZoningIdMappers();
  }
}