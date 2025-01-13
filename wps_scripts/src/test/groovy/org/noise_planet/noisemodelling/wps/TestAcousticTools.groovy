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
import org.h2gis.functions.io.shp.SHPRead
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.junit.Test
import org.noise_planet.noisemodelling.wps.Acoustic_Tools.Add_Laeq_Leq_columns
import org.noise_planet.noisemodelling.wps.Acoustic_Tools.Create_Isosurface
import org.noise_planet.noisemodelling.wps.NoiseModelling.Noise_level_from_traffic
import org.noise_planet.noisemodelling.wps.NoiseModelling.Road_Emission_from_Traffic
import org.noise_planet.noisemodelling.wps.Receivers.Delaunay_Grid
import org.slf4j.Logger
import org.slf4j.LoggerFactory


import java.sql.SQLException

/**
 * Test parsing of zip file using H2GIS database
 */
class TestAcousticTools extends JdbcTestCase {
    Logger LOGGER = LoggerFactory.getLogger(TestAcousticTools.class)

    void testAddLeqLaeqColumns1() {

        SHPRead.importTable(connection, TestAcousticTools.getResource("ROADS2.shp").getPath())

        new Road_Emission_from_Traffic().exec(connection,
                ["tableRoads": "ROADS2"])

        String res = new Add_Laeq_Leq_columns().exec(connection,
                ["prefix": "HZ",
                 "tableName": "LW_ROADS"])

        assertEquals("This table does not contain column with this suffix : HZ", res)
    }

    void testAddLeqLaeqColumns2() {

        SHPRead.importTable(connection, TestAcousticTools.getResource("ROADS2.shp").getPath())

        new Road_Emission_from_Traffic().exec(connection,
                ["tableRoads": "ROADS2"])

        String res = new Add_Laeq_Leq_columns().exec(connection,
                ["prefix": "LWD",
                 "tableName": "LW_ROADS"])

        List<String> fields = JDBCUtilities.getColumnNames(connection, "LW_ROADS")

        assertEquals(true, fields.contains("LEQ"))
    }

    void testCreateIsosurface() {
        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        SHPRead.importTable(connection, TestReceivers.getResource("ROADS2.shp").getPath())
        sql.execute("CREATE SPATIAL INDEX ON BUILDINGS(THE_GEOM)")
        sql.execute("CREATE SPATIAL INDEX ON ROADS2(THE_GEOM)")

        new Delaunay_Grid().exec(connection, ["buildingTableName": "BUILDINGS",
                                              "sourcesTableName" : "ROADS2",
                                              "sourceDensification": 0]);


        new Noise_level_from_traffic().exec(connection, [tableBuilding :"BUILDINGS", tableRoads: "ROADS2",
                                                         tableReceivers: "RECEIVERS", confSkipLday: true,
                                                         confSkipLnight: true, confSkipLevening: true,
                                                         confMaxSrcDist:100, confTemperature:20, confHumidity:50,
                                                         confFavorableOccurrences: "0.5, 0.1, 0.1, 0.1, 0.2, 0.5," +
                                                                 " 0.7, 0.8, 0.8, 0.6, 0.5, 0.5, 0.5, 0.5, 0.5, 0.2"])

        new Create_Isosurface().exec(connection, [resultTable : "LDEN_GEOM"])

        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("ROADS2")))
        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("LDEN_GEOM")))
        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("CONTOURING_NOISE_MAP")))


        List<String> fieldValues = JDBCUtilities.getUniqueFieldValues(connection, "CONTOURING_NOISE_MAP", "ISOLVL");
        assertTrue(fieldValues.contains("0"));
        assertTrue(fieldValues.contains("1"));
        assertTrue(fieldValues.contains("2"));
        assertTrue(fieldValues.contains("3"));
        assertTrue(fieldValues.contains("4"));
        assertTrue(fieldValues.contains("5"));
        assertTrue(fieldValues.contains("6"));
        assertTrue(fieldValues.contains("7"));
    }

    void testUpdateZ() throws SQLException, IOException {
        SHPRead.importTable(connection, TestAcousticTools.getResource("receivers.shp").getPath())
        def st = new Sql(connection)
        st.execute("select ST_FORCE3D('MULTILINESTRING ((223553.4 6757818.7, 223477.7 6758058))'::geometry) the_geom")
    }

}
