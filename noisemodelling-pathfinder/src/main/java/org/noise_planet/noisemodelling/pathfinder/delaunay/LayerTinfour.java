/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */


package org.noise_planet.noisemodelling.pathfinder.delaunay;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.io.WKTWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinfour.common.*;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.TriangleCollector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class LayerTinfour implements LayerDelaunay {
    private double epsilon = 0.001; // merge of Vertex instances below this distance
    private static final Logger LOGGER = LoggerFactory.getLogger(LayerTinfour.class);
    public String dumpFolder = "";

    List<IConstraint> constraints = new ArrayList<>();
    List<Integer> constraintIndex = new ArrayList<>();

    Quadtree ptsIndex = new Quadtree();
    private boolean computeNeighbors = false;
    private double maxArea = 0;

    // Output data
    private List<Coordinate> vertices = new ArrayList<Coordinate>();
    private List<Triangle> triangles = new ArrayList<Triangle>();
    private List<Triangle> neighbors = new ArrayList<Triangle>(); // The first neighbor triangle is opposite the first corner of triangle  i


    /**
     *
     * @param coordinate
     * @param index
     * @return
     */
    private Vertex addCoordinate(Coordinate coordinate, int index) {
        final Envelope env = new Envelope(coordinate);
        env.expandBy(epsilon);
        List result = ptsIndex.query(env);
        Vertex found = null;
        for(Object vertex : result) {
            if(vertex instanceof Vertex) {
                if(((Vertex) vertex).getDistance(coordinate.x, coordinate.y) < epsilon) {
                    found = (Vertex) vertex;
                    break;
                }
            }
        }
        if(found == null) {
            found = new Vertex(coordinate.x, coordinate.y, Double.isNaN(coordinate.z) ? 0 : coordinate.z, index);
            ptsIndex.insert(new Envelope(coordinate),  found);
        }
        return found;
    }


    /**
     *
     * @param incrementalTin
     * @return
     */
    private List<SimpleTriangle> computeTriangles(IncrementalTin incrementalTin) {
        ArrayList<SimpleTriangle> triangles = new ArrayList<>(incrementalTin.countTriangles().getCount());
        Triangle.TriangleBuilder triangleBuilder = new Triangle.TriangleBuilder(triangles);
        TriangleCollector.visitSimpleTriangles(incrementalTin, triangleBuilder);
        return triangles;
    }

    /**
     * @return When an exception occur, this folder with receiver the input data
     */
    public String getDumpFolder() {
        return dumpFolder;
    }

    /**
     * @param dumpFolder When an exception occur, this folder with receiver the input data
     */
    public void setDumpFolder(String dumpFolder) {
        this.dumpFolder = dumpFolder;
    }

    /**
     * @return Merge vertices closer than specified epsilon
     */
    public double getEpsilon() {
        return epsilon;
    }

    /**
     * @param epsilon Merge vertices closer than specified epsilon
     */
    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }


    /**
     *
     * @param triangle
     * @return
     */
    private static Coordinate getCentroid(SimpleTriangle triangle) {
        Vertex va = triangle.getVertexA();
        Vertex vb = triangle.getVertexB();
        Vertex vc = triangle.getVertexC();
        double cx = ( va.getX() + vb.getX() + vc.getX() ) / 3d;
        double cy = ( va.getY() + vb.getY() + vc.getY() ) / 3d;
        double cz = ( va.getZ() + vb.getZ() + vc.getZ() ) / 3d;
        return new Coordinate( cx, cy, cz);
    }

    public void dumpData() {
        GeometryFactory factory = new GeometryFactory();
        WKTWriter wktWriter = new WKTWriter(3);
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dumpFolder, "tinfour_dump.csv")))) {
                for(Object vObj : ptsIndex.queryAll()) {
                    if(vObj instanceof Vertex) {
                        final Vertex v = (Vertex)vObj;
                        Point p = factory.createPoint(toCoordinate(v));
                        writer.write(wktWriter.write(p));
                        writer.write("\n");
                    }
                }
                for (IConstraint constraint : constraints) {
                    if (constraint instanceof LinearConstraint) {
                        List<Vertex> vertices = constraint.getVertices();
                        Coordinate[] coordinates = new Coordinate[vertices.size()];
                        for (int i = 0; i < vertices.size(); i++) {
                            final Vertex v = vertices.get(i);
                            coordinates[i] = new Coordinate(v.getX(), v.getY(), v.getZ());
                        }
                        LineString l = factory.createLineString(coordinates);
                        writer.write(wktWriter.write(l));
                        writer.write("\n");
                    } else if (constraint instanceof PolygonConstraint) {
                        List<Vertex> vertices = constraint.getVertices();
                        if(vertices != null && vertices.size() >= 3) {
                            Coordinate[] coordinates = new Coordinate[vertices.size() + 1];
                            for (int i = 0; i < vertices.size() ; i++) {
                                final Vertex v = vertices.get(i);
                                coordinates[i] = new Coordinate(v.getX(), v.getY(), v.getZ());
                            }
                            coordinates[coordinates.length - 1] = coordinates[0];
                            Polygon l = factory.createPolygon(coordinates);
                            writer.write(wktWriter.write(l));
                            writer.write("\n");
                        } else {
                            LOGGER.info("Weird null polygon " + constraint);
                        }
                    }
                }
            }
        }  catch (IOException ioEx) {
            // ignore
        }
    }

    /**
     * Launch delaunay process
     */
    @Override
    public void processDelaunay() throws LayerDelaunayError {
        triangles.clear();
        vertices.clear();

        List<Vertex> meshPoints = ptsIndex.queryAll();

        IncrementalTin tin;
        boolean refine;
        List<SimpleTriangle> simpleTriangles = new ArrayList<>();
        do {
            // Triangulate
            tin = new IncrementalTin();
            // Add points
            tin.add(meshPoints, null);
            // Add constraints
            try {
                tin.addConstraints(constraints, false);
            }catch (IllegalStateException ex) {
                // Got error
                // Dump input data
                if(!dumpFolder.isEmpty()) {
                    dumpData();
                }
                throw new LayerDelaunayError(ex);
            }
            refine = false;

            simpleTriangles = computeTriangles(tin);
            // Will triangulate multiple time if refinement is necessary
            if(maxArea > 0) {
                for (SimpleTriangle triangle : simpleTriangles) {
                    if(triangle.getArea() > maxArea) {
                        // Insert steiner point in circumcircle
                        Coordinate centroid = getCentroid(triangle);
                        meshPoints.add(new Vertex(centroid.x, centroid.y, centroid.z));
                        refine = true;
                    }
                }
            }
        } while (refine);
        List<Vertex> verts = tin.getVertices();
        vertices = new ArrayList<>(verts.size());
        Map<Vertex, Integer> vertIndex = new HashMap<>();
        for(Vertex v : verts) {
            vertIndex.put(v, vertices.size());
            vertices.add(toCoordinate(v));
        }
        Map<Integer, Integer> edgeIndexToTriangleIndex = new HashMap<>();
        for(SimpleTriangle t : simpleTriangles) {
            int triangleAttribute = 0;
            if(t.getContainingRegion() != null) {
                if(t.getContainingRegion().getConstraintIndex() < constraintIndex.size()) {
                    triangleAttribute = constraintIndex.get(t.getContainingRegion().getConstraintIndex());
                }
            }
            triangles.add(new Triangle(vertIndex.get(t.getVertexA()), vertIndex.get(t.getVertexB()),vertIndex.get(t.getVertexC()), triangleAttribute));
            edgeIndexToTriangleIndex.put(t.getEdgeA().getIndex(), triangles.size() - 1);
            edgeIndexToTriangleIndex.put(t.getEdgeB().getIndex(), triangles.size() - 1);
            edgeIndexToTriangleIndex.put(t.getEdgeC().getIndex(), triangles.size() - 1);
        }
        if(computeNeighbors) {
            for(SimpleTriangle t : simpleTriangles) {
                Integer neighA = edgeIndexToTriangleIndex.get(t.getEdgeA().getDual().getIndex());
                Integer neighB = edgeIndexToTriangleIndex.get(t.getEdgeB().getDual().getIndex());
                Integer neighC = edgeIndexToTriangleIndex.get(t.getEdgeC().getDual().getIndex());
                neighbors.add(new Triangle(neighA != null ? neighA : -1,
                        neighB != null ? neighB : -1,
                        neighC != null ? neighC : -1));
            }
        }
    }

    /**
     * Append a polygon into the triangulation
     *
     * @param newPoly Polygon to append into the mesh, internal rings willb be inserted as holes.
     * @param buildingId Polygon attribute. {@link Triangle#getAttribute()}
     */
    @Override
    public void addPolygon(Polygon newPoly, int buildingId) throws LayerDelaunayError {
        final Coordinate[] coordinates = newPoly.getExteriorRing().getCoordinates();
        // Exterior ring must be CCW
        if(!Orientation.isCCW(coordinates)) {
            CoordinateArrays.reverse(coordinates);
        }
        if (coordinates.length >= 4) {
            List<Vertex> vertexList = new ArrayList<>();
            for(int vId = 0; vId < coordinates.length - 1 ; vId++) {
                vertexList.add(addCoordinate(coordinates[vId], buildingId));
            }
            PolygonConstraint polygonConstraint = new PolygonConstraint(vertexList);
            polygonConstraint.complete();
            if(polygonConstraint.isValid()) {
                constraints.add(polygonConstraint);
                constraintIndex.add(buildingId);
            }
        }
        // Append holes
        final int holeCount = newPoly.getNumInteriorRing();
        for (int holeIndex = 0; holeIndex < holeCount; holeIndex++) {
            LineString holeLine = newPoly.getInteriorRingN(holeIndex);
            final Coordinate[] hCoordinates = holeLine.getCoordinates();
            // Holes must be CW
            if(Orientation.isCCW(hCoordinates)) {
                CoordinateArrays.reverse(hCoordinates);
            }
            List<Vertex> vertexList = new ArrayList<>(hCoordinates.length);
            for(int vId = 0; vId < hCoordinates.length - 1 ; vId++) {
                vertexList.add(addCoordinate(hCoordinates[vId], buildingId));
            }
            PolygonConstraint polygonConstraint = new PolygonConstraint(vertexList);
            polygonConstraint.complete();
            if(polygonConstraint.isValid()) {
                constraints.add(polygonConstraint);
                constraintIndex.add(buildingId);
            }
        }
    }

    @Override
    public void setMinAngle(Double minAngle) {
        // TODO Auto-generated method stub

    }

    @Override
    public void hintInit(Envelope bBox, long polygonCount, long verticesCount) throws LayerDelaunayError {
    }

    @Override
    public List<Coordinate> getVertices() throws LayerDelaunayError {
        return this.vertices;
    }

    @Override
    public List<Triangle> getTriangles() throws LayerDelaunayError {
        return triangles;
    }

    private static Coordinate toCoordinate(Vertex v) {
        return new Coordinate(v.getX(), v.getY(), v.getZ());
    }

    /**
     * Append a vertex into the triangulation
     *
     * @param vertexCoordinate coordinate of the new vertex
     */
    @Override
    public void addVertex(Coordinate vertexCoordinate) throws LayerDelaunayError {
        addCoordinate(vertexCoordinate, 0);
    }

    /**
     * Set the maximum area in m²
     *
     * @param maxArea Maximum area in m²
     */
    @Override
    public void setMaxArea(Double maxArea) throws LayerDelaunayError {
        this.maxArea = Math.max(0, maxArea);
    }

    /**
     * Append a LineString into the triangulation
     * @param buildingID Associated ID building that will be available on points
     */
    //add buildingID to edge property and to points property
    public void addLineString(LineString lineToProcess, int buildingID) throws LayerDelaunayError {
        Coordinate[] coordinates = lineToProcess.getCoordinates();
        List<Vertex> vertexList = new ArrayList<>();
        for(Coordinate coordinate : coordinates) {
            vertexList.add(addCoordinate(coordinate, buildingID));
        }
        LinearConstraint linearConstraint = new LinearConstraint(vertexList);
        linearConstraint.complete();
        if(linearConstraint.isValid()) {
            constraints.add(linearConstraint);
            constraintIndex.add(buildingID);
        }
    }
    //add buildingID to edge property and to points property

    @Override
    public void reset() {
        // TODO Auto-generated method stub

    }

    /**
     * retrieve results Triangle link
     * @return list of triangles neighbor by their index.
     */
    @Override
    public List<Triangle> getNeighbors() throws LayerDelaunayError {
        if (computeNeighbors) {
            return neighbors;
        } else {
            throw new LayerDelaunayError("You must call setRetrieveNeighbors(True) before process delaunay triangulation");
        }
    }

    /**
     * Enable or Disable the collecting of triangles neighboring data.
     * @param retrieve
     */
    @Override
    public void setRetrieveNeighbors(boolean retrieve) {
        this.computeNeighbors = retrieve;

    }
}
