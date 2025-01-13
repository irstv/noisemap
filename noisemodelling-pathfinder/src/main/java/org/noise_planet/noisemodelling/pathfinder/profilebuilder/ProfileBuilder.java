/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.math.Vector3D;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunay;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunayError;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerTinfour;
import org.noise_planet.noisemodelling.pathfinder.delaunay.Triangle;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.utils.IntegerTuple;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Double.NaN;
import static java.lang.Double.isNaN;
import static org.locationtech.jts.algorithm.Orientation.isCCW;
//import static org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility.dist2D;
import static org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder.IntersectionType.*;

//TODO use NaN for building height
//TODO fix wall references id in order to use also real wall database key
//TODO check how the wall alpha are set to the cut point
//TODO check how the topo and building height are set to cut point
//TODO check how the building pk is set to cut point
//TODO difference between Z and height (z = height+topo)
//TODO create class org.noise_planet.noisemodelling.pathfinder.cnossos.ComputeCnossosRays which is a copy of computeRays using ProfileBuilder

/**
 * Builder constructing profiles from buildings, topography and ground effects.
 */
public class ProfileBuilder {
    public static final double epsilon = 1e-7;
    public static final double MILLIMETER = 0.001;
    public static final double LEFT_SIDE = Math.PI / 2;
    /** Class {@link java.util.logging.Logger}. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileBuilder.class);
    /** Default RTree node capacity. */
    private static final int TREE_NODE_CAPACITY = 5;
    /** {@link Geometry} factory. */
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final double DELTA = 1e-3;

    /** If true, no more data can be add. */
    private boolean isFeedingFinished = false;
    /** Wide angle points of a building polygon */
    private final Map<Integer, ArrayList<Coordinate>> buildingsWideAnglePoints = new HashMap<>();
    /** Building RTree node capacity. */
    private int buildingNodeCapacity = TREE_NODE_CAPACITY;
    /** Topographic RTree node capacity. */
    private int topoNodeCapacity = TREE_NODE_CAPACITY;
    /** Ground RTree node capacity. */
    private int groundNodeCapacity = TREE_NODE_CAPACITY;
    /**
     * Max length of line part used for profile retrieving.
     * @see ProfileBuilder#getProfile(Coordinate, Coordinate)
     */
    private double maxLineLength = 60;
    /** List of buildings. */
    private final List<Building> buildings = new ArrayList<>();
    /** List of walls. */
    private final List<Wall> walls = new ArrayList<>();
    /** Building RTree. */
    private final STRtree buildingTree;
    /** Building RTree. */
    private STRtree wallTree = new STRtree(TREE_NODE_CAPACITY);
    /** RTree with Buildings's walls linestrings, walls linestring, GroundEffect linestrings
     * The object is an integer. It's an index of the array {@link #processedWalls} */
    public STRtree rtree;
    private STRtree groundEffectsRtree = new STRtree(TREE_NODE_CAPACITY);


    /** List of topographic points. */
    private final List<Coordinate> topoPoints = new ArrayList<>();
    /** List of topographic lines. */
    private final List<LineString> topoLines = new ArrayList<>();
    /** Topographic triangle facets. */
    private List<Triangle> topoTriangles = new ArrayList<>();
    /** Topographic triangle neighbors. */
    private List<Triangle> topoNeighbors = new ArrayList<>();
    /** Topographic Vertices .*/
    private List<Coordinate> vertices = new ArrayList<>();
    /** Topographic RTree. */
    private STRtree topoTree;

    /** List of ground effects. */
    private final List<GroundAbsorption> groundAbsorptions = new ArrayList<>();

    /** Receivers .*/
    private final List<Coordinate> receivers = new ArrayList<>();

    /** List of processed walls. */
    public final List<Wall> processedWalls = new ArrayList<>();

    /** Global envelope of the builder. */
    private Envelope envelope;

    /** if true take into account z value on Buildings Polygons
     * In this case, z represent the altitude (from the sea to the top of the wall) */
    private boolean zBuildings = false;


    /**
     * @param zBuildings if true take into account z value on Buildings Polygons
     *                   In this case, z represent the altitude (from the sea to the top of the wall). If false, Z is
     *                   ignored and the height attribute of the Building/Wall is used to extrude the building from the DEM
     * @return this
     */
    public ProfileBuilder setzBuildings(boolean zBuildings) {
        this.zBuildings = zBuildings;
        return this;
    }


    /**
     * Main empty constructor.
     */
    public ProfileBuilder() {
        buildingTree = new STRtree(buildingNodeCapacity);
    }

