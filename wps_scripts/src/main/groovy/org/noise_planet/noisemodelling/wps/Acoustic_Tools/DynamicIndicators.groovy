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

package org.noise_planet.noisemodelling.wps.Acoustic_Tools

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

title = 'Compute dynamic indicators'
description = 'Compute dynamic indicators as L10, L90 </br> The columns of the table should be named HZ63, HZ125,..., HZ8000 with an HZ prefix that can be changed.'

inputs = [
        columnName   : [
                name       : 'Column name',
                title      : 'Column name',
                description: 'Column name on which to perform the calculation. (STRING) </br> For example : LEQA',
                type       : String.class
        ],
        tableName: [
                title      : 'Name of the table',
                name       : 'Name of the table',
                description: 'Name of the table on which to perform the calculation. The table must contain multiple sound level values for a single receiver. (STRING) </br> For example : LDAY_GEOM',
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

    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")

    // print to command window
    logger.info('Start : Add Leq and LAeq column')
    logger.info("inputs {}", input) // log inputs of the run

    // Open connection
    Sql sql = new Sql(connection)

    // -------------------
    // Get inputs
    // -------------------

    // Get name of the prefix
    String columnName = input['columnName'] as String
    // do it case-insensitive
    columnName = columnName.toUpperCase()

    // Get name of the table
    String table = input["tableName"] as String
    // do it case-insensitive
    table = table.toUpperCase()

    sql.execute("DROP TABLE " + table + "_DYN_IND IF EXISTS;")
    sql.execute("CREATE TABLE " + table + "_DYN_IND AS SELECT THE_GEOM, " +
            "ROUND(MEDIAN(" + columnName + "), 1) L50, " +
            "ROUND(percentile_cont(0.9) WITHIN GROUP (ORDER BY " + columnName + "), 1) L10," +
            "ROUND(percentile_cont(0.1) WITHIN GROUP (ORDER BY " + columnName + "), 1) L90 FROM " + table + " GROUP BY THE_GEOM;")

    resultString = "The columns LEQA and LEQ have been added to the table: " + table + "."

    // print to command window
    logger.info('End : Add Dynamic Indicator')

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