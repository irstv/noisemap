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
 * @Author Pierre Aumond, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.wps.Dynamic

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
        lwTable : [
                name: 'LW(t)',
                title: 'LW(t)',
                description: 'LW(t)' +
                        '<br/>The table must contain the following fields :' +
                        '<br/>PK, THE_GEOM, HZ63, HZ125, HZ250, HZ500, HZ1000, HZ2000, HZ4000, HZ8000, T' +
                        '<br/> with PK, the IDSOURCE and T a timestring',
                type: String.class
        ],
        lwTable_sourceId: [
                name: 'LW(t) sourceId',
                title: 'LW(t) sourceId',
                description: 'LW(t)tring',
                min        : 0,
                max        : 1,
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
        outputTable : [
                name: 'outputTable Matrix Table name',
                title: 'outputTable Matrix Table name',
                description: 'outputTable',
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
    logger.info('Start : Noise_From_Attenuation_Matrix')
    logger.info("inputs {}", input)



    String lwTable_sourceId = "PK"
    if (input['lwTable_sourceId']) {
        lwTable_sourceId = input['lwTable_sourceId']
    }


    String outputTable = input['outputTable'].toString().toUpperCase()
    String attenuationTable = input['attenuationTable'].toString().toUpperCase()
    String lwTable = input['lwTable'].toString().toUpperCase()
    String timeString = "IT"

    DatabaseMetaData dbMeta = connection.getMetaData();
    ResultSet rs = dbMeta.getIndexInfo(null, null, lwTable, false, false);

    logger.info("searching indexes on Source Table ... ")
    boolean indexIDSOURCE = false;
    boolean indexGEOM = false;
    while (rs.next()) {
        String column = rs.getString("COLUMN_NAME");
        String pos = rs.getString("ORDINAL_POSITION");

        if (column == "PK" && pos == "1") {
            indexIDSOURCE = true;
            logger.info("index on Source Table -  PK found")
        }
        if (column == "THE_GEOM" && pos == "1") {
            indexGEOM = true;
            logger.info("index on Source Table - THE_GEOM found")
        }
    }

    if (!indexIDSOURCE) {
        logger.info("index on Source Table "+lwTable_sourceId+", NOT found, creating one...")
        sql.execute("CREATE INDEX ON " + lwTable + " ("+lwTable_sourceId+")");
    }
    if (!indexGEOM) {
        logger.info("index on Source Table THE_GEOM, NOT found, creating one...")
        sql.execute("CREATE SPATIAL INDEX ON " + lwTable + " (THE_GEOM)");
    }

    rs = dbMeta.getIndexInfo(null, null, attenuationTable, false, false);

    logger.info("searching indexes on Attenuation Matrix ... ")
    indexIDSOURCE = false;
    boolean indexIDRECEIVER = false;
    while (rs.next()) {
        String column = rs.getString("COLUMN_NAME");
        String pos = rs.getString("ORDINAL_POSITION");

        if (column == "IDSOURCE" && pos == "1") {
            indexIDSOURCE = true;
            logger.info("index on Attenuation Matrix -  IDSOURCE found")
        }
        if (column == "IDRECEIVER" && pos == "1") {
            indexIDRECEIVER = true;
            logger.info("index on Attenuation Matrix - IDRECEIVER found")
        }
    }

    if (!indexIDSOURCE) {
        logger.info("index on Attenuation Matrix IDSOURCE, NOT found, creating one...")
        sql.execute("CREATE INDEX ON " + attenuationTable + " (IDSOURCE)");
    }
    if (!indexIDRECEIVER) {
        logger.info("index on Attenuation Matrix IDRECEIVER, NOT found, creating one...")
        sql.execute("CREATE SPATIAL INDEX ON " + attenuationTable + " (IDRECEIVER)");
    }

    rs = dbMeta.getIndexInfo(null, null, "RECEIVERS", false, false);

    logger.info("searching indexes on RECEIVERS ... ")
    indexIDRECEIVER = false;
    while (rs.next()) {
        String column = rs.getString("COLUMN_NAME");
        String pos = rs.getString("ORDINAL_POSITION");

        if (column == "PK" && pos == "1") {
            indexIDRECEIVER = true;
            logger.info("index on RECEIVERS - PK found")
        }
    }

    if (!indexIDRECEIVER) {
        logger.info("index on RECEIVERS PK, NOT found, creating one...")
        sql.execute("CREATE SPATIAL INDEX ON RECEIVERS(PK)");
    }

    String query2 = '''CREATE TABLE step1 AS SELECT lg.IDRECEIVER,
            10 * LOG10( SUM(POWER(10,(mr.HZ63 + lg.HZ63) / 10))) AS HZ63,
            10 * LOG10( SUM(POWER(10,(mr.HZ125 + lg.HZ125) / 10))) AS HZ125,
            10 * LOG10( SUM(POWER(10,(mr.HZ250 + lg.HZ250) / 10))) AS HZ250,
            10 * LOG10( SUM(POWER(10,(mr.HZ500 + lg.HZ500) / 10))) AS HZ500,
            10 * LOG10( SUM(POWER(10,(mr.HZ1000 + lg.HZ1000) / 10))) AS HZ1000,
            10 * LOG10( SUM(POWER(10,(mr.HZ2000 + lg.HZ2000) / 10))) AS HZ2000,
            10 * LOG10( SUM(POWER(10,(mr.HZ4000 + lg.HZ4000) / 10))) AS HZ4000,
            10 * LOG10( SUM(POWER(10,(mr.HZ8000 + lg.HZ8000) / 10))) AS HZ8000,
            mr.T AS TIMESTRING
        FROM ''' + attenuationTable + '''  lg , ''' + lwTable + ''' mr WHERE lg.IDSOURCE = mr.'''+lwTable_sourceId+''' 
        GROUP BY lg.IDRECEIVER, mr.T;


CREATE TABLE ''' + outputTable + ''' AS SELECT 
    a.IDRECEIVER, r.the_geom,
    a.HZ63, a.HZ125, a.HZ250, a.HZ500, a.HZ1000, a.HZ2000, a.HZ4000, a.HZ8000,
    a.TIMESTRING
FROM step1 a, RECEIVERS r WHERE a.IDRECEIVER = r.PK;

DROP TABLE IF EXISTS step1;
    '''




   long start = System.currentTimeMillis();

   /* int timeBinSize = 3600
    if (input["timeBinSize"]) {
        timeBinSize = input["timeBinSize"] as int;
    }


    sql.execute(String.format("DROP TABLE %s IF EXISTS", outputTable))
    String query3 = "CREATE TABLE " + outputTable + '''(
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
                        TIME int
                    )
                '''
    sql.execute(query3)
    PreparedStatement insert_stmt = connection.prepareStatement(
            "INSERT INTO " + outputTable + " VALUES(DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    )

    logger.info("searching indexes on attenuation matrix ... ")
    ensureIndex(connection, attenuationTable, "IDSOURCE", false)
    ensureIndex(connection, attenuationTable, "IDRECEIVER", false)
    logger.info("searching indexes on traffic tables ... ")
    ensureIndex(connection, "LW_DYNAMIC_GEOM", "PK", false)

    List<String> mrs_freqs = ["HZ63", "HZ125", "HZ250", "HZ500", "HZ1000", "HZ2000", "HZ4000", "HZ8000"]

    long count = 0, do_print = 1
    List<GroovyRowResult> receivers_res = sql.rows("SELECT * FROM RECEIVERS;" );
    long nb_receivers = receivers_res.size()

    for (GroovyRowResult receiver: receivers_res) {
        long receiver_id = receiver["PK"] as long;
        Geometry receiver_geom = receiver["THE_GEOM"] as Geometry;
        Map<Integer, List<Double>> levels = new HashMap<Integer, List<Double>>();
        List<GroovyRowResult> sources_att_res = sql.rows(String.format("SELECT lg.* FROM %s lg WHERE lg.IDRECEIVER = %d", attenuationTable, receiver_id));
        long nb_sources = sources_att_res.size();
        if (nb_sources == 0) {
            count++
            continue
        }
        for (GroovyRowResult sources_att: sources_att_res) {
            long source_id = sources_att["IDSOURCE"] as long;
            List<Double> attenuation = [
                    sources_att["HZ63"] as double,
                    sources_att["HZ125"] as double,
                    sources_att["HZ250"] as double,
                    sources_att["HZ500"] as double,
                    sources_att["HZ1000"] as double,
                    sources_att["HZ2000"] as double,
                    sources_att["HZ4000"] as double,
                    sources_att["HZ8000"] as double,
            ];
            List<GroovyRowResult> roads_stats_res = sql.rows(String.format("SELECT * FROM LW_DYNAMIC_GEOM"));
            for (GroovyRowResult roads_stats: roads_stats_res) {
                int timeBin = roads_stats["T"] as int
                if (!levels.containsKey(timeBin)) {
                    levels[timeBin] = [-99.0, -99.0, -99.0, -99.0, -99.0, -99.0, -99.0, -99.0] as List<Double>
                }
                for (i in 0..<8) {
                    double new_level = (roads_stats[mrs_freqs[i]] as double) + attenuation[i];
                    levels[timeBin][i] = 10 * Math.log10( Math.pow(10, levels[timeBin][i] / 10) + Math.pow(10, new_level / 10))
                }
            }
        }

        for (int timeBin = 0; timeBin < 86400; timeBin += timeBinSize) {
            if (!levels.containsKey(timeBin)) {
                levels[timeBin] = [-99.0, -99.0, -99.0, -99.0, -99.0, -99.0, -99.0, -99.0] as List<Double>
            }
            List<Double> ts_levels = levels[timeBin]
            insert_stmt.setLong(1, receiver_id)
            insert_stmt.setString(2, receiver_geom.toText())
            for (i in 0..<8) {
                insert_stmt.setDouble(i+3, ts_levels[i])
            }
            insert_stmt.setInt(11, timeBin)
            insert_stmt.execute()
        }
        if (count >= do_print) {
            double elapsed = (System.currentTimeMillis() - start + 1) / 1000
            logger.info(String.format("Processing Receiver %d (max:%d) - elapsed : %ss (%.1fit/s)",
                    count, nb_receivers, elapsed, count/elapsed))
            do_print *= 2
        }
        count ++
    }
*/

    long stop = System.currentTimeMillis();

    println(stop-start)



     start = System.currentTimeMillis();

    //logger.info(query2)
    sql.execute(query2)
    stop = System.currentTimeMillis();
    println(stop-start)





    String prefix = "HZ"
    sql.execute("ALTER TABLE  "+outputTable+" ADD COLUMN LEQA float as 10*log10((power(10,(" + prefix + "63-26.2)/10)+power(10,(" + prefix + "125-16.1)/10)+power(10,(" + prefix + "250-8.6)/10)+power(10,(" + prefix + "500-3.2)/10)+power(10,(" + prefix + "1000)/10)+power(10,(" + prefix + "2000+1.2)/10)+power(10,(" + prefix + "4000+1)/10)+power(10,(" + prefix + "8000-1.1)/10)))")
    sql.execute("ALTER TABLE "+outputTable+" ADD COLUMN LEQ float as 10*log10((power(10,(" + prefix + "63)/10)+power(10,(" + prefix + "125)/10)+power(10,(" + prefix + "250)/10)+power(10,(" + prefix + "500)/10)+power(10,(" + prefix + "1000)/10)+power(10,(" + prefix + "2000)/10)+power(10,(" + prefix + "4000)/10)+power(10,(" + prefix + "8000)/10)))")

    logger.info('End : Noise_From_Attenuation_Matrix_MatSim')
    resultString = "Process done. Table of receivers LT_GEOM created !"
    logger.info('Result : ' + resultString)
    return resultString
}


static boolean indexExists(Connection connection, String table, String column_name) {
    DatabaseMetaData dbMeta = connection.getMetaData();
    ResultSet rs = dbMeta.getIndexInfo(null, null, table, false, false);
    boolean index_found = false;
    while (rs.next()) {
        String column = rs.getString("COLUMN_NAME");
        String pos = rs.getString("ORDINAL_POSITION");
        if (column == column_name && pos == "1") {
            index_found = true;
        }
    }
    return index_found
}
static void ensureIndex(Connection connection, String table, String column_name, boolean spatial) {
    if (!indexExists(connection, table, column_name)) {
        Sql sql = new Sql(connection)
        sql.execute("CREATE " + (spatial ? "SPATIAL " : "") + "INDEX ON " + table + " (" + column_name + ")");
    }
}
