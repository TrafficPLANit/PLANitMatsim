package org.goplanit.matsim.converter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import org.goplanit.matsim.util.MatsimBuiltInMode;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.Vertex;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.unit.Unit;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.connectoid.ConnectoidAccessZoneEntry;
import org.goplanit.utils.zoning.connectoid.TransferConnectoid;
import org.goplanit.utils.zoning.connectoid.ZoneConnectoidType;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Point;

/**
 * Derives the MATSim nodes and links needed to make PLANit transfer zones reachable, so a traveller can get off a train
 * or bus and continue on the road network, and the other way around.
 * <p>
 * PLANit models this transition as a transfer zone plus one or more transfer connectoids, where each connectoid access
 * zone entry records where the stop meets the network and which modes may use that entry. MATSim has no equivalent and
 * only understands nodes and links, so the transition has to be materialised as dummy infrastructure: one node per
 * transfer zone, and a pair of links per access node connecting that node to the physical network.
 * </p>
 * <p>
 * Nothing is inferred spatially. Only the entries PLANit already recorded are consumed, so a stop that was never
 * connected on the PLANit side stays unconnected here rather than being silently invented. Deciding which stops to
 * leave out, and reporting them, is the caller's to make and arrives as the excluded transfer zones.
 * </p>
 * <p>
 * The result is a plain description of what to write, deliberately holding no reference to an XML writer, since
 * deciding what the transition looks like belongs here whereas the file structure does not.
 * </p>
 *
 * @author markr
 */
class TransferAccessBuilder {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(TransferAccessBuilder.class.getCanonicalName());

  /** prefix for generated MATSim transfer node ids, keeping them clear of the physical node ids */
  private static final String TRANSFER_NODE_ID_PREFIX = "tz_";

  /** prefix for generated MATSim transfer link ids, keeping them clear of the physical link ids */
  private static final String TRANSFER_LINK_ID_PREFIX = "tzl_";

  /**
   * A dummy MATSim node standing in for a PLANit transfer zone. It has no counterpart in the physical PLANit network,
   * it exists only because MATSim cannot express a stop other than as a node
   */
  static class MatsimDummyTransferNode {

    /** position in the network's own CRS, the writer converts it to the destination CRS */
    final Point position;

    /** the transfer zone represented, held in memory model form so the id mappers can be applied to it unchanged at
     * the point of writing */
    final TransferZone transferZone;

    /** Constructor
     *
     * @param position to use
     * @param transferZone represented
     */
    MatsimDummyTransferNode(Point position, TransferZone transferZone) {
      this.position = position;
      this.transferZone = transferZone;
    }
  }

  /**
   * A dummy MATSim link standing in for one direction of the connection between a transfer zone and one of its access
   * nodes. It has no counterpart in the physical PLANit network, it represents the walk between a stop and the network
   * rather than infrastructure
   */
  static class MatsimDummyTransferLink {

    /** the transfer zone this link connects, this link's non physical end */
    final TransferZone transferZone;

    /** every access zone entry reaching the network at this access node, together deciding who may traverse the link.
     * Several entries can describe the same physical gap, a stop's pt vehicle entry and its access/egress entry often
     * sharing an access node, and those collapse onto this one link rather than becoming parallel duplicates */
    final List<ConnectoidAccessZoneEntry> entries;

    /** the PLANit access node the link attaches to */
    final Vertex accessVertex;

    /** true when the link runs from the transfer zone to the network, false for the opposite direction */
    final boolean fromTransferZone;

    /** length in meters, never zero so MATSim can derive a travel time */
    final double lengthMeters;

    /** free speed in meters per second */
    final double freeSpeedMeterPerSecond;

    /** Constructor
     *
     * @param transferZone connected
     * @param entries represented
     * @param accessVertex to use
     * @param fromTransferZone direction of the link
     * @param lengthMeters to use
     * @param freeSpeedMeterPerSecond to use
     */
    MatsimDummyTransferLink(
        TransferZone transferZone,
        List<ConnectoidAccessZoneEntry> entries,
        Vertex accessVertex,
        boolean fromTransferZone,
        double lengthMeters,
        double freeSpeedMeterPerSecond) {
      this.transferZone = transferZone;
      this.entries = entries;
      this.accessVertex = accessVertex;
      this.fromTransferZone = fromTransferZone;
      this.lengthMeters = lengthMeters;
      this.freeSpeedMeterPerSecond = freeSpeedMeterPerSecond;
    }
  }

  /** the dummy transfer nodes to write, one per eligible transfer zone */
  private final List<MatsimDummyTransferNode> transferNodes = new ArrayList<>();

