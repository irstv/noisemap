/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc.utils;

import org.h2gis.functions.spatial.convert.ST_Force2D;
import org.h2gis.functions.spatial.convert.ST_Force3D;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.TableLocation;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.h2gis.utilities.dbtypes.DBUtils;
import org.h2gis.utilities.jts_utils.Contouring;
import org.h2gis.utilities.jts_utils.TriMarkers;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;

import static org.noise_planet.noisemodelling.emission.utils.Utils.dbaToW;


/**
 * Create isosurfaces
 * @author  Nicolas Fortin, Université Gustave Eiffel
 */
public class IsoSurface {
    Logger log = LoggerFactory.getLogger(IsoSurface.class);
    static final int BATCH_MAX_SIZE = 500;
    String pointTable = "LDEN_RESULT";
    String triangleTable = "TRIANGLES";
    String outputTable = "CONTOURING_NOISE_MAP";
    String pointTableField = "LAEQ";
    List<Double> isoLevels;
    List<String> isoLabels;
    boolean smooth = true;

    boolean mergeTriangles = true;
    double smoothCoefficient = 1.0;
    double deltaPoints = 0.5; // minimal distance between bezier points
    double epsilon = 0.05;

    int srid;
    public static final List<Double> NF31_133_ISO = Collections.unmodifiableList(Arrays.asList(35.0,40.0,45.0,50.0,55.0,60.0,65.0,70.0,75.0,80.0,200.0));

    private int exportDimension = 2;

    /**
     * @param isoLevels Iso levels in dB
     */
    public IsoSurface(List<Double> isoLevels, int srid) {
        this.isoLevels = new ArrayList<>(isoLevels.size());
        this.isoLabels = new ArrayList<>(isoLevels.size());
        this.srid = srid;
        DecimalFormat format = new DecimalFormat("#.##");
        for (int idiso = 0; idiso < isoLevels.size(); idiso++) {
            double lvl = isoLevels.get(idiso);
            this.isoLevels.add(dbaToW(lvl));
            // Symbols ( and [ are used for ordering legend in application
            // in ascii ( is 40 and [ is 91, numbers are between the two
            if (idiso == 0) {
                this.isoLabels.add(String.format(Locale.ROOT, "%s)", format.format(lvl)));
            } else if(idiso < isoLevels.size() - 1){
                this.isoLabels.add(String.format(Locale.ROOT, "%s-%s", format.format(isoLevels.get(idiso - 1)), format.format(lvl)));
            } else {
                this.isoLabels.add(String.format(Locale.ROOT, "[%s", format.format(isoLevels.get(idiso - 1))));
            }
        }
    }

    public void setIsoLabels(List<String> isoLabels) {
        this.isoLabels = isoLabels;
    }

    /**
     * @param smooth If true smooth generated polygons
     */
    public void setSmooth(boolean smooth) {
        this.smooth = smooth;
    }

    public double getSmoothCoefficient() {
        return smoothCoefficient;
    }

    /**
     * @return True if triangles will be merged using isolevel attribute
     */
    public boolean isMergeTriangles() {
        return mergeTriangles;
    }

    /**
     * @param mergeTriangles True if triangles will be merged using isolevel attribute, Z ordinate will be lost
     */
    public void setMergeTriangles(boolean mergeTriangles) {
        this.mergeTriangles = mergeTriangles;
    }

    /**
     * @param smoothCoefficient Coefficient of polygons smoothing [0-1]
     */
    public void setSmoothCoefficient(double smoothCoefficient) {
        this.smoothCoefficient = smoothCoefficient;
    }

    /**
     * @param epsilon Merge distance of polygons vertices
     */
    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public String getPointTableField() {
        return pointTableField;
    }

