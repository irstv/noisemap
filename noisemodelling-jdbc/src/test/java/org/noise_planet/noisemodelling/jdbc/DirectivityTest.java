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
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.emission.directivity.DiscreteDirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.PolarGraphDirectivity;
import org.noise_planet.noisemodelling.emission.railway.nmpb.RailWayNMPBParameters;
import org.noise_planet.noisemodelling.emission.railway.nmpb.TrainAttenuation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;

public class DirectivityTest {
    final static double[] freqTest = new double[] {125, 250, 500, 1000, 2000, 4000, 8000, 16000};

    /**
     * Create cardioid using equation from
     * Farina, Angelo & Capra, Andrea & Chiesi, Lorenzo & Scopece, Leonardo. (2010). A Spherical Microphone Array for
     * Synthesizing Virtual Directive Microphones in Live Broadcasting and in Post Production.
     * @throws Exception
     */
    @Test
    public void exportDirectivityCardioid() throws Exception {
        PolarGraphDirectivity polarGraphDirectivity = new PolarGraphDirectivity();
        Connection connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(DirectivityTableLoaderTest.class.getSimpleName(), true, ""));
        Statement st = connection.createStatement();
        double back_attenuation = -40;
        double omnidirectionalFactor = 1; // 0=omnidirectional 1=cardioid 2="super cardioid" ..

        PreparedStatement pst = connection.prepareStatement("CREATE TABLE DIRECTIVITY(DIR_ID INTEGER, THETA FLOAT, PHI FLOAT, LW500 FLOAT)" +
                " AS SELECT 1, THETA, PHI, ? * (1 - POWER(0.5 + 0.5 * COS(RADIANS(PHI)) * COS(RADIANS(THETA)), ?)) FROM" +
                "  (SELECT X PHI FROM SYSTEM_RANGE(0, 355, 5)) T1, (SELECT X THETA FROM SYSTEM_RANGE(-90, 90, 5)) T2");
        pst.setDouble(1, back_attenuation);
        pst.setDouble(2, omnidirectionalFactor);
        pst.execute();
        // DEBUG st.execute("UPDATE DIRECTIVITY SET LW500=-10 WHERE THETA=45 AND PHI=270 ");
        // Data is inserted now fetch it from the database
        Map<Integer, DiscreteDirectivitySphere> directivities =
                NoiseMapLoader.fetchDirectivity(connection, "DIRECTIVITY", 1);

