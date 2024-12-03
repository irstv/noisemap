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
import groovy.sql.GroovyRowResult
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Geometry
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.*
import groovy.sql.Sql

title = 'Chose Closest Receivers For Matsim Activities'
description = 'Chose the closest receiver in a RECEIVERS table for every Mastim Activity in an ACTIVITIES table'

inputs = [
        activitiesTable : [
                name: 'Name of the table containing the activities',
                title: 'Name of the table containing the activities',
                description: 'Name of the table containing the activities' +
                        '<br/>The table must contain the following fields : ' +
                        '<br/>PK, FACILITY, THE_GEOM, TYPES',
                type: String.class
        ],
        receiversTable : [
                name: 'Name of the table containing the receivers',
                title: 'Name of the table containing the receivers',
                description: 'Name of the table containing the receivers' +
                        '<br/>The table must contain the following fields : ' +
                        '<br/>PK, THE_GEOM',
                type: String.class
        ],
        outTableName: [
                name: 'Output table name',
                title: 'Name of created table',
                description: 'Name of the table you want to create' +
                        '<br/>The table will contain the following fields : ' +
                        '<br/>PK, FACILITY, ORIGIN_GEOM, THE_GEOM, TYPES',
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
    logger.info('Start : Receivers_From_Activities_Closest')
    logger.info("inputs {}", input)

    String outTableName = input['outTableName']
    String activitiesTable = input['activitiesTable']
    String receiversTable = input['receiversTable']
    int dist = 50
    if (input['maxDistance']) {
        dist = input['maxDistance'] as Integer;
    }

    DatabaseMetaData metadata = connection.getMetaData();

    boolean geomIndexFound = false;
    ResultSet resultSet = metadata.getIndexInfo(null, null, receiversTable, false, false);
    while (resultSet.next()) {
        String name = resultSet.getString("COLUMN_NAME");
        if (name == "THE_GEOM") {
            geomIndexFound = true;
        }
    }
    if (!geomIndexFound) {
        logger.info("THE_GEOM index missing from receivers table, creating one ...")
        sql.execute("CREATE SPATIAL INDEX ON " + receiversTable + " (THE_GEOM)");
    }

    geomIndexFound = false;
    resultSet = metadata.getIndexInfo(null, null, activitiesTable, false, false);
    while (resultSet.next()) {
        String name = resultSet.getString("COLUMN_NAME");
        if (name == "THE_GEOM") {
            geomIndexFound = true;
        }
    }
    if (!geomIndexFound) {
        logger.info("THE_GEOM index missing from activities table, creating one ...")
        sql.execute("CREATE SPATIAL INDEX ON " + activitiesTable + " (THE_GEOM)");
    }

    logger.info("Checking indexes done, running script ...")

    sql.execute(String.format("DROP TABLE IF EXISTS %s", outTableName))

    String create_query = "CREATE TABLE " + outTableName + '''( 
        PK integer PRIMARY KEY AUTO_INCREMENT,
        FACILITY varchar(255),
        THE_GEOM geometry,
        ORIGIN_GEOM geometry,
        TYPES varchar(255)
    )'''
    sql.execute(create_query)

    PreparedStatement insert_stmt = connection.prepareStatement(
            "INSERT INTO " + outTableName + " VALUES(DEFAULT, ?, ST_GeomFromText(?, ?), ST_GeomFromText(?, ?), ?)"
    )
    List<GroovyRowResult> activities_res = sql.rows("SELECT A.PK, A.FACILITY, A.THE_GEOM AS ORIGIN_GEOM, ST_SRID(A.THE_GEOM) as SRID, A.TYPES FROM " + activitiesTable + " A");
    long nb_activities = activities_res.size()
    long count = 0, do_print = 1
    int srid = 0
    long start = System.currentTimeMillis();
    for (GroovyRowResult activity: activities_res) {
        Geometry activityGeom = activity["ORIGIN_GEOM"] as Geometry;
        String facility = activity["FACILITY"] as String;
        String types = activity["TYPES"] as String;
        srid = activity["SRID"] as Integer
        List<GroovyRowResult> receiver_res = sql.rows(String.format('''
            SELECT R.THE_GEOM 
            FROM %s R
            WHERE ST_EXPAND(ST_GeomFromText('%s', %s), %s, %s) && R.THE_GEOM
            ORDER BY ST_Distance(ST_GeomFromText('%s', %s), R.THE_GEOM) ASC LIMIT 1
        ''', receiversTable, activityGeom.toText(), srid, dist, dist, activityGeom.toText(), srid));
        Geometry receiverGeom = (receiver_res.size() == 0) ? activityGeom : (receiver_res.get(0)["THE_GEOM"] as Geometry);
        insert_stmt.setString(1, facility)
        insert_stmt.setString(2, receiverGeom.toText())
        insert_stmt.setInt(3, srid)
        insert_stmt.setString(4, activityGeom.toText())
        insert_stmt.setInt(5, srid)
        insert_stmt.setString(6, types)
        insert_stmt.execute()

        if (count >= do_print) {
            double elapsed = (System.currentTimeMillis() - start + 1) / 1000
            logger.info(String.format("Processing Activity %d (max:%d) - elapsed : %ss (%.1fit/s)",
                    count, nb_activities, elapsed, count/elapsed))
            do_print *= 2
        }
        count ++
    }

    logger.info("Creating index on " + outTableName + "(FACILITY)");
    sql.execute("CREATE INDEX ON " + outTableName + "(FACILITY)");
    logger.info("Creating spatial index on " + outTableName + "(THE_GEOM)");
    sql.execute("CREATE SPATIAL INDEX ON " + outTableName + "(THE_GEOM)");

    sql.execute("UPDATE " + outTableName + " SET THE_GEOM = ST_UpdateZ(THE_GEOM, 4.0), ORIGIN_GEOM  = ST_UpdateZ(ORIGIN_GEOM, 4.0)")
    
    logger.info('End : Receivers_From_Activities_Closest')
    resultString = "Process done. Table of receivers " + outTableName + " created !"
    logger.info('Result : ' + resultString)
    return resultString;
}