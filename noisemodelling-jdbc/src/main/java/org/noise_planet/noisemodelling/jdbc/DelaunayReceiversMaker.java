/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */


package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.utilities.GeometryTableUtilities;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.TableLocation;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.h2gis.utilities.dbtypes.DBUtils;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.noise_planet.noisemodelling.pathfinder.delaunay.Triangle;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunay;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunayError;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerTinfour;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Building;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Wall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Create input receivers built from Delaunay for contructing a NoiseMap rendering.
 * SQL syntax is compatible with H2 and PostGIS.
 * @author Nicolas Fortin
 * @author SU Qi
 */
public class DelaunayReceiversMaker extends NoiseMapLoader {
    private static final int BATCH_MAX_SIZE = 100;
    private Logger logger = LoggerFactory.getLogger(DelaunayReceiversMaker.class);
    private double roadWidth = 2;
    private double maximumArea = 75;
    private long nbreceivers = 0;
    private double receiverHeight = 1.6;
    private double buildingBuffer = 2;
    private String exceptionDumpFolder = "";
    private AtomicInteger constraintId = new AtomicInteger(1);
    private double epsilon = 1e-6;
    private double geometrySimplificationDistance = 1;
    private boolean isoSurfaceInBuildings = false;

    /**
     * Create constructor DelaunayReceiversMaker
     * @param buildingsTableName Buildings table
     * @param sourcesTableName Source table name
     */
    public DelaunayReceiversMaker(String buildingsTableName, String sourcesTableName) {
        super(buildingsTableName, sourcesTableName);
    }

    /**
     * @return True if isosurface will be placed into buildings
     */
    public boolean isIsoSurfaceInBuildings() {
        return isoSurfaceInBuildings;
    }
    /**
     * @param isoSurfaceInBuildings Set true in order to place isosurface in buildings
     */
    public void setIsoSurfaceInBuildings(boolean isoSurfaceInBuildings) {
        this.isoSurfaceInBuildings = isoSurfaceInBuildings;
    }

    /**
     * @return When an exception occur, this folder with receiver the input data
     */
    public String getExceptionDumpFolder() {
        return exceptionDumpFolder;
    }

    /**
     * @param exceptionDumpFolder When an exception occur, this folder with receiver the input data
     */
    public void setExceptionDumpFolder(String exceptionDumpFolder) {
        this.exceptionDumpFolder = exceptionDumpFolder;
    }

    /**
     * @return Do not add receivers closer to specified distance
     */
    public double getBuildingBuffer() {
        return buildingBuffer;
    }

    /**
     * Do not add receivers closer to specified distance
     * @param buildingBuffer Distance in meters
     */
    public void setBuildingBuffer(double buildingBuffer) {
        this.buildingBuffer = buildingBuffer;
    }

    /**
     * Explodes a geometry collection and adds polygons to the Delaunay triangulation tool.
     * @param intersectedGeometry
     * @param delaunayTool
     * @throws LayerDelaunayError
     */
    private void explodeAndAddPolygon(Geometry intersectedGeometry,
                                      LayerDelaunay delaunayTool)
            throws LayerDelaunayError {
        if (intersectedGeometry instanceof GeometryCollection) {
            for (int j = 0; j < intersectedGeometry.getNumGeometries(); j++) {
                Geometry subGeom = intersectedGeometry.getGeometryN(j);
                explodeAndAddPolygon(subGeom, delaunayTool);
            }
        } else if(intersectedGeometry instanceof Polygon && !intersectedGeometry.isEmpty()){
            delaunayTool.addPolygon((Polygon)intersectedGeometry, constraintId.getAndAdd(1));
        }
    }

