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
import org.h2gis.functions.spatial.crs.ST_Transform
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.junit.Test
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.noise_planet.noisemodelling.wps.Acoustic_Tools.Create_Isosurface
import org.noise_planet.noisemodelling.wps.Database_Manager.Display_Database
import org.noise_planet.noisemodelling.wps.Database_Manager.Table_Visualization_Data
import org.noise_planet.noisemodelling.wps.Import_and_Export.Export_Table
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_Asc_File
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_File
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_Folder
import org.noise_planet.noisemodelling.wps.NoiseModelling.Noise_level_from_source
import org.noise_planet.noisemodelling.wps.Receivers.Delaunay_Grid
import org.noise_planet.noisemodelling.wps.Receivers.Regular_Grid
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Test parsing of zip file using H2GIS database
 */
class TestTutorials extends JdbcTestCase {
    Logger LOGGER = LoggerFactory.getLogger(TestTutorials.class)


    void testTutorialPointSource() {
        Sql sql = new Sql(connection)

        // Check empty database
        Object res = new Display_Database().exec(connection, [])
        assertEquals("", res)

        new Import_Folder().exec(connection,
                ["pathFolder": TestImportExport.class.getResource("TutoPointSource/").getPath(),
                 "inputSRID" : "2154",
                 "importExt" : "geojson"])

        // Check SRID
        //assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("BUILDINGS")))

        // Check database
        res = new Display_Database().exec(connection, [])

        //assertTrue(res.contains("SOURCES"))
        //assertTrue(res.contains("BUILDINGS"))

        new Regular_Grid().exec(connection, ["sourcesTableName": "SOURCES",
                                             delta             : 0.2,
                                               "buildingTableName"   : "BUILDINGS"])

        // Check database
        res = new Display_Database().exec(connection, [])

        //assertTrue(res.contains("RECEIVERS"))


        res = new Noise_level_from_source().exec(connection, ["tableSources"  : "SOURCES",
                                                              "tableBuilding" : "BUILDINGS",
                                                              "tableReceivers": "RECEIVERS",
                                                              "confReflOrder" : 1,
                                                              "confDiffVertical" : true,
                                                              "confDiffHorizontal" : true,
                                                              "confSkipLevening" : true,
                                                              "confSkipLnight" : true,
                                                              "confSkipLden" : true])

        res =  new Display_Database().exec(connection, [])

        // Check database
        new Table_Visualization_Data().exec(connection, ["tableName": "LDAY_GEOM"])

        assertTrue(res.contains("LDAY_GEOM"))

        def rowResult = sql.firstRow("SELECT MAX(LEQ), MAX(LAEQ) FROM LDAY_GEOM")
        assertEquals(72, rowResult[0] as Double, 5.0)
        assertEquals(69, rowResult[1] as Double, 5.0)

        // Check export geojson
        File testPath = new File("target/tutoPointSource.geojson")

        if(testPath.exists()) {
            testPath.delete()
        }

        new Export_Table().exec(connection,
                ["exportPath"   : "target/tutoPointSource.geojson",
                 "tableToExport": "LDAY_GEOM"])


    }


    void testTutorialPointSourceDirectivity() {
        Logger logger = LoggerFactory.getLogger(TestTutorials.class)

        Sql sql = new Sql(connection)

        // Check empty database
        Object res = new Display_Database().exec(connection, [])
        assertEquals("", res)

        new Import_File().exec(connection, [
                pathFile : TestTutorials.class.getResource("buildings.shp").getPath(),
                tableName : "BUILDINGS"])

        new Import_File().exec(connection, [
                pathFile : TestTutorials.class.getResource("TutoPointSourceDirectivity/Point_Source.geojson").getPath(),
                tableName : "Point_Source"])

        new Import_File().exec(connection, [
                pathFile : TestTutorials.class.getResource("ground_type.shp").getPath(),
                tableName : "ground_type"])

        new Import_File().exec(connection, [
                pathFile : TestTutorials.class.getResource("TutoPointSourceDirectivity/Directivity.csv").getPath(),
                tableName : "Directivity"])

        new Import_File().exec(connection, [
                pathFile : TestTutorials.class.getResource("dem.geojson").getPath(),
                tableName : "DEM"])



        res = new Display_Database().exec(connection, [])

        assertTrue(res.contains("BUILDINGS"))
        assertTrue(res.contains("DIRECTIVITY"))
        assertTrue(res.contains("GROUND"))
        assertTrue(res.contains("POINT_SOURCE"))
        assertTrue(res.contains("DEM"))

        // generate a grid of receivers using the buildings as envelope
        logger.info(new Delaunay_Grid().exec(connection, [maxArea: 60, tableBuilding: "BUILDINGS",
                                                          sourcesTableName : "POINT_SOURCE" , height: 1.6]));


        new Export_Table().exec(connection, [exportPath:"target/receivers.shp", tableToExport: "RECEIVERS"])
        new Export_Table().exec(connection, [exportPath:"target/TRIANGLES.shp", tableToExport: "TRIANGLES"])

        new Noise_level_from_source().exec(connection, [tableBuilding: "BUILDINGS", tableSources:"POINT_SOURCE",
                                                        tableReceivers : "RECEIVERS",
                                                        tableGroundAbs: "GROUND_TYPE",
                                                        tableSourceDirectivity: "DIRECTIVITY",
                                                        confMaxSrcDist : 800,
                                                        confSkipLden: true,
                                                        confSkipLnight: true,
                                                        confSkipLevening: true,
                                                        tableDEM: "DEM"
                                                        ])

        new Create_Isosurface().exec(connection, [resultTable: "LDAY_GEOM", smoothCoefficient : 0.4])

        new Export_Table().exec(connection, [exportPath:"target/CONTOURING_NOISE_MAP.shp", tableToExport: "CONTOURING_NOISE_MAP"])

        new Export_Table().exec(connection, [exportPath:"target/LDAY_GEOM.shp", tableToExport: "LDAY_GEOM"])



    }
}
