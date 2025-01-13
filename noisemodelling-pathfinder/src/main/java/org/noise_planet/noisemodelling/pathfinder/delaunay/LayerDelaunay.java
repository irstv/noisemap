/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder.delaunay;

import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

/*
 * This interface aims to link the acoustic module with many delaunay library,
 * to easy switch between libraries
 * @author Nicolas Fortin
 */
public interface LayerDelaunay {
	/**
	 * This optional method give an hint of the size of the delaunay process.
	 * Call this method before the first call of addPolygon This method is used
	 * only for optimization.
	 * 
	 * @param boundingBox Bounding box of the delaunay mesh
	 * @param polygonCount Size of the polygon count
	 */
	void hintInit(Envelope boundingBox, long polygonCount, long verticesCount)
			throws LayerDelaunayError;

    /**
     * Append a polygon into the triangulation
     *
     * @param newPoly Polygon to append into the mesh, internal rings will be inserted as holes.
     * @param attribute Polygon attribute. {@link Triangle#getAttribute()}
     */
    void addPolygon(Polygon newPoly,int attribute) throws LayerDelaunayError;

	/**
	 * Append a vertex into the triangulation
	 * 
	 * @param vertexCoordinate coordinate of the new vertex
	 */
	void addVertex(Coordinate vertexCoordinate) throws LayerDelaunayError;


	void addLineString(LineString line, int attribute) throws LayerDelaunayError;

	/**
	 * Set the minimum angle, if you wish to enforce the quality of the delaunay
	 * Call processDelauney after to take account of this method.
	 * 
	 * @param minAngle Minimum angle in radiant
	 */
	void setMinAngle(Double minAngle) throws LayerDelaunayError;

	/**
	 * Set the maximum area in m² Call processDelauney after to take account of
	 * this method.
	 * 
	 * @param maxArea Maximum area in m²
	 */
	void setMaxArea(Double maxArea) throws LayerDelaunayError;

	/**
	 * Launch delaunay process
	 */
	void processDelaunay() throws LayerDelaunayError;

	/**
	 * When the processDelaunay has been called, retrieve results vertices
	 */
	List<Coordinate> getVertices() throws LayerDelaunayError;

	/**
	 * When the processDelaunay has been called, retrieve results Triangle link
	 * unique vertices by their index.
	 */
	List<Triangle> getTriangles() throws LayerDelaunayError;
	/**
	 * When the processDelaunay has been called, retrieve results Triangle link
	 * triangles neighbor by their index.
	 */
	List<Triangle> getNeighbors() throws LayerDelaunayError;
	/**
	 * Remove all data, come back to the constructor state
	 */
	void reset();
	/**
	 * Enable or Disable the collecting of triangles neighboring data.
	 * @param retrieve
	 */
	public void setRetrieveNeighbors(boolean retrieve);
}