    /**
     * Interpolation method from
     * @link http://agg.sourceforge.net/antigrain.com/research/bezier_interpolation/index.html#PAGE_BEZIER_INTERPOLATION
     * @param anchor1 Anchor point, start coordinate of Bezier curve
     * @param control1 First control point
     * @param control2 Second control point
     * @param anchor2 Anchor point, end coordinate of Bezier curve
     * @param numSteps Number of intermediate points
     * @return Bezier points
     */
    static List<Coordinate> curve4(Coordinate anchor1, Coordinate control1, Coordinate control2, Coordinate anchor2, int numSteps)   //Anchor2
    {
        double subdiv_step  = 1.0 / (numSteps + 1);
        double subdiv_step2 = subdiv_step*subdiv_step;
        double subdiv_step3 = subdiv_step*subdiv_step*subdiv_step;

        double pre1 = 3.0 * subdiv_step;
        double pre2 = 3.0 * subdiv_step2;
        double pre4 = 6.0 * subdiv_step2;
        double pre5 = 6.0 * subdiv_step3;

        double tmp1x = anchor1.x - control1.x * 2.0 + control2.x;
        double tmp1y = anchor1.y - control1.y * 2.0 + control2.y;

        double tmp2x = (control1.x - control2.x)*3.0 - anchor1.x + anchor2.x;
        double tmp2y = (control1.y - control2.y)*3.0 - anchor1.y + anchor2.y;

        double fx = anchor1.x;
        double fy = anchor1.y;

        double dfx = (control1.x - anchor1.x)*pre1 + tmp1x*pre2 + tmp2x*subdiv_step3;
        double dfy = (control1.y - anchor1.y)*pre1 + tmp1y*pre2 + tmp2y*subdiv_step3;

        double ddfx = tmp1x*pre4 + tmp2x*pre5;
        double ddfy = tmp1y*pre4 + tmp2y*pre5;

        double dddfx = tmp2x*pre5;
        double dddfy = tmp2y*pre5;

        int step = numSteps;

        // Suppose, we have some abstract object Polygon which
        // has method AddVertex(x, y), similar to LineTo in
        // many graphical APIs.
        // Note, that the loop has only operation add!
        List<Coordinate> ret = new ArrayList<>(numSteps);
        while(step-- > 0)
        {
            fx   += dfx;
            fy   += dfy;
            dfx  += ddfx;
            dfy  += ddfy;
            ddfx += dddfx;
            ddfy += dddfy;
            ret.add(new Coordinate(fx, fy));
        }
        ret.add(new Coordinate(anchor2.x, anchor2.y)); // Last step must go exactly to anchor2.x, anchor2.y

        return ret;
    }

