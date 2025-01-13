/**
 * H2GIS is a library that brings spatial support to the H2 Database Engine
 * <http://www.h2database.com>. H2GIS is developed by CNRS
 * <http://www.cnrs.fr/>.
 *
 * This code is part of the H2GIS project. H2GIS is free software;
 * you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * H2GIS is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details <http://www.gnu.org/licenses/>.
 *
 *
 * For more information, please consult: <http://www.h2gis.org/>
 * or contact directly: info_at_h2gis.org
 */

package org.noise_planet.noisemodelling.jdbc.utils;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.TableLocation;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;


public class AscReaderDriverTest {

    private Connection connection;

    @BeforeEach
    public void tearUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(AscReaderDriverTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(connection != null) {
            connection.close();
        }
    }

    @Test
    public void testReadPrecip() throws IOException, SQLException {
        Statement st = connection.createStatement();
        st.execute("DROP TABLE IF EXISTS PRECIP30MIN");
        AscReaderDriver reader = new AscReaderDriver();
        reader.read(connection, new File(AscReaderDriverTest.class.getResource("precip30min.asc").getFile()), new EmptyProgressVisitor(), "PRECIP30MIN", 4326);

        // Check first read cell
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-179.75,-80.25), 4326))")) {
            assertTrue(rs.next());
            assertEquals(234, rs.getInt("Z"));
        }

        // Check last read cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-172.75, -89.75), 4326))")) {
            assertTrue(rs.next());
            assertEquals(114, rs.getInt("Z"));
        }
        // Check nodata cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM,  ST_SETSRID(ST_MAKEPOINT(-177.25, -84.25), 4326))")) {
            assertFalse(rs.next());
        }
    }


    /**
     * Test reading two time the same asc by pushing more lines
     * asc files may be tiled, it is interesting to have a method to read all asc files into the same output table
     * @throws IOException
     * @throws SQLException
     */
    @Test
    public void testReadPrecipTwoTimes() throws IOException, SQLException {
        Statement st = connection.createStatement();
        st.execute("DROP TABLE IF EXISTS PRECIP30MIN");
        AscReaderDriver reader = new AscReaderDriver();
        reader.setDeleteTable(true);
        reader.read(connection, new File(AscReaderDriverTest.class.getResource("precip30min.asc").getPath()), new EmptyProgressVisitor(), "PRECIP30MIN", 4326);

        // Check number of read cells
        assertEquals(299, JDBCUtilities.getRowCount(connection, TableLocation.parse("PRECIP30MIN", DBTypes.H2GIS)));

        // Check first read cell
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-179.75,-80.25), 4326))")) {
            assertTrue(rs.next());
            assertEquals(234, rs.getInt("Z"));
        }

        // Check last read cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-172.75, -89.75), 4326))")) {
            assertTrue(rs.next());
            assertEquals(114, rs.getInt("Z"));
        }
        // Check nodata cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM,  ST_SETSRID(ST_MAKEPOINT(-177.25, -84.25), 4326))")) {
            assertFalse(rs.next());
        }

        // Push the same file as a new tile
        reader.setDeleteTable(false);
        reader.read(connection, new File(AscReaderDriverTest.class.getResource("precip30min.asc").getPath()), new EmptyProgressVisitor(), "PRECIP30MIN", 4326);

        // Check first read cell
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-179.75,-80.25), 4326))")) {
            assertTrue(rs.next());
            assertEquals(234, rs.getInt("Z"));
        }

        // Check last read cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-172.75, -89.75), 4326))")) {
            assertTrue(rs.next());
            assertEquals(114, rs.getInt("Z"));
        }
        // Check nodata cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM,  ST_SETSRID(ST_MAKEPOINT(-177.25, -84.25), 4326))")) {
            assertFalse(rs.next());
        }

        // Check number of read cells
        assertEquals(299 * 2, JDBCUtilities.getRowCount(connection, TableLocation.parse("PRECIP30MIN", DBTypes.H2GIS)));
    }

    @Test
    public void testReadPrecipDouble() throws IOException, SQLException {
        Statement st = connection.createStatement();
        st.execute("DROP TABLE IF EXISTS PRECIP30MIN");
        AscReaderDriver reader = new AscReaderDriver();
        reader.setZType(2);
        reader.read(connection, new File(AscReaderDriverTest.class.getResource("precip30min.asc").getPath()), new EmptyProgressVisitor(), "PRECIP30MIN", 4326);

        // Check first read cell
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-179.75,-80.25), 4326))")) {
            assertTrue(rs.next());
            assertEquals(234.0, rs.getDouble("Z"), 0.00001);
        }

        // Check last read cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-172.75, -89.75), 4326))")) {
            assertTrue(rs.next());
            assertEquals(114, rs.getDouble("Z"), 0.00001);
        }
        // Check nodata cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM,  ST_SETSRID(ST_MAKEPOINT(-177.25, -84.25), 4326))")) {
            assertFalse(rs.next());
        }
    }

    @Test
    public void testReadPrecipCenterNodata() throws IOException, SQLException {
        AscReaderDriver reader = new AscReaderDriver();
        reader.setDeleteTable(true);
        reader.setImportNodata(true);
        reader.read(connection, new File(AscReaderDriverTest.class.getResource("precip30min_center.asc").getPath()), new EmptyProgressVisitor(), "PRECIP30MIN", 4326);

        // Check database content
        // Check first read cell
        Statement st = connection.createStatement();

        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-180, -80.50), 4326))")) {
            assertTrue(rs.next());
            assertEquals(234, rs.getInt("Z"));
        }

        // Check last read cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-173, -90), 4326))")) {
            assertTrue(rs.next());
            assertEquals(114, rs.getInt("Z"));
        }

        st.execute("CALL SHPWRITE('target/grid.shp', 'PRECIP30MIN', true)");
        //st.execute("CALL SHPWRITE('target/grid_nodata.shp', '(SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM,  st_buffer(ST_SETSRID(ST_MAKEPOINT(-179.5,-80.25), 4326), 0.1)))')");

        // Check nodata cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-177.50, -84.5), 4326))")) {
            assertTrue(rs.next());
            assertEquals(-9999, rs.getInt("Z"));
        }
    }

    @Test
    public void testReadPrecipCenter() throws IOException, SQLException {
        AscReaderDriver reader = new AscReaderDriver();
        reader.setDeleteTable(true);
        reader.read(connection, new File(AscReaderDriverTest.class.getResource("precip30min_center.asc").getPath()), new EmptyProgressVisitor(), "PRECIP30MIN", 4326);

        // Check database content

        // Check first read cell
        Statement st = connection.createStatement();

        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-180, -80.50), 4326))")) {
            assertTrue(rs.next());
            assertEquals(234, rs.getInt("Z"));
        }

        // Check last read cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-173, -90), 4326))")) {
            assertTrue(rs.next());
            assertEquals(114, rs.getInt("Z"));
        }

        // Check nodata cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT * FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_MAKEPOINT(-177.50, -84.5), 4326))")) {
            assertFalse(rs.next());
        }
    }
    @Test
    public void testReadPrecipPoint() throws IOException, SQLException {
        AscReaderDriver reader = new AscReaderDriver();
        reader.setAs3DPoint(true);
        reader.setDeleteTable(true);
        reader.read(connection, new File(AscReaderDriverTest.class.getResource("precip30min.asc").getPath()), new EmptyProgressVisitor(), "PRECIP30MIN", 4326);


        // Check database content

        // Check first read cell
        Statement st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT ST_Z(THE_GEOM) Z FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_EXPAND(ST_MAKEPOINT(-179.74,-80.18), 0.25, 0.25), 4326))")) {
            assertTrue(rs.next());
            assertEquals(234, rs.getInt("Z"));
        }

        // Check last read cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT ST_Z(THE_GEOM) Z FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_EXPAND( ST_MAKEPOINT(-172.604,-89.867), 0.25, 0.25), 4326))")) {
            assertTrue(rs.next());
            assertEquals(114, rs.getInt("Z"));
        }

        // Check nodata cell
        st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT  ST_Z(THE_GEOM) Z FROM PRECIP30MIN WHERE ST_INTERSECTS(THE_GEOM, ST_SETSRID(ST_EXPAND( ST_MAKEPOINT(-177.438, -84.077), 0.25, 0.25), 4326))")) {
            assertFalse(rs.next());
        }
    }


    @Test
    public void testReadPrecipEnvelope() throws IOException, SQLException {
        AscReaderDriver reader = new AscReaderDriver();
        reader.setExtractEnvelope(new Envelope(-178.242, -174.775, -89.707, -85.205));
        reader.setDeleteTable(true);
        reader.read(connection, new File(AscReaderDriverTest.class.getResource("precip30min.asc").getPath()), new EmptyProgressVisitor(), "PRECIP30MIN", 4326);
        // Check database content
        // Check number of extracted cells
        Statement st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT COUNT(*) CPT FROM PRECIP30MIN")) {
            assertTrue(rs.next());
            assertEquals(90, rs.getInt("CPT"));
        }
    }

    @Test
    public void testReadPrecipDownscale() throws IOException, SQLException {
        AscReaderDriver reader = new AscReaderDriver();
        reader.setDownScale(5);
        reader.setDeleteTable(true);
        reader.read(connection, new File(AscReaderDriverTest.class.getResource("precip30min.asc").getPath()), new EmptyProgressVisitor(), "PRECIP30MIN", 4326);
        // Check database content

        // Check number of extracted cells
        Statement st = connection.createStatement();
        try(ResultSet rs = st.executeQuery("SELECT COUNT(*) CPT FROM PRECIP30MIN")) {
            assertTrue(rs.next());
            assertEquals((15 / 5) * (20 / 5), rs.getInt("CPT"));
        }
    }

}