    /**
     * Merges geometries in the provided list and applies a buffer operation.
     * @param toUnite
     * @param bufferSize
     * @return the merged and buffered geometry.
     */
    private Geometry merge(LinkedList<Geometry> toUnite, double bufferSize) {
        Geometry geoArray[] = new Geometry[toUnite.size()];
        toUnite.toArray(geoArray);
        GeometryCollection polygonCollection = geometryFactory
                .createGeometryCollection(geoArray);
        BufferOp bufferOp = new BufferOp(polygonCollection,
                new BufferParameters(BufferParameters.DEFAULT_QUADRANT_SEGMENTS, BufferParameters.CAP_SQUARE,
                BufferParameters.JOIN_MITRE, BufferParameters.DEFAULT_MITRE_LIMIT));
        return bufferOp.getResultGeometry(bufferSize);
    }

    /**
     * Prepares input geometries and feeds them into the Delaunay triangulation algorithm.
     * @param buildings  the list of buildings to be included in the triangulation.
     * @param delaunayTool   the Delaunay triangulation tool.
     * @param boundingBoxFilter  the bounding box filter to confine the triangulation area.
     * @param srcDistance  the source distance for expanding the bounding box.
     * @param delaunaySegments   the list of road segments to be included in the triangulation.
     * @param minRecDist   the minimum recommended distance for merging road segments.
     * @param buildingBuffer the buffer distance for buildings.
     * @throws LayerDelaunayError if an error occurs during the Delaunay triangulation process.
     */
    private void feedDelaunay(List<Building> buildings, LayerDelaunay delaunayTool, Envelope boundingBoxFilter,
                              double srcDistance, LinkedList<LineString> delaunaySegments, double minRecDist,
                              double buildingBuffer) throws LayerDelaunayError {
        Envelope extendedEnvelope = new Envelope(boundingBoxFilter);
        extendedEnvelope.expandBy(srcDistance * 2.);
        Geometry linearRing = geometryFactory.toGeometry(boundingBoxFilter);
        if (!(linearRing instanceof Polygon)) {
            return;
        }
        Polygon boundingBox = (Polygon)linearRing;
        LinkedList<Geometry> toUnite = new LinkedList<>();
        Envelope fetchBox = new Envelope(boundingBoxFilter);
        fetchBox.expandBy(buildingBuffer);
        Geometry fetchGeometry = geometryFactory.toGeometry(fetchBox);
        for(Building building : buildings) {
            if(building.getGeometry().distance(fetchGeometry) < buildingBuffer) {
                toUnite.add(building.getGeometry());
            }
        }
        // Reduce small artifacts to avoid, shortest geometry to be
        // over-triangulated
        LinkedList<Geometry> toUniteFinal = new LinkedList<>();
        if (!toUnite.isEmpty()) {
            Geometry bufferBuildings = merge(toUnite, buildingBuffer);
            bufferBuildings = TopologyPreservingSimplifier.simplify(bufferBuildings, geometrySimplificationDistance);
            if(bufferBuildings.getNumPoints() > 3) {
                // Densify buildings to follow triangle area constraint
                if (maximumArea > 1) {
                    double triangleSide = (2 * Math.pow(maximumArea, 0.5)) / Math.pow(3, 0.25);
                    bufferBuildings = Densifier.densify(bufferBuildings, triangleSide);
                }
                toUniteFinal.add(bufferBuildings); // Add buildingsTableName to triangulation
            }
        }
        Geometry geom1 = geometryFactory.createPolygon();
        Geometry geom2 = geometryFactory.createPolygon();
        try {
            // Merge roads
            if (minRecDist > 0.01) {
                LinkedList<Geometry> toUniteRoads = new LinkedList<Geometry>(delaunaySegments);
                if (!toUniteRoads.isEmpty()) {
                    // Build Polygons buffer from roads lines
                    Geometry bufferRoads = merge(toUniteRoads, minRecDist / 2);
                    // Remove small artifacts due to multiple buffer crosses
                    bufferRoads = TopologyPreservingSimplifier.simplify(bufferRoads, geometrySimplificationDistance);
                    // Densify roads to follow triangle area constraint
                    if(maximumArea > 1) {
                        double triangleSide = (2*Math.pow(maximumArea, 0.5)) / Math.pow(3, 0.25);
                        bufferRoads = Densifier.densify(bufferRoads, triangleSide);
                    }
                    toUniteFinal.add(bufferRoads); // Merge roads with minRecDist m
                }
            }
            Geometry union = merge(toUniteFinal, 0.); // Merge roads and buildingsTableName
            // together
            // Remove geometries out of the bounding box
            geom1 = union;
            geom2 = boundingBox;
            union = union.intersection(boundingBox);
            explodeAndAddPolygon(union, delaunayTool);
        } catch (TopologyException ex) {
            WKTWriter wktWriter = new WKTWriter(3);
            logger.error(String.format("Error with input geometries\n%s\n%s",wktWriter.write(geom1),wktWriter.write(geom2)), ex);
            throw ex;
        }
    }


