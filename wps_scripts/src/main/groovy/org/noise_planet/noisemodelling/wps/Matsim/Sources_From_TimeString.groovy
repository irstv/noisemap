/**
 * NoiseModelling is an open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by Université Gustave Eiffel and CNRS
 * <http://noise-planet.org/noisemodelling.html>
 *
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 *
 * Contact: contact@noise-planet.org
 *
 */
/**
 * @Author Valentin Le Bescond, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.wps.Matsim

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

title = 'Create a SOURCE table from imported MATSim tables'
description = 'Create a ROADS table from imported MATSim tables, for a specific timeBin' +
        '<br/>The timeBin can be 0, 900, 1800, etc. for example for 15minutes analysis.' +
        '<br/>The resulting table will contain the following fields :' +
        '<br/><br/> - '

inputs = [
    roadsTableName: [
        name: 'Table name of the MATSIM table containing the roads geometries',
        title: 'Table name of the MATSIM table containing the roads geometries',
        description: 'Table name of the MATSIM table containing the roads geometries' +
                '<br/>The table must contain the following fields : (PK, LINK_ID, THE_GEOM)',
        type: String.class
    ],
    lwTableName: [
        name: 'Table name of the MATSIM table containing the roads LW stats per timeBin',
        title: 'Table name of the MATSIM table containing the roads LW stats per timeBin',
        description: 'Table name of the MATSIM table containing the roads LW stats per timeBin' +
                '<br/>The table must contain the following fields : ' +
                '<br/>(PK, LINK_ID, LW63, LW125, LW250, LW500, LW1000, LW2000, LW4000, LW8000, TIME)' +
                '<br/>default : roadsTableName + "_LW"',
        min: 0,
        max: 1,
        type: String.class
    ],
    timeBin: [
        name: 'TIME Field value',
        title: 'TIME Field value',
        description: 'TIME Field value' +
            '<br/>The timeBin is an integer representing the starting time of the desired timebin in seconds, it should exist in the traffic data tables',
        type: Integer.class
    ],
    outTableName: [
        name: 'Output table name',
        title: 'Output table name',
        description: 'Output table name' +
                '<br/>The table will contain the following fields :' +
                '<br/>(PK, THE_GEOM, LW63, LW125, LW250, LW500, LW1000, LW2000, LW4000, LW8000)',
        type: String.class
    ]
]

outputs = [
    result: [
        name: 'Result output string',
        title: 'Result output string',
        description: 'This type of result does not allow the blocks to be linked together.',
        type: String.class
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
    String dbName = "h2gisdb"

    // Open connection
    openGeoserverDataStoreConnection(dbName).withCloseable {
        Connection connection ->
            return [result: exec(connection, input)]
    }
}

// main function of the script
def exec(Connection connection, input) {

    connection = new ConnectionWrapper(connection)

    Sql sql = new Sql(connection)

    String resultString = null

    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")
    logger.info('Start : Create_Sources_Matsim_From_TimeString')
    logger.info("inputs {}", input)

    String roadsTableName = input["roadsTableName"] as String;
    String statsTableName =  roadsTableName + "_STATS";
    if (input["statsTableName"]) {
        statsTableName = input["statsTableName"] as String;
    }
    String timeString = input["timeString"] as String;
    String outTableName = input["outTableName"] as String;

    sql.execute("DROP TABLE IF EXISTS " + outTableName)
    sql.execute("CREATE TABLE " + outTableName + '''( 
        PK integer PRIMARY KEY, 
        THE_GEOM geometry,
        LW63 double precision, LW125 double precision, LW250 double precision, LW500 double precision, LW1000 double precision, LW2000 double precision, LW4000 double precision, LW8000 double precision,
    );''')

    sql.execute("MERGE INTO " + outTableName + '''
        SELECT R.PK PK, R.THE_GEOM THE_GEOM,
        S.LW63, S.LW125, S.LW250, S.LW500, S.LW1000, S.LW2000, S.LW4000, S.LW8000
        FROM ''' + roadsTableName + ''' R, ''' + statsTableName + ''' S
        WHERE R.LINK_ID = S.LINK_ID AND S.TIMESTRING = \'''' + timeString + '''\';
    ''')

    logger.info('End : Create_Sources_Matsim_From_TimeString')
    resultString = outTableName + " created.";
    logger.info('Result : ' + resultString)
    return resultString;
}
