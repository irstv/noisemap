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


package org.noise_planet.noisemodelling.wps.NoiseModelling

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.GeometryMetaData
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.SpatialResultSet
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.dbtypes.DBUtils
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.noise_planet.noisemodelling.jdbc.NoiseEmissionMaker
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
/**
 * @Author Pierre Aumond,  Univ Gustave Eiffel
 * @Author Adrien Le Bellec,  Univ Gustave Eiffel
 * @Author Olivier Chiello, Univ Gustave Eiffel
 */

title = 'Compute railway emission noise map from vehicule, traffic table AND section table.'
description = '&#10145;&#65039; Compute Rail Emission Noise Map from Day, Evening and Night traffic flow rate and speed estimates (specific format, see input details). </br>' +
        '<hr>' +
        '&#x2705; The output table is called <b>LW_RAILWAY</b>'

inputs = [
        tableRailwayTraffic: [
                name : 'Railway traffic table name',
                title : 'Railway traffic table name',
                description : '<b>Name of the Rail traffic table.</b>  </br><br>' +
                        'This function recognize the following columns (* mandatory): </br><ul>' +
                        '<li><b>IDTRAFFIC</b>* : A traffic identifier (PRIMARY KEY) (INTEGER) </li>' +
                        '<li><b>IDSECTION</b>* : A section identifier, refering to RAIL_SECTIONS table (INTEGER)</li>' +
                        '<li><b>TRAINTYPE</b>* : Type of vehicle, listed in the <a href="https://github.com/Universite-Gustave-Eiffel/NoiseModelling/blob/4.X/noisemodelling-emission/src/main/resources/org/noise_planet/noisemodelling/emission/Rail_Train_SNCF_2021.json" target="_blank">Rail_Train_SNCF_2021</a> file (mainly for french SNCF) (STRING)</li>' +
                        '<li><b>TRAINSPD</b>* : Maximum Train speed (in km/h) (DOUBLE)</li>' +
                        '<li><b>TDAY</b>, <b>TEVENING</b> and <b>TNIGHT</b> : Hourly average train count (6-18h)(18-22h)(22-6h) (INTEGER)</li></ul>',
                type: String.class
        ],
        tableRailwayTrack  : [
                name : 'RailWay Geom table name',
                title : 'RailWay Track table name',
                description : '<b>Name of the Railway Track table.</b> </br><br>' +
                        'This function recognize the following columns (* mandatory): </br><ul>' +
                        '<li><b>IDSECTION</b>* : A section identifier (PRIMARY KEY) (INTEGER)</li>' +
                        '<li><b>NTRACK</b>* : Number of tracks (INTEGER)</li>' +
                        '<li><b>TRACKSPD</b>* : Maximum speed on the section (in km/h) (DOUBLE)</li>' +
                        '<li><b>TRANSFER</b> : Track transfer function identifier (INTEGER)</li>' +
                        '<li><b>ROUGHNESS</b> : Rail roughness identifier (INTEGER)</li>' +
                        '<li><b>IMPACT</b> : Impact noise coefficient identifier (INTEGER)</li>' +
                        '<li><b>CURVATURE</b> : Listed code describing the curvature of the section (INTEGER)</li>' +
                        '<li><b>BRIDGE</b> : Bridge transfer function identifier (INTEGER)</li>' +
                        '<li><b>TRACKSPD</b> : Commercial speed on the section (in km/h) (DOUBLE)</li>' +
                        '<li><b>ISTUNNEL</b> : Indicates whether the section is a tunnel or not (0 = no / 1 = yes) (BOOLEAN) </li></ul>',
                type: String.class
        ]
]

outputs = [result: [name: 'Result output string',
                    title: 'Result output string',
                    description: 'This type of result does not allow the blocks to be linked together.',
                    type: String.class
]
]

// Open Connection to Geoserver
static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

// run the script
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

// main function of the script
def exec(Connection connection, input) {
    Logger LOGGER = LoggerFactory.getLogger("Railway_Emission_from_Traffic")

    //Need to change the ConnectionWrapper to WpsConnectionWrapper to work under postGIS database
    connection = new ConnectionWrapper(connection)

    // print to command window
    LOGGER.info('Start : Railway Emission from DEN')

    // -------------------
    // Get every inputs
    // -------------------

    String sources_geom_table_name = input['tableRailwayTrack'] as String
    // do it case-insensitive
    sources_geom_table_name = sources_geom_table_name.toUpperCase()

    int sridSources = GeometryTableUtilities.getSRID(connection, TableLocation.parse(sources_geom_table_name))
    if (sridSources == 3785 || sridSources == 4326) throw new IllegalArgumentException("Error : Please use a metric projection for "+sources_geom_table_name+".")
    if (sridSources == 0) throw new IllegalArgumentException("Error : The table "+sources_geom_table_name+" does not have an associated spatial reference system. (missing prj file on import ?)")

    String sources_table_traffic_name = input['tableRailwayTraffic'] as String
    // do it case-insensitive
    sources_table_traffic_name = sources_table_traffic_name.toUpperCase()

    //Get the geometry field of the source table
    TableLocation sourceTableIdentifier = TableLocation.parse(sources_geom_table_name)
    List<String> geomFields = GeometryTableUtilities.getGeometryColumnNames(connection, sourceTableIdentifier)
    if (geomFields.isEmpty()) {
        throw new SQLException(String.format("The table %s does not exists or does not contain a geometry field", sourceTableIdentifier))
    }


    // -------------------
    // Init table LW_RAIL
    // -------------------


    // Create a sql connection to interact with the database in SQL
    Sql sql = new Sql(connection)

    // Get size of the table (number of rail segments
    PreparedStatement st = connection.prepareStatement("SELECT COUNT(*) AS total FROM " + sources_geom_table_name)
    SpatialResultSet rs1 = st.executeQuery().unwrap(SpatialResultSet.class)

    while (rs1.next()) {
        nSection = rs1.getInt("total")
        System.println('The table Rail Geom has ' + nSection + ' rail segments.')
    }

    NoiseEmissionMaker.makeTrainLWTable(connection, sources_geom_table_name, sources_table_traffic_name,
            "LW_RAILWAY")

    TableLocation alterTable = TableLocation.parse("LW_RAILWAY", DBUtils.getDBType(connection))
    GeometryMetaData metaData = GeometryTableUtilities.getMetaData(connection, alterTable, "THE_GEOM");
    metaData.setSRID(sridSources);
    sql.execute(String.format("ALTER TABLE %s ALTER COLUMN %s %s USING ST_SetSRID(%s,%d)", alterTable, "THE_GEOM",
            metaData.getSQL(), "THE_GEOM" , metaData.getSRID()))

    resultString = "Calculation Done ! The table LW_RAILWAY has been created."

    // print to command window
    System.out.println('\nResult : ' + resultString)
    System.out.println('End : LW_RAILWAY from Emission')
    // System.out.println('Duration : ' + TimeCategory.minus(new Date(), start))

    // print to WPS Builder
    return resultString

}