    //TODO : when a source/receiver are underground, should an offset be applied ?
    /**
     * Constructor setting parameters.
     * @param buildingNodeCapacity Building RTree node capacity.
     * @param topoNodeCapacity     Topographic RTree node capacity.
     * @param groundNodeCapacity   Ground RTree node capacity.
     * @param maxLineLength        Max length of line part used for profile retrieving.
     */
    public ProfileBuilder(int buildingNodeCapacity, int topoNodeCapacity, int groundNodeCapacity, int maxLineLength) {
        this.buildingNodeCapacity = buildingNodeCapacity;
        this.topoNodeCapacity = topoNodeCapacity;
        this.groundNodeCapacity = groundNodeCapacity;
        this.maxLineLength = maxLineLength;
        buildingTree = new STRtree(buildingNodeCapacity);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param building Building.
     */
    public ProfileBuilder addBuilding(Building building) {
        if(building.poly == null || building.poly.isEmpty()) {
            LOGGER.error("Cannot add a building with null or empty geometry.");
        }
        else if(!isFeedingFinished) {
            if(envelope == null) {
                envelope = building.poly.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(building.poly.getEnvelopeInternal());
            }
            buildings.add(building);
            buildingTree.insert(building.poly.getEnvelopeInternal(), buildings.size());
            return this;
        }
        else{
            LOGGER.warn("Cannot add building, feeding is finished.");
        }
        return this;
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param geom   Building footprint.
     */
    public ProfileBuilder addBuilding(Geometry geom) {
        return addBuilding(geom, -1);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords) {
        return addBuilding(coords, -1);
    }

    /**
     * Add the given {@link Geometry} footprint and height as building.
     * @param geom   Building footprint.
     * @param height Building height.
     */
    public ProfileBuilder addBuilding(Geometry geom, double height) {
        return addBuilding(geom, height, new ArrayList<>());
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param height Building height.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height) {
        return addBuilding(coords, height, -1);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param geom   Building footprint.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Geometry geom, int id) {
        return addBuilding(geom, NaN, id);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, int id) {
        return addBuilding(coords, NaN, id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as building.
     * @param geom   Building footprint.
     * @param height Building height.
     * @param id     Database id.
     */
    public ProfileBuilder addBuilding(Geometry geom, double height, int id) {
        return addBuilding(geom, height, new ArrayList<>(), id);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param height Building height.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height, int id) {
        return addBuilding(coords, height, new ArrayList<>(), id);
    }

    /**
     * Add the given {@link Geometry} footprint, height and alphas (absorption coefficients) as building.
     * @param geom   Building footprint.
     * @param height Building height.
     * @param alphas Absorption coefficients.
     */
    public ProfileBuilder addBuilding(Geometry geom, double height, List<Double> alphas) {
        return addBuilding(geom, height, alphas, -1);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param height Building height.
     * @param alphas Absorption coefficients.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height, List<Double> alphas) {
        return addBuilding(coords, height, alphas, -1);
    }

    /**
     * Add the given {@link Geometry} footprint, height and alphas (absorption coefficients) as building.
     * @param geom   Building footprint.
     * @param alphas Absorption coefficients.
     */
    public ProfileBuilder addBuilding(Geometry geom, List<Double> alphas) {
        return addBuilding(geom, NaN, alphas, -1);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param alphas Absorption coefficients.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, List<Double> alphas) {
        return addBuilding(coords, NaN, alphas, -1);
    }

    /**
     * Add the given {@link Geometry} footprint, height and alphas (absorption coefficients) as building.
     * @param geom   Building footprint.
     * @param alphas Absorption coefficients.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Geometry geom, List<Double> alphas, int id) {
        return addBuilding(geom, NaN, alphas, id);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param alphas Absorption coefficients.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, List<Double> alphas, int id) {
        return addBuilding(coords, NaN, alphas, id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database primary key
     * as building.
     * @param geom   Building footprint.
     * @param height Building height.
     * @param alphas Absorption coefficients.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Geometry geom, double height, List<Double> alphas, int id) {
        if(!(geom instanceof Polygon)) {
            LOGGER.error("Building geometry should be Polygon");
            return null;
        }
        Polygon poly = (Polygon)geom;
        addBuilding(new Building(poly, height, alphas, id, zBuildings));
        return this;
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param height Building height.
     * @param alphas Absorption coefficients.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height, List<Double> alphas, int id) {
        Coordinate[] polyCoords;
        int l = coords.length;
        if(!coords[0].equals2D(coords[l-1])) {
            // Not closed linestring
            polyCoords = Arrays.copyOf(coords, l+1);
            polyCoords[l] = new Coordinate(coords[0]);
        }
        else {
            polyCoords = coords;
        }
        return addBuilding(FACTORY.createPolygon(polyCoords), height, alphas, id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param geom   Wall footprint.
     * @param height Wall height.
     * @param id     Database key.
     */
    /*public ProfileBuilder addWall(LineString geom, double height, int id) {
        return addWall(geom, height, new ArrayList<>(), id);
    }*/

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param coords Wall footprint coordinates.
     * @param height Wall height.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(Coordinate[] coords, double height, int id) {
        return addWall(FACTORY.createLineString(coords), height, new ArrayList<>(), id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param geom   Wall footprint.
     * @param id     Database key.
     */
    /*public ProfileBuilder addWall(LineString geom, int id) {
        return addWall(geom, 0.0, new ArrayList<>(), id);
    }*/

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param coords Wall footprint coordinates.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(Coordinate[] coords, int id) {
        return addWall(FACTORY.createLineString(coords), 0.0, new ArrayList<>(), id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param wall
     */
    public ProfileBuilder addWall(Wall wall) {
        walls.add(wall);
        wallTree.insert(new Envelope(wall.p0, wall.p1), walls.size());
        return this;
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param geom   Wall footprint.
     * @param height Wall height.
     * @param alphas Absorption coefficient.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(LineString geom, double height, List<Double> alphas, int id) {
        if(!isFeedingFinished) {
            if(envelope == null) {
                envelope = geom.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(geom.getEnvelopeInternal());
            }

            for(int i=0; i<geom.getNumPoints()-1; i++) {
                Wall wall = new Wall(geom.getCoordinateN(i), geom.getCoordinateN(i+1), id, IntersectionType.BUILDING);
                wall.setHeight(height);
                wall.setAlpha(alphas);
                addWall(wall);
            }
            return this;
        }
        else{
            LOGGER.warn("Cannot add building, feeding is finished.");
            return null;
        }
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param coords Wall footprint coordinates.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(Coordinate[] coords, double height, List<Double> alphas, int id) {
        return addWall(FACTORY.createLineString(coords), height, alphas, id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param coords Wall footprint coordinates.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(Coordinate[] coords, List<Double> alphas, int id) {
        return addWall(FACTORY.createLineString(coords), 0.0, alphas, id);
    }

    /**
     * Add the topographic point in the data, to complete the topographic data.
     * @param point Topographic point.
     */
    public ProfileBuilder addTopographicPoint(Coordinate point) {
        if(!isFeedingFinished) {
            //Force to 3D
            if (isNaN(point.z)) {
                point.setCoordinate(new Coordinate(point.x, point.y, 0.));
            }
            if(envelope == null) {
                envelope = new Envelope(point);
            }
            else {
                envelope.expandToInclude(point);
            }
            this.topoPoints.add(point);
        }
        return this;
    }

    /**
     * Add the topographic line in the data, to complete the topographic data.
     */
    public ProfileBuilder addTopographicLine(LineSegment segment) {
        addTopographicLine(segment.p0, segment.p1);
        return this;
    }

    /**
     * Add the topographic line in the data, to complete the topographic data.
     */
    public ProfileBuilder addTopographicLine(Coordinate p0, Coordinate p1) {
        addTopographicLine(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z);
        return this;
    }

    /**
     * Add the topographic line in the data, to complete the topographic data.
     */
    public ProfileBuilder addTopographicLine(double x0, double y0, double z0, double x1, double y1, double z1) {
        if(!isFeedingFinished) {
            LineString lineSegment = FACTORY.createLineString(new Coordinate[]{new Coordinate(x0, y0, z0), new Coordinate(x1, y1, z1)});
            if(envelope == null) {
                envelope = lineSegment.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(lineSegment.getEnvelopeInternal());
            }
            this.topoLines.add(lineSegment);
        }
        return this;
    }

    /**
     * Add the topographic line in the data, to complete the topographic data.
     * @param lineSegment Topographic line.
     */
    public ProfileBuilder addTopographicLine(LineString lineSegment) {
        if(!isFeedingFinished) {
            if(envelope == null) {
                envelope = lineSegment.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(lineSegment.getEnvelopeInternal());
            }
            this.topoLines.add(lineSegment);
        }
        return this;
    }

    /**
     * Add a ground effect.
     * @param geom        Ground effect area footprint.
     * @param coefficient Ground effect coefficient.
     */
    public ProfileBuilder addGroundEffect(Geometry geom, double coefficient) {
        if(!isFeedingFinished) {
            if(envelope == null) {
                envelope = geom.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(geom.getEnvelopeInternal());
            }
            this.groundAbsorptions.add(new GroundAbsorption(geom, coefficient));
        }
        return this;
    }

    /**
     * Add a ground effect.
     * @param minX        Ground effect minimum X.
     * @param maxX        Ground effect maximum X.
     * @param minY        Ground effect minimum Y.
     * @param maxY        Ground effect maximum Y.
     * @param coefficient Ground effect coefficient.
     */
    public ProfileBuilder addGroundEffect(double minX, double maxX, double minY, double maxY, double coefficient) {
        if(!isFeedingFinished) {
            Geometry geom = FACTORY.createPolygon(new Coordinate[]{
                    new Coordinate(minX, minY),
                    new Coordinate(minX, maxY),
                    new Coordinate(maxX, maxY),
                    new Coordinate(maxX, minY),
                    new Coordinate(minX, minY)
            });
            if(envelope == null) {
                envelope = geom.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(geom.getEnvelopeInternal());
            }
            this.groundAbsorptions.add(new GroundAbsorption(geom, coefficient));
        }
        return this;
    }

    public List<Wall> getProcessedWalls() {
        return processedWalls;
    }

    /**
     * Retrieve the building list.
     * @return The building list.
     */
    public List<Building> getBuildings() {
        return buildings;
    }

    /**
     * Retrieve the count of building add to this builder.
     * @return The count of building.
     */
    public int getBuildingCount() {
        return buildings.size();
    }

    /**
     * Retrieve the building with the given id (id is starting from 1).
     * @param id Id of the building
     * @return The building corresponding to the given id.
     */
    public Building getBuilding(int id) {
        return buildings.get(id);
    }

    /**
     * Retrieve the wall list.
     * @return The wall list.
     */
    public List<Wall> getWalls() {
        return walls;
    }

    /**
     * Retrieve the count of wall add to this builder.
     * @return The count of wall.
     */
    /*public int getWallCount() {
        return walls.size();
    }*/

    /**
     * Retrieve the wall with the given id (id is starting from 1).
     * @param id Id of the wall
     * @return The wall corresponding to the given id.
     */
    public Wall getWall(int id) {
        return walls.get(id);
    }

    /**
     * Clear the building list.
     */
    /*public void clearBuildings() {
        buildings.clear();
    }

    /**
     * Retrieve the global profile envelope.
     * @return The global profile envelope.

    public Envelope getMeshEnvelope() {
        return envelope;
    }

    /**
     * Add a constraint on maximum triangle area.
     * @param maximumArea Value in square meter.

    public void setMaximumArea(double maximumArea) {
        maxArea = maximumArea;
    }*/

    /**
     * Retrieve the topographic triangles.
     * @return The topographic triangles.
     */
    public List<Triangle> getTriangles() {
        return topoTriangles;
    }

    /**
     * Retrieve the topographic vertices.
     * @return The topographic vertices.
     */
    public List<Coordinate> getVertices() {
        return vertices;
    }

    /**
     * Retrieve the receivers list.
     * @return The receivers list.
     */
    public List<Coordinate> getReceivers() {
        return receivers;
    }

    /**
     * Retrieve the ground effects.
     * @return The ground effects.
     */
    public List<GroundAbsorption> getGroundEffects() {
        return groundAbsorptions;
    }

    /**
     * Finish the data feeding. Once called, no more data can be added and process it in order to prepare the
     * profile retrieving.
     * The building are processed to include each facets into a RTree
     * The topographic points and lines are meshed using delaunay and triangles facets are included into a RTree
     *
     * @return True if the finishing has been successfully done, false otherwise.
     */
    public ProfileBuilder finishFeeding() {
        isFeedingFinished = true;

        //Process topographic points and lines
        if(topoPoints.size()+topoLines.size() > 1) {
            //Feed the Delaunay layer
            LayerDelaunay layerDelaunay = new LayerTinfour();
            layerDelaunay.setRetrieveNeighbors(true);
            try {
                for (Coordinate topoPoint : topoPoints) {
                    layerDelaunay.addVertex(topoPoint);
                }
            } catch (LayerDelaunayError e) {
                LOGGER.error("Error while adding topographic points to Delaunay layer.", e);
                return null;
            }
            try {
                for (LineString topoLine : topoLines) {
                    //TODO ensure the attribute parameter is useless
                    layerDelaunay.addLineString(topoLine, -1);
                }
            } catch (LayerDelaunayError e) {
                LOGGER.error("Error while adding topographic points to Delaunay layer.", e);
                return null;
            }
            //Process Delaunay
            try {
                layerDelaunay.processDelaunay();
            } catch (LayerDelaunayError e) {
                LOGGER.error("Error while processing Delaunay.", e);
                return null;
            }
            try {
                topoTriangles = layerDelaunay.getTriangles();
                topoNeighbors = layerDelaunay.getNeighbors();
            } catch (LayerDelaunayError e) {
                LOGGER.error("Error while getting triangles", e);
                return null;
            }
            //Feed the RTree
            topoTree = new STRtree(topoNodeCapacity);
            try {
                vertices = layerDelaunay.getVertices();
            } catch (LayerDelaunayError e) {
                LOGGER.error("Error while getting vertices", e);
                return null;
            }
            // wallIndex set will merge shared triangle segments
            Set<IntegerTuple> wallIndex = new HashSet<>();
            for (int i = 0; i < topoTriangles.size(); i++) {
                final Triangle tri = topoTriangles.get(i);
                wallIndex.add(new IntegerTuple(tri.getA(), tri.getB(), i));
                wallIndex.add(new IntegerTuple(tri.getB(), tri.getC(), i));
                wallIndex.add(new IntegerTuple(tri.getC(), tri.getA(), i));
                // Insert triangle in rtree
                Coordinate vA = vertices.get(tri.getA());
                Coordinate vB = vertices.get(tri.getB());
                Coordinate vC = vertices.get(tri.getC());
                Envelope env = FACTORY.createLineString(new Coordinate[]{vA, vB, vC}).getEnvelopeInternal();
                topoTree.insert(env, i);
            }
            topoTree.build();
        }
        //Update building z
        if(topoTree != null) {
            for (Building b : buildings) {
                if(isNaN(b.poly.getCoordinate().z) || b.poly.getCoordinate().z == 0.0 || !zBuildings) {
                    b.poly2D_3D();
                    b.poly.apply(new ElevationFilter.UpdateZ(b.height + b.updateZTopo(this)));
                }
            }
            for (Wall w : walls) {
                if(isNaN(w.p0.z) || w.p0.z == 0.0) {
                    w.p0.z = w.height + getZGround(w.p0);
                }
                if(isNaN(w.p1.z) || w.p1.z == 0.0) {
                    w.p1.z = w.height + getZGround(w.p1);
                }
            }
        } else {
            for (Building b : buildings) {
                if(b != null && b.poly != null && b.poly.getCoordinate() != null && (!zBuildings ||
                        isNaN(b.poly.getCoordinate().z) || b.poly.getCoordinate().z == 0.0)) {

                    b.poly2D_3D();
                    b.poly.apply(new ElevationFilter.UpdateZ(b.height));
                }

            }
            for (Wall w : walls) {
                if(isNaN(w.p0.z) || w.p0.z == 0.0) {
                    w.p0.z = w.height;
                }
                if(isNaN(w.p1.z) || w.p1.z == 0.0) {
                    w.p1.z = w.height;
                }
            }
        }
        //Process buildings
        rtree = new STRtree(buildingNodeCapacity);
        buildingsWideAnglePoints.clear();
        for (int j = 0; j < buildings.size(); j++) {
            Building building = buildings.get(j);
            buildingsWideAnglePoints.put(j + 1,
                    getWideAnglePointsByBuilding(j + 1, 0, 2 * Math.PI));
            List<Wall> walls = new ArrayList<>();
            Coordinate[] coords = building.poly.getCoordinates();
            for (int i = 0; i < coords.length - 1; i++) {
                LineSegment lineSegment = new LineSegment(coords[i], coords[i + 1]);
                Wall w = new Wall(lineSegment, j, IntersectionType.BUILDING).setProcessedWallIndex(processedWalls.size());
                walls.add(w);
                w.setPrimaryKey(building.getPrimaryKey());
                w.setAlpha(building.alphas);
                processedWalls.add(w);
                rtree.insert(lineSegment.toGeometry(FACTORY).getEnvelopeInternal(), processedWalls.size()-1);
            }
            building.setWalls(walls);
        }
        for (int j = 0; j < walls.size(); j++) {
            Wall wall = walls.get(j);
            Coordinate[] coords = new Coordinate[]{wall.p0, wall.p1};
            for (int i = 0; i < coords.length - 1; i++) {
                LineSegment lineSegment = new LineSegment(coords[i], coords[i + 1]);
                Wall w = new Wall(lineSegment, j, IntersectionType.WALL).setProcessedWallIndex(processedWalls.size());
                w.setAlpha(wall.alphas);
                w.setPrimaryKey(wall.primaryKey);
                processedWalls.add(w);
                rtree.insert(lineSegment.toGeometry(FACTORY).getEnvelopeInternal(), processedWalls.size()-1);
            }
        }
        //Process the ground effects
        groundEffectsRtree = new STRtree(TREE_NODE_CAPACITY);
        for (int j = 0; j < groundAbsorptions.size(); j++) {
            GroundAbsorption effect = groundAbsorptions.get(j);
            List<Polygon> polygons = new ArrayList<>();
            if (effect.geom instanceof Polygon) {
                polygons.add((Polygon) effect.geom);
            }
            if (effect.geom instanceof MultiPolygon) {
                MultiPolygon multi = (MultiPolygon) effect.geom;
                for (int i = 0; i < multi.getNumGeometries(); i++) {
                    polygons.add((Polygon) multi.getGeometryN(i));
                }
            }
            for (Polygon poly : polygons) {
                groundEffectsRtree.insert(poly.getEnvelopeInternal(), j);
                Coordinate[] coords = poly.getCoordinates();
                for (int k = 0; k < coords.length - 1; k++) {
                    LineSegment line = new LineSegment(coords[k], coords[k + 1]);
                    processedWalls.add(new Wall(line, j, GROUND_EFFECT).setProcessedWallIndex(processedWalls.size()));
                    rtree.insert(new Envelope(line.p0, line.p1), processedWalls.size() - 1);
                }
            }
        }
        rtree.build();
        groundEffectsRtree.build();
        return this;
    }


    /**
     *
     * @param reflectionPt
     * @return
     */
    public double getZ(Coordinate reflectionPt) {
        List<Integer> ids = buildingTree.query(new Envelope(reflectionPt));
        if(ids.isEmpty()) {
            return getZGround(reflectionPt);
        }
        else {
            for(Integer id : ids) {
                Geometry buildingGeometry =  buildings.get(id - 1).getGeometry();
                if(buildingGeometry.getEnvelopeInternal().intersects(reflectionPt)) {
                    return buildingGeometry.getCoordinate().z;
                }
            }
            return getZGround(reflectionPt);
        }
    }


    /**
     *
     * @param env
     * @return
     */
    public List<Wall> getWallsIn(Envelope env) {
        List<Wall> list = new ArrayList<>();
        List<Integer> indexes = rtree.query(env);
        for(int i : indexes) {
            Wall w = getProcessedWalls().get(i);
            if(w.getType().equals(BUILDING) || w.getType().equals(WALL)) {
                list.add(w);
            }
        }
        return list;
    }



    /**
     * Retrieve the cutting profile following the line build from the given coordinates.
     * @param c0 Starting point.
     * @param c1 Ending point.
     * @return Cutting profile.
     */
    public CutProfile getProfile(Coordinate c0, Coordinate c1) {
        return getProfile(c0, c1, 0.0, false);
    }

    /**
     * split the segment between two points in segments of a given length maxLineLength
     * @param c0
     * @param c1
     * @param maxLineLength
     * @return
     */
    public static List<LineSegment> splitSegment(Coordinate c0, Coordinate c1, double maxLineLength) {
        List<LineSegment> lines = new ArrayList<>();
        LineSegment fullLine = new LineSegment(c0, c1);
        double l = c0.distance(c1);
        //If the line length if greater than the MAX_LINE_LENGTH value, split it into multiple lines
        if(l < maxLineLength) {
            lines.add(fullLine);
        }
        else {
            double frac = maxLineLength /l;
            for(int i = 0; i<l/ maxLineLength; i++) {
                Coordinate p0 = fullLine.pointAlong(i*frac);
                p0.z = c0.z + (c1.z - c0.z) * i*frac;
                Coordinate p1 = fullLine.pointAlong(Math.min((i+1)*frac, 1.0));
                p1.z = c0.z + (c1.z - c0.z) * Math.min((i+1)*frac, 1.0);
                lines.add(new LineSegment(p0, p1));
            }
        }
        return lines;
    }

    /**
     * Retrieve the cutting profile following the line build from the given coordinates.
     * @param sourceCoordinate Starting point.
     * @param receiverCoordinate Ending point.
     * @param gS Default source absorption ground effect value if no ground absorption value is found
     * @param stopAtObstacleOverSourceReceiver If an obstacle is found higher than then segment sourceCoordinate
     *                                        receiverCoordinate, stop computing and a CutProfile with intersection information
     * @return Cutting profile.
     */
    public CutProfile getProfile(Coordinate sourceCoordinate, Coordinate receiverCoordinate, double gS, boolean stopAtObstacleOverSourceReceiver) {
        CutPointSource sourcePoint  = new CutPointSource(sourceCoordinate);
        CutPointReceiver receiverPoint = new CutPointReceiver(receiverCoordinate);

        CutProfile profile = new CutProfile(sourcePoint, receiverPoint);

        // Add sourceCoordinate
        int groundAbsorptionIndex = getIntersectingGroundAbsorption(FACTORY.createPoint(sourceCoordinate));
        if(groundAbsorptionIndex >= 0) {
            sourcePoint.setGroundCoefficient(groundAbsorptions.get(groundAbsorptionIndex).getCoefficient());
        } else {
            sourcePoint.setGroundCoefficient(gS);
        }

        //Fetch topography evolution between sourceCoordinate and receiverCoordinate
        if(topoTree != null) {
            addTopoCutPts(sourceCoordinate, receiverCoordinate, profile, stopAtObstacleOverSourceReceiver);
            if(stopAtObstacleOverSourceReceiver && profile.hasTopographyIntersection) {
                return profile;
            }
        } else {
            profile.getSource().zGround = 0.0;
            profile.getReceiver().zGround = 0.0;
        }

        //Add Buildings/Walls and Ground effect transition points
        if(rtree != null) {
            LineSegment fullLine = new LineSegment(sourceCoordinate, receiverCoordinate);
            addGroundBuildingCutPts(fullLine, profile, stopAtObstacleOverSourceReceiver);
            if(stopAtObstacleOverSourceReceiver && profile.hasBuildingIntersection) {
                return profile;
            }
        }

        // Propagate ground coefficient for unknown coefficients
        double currentCoefficient = sourcePoint.groundCoefficient;
        for (CutPoint cutPoint : profile.cutPoints) {
            if(Double.isNaN(cutPoint.groundCoefficient)) {
                cutPoint.setGroundCoefficient(currentCoefficient);
            } else if (cutPoint instanceof CutPointGroundEffect) {
                currentCoefficient = cutPoint.getGroundCoefficient();
            }
        }

        // Compute the interpolation of Z ground for intermediate points
        CutPoint previousZGround = sourcePoint;
        int nextPointIndex = 0;
        for (int pointIndex = 1; pointIndex < profile.cutPoints.size() - 1; pointIndex++) {
            CutPoint cutPoint = profile.cutPoints.get(pointIndex);
            if(Double.isNaN(cutPoint.zGround)) {
                if(nextPointIndex <= pointIndex) {
                    // look for next reference Z ground point
                    for (int i = pointIndex + 1; i < profile.cutPoints.size(); i++) {
                        CutPoint nextPoint = profile.cutPoints.get(i);
                        if (!Double.isNaN(nextPoint.zGround)) {
                            nextPointIndex = i;
                            break;
                        }
                    }
                }
                CutPoint nextPoint = profile.cutPoints.get(nextPointIndex);
                cutPoint.zGround = Vertex.interpolateZ(cutPoint.coordinate,
                        new Coordinate(previousZGround.coordinate.x, previousZGround.coordinate.y,
                                previousZGround.getzGround()),
                        new Coordinate(nextPoint.coordinate.x, nextPoint.coordinate.y, nextPoint.getzGround()));
                if(Double.isNaN(cutPoint.coordinate.z) || cutPoint instanceof CutPointGroundEffect) {
                    // Bottom of walls are set to NaN z because it can be computed here at low cost
                    // (without fetch dem r-tree)
                    // ground effect change points is taking the Z of ground in coordinate too
                    cutPoint.coordinate.setZ(cutPoint.zGround);
                }
            } else {
                // we have an update on Z ground
                previousZGround = cutPoint;
            }
        }
        return profile;
    }

    /**
     * Fetch the first intersecting ground absorption object that intersects with the provided geometry
     * @param query The geometry object to check for intersection
     * @return The ground absorption object or null if nothing is found here
     */
    public int getIntersectingGroundAbsorption(Geometry query) {
        if(groundEffectsRtree != null) {
            var res = groundEffectsRtree.query(query.getEnvelopeInternal());
            for (Object groundEffectAreaIndex : res) {
                if(groundEffectAreaIndex instanceof Integer) {
                    GroundAbsorption groundAbsorption = groundAbsorptions.get((Integer) groundEffectAreaIndex);
                    if(groundAbsorption.geom.intersects(query)) {
                        return (Integer) groundEffectAreaIndex;
                    }
                }
            }
        }
        return -1;
    }



    private boolean processWall(int processedWallIndex, Coordinate intersection, Wall facetLine,
                                    LineSegment fullLine, List<CutPoint> newCutPoints,
                                    boolean stopAtObstacleOverSourceReceiver, CutProfile profile) {

        CutPointWall cutPointWall = new CutPointWall(processedWallIndex,
                intersection, facetLine.getLineSegment(), facetLine.alphas);
        cutPointWall.intersectionType = CutPointWall.INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT;
        if(facetLine.primaryKey >= 0) {
            cutPointWall.setPk(facetLine.primaryKey);
        }
        newCutPoints.add(cutPointWall);

        double zRayReceiverSource = Vertex.interpolateZ(intersection, fullLine.p0, fullLine.p1);
        if (zRayReceiverSource <= intersection.z) {
            profile.hasBuildingIntersection = true;
            return !stopAtObstacleOverSourceReceiver;
        } else {
            return true;
        }
    }


    private boolean processBuilding(int processedWallIndex, Coordinate intersection, Wall facetLine,
                                 LineSegment fullLine, List<CutPoint> newCutPoints,
                                    boolean stopAtObstacleOverSourceReceiver, CutProfile profile) {
        CutPointWall wallCutPoint = new CutPointWall(processedWallIndex, intersection, facetLine.getLineSegment(),
                buildings.get(facetLine.getOriginId()).alphas);
        if(facetLine.primaryKey >= 0) {
            wallCutPoint.setPk(facetLine.primaryKey);
        }
        newCutPoints.add(wallCutPoint);
        double zRayReceiverSource = Vertex.interpolateZ(intersection, fullLine.p0, fullLine.p1);
        // add a point at the bottom of the building on the exterior side of the building
        Vector2D facetVector = Vector2D.create(facetLine.p0, facetLine.p1);
        // exterior polygon segments are CW, so the exterior of the polygon is on the left side of the vector
        // it works also with polygon holes as interiors are CCW
        Vector2D exteriorVector = facetVector.rotate(LEFT_SIDE).normalize().multiply(MILLIMETER);
        Coordinate exteriorPoint = exteriorVector.add(Vector2D.create(intersection)).toCoordinate();
        // exterior point closer to source so we know that we enter the building
        if(exteriorPoint.distance(fullLine.p0) < intersection.distance(fullLine.p0)) {
            wallCutPoint.intersectionType = CutPointWall.INTERSECTION_TYPE.BUILDING_ENTER;
        } else {
            wallCutPoint.intersectionType = CutPointWall.INTERSECTION_TYPE.BUILDING_EXIT;
        }

        if (zRayReceiverSource <= intersection.z) {
            profile.hasBuildingIntersection = true;
            return !stopAtObstacleOverSourceReceiver;
        } else {
            return true;
        }
    }


    private boolean processGroundEffect(int processedWallIndex, Coordinate intersection, Wall facetLine,
                                    LineSegment fullLine, List<CutPoint> newCutPoints,
                                    boolean stopAtObstacleOverSourceReceiver, CutProfile profile) {

        // we hit the border of a ground effect
        // we need to add a new point with the new value of the ground effect
        // we will query for the point that lie after the intersection with the ground effect border
        // in order to have the new value of the ground effect, if there is nothing at this location
        // we fall back to the default value of ground effect
        // if this is another ground effect we will add it here because we may have overlapping ground effect.
        // if it is overlapped then we will have two points with the same G at almost the same location. (it's ok)
        // retrieve the ground coefficient after the intersection in the direction of the profile
        // this method will solve the question if we enter a new ground absorption or we will leave one
        Vector2D directionAfter = Vector2D.create(fullLine.p0, fullLine.p1).normalize().multiply(MILLIMETER);
        Point afterIntersectionPoint = FACTORY.createPoint(Vector2D.create(intersection).add(directionAfter).toCoordinate());
        GroundAbsorption groundAbsorption = groundAbsorptions.get(facetLine.getOriginId());
        if (groundAbsorption.geom.intersects(afterIntersectionPoint)) {
            // we enter a new ground effect
            newCutPoints.add(new CutPointGroundEffect(processedWallIndex, intersection, groundAbsorption.getCoefficient()));
        } else {
            // we exit a ground surface, we have to check if there is
            // another ground surface at this point, could be none or could be
            // an overlapping/touching ground surface
            int groundSurfaceIndex = getIntersectingGroundAbsorption(afterIntersectionPoint);
            if (groundSurfaceIndex == -1) {
                // no new ground effect, we fall back to default G
                newCutPoints.add(new CutPointGroundEffect(-1, intersection, Scene.DEFAULT_G));
            } else {
                // add another ground surface, could be duplicate points if
                // the two ground surfaces is touching
                GroundAbsorption nextGroundAbsorption = groundAbsorptions.get(groundSurfaceIndex);
                // if the interior of the two ground surfaces overlaps we add the ground point
                // (as we will not encounter the side of this other ground surface)
                if (!nextGroundAbsorption.geom.touches(groundAbsorption.geom)) {
                    newCutPoints.add(new CutPointGroundEffect(groundSurfaceIndex,
                            afterIntersectionPoint.getCoordinate(),
                            nextGroundAbsorption.getCoefficient()));
                }
            }
        }
        return true;
    }
    /**
     * Fetch intersection of a line segment with Buildings lines/Walls lines/Ground Effect lines
     * @param fullLine P0 to P1 query for the profile of buildings
     * @param profile Object to feed the results (out)
     * @param stopAtObstacleOverSourceReceiver If an obstacle is found higher than then segment sourceCoordinate
     *                                        receiverCoordinate, stop computing and set #CutProfile.hasBuildingInter to buildings in profile data
     */
    private void addGroundBuildingCutPts(LineSegment fullLine, CutProfile profile, boolean stopAtObstacleOverSourceReceiver) {
        // Collect all objects where envelope intersects all sub-segments of fullLine
        Set<Integer> processed = new HashSet<>();

        // Segmented fullLine, this is the query for rTree indexes
        // Split line into segments for structures based on RTree in order to limit the number of queries
        // (for large area of the line segment envelope)
        List<LineSegment> lines = splitSegment(fullLine.p0, fullLine.p1, maxLineLength);
        List<CutPoint> newCutPoints = new LinkedList<>();
        try {
            for (int j = 0; j < lines.size()
                    && !(profile.hasBuildingIntersection && stopAtObstacleOverSourceReceiver); j++) {
                LineSegment line = lines.get(j);
                for (Object result : rtree.query(new Envelope(line.p0, line.p1))) {
                    if (!(result instanceof Integer) || processed.contains((Integer) result)) {
                        continue;
                    }
                    processed.add((Integer) result);
                    int i = (Integer) result;
                    Wall facetLine = processedWalls.get(i);
                    Coordinate intersection = fullLine.intersection(facetLine.ls);
                    if (intersection != null) {
                        intersection = new Coordinate(intersection);
                        if (!isNaN(facetLine.p0.z) && !isNaN(facetLine.p1.z)) {
                            // same z in the line, so useless to compute interpolation between points
                            if (Double.compare(facetLine.p0.z, facetLine.p1.z) == 0) {
                                intersection.z = facetLine.p0.z;
                            } else {
                                intersection.z = Vertex.interpolateZ(intersection, facetLine.p0, facetLine.p1);
                            }
                        }
                        switch (facetLine.type) {
                            case BUILDING:
                                if (!processBuilding(i, intersection, facetLine, fullLine, newCutPoints,
                                        stopAtObstacleOverSourceReceiver, profile)) {
                                    return;
                                }
                                break;
                            case WALL:
                                if (!processWall(i, intersection, facetLine, fullLine, newCutPoints,
                                        stopAtObstacleOverSourceReceiver, profile)) {
                                    return;
                                }
                                break;
                            case GROUND_EFFECT:
                                if (!processGroundEffect(i, intersection, facetLine, fullLine, newCutPoints,
                                        stopAtObstacleOverSourceReceiver, profile)) {
                                    return;
                                }
                                break;
                        }
                    }
                }
            }
        } finally {
            profile.insertCutPoint(true, newCutPoints.toArray(CutPoint[]::new));
        }
    }

    Coordinate[] getTriangleVertices(int triIndex) {
        final Triangle tri = topoTriangles.get(triIndex);
        return new Coordinate[] {this.vertices.get(tri.getA()), this.vertices.get(tri.getB()), this.vertices.get(tri.getC())};
    }
    /**
     * Compute the next triangle index.Find the shortest intersection point of
     * triIndex segments to the p1 coordinate
     *
     * @param triIndex        Triangle index
     * @param propagationLine Propagation line
     * @return Next triangle to the specified direction, -1 if there is no
     * triangle neighbor.
     */
    private int getNextTri(final int triIndex,
                           final LineSegment propagationLine,
                           HashSet<Integer> navigationHistory, final Coordinate segmentIntersection) {
        final Triangle tri = topoTriangles.get(triIndex);
        final Triangle triNeighbors = topoNeighbors.get(triIndex);
        int nearestIntersectionSide = -1;
        int idNeighbor;

        double nearestIntersectionPtDist = Double.MAX_VALUE;
        // Find intersection pt
        final Coordinate aTri = this.vertices.get(tri.getA());
        final Coordinate bTri = this.vertices.get(tri.getB());
        final Coordinate cTri = this.vertices.get(tri.getC());
        double distline_line;
        // Intersection First Side
        idNeighbor = triNeighbors.get(2);
        if (!navigationHistory.contains(idNeighbor)) {
            LineSegment triSegment = new LineSegment(aTri, bTri);
            Coordinate[] closestPoints = propagationLine.closestPoints(triSegment);
            Coordinate intersectionTest = null;
            if(closestPoints.length == 2 && closestPoints[0].distance(closestPoints[1]) < JTSUtility.TRIANGLE_INTERSECTION_EPSILON) {
                intersectionTest = new Coordinate(closestPoints[0].x, closestPoints[0].y, Vertex.interpolateZ(closestPoints[0], triSegment.p0, triSegment.p1));
            }
            if(intersectionTest != null) {
                distline_line = propagationLine.p1.distance(intersectionTest);
                if (distline_line < nearestIntersectionPtDist) {
                    segmentIntersection.setCoordinate(intersectionTest);
                    nearestIntersectionPtDist = distline_line;
                    nearestIntersectionSide = 2;
                }
            }
        }
        // Intersection Second Side
        idNeighbor = triNeighbors.get(0);
        if (!navigationHistory.contains(idNeighbor)) {
            LineSegment triSegment = new LineSegment(bTri, cTri);
            Coordinate[] closestPoints = propagationLine.closestPoints(triSegment);
            Coordinate intersectionTest = null;
            if(closestPoints.length == 2 && closestPoints[0].distance(closestPoints[1]) < JTSUtility.TRIANGLE_INTERSECTION_EPSILON) {
                intersectionTest = new Coordinate(closestPoints[0].x, closestPoints[0].y, Vertex.interpolateZ(closestPoints[0], triSegment.p0, triSegment.p1));
            }
            if(intersectionTest != null) {
                distline_line = propagationLine.p1.distance(intersectionTest);
                if (distline_line < nearestIntersectionPtDist) {
                    segmentIntersection.setCoordinate(intersectionTest);
                    nearestIntersectionPtDist = distline_line;
                    nearestIntersectionSide = 0;
                }
            }
        }
        // Intersection Third Side
        idNeighbor = triNeighbors.get(1);
        if (!navigationHistory.contains(idNeighbor)) {
            LineSegment triSegment = new LineSegment(cTri, aTri);
            Coordinate[] closestPoints = propagationLine.closestPoints(triSegment);
            Coordinate intersectionTest = null;
            if(closestPoints.length == 2 && closestPoints[0].distance(closestPoints[1]) < JTSUtility.TRIANGLE_INTERSECTION_EPSILON) {
                intersectionTest = new Coordinate(closestPoints[0].x, closestPoints[0].y, Vertex.interpolateZ(closestPoints[0], triSegment.p0, triSegment.p1));
            }
            if(intersectionTest != null) {
                distline_line = propagationLine.p1.distance(intersectionTest);
                if (distline_line < nearestIntersectionPtDist) {
                    segmentIntersection.setCoordinate(intersectionTest);
                    nearestIntersectionSide = 1;
                }
            }
        }
        if(nearestIntersectionSide > -1) {
            return triNeighbors.get(nearestIntersectionSide);
        } else {
            return -1;
        }
    }


    /**
     * Get coordinates of triangle vertices
     * @param triIndex Index of triangle
     * @return triangle vertices
     */
    Coordinate[] getTriangle(int triIndex) {
        final Triangle tri = this.topoTriangles.get(triIndex);
        return new Coordinate[]{this.vertices.get(tri.getA()),
                this.vertices.get(tri.getB()), this.vertices.get(tri.getC())};
    }


    /**
     * Get coordinates of triangle vertices (last point is first point)
     * @param triIndex Index of triangle
     * @return triangle vertices
     */
    Coordinate[] getClosedTriangle(int triIndex) {
        final Triangle tri = this.topoTriangles.get(triIndex);
        return new Coordinate[]{this.vertices.get(tri.getA()), this.vertices.get(tri.getB()),
                this.vertices.get(tri.getC()), this.vertices.get(tri.getA())};
    }

    /**
     * Return the triangle id from a point coordinate inside the triangle
     *
     * @param pt Point test
     * @return Triangle Id, Or -1 if no triangle has been found
     */

    public int getTriangleIdByCoordinate(Coordinate pt) {
        Envelope ptEnv = new Envelope(pt);
        ptEnv.expandBy(1);
        var res = topoTree.query(new Envelope(ptEnv));
        double minDistance = Double.MAX_VALUE;
        int minDistanceTriangle = -1;
        for(Object objInd : res) {
            int triId = (Integer) objInd;
            Coordinate[] tri = getTriangle(triId);
            AtomicReference<Double> err = new AtomicReference<>(0.);
            JTSUtility.dotInTri(pt, tri[0], tri[1], tri[2], err);
            if (err.get() < minDistance) {
                minDistance = err.get();
                minDistanceTriangle = triId;
            }
        }
        return minDistanceTriangle;
    }

    /**
     *
     * @param p1
     * @param p2
     * @param profile
     */
    public void addTopoCutPts(Coordinate p1, Coordinate p2, CutProfile profile, boolean stopAtObstacleOverSourceReceiver) {
        List<Coordinate> coordinates = new ArrayList<>();
        boolean freeField = fetchTopographicProfile(coordinates, p1, p2, stopAtObstacleOverSourceReceiver);
        if(coordinates.size() >= 2) {
            profile.getSource().zGround = coordinates.get(0).z;
            profile.getReceiver().zGround = coordinates.get(coordinates.size() - 1).z;
        } else {
            LOGGER.warn(String.format(Locale.ROOT, "Propagation out of the DEM area from %s to %s",
                    p1.toString(), p2.toString()));
            return;
        }
        profile.hasTopographyIntersection = !freeField;

        List<CutPointTopography> topographyList = new ArrayList<>(coordinates.size());
        for(int idPoint = 1; idPoint < coordinates.size() - 1; idPoint++) {
            final Coordinate previous = coordinates.get(idPoint - 1);
            final Coordinate current = coordinates.get(idPoint);
            final Coordinate next = coordinates.get(idPoint+1);
            // Do not add topographic points which are simply the linear interpolation between two points
            // triangulation add a lot of interpolated lines from line segment DEM
            if(CGAlgorithms3D.distancePointSegment(current, previous, next) >= DELTA) {
                topographyList.add(new CutPointTopography(current));
            }
        }
        profile.insertCutPoint(true, topographyList.toArray(CutPoint[]::new));
    }

    /**
     * Find closest triangle that intersects with segment
     * @param segment Segment to intersects will all triangles
     * @param intersection Found closest intersection point with p0
     * @param intersectionTriangle Found closest intersection triangle
     * @return True if at least one triangle as been found on intersection
     */
    boolean findClosestTriangleIntersection(LineSegment segment, final Coordinate intersection, AtomicInteger intersectionTriangle) {
        Envelope queryEnvelope = new Envelope(segment.p0);
        queryEnvelope.expandToInclude(segment.p1);
        if(queryEnvelope.getHeight() < 1.0 || queryEnvelope.getWidth() < 1) {
            queryEnvelope.expandBy(1.0);
        }
        List res = topoTree.query(queryEnvelope);
        double minDistance = Double.MAX_VALUE;
        int minDistanceTriangle = -1;
        GeometryFactory factory = new GeometryFactory();
        LineString lineString = factory.createLineString(new Coordinate[]{segment.p0, segment.p1});
        Coordinate intersectionPt = null;
        for(Object objInd : res) {
            int triId = (Integer) objInd;
            Coordinate[] tri = getTriangle(triId);
            Geometry triangleGeometry = factory.createPolygon(new Coordinate[]{ tri[0], tri[1], tri[2], tri[0]});
            if(triangleGeometry.intersects(lineString)) {
                Coordinate[] nearestCoordinates = DistanceOp.nearestPoints(triangleGeometry, lineString);
                for (Coordinate nearestCoordinate : nearestCoordinates) {
                    double distance = nearestCoordinate.distance(segment.p0);
                    if (distance < minDistance) {
                        minDistance = distance;
                        minDistanceTriangle = triId;
                        intersectionPt = nearestCoordinate;
                    }
                }
            }
        }
        if(minDistanceTriangle != -1) {
            Coordinate[] tri = getTriangle(minDistanceTriangle);
            // Compute interpolated Z of the intersected point on the nearest triangle
            intersectionPt.setZ(Vertex.interpolateZ(intersectionPt, tri[0], tri[1], tri[2]));
            intersection.setCoordinate(intersectionPt);
            intersectionTriangle.set(minDistanceTriangle);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Fetch all intersections with TIN. For simplification only plane change are pushed.
     * @param p1 first point
     * @param p2 second point
     * @param stopAtObstacleOverSourceReceiver Stop fetching intersections if the segment p1-p2 is intersecting with TIN
     * @return True if the segment p1-p2 is not intersecting with DEM
     */
    public boolean fetchTopographicProfile(List<Coordinate> outputPoints,Coordinate p1, Coordinate p2, boolean stopAtObstacleOverSourceReceiver) {
        if(topoTree == null) {
            return true;
        }
        //get origin triangle id
        int curTriP1 = getTriangleIdByCoordinate(p1);
        LineSegment propaLine = new LineSegment(p1, p2);
        if(curTriP1 == -1) {
            // we are outside the bounds of the triangles
            // Find the closest triangle to p1
            Coordinate intersectionPt = new Coordinate();
            AtomicInteger minDistanceTriangle = new AtomicInteger();
            if(findClosestTriangleIntersection(propaLine, intersectionPt, minDistanceTriangle)) {
                outputPoints.add(intersectionPt);
                curTriP1 = minDistanceTriangle.get();
            } else {
                // out of DEM propagation area
                return true;
            }
        }
        HashSet<Integer> navigationHistory = new HashSet<Integer>();
        int navigationTri = curTriP1;
        // Add p1 coordinate
        Coordinate[] vertices = getTriangleVertices(curTriP1);
        outputPoints.add(new Coordinate(p1.x, p1.y, Vertex.interpolateZ(p1, vertices[0], vertices[1], vertices[2])));
        boolean freeField = true;
        while (navigationTri != -1) {
            navigationHistory.add(navigationTri);
            Coordinate intersectionPt = new Coordinate();
            int propaTri = this.getNextTri(navigationTri, propaLine, navigationHistory, intersectionPt);
            if(propaTri == -1) {
                // Add p2 coordinate
                vertices = getTriangleVertices(navigationTri);
                outputPoints.add(new Coordinate(p2.x, p2.y, Vertex.interpolateZ(p2, vertices[0], vertices[1], vertices[2])));
            } else {
                // Found next triangle (if propaTri >= 0)
                // extract X,Y,Z values of intersection with triangle segment
                if(!Double.isNaN(intersectionPt.z)) {
                    outputPoints.add(intersectionPt);
                    Coordinate closestPointOnPropagationLine = propaLine.closestPoint(intersectionPt);
                    double interpolatedZ = Vertex.interpolateZ(closestPointOnPropagationLine, propaLine.p0, propaLine.p1);
                    if(interpolatedZ < intersectionPt.z) {
                        freeField = false;
                        if(stopAtObstacleOverSourceReceiver) {
                            return false;
                        }
                    }
                }
            }
            navigationTri = propaTri;
        }
        return freeField;
    }

    /**
     * @param normal1 Normalized vector 1
     * @param normal2 Normalized vector 2
     * @return The angle between the two normals
     */
    private double computeNormalsAngle(Vector3D normal1, Vector3D normal2) {
        return Math.acos(normal1.dot(normal2));
    }

    /**
     * @return True if digital elevation model has been added
     */
    public boolean hasDem() {
        return topoTree != null && !topoTree.isEmpty();
    }

    /**
     * @return Mesh of digital elevation model
     */
    public MultiPolygon demAsMultiPolygon() {
        GeometryFactory GF = new GeometryFactory();
        if(!topoTriangles.isEmpty()) {
            List<Polygon> polyTri = new ArrayList<>(topoTriangles.size());
            for (int i = 0; i < topoTriangles.size(); i++) {
                polyTri.add(GF.createPolygon(getClosedTriangle(i)));
            }
            return GF.createMultiPolygon(polyTri.toArray(Polygon[]::new));
        } else {
            return GF.createMultiPolygon();
        }
    }


    /**
     * @return Altitude in meters from sea level
     */
    public double getZGround(Coordinate coordinate) {
        return getZGround(coordinate, new AtomicInteger(-1));
    }

    /**
     * Fetch Altitude in meters from sea level at a location. You can use the triangle hint if you request a lot of
     * positions in the same location
     * @param coordinate X,Y coordinate to fetch
     * @param triangleHint Triangle index hint (if >= 0 will be checked, and will be updated with the triangle is found)
     * @return Altitude in meters from sea level
     */
    public double getZGround(Coordinate coordinate, AtomicInteger triangleHint) {
        if(topoTree == null) {
            return 0.0;
        }
        int i = triangleHint.get();
        if(i >= 0 && i < topoTriangles.size()) {
            final Triangle tri = topoTriangles.get(i);
            final Coordinate p1 = vertices.get(tri.getA());
            final Coordinate p2 = vertices.get(tri.getB());
            final Coordinate p3 = vertices.get(tri.getC());
            if(!JTSUtility.dotInTri(coordinate, p1, p2, p3)) {
                i = -1;
            }
        }
        if(i < 0) {
            i = getTriangleIdByCoordinate(coordinate);
            if(i == -1) {
                return 0.0;
            }
        }
        final Triangle tri = topoTriangles.get(i);
        final Coordinate p1 = vertices.get(tri.getA());
        final Coordinate p2 = vertices.get(tri.getB());
        final Coordinate p3 = vertices.get(tri.getC());
        if(JTSUtility.dotInTri(coordinate, p1, p2, p3)) {
            triangleHint.set(i);
            return Vertex.interpolateZ(coordinate, p1, p2, p3);
        } else {
            return 0.0;
        }
    }

    /**
     * Different type of intersection.
     */
    public enum IntersectionType {BUILDING, WALL, TOPOGRAPHY, GROUND_EFFECT, SOURCE, RECEIVER, REFLECTION, V_EDGE_DIFFRACTION}

    /**
     * Cutting profile containing all th cut points with there x,y,z position.
     */


    /**
     * Profile cutting point.
     */

    // Buffer around obstacles when computing diffraction (ISO / TR 17534-4 look like using this value)
    public static final double wideAngleTranslationEpsilon = 0.015;

    /**
     * @param build 1-n based building identifier
     * @return
     */
    public ArrayList<Coordinate> getPrecomputedWideAnglePoints(int build) {
        return buildingsWideAnglePoints.get(build);
    }

    /**
     *
     * @param build
     * @param minAngle
     * @param maxAngle
     * @return
     */
    public ArrayList<Coordinate> getWideAnglePointsByBuilding(int build, double minAngle, double maxAngle) {
        ArrayList <Coordinate> verticesBuilding = new ArrayList<>();
        Coordinate[] ring = getBuilding(build-1).getGeometry().getExteriorRing().getCoordinates().clone();
        if(!isCCW(ring)) {
            for (int i = 0; i < ring.length / 2; i++) {
                Coordinate temp = ring[i];
                ring[i] = ring[ring.length - 1 - i];
                ring[ring.length - 1 - i] = temp;
            }
        }
        for(int i=0; i < ring.length - 1; i++) {
            int i1 = i > 0 ? i-1 : ring.length - 2;
            int i3 = i + 1;
            double smallestAngle = Angle.angleBetweenOriented(ring[i1], ring[i], ring[i3]);
            double openAngle;
            if(smallestAngle >= 0) {
                // corresponds to a counterclockwise (CCW) rotation
                openAngle = smallestAngle;
            } else {
                // corresponds to a clockwise (CW) rotation
                openAngle = 2 * Math.PI + smallestAngle;
            }
            // Open Angle is the building angle in the free field area
            if(openAngle > minAngle && openAngle < maxAngle) {
                // corresponds to a counterclockwise (CCW) rotation
                double midAngle = openAngle / 2;
                double midAngleFromZero = Angle.angle(ring[i], ring[i1]) + midAngle;
                Coordinate offsetPt = new Coordinate(
                        ring[i].x + Math.cos(midAngleFromZero) * wideAngleTranslationEpsilon,
                        ring[i].y + Math.sin(midAngleFromZero) * wideAngleTranslationEpsilon,
                        buildings.get(build - 1).getGeometry().getCoordinate().z + wideAngleTranslationEpsilon);
                verticesBuilding.add(offsetPt);
            }
        }
        verticesBuilding.add(verticesBuilding.get(0));
        return verticesBuilding;
    }

    /**
     *
     * @param p1
     * @param p2
     * @param visitor
     */
    public void getWallsOnPath(Coordinate p1, Coordinate p2, BuildingIntersectionPathVisitor visitor) {
        // Update intersection line test in the rtree visitor
        try {
            List<LineSegment> lines = splitSegment(p1, p2, maxLineLength);
            for(LineSegment segment : lines) {
                visitor.setIntersectionLine(segment);
                Envelope pathEnv = new Envelope(segment.p0, segment.p1);
                rtree.query(pathEnv, visitor);
            }
        } catch (IllegalStateException ex) {
            //Ignore
        }
    }


    /**
     * Hold two integers. Used to store unique triangle segments
     */

}
