/**
 * NoiseModelling is an open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by the DECIDE team from the Lab-STICC (CNRS) and by the Mixt Research Unit in Environmental Acoustics (Université Gustave Eiffel).
 * <http://noise-planet.org/noisemodelling.html>
 *
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 *
 * Contact: contact@noise-planet.org
 *
 */

/**
 * @Author Nicolas Fortin, Université Gustave Eiffel
 */


package org.noise_planet.noisemodelling.wps.Receivers

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import groovy.time.TimeCategory
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.functions.spatial.crs.ST_SetSRID
import org.h2gis.functions.spatial.crs.ST_Transform
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.dbtypes.DBUtils
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.WKTReader
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

title = 'Buildings Grid'
description = '&#10145;&#65039; Generates receivers, 2m around the building facades, at a given height. </br>' +
              '<hr>' +
              '&#x2705; The output table is called <b>RECEIVERS</b> </br></br>'+
              '<img src="/wps_images/building_grid_output.png" alt="Building grid output" width="95%" align="center">'

inputs = [
        tableBuilding : [
                name       : 'Buildings table name',
                title      : 'Buildings table name',
                description: 'Name of the Buildings table. <br></br>' +
                             'The table must contain: <ul>' +
                             '<li> <b>THE_GEOM</b> : the 2D geometry of the building (POLYGON or MULTIPOLYGON)</li>' +
                             '<li> <b>HEIGHT</b> : the height of the building (in meter) (FLOAT)</li>' +
                             '<li> <b>POP</b> : (optional field) building population to add in the receiver attribute (FLOAT)</li></ul>',
                type       : String.class
        ],
        fence : [
                name       : 'Fence geometry',
                title      : 'Extent filter',
                description: 'Create receivers only in the provided polygon (fence)',
                min        : 0, 
                max        : 1,
                type       : Geometry.class
        ],
        fenceTableName : [
                name       : 'Fence geometry from table',
                title      : 'Filter using table bounding box',
                description: 'Filter receivers, using the bounding box of the given table name:<br><ol>' +
                             '<li> Extract the bounding box of the specified table,</li>' +
                             '<li> then create only receivers on the table bounding box.</li></ol>' +
                             'The given table must contain: <ul>' +
                             '<li> <b>THE_GEOM</b> : any geometry type. </li></ul>',
                min        : 0, 
                max        : 1,
                type       : String.class
        ],
        sourcesTableName : [
                name       : 'Sources table name',
                title      : 'Sources table name',
                description: 'Keep only receivers that are at least 1 meter from the provided source geometries.</br></br>' +
                             'The source geometries table must contain: <ul>' +
                             '<li> <b>THE_GEOM</b> : any geometry type. </li></ul>',
                min        : 0, 
                max        : 1,
                type       : String.class
        ],
        delta : [
                name       : 'Receivers minimal distance',
                title      : 'Distance between receivers',
                description: 'Distance between receivers (in the Cartesian plane - in meter) (FLOAT) </br></br>'+
                             '&#128736; Default value: <b>10 </b>',
                min        : 0, 
                max        : 1,
                type       : Double.class
        ],
        height : [
                name       : 'Height',
                title      : 'Height',
                description: 'Height of receivers (in meter) (FLOAT) </br></br>' +
                             '&#128736; Default value: <b>4</b>',
                min        : 0, 
                max        : 1,
                type       : Double.class
        ],
        distance          : [
                name       : 'Distance',
                title      : 'Distance from wall',
                description: 'Distance of receivers from the wall in meters (FLOAT) </br></br>' +
                             '&#128736; Default value: <b>2 </b>',
                min        : 0, max: 1,
                type       : Double.class
        ]
]

outputs = [
        result: [
                name       : 'Result output string',
                title      : 'Result output string',
                description: 'This type of result does not allow the blocks to be linked together.',
                type       : String.class
        ]
]

static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}