    /**
     *
     * @param coordinates
     * @param segmentTree
     * @param pointsDelta
     * @return
     */
    static Coordinate[] generateBezierCurves(Coordinate[] coordinates, Quadtree segmentTree, double pointsDelta) {
        ArrayList<Coordinate> pts = new ArrayList<>();
        pts.add(new Coordinate(coordinates[0].x, coordinates[0].y));
        for(int i = 0; i < coordinates.length - 1; i++) {
            final int i2 = i + 1;
            Coordinate p1 = new Coordinate(coordinates[i].x, coordinates[i].y);
            Coordinate p2 = new Coordinate(coordinates[i2].x, coordinates[i2].y);

            Segment segment = new Segment(p1, p2);
            List<Segment> segments = (List<Segment>)segmentTree.query(segment.getEnvelope());
            boolean insert = false;
            for(Segment s : segments) {
                if(segment.equals(s)) {
                    if(s.getControlPoints().size() > 2) {
                        insert = true;
                        // If this segment is shared by two polygons
                        int numSteps = Math.max(4, (int)Math.ceil(p1.distance(p2) / pointsDelta));
                        // Compute bezier curve
                        Coordinate ctrl1 = s.getControlPoints().get(0);
                        Coordinate ctrl2 = s.getControlPoints().get(1);
                        // if the control point is defined by other side
                        // reverse control point order
                        if(!s.p0.equals(p1)) {
                            ctrl1 = s.getControlPoints().get(1);
                            ctrl2 = s.getControlPoints().get(0);
                        }
                        pts.addAll(curve4(p1, ctrl1, ctrl2, p2, numSteps));
                    }
                    break;
                }
            }
            if(!insert) {
                pts.add(p2);
            }
        }
        return pts.toArray(new Coordinate[0]);
    }
    /**
     * The same segment shared by two polygons should share also the control points
     * @param coordinates
     * @param smoothValue
     */
    static void computeBezierControlPoints(Coordinate[] coordinates, double smoothValue, Quadtree segmentTree) {
        Coordinate[] midPoint = new Coordinate[coordinates.length];
        double[] len = new double[coordinates.length];
        // precompute values
        for(int i = 0; i < coordinates.length; i++) {
            final int i2 = i + 1 >= coordinates.length ? i + 1 - (coordinates.length - 1) : i + 1;
            midPoint[i] = new Coordinate((coordinates[i].x + coordinates[i2].x) / 2, (coordinates[i].y + coordinates[i2].y) / 2);
            len[i] = coordinates[i].distance(coordinates[i2]);
        }
        for(int i = 0; i < coordinates.length - 1; i++) {
            // Compute Bezier control points
            final int i0 = i - 1 < 0 ? coordinates.length - 2 : i - 1;
            final int i2 = i + 1;

            Coordinate p1 = coordinates[i];
            Coordinate p2 = coordinates[i2];

            final double x1 = p1.x;
            final double y1 = p1.y;
            final double x2 = p2.x;
            final double y2 = p2.y;

            double xc1 = midPoint[i0].x;
            double yc1 = midPoint[i0].y;
            double xc2 = midPoint[i].x;
            double yc2 = midPoint[i].y;
            double xc3 = midPoint[i2].x;
            double yc3 = midPoint[i2].y;

            double len1 = len[i0];
            double len2 = len[i];
            double len3 = len[i2];

            double k1 = len1 / (len1 + len2);
            double k2 = len2 / (len2 + len3);

            double xm1 = xc1 + (xc2 - xc1) * k1;
            double ym1 = yc1 + (yc2 - yc1) * k1;

            double xm2 = xc2 + (xc3 - xc2) * k2;
            double ym2 = yc2 + (yc3 - yc2) * k2;

            // Resulting control points. Here smooth_value is mentioned
            // above coefficient K whose value should be in range [0...1].
            double ctrl1_x = xm1 + (xc2 - xm1) * smoothValue + x1 - xm1;
            double ctrl1_y = ym1 + (yc2 - ym1) * smoothValue + y1 - ym1;

            double ctrl2_x = xm2 + (xc2 - xm2) * smoothValue + x2 - xm2;
            double ctrl2_y = ym2 + (yc2 - ym2) * smoothValue + y2 - ym2;

            // Store control points
            Segment segment = new Segment(p1, p2);
            List<Segment> segments = (List<Segment>)segmentTree.query(segment.getEnvelope());
            boolean exists = false;
            for(Segment s : segments) {
                if(segment.equals(s)) {
                    exists = true;
                    s.addControlPoints(new Coordinate(ctrl1_x, ctrl1_y), new Coordinate(ctrl2_x, ctrl2_y));
                    break;
                }
            }
            if(!exists) {
                segment.addControlPoints(new Coordinate(ctrl1_x, ctrl1_y), new Coordinate(ctrl2_x, ctrl2_y));
                segmentTree.insert(segment.getEnvelope(), segment);
            }
        }
    }
    /**
     * @param pointTableField Field with level in dB(A)
     */
    public void setPointTableField(String pointTableField) {
        this.pointTableField = pointTableField;
    }

    public String getPointTable() {
        return pointTable;
    }

    /**
     * Point table where primary key is referenced by triangle table PK_1, PK_2, PK_3
     * @param pointTable table name
     */
    public void setPointTable(String pointTable) {
        this.pointTable = pointTable;
    }

    public String getTriangleTable() {
        return triangleTable;
    }

    /**
     * Triangle table with fields THE_GEOM, PK_1, PK_2, PK_3, CELL_ID
     */
    public void setTriangleTable(String triangleTable) {
        this.triangleTable = triangleTable;
    }

    public String getOutputTable() {
        return outputTable;
    }