  /** the dummy transfer links to write, two per access node a transfer zone reaches the network at */
  private final List<MatsimDummyTransferLink> transferLinks = new ArrayList<>();

  /** the zoning writer settings to use */
  private final MatsimZoningWriterSettings settings;

  /** minimum length in meters to fall back on when no length can be established */
  private final double minimumLinkLengthMeters;

  /** to compute distances with when an entry carries no length of its own */
  private final PlanitJtsCrsUtils geoUtils;


  /**
   * Determine where to put the MATSim node representing the transfer zone. The transfer zone geometry is preferred
   * since that is the physical stop, falling back on the connectoid's access node when the zone carries no geometry at
   * all, which at least keeps the node in the right place rather than dropping the stop entirely.
   *
   * @param transferZone to position
   * @param connectoid to fall back on
   * @return the position to use, null when neither is available
   */
  private static Point determineTransferNodePosition(TransferZone transferZone, TransferConnectoid connectoid) {
    if(transferZone.hasCentroid() && transferZone.getCentroid().hasPosition()) {
      return transferZone.getCentroid().getPosition();
    }
    var geometry = transferZone.getGeometry(true);
    if(geometry != null && !geometry.isEmpty()) {
      return geometry.getCentroid();
    }
    return connectoid.getReferenceVertex() != null ? connectoid.getReferenceVertex().getPosition() : null;
  }

  /**
   * Collect the MATSim modes for the links to a stop.
   * <p>
   * Walk is always present: it is how a passenger covers the gap between the platform and the vehicle, and so is what
   * a pt vehicle stop entry contributes here. Its mode names the vehicle serving the stop rather than anything that
   * traverses the walk to it, so it has no further say. Beyond walk the access and egress entries decide, they being
   * the record of which passengers can reach the stop and how, which is exactly what the link is being written for.
   * </p>
   *
   * @param entries to collect the allowed modes from
   * @param planitModeToMatsimModeMapping to map PLANit modes with, only holding modes activated for MATSim
   * @return the MATSim modes to write
   */
  private static Set<String> collectTransferLinkModes(
      List<ConnectoidAccessZoneEntry> entries, Map<Mode, String> planitModeToMatsimModeMapping) {
    var matsimModes = new LinkedHashSet<String>();
    matsimModes.add(MatsimBuiltInMode.WALK.getValue());

    for(var entry : entries) {
      if(entry.getType().equals(ZoneConnectoidType.PT_VEHICLE_STOP) || !entry.hasExplicitlyAllowedModes()) {
        continue;
      }
      for(var mode : entry.getExplicitlyAllowedModes()) {
        var matsimMode = planitModeToMatsimModeMapping.get(mode);
        if(matsimMode != null) {
          matsimModes.add(matsimMode);
        }
      }
    }
    return matsimModes;
  }

  /**
   * Determine the length of a transfer link in meters.
   * <p>
   * The recorded length is preferred, but it is regularly absent: pt vehicle stop entries are never given one by the
   * parsers, and the injector only sets one on the entries it creates itself. So the distance between the stop and its
   * access node is computed as a fallback. Even that can legitimately come out as zero when the stop sits exactly on
   * its access node, hence the minimum, since a zero length link makes MATSim divide by zero when deriving a travel
   * time. Note the recorded length is in km whereas everything returned here is in meters.
   * </p>
   *
   * @param entries to take the recorded length from, all describing the same physical gap
   * @param transferNodePosition position of the transfer zone side
   * @param accessVertex the network side
   * @return length in meters, always strictly positive
   */
  private double determineLengthMeters(
      List<ConnectoidAccessZoneEntry> entries, Point transferNodePosition, Vertex accessVertex) {
    /* the entries describe the same stop reaching the same access node, so any recorded length among them describes
     * the same gap, the smallest being the least likely to overstate the walk */
    var recordedLengthKm = entries.stream().map(ConnectoidAccessZoneEntry::getLengthKm)
        .filter(length -> length.isPresent() && length.get() > 0.0).map(Optional::get).min(Double::compare);
    if(recordedLengthKm.isPresent()) {
      return Unit.KM.convertTo(Unit.METER, recordedLengthKm.get());
    }

    double computedLengthMeters = 0.0;
    if(transferNodePosition != null && accessVertex != null && accessVertex.getPosition() != null) {
      computedLengthMeters = geoUtils.getDistanceInMetres(
          transferNodePosition.getCoordinate(), accessVertex.getPosition().getCoordinate());
    }
    return computedLengthMeters > 0.0 ? computedLengthMeters : minimumLinkLengthMeters;
  }