    /**
     * Delaunay triangulation of Sub-Domain
     *
     * @param cellMesh Final mesh target
     * @param mainEnvelope Global envelope
     * @param cellI I cell index
     * @param cellJ J cell index
     * @param maxSrcDist Maximum propagation distance
     * @param minRecDist Minimal distance receiver-source
     * @param maximumArea Maximum area of triangles
     * @throws LayerDelaunayError
     */
    public void computeDelaunay(LayerDelaunay cellMesh,
                                Envelope mainEnvelope, int cellI, int cellJ, double maxSrcDist, Collection<Geometry> sources,
                                double minRecDist, double maximumArea, double buildingBuffer, List<Building> buildings)
            throws LayerDelaunayError {

        Envelope cellEnvelope = getCellEnv(mainEnvelope, cellI, cellJ,
                getCellWidth(), getCellHeight());
        Geometry cellEnvelopeGeometry = new GeometryFactory().toGeometry(cellEnvelope);

        Envelope expandedCellEnvelop = new Envelope(cellEnvelope);
        expandedCellEnvelop.expandBy(maxSrcDist);

        // Build delaunay triangulation from buildings inside the extended
        // bounding box

        // /////////////////////////////////////////////////
        // Add roads into delaunay tool
        LinkedList<LineString> delaunaySegments = new LinkedList<>();
        if (minRecDist > 0.1) {
            for (Geometry sourceGeometry : sources) {
                Envelope ptEnv = sourceGeometry.getEnvelopeInternal();
                if (ptEnv.intersects(expandedCellEnvelop)) {
                    if (sourceGeometry instanceof Point) {
                        // Add square in rendering
                        cellMesh.addPolygon((Polygon)cellEnvelopeGeometry.intersection(sourceGeometry.buffer(minRecDist, BufferParameters.CAP_SQUARE)), 1);
                    } else {
                        if (sourceGeometry instanceof LineString) {
                            delaunaySegments.add((LineString) (sourceGeometry));
                        } else if (sourceGeometry instanceof MultiLineString) {
                            int nbLineString = sourceGeometry.getNumGeometries();
                            for (int idLineString = 0; idLineString < nbLineString; idLineString++) {
                                delaunaySegments.add((LineString) (sourceGeometry
                                        .getGeometryN(idLineString)));
                            }
                        }
                    }
                }
            }
        }

        feedDelaunay(buildings, cellMesh, cellEnvelope, maxSrcDist, delaunaySegments,
                minRecDist, buildingBuffer);

        // Process delaunay
        logger.info("Begin delaunay");
        cellMesh.setRetrieveNeighbors(false);
        // Add cell envelope
        if (maximumArea > 1) {
            double triangleSide = (2*Math.pow(maximumArea, 0.5)) / Math.pow(3, 0.25);
            Polygon polygon = (Polygon)Densifier.densify(new GeometryFactory().toGeometry(cellEnvelope), triangleSide);
            Coordinate[] points = polygon.getExteriorRing().getCoordinates();
            for(Coordinate point : points) {
                cellMesh.addVertex(point);
            }
        } else {
            Polygon polygon = (Polygon) new GeometryFactory().toGeometry(cellEnvelope);
            Coordinate[] points = polygon.getExteriorRing().getCoordinates();
            for(Coordinate point : points) {
                cellMesh.addVertex(point);
            }
        }
        cellMesh.processDelaunay();
        logger.info("End delaunay");
    }

