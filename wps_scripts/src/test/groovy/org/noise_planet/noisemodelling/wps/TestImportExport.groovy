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
import org.junit.Assert
import org.junit.Test
import org.noise_planet.noisemodelling.wps.Database_Manager.Display_Database
import org.noise_planet.noisemodelling.wps.Database_Manager.Table_Visualization_Data
import org.noise_planet.noisemodelling.wps.Database_Manager.Table_Visualization_Map
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_Asc_Folder
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_OSM
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_OSM_Pedestrian
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_Symuvia
import org.noise_planet.noisemodelling.wps.Import_and_Export.Export_Table
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_Asc_File
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_File
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_Folder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Test parsing of zip file using H2GIS database
 */
class TestImportExport extends JdbcTestCase {
    Logger LOGGER = LoggerFactory.getLogger(TestImportExport.class)

    @Test
    void testImportSymuvia() {
        // Check empty database
        Object res = new Display_Database().exec(connection, [])

        assertEquals("", res)
        // Import OSM file
        new Import_Symuvia().exec(connection,
                ["pathFile"   : TestImportExport.getResource("symuvia.xml").getPath(),
                 "defaultSRID": 2154])

        res = new Display_Database().exec(connection, [])

        assertTrue(res.contains("SYMUVIA_TRAJ"))


    }


    @Test
    void testImportFile1() {

        String res = new Import_File().exec(connection,
                ["pathFile" : TestImportExport.getResource("receivers.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "receivers"])

        assertEquals("The table RECEIVERS has been uploaded to database!", res)
    }

    @Test
    void testImportFile2() {
        try {
            String res = new Import_File().exec(connection,
                    ["pathFile" : TestImportExport.getResource("receivers.shp").getPath(),
                     "inputSRID": "4362",
                     "tableName": "receivers"])
        }
        catch (Exception e) {
            String expectedMessage = "ERROR : The table already has a different SRID than the one you gave.";
            Assert.assertEquals("Exception message must be correct", expectedMessage, e.getMessage());
        }

    }

    @Test
    void testImportFile3() {

        new Import_File().exec(connection,
                ["pathFile" : TestImportExport.getResource("ROADS2.shp").getPath(),
                 "inputSRID": "2154",
                 "tableName": "ROADS"])

        String res = new Table_Visualization_Data().exec(connection,
                ["tableName": "ROADS"])

        assertFalse(res.contains("PK2"))
    }

    @Test
    void testImportAsc() {

        String res = new Import_Asc_File().exec(connection,
                ["pathFile" : TestImportExport.getResource("testAscFolder/precip30min.asc").getPath(),
                 "inputSRID": 2154])

        assertEquals("The table DEM has been uploaded to database ! </br>  Its SRID is : 4326. </br> Remember that to calculate a noise map, your SRID must be in metric coordinates. Please use the Wps block 'Change SRID' if needed.", res)
    }

    @Test
    void testImportAscFolder() {

        File file = new File(TestImportExport.getResource("testAscFolder/precip30min.asc").getPath()).getParentFile()
        String res = new Import_Asc_Folder().exec(connection,
                ["pathFolder": file.getPath(),
                 "inputSRID" : 2154])

        res = new Table_Visualization_Map().exec(connection, ["tableName": "DEM"]).getNumPoints()

        assertTrue(res == "598")
    }

    @Test
    void testImportFolder() {

        File file = new File(TestImportExport.getResource("Train/buildings2.shp").getPath()).getParentFile()
        String res = new Import_Folder().exec(connection,
                ["pathFolder": file.getPath(),
                 "importExt" : "shp"])

        assertTrue(res.contains("BUILDINGS2"))
        assertTrue(res.contains("RAIL_SECTIONS"))
        assertTrue(res.contains("RAILTRACK"))
        assertTrue(res.contains("RECEIVERS_RAILWAY"))
    }

    @Test
    void testExportFile() {

        // Check export geojson
        File testPath = new File("target/test.geojson")

        if (testPath.exists()) {
            testPath.delete()
        }

        SHPRead.importTable(connection, TestImportExport.getResource("receivers.shp").getPath())

        String res = new Export_Table().exec(connection,
                ["exportPath"   : "target/test.geojson",
                 "tableToExport": "RECEIVERS"])


        assertTrue(res.contains("RECEIVERS"))
        assertTrue(res.contains("2154"))
    }

    @Test
    void testImportOSM_Pbf_Pedestrian() {

        new Import_OSM_Pedestrian().exec(connection, [
                "pathFile"      : TestImportExport.getResource("map.osm.pbf").getPath(),
                "targetSRID"    : 2154
        ]);
        String res = new Display_Database().exec(connection, [])

        assertEquals("BUILDINGS</br></br>GROUND</br></br>PEDESTRIAN_AREA</br></br>PEDESTRIAN_POIS</br></br>PEDESTRIAN_WAYS</br></br>", res)

    }

    @Test
    void testImportOSMPBF() {

        new Import_OSM().exec(connection, [
                "pathFile"      : TestImportExport.getResource("map.osm.pbf").getPath(),
                "targetSRID"    : 2154,
                "ignoreGround"  : false,
                "ignoreBuilding": false,
                "ignoreRoads"   : false,
                "removeTunnels" : true
        ]);
        String res = new Display_Database().exec(connection, [])

        assertEquals("BUILDINGS</br></br>GROUND</br></br>ROADS</br></br>", res)

    }


    @Test
    void testImportOSMXML() {

        new Import_OSM().exec(connection, [
                "pathFile"      : TestImportExport.getResource("map.osm.gz").getPath(),
                "targetSRID"    : 2154,
                "ignoreGround"  : false,
                "ignoreBuilding": false,
                "ignoreRoads"   : false,
                "removeTunnels" : true
        ]);
        String res = new Display_Database().exec(connection, [])

        assertEquals("BUILDINGS</br></br>GROUND</br></br>ROADS</br></br>", res)

    }
}
