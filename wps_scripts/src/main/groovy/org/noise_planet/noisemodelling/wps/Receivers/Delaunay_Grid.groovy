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
 * @Author Pierre Aumond, Université Gustave Eiffel
 * @Author Nicolas Fortin, Université Gustave Eiffel
 */


package org.noise_planet.noisemodelling.wps.Receivers

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.api.EmptyProgressVisitor
import org.h2gis.api.ProgressVisitor
import org.h2gis.functions.spatial.crs.ST_SetSRID
import org.h2gis.functions.spatial.crs.ST_Transform
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader

import org.noise_planet.noisemodelling.emission.*
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunayError
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.noise_planet.noisemodelling.propagation.*
import org.noise_planet.noisemodelling.jdbc.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger

title = 'Delaunay Grid'
description = '&#10145;&#65039; Computes a <a href="https://en.wikipedia.org/wiki/Delaunay_triangulation" target="_blank">Delaunay</a> grid of receivers.</br>' +
              '<hr>' +
              'The grid will be based on: <ul>' +
              '<li> the BUILDINGS table extent (option by default)</li>' +
              '<li> <b>OR</b> a single Geometry "fence" (Extent filter).</li></ul>' +
              '&#x2705; Two tables are returned:<ul>' +
              '<li> <b>RECEIVERS</b></li>' +
              '<li> <b>TRIANGLES</b></li>' +
              '<img src="/wps_images/delaunay_grid_output.png" alt="Delaunay grid output" width="95%" align="center">'

