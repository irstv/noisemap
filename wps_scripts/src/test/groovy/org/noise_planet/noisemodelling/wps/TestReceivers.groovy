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
import org.h2.value.ValueGeometry
import org.h2gis.functions.io.shp.SHPRead
import org.h2gis.functions.io.shp.SHPWrite
import org.h2gis.functions.spatial.crs.ST_SetSRID
import org.h2gis.functions.spatial.crs.ST_Transform
import org.h2gis.utilities.GeometryMetaData
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTReader
import org.noise_planet.noisemodelling.wps.Receivers.Building_Grid
import org.noise_planet.noisemodelling.wps.Receivers.Building_Grid3D
import org.noise_planet.noisemodelling.wps.Receivers.Delaunay_Grid
import org.noise_planet.noisemodelling.wps.Receivers.Random_Grid
import org.noise_planet.noisemodelling.wps.Receivers.Regular_Grid

class TestReceivers extends JdbcTestCase {

    void testBuildingGrid3D() {
        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        sql.execute("CREATE SPATIAL INDEX ON BUILDINGS(THE_GEOM)")
        sql.execute("CREATE INDEX bheight ON BUILDINGS(height)")


        new Building_Grid3D().exec(connection,  ["tableBuilding" : "BUILDINGS",
                                                 "delta"         : 5,
                                                 "heightLevels"  : 2.5,
                                                 "fenceTableName": "BUILDINGS"])

        def receivers_in_buildings = sql.firstRow("SELECT COUNT(*) from receivers r, buildings b where r.the_geom && b.the_geom and st_intersects(r.the_geom, b.the_geom) and ST_Z(r.the_geom) < b.height ")[0] as Integer
        assertEquals(0, receivers_in_buildings)

        sql.execute("CREATE INDEX ON RECEIVERS(pk_building)")

        // check effective distance between receivers

        def average_receiver_min_distance = sql.firstRow("SELECT AVG((select ST_3DLength(ST_MakeLine(R.THE_GEOM, RR.THE_GEOM)) dist from receivers rr where rr.pk_building = r.pk_building and r.pk != rr.pk ORDER BY ST_DISTANCE(R.THE_GEOM, RR.THE_GEOM) LIMIT 1)) avgdist from receivers r")[0] as Double

        // SHPWrite.exportTable(connection, "target/receivers.shp", "RECEIVERS")
        //SHPWrite.exportTable(connection, "target/receivers_line.shp", "TMP_SCREENS_MERGE")
        assertEquals(4.55, average_receiver_min_distance, 0.1)


        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))


    }

    void testBuildingGrid3DNoFence() {
        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        sql.execute("CREATE SPATIAL INDEX ON BUILDINGS(THE_GEOM)")
        sql.execute("CREATE INDEX bheight ON BUILDINGS(height)")


        new Building_Grid3D().exec(connection,  ["tableBuilding" : "BUILDINGS",
                                                 "delta"         : 5,
                                                 "heightLevels"  : 2.5])

        def receivers_in_buildings = sql.firstRow("SELECT COUNT(*) from receivers r, buildings b where r.the_geom && b.the_geom and st_intersects(r.the_geom, b.the_geom) and ST_Z(r.the_geom) < b.height ")[0] as Integer
        assertEquals(0, receivers_in_buildings)

        sql.execute("CREATE INDEX ON RECEIVERS(pk_building)")

        // check effective distance between receivers

        def average_receiver_min_distance = sql.firstRow("SELECT AVG((select ST_3DLength(ST_MakeLine(R.THE_GEOM, RR.THE_GEOM)) dist from receivers rr where rr.pk_building = r.pk_building and r.pk != rr.pk ORDER BY ST_DISTANCE(R.THE_GEOM, RR.THE_GEOM) LIMIT 1)) avgdist from receivers r")[0] as Double

        // SHPWrite.exportTable(connection, "target/receivers.shp", "RECEIVERS")
        //SHPWrite.exportTable(connection, "target/receivers_line.shp", "TMP_SCREENS_MERGE")
        assertEquals(4.55, average_receiver_min_distance, 0.1)


        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))

        //Execute a second time for missing drop tables test



        new Building_Grid3D().exec(connection,  ["tableBuilding" : "BUILDINGS",
                                                 "delta"         : 5,
                                                 "heightLevels"  : 2.5])

    }

    void testBuildingGrid3DWithPop() {
        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        sql.execute("CREATE SPATIAL INDEX ON BUILDINGS(THE_GEOM)")
        sql.execute("CREATE INDEX bheight ON BUILDINGS(height)")
        sql.execute("ALTER TABLE BUILDINGS ADD COLUMN POP INT DEFAULT RANDOM(20) + 1")

        new Building_Grid3D().exec(connection,  ["tableBuilding" : "BUILDINGS",
                                                 "delta"         : 5,
                                                 "heightLevels"  : 2.5,
                                                 "fenceTableName": "BUILDINGS"])

        def receivers_in_buildings = sql.firstRow("SELECT COUNT(*) from receivers r, buildings b where r.the_geom && b.the_geom and st_intersects(r.the_geom, b.the_geom) and ST_Z(r.the_geom) < b.height ")[0] as Integer
        assertEquals(0, receivers_in_buildings)

        sql.execute("CREATE INDEX ON RECEIVERS(pk_building)")

        // check effective distance between receivers

        def average_receiver_min_distance = sql.firstRow("SELECT AVG((select ST_3DLength(ST_MakeLine(R.THE_GEOM, RR.THE_GEOM)) dist from receivers rr where rr.pk_building = r.pk_building and r.pk != rr.pk ORDER BY ST_DISTANCE(R.THE_GEOM, RR.THE_GEOM) LIMIT 1)) avgdist from receivers r")[0] as Double

        // SHPWrite.exportTable(connection, "target/receivers.shp", "RECEIVERS")
        //SHPWrite.exportTable(connection, "target/receivers_line.shp", "TMP_SCREENS_MERGE")
        assertEquals(4.55, average_receiver_min_distance, 0.1)


        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))


    }

    void testBuildingGrid() {
        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        sql.execute("CREATE SPATIAL INDEX ON BUILDINGS(THE_GEOM)")
        sql.execute("CREATE INDEX bheight ON BUILDINGS(height)")

        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath())

        new Building_Grid().exec(connection,  ["tableBuilding" : "BUILDINGS",
                                               "delta" : 5,
                                                "height" : 6,
                                               "sourcesTableName" : "ROADS",
                                               "fenceTableName" : "BUILDINGS"])


        def receivers_in_buildings = sql.firstRow("SELECT COUNT(*) from receivers r, buildings b where r.the_geom && b.the_geom and st_intersects(r.the_geom, b.the_geom) and ST_Z(r.the_geom) < b.height ")[0] as Integer
        assertEquals(0, receivers_in_buildings)

        sql.execute("CREATE SPATIAL INDEX ON RECEIVERS(the_geom)")
        sql.execute("CREATE INDEX ON RECEIVERS(build_pk)")

        // check effective distance between receivers

        def average_receiver_min_distance = sql.firstRow("SELECT AVG((select ST_DISTANCE(R.THE_GEOM, RR.THE_GEOM) dist from receivers rr where rr.build_pk = r.build_pk and r.pk != rr.pk ORDER BY ST_DISTANCE(R.THE_GEOM, RR.THE_GEOM) LIMIT 1)) avgdist from receivers r")[0] as Double

        //SHPWrite.exportTable(connection, "target/receivers.shp", "RECEIVERS")
        //SHPWrite.exportTable(connection, "target/receivers_line.shp", "TMP_SCREENS_MERGE")
        assertEquals(5, average_receiver_min_distance, 0.6)


        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))


    }

    void testBuildingGridWithPop() {
        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath(),
                "BUILDINGS_NOPOP", ValueBoolean.TRUE)
        sql.execute("DROP TABLE IF EXISTS BUILDINGS")
        sql.execute("CREATE TABLE BUILDINGS(pk serial primary key, the_geom geometry, height double, pop double) AS SELECT pk, the_geom, height, ST_AREA(THE_GEOM) / 15 as pop from buildings_nopop")

        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath(), ValueBoolean.TRUE)

        new Building_Grid().exec(connection,  ["tableBuilding" : "BUILDINGS",
                                               "delta" : 5,
                                               "height" : 6,
                                               "sourcesTableName" : "ROADS",
                                               "fenceTableName" : "BUILDINGS"])

        //SHPWrite.exportTable(connection, "target/receivers.shp", "RECEIVERS")
        //SHPWrite.exportTable(connection, "target/buildings.shp", "BUILDINGS")

        def receivers_pop = sql.firstRow("SELECT sum(pop) from receivers")[0] as Double

        def buildings_pop = sql.firstRow("SELECT sum(pop) from buildings where pk in (select distinct build_pk from receivers)")[0] as Double

        assertEquals(0, buildings_pop - receivers_pop, 0.1);


        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))

    }
    void testBuildingGridFence() {
        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        sql.execute("CREATE SPATIAL INDEX ON BUILDINGS(THE_GEOM)")
        sql.execute("CREATE INDEX bheight ON BUILDINGS(height)")

        GeometryFactory f = new GeometryFactory();
        def g = f.toGeometry(new Envelope(223556.5, 223765.7,6758256.91, 6758576.3))
        g.setSRID(2154)
        def gFence = ST_Transform.ST_Transform(connection, g, 4326)
        new Building_Grid().exec(connection,  ["tableBuilding" : "BUILDINGS",
                                               "delta" : 5,
                                               "height" : 6,
                                               "fence" : gFence.toString()]) // in WPS Fence is an instance of geoscript.geom.Polygon not jts

        assertTrue(sql.firstRow("SELECT count(*) cpt from receivers")[0] > 0)

        def receivers_pop = sql.firstRow("SELECT count(*) cpt from receivers r where not ST_Intersects(r.the_geom, :g)", [g : g])[0] as Integer

        assertEquals(0, receivers_pop);


        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))

    }

    void testDelaunayGrid() {
        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath())
        sql.execute("CREATE SPATIAL INDEX ON BUILDINGS(THE_GEOM)")
        sql.execute("CREATE SPATIAL INDEX ON ROADS(THE_GEOM)")

        new Delaunay_Grid().exec(connection, ["buildingTableName" : "BUILDINGS",
        "sourcesTableName" : "ROADS"]);


        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))

        sql.execute("CREATE SPATIAL INDEX ON RECEIVERS(THE_GEOM)")

        // Check if index and geoms is corresponding
        def res = sql.firstRow("SELECT MAX((SELECT ST_DISTANCE(T.THE_GEOM, R.THE_GEOM) D FROM RECEIVERS R WHERE R.PK = T.PK_1)) D1," +
                " MAX((SELECT ST_DISTANCE(T.THE_GEOM, R.THE_GEOM) D FROM RECEIVERS R WHERE R.PK = T.PK_2)) D2," +
                " MAX((SELECT ST_DISTANCE(T.THE_GEOM, R.THE_GEOM) D FROM RECEIVERS R WHERE R.PK = T.PK_3)) D3 FROM TRIANGLES T");
        def max_dist_a = res[0] as Double
        def max_dist_b = res[1] as Double
        def max_dist_c = res[2] as Double
        assertEquals(0.0, max_dist_a, 1e-6d);
        assertEquals(0.0, max_dist_b, 1e-6d);
        assertEquals(0.0, max_dist_c, 1e-6d);
    }

    public void testRandomGrid() {

        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath())

        new Random_Grid().exec(connection,  ["buildingTableName" : "BUILDINGS",
                                             "sourcesTableName" : "ROADS",
                                             "nReceivers" : 200])

        assertTrue(200 >= (sql.firstRow("SELECT COUNT(*) CPT FROM RECEIVERS")[0] as Integer))
        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))
    }

    public void testRandomGridFence() {

        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath())

        new Random_Grid().exec(connection,  ["buildingTableName" : "BUILDINGS",
                                             "sourcesTableName" : "ROADS",
                                             "nReceivers" : 200,
                                            "fenceTableName" : "BUILDINGS"])

        assertTrue(200 >= (sql.firstRow("SELECT COUNT(*) CPT FROM RECEIVERS")[0] as Integer))

        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))
    }

    public void testRandomGridFence2() {

        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath())

        GeometryFactory f = new GeometryFactory();
        def g = ValueGeometry.get("SRID=2154; POLYGON ((223994.2 6757775.9, 223930.2 6757890.1, 223940.2 6757895.7, 224001.6 6757783.2, 223994.2 6757775.9))").getGeometry()
        def gNoReceiver = ValueGeometry.get("SRID=2154; POLYGON ((223938 6757827.1, 223957.4 6757836.6, 223947.4 6757851.4, 223940.2 6757833, 223938 6757827.1))").getGeometry()

        def gFence = ST_Transform.ST_Transform(connection, g, 4326)

        new Random_Grid().exec(connection,  ["buildingTableName" : "BUILDINGS",
                                             "sourcesTableName" : "ROADS",
                                             "nReceivers" : 200,
                                             "fence" : gFence.toString()])

        assertFalse(0 == sql.firstRow("SELECT COUNT(*) CPT FROM RECEIVERS")[0] as Integer)

        assertEquals(0, sql.firstRow("SELECT COUNT(*) CPT FROM RECEIVERS WHERE NOT ST_INTERSECTS(THE_GEOM, :geom)", [geom : g])[0] as Integer)

        assertEquals(0, sql.firstRow("SELECT COUNT(*) CPT FROM RECEIVERS WHERE ST_INTERSECTS(THE_GEOM, :gNoReceiver)", [gNoReceiver : gNoReceiver])[0] as Integer)

        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))
    }


    public void testRegularGridFence() {

        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath())

        GeometryFactory f = new GeometryFactory();
        def g = f.toGeometry(new Envelope(223556.5, 223765.7,6758256.91, 6758576.3))
        def gFence = ST_Transform.ST_Transform(connection, ST_SetSRID.setSRID(g, 2154), 4326)

        new Regular_Grid().exec(connection,  ["buildingTableName": "BUILDINGS",
                                              "sourcesTableName" : "ROADS",
                                              "delta" : 50,
                                              "fence" : gFence.toString()])

        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))
    }

    public void testRegularGridFenceTable() {

        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath())

        new Regular_Grid().exec(connection,  ["buildingTableName": "BUILDINGS",
                                              "sourcesTableName" : "ROADS",
                                              "delta" : 50,
                                              "fenceTableName" : "BUILDINGS"])

        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))
    }


    public void testRegularGridFenceGeom() {

        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath())

        GeometryFactory f = new GeometryFactory();

        def g = ValueGeometry.get("SRID=2154;POLYGON ((223994.2 6757775.9, 223930.2 6757890.1, 223940.2 6757895.7, 224001.6 6757783.2, 223994.2 6757775.9))").geometry
        def gNoReceiver = ValueGeometry.get("SRID=2154;POLYGON ((223938 6757827.1, 223957.4 6757836.6, 223947.4 6757851.4, 223940.2 6757833, 223938 6757827.1))").geometry;

        def gFence = ST_Transform.ST_Transform(connection, g, 4326)

        new Regular_Grid().exec(connection,  ["buildingTableName" : "BUILDINGS",
                                             "sourcesTableName" : "ROADS",
                                             "delta" : 1,
                                             "fence" : gFence.toString()])

        assertFalse(0 == sql.firstRow("SELECT COUNT(*) CPT FROM RECEIVERS")[0] as Integer)

        assertEquals(0, sql.firstRow("SELECT COUNT(*) CPT FROM RECEIVERS WHERE NOT ST_INTERSECTS(THE_GEOM, :geom)", [geom : g])[0] as Integer)

        assertEquals(0, sql.firstRow("SELECT COUNT(*) CPT FROM RECEIVERS WHERE ST_INTERSECTS(THE_GEOM, :geom)", [geom : gNoReceiver])[0] as Integer)

        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))

        SHPWrite.exportTable(connection, "target/regular.shp", "RECEIVERS", ValueBoolean.TRUE)

    }

    public void testRegularGridWithTriangleTable() {

        def sql = new Sql(connection)

        SHPRead.importTable(connection, TestReceivers.getResource("buildings.shp").getPath())
        SHPRead.importTable(connection, TestReceivers.getResource("roads.shp").getPath())

        new Regular_Grid().exec(connection,  ["fenceTableName" : "BUILDINGS",
                                              "delta" : 50,
                                              "outputTriangleTable" : true])

        assertEquals(2154, GeometryTableUtilities.getSRID(connection, TableLocation.parse("RECEIVERS")))

        assertEquals(1920, sql.firstRow("SELECT COUNT(*) FROM TRIANGLES")[0] as Integer)
    }
}