        try(BufferedWriter bw = new BufferedWriter(new FileWriter("target/cardioid_dir.html"))) {
            bw.write("<!DOCTYPE html>\n" +
                    "<html><head><meta charset=\"utf-8\">\n" +
                    "<style>" +
                    ".wrapper {\n" +
                    "  display: grid;\n" +
                    "  grid-template-columns: repeat(2, 1fr);\n" +
                    "  grid-gap: 10px;\n" +
                    "  grid-auto-rows: minmax(100px, auto);\n" +
                    "}" +
                    "P { text-align: center; font-size: xx-large; }" +
                    ".one {\n" +
                    "  grid-column: 1 / 2;\n" +
                    "  grid-row: 1;\n" +
                    "}\n" +
                    ".two {\n" +
                    "  grid-column: 2 / 2;\n" +
                    "  grid-row: 1;\n" +
                    "}" +
                    ".three {\n" +
                    "  grid-column: 1 / 2;\n" +
                    "  grid-row: 2;\n" +
                    "}" +
                    "</style>" +
                    "<head><body><h1>500 Hz</h1>\n");
            Logger logger = LoggerFactory.getLogger(DirectivityTest.class);
            for (Map.Entry<Integer, DiscreteDirectivitySphere> entry : directivities.entrySet()) {
                // DEBUG logger.info(String.format("phi 0 theta 0 %f",
                //       entry.getValue().getAttenuation(500, Math.toRadians(0), Math.toRadians(0))));
                bw.write("<div class=\"wrapper\">");
                bw.write("<div class=\"one\">");
                bw.write(polarGraphDirectivity.generatePolarGraph(entry.getValue(), 500,
                        -40, 0, PolarGraphDirectivity.ORIENTATION.TOP));
                bw.write(String.format(Locale.ROOT, "<p>%d Top</p>",entry.getKey()));
                bw.write("</div>");
                bw.write("<div class=\"two\">");
                bw.write(polarGraphDirectivity.generatePolarGraph(entry.getValue(), 500,
                        -40, 0, PolarGraphDirectivity.ORIENTATION.SIDE));
                bw.write(String.format(Locale.ROOT, "<p>%d Side</p>",entry.getKey()));
                bw.write("</div>");
                bw.write("<div class=\"three\">");
                bw.write(polarGraphDirectivity.generatePolarGraph(entry.getValue(), 500,
                        -40, 0, PolarGraphDirectivity.ORIENTATION.FRONT));
                bw.write(String.format(Locale.ROOT, "<p>%d Front</p>",entry.getKey()));
                bw.write("</div>");
                bw.write("</div>");
            }
            bw.write("</body>\n" +
                    "</html>\n ");
        }
    }

    @Test
    public void exportDirectivityDiscrete() throws IOException {


        DiscreteDirectivitySphere noiseSource = new DiscreteDirectivitySphere(1, freqTest);
        noiseSource.setInterpolationMethod(1);

        TrainAttenuation att = new TrainAttenuation(RailWayNMPBParameters.TrainNoiseSource.ROLLING);

        for(int yaw = 0; yaw < 360; yaw += 5) {
            double phi = Math.toRadians(yaw);
            for(int pitch = -90; pitch <= 90; pitch += 5) {
                double theta = Math.toRadians(pitch);
                double[] attSpectrum = att.getAttenuationArray(freqTest, phi, theta);
                noiseSource.addDirectivityRecord(theta, phi,
                        attSpectrum);
            }
        }



        PolarGraphDirectivity polarGraphDirectivity = new PolarGraphDirectivity();
        try(BufferedWriter bw = new BufferedWriter(new FileWriter("target/testDiscrete.html"))) {
            bw.write("<!DOCTYPE html>\n" +
                    "<html><head><meta charset=\"utf-8\">\n" +
                    "<style>" +
                    ".wrapper {\n" +
                    "  display: grid;\n" +
                    "  grid-template-columns: repeat(3, 1fr);\n" +
                    "  grid-gap: 10px;\n" +
                    "  grid-auto-rows: minmax(100px, auto);\n" +
                    "}" +
                    "P { text-align: center; font-size: xx-large; }" +
                    ".one {\n" +
                    "  grid-column: 1 / 2;\n" +
                    "  grid-row: 1;\n" +
                    "}\n" +
                    ".two {\n" +
                    "  grid-column: 2 / 2;\n" +
                    "  grid-row: 1;\n" +
                    "}" +
                    "</style>" +
                    "<head><body><h1>500 Hz</h1>\n");
            bw.write("<div class=\"wrapper\">");
            bw.write("<div class=\"one\">");
            bw.write(polarGraphDirectivity.generatePolarGraph(noiseSource, 500,
                    -35, 0, PolarGraphDirectivity.ORIENTATION.TOP));
            bw.write(String.format(Locale.ROOT, "<p>%s Horizontal</p>", "Discrete directivity"));
            bw.write("</div><div class=\"two\">");
            bw.write(polarGraphDirectivity.generatePolarGraph(noiseSource, 500,
                    -35, 0, PolarGraphDirectivity.ORIENTATION.SIDE));
            bw.write(String.format(Locale.ROOT, "<p>%s Side</p>", "Discrete directivity"));
            bw.write("</div></div>");


            bw.write("</body>\n" +
                    "</html>\n ");
        }
    }
}