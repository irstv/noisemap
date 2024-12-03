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

title = 'Chose a Random Receivers For Matsim Activities'
description = 'Chose the closest building for every Mastim Activity in an ACTIVITIES table, and then chose a random receiver previously generated around this building.'

inputs = [
        activitiesTable : [
                name: 'Name of the table containing the activities',
                title: 'Name of the table containing the activities',
                description: 'Name of the table containing the activities' +
                        '<br/>The table must contain the following fields : ' +
                        '<br/>PK, FACILITY, THE_GEOM, TYPES',
                type: String.class
        ],
        buildingsTable : [
                name: 'Name of the table containing the buildings',
                title: 'Name of the table containing the buildings',
                description: 'Name of the table containing the buildings' +
                        '<br/>The table must contain the following fields : ' +
                        '<br/>PK, THE_GEOM',
                type: String.class
        ],
        receiversTable : [
                name: 'Name of the table containing the receivers',
                title: 'Name of the table containing the receivers',
                description: 'Name of the table containing the receivers' +
                        '<br/>The table must contain the following fields : ' +
                        '<br/>PK, THE_GEOM, BUILD_PK',
                type: String.class
        ],
        randomSeed : [
                name: 'Random seed',
                title: 'Random seed',
                description: 'Random seed, default: 1234',
                min : 0,
                max : 1,
                type: Integer.class
        ],
        outTableName: [
                name: 'Output table name',
                title: 'Name of created table',
                description: 'Name of the table you want to create' +
                        '<br/>The table will contain the following fields : ' +
                        '<br/>PK, FACILITY, ORIGIN_GEOM, THE_GEOM, TYPES, BUILD_PK',
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

    String activitiesTable = input['activitiesTable']
    String buildingsTable = input['buildingsTable']
    String receiversTable = input['receiversTable']
    int randomSeed = 1234
    if (input["randomSeed"]) {
        randomSeed = intput["randomSeed"] as Integer
    }
    Random randGen = new Random(randomSeed);
    String outTableName = input['outTableName']

    DatabaseMetaData metadata = connection.getMetaData();
    boolean fieldFound = false;
    boolean buildingIndexFound = false;
    boolean receiverIndexFound = false;

    ResultSet resultSet = metadata.getColumns(null, null, activitiesTable, null);
    while (resultSet.next()) {
        String name = resultSet.getString("COLUMN_NAME");
        if (name == "BUILDING_ID") {
            fieldFound = true;
        }
    }
    resultSet = metadata.getIndexInfo(null, null, buildingsTable, false, false);
    while (resultSet.next()) {
        String name = resultSet.getString("COLUMN_NAME");
        if (name == "THE_GEOM") {
            buildingIndexFound = true;
        }
    }
    resultSet = metadata.getIndexInfo(null, null, receiversTable, false, false);
    while (resultSet.next()) {
        String name = resultSet.getString("COLUMN_NAME");
        if (name == "BUILD_PK") {
            receiverIndexFound = true;
        }
    }

//    if (!fieldFound) {
//        sql.execute("ALTER TABLE " + activitiesTable + " ADD BUILDING_ID integer default null")
//    }
    if (!buildingIndexFound) {
        logger.info("THE_GEOM index missing from buildings table, creating one ...")
        sql.execute("CREATE SPATIAL INDEX ON " + buildingsTable + " (THE_GEOM)");
    }
    if (!receiverIndexFound) {
        logger.info("BUILD_PK index missing from receivers table, creating one ...")
        sql.execute("CREATE INDEX ON " + receiversTable + " (BUILD_PK)");
    }

    sql.execute(String.format("DROP TABLE IF EXISTS %s", outTableName))

    logger.info("Loading activities...")
    def res = sql.firstRow("SELECT COUNT(PK) as NB_ACTIVITIES FROM " + activitiesTable)
    int NB_ACTIVITIES = res[0];
    logger.info("Found " + NB_ACTIVITIES + " activities")

    int counter = 0;
    int doprint = 1;

    logger.info("Finding closest building and choosing a receiver for every activity ...")

    sql.execute("DROP TABLE IF EXISTS TMP_ACT_BUILD_REC")
    String query = '''CREATE TABLE TMP_ACT_BUILD_REC ( 
        ACTIVITY_PK int,
        BUILDING_PK int,
        RECEIVER_PK int
    )''';
    sql.execute(query)

    sql.eachRow("SELECT * FROM " + activitiesTable, { activity ->
        int activity_id = activity[0]
        Geometry activity_geom = activity[2]

        def buildings = sql.rows("SELECT B.PK FROM " + buildingsTable + " AS B, " + activitiesTable + " AS A " +
                "WHERE ST_EXPAND(A.THE_GEOM, 200, 200) && B.THE_GEOM AND A.PK = " + activity_id +
                "ORDER BY ST_Distance(A.THE_GEOM, B.THE_GEOM) ASC LIMIT 5")
        if (buildings == null) {
            sql.execute("INSERT INTO TMP_ACT_BUILD_REC VALUES("+activity_id+", null, null)")
            counter++
            return
        }
        boolean good_building_found = false;
        int building_index = 0;
        List<GroovyRowResult> receivers = null;
        String building_id;
        while (!good_building_found && building_index < buildings.size()) {
            building_id = buildings[building_index][0]

            receivers = sql.rows("SELECT * FROM " + receiversTable + " WHERE BUILD_PK = " + building_id)
            if (receivers != null && receivers.size() > 0) {
                good_building_found = true
            }
            building_index++
        }
        if (!good_building_found) {
            logger.debug("Could not find a building with receivers around activity n°" + activity_id)
            sql.execute("INSERT INTO TMP_ACT_BUILD_REC VALUES("+activity_id+", "+building_id+", null)")
            counter++
            return
        }
        int random_index = randGen.nextInt(receivers.size());
        int receiver_id = receivers[random_index][0] as int;

        sql.execute("INSERT INTO TMP_ACT_BUILD_REC VALUES("+activity_id+", "+building_id+", "+receiver_id+")")

        if (counter >= doprint) {
            logger.info("Activity # " + counter)
            doprint *= 2
        }
        counter ++
    });

    query = "CREATE TABLE " + outTableName + '''( 
        PK integer PRIMARY KEY AUTO_INCREMENT,
        FACILITY varchar(255),
        THE_GEOM geometry,
        ORIGIN_GEOM geometry,
        TYPES varchar(255)    
    ) AS
    SELECT A.PK, A.FACILITY, R.THE_GEOM AS THE_GEOM, A.THE_GEOM AS ORIGIN_GEOM, A.TYPES
    FROM TMP_ACT_BUILD_REC ABR
    INNER JOIN ''' + activitiesTable + ''' A ON A.PK = ABR.ACTIVITY_PK
    INNER JOIN ''' + receiversTable + ''' R ON R.PK = ABR.RECEIVER_PK''';

    sql.execute(query);
    sql.execute("CREATE INDEX ON " + outTableName + "(FACILITY)");
    sql.execute("CREATE SPATIAL INDEX ON " + outTableName + "(THE_GEOM)");

    sql.execute("UPDATE " + outTableName + " SET THE_GEOM = CASE " + '''
        WHEN THE_GEOM IS NULL 
        THEN ST_UpdateZ(ORIGIN_GEOM, 4.0)
        ELSE THE_GEOM 
        END
    ''')

    sql.execute("DROP TABLE IF EXISTS TMP_ACT_BUILD_REC")

    logger.info('End : Assign_Buildings_To_Activity')
    resultString = "Process done. Table " + outTableName + " created !"
    logger.info('Result : ' + resultString)
    return resultString;
}