def run(input) {

    // Get name of the database
    // by default an embedded h2gis database is created
    // Advanced user can replace this database for a postGis or h2Gis server database.
    String dbName = "h2gisdb"

    // Open connection
    openGeoserverDataStoreConnection(dbName).withCloseable {
        Connection connection ->
            return [result: exec(connection, input)]
    }
}


def exec(Connection connection, input) {

    // output string, the information given back to the user
    String resultString = null

    // Create a logger to display messages in the geoserver logs and in the command prompt.
    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")

    // print to command window
    logger.info('Start : Receivers grid around buildings')
    logger.info("inputs {}", input) // log inputs of the run


    String receivers_table_name = "RECEIVERS"

    Double delta = 10
    if (input['delta']) {
        delta = input['delta'] as Double
    }

    Double h = 4.0d
    if (input['height']) {
        h = input['height'] as Double
    }

    Double distance = 2.0d
    if (input['distance']) {
        distance = input['distance'] as Double
    }



    String sources_table_name = "SOURCES"
    if (input['sourcesTableName']) {
        sources_table_name = input['sourcesTableName']
    }
    sources_table_name = sources_table_name.toUpperCase()


    String building_table_name = input['tableBuilding']
    building_table_name = building_table_name.toUpperCase()

    Boolean hasPop = JDBCUtilities.hasField(connection, building_table_name, "POP")
    if (hasPop) logger.info("The building table has a column named POP.")
    if (!hasPop) logger.info("The building table has not a column named POP.")

    if (!JDBCUtilities.hasField(connection, building_table_name, "HEIGHT")) {
        resultString = "Buildings table must have HEIGHT field"
        return resultString
    }

    //Statement sql = connection.createStatement()
    Sql sql = new Sql(connection)
    sql.execute(String.format("DROP TABLE IF EXISTS %s", receivers_table_name))

    // Reproject fence
    int targetSrid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(building_table_name))
    if (targetSrid == 0 && input['sourcesTableName']) {
        targetSrid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(sources_table_name))
    }

    Geometry fenceGeom = null
    if (input['fence'] != null) {
        if (targetSrid != 0) {
            // Transform fence to the same coordinate system than the buildings & sources
            WKTReader wktReader = new WKTReader()
            fence = wktReader.read(input['fence'] as String)
            fenceGeom = ST_Transform.ST_Transform(connection, ST_SetSRID.setSRID(fence, 4326), targetSrid)
        } else {
            throw new Exception("Unable to find buildings or sources SRID, ignore fence parameters")
        }
    } else if (input['fenceTableName']) {
        fenceGeom = GeometryTableUtilities.getEnvelope(connection, TableLocation.parse(input['fenceTableName'] as String), "THE_GEOM")
    }

    def buildingPk = JDBCUtilities.getColumnName(connection, building_table_name,
            JDBCUtilities.getIntegerPrimaryKey(connection,
                    TableLocation.parse(building_table_name, DBUtils.getDBType(connection))))
    if (buildingPk == "") {
        return "Buildings table must have a primary key"
    }

    sql.execute("drop table if exists tmp_receivers_lines")

    if (fenceGeom != null) {
        sql.execute("create table tmp_receivers_lines(pk int not null primary key, the_geom geometry) as select " + buildingPk + " as pk, st_simplifypreservetopology(ST_ToMultiLine(ST_Buffer(the_geom, :distance_wall, 'join=bevel')), 0.05) the_geom from " + building_table_name + " WHERE the_geom && :fenceGeom AND ST_INTERSECTS(the_geom, :fenceGeom)", [fenceGeom : fenceGeom, distance_wall : distance])
    } else {
        sql.execute("create table tmp_receivers_lines(pk int not null primary key, the_geom geometry) as select " + buildingPk + " as pk, st_simplifypreservetopology(ST_ToMultiLine(ST_Buffer(the_geom, :distance_wall, 'join=bevel')), 0.05) the_geom from " + building_table_name, [distance_wall : distance])
    }

    logger.info('create line of receivers')

    sql.execute("drop table if exists tmp_relation_screen_building;")
    sql.execute("create spatial index on tmp_receivers_lines(the_geom)")
    logger.info('list buildings that will remove receivers (if height is superior than receiver height)')
    sql.execute("create table tmp_relation_screen_building as select b." + buildingPk + " as PK_building, s.pk as pk_screen from " + building_table_name + " b, tmp_receivers_lines s where b.the_geom && s.the_geom and s.pk != b.pk and ST_Intersects(b.the_geom, s.the_geom) and b.height > " + h)
    sql.execute("CREATE INDEX ON tmp_relation_screen_building(PK_building);")
    sql.execute("CREATE INDEX ON tmp_relation_screen_building(pk_screen);")
    sql.execute("drop table if exists tmp_screen_truncated;")
    logger.info('truncate receiver lines')
    sql.execute("create table tmp_screen_truncated(pk_screen integer not null, the_geom geometry) as select r.pk_screen, ST_DIFFERENCE(s.the_geom, ST_BUFFER(ST_ACCUM(b.the_geom), :distance_wall)) the_geom from tmp_relation_screen_building r, " + building_table_name + " b, tmp_receivers_lines s WHERE PK_building = b." + buildingPk + " AND pk_screen = s.pk  GROUP BY pk_screen, s.the_geom;", [distance_wall : distance])
    logger.info('Add primary key')
    sql.execute("ALTER TABLE tmp_screen_truncated add primary key(pk_screen)")
    sql.execute("DROP TABLE IF EXISTS TMP_SCREENS_MERGE;")
    sql.execute("DROP TABLE IF EXISTS TMP_SCREENS;")
    logger.info('union of truncated receivers and non tructated')
    sql.execute("create table TMP_SCREENS_MERGE (pk integer not null, the_geom geometry) as select s.pk, s.the_geom the_geom from tmp_receivers_lines s where not st_isempty(s.the_geom) and pk not in (select pk_screen from tmp_screen_truncated) UNION ALL select pk_screen, the_geom from tmp_screen_truncated where not st_isempty(the_geom);")
    logger.info('Add primary key')
    sql.execute("ALTER TABLE TMP_SCREENS_MERGE add primary key(pk)")
    logger.info('Collect all lines and convert into points using custom method')
    sql.execute("CREATE TABLE TMP_SCREENS(pk integer, the_geom geometry)")
    def qry = 'INSERT INTO TMP_SCREENS(pk , the_geom) VALUES (?,?);'
    GeometryFactory factory = new GeometryFactory(new PrecisionModel(), targetSrid);
    logger.info('Split line to points')
    int nrows = sql.firstRow('SELECT COUNT(*) FROM TMP_SCREENS_MERGE')[0] as Integer
    RootProgressVisitor progressLogger = new RootProgressVisitor(nrows, true, 1)
    sql.withBatch(100, qry) { ps ->
        sql.eachRow("SELECT pk, the_geom from TMP_SCREENS_MERGE") { row ->
            List<Coordinate> pts = new ArrayList<Coordinate>()
            def geom = row[1] as Geometry
            if (geom instanceof LineString) {
                splitLineStringIntoPoints(geom as LineString, delta, pts)
            } else if (geom instanceof MultiLineString) {
                for (int idgeom = 0; idgeom < geom.numGeometries; idgeom++) {
                    splitLineStringIntoPoints(geom.getGeometryN(idgeom) as LineString, delta, pts)
                }
            }
            for (int idp = 0; idp < pts.size(); idp++) {
                Coordinate pt = pts.get(idp);
                if (!Double.isNaN(pt.x) && !Double.isNaN(pt.y)) {
                    // define coordinates of receivers
                    Coordinate newCoord = new Coordinate(pt.x, pt.y, h)
                    ps.addBatch(row[0] as Integer, factory.createPoint(newCoord))
                }
            }
            progressLogger.endStep()
        }
    }
    sql.execute("drop table if exists TMP_SCREENS_MERGE")
    sql.execute("drop table if exists " + receivers_table_name)

    if (!hasPop) {
        logger.info('create RECEIVERS table...')


        sql.execute("create table " + receivers_table_name + "(pk integer not null AUTO_INCREMENT, the_geom geometry,build_pk integer)")
        sql.execute("insert into " + receivers_table_name + "(the_geom, build_pk) select ST_SetSRID(the_geom," + targetSrid.toInteger() + ") , pk building_pk from TMP_SCREENS;")
        logger.info('Add primary key')
        sql.execute("ALTER TABLE "+receivers_table_name+" add primary key(pk)")

        if (input['sourcesTableName']) {
            // Delete receivers near sources
            logger.info('Delete receivers near sources...')
            sql.execute("Create spatial index on " + sources_table_name + "(the_geom);")
            sql.execute("delete from " + receivers_table_name + " g where exists (select 1 from " + sources_table_name + " r where st_expand(g.the_geom, 1, 1) && r.the_geom and st_distance(g.the_geom, r.the_geom) < 1 limit 1);")
        }

        if (fenceGeom != null) {
            // delete receiver not in fence filter
            sql.execute("delete from " + receivers_table_name + " g where not ST_INTERSECTS(g.the_geom , :fenceGeom);", [fenceGeom : fenceGeom])
        }
    } else {
        logger.info('create RECEIVERS table...')
        // building have population attribute
        // set population attribute divided by number of receiver to each receiver
        sql.execute("DROP TABLE IF EXISTS tmp_receivers")
        sql.execute("create table tmp_receivers(pk integer not null AUTO_INCREMENT, the_geom geometry,build_pk integer not null)")

        sql.execute("insert into tmp_receivers(the_geom, build_pk) select ST_SetSRID(the_geom," + targetSrid.toInteger() + "), pk building_pk from TMP_SCREENS;")
        logger.info('Add primary key')
        sql.execute("ALTER TABLE tmp_receivers add primary key(pk)")

        if (input['sourcesTableName']) {
            // Delete receivers near sources
            logger.info('Delete receivers near sources...')
            sql.execute("Create spatial index on " + sources_table_name + "(the_geom);")
            sql.execute("delete from tmp_receivers g where exists (select 1 from " + sources_table_name + " r where st_expand(g.the_geom, 1) && r.the_geom and st_distance(g.the_geom, r.the_geom) < 1 limit 1);")
        }

        if (fenceGeom != null) {
            // delete receiver not in fence filter
            sql.execute("delete from tmp_receivers g where not ST_INTERSECTS(g.the_geom , :fenceGeom);", [fenceGeom : fenceGeom])
        }

        sql.execute("CREATE INDEX ON tmp_receivers(build_pk)")
        logger.info('Distribute population over receivers')
        sql.execute("create table " + receivers_table_name + "(pk integer not null AUTO_INCREMENT, the_geom geometry,build_pk integer, pop real)");
        sql.execute("insert into "+receivers_table_name+"(the_geom, build_pk, pop) select a.the_geom, a.build_pk, b.pop/COUNT(DISTINCT aa.pk)::float from tmp_receivers a, " + building_table_name + " b,tmp_receivers aa where b." + buildingPk + " = a.build_pk and a.build_pk = aa.build_pk GROUP BY a.the_geom, a.build_pk, b.pop;")
        logger.info('Add primary key')
        sql.execute("ALTER TABLE "+receivers_table_name+" add primary key(pk)")

        sql.execute("drop table if exists tmp_receivers")
    }
    // cleaning
    sql.execute("drop table TMP_SCREENS")
    sql.execute("drop table tmp_screen_truncated")
    sql.execute("drop table tmp_relation_screen_building")
    sql.execute("drop table tmp_receivers_lines")
    sql.execute("drop table if exists tmp_buildings;")
    // Process Done
    resultString = "Process done. Table of receivers " + receivers_table_name + " created !"

    // print to command window
    logger.info('Result : ' + resultString)
    logger.info('End : Receivers grid around buildings')

    // print to WPS Builder
    return resultString

}


