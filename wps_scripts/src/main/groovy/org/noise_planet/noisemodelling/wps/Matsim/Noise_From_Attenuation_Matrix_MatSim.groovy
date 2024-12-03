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
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.*
import groovy.sql.Sql

title = 'Noise Map From Attenuation Matrix'
description = 'Noise Map From Attenuation Matrix.' +
        '<br/>'

inputs = [
    matsimRoads: [
        name: 'Table name of the MATSIM table containing the roads geometries',
        title: 'Table name of the MATSIM table containing the roads geometries',
        description: 'Table name of the MATSIM table containing the roads geometries' +
                '<br/>The table must contain the following fields : (PK, LINK_ID, THE_GEOM)',
        type: String.class
    ],
    matsimRoadsStats : [
        name: 'Table name of the MATSIM table containing the roads LW stats per timeString',
        title: 'Table name of the MATSIM table containing the roads LW stats per timeString',
        description: 'Table name of the MATSIM table containing the roads LW stats per timeString' +
                '<br/>The table must contain the following fields : ' +
                '<br/>PK, LINK_ID, LW63, LW125, LW250, LW500, LW1000, LW2000, LW4000, LW8000, TIMESTRING',
        type: String.class
    ],
    timeString: [
        name: 'TIMESTRING Field value',
        title: 'TIMESTRING Field value.',
        description: 'TIMESTRING Field value. If defined will only output data for the specified timeString.' +
                '<br/>The timeString can be "D", "E", "N" for DEN analysis, "12_14" for example for hour analysis or "0h15_0h30" for example for 15minutes analysis.',
        min: 0,
        max : 1,
        type: String.class
    ],
    attenuationTable : [
        name: 'Attenuation Matrix Table name',
        title: 'Attenuation Matrix Table name',
        description: 'Attenuation Matrix Table name, Obtained from the Noise_level_from_source script with "confExportSourceId" enabled' +
                '<br/>The table must contain the following fields :' +
                '<br/>IDRECEIVER, IDSOURCE, THE_GEOM, HZ63, HZ125, HZ250, HZ500, HZ1000, HZ2000, HZ4000, HZ8000',
        type: String.class
    ],
    outTableName: [
        name: 'Output table name',
        title: 'Output table name',
        description: 'Output table name' +
                '<br/>The table will contain the following fields :' +
                '<br/>PK, IDRECEIVER, THE_GEOM, HZ63, HZ125, HZ250, HZ500, HZ1000, HZ2000, HZ000, HZ8000, TIMESTRING',
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

    String resultString

    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")
    logger.info('Start : Noise_From_Attenuation_Matrix_MatSim')
    logger.info("inputs {}", input)

    String matsimRoads = input['matsimRoads']
    String matsimRoadsStats = input['matsimRoadsStats']

    String timeString = ""
    if (input["timeString"]) {
        timeString = input["timeString"];
    }

    String attenuationTable = input['attenuationTable']
    String outTableName = input['outTableName']

    DatabaseMetaData dbMeta = connection.getMetaData();
    ResultSet rs = dbMeta.getIndexInfo(null, null, attenuationTable, false, false);

    logger.info("searching indexes on attenuation matrix ... ")
    boolean indexIDSOURCE = false;
    boolean indexGEOM = false;
    while (rs.next()) {
        String column = rs.getString("COLUMN_NAME");
        String pos = rs.getString("ORDINAL_POSITION");
        if (column == "IDSOURCE" && pos == "1") {
            indexIDSOURCE = true;
            logger.info("index on attenuation matrix IDSOURCE found")
        }
        if (column == "THE_GEOM" && pos == "1") {
            indexGEOM = true;
            logger.info("index on attenuation matrix THE_GEOM found")
        }
    }

    if (!indexIDSOURCE) {
        logger.info("index on attenuation matrix IDSOURCE, NOT found, creating one...")
        sql.execute("CREATE INDEX ON " + attenuationTable + " (IDSOURCE)");
    }
    if (!indexGEOM) {
        logger.info("index on attenuation matrix THE_GEOM, NOT found, creating one...")
        sql.execute("CREATE SPATIAL INDEX ON " + attenuationTable + " (THE_GEOM)");
    }

    sql.execute(String.format("DROP TABLE IF EXISTS %s", outTableName))

    String query = "CREATE TABLE " + outTableName + '''( 
            PK integer PRIMARY KEY AUTO_INCREMENT,
            IDRECEIVER integer,
            THE_GEOM geometry,
            HZ63 double precision,
            HZ125 double precision,
            HZ250 double precision,
            HZ500 double precision,
            HZ1000 double precision,
            HZ2000 double precision,
            HZ4000 double precision,
            HZ8000 double precision,
            TIMESTRING varchar
        );
        INSERT INTO RESULT_GEOM(IDRECEIVER , THE_GEOM , HZ63 , HZ125 , HZ250 , HZ500 , HZ1000 , HZ2000 , HZ4000 , HZ8000 , TIMESTRING ) 
        SELECT lg.IDRECEIVER,  lg.THE_GEOM,
            10 * LOG10( SUM(POWER(10,(mrs.LW63 + lg.HZ63) / 10))) AS HZ63,
            10 * LOG10( SUM(POWER(10,(mrs.LW125 + lg.HZ125) / 10))) AS HZ125,
            10 * LOG10( SUM(POWER(10,(mrs.LW250 + lg.HZ250) / 10))) AS HZ250,
            10 * LOG10( SUM(POWER(10,(mrs.LW500 + lg.HZ500) / 10))) AS HZ500,
            10 * LOG10( SUM(POWER(10,(mrs.LW1000 + lg.HZ1000) / 10))) AS HZ1000,
            10 * LOG10( SUM(POWER(10,(mrs.LW2000 + lg.HZ2000) / 10))) AS HZ2000,
            10 * LOG10( SUM(POWER(10,(mrs.LW4000 + lg.HZ4000) / 10))) AS HZ4000,
            10 * LOG10( SUM(POWER(10,(mrs.LW8000 + lg.HZ8000) / 10))) AS HZ8000,
            mrs.TIMESTRING AS TIMESTRING
        FROM ''' + attenuationTable + '''  lg 
        INNER JOIN ''' + matsimRoads + ''' mr ON lg.IDSOURCE = mr.PK
        INNER JOIN ''' + matsimRoadsStats + ''' mrs ON mr.LINK_ID = mrs.LINK_ID
        ''' + ((timeString != "") ? "WHERE mrs.TIMESTRING = \'" + timeString + "\' " : "") + '''
        GROUP BY lg.IDRECEIVER, lg.THE_GEOM, mrs.TIMESTRING;
    '''

    logger.info(query)

    sql.execute(query)

    String prefix = "HZ"
    sql.execute("ALTER TABLE " + outTableName + " ADD COLUMN LEQA float as 10*log10((power(10,(" + prefix + "63-26.2)/10)+power(10,(" + prefix + "125-16.1)/10)+power(10,(" + prefix + "250-8.6)/10)+power(10,(" + prefix + "500-3.2)/10)+power(10,(" + prefix + "1000)/10)+power(10,(" + prefix + "2000+1.2)/10)+power(10,(" + prefix + "4000+1)/10)+power(10,(" + prefix + "8000-1.1)/10)))")
    sql.execute("ALTER TABLE " + outTableName + " ADD COLUMN LEQ float as 10*log10((power(10,(" + prefix + "63)/10)+power(10,(" + prefix + "125)/10)+power(10,(" + prefix + "250)/10)+power(10,(" + prefix + "500)/10)+power(10,(" + prefix + "1000)/10)+power(10,(" + prefix + "2000)/10)+power(10,(" + prefix + "4000)/10)+power(10,(" + prefix + "8000)/10)))")

    logger.info('End : Noise_From_Attenuation_Matrix_MatSim')
    resultString = "Process done. Table of receivers " + outTableName + " created !"
    logger.info('Result : ' + resultString)
    return resultString
}

