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
import org.matsim.api.core.v01.Coord
import org.matsim.api.core.v01.Scenario
import org.matsim.core.config.ConfigUtils
import org.matsim.core.scenario.ScenarioUtils
import org.matsim.facilities.ActivityFacilities
import org.matsim.facilities.ActivityFacility
import org.matsim.facilities.MatsimFacilitiesReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.*
import groovy.sql.Sql

title = 'Import Matsim "facilities" file'
description = 'Import Matsim "facilities" file containing agents activities location.'

inputs = [
    facilitiesPath : [
        name: 'Path of the Matsim facilities file',
        title: 'Path of the Matsim facilities file',
        description: 'Path of the Matsim facilities file',
        type: String.class
    ],
    SRID : [
        name: 'Projection identifier',
        title: 'Projection identifier',
        description: 'Original projection identifier (also called SRID) of your table.' +
                'It should be an EPSG code, a integer with 4 or 5 digits (ex: 3857 is Web Mercator projection).' +
                '</br><b> Default value : 4326 </b> ',
        min: 0,
        max: 1,
        type: Integer.class
    ],
    outTableName: [
            name: 'Output table name',
            title: 'Name of created table',
            description: 'Name of the table you want to create' +
                    '<br/>The table will contain the following fields : ' +
                    '<br/>PK, FACILITY, THE_GEOM, TYPES, BUILDING_ID',
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
    logger.info('Start : Import_Activities')
    logger.info("inputs {}", input)

    String facilitiesPath = input['facilitiesPath']
    String outTableName = input['outTableName']

    String SRID = "4326"
    if (input['SRID']) {
        SRID = input['SRID'];
    }

    double height = 4.0;

    //Delete previous receivers
    sql.execute(String.format("DROP TABLE IF EXISTS %s", outTableName))
    sql.execute("CREATE TABLE " + outTableName + '''( 
        PK integer PRIMARY KEY AUTO_INCREMENT,
        FACILITY varchar(255),
        THE_GEOM geometry,
        TYPES varchar(255)
    );''')
    sql.execute("CREATE INDEX ON " + outTableName + "(FACILITY)");
    sql.execute("CREATE SPATIAL INDEX ON " + outTableName + "(THE_GEOM)");

    Scenario scenario = ScenarioUtils.loadScenario(ConfigUtils.createConfig())
    MatsimFacilitiesReader facilitiesReader = new MatsimFacilitiesReader(scenario)
    facilitiesReader.readFile(facilitiesPath);

    ActivityFacilities facilities = scenario.getActivityFacilities();

    for (def entry : facilities.getFacilities().entrySet()) {
        String facilityId = entry.getKey().toString();
        ActivityFacility facility = entry.getValue();
        Coord c = facility.getCoord();
        String geom = String.format(Locale.ROOT, "SRID=%s;POINTZ(%f %f %f)", SRID, c.getX(),c.getY(), height);
        String types = facility.getActivityOptions().keySet().join(',');
        String query = "INSERT INTO " + outTableName + "(FACILITY, THE_GEOM, TYPES) VALUES( '" + facilityId + "', '" + geom + "', '" + types + "')";
        sql.execute(query);
    }

    logger.info('End : Import_Activities')
    resultString = "Process done. Table of receivers " + outTableName + " created !"
    logger.info('Result : ' + resultString)
    return resultString
}