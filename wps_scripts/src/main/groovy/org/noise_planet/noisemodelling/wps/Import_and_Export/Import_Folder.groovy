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

package org.noise_planet.noisemodelling.wps.Import_and_Export

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.io.FileType
import org.apache.commons.io.FilenameUtils
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.api.EmptyProgressVisitor
import org.h2gis.functions.io.csv.CSVDriverFunction
import org.h2gis.functions.io.dbf.DBFDriverFunction
import org.h2gis.functions.io.geojson.GeoJsonDriverFunction
import org.h2gis.functions.io.gpx.GPXDriverFunction
import org.h2gis.functions.io.osm.OSMDriverFunction
import org.h2gis.functions.io.shp.SHPDriverFunction
import org.h2gis.functions.io.tsv.TSVDriverFunction
import org.h2gis.utilities.GeometryMetaData
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.dbtypes.DBUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

title = 'Import all files from a folder'
description = '&#10145;&#65039; Import all files with a specified extension from a folder to the database. ' +
              '<hr>' +
              'Valid file extensions: csv, dbf, geojson, gpx, bz2, gz, osm, shp, tsv.</br> </br>'+
              '&#x2705; The resulting tables will have the same name as the input files</br> </br>' +
              '<img src="/wps_images/import_folder.png" alt="Import folder" width="95%" align="center">'

