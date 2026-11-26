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
 * Adds the MATSim nodes and links that make PLANit transfer zones reachable to a MATSim network file another writer is
 * writing.
 * <p>
 * Transfer zones are part of the zoning, so the zoning writer decides what their access looks like. The nodes and
 * links must still go in the MATSim network file, as MATSim has no other place for them. The network writer calls
 * this at the right two points and passes the writing context, without knowing what a transfer zone is.
 * </p>
 * <p>
 * The context is passed in, not taken from the implementation's own state, because both writers hold their own
 * coordinate reference system, indentation and id mappings, and only the network writer has them in the right state
 * while its nodes and links are written.
 * </p>
 * <p>
 * This is internal wiring between the MATSim writers, not for users to implement or call. It is public only because
 * the writers are in different packages.
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
