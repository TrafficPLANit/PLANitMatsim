package org.goplanit.matsim.test;

import java.util.List;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.layer.macroscopic.AccessGroupPropertiesFactory;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.goplanit.utils.network.layer.physical.Node;

/**
 * Builds small hand-made networks in tests: the network with its layer, and the link segment types, nodes and link
 * segments on that layer, each with its id as XML id so the writers can refer to it
 *
 * @author markr
 */
public final class NetworkLayerTestUtils {

  /**
   * Create a network in a Cartesian coordinate reference system with the given predefined modes and one layer that
   * supports all of them
   *
   * @param modeTypes the predefined modes of the network
   * @return the network
   */
  public static MacroscopicNetwork createCartesianNetworkWithOneLayer(PredefinedModeType... modeTypes) {
    var network = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken());
    network.setCoordinateReferenceSystem(PlanitJtsCrsUtils.CARTESIANCRS);
    for (var modeType : modeTypes) {
      network.getModes().getFactory().registerNew(modeType);
    }
    network.getTransportLayers().getFactory().registerNew(network.getModes());
    return network;
  }

  /**
   * Register a link segment type with the default maximum density, allowing the given modes up to a maximum speed
   *
   * @param layer to register the link segment type on
   * @param name of the link segment type
   * @param capacityPcuPerHour of the link segment type
   * @param maxSpeedKmH the modes may drive at on it
   * @param modes allowed on it
   * @return the link segment type
   */
  public static MacroscopicLinkSegmentType registerLinkSegmentTypeAllowing(
      MacroscopicNetworkLayer layer, String name, double capacityPcuPerHour, double maxSpeedKmH, Mode... modes) {
    var type = layer.getLinkSegmentTypes().getFactory().registerNew(
        name, capacityPcuPerHour, MacroscopicLinkSegmentType.DEFAULT_MAX_DENSITY_PER_LANE);
    type.setXmlId(String.valueOf(type.getId()));
    AccessGroupPropertiesFactory.createOnLinkSegmentType(type, maxSpeedKmH, List.of(modes));
    return type;
  }

  /**
   * Register a node at a position with its id as XML id
   *
   * @param layer to register the node on
   * @param x of the position
   * @param y of the position
   * @return the node
   */
  public static Node registerNodeAt(MacroscopicNetworkLayer layer, double x, double y) {
    return layer.getNodes().getFactory().registerNew(PlanitJtsUtils.createPoint(x, y), true);
  }

  /**
   * Register a link from one node to another, with a straight geometry and a length of 0.1 km, and one link segment in
   * that direction
   *
   * @param layer to register the link and its link segment on
   * @param from the node the link and its link segment start at
   * @param to the node the link and its link segment end at
   * @param type of the link segment
   * @return the link segment
   */
  public static MacroscopicLinkSegment registerLinkWithOneSegment(
      MacroscopicNetworkLayer layer, Node from, Node to, MacroscopicLinkSegmentType type) {
    var link = layer.getLinks().getFactory().registerNew(from, to, 0.1, true);
    link.setXmlId(String.valueOf(link.getId()));
    link.setGeometry(PlanitJtsUtils.createLineString(
        from.getPosition().getCoordinate(), to.getPosition().getCoordinate()));
    var segment = layer.getLinkSegments().getFactory().registerNew(link, true, true);
    segment.setXmlId(String.valueOf(segment.getId()));
    segment.setLinkSegmentType(type);
    return segment;
  }
}
