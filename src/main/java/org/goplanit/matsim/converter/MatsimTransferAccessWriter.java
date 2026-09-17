package org.goplanit.matsim.converter;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.function.Function;

import javax.xml.stream.XMLStreamWriter;

import org.goplanit.utils.graph.Vertex;
import org.goplanit.utils.mode.Mode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

/**
 * Contributes the MATSim nodes and links that make PLANit transfer zones reachable to a MATSim network file that is
 * being written by someone else.
 * <p>
 * Transfer zones belong to the zoning half of the PLANit memory model, so deciding what their access looks like sits
 * with whoever writes the zoning. The nodes and links expressing it however have to end up in the MATSim network file,
 * because MATSim has no separate place to put them. This interface is that hand-over: the network file's author invites
 * the contribution at the right two points and supplies the writing context, without needing to know what a transfer
 * zone is.
 * </p>
 * <p>
 * The writing context is passed in rather than taken from the implementation's own state on purpose. Both writers hold
 * their own coordinate reference system, indentation and id mappings, and only the network file's author has those in
 * the correct state at the moment its nodes and links are being written.
 * </p>
 * <p>
 * This is internal wiring between the MATSim writers rather than something to be implemented or invoked by a user. It
 * is public only because the writers involved live in different packages.
 * </p>
 *
 * @author markr
 */
public interface MatsimTransferAccessWriter {

  /**
   * Write the MATSim nodes representing the transfer zones, to be invoked while the network file's nodes are being
   * written
   *
   * @param xmlWriter to write to
   * @param indentLevel the indentation the surrounding node elements use
   * @param toDestinationCrsCoordinate converts a position to a coordinate in the file's destination CRS
   * @param coordinateFormat to format the coordinates with, so they match the surrounding nodes
   */
  public abstract void writeTransferAccessNodes(
      XMLStreamWriter xmlWriter,
      int indentLevel,
      Function<Point, Coordinate> toDestinationCrsCoordinate,
      DecimalFormat coordinateFormat);

  /**
   * Write the MATSim links connecting the transfer zones to their access nodes in the physical network, to be invoked
   * while the network file's links are being written
   *
   * @param xmlWriter to write to
   * @param indentLevel the indentation the surrounding link elements use
   * @param planitModeToMatsimModeMapping to map the modes allowed on the access with
   * @param vertexIdMapper yields the MATSim id a physical access node is written with, so the links refer to the same
   *                       nodes the network file itself wrote
   */
  public abstract void writeTransferAccessLinks(
      XMLStreamWriter xmlWriter,
      int indentLevel,
      Map<Mode, String> planitModeToMatsimModeMapping,
      Function<Vertex, String> vertexIdMapper);
}