    /**
     * Retrieves the computation envelope based on data stored in the database tables.
     * @param connection the database connection.
     * @return the computation envelope containing the bounding box of the data stored in the specified tables.
     * @throws SQLException
     */
    @Override
    protected Envelope getComputationEnvelope(Connection connection) throws SQLException {
        Envelope computationEnvelope = new Envelope();
        DBTypes dbTypes = DBUtils.getDBType(connection);
        if(!sourcesTableName.isEmpty() && JDBCUtilities.getRowCount(connection, sourcesTableName) > 0) {
            computationEnvelope.expandToInclude(GeometryTableUtilities.getEnvelope(connection, TableLocation.parse(sourcesTableName, dbTypes)).getEnvelopeInternal());
        }
        if(!buildingsTableName.isEmpty() && JDBCUtilities.getRowCount(connection, buildingsTableName) > 0) {
            computationEnvelope.expandToInclude(GeometryTableUtilities.getEnvelope(connection, TableLocation.parse(buildingsTableName, dbTypes)).getEnvelopeInternal());
        }
        return computationEnvelope;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public double getGeometrySimplificationDistance() {
        return geometrySimplificationDistance;
    }

    public void setGeometrySimplificationDistance(double geometrySimplificationDistance) {
        this.geometrySimplificationDistance = geometrySimplificationDistance;
    }

    public static void generateResultTable(Connection connection, String receiverTableName, String trianglesTableName,
                                           AtomicInteger receiverPK, List<Coordinate> vertices,
                                           GeometryFactory geometryFactory, List<Triangle> triangles, int cellI,
                                           int cellJ, int gridDim) throws SQLException {

        if(!JDBCUtilities.tableExists(connection, receiverTableName)) {
            Statement st = connection.createStatement();
            st.execute("CREATE TABLE "+TableLocation.parse(receiverTableName)+"(pk serial NOT NULL, the_geom geometry not null, PRIMARY KEY (PK))");
        }
        if(!JDBCUtilities.tableExists(connection, trianglesTableName)) {
            Statement st = connection.createStatement();
            st.execute("CREATE TABLE "+TableLocation.parse(trianglesTableName)+"(pk serial NOT NULL, the_geom geometry , PK_1 integer not null, PK_2 integer not null, PK_3 integer not null, cell_id integer not null, PRIMARY KEY (PK))");
        }
        int receiverPkOffset = receiverPK.get();
        // Add vertices to receivers
        PreparedStatement ps = connection.prepareStatement("INSERT INTO "+TableLocation.parse(receiverTableName)+" VALUES (?, ?);");
        int batchSize = 0;
        for(Coordinate v : vertices) {
            ps.setInt(1, receiverPK.getAndAdd(1));
            ps.setObject(2, geometryFactory.createPoint(v));
            ps.addBatch();
            batchSize++;
            if (batchSize >= BATCH_MAX_SIZE) {
                ps.executeBatch();
                ps.clearBatch();
                batchSize = 0;
            }
        }
        if (batchSize > 0) {
            ps.executeBatch();
        }
        // Add triangles
        ps = connection.prepareStatement("INSERT INTO "+TableLocation.parse(trianglesTableName)+"(the_geom, PK_1, PK_2, PK_3, CELL_ID) VALUES (?, ?, ?, ?, ?);");
        batchSize = 0;
        for(Triangle t : triangles) {
            ps.setObject(1, geometryFactory.createPolygon(new Coordinate[]{vertices.get(t.getA()),
                    vertices.get(t.getB()), vertices.get(t.getC()), vertices.get(t.getA())}));
            ps.setInt(2, t.getA() + receiverPkOffset);
            ps.setInt(3, t.getC() + receiverPkOffset);
            ps.setInt(4, t.getB() + receiverPkOffset);
            ps.setInt(5, cellI * gridDim + cellJ);
            ps.addBatch();
            batchSize++;
            if (batchSize >= BATCH_MAX_SIZE) {
                ps.executeBatch();
                ps.clearBatch();
                batchSize = 0;
            }
        }
        if (batchSize > 0) {
            ps.executeBatch();
        }
    }

    /**
     * @param epsilon Merge points that are closer that this epsilon value
     */
    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    public void generateReceivers(Connection connection, int cellI, int cellJ, String receiverTableName, String trianglesTableName, AtomicInteger receiverPK) throws SQLException, LayerDelaunayError, IOException {

        int ij = cellI * gridDim + cellJ + 1;
        if(verbose) {
            logger.info("Begin processing of cell " + ij + " / " + gridDim * gridDim);
        }
        // Compute the first pass delaunay mesh
        // The first pass doesn't take account of additional
        // vertices of neighbor cells at the borders
        // then, there are discontinuities in iso surfaces at each
        // border of cell
        Envelope cellEnvelope = getCellEnv(mainEnvelope, cellI,
                cellJ, getCellWidth(), getCellHeight());
        // Fetch all source located in expandedCellEnvelop
        Scene data = new Scene(null, attenuationCnossosParametersDay.freq_lvl);
        if(!sourcesTableName.isEmpty()) {
            fetchCellSource(connection, cellEnvelope, data, false);
        }

        List<Geometry> sourceDelaunayGeometries = data.sourceGeometries;

        List<Building> buildings = new LinkedList<>();
        List<Wall> walls = new LinkedList<>();
        Envelope expandedCell = new Envelope(cellEnvelope);
        expandedCell.expandBy(buildingBuffer);
        fetchCellBuildings(connection, cellEnvelope, buildings, walls);

        LayerTinfour cellMesh = new LayerTinfour();
        cellMesh.setEpsilon(epsilon);
        cellMesh.setDumpFolder(exceptionDumpFolder);
        cellMesh.setMaxArea(maximumArea > 1 ? maximumArea : 0);

        try {
            computeDelaunay(cellMesh, mainEnvelope, cellI,
                    cellJ,
                    maximumPropagationDistance, sourceDelaunayGeometries, roadWidth, maximumArea, buildingBuffer, buildings);
        } catch (LayerDelaunayError err) {
            throw new SQLException(err.getLocalizedMessage(), err);
        }
        sourceDelaunayGeometries.clear();
        // Make a structure to keep the following information
        // Triangle list with 3 vertices(int), and 3 neighbor
        // triangle ID
        // Vertices list

        // The evaluation of sound level must be done where the
        // following vertices are
        List<Coordinate> vertices = new ArrayList<>(cellMesh.getVertices().size());
        for(Coordinate vertex : cellMesh.getVertices()) {
            Coordinate translatedVertex = new Coordinate(vertex);
            double z = receiverHeight;
            translatedVertex.setOrdinate(2, z);
            vertices.add(translatedVertex);
        }
        // Do not add triangles associated with buildings
        List<Triangle> triangles;
        if (!isoSurfaceInBuildings) {
            triangles = new ArrayList<>(cellMesh.getTriangles().size());
            for (Triangle triangle : cellMesh.getTriangles()) {
                if (triangle.getAttribute() == 0) {
                    // place only triangles not associated to a building
                    triangles.add(triangle);
                }
            }
        } else {
            triangles = cellMesh.getTriangles();
        }
        nbreceivers += vertices.size();

        generateResultTable(connection, receiverTableName, trianglesTableName, receiverPK, vertices, geometryFactory,
                triangles, cellI, cellJ, gridDim);
    }

    public double getRoadWidth() {
        return roadWidth;
    }

    public void setRoadWidth(double roadWidth) {
        this.roadWidth = roadWidth;
    }

    public double getMaximumArea() {
        return maximumArea;
    }

    public void setMaximumArea(double maximumArea) {
        this.maximumArea = maximumArea;
    }

    public double getReceiverHeight() {
        return receiverHeight;
    }

    public void setReceiverHeight(double receiverHeight) {
        this.receiverHeight = receiverHeight;
    }

    public long getNbreceivers() {
        return nbreceivers;
    }
}