    public void setOutputTable(String outputTable) {
        this.outputTable = outputTable;
    }

    /**
     * Extract all polygons of the provided geometry
     * @param geom geometry
     * @param polygons polygon list
     */
    void explode(Geometry geom, List<Polygon> polygons) {
        if(geom instanceof Polygon) {
            polygons.add((Polygon)geom);
        } else if(geom instanceof GeometryCollection){
            for(int i = 0; i < geom.getNumGeometries(); i++) {
                explode(geom.getGeometryN(i), polygons);
            }
        }
    }

    /**
     * Merge polygons of the same iso levels then apply bezier filtering on outer and inner rings.
     * Finally insert those polygons into the output table
     * @param connection jdbc connection (h2gis or postgis)
     * @param cellId area id (aggregate polygons by large area in order to avoid memory overloading)
     * @param polys Polygons by isolevel
     */
    void processCell(Connection connection, int cellId, Map<Short, ArrayList<Geometry>> polys) throws SQLException {
        // First step
        // Smoothing of polygons
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), srid);
        if(smooth) {
            Quadtree segmentTree = new Quadtree();
            // Merge triangles and create an index of all segments
            for (Map.Entry<Short, ArrayList<Geometry>> entry : polys.entrySet()) {
                // Merge triangles
                CascadedPolygonUnion union = new CascadedPolygonUnion(entry.getValue());
                Geometry mergeTriangles = union.union();
                ArrayList<Polygon> polygons = new ArrayList<>();
                explode(mergeTriangles, polygons);
                for(Polygon polygon : polygons) {
                    Coordinate[] extRing = polygon.getExteriorRing().getCoordinates();
                    computeBezierControlPoints(extRing, smoothCoefficient, segmentTree);
                    LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
                    for(int idHole = 0; idHole < holes.length; idHole++) {
                        computeBezierControlPoints(polygon.getInteriorRingN(idHole).getCoordinates(), smoothCoefficient, segmentTree);
                    }
                }
                // Replace triangles by polygons
                entry.getValue().clear();
                entry.getValue().add(mergeTriangles);
            }
            // Using precomputed (shared) Bezier control points smooth polygons
            for (Map.Entry<Short, ArrayList<Geometry>> entry : polys.entrySet()) {
                ArrayList<Geometry> newPolygons = new ArrayList<>();
                if(entry.getValue().size() == 1) {
                    ArrayList<Polygon> polygons = new ArrayList<>();
                    explode(entry.getValue().get(0), polygons);
                    for(Polygon polygon : polygons) {
                        if(!polygon.isEmpty()) {
                            Coordinate[] extRing = generateBezierCurves(polygon.getExteriorRing().getCoordinates(), segmentTree, deltaPoints);
                            LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
                            for (int idHole = 0; idHole < holes.length; idHole++) {
                                Coordinate[] hole = generateBezierCurves(polygon.getInteriorRingN(idHole).getCoordinates(), segmentTree, deltaPoints);
                                holes[idHole] = factory.createLinearRing(hole);
                            }
                            polygon = factory.createPolygon(factory.createLinearRing(extRing), holes);
                            TopologyPreservingSimplifier simplifier = new TopologyPreservingSimplifier(polygon);
                            simplifier.setDistanceTolerance(epsilon);
                            Geometry res = simplifier.getResultGeometry();
                            if (res instanceof Polygon) {
                                polygon = (Polygon) res;
                            }
                            newPolygons.add(polygon);
                        }
                    }
                    entry.getValue().clear();
                    entry.getValue().addAll(newPolygons);
                }
            }
        }
        // Second step insertion
        int batchSize = 0;
        try(PreparedStatement ps = connection.prepareStatement("INSERT INTO " + TableLocation.parse(outputTable)
                + "(cell_id, the_geom, ISOLVL, ISOLABEL) VALUES (?, ?, ?, ?);")) {
            for (Map.Entry<Short, ArrayList<Geometry>> entry : polys.entrySet()) {
                ArrayList<Polygon> polygons = new ArrayList<>();
                if(!smooth && mergeTriangles) {
                    // Merge triangles
                    try {
                        CascadedPolygonUnion union = new CascadedPolygonUnion(entry.getValue());
                        Geometry mergeTriangles = union.union();
                        explode(mergeTriangles, polygons);
                    } catch (TopologyException t) {
                        log.warn(t.getLocalizedMessage(), t);
                        explode(factory.createGeometryCollection(entry.getValue().toArray(new Geometry[0])), polygons);
                    }
                } else {
                    explode(factory.createGeometryCollection(entry.getValue().toArray(new Geometry[0])), polygons);
                }
                for(Polygon polygon : polygons) {
                    int geomDim = 0;
                    boolean mixedDimension = false;
                    for(Coordinate coordinate : polygon.getExteriorRing().getCoordinates()) {
                        if(Double.isNaN(coordinate.getZ())) {
                            if(geomDim == 0) {
                                geomDim = 2;
                            } else if (geomDim == 3) {
                                mixedDimension = true;
                            }
                        } else {
                            if(geomDim == 0) {
                                geomDim = 3;
                            } else if (geomDim == 2) {
                                mixedDimension = true;
                            }
                        }
                    }
                    if(geomDim != exportDimension || mixedDimension) {
                        // Have to force geometry dimension one way
                        if(exportDimension == 3) {
                            polygon = ST_Force3D.convert(polygon, 0);
                            polygon.setSRID(srid);
                        } else {
                            // remove z
                            polygon = (Polygon)ST_Force2D.force2D(polygon);
                            polygon.setSRID(srid);
                        }
                    }
                    int parameterIndex = 1;
                    ps.setInt(parameterIndex++, cellId);
                    ps.setObject(parameterIndex++, polygon);
                    ps.setInt(parameterIndex++, entry.getKey());
                    ps.setString(parameterIndex++, isoLabels.get(entry.getKey()));
                    ps.addBatch();
                    batchSize++;
                    if (batchSize >= BATCH_MAX_SIZE) {
                        ps.executeBatch();
                        ps.clearBatch();
                        batchSize = 0;
                    }
                }
            }
            if (batchSize > 0) {
                ps.executeBatch();
            }
        }
    }

    /**
     *
     * @param connection
     * @throws SQLException
     */
    public void createTable(Connection connection) throws SQLException {
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        List<String> fields = JDBCUtilities.getColumnNames(connection, TableLocation.parse(pointTable, dbType));
        int pk = JDBCUtilities.getIntegerPrimaryKey(connection.unwrap(Connection.class), TableLocation.parse(pointTable, dbType));
        if(pk == 0) {
            throw new SQLException(pointTable+" does not contain a primary key");
        }
        String pkField = fields.get(pk - 1);
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), srid);
        Map<Short, ArrayList<Geometry>> polyMap = new HashMap<>();
        int lastCellId = -1;
        try(Statement st = connection.createStatement()) {
            String geometryType = "GEOMETRY(POLYGONZ,"+srid+")";
            exportDimension = 3;
            if(smooth && smoothCoefficient > 0) {
                // Bezier interpolation we loose 3d
                geometryType = "GEOMETRY(POLYGON,"+srid+")";
                exportDimension = 2;
            }
            st.execute("DROP TABLE IF EXISTS " + TableLocation.parse(outputTable, dbType));
            st.execute("CREATE TABLE " + TableLocation.parse(outputTable, dbType) +
                    "(PK SERIAL, CELL_ID INTEGER, THE_GEOM "+geometryType+", ISOLVL INTEGER, ISOLABEL VARCHAR);");
            String query = "SELECT CELL_ID, ST_X(p1.the_geom) xa,ST_Y(p1.the_geom) ya, ST_Z(p1.the_geom) za," +
                    "ST_X(p2.the_geom) xb,ST_Y(p2.the_geom) yb, ST_Z(p2.the_geom) zb," +
                    "ST_X(p3.the_geom) xc,ST_Y(p3.the_geom) yc, ST_Z(p3.the_geom) zc," +
                    " p1."+pointTableField+" lvla, p2."+pointTableField+" lvlb, p3."+pointTableField+" lvlc FROM "+triangleTable+" t, "+pointTable+" p1,"+pointTable+" p2,"+pointTable+" p3 WHERE t.PK_1 = p1."+pkField+" and t.PK_2 = p2."+pkField+" AND t.PK_3 = p3."+pkField+" order by cell_id;";
            try(ResultSet rs = st.executeQuery(query)) {
                // Cache columns index
                int xa = 0, xb = 0, xc = 0, ya = 0, yb = 0, yc = 0, za = 0, zb = 1, zc = 1, lvla = 0, lvlb = 0,
                        lvlc = 0, cell_id = 0;
                ResultSetMetaData resultSetMetaData = rs.getMetaData();
                for (int columnId = 1; columnId <= resultSetMetaData.getColumnCount(); columnId++) {
                    switch (resultSetMetaData.getColumnLabel(columnId).toUpperCase()) {
                        case "XA":
                            xa = columnId;
                            break;
                        case "XB":
                            xb = columnId;
                            break;
                        case "XC":
                            xc = columnId;
                            break;
                        case "YA":
                            ya = columnId;
                            break;
                        case "YB":
                            yb = columnId;
                            break;
                        case "YC":
                            yc = columnId;
                            break;
                        case "ZA":
                            za = columnId;
                            break;
                        case "ZB":
                            zb = columnId;
                            break;
                        case "ZC":
                            zc = columnId;
                            break;
                        case "LVLA":
                            lvla = columnId;
                            break;
                        case "LVLB":
                            lvlb = columnId;
                            break;
                        case "LVLC":
                            lvlc = columnId;
                            break;
                        case "CELL_ID":
                            cell_id = columnId;
                            break;
                    }
                }
                if (xa == 0 || xb == 0 || xc == 0 || ya == 0 || yb == 0 || yc == 0  || za == 0 || zb == 0 || zc == 0
                        || lvla == 0 || lvlb == 0 || lvlc == 0 || cell_id == 0) {
                    throw new SQLException("Missing field in input tables");
                }
                while(rs.next()) {
                    int cellId = rs.getInt(cell_id);
                    // Process polygons of last cell
                    if(cellId != lastCellId && lastCellId != -1) {
                        processCell(connection, cellId, polyMap);
                        polyMap.clear();
                    }
                    lastCellId = cellId;
                    // Split current triangle
                    Coordinate a = new Coordinate(rs.getDouble(xa), rs.getDouble(ya), rs.getDouble(za));
                    Coordinate b = new Coordinate(rs.getDouble(xb), rs.getDouble(yb), rs.getDouble(zb));
                    Coordinate c = new Coordinate(rs.getDouble(xc), rs.getDouble(yc), rs.getDouble(zc));
                    // Fetch data
                    TriMarkers triMarkers = new TriMarkers(a, b, c, dbaToW(rs.getDouble(lvla)),
                            dbaToW(rs.getDouble(lvlb)),
                            dbaToW(rs.getDouble(lvlc)));
                    // Split triangle
                    Map<Short, Deque<TriMarkers>> res = Contouring.processTriangle(triMarkers, isoLevels);
                    for(Map.Entry<Short, Deque<TriMarkers>> entry : res.entrySet()) {
                        if(!polyMap.containsKey(entry.getKey())) {
                            polyMap.put(entry.getKey(), new ArrayList<>());
                        }
                        ArrayList<Geometry> polygonsArray = polyMap.get(entry.getKey());
                        for(TriMarkers tri : entry.getValue()) {
                            Polygon poly = geometryFactory.createPolygon(new Coordinate[]{tri.p0, tri.p1, tri.p2, tri.p0});
                            polygonsArray.add(poly);
                        }
                    }
                }
            }
            if(!polyMap.isEmpty()) {
                processCell(connection, lastCellId, polyMap);
            }
        }
        if(!connection.getAutoCommit()) {
            connection.commit();
        }
    }

}