inputs = [
        pathFolder: [
                name       : 'Path of the folder',
                title      : 'Path of the folder',
                description: '&#128194; Path of the folder </br> </br>' +
                             ' For example : c:/home/inputdata/ ',
                type       : String.class
        ],
        inputSRID: [
                name       : 'Projection identifier',
                title      : 'Projection identifier',
                description: '&#127757; Original projection identifier (also called SRID) of your table. </br> </br>' +
                             'It should be an <a href="https://epsg.io/" target="_blank">EPSG</a> code, an integer with 4 or 5 digits (ex: <a href="https://epsg.io/3857" target="_blank">3857</a> is Pseudo-Mercator projection). </br> </br>' +
                             'This entry is optional because many formats already include the projection and you can also import files without geometry attributes.</br> </br>' +
                             'If the table is geometric and if this parameter is not filled and:</br>' +
                             '- the file has a .prj file associated: the SRID is deduced from the .prj </br>' +
                             '- the file has no .prj file associated: we apply the WGS84 (<a href="https://epsg.io/4326" target="_blank">EPSG:4326</a>) code </br> </br>' +                          
                             '&#128736; Default value: <b>4326 </b> ',
                type       : Integer.class,
                min        : 0, max: 1
        ],
        importExt: [
                name       : 'Extension to import',
                title      : 'Extension to import',
                description: 'Extension to import. </br> </br>' +
                             'For example: shp ',
                type       : String.class
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

def exec(Connection connection, input) {

    // output string, the information given back to the user
    String resultString = null

    // Create a logger to display messages in the geoserver logs and in the command prompt.
    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")

    // print to command window
    logger.info('Start : Import all files of a folder')
    logger.info("inputs {}", input) // log inputs of the run


    // Default SRID (WGS84)
    Integer srid = 4326
    // Get user SRID
    if (input['inputSRID']) {
        srid = input['inputSRID'] as Integer
    }

    // Get file extension
    String importExt = input["importExt"] as String

    // Get path of the folder
    String folder = input["pathFolder"] as String
    def dir = new File(folder)

    // name of the imported tables
    String outputTableName_full = ""

    // Create a connection statement to interact with the database in SQL
    Statement stmt = connection.createStatement()

    dir.eachFileRecurse(FileType.FILES) { file ->

        String pathFile = file as String
        String ext = pathFile.substring(pathFile.lastIndexOf('.') + 1, pathFile.length())

        if (ext == importExt) {

            // get the name of the fileName
            String fileName = FilenameUtils.removeExtension(new File(pathFile).getName())
            // replace whitespaces by _ in the file name
            fileName.replaceAll("\\s", "_")
            // remove special characters in the file name
            fileName.replaceAll("[^a-zA-Z0-9 ]+", "_")
            // the tableName will be called as the fileName
            String outputTableName = fileName.toUpperCase()
            TableLocation outputTableIdentifier = TableLocation.parse(outputTableName, DBUtils.getDBType(connection))

            // Drop the table if already exists
            String dropOutputTable = "drop table if exists " + outputTableIdentifier.toString()
            stmt.execute(dropOutputTable)

            switch (ext) {
                case "csv":
                    CSVDriverFunction csvDriver = new CSVDriverFunction()
                    csvDriver.importFile(connection, outputTableName, new File(pathFile), new EmptyProgressVisitor())
                    outputTableName_full = outputTableName + " & " + outputTableName_full
                    break
                case "dbf":
                    DBFDriverFunction dbfDriver = new DBFDriverFunction()
                    dbfDriver.importFile(connection, outputTableName, new File(pathFile), new EmptyProgressVisitor())
                    outputTableName_full = outputTableName + " & " + outputTableName_full
                    break
                case "geojson":
                    GeoJsonDriverFunction geoJsonDriver = new GeoJsonDriverFunction()
                    geoJsonDriver.importFile(connection, outputTableName, new File(pathFile), new EmptyProgressVisitor())
                    outputTableName_full = outputTableName + " & " + outputTableName_full
                    break
                case "gpx":
                    GPXDriverFunction gpxDriver = new GPXDriverFunction()
                    gpxDriver.importFile(connection, outputTableName, new File(pathFile), new EmptyProgressVisitor())
                    outputTableName_full = outputTableName + " & " + outputTableName_full
                    break
                case "bz2":
                    OSMDriverFunction osmDriver = new OSMDriverFunction()
                    osmDriver.importFile(connection, outputTableName, new File(pathFile), new EmptyProgressVisitor())
                    outputTableName_full = outputTableName + " & " + outputTableName_full
                    break
                case "gz":
                    OSMDriverFunction osmDriver = new OSMDriverFunction()
                    osmDriver.importFile(connection, outputTableName, new File(pathFile), new EmptyProgressVisitor())
                    outputTableName_full = outputTableName + " & " + outputTableName_full
                    break
                case "osm":
                    OSMDriverFunction osmDriver = new OSMDriverFunction()
                    osmDriver.importFile(connection, outputTableName, new File(pathFile), new EmptyProgressVisitor())
                    outputTableName_full = outputTableName + " & " + outputTableName_full
                    break
                case "shp":
                    SHPDriverFunction shpDriver = new SHPDriverFunction()
                    shpDriver.importFile(connection, outputTableName, new File(pathFile), new EmptyProgressVisitor())
                    outputTableName_full = outputTableName + " & " + outputTableName_full
                    break
                case "tsv":
                    TSVDriverFunction tsvDriver = new TSVDriverFunction()
                    tsvDriver.importFile(connection, outputTableName, new File(pathFile), new EmptyProgressVisitor())
                    outputTableName_full = outputTableName + " & " + outputTableName_full
                    break
            }


            ResultSet rs = stmt.executeQuery("SELECT * FROM \"" + outputTableName + "\"")

            int pk2Field = JDBCUtilities.getFieldIndex(rs.getMetaData(), "PK2")
            int pkField = JDBCUtilities.getFieldIndex(rs.getMetaData(), "PK")

            if (pk2Field > 0 && pkField > 0) {
                stmt.execute("ALTER TABLE " + outputTableIdentifier + " DROP COLUMN PK2;")
                logger.warn("The PK2 column automatically created by the SHP driver has been deleted.")
            }

            // Read Geometry Index and type of the table
            List<String> spatialFieldNames = GeometryTableUtilities.getGeometryColumnNames(connection, TableLocation.parse(outputTableName, DBUtils.getDBType(connection)))

            // If the table does not contain a geometry field
            if (spatialFieldNames.isEmpty()) {
                logger.warn("The table " + outputTableName + " does not contain a geometry field.")
            } else {
                stmt.execute('CREATE SPATIAL INDEX IF NOT EXISTS ' + outputTableName + '_INDEX ON ' + outputTableIdentifier + '(the_geom);')
                // Get the SRID of the table
                Integer tableSrid = GeometryTableUtilities.getSRID(connection, outputTableIdentifier)

                if (tableSrid != 0 && tableSrid != srid && input['inputSRID']) {
                    resultString = "The table " + outputTableName + " already has a different SRID than the one you gave."
                    throw new Exception('ERROR : ' + resultString)
                }

                // Replace default SRID by the srid of the table
                if (tableSrid != 0) srid = tableSrid


                // If the table does not have an associated SRID, add a SRID
                if (tableSrid == 0) {
                    Statement st = connection.createStatement()
                    try {
                        st.execute(String.format(Locale.ROOT,
                                "SELECT UpdateGeometrySRID('%s', '%s', %d);",
                                outputTableIdentifier, spatialFieldNames.get(0), srid))
                    } catch (SQLException ex) {
                        logger.error("Failed to convert the SRID of the file:\n{}", pathFile, ex)
                    }
                }

                // Display the actual SRID in the command window
                logger.info("The SRID of the table " + outputTableName + " is " + srid + " was " + tableSrid)
            }

            // If the table has a PK column and doesn't have any Primary Key Constraint, then automatically associate a Primary Key
            ResultSet rs2 = stmt.executeQuery("SELECT * FROM " + outputTableIdentifier)
            int pkUserIndex = JDBCUtilities.getFieldIndex(rs2.getMetaData(), "PK")
            int pkIndex = JDBCUtilities.getIntegerPrimaryKey(connection, outputTableIdentifier)

            if (pkIndex == 0) {
                if (pkUserIndex > 0) {
                    stmt.execute("ALTER TABLE " + outputTableIdentifier + " ALTER COLUMN PK INT NOT NULL;")
                    stmt.execute("ALTER TABLE " + outputTableIdentifier + " ADD PRIMARY KEY (PK);  ")
                    resultString = resultString + String.format(outputTableIdentifier.toString() + " has a new primary key constraint on PK")
                    logger.info(String.format(outputTableIdentifier.toString() + " has a new primary key constraint on PK"))
                }
            }

        }
    }


    resultString = "The table(s) " + outputTableName_full + " has/have been uploaded to database !"

    // print to command window
    logger.info(resultString)
    logger.info('End : Import all files of a folder')

    // print to WPS Builder
    return resultString

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