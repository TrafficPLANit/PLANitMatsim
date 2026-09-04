package org.goplanit.matsim.converter;

import java.text.DecimalFormat;
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
import org.goplanit.utils.mode.TrackModeType;
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

  /** reference network to use */
  private MacroscopicNetwork referenceNetwork;

  /** the zoning writer settings used for the MATSim pt component*/
  private final MatsimZoningWriterSettings zoningWriterSettings;

  /** describes the transfer zone access to contribute to a MATSim network file, null when there is none to write */
  private TransferAccessBuilder transferAccessBuilder;

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
   * Identify the transfer zones that cannot be reached from road based infrastructure, which MATSim needs to get
   * anyone to or from a stop once pt is enabled.
   * <p>
   * Rail platforms and ferry terminals only qualify when access/egress entries towards the road network were created
   * for them. Bus stops qualify trivially, their pt vehicle stop entry already being on a road. The modes on an entry
   * are what tells us which network it attaches to, so no traversal of the network is needed here.
   * </p>
   * <p>
   * Individual stops that could not be connected are a fact of parsed data, a stop near the edge of an extract may
   * simply have no road within reach, so those are reported and skipped. None of them being connected is a different
   * matter, that means the connections were never made at all, and there is no point writing a pt network nobody can
   * enter.
   * </p>
   * <p>
   * We deliberately do not create the missing connections here. PLANit provides that capability, so it stays with
   * whoever builds the zoning.
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
      if(entry.getExplicitlyAllowedModes().stream().anyMatch(
          m -> m.getPhysicalFeatures().getTrackType().equals(TrackModeType.ROAD))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Prepare the transfer zone access so it can be contributed to a MATSim network file that is about to be written.
   * <p>
   * Done as a separate step because the network file is written before the zoning is, so what goes into it has to be
   * known ahead of {@link #write(Zoning)}. Calling this more than once simply rebuilds the description, it holds no
   * state beyond it.
   * </p>
   *
   * @param zoning to derive the transfer zone access from
   * @param networkLayer the layer being written, stops attached outside of it are left out
   * @param minimumLinkLengthMeters to fall back on for a transfer link without a usable length
   */
  void prepareTransferAccess(Zoning zoning, MacroscopicNetworkLayer networkLayer, double minimumLinkLengthMeters) {
    this.transferAccessBuilder = null;
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
   * Access to zoning writer settings
   * @return settings
   */
  MatsimZoningWriterSettings getZoningWriterSettings() {
    return zoningWriterSettings;
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
     * transfer access was already prepared, as that ran the same check and reporting it twice only confuses */
    if(transferAccessBuilder == null) {
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
    new MatsimPtXmlWriter(this, stopFacilityIdMapper).writeXmlTransitScheduleFileStopsOnly(
        zoning, getZoningWriterSettings());

    if(getSettings().isGenerateMatrixBasedPtRouterFiles()) {
      new MatsimPtMatrixBasedRouterWriter(this, stopFacilityIdMapper).write(zoning);
    }


  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    //TODO:
  }

  /** Collect the zoning writer settings
   *
   * @return zoning writer settings
   */
  public MatsimZoningWriterSettings getSettings() {
    return zoningWriterSettings;
  }

  /** Collect the reference network used
   *
   * @return reference network
   */
  protected MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
  }

  /** Set the reference network compatible with the zoning
   * @param referenceNetwork to use
   */
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