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

package org.noise_planet.noisemodelling.wps

import groovy.sql.Sql
import org.h2.value.ValueBoolean
import org.h2gis.functions.io.dbf.DBFRead
import org.h2gis.functions.io.shp.SHPRead
import org.h2gis.utilities.JDBCUtilities
import org.junit.Test
import org.noise_planet.noisemodelling.wps.Geometric_Tools.Set_Height
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_File
import org.noise_planet.noisemodelling.wps.Import_and_Export.Export_Table
import org.noise_planet.noisemodelling.wps.NoiseModelling.Noise_level_from_source
import org.noise_planet.noisemodelling.wps.NoiseModelling.Noise_level_from_traffic
import org.noise_planet.noisemodelling.wps.NoiseModelling.Railway_Emission_from_Traffic
import org.noise_planet.noisemodelling.wps.NoiseModelling.Road_Emission_from_Traffic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseModelling extends JdbcTestCase {
    Logger LOGGER = LoggerFactory.getLogger(TestNoiseModelling.class)


    void testRoadEmissionFromDEN() {

        SHPRead.importTable(connection, TestDatabaseManager.getResource("ROADS2.shp").getPath())

        String res = new Road_Emission_from_Traffic().exec(connection,
                ["tableRoads": "ROADS2"])


        assertEquals("Calculation Done ! The table LW_ROADS has been created.", res)
    }

    void testRailWayEmissionFromDEN() {

        def sql = new Sql(connection)

        sql.execute("DROP TABLE IF EXISTS LW_RAILWAY")


        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("Train/RAIL_SECTIONS.shp").getPath(),
                 "inputSRID": "2154"])

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("Train/RAIL_TRAFFIC.dbf").getPath(),
                 "inputSRID": "2154"])

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("Train/receivers_Railway_.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName" : "RECEIVERS"])

        new Railway_Emission_from_Traffic().exec(connection,
                ["tableRailwayTraffic": "RAIL_TRAFFIC",
                 "tableRailwayTrack": "RAIL_SECTIONS"
                ])

        def fieldNames = JDBCUtilities.getColumnNames(connection, "LW_RAILWAY")

        def expected = ["PK_SECTION","THE_GEOM","DIR_ID","GS","LWD50","LWD63","LWD80","LWD100","LWD125",
                        "LWD160","LWD200","LWD250","LWD315","LWD400","LWD500","LWD630","LWD800","LWD1000","LWD1250",
                        "LWD1600","LWD2000","LWD2500","LWD3150","LWD4000","LWD5000","LWD6300","LWD8000","LWD10000",
                        "LWE50","LWE63","LWE80","LWE100","LWE125","LWE160","LWE200","LWE250","LWE315","LWE400",
                        "LWE500","LWE630","LWE800","LWE1000","LWE1250","LWE1600","LWE2000","LWE2500","LWE3150",
                        "LWE4000","LWE5000","LWE6300","LWE8000","LWE10000","LWN50","LWN63","LWN80","LWN100","LWN125",
                        "LWN160","LWN200","LWN250","LWN315","LWN400","LWN500","LWN630","LWN800","LWN1000","LWN1250",
                        "LWN1600","LWN2000","LWN2500","LWN3150","LWN4000","LWN5000","LWN6300","LWN8000","LWN10000","PK"]

        //assertArrayEquals(expected.toArray(new String[expected.size()]), fieldNames.toArray(new String[fieldNames.size()]))


        SHPRead.importTable(connection, TestDatabaseManager.getResource("Train/buildings2.shp").getPath(),
                "BUILDINGS", ValueBoolean.TRUE)

        sql.execute("DROP TABLE IF EXISTS LDAY_GEOM")

        new Noise_level_from_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "LW_RAILWAY",
                 "tableReceivers": "RECEIVERS",
                 "confSkipLevening": false,
                 "confSkipLnight": false,
                 "confSkipLden": false])

        //assertTrue(JDBCUtilities.tableExists(connection, "LDAY_GEOM"))

        def receiversLvl = sql.rows("SELECT * FROM LDAY_GEOM ORDER BY IDRECEIVER")

        new Export_Table().exec(connection,
                ["exportPath"   : "target/LDAY_GEOM_rail.geojson",
                 "tableToExport": "LDAY_GEOM"])

        //assertEquals(70.38,receiversLvl[0]["LEQ"] as Double,4)
    }

    void testLdayFromTraffic() {

        SHPRead.importTable(connection, TestNoiseModelling.getResource("ROADS2.shp").getPath())

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("buildings.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "buildings"])

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("receivers.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "receivers"])


       String res = new Noise_level_from_traffic().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableRoads"   : "ROADS2",
                 "tableReceivers": "RECEIVERS"])

        assertTrue(res.contains("LDAY_GEOM"))

        def sql = new Sql(connection)

        def leqs = sql.firstRow("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000) FROM LDAY_GEOM")

        assertEquals(87, leqs[0] as Double, 2.0)
        assertEquals(78, leqs[1] as Double, 2.0)
        assertEquals(78, leqs[2] as Double, 2.0)
        assertEquals(79, leqs[3] as Double, 2.0)
        assertEquals(82, leqs[4] as Double, 2.0)
        assertEquals(80, leqs[5] as Double, 2.0)
        assertEquals(71, leqs[6] as Double, 2.0)
        assertEquals(62, leqs[7] as Double, 2.0)

        assertTrue(res.contains("LEVENING_GEOM"))

        leqs = sql.firstRow("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000) FROM LEVENING_GEOM")

        assertEquals(81.0, leqs[0] as Double, 2.0)
        assertEquals(74.0, leqs[1] as Double, 2.0)
        assertEquals(73.0, leqs[2] as Double, 2.0)
        assertEquals(75.0, leqs[3] as Double, 2.0)
        assertEquals(77.0, leqs[4] as Double, 2.0)
        assertEquals(75.0, leqs[5] as Double, 2.0)
        assertEquals(66.0, leqs[6] as Double, 2.0)
        assertEquals(57.0, leqs[7] as Double, 2.0)

        assertTrue(res.contains("LNIGHT_GEOM"))

        leqs = sql.firstRow("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000) FROM LNIGHT_GEOM")

        assertEquals(78, leqs[0] as Double, 2.0)
        assertEquals(71, leqs[1] as Double, 2.0)
        assertEquals(70, leqs[2] as Double, 2.0)
        assertEquals(72, leqs[3] as Double, 2.0)
        assertEquals(74, leqs[4] as Double, 2.0)
        assertEquals(72, leqs[5] as Double, 2.0)
        assertEquals(63, leqs[6] as Double, 2.0)
        assertEquals(54, leqs[7] as Double, 2.0)

        assertTrue(res.contains("LDEN_GEOM"))

        leqs = sql.firstRow("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000) FROM LDEN_GEOM")

        assertEquals(87, leqs[0] as Double, 2.0)
        assertEquals(79, leqs[1] as Double, 2.0)
        assertEquals(79, leqs[2] as Double, 2.0)
        assertEquals(80, leqs[3] as Double, 2.0)
        assertEquals(83, leqs[4] as Double, 2.0)
        assertEquals(81, leqs[5] as Double, 2.0)
        assertEquals(72, leqs[6] as Double, 2.0)
        assertEquals(63, leqs[7] as Double, 2.0)
    }


    void testLdayFromTrafficWithBuildingsZ() {

        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestNoiseModelling.getResource("ROADS2.shp").getPath())

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("buildings.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "buildings"])

        new Set_Height().exec(connection,
                ["height": -50,
                 "tableName": "buildings"])

        sql.firstRow("SELECT THE_GEOM FROM buildings")[0]

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("receivers.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "receivers"])


        String res = new Noise_level_from_traffic().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableRoads"   : "ROADS2",
                 "tableReceivers": "RECEIVERS"])

        assertTrue(res.contains("LDAY_GEOM"))



        def leqs = sql.firstRow("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000) FROM LDAY_GEOM")

        assertEquals(87, leqs[0] as Double, 2.0)
        assertEquals(78, leqs[1] as Double, 2.0)
        assertEquals(78, leqs[2] as Double, 2.0)
        assertEquals(79, leqs[3] as Double, 2.0)
        assertEquals(82, leqs[4] as Double, 2.0)
        assertEquals(80, leqs[5] as Double, 2.0)
        assertEquals(71, leqs[6] as Double, 2.0)
        assertEquals(62, leqs[7] as Double, 2.0)

        assertTrue(res.contains("LEVENING_GEOM"))

        leqs = sql.firstRow("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000) FROM LEVENING_GEOM")

        assertEquals(81, leqs[0] as Double, 2.0)
        assertEquals(74, leqs[1] as Double, 2.0)
        assertEquals(73, leqs[2] as Double, 2.0)
        assertEquals(75, leqs[3] as Double, 2.0)
        assertEquals(77, leqs[4] as Double, 2.0)
        assertEquals(75, leqs[5] as Double, 2.0)
        assertEquals(66, leqs[6] as Double, 2.0)
        assertEquals(57, leqs[7] as Double, 2.0)

        assertTrue(res.contains("LNIGHT_GEOM"))

        leqs = sql.firstRow("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000) FROM LNIGHT_GEOM")

        assertEquals(78, leqs[0] as Double, 2.0)
        assertEquals(71, leqs[1] as Double, 2.0)
        assertEquals(70, leqs[2] as Double, 2.0)
        assertEquals(72, leqs[3] as Double, 2.0)
        assertEquals(74, leqs[4] as Double, 2.0)
        assertEquals(72, leqs[5] as Double, 2.0)
        assertEquals(63, leqs[6] as Double, 2.0)
        assertEquals(54, leqs[7] as Double, 2.0)

        assertTrue(res.contains("LDEN_GEOM"))

        leqs = sql.firstRow("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000) FROM LDEN_GEOM")

        assertEquals(87, leqs[0] as Double, 2.0)
        assertEquals(79, leqs[1] as Double, 2.0)
        assertEquals(79, leqs[2] as Double, 2.0)
        assertEquals(80, leqs[3] as Double, 2.0)
        assertEquals(83, leqs[4] as Double, 2.0)
        assertEquals(81, leqs[5] as Double, 2.0)
        assertEquals(72, leqs[6] as Double, 2.0)
        assertEquals(63, leqs[7] as Double, 2.0)
    }


    void testLdenFromEmission() {

        SHPRead.importTable(connection, TestNoiseModelling.getResource("ROADS2.shp").getPath())

        new Road_Emission_from_Traffic().exec(connection,
                ["tableRoads": "ROADS2"])

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("buildings.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "buildings"])

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("receivers.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "receivers"])


        String res = new Noise_level_from_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "LW_ROADS",
                 "tableReceivers": "RECEIVERS"])

        assertTrue(res.contains("LDAY_GEOM"))
        assertTrue(res.contains("LEVENING_GEOM"))
        assertTrue(res.contains("LNIGHT_GEOM"))
        assertTrue(res.contains("LDEN_GEOM"))
    }

    void testLdenFromEmission1khz() {

        SHPRead.importTable(connection, TestNoiseModelling.getResource("ROADS2.shp").getPath())

        new Road_Emission_from_Traffic().exec(connection,
                ["tableRoads": "ROADS2"])

        // select only 1khz band
        Sql sql = new Sql(connection)

        sql.execute("CREATE TABLE LW_ROADS2(pk serial primary key, the_geom geometry, LWD1000 double) as select pk, the_geom, lwd1000 from LW_ROADS")

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("buildings.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "buildings"])

        new Import_File().exec(connection,
                ["pathFile" : TestNoiseModelling.getResource("receivers.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "receivers"])


        String res = new Noise_level_from_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "LW_ROADS2",
                 "tableReceivers": "RECEIVERS",
                "confSkipLevening": true,
                "confSkipLnight": true,
                "confSkipLden": true])

        assertTrue(res.contains("LDAY_GEOM"))

        // fetch columns
        def fields = JDBCUtilities.getColumnNames(connection, "LDAY_GEOM")

        assertArrayEquals(["IDRECEIVER","THE_GEOM", "HZ1000", "LAEQ", "LEQ"].toArray(), fields.toArray())
    }
}
