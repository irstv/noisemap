/**
 * @Author Pierre Aumond, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.wps.Experimental

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.SpatialResultSet
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Geometry
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossos
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossosParameters
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException

title = 'Compute Road Emission'
description = '&#10145;&#65039; Compute Road Emission Noise Map from Estimated Annual average daily flows (TMJA) estimates. '

inputs = [databaseName    : [name: 'Name of the database', title: 'Name of the database', description: 'Name of the database (default : first found db)', min: 0, max: 1, type: String.class],
          sourcesTableName: [name: 'Sources table name', title: 'Sources table name', type: String.class]]

outputs = [result: [name: 'result', title: 'Result', type: String.class]]


static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}


def run(input) {

    String output = null
    // -------------------
    // Get inputs
    // -------------------
    String sources_table_name = "SOURCES"
    if (input['sourcesTableName']) {
        sources_table_name = input['sourcesTableName']
    }
    sources_table_name = sources_table_name.toUpperCase()

    // Get name of the database
    String dbName = ""
    if (input['databaseName']) {
        dbName = input['databaseName'] as String
    }

    // ----------------------------------
    // Start... 
    // ----------------------------------

    System.out.println("Run ...")

    // Open connection
    openGeoserverDataStoreConnection(dbName).withCloseable { Connection connection ->

        //Need to change the ConnectionWrapper to WpsConnectionWrapper to work under postgis database
        connection = new ConnectionWrapper(connection)
        System.out.println("Connection to the database ok ...")

        //Get the geometry field of the source table
        TableLocation sourceTableIdentifier = TableLocation.parse(sources_table_name)
        List<String> geomFields = GeometryTableUtilities.getGeometryColumnNames(connection, sourceTableIdentifier)
        if (geomFields.isEmpty()) {
            output = String.format("The table %s does not exists or does not contain a geometry field", sourceTableIdentifier)
            throw new SQLException(String.format("The table %s does not exists or does not contain a geometry field", sourceTableIdentifier));
        }
        String sourceGeomName = geomFields.get(0);

        //Get the primary key field of the source table
        int pkIndex = JDBCUtilities.getIntegerPrimaryKey(connection, TableLocation.parse(sources_table_name))
        if (pkIndex < 1) {
            throw new IllegalArgumentException(String.format("Source table %s does not contain a primary key", sourceTableIdentifier));
        }

        // open sql connection
        Sql sql = new Sql(connection)

        // create empty LW_ROADS
        sql.execute("drop table if exists LW_ROADS;")
        sql.execute("create table LW_ROADS (IDSOURCE integer, the_geom Geometry, " +
                "Ld63 double precision, Ld125 double precision, Ld250 double precision, Ld500 double precision, Ld1000 double precision, Ld2000 double precision, Ld4000 double precision, Ld8000 double precision," +
                "Le63 double precision, Le125 double precision, Le250 double precision, Le500 double precision, Le1000 double precision, Le2000 double precision, Le4000 double precision, Le8000 double precision," +
                "Ln63 double precision, Ln125 double precision, Ln250 double precision, Ln500 double precision, Ln1000 double precision, Ln2000 double precision, Ln4000 double precision, Ln8000 double precision);")

        def qry = 'INSERT INTO LW_ROADS(IDSOURCE,the_geom, ' +
                'Ld63, Ld125, Ld250, Ld500, Ld1000,Ld2000, Ld4000, Ld8000,' +
                'Le63, Le125, Le250, Le500, Le1000,Le2000, Le4000, Le8000,' +
                'Ln63, Ln125, Ln250, Ln500, Ln1000,Ln2000, Ln4000, Ln8000) ' +
                'VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);'


        long start = System.currentTimeMillis()
        System.out.println("Start ...")
        // fill the table LW_ROADS
        sql.withBatch(100, qry) { ps ->
            PreparedStatement st = connection.prepareStatement("SELECT * FROM " + sources_table_name)
            SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)
            while (rs.next()) {
                System.println(rs)
                Geometry geo = rs.getGeometry()
                def results = computeLw(rs.getLong(pkIndex), geo, rs)

                ps.addBatch(rs.getLong(pkIndex) as Integer, geo as Geometry,
                        results[0][0] as Double, results[0][1] as Double, results[0][2] as Double,
                        results[0][3] as Double, results[0][4] as Double, results[0][5] as Double,
                        results[0][6] as Double, results[0][7] as Double,
                        results[1][0] as Double, results[1][1] as Double, results[1][2] as Double,
                        results[1][3] as Double, results[1][4] as Double, results[1][5] as Double,
                        results[1][6] as Double, results[1][7] as Double,
                        results[2][0] as Double, results[2][1] as Double, results[2][2] as Double,
                        results[2][3] as Double, results[2][4] as Double, results[2][5] as Double,
                        results[2][6] as Double, results[2][7] as Double)
            }
        }

        sql.execute("UPDATE LW_ROADS SET THE_GEOM = ST_UPDATEZ(The_geom,0.05);")
        sql.execute("ALTER TABLE LW_ROADS ADD pk INT AUTO_INCREMENT PRIMARY KEY;")
        long computationTime = System.currentTimeMillis() - start;
        output = "The Table LW_ROADS have been created"
        return [result: output]


    }

}

static double[][] computeLw(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException {

    def lv_hourly_distribution = [0.56, 0.3, 0.21, 0.26, 0.69, 1.8, 4.29, 7.56, 7.09, 5.5, 4.96, 5.04,
                                  5.8, 6.08, 6.23, 6.67, 7.84, 8.01, 7.12, 5.44, 3.45, 2.26, 1.72, 1.12];
    def hv_hourly_distribution = [1.01, 0.97, 1.06, 1.39, 2.05, 3.18, 4.77, 6.33, 6.72, 7.32, 7.37, 7.4,
                                  6.16, 6.22, 6.84, 6.74, 6.23, 4.88, 3.79, 3.05, 2.36, 1.76, 1.34, 1.07];

    double tmja = rs.getDouble("TMJA")

    double z_ini = rs.getDouble("Z_INI")
    double z_fin = rs.getDouble("Z_FIN")

    double q_vl_d = rs.getDouble("Q_VL_D")
    double q_vl_e = rs.getDouble("Q_VL_E")
    double q_vl_n = rs.getDouble("Q_VL_N")

    double q_pl_d = rs.getDouble("Q_PL_D")
    double q_pl_e = rs.getDouble("Q_PL_E")
    double q_pl_n = rs.getDouble("Q_PL_N")

    double v_vl_d = rs.getDouble("V_VL_D")
    double v_vl_e = rs.getDouble("V_VL_E")
    double v_vl_n = rs.getDouble("V_VL_N")

    double v_pl_d = rs.getDouble("V_PL_D")
    double v_pl_e = rs.getDouble("V_PL_E")
    double v_pl_n = rs.getDouble("V_PL_N")

    // Annual Average Daily Flow (AADF) estimates
    String ROAD_CATEGORY_FIELD_NAME = "IMPORTANCE"

    int LDAY_START_HOUR = 6
    int LDAY_STOP_HOUR = 18
    int LEVENING_STOP_HOUR = 22
    int[] nightHours = [22, 23, 0, 1, 2, 3, 4, 5]
    def HV_PERCENTAGE = 0.2 // ratio of heavy vehicule by default
    int idSource = 0
    idSource = idSource + 1

    /**
     * Vehicles category Table 3 P.31 CNOSSOS_EU_JRC_REFERENCE_REPORT
     * lv : Passenger cars, delivery vans ≤ 3.5 tons, SUVs , MPVs including trailers and caravans
     * mv: Medium heavy vehicles, delivery vans > 3.5 tons,  buses, touring cars, etc. with two axles and twin tyre mounting on rear axle
     * hgv: Heavy duty vehicles, touring cars, buses, with three or more axles
     * wav:  mopeds, tricycles or quads ≤ 50 cc
     * wbv:  motorcycles, tricycles or quads > 50 cc
     * @param lv_speed Average light vehicle speed
     * @param mv_speed Average medium vehicle speed
     * @param hgv_speed Average heavy goods vehicle speed
     * @param wav_speed Average light 2 wheels vehicle speed
     * @param wbv_speed Average heavy 2 wheels vehicle speed
     * @param lvPerHour Average light vehicle per hour
     * @param mvPerHour Average heavy vehicle per hour
     * @param hgvPerHour Average heavy vehicle per hour
     * @param wavPerHour Average heavy vehicle per hour
     * @param wbvPerHour Average heavy vehicle per hour
     * @param FreqParam Studied Frequency
     * @param Temperature Temperature (Celsius)
     * @param roadSurface roadSurface empty default, NL01 FR01 ..
     * @param Ts_stud A limited period Ts (in months) over the year where a average proportion pm of light vehicles are equipped with studded tyres and during .
     * @param Pm_stud Average proportion of vehicles equipped with studded tyres
     * @param Junc_dist Distance to junction
     * @param Junc_type Type of junction ((k = 1 for a crossing with traffic lights ; k = 2 for a roundabout)
     */
    // Compute day average level
    double[] ld = new double[AttenuationCnossosParameters.freq_lvl.size()];
    double[] le = new double[AttenuationCnossosParameters.freq_lvl.size()];
    double[] ln = new double[AttenuationCnossosParameters.freq_lvl.size()];

    double lvPerHour = 0
    double mvPerHour = 0
    double hgvPerHour = 0
    double wavPerHour = 0
    double wbvPerHour = 0
    double Temperature = 20.0d
    String roadSurface = "FR_R2"
    double Ts_stud = 0
    double Pm_stud = 0
    double Junc_dist = 0
    int Junc_type = 0
    double slopePercentage = 0
    double speedLv = 30
    double speedMv = 30
    double speedHgv = 30
    double speedWav = 30
    double speedWbv = 30


    int idFreq = 0

    for (int freq : AttenuationCnossosParameters.freq_lvl) {
        RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(v_vl_d, speedMv, v_pl_d, speedWav,
                speedWbv, q_vl_d, mvPerHour, q_pl_d, wavPerHour, wbvPerHour, freq, Temperature,
                roadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
        ld[idFreq++] += RoadCnossos.evaluate(rsParametersCnossos)
    }

    // Evening
    idFreq = 0

    for (int freq : AttenuationCnossosParameters.freq_lvl) {
        RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(v_vl_e, speedMv, v_pl_e, speedWav,
                speedWbv, q_vl_e, mvPerHour, q_pl_e, wavPerHour, wbvPerHour, freq, Temperature,
                roadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
        le[idFreq++] += RoadCnossos.evaluate(rsParametersCnossos)
    }

    // Night
    idFreq = 0

    for (int freq : AttenuationCnossosParameters.freq_lvl) {
        RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(v_vl_n, speedMv, v_pl_n, speedWav,
                speedWbv, q_vl_n, mvPerHour, q_pl_n, wavPerHour, wbvPerHour, freq, Temperature,
                roadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
        ln[idFreq++] += RoadCnossos.evaluate(rsParametersCnossos)
    }



    return [ld, le, ln]
}
