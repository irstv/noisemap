package org.noise_planet.noisemodelling.jdbc.utils;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class StringPreparedStatementsTest {

    @Test
    public void testInsert() throws SQLException, IOException {
        StringWriter sb = new StringWriter();
        BufferedWriter w = new BufferedWriter(sb);
        PreparedStatement p = new StringPreparedStatements(w, "INSERT INTO TABLE VALUES(?, ?, ?, ?);");
        GeometryFactory f = new GeometryFactory();
        p.setInt(1, 15);
        p.setString(2, "Test ' Hello");
        p.setObject(3, f.createPoint(new Coordinate(1, 2, 3)));
        p.setDouble(4, 15.2);
        p.addBatch();
        w.flush();
        assertEquals("INSERT INTO TABLE VALUES(15, 'Test '' Hello', 'POINT Z(1 2 3)', 15.2);\n", sb.toString());
    }
}