/**
 *
 * @param geom Geometry
 * @param segmentSizeConstraint Maximal distance between points
 * @param [out]pts computed points
 * @return Fixed distance between points
 */
double splitLineStringIntoPoints(LineString geom, double segmentSizeConstraint,
                                 List<Coordinate> pts) {
    // If the linear sound source length is inferior than half the distance between the nearest point of the sound
    // source and the receiver then it can be modelled as a single point source
    double geomLength = geom.getLength();
    if (geomLength < segmentSizeConstraint) {
        // Return mid point
        Coordinate[] points = geom.getCoordinates();
        double segmentLength = 0;
        final double targetSegmentSize = geomLength / 2.0;
        for (int i = 0; i < points.length - 1; i++) {
            Coordinate a = points[i];
            final Coordinate b = points[i + 1];
            double length = a.distance3D(b);
            if (length + segmentLength > targetSegmentSize) {
                double segmentLengthFraction = (targetSegmentSize - segmentLength) / length;
                Coordinate midPoint = new Coordinate(a.x + segmentLengthFraction * (b.x - a.x),
                        a.y + segmentLengthFraction * (b.y - a.y),
                        a.z + segmentLengthFraction * (b.z - a.z));
                pts.add(midPoint);
                break;
            }
            segmentLength += length;
        }
        return geom.getLength();
    } else {
        double targetSegmentSize = geomLength / Math.ceil(geomLength / segmentSizeConstraint);
        Coordinate[] points = geom.getCoordinates();
        double segmentLength = 0.0;

        // Mid point of segmented line source
        def midPoint = null;
        for (int i = 0; i < points.length - 1; i++) {
            Coordinate a = points[i];
            final Coordinate b = points[i + 1];
            double length = a.distance3D(b);
            if (Double.isNaN(length)) {
                length = a.distance(b);
            }
            while (length + segmentLength > targetSegmentSize) {
                //LineSegment segment = new LineSegment(a, b);
                double segmentLengthFraction = (targetSegmentSize - segmentLength) / length;
                Coordinate splitPoint = new Coordinate();
                splitPoint.x = a.x + segmentLengthFraction * (b.x - a.x);
                splitPoint.y = a.y + segmentLengthFraction * (b.y - a.y);
                splitPoint.z = a.z + segmentLengthFraction * (b.z - a.z);
                if (midPoint == null && length + segmentLength > targetSegmentSize / 2) {
                    segmentLengthFraction = (targetSegmentSize / 2.0 - segmentLength) / length;
                    midPoint = new Coordinate(a.x + segmentLengthFraction * (b.x - a.x),
                            a.y + segmentLengthFraction * (b.y - a.y),
                            a.z + segmentLengthFraction * (b.z - a.z));
                }
                pts.add(midPoint);
                a = splitPoint;
                length = a.distance3D(b);
                if (Double.isNaN(length)) {
                    length = a.distance(b);
                }
                segmentLength = 0;
                midPoint = null;
            }
            if (midPoint == null && length + segmentLength > targetSegmentSize / 2) {
                double segmentLengthFraction = (targetSegmentSize / 2.0 - segmentLength) / length;
                midPoint = new Coordinate(a.x + segmentLengthFraction * (b.x - a.x),
                        a.y + segmentLengthFraction * (b.y - a.y),
                        a.z + segmentLengthFraction * (b.z - a.z));
            }
            segmentLength += length;
        }
        if (midPoint != null) {
            pts.add(midPoint);
        }
        return targetSegmentSize;
    }
}