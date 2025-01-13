/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.emission.LineSource;
import org.noise_planet.noisemodelling.emission.directivity.DirectivityRecord;
import org.noise_planet.noisemodelling.emission.directivity.cnossos.RailwayCnossosDirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.DiscreteDirectivitySphere;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


public class DirectivityTableLoaderTest {

    private Connection connection;

    @BeforeEach
    public void tearUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(DirectivityTableLoaderTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(connection != null) {
            connection.close();
        }
    }

    /**
     * Create discrete directivity table from a formulae directivity class. Then load it using library
     * @throws SQLException
     */
    @Test
    public void testFetch() throws SQLException {
        double[] freqTest = new double[] {63, 125, 250, 500, 1000, 2000, 4000, 8000};
        try(Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE DIRTEST(DIR_ID INTEGER, THETA FLOAT, PHI FLOAT, LW63 FLOAT, LW125 FLOAT, LW250 FLOAT, LW500 FLOAT, LW1000 FLOAT, LW2000 FLOAT, LW4000 FLOAT, LW8000 FLOAT)");
        }

        RailwayCnossosDirectivitySphere att = new RailwayCnossosDirectivitySphere(new LineSource("TRACTIONB"));

        try(PreparedStatement st = connection.prepareStatement("INSERT INTO DIRTEST VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            for(int yaw = 0; yaw < 360; yaw += 5) {
                double phi = Math.toRadians(yaw);
                for (int pitch = -90; pitch <= 90; pitch += 5) {
                    double theta = Math.toRadians(pitch);
                    st.setInt(1, 1);
                    st.setFloat(2, pitch);
                    st.setFloat(3, yaw);
                    double[] attSpectrum = att.getAttenuationArray(freqTest, phi, theta);
                    for (int idFreq = 0; idFreq < freqTest.length; idFreq++) {
                        st.setFloat(4 + idFreq, (float)attSpectrum[idFreq]);
                    }
                    st.executeUpdate();
                }
            }
        }

        try(Statement st = connection.createStatement()) {
            st.execute("CALL CSVWrite('target/directivity_demo.csv', 'SELECT * FROM DIRTEST');");
        }

        // Data is inserted now fetch it from the database
        Map<Integer, DiscreteDirectivitySphere> directivities = NoiseMapLoader.fetchDirectivity(connection, "DIRTEST", 1);

        assertEquals(1, directivities.size());

        assertTrue(directivities.containsKey(1));

        DiscreteDirectivitySphere d = directivities.get(1);
        for(DirectivityRecord directivityRecord : d.getRecordsTheta()) {
            double[] attSpectrum = att.getAttenuationArray(freqTest, directivityRecord.getPhi(), directivityRecord.getTheta());
            assertArrayEquals(attSpectrum, directivityRecord.getAttenuation(), 1e-2);
        }
    }

}