  /**
   * Register the two MATSim links for a single connectoid access zone entry, one for each direction. PLANit holds a
   * single link with up to two segments whereas MATSim only knows directional links, so the pair here mirrors how a
   * PLANit link segment maps onto a MATSim link elsewhere.
   *
   * @param transferNode the transfer zone side
   * @param accessVertex the network side
   * @param entries every entry of this zone reaching the network at that access node
   */
  private void registerTransferLinkPair(
      MatsimDummyTransferNode transferNode,
      Vertex accessVertex,
      List<ConnectoidAccessZoneEntry> entries) {

    double lengthMeters = determineLengthMeters(entries, transferNode.position, accessVertex);
    double freeSpeedMeterPerSecond = Unit.KM_HOUR.convertTo(
        Unit.METER_SECOND, settings.getTransferZoneAccessSpeedKmH());

    /* transfer zone --> network, the departing traveller */
    transferLinks.add(new MatsimDummyTransferLink(
        transferNode.transferZone, entries, accessVertex,
        true /* from transfer zone */, lengthMeters, freeSpeedMeterPerSecond));

    /* network --> transfer zone, the arriving traveller */
    transferLinks.add(new MatsimDummyTransferLink(
        transferNode.transferZone, entries, accessVertex,
        false /* towards transfer zone */, lengthMeters, freeSpeedMeterPerSecond));
  }

  /** Resolve the MATSim id of a transfer node. The prefix keeps it out of the physical node id namespace, which under
   * the default id mapping consists of plain numerics that a transfer zone id would otherwise clash with
   *
   * @param transferNode to resolve for
   * @param transferZoneIdMapper yields the id a transfer zone is written with
   * @return the MATSim node id
   */
  String resolveNodeId(MatsimDummyTransferNode transferNode, Function<TransferZone, String> transferZoneIdMapper) {
    return TRANSFER_NODE_ID_PREFIX + transferZoneIdMapper.apply(transferNode.transferZone);
  }

  /**
   * Resolve the MATSim id of a transfer link. A link is the connection between one transfer zone and one access node,
   * in one direction, which is exactly what identifies it. The prefix additionally keeps the whole set out of the
   * physical link id namespace
   *
   * @param link to resolve for
   * @param vertexIdMapper yields the MATSim id a physical access node is written with
   * @param transferZoneIdMapper yields the id a transfer zone is written with
   * @return the MATSim link id
   */
  String resolveLinkId(
      MatsimDummyTransferLink link,
      Function<Vertex, String> vertexIdMapper,
      Function<TransferZone, String> transferZoneIdMapper) {
    return String.format("%s%s_%s_%s",
        TRANSFER_LINK_ID_PREFIX,
        transferZoneIdMapper.apply(link.transferZone),
        vertexIdMapper.apply(link.accessVertex),
        link.fromTransferZone ? "ab" : "ba");
  }

  /**
   * Resolve the MATSim modes allowed on a transfer link. Deferred to the point of writing because the mapping from
   * PLANit to MATSim modes belongs to the network being written, while the policy of which modes may traverse a
   * transfer link belongs here
   *
   * @param link to resolve for
   * @param planitModeToMatsimModeMapping to map PLANit modes with
   * @return the MATSim modes to write
   */
  Set<String> resolveMatsimModes(MatsimDummyTransferLink link, Map<Mode, String> planitModeToMatsimModeMapping) {
    return collectTransferLinkModes(link.entries, planitModeToMatsimModeMapping);
  }

  /** Resolve the MATSim id of the node this link runs from
   *
   * @param link to resolve for
   * @param vertexIdMapper yields the MATSim id a physical access node is written with
   * @param transferZoneIdMapper yields the id a transfer zone is written with
   * @return the MATSim node id
   */
  String resolveFromNodeId(
      MatsimDummyTransferLink link,
      Function<Vertex, String> vertexIdMapper,
      Function<TransferZone, String> transferZoneIdMapper) {
    return link.fromTransferZone
        ? TRANSFER_NODE_ID_PREFIX + transferZoneIdMapper.apply(link.transferZone)
        : vertexIdMapper.apply(link.accessVertex);
  }

  /** Resolve the MATSim id of the node this link runs to
   *
   * @param link to resolve for
   * @param vertexIdMapper yields the MATSim id a physical access node is written with
   * @param transferZoneIdMapper yields the id a transfer zone is written with
   * @return the MATSim node id
   */
  String resolveToNodeId(
      MatsimDummyTransferLink link,
      Function<Vertex, String> vertexIdMapper,
      Function<TransferZone, String> transferZoneIdMapper) {
    return link.fromTransferZone
        ? vertexIdMapper.apply(link.accessVertex)
        : TRANSFER_NODE_ID_PREFIX + transferZoneIdMapper.apply(link.transferZone);
  }

