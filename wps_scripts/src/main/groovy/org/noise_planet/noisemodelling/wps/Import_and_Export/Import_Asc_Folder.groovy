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
 */

package org.noise_planet.noisemodelling.wps.Import_and_Export

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.io.FileType
import org.apache.commons.io.FilenameUtils
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.functions.io.utility.PRJUtil
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.dbtypes.DBUtils
import org.noise_planet.noisemodelling.jdbc.utils.AscReaderDriver
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.Statement

title = 'Import all .asc files from a folder'
description = '&#10145;&#65039; Import all files with .asc extension from a folder to the database </br>' +
              '<hr>' +
              '&#x2705; The resulting tables will have the same name as the input files </br> </br>' +
              '<img src="/wps_images/import_asc_folder.png" alt="Import asc folder" width="95%" align="center">'

inputs = [
        pathFolder: [
                name       : 'Path of the folder',
                title      : 'Path of the folder',
                description: '&#128194; Path of the folder </br> </br>' +
                             'For example: c:/home/inputdata/ ',
                type       : String.class
        ],
        inputSRID: [
                name       : 'Projection identifier',
                title      : 'Projection identifier',
                description: '&#127757; Original projection identifier (also called SRID) of the .asc file. </br> </br>' +
                             'It should be an <a href="https://epsg.io/" target="_blank">EPSG</a> code, an integer with 4 or 5 digits (ex: <a href="https://epsg.io/3857" target="_blank">3857</a> is Pseudo-Mercator projection). </br> </br>' +
                             '&#128736; Default value: <b>4326 </b> ',
                type       : Integer.class,
                min        : 0, max: 1
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
    logger.info('Start : Import all asc files of a folder')
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

    // Get path of the folder
    String folder = input["pathFolder"] as String
    def dir = new File(folder)

    String outputTableName = 'DEM'

    // Create a connection statement to interact with the database in SQL
    Statement stmt = connection.createStatement()

    // Drop the table if already exists
    String dropOutputTable = "drop table if exists " + outputTableName
    stmt.execute(dropOutputTable)

    dir.eachFileRecurse(FileType.FILES) { file ->
        String pathFile = file as String
        // Get the extension of the file
        String ext = pathFile.substring(pathFile.lastIndexOf('.') + 1, pathFile.length())
        if (ext == "asc") {

            int srid
            final int dotIndex = pathFile.lastIndexOf('.')
            final String fileNamePrefix = pathFile.substring(0, dotIndex)
            File prjFile = new File(fileNamePrefix + ".prj")
            if (prjFile.exists()) {
                logger.info("Found prj file :" + prjFile.getAbsolutePath())
                try {
                    srid = PRJUtil.getSRID(prjFile)
                    if (srid == 0) {
                        srid = defaultSRID
                    }
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("PRJ file invalid, use default SRID " + prjFile.getAbsolutePath())
                }
            } else {
                srid = defaultSRID
                logger.warn("PRJ file not found, use default SRID : " + defaultSRID )
            }


            // get the name of the fileName
            String fileName = FilenameUtils.removeExtension(new File(pathFile).getName())
            // replace whitespaces by _ in the file name
            fileName.replaceAll("\\s", "_")
            // remove special characters in the file name
            fileName.replaceAll("[^a-zA-Z0-9 ]+", "_")

            AscReaderDriver ascDriver = new AscReaderDriver()
            ascDriver.setAs3DPoint(true)
            ascDriver.deleteTable = false
            ascDriver.setExtractEnvelope()
            if (downscale > 1) {
                ascDriver.setDownScale(downscale)
            }

            // Import ASC file
            RootProgressVisitor progressLogger = new RootProgressVisitor(1, true, 1)
            ascDriver.read(connection, new File(pathFile), progressLogger, outputTableName, srid)
        }
    }

    logger.info("Create spatial index on "+ outputTableName )
    stmt.execute("Create spatial index on "+outputTableName+"(the_geom);")

    resultString = "The table(s) DEM has/have been uploaded to database !"

    // print to command window
    logger.info(resultString)
    logger.info('End : Import all files of a folder')

    // print to WPS Builder
    return resultString

}