inputs = [
        tableBuilding      : [
                name       : 'Buildings table name',
                title      : 'Buildings table name',
                description: 'Name of the Buildings table. </br><br>' +
                             'The table must contain: <ul>' +
                             '<li> <b> THE_GEOM </b> : the 2D geometry of the building (POLYGON or MULTIPOLYGON)</li></ul>',
                type       : String.class
        ],
        fence              : [
                name       : 'Extent filter', 
                title      : 'Extent filter',
                description: 'Create receivers only in the provided polygon (fence)',
                min        : 0, max: 1,
                type       : Geometry.class
        ],
        sourcesTableName   : [
                name       : 'Sources table name',
                title      : 'Sources table name',
                description: 'Name of the Road table.</br><br>' +
                             'Receivers will not be created on the specified road width',
                type       : String.class
        ],
        maxCellDist        : [
                name       : 'Maximum cell size',
                title      : 'Maximum cell size',
                description: 'Maximum distance used to split the domain into sub-domains (in meters) (FLOAT).</br><br>' +
                             'In a logic of optimization of processing times, it allows to limit the number of objects (buildings, roads, …) stored in memory during the Delaunay triangulation.</br></br>' +
                             '&#128736; Default value: <b>600 </b>',
                min        : 0, max: 1,
                type       : Double.class
        ],
        roadWidth          : [
                name       : 'Road width',
                title      : 'Road width',
                description: 'Set Road Width (in meters) (FLOAT).</br> </br>' +
                             'No receivers closer than road width distance will be created.</br> </br>' +
                             '&#128736; Default value: <b>2 </b>',
                min        : 0, max: 1,
                type       : Double.class
        ],
        maxArea            : [
                name       : 'Maximum Area',
                title      : 'Maximum Area',
                description: 'Set Maximum Area (in m2) (FLOAT).</br> </br>' +
                             'No triangles larger than provided area will be created.</br>' +
                             'Smaller area will create more receivers.</br> </br> ' +
                             '&#128736; Default value: <b>2500 </b>',
                min        : 0, max: 1,
                type       : Double.class
        ],
        height             : [
                name       : 'Height',
                title      : 'Height',
                description: 'Receiver height relative to the ground (in meters) (FLOAT).</br> </br>' +
                             '&#128736; Default value: <b>4 </b>',
                min        : 0, max: 1,
                type       : Double.class
        ],
        outputTableName    : [
                name       : 'outputTableName',
                title      : 'Name of output table',
                description: 'Name of the output table.</br> </br>' +
                             'Do not write the name of a table that contains a space.</br> </br>' +
                             '&#128736; Default value: <b>RECEIVERS </b>',
                min        : 0, max: 1,
                type       : String.class
        ],
        isoSurfaceInBuildings: [
                name        : 'Create IsoSurfaces over buildings',
                title       : 'Create IsoSurfaces over buildings',
                description : 'If enabled, isosurfaces will be visible at the location of buildings </br></br>' +
                              '&#128736; Default value: <b>false </b>',
                min         : 0, max: 1,
                type        : Boolean.class
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
    logger.info('Start : Delaunay grid')
    logger.info("inputs {}", input) // log inputs of the run


    String receivers_table_name = "RECEIVERS"
    if (input['outputTableName']) {
        receivers_table_name = input['outputTableName']
    }
    receivers_table_name = receivers_table_name.toUpperCase()

    String sources_table_name = "SOURCES"
    if (input['sourcesTableName']) {
        sources_table_name = input['sourcesTableName']
    } else {
        return "Source table must be specified"
    }
    sources_table_name = sources_table_name.toUpperCase()

    String building_table_name = "BUILDINGS"
    if (input['tableBuilding']) {
        building_table_name = input['tableBuilding']
    }
    building_table_name = building_table_name.toUpperCase()

    boolean isoSurfaceInBuildings = false;
    if(input['isoSurfaceInBuildings)']) {
        isoSurfaceInBuildings = input['isoSurfaceInBuildings'] as Boolean
    }


    Double maxCellDist = 600.0
    if (input['maxCellDist']) {
        maxCellDist = input['maxCellDist'] as Double
    }

    Double height = 4.0
    if (input['height']) {
        height = input['height'] as Double
    }

    Double roadWidth = 2.0
    if (input['roadWidth']) {
        roadWidth = input['roadWidth'] as Double
    }

    Double maxArea = 2500
    if (input.containsKey('maxArea')) {
        maxArea = input['maxArea'] as Double
    }

    int srid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(building_table_name))

    Geometry fence = null
    WKTReader wktReader = new WKTReader()
    if (input['fence']) {
        fence = wktReader.read(input['fence'] as String)
    }

    //Statement sql = connection.createStatement()
    Sql sql = new Sql(connection)
    connection = new ConnectionWrapper(connection)
    RootProgressVisitor progressLogger = new RootProgressVisitor(1, true, 1)

    // Delete previous receivers grid
    sql.execute(String.format("DROP TABLE IF EXISTS %s", receivers_table_name))
    sql.execute("DROP TABLE IF EXISTS TRIANGLES")

    // Generate receivers grid for noise map rendering
    DelaunayReceiversMaker delaunayReceiversMaker = new DelaunayReceiversMaker(building_table_name, sources_table_name)

    if (fence != null) {
        // Reproject fence
        int targetSrid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(building_table_name))
        if (targetSrid == 0) {
            targetSrid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(sources_table_name))
        }
        if (targetSrid != 0) {
            // Transform fence to the same coordinate system than the buildings & sources
            fence = ST_Transform.ST_Transform(connection, ST_SetSRID.setSRID(fence, 4326), targetSrid)
            delaunayReceiversMaker.setMainEnvelope(fence.getEnvelopeInternal())
        } else {
            System.err.println("Unable to find buildings or sources SRID, ignore fence parameters")
        }
    }


    // Avoid loading to much geometries when doing Delaunay triangulation
    delaunayReceiversMaker.setMaximumPropagationDistance(maxCellDist)
    // Receiver height relative to the ground
    delaunayReceiversMaker.setReceiverHeight(height)
    // No receivers closer than road width distance
    delaunayReceiversMaker.setRoadWidth(roadWidth)
    // No triangles larger than provided area
    delaunayReceiversMaker.setMaximumArea(maxArea)

    delaunayReceiversMaker.setIsoSurfaceInBuildings(isoSurfaceInBuildings)

    logger.info("Delaunay initialize")
    delaunayReceiversMaker.initialize(connection, new EmptyProgressVisitor())

    if(input['errorDumpFolder']) {
        // Will write the input mesh in this folder in order to
        // help debugging delaunay triangulation
        delaunayReceiversMaker.setExceptionDumpFolder(input['errorDumpFolder'] as String)
    }

    AtomicInteger pk = new AtomicInteger(0)
    ProgressVisitor progressVisitorNM = progressLogger.subProcess(delaunayReceiversMaker.getGridDim() * delaunayReceiversMaker.getGridDim())

    try {
        for (int i = 0; i < delaunayReceiversMaker.getGridDim(); i++) {
            for (int j = 0; j < delaunayReceiversMaker.getGridDim(); j++) {
                logger.info("Compute cell " + (i * delaunayReceiversMaker.getGridDim() + j + 1) + " of " + delaunayReceiversMaker.getGridDim() * delaunayReceiversMaker.getGridDim())
                delaunayReceiversMaker.generateReceivers(connection, i, j, receivers_table_name, "TRIANGLES", pk)
                progressVisitorNM.endStep()
            }
        }
    } catch (LayerDelaunayError ex) {
        logger.error("Got an error use the errorDumpFolder parameter with a folder path in order to save the " +
                "input geometries for debugging purpose")
        throw ex
    }

    logger.info("Create spatial index on "+receivers_table_name+" table")
    sql.execute("Create spatial index on " + receivers_table_name + "(the_geom);")

    int nbReceivers = sql.firstRow("SELECT COUNT(*) FROM " + receivers_table_name)[0] as Integer

    // Process Done
    resultString = "Process done. " + receivers_table_name + " (" + nbReceivers + " receivers) and TRIANGLES tables created. "

    // print to command window
    logger.info('Result : ' + resultString)
    logger.info('End : Delaunay grid')

    // print to WPS Builder
    return resultString

}

