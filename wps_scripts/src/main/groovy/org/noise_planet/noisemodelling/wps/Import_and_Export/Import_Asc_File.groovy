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

package org.noise_planet.noisemodelling.wps.Import_and_Export

import geoserver.GeoServer
import geoserver.catalog.Store
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.functions.io.asc.AscReaderDriver
import org.h2gis.functions.io.utility.PRJUtil
import org.h2gis.functions.spatial.crs.ST_SetSRID
import org.h2gis.functions.spatial.crs.ST_Transform
import org.h2gis.utilities.TableLocation
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.io.WKTWriter
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.Statement

title = 'Import Asc File.'
description = '&#10145;&#65039; Import ESRI Ascii Raster file and convert into a Digital Elevation Model (DEM) compatible with NoiseModelling (X,Y,Z). </br>'+
              '<hr>' +
              ' Valid file extensions : asc. </br> </br>' +
              '&#x2705; The output table is called: <b>DEM</b> and contain: </br>' +
              '- <b>THE_GEOM</b>: the 3D point cloud of the DEM (POINT) </br> </br>' +
              '<img src="/wps_images/import_asc_file.png" alt="Import asc file" width="95%" align="center">'

inputs = [
        pathFile : [
                name       : 'Path of the input File',
                title      : 'Path of the ESRI Ascii Raster file',
                description: '&#128194; Path of the ESRI Ascii Raster file you want to import, including its extension. </br> </br>' +
                             'For example: c:/home/receivers.asc',
                type       : String.class
        ],
        inputSRID: [
                name       : 'Projection identifier',
                title      : 'Projection identifier',
                description: '&#127757; Original projection identifier (also called SRID) of the .asc files. </br> </br>' +
                             'It should be an <a href="https://epsg.io/" target="_blank">EPSG</a> code, an integer with 4 or 5 digits (ex: <a href="https://epsg.io/3857" target="_blank">3857</a> is Pseudo-Mercator projection). </br> </br>' +
                             '&#128736; Default value: <b>4326 </b> ',
                type       : Integer.class,
                min        : 0, max: 1
        ],
        fence    : [
                name       : 'Fence geometry',
                title      : 'Fence geometry',
                description: 'Create DEM table only in the provided polygon',
                min        : 0, max: 1,
                type       : Geometry.class
        ],
        downscale: [
                name       : 'Skip pixels on each axis',
                title      : 'Skip pixels on each axis',
                description: 'Divide the number of rows and columns read by the following coefficient (FLOAT) </br> </br>' +
                             '&#128736; Default value: <b>1.0 </b>',
                min        : 0, max: 1,
                type       : Integer.class
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
    logger.info('Start : Import Asc File')
    logger.info("inputs {}", input) // log inputs of the run


    // Default SRID (WGS84)
    Integer defaultSRID = 4326
    // Get user SRID
    if (input['inputSRID']) {
        defaultSRID = input['inputSRID'] as Integer
    }

    Integer downscale = 1
    if (input['downscale']) {
        downscale = Math.max(1, input['downscale'] as Integer)
    }

    String fence = null
    if (input['fence']) {
        fence = (String) input['fence']
    }

    String pathFile = input["pathFile"] as String

    def file = new File(pathFile)
    if (!file.exists()) {
        resultString = pathFile + " is not found."
        // print to command window
        throw new Exception('ERROR : ' + resultString)
    }

    String outputTableName = 'DEM'

    // Create a connection statement to interact with the database in SQL
    Statement stmt = connection.createStatement()

    // Drop the table if already exists
    String dropOutputTable = "drop table if exists " + outputTableName
    stmt.execute(dropOutputTable)


    // Get the extension of the file
    String ext = pathFile.substring(pathFile.lastIndexOf('.') + 1, pathFile.length())
    if (ext != "asc") {
        resultString = "The extension is not valid"
        // print to command window
        throw new Exception('ERROR : ' + resultString)
    }

    AscReaderDriver ascDriver = new AscReaderDriver()
    ascDriver.setAs3DPoint(true)
    ascDriver.setExtractEnvelope()

    int srid = defaultSRID

    String filePath = new File(pathFile).getAbsolutePath()
    final int dotIndex = filePath.lastIndexOf('.')
    final String fileNamePrefix = filePath.substring(0, dotIndex)
    File prjFile = new File(fileNamePrefix + ".prj")
    if (prjFile.exists()) {
        logger.info("Found prj file :" + prjFile.getAbsolutePath())
        try {
            srid = PRJUtil.getSRID(prjFile)
            if (srid == 0) {
                srid = defaultSRID;
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("PRJ file invalid, use default SRID " + prjFile.getAbsolutePath())
        }
    } else {
        srid = defaultSRID
        logger.warn("PRJ file not found, use default SRID" + prjFile.getAbsolutePath())
    }
    if (fence != null) {
        // Reproject fence
        if (srid != 0) {
            // Transform fence to the same coordinate system than the DEM table
            Geometry fenceGeom = null
            WKTReader wktReader = new WKTReader()
            WKTWriter wktWriter = new WKTWriter()
            if (input['fence']) {
                fenceGeom = wktReader.read(input['fence'] as String)
            }
            logger.info("Got fence :" + wktWriter.write(fenceGeom))
            Geometry fenceTransform = ST_Transform.ST_Transform(connection, ST_SetSRID.setSRID(fenceGeom, 4326), srid)
            ascDriver.setExtractEnvelope(fenceTransform.getEnvelopeInternal())
            logger.info("Fence coordinate transformed :" + wktWriter.write(fenceTransform))
        } else {
            throw new IllegalArgumentException("Unable to find DEM SRID but fence was provided")
        }
    }
    if (downscale > 1) {
        ascDriver.setDownScale(downscale)
    }

    // Import ASC file
    RootProgressVisitor progressLogger = new RootProgressVisitor(1, true, 1)
    ascDriver.read(connection, new File(pathFile), progressLogger, outputTableName, srid)

    logger.info("Create spatial index on " + outputTableName )
    stmt.execute("Create spatial index on "+outputTableName+"(the_geom);")

    // Display the actual SRID in the command window
    logger.info("The SRID of your table is " + srid)

    resultString = "The table DEM has been uploaded to database ! </br>  Its SRID is : " + srid + ". </br> Remember that to calculate a noise map, your SRID must be in metric coordinates. Please use the Wps block 'Change SRID' if needed."

    // print to command window
    logger.info(resultString)
    logger.info('End : Import Asc File')

    // print to WPS Builder
    return resultString

}