  /**
   * Capacity written on transfer links. A walking transfer imposes no meaningful vehicular capacity, so this is a
   * nominal constant rather than something for a user to tune.
   */
  static final double TRANSFER_LINK_CAPACITY_PCU_H = 9999.0;

  /** Number of lanes written on transfer links, nominal for the same reason as the capacity */
  static final int TRANSFER_LINK_LANES = 1;

  /**
   * Constructor, deriving everything to be written up front so nodes and links can be emitted in the two separate
   * passes the MATSim file format requires while the generated ids stay consistent between them.
   *
   * @param zoning to derive the transfer access from
   * @param networkLayer the layer being written, used to skip stops whose access node is not part of it
   * @param excludedTransferZones transfer zones to leave out, typically those without road based access
   * @param settings to take the transfer link free speed from
   * @param minimumLinkLengthMeters to fall back on when no length can be established
   * @param geoUtils to compute distances with
   */
  TransferAccessBuilder(
      Zoning zoning,
      MacroscopicNetworkLayer networkLayer,
      Set<TransferZone> excludedTransferZones,
      MatsimZoningWriterSettings settings,
      double minimumLinkLengthMeters,
      PlanitJtsCrsUtils geoUtils) {

    this.settings = settings;
    this.minimumLinkLengthMeters = minimumLinkLengthMeters;
    this.geoUtils = geoUtils;

    var connectoidsByAccessZone = zoning.getTransferConnectoids().createIndexByAccessZone();
    int skippedDetachedConnectoids = 0;

    for(var zoneWithConnectoids : connectoidsByAccessZone.entrySet()) {
      if(!(zoneWithConnectoids.getKey() instanceof TransferZone)) {
        continue;
      }
      var transferZone = (TransferZone) zoneWithConnectoids.getKey();
      if(excludedTransferZones.contains(transferZone)) {
        continue;
      }

      var connectoids = zoneWithConnectoids.getValue();
      if(connectoids.isEmpty()) {
        continue;
      }

      var position = determineTransferNodePosition(transferZone, connectoids.iterator().next());
      if(position == null) {
        LOGGER.warning(String.format(
            "Transfer zone (%s) has no position available for its MATSim node, stop excluded",
            transferZone.getIdsAsString()));
        continue;
      }

      var transferNode = new MatsimDummyTransferNode(position, transferZone);
      int transferLinksBefore = transferLinks.size();

      /* grouped by access node rather than kept per entry, since a stop's pt vehicle entry and its access/egress
       * entry commonly reach the network at the same node and would otherwise become parallel duplicates */
      var entriesByAccessVertex = new LinkedHashMap<Vertex, List<ConnectoidAccessZoneEntry>>();
      for(var connectoid : connectoids) {
        var accessVertex = connectoid.getReferenceVertex();
        /* mode withdrawal and dangling subnetwork removal can have taken the access node out of the layer, in which
         * case it is not part of the written network and a link referencing it would dangle */
        if(accessVertex == null || networkLayer.getNodes().get(accessVertex.getId()) != accessVertex) {
          ++skippedDetachedConnectoids;
          continue;
        }
        for(var entry : connectoid) {
          /* a connectoid can provide access to several zones, so only the entries of the zone currently being
           * processed may be used, otherwise this node ends up connected through another zone's entry */
          if(!transferZone.equals(entry.getAccessZone())) {
            continue;
          }
          entriesByAccessVertex.computeIfAbsent(accessVertex, v -> new ArrayList<>()).add(entry);
        }
      }

      /* one pair of links per access node, however many entries reach the network there */
      entriesByAccessVertex.forEach(
          (accessVertex, accessZoneEntries) ->
              registerTransferLinkPair(transferNode, accessVertex, accessZoneEntries));

      if(transferLinks.size() == transferLinksBefore) {
        /* no usable connectoid remained, so the node would be isolated */
        continue;
      }
      transferNodes.add(transferNode);
    }

    if(skippedDetachedConnectoids > 0) {
      LOGGER.info(String.format(
          "Skipped %d transfer connectoid(s) whose access node is no longer part of the written network layer",
          skippedDetachedConnectoids));
    }
  }

  /** Collect the dummy MATSim nodes to write for the transfer zones
   *
   * @return transfer nodes
   */
  List<MatsimDummyTransferNode> getTransferNodes() {
    return transferNodes;
  }

  /** Collect the dummy MATSim links to write for the transfer zone access
   *
   * @return transfer links
   */
  List<MatsimDummyTransferLink> getTransferLinks() {
    return transferLinks;
  }
}
