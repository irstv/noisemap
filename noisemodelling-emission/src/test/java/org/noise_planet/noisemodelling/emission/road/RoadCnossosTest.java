/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.emission.road;

import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossos;
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossosParameters;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * Test the Road model CNOSSOS as implemented in RoadCnossos.java
 * @author Pierre Aumond, Univ. Gustave eiffel
 */


public class RoadCnossosTest {
    private static final double EPSILON_TEST1 = 0.01;
    private static final int[] FREQUENCIES = new int[]{63, 125, 250, 500, 1000, 2000, 4000, 8000};

    /** based on CNOSSOS_Road_Output.csv and the CNOSSOS_DLL_CONSOLE.exe**/
    @Test
    public void T02() throws IOException {
        double lv_speed = 70;
        int lv_per_hour = 1000;
        double mv_speed = 70;
        int mv_per_hour = 1000;
        double hgv_speed = 70;
        int hgv_per_hour = 1000;
        double wav_speed = 70;
        int wav_per_hour = 1000;
        double wbv_speed = 70;
        int wbv_per_hour = 1000;
        int FreqParam = 8000;
        double Temperature = 15;
        String RoadSurface = "NL01";
        double Pm_stud = 0.5;
        double Ts_stud = 4;
        double Junc_dist = 200;
        int Junc_type = 1;
        RoadCnossosParameters rsParameters = new RoadCnossosParameters(lv_speed, mv_speed, hgv_speed, wav_speed, wbv_speed, lv_per_hour, mv_per_hour, hgv_per_hour, wav_per_hour, wbv_per_hour, FreqParam, Temperature, RoadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
        rsParameters.setSlopePercentage_without_limit(10);
        rsParameters.setFileVersion(1);
        assertEquals(77.6711, RoadCnossos.evaluate(rsParameters), EPSILON_TEST1);
    }

    /** based on CNOSSOS_Road_Output.csv and the CNOSSOS_DLL_CONSOLE.exe**/
    @Test
    public void T03() throws IOException {
        double lv_speed = 40;
        int lv_per_hour = 582;
        double mv_speed = 43;
        int mv_per_hour = 500;
        double hgv_speed = 45;
        int hgv_per_hour = 400;
        double wav_speed = 35;
        int wav_per_hour = 1000;
        double wbv_speed = 32;
        int wbv_per_hour = 1100;
        int FreqParam = 1000;
        double Temperature = 5;
        String RoadSurface = "NL02";
        double Pm_stud = 0.5;
        double Ts_stud = 4;
        double Junc_dist = 200;
        int Junc_type = 1;
        RoadCnossosParameters rsParameters = new RoadCnossosParameters(lv_speed, mv_speed, hgv_speed, wav_speed, wbv_speed, lv_per_hour, mv_per_hour, hgv_per_hour, wav_per_hour, wbv_per_hour, FreqParam, Temperature, RoadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
        rsParameters.setSlopePercentage_without_limit(-5);
        rsParameters.setFileVersion(1);
        assertEquals(79.6814, RoadCnossos.evaluate(rsParameters), EPSILON_TEST1);
    }

    /** based on CNOSSOS_Road_Output.csv and the CNOSSOS_DLL_CONSOLE.exe**/
    @Test
    public void T04() throws IOException {
        double lv_speed = 40;
        int lv_per_hour = 58;
        double mv_speed = 43;
        int mv_per_hour = 50;
        double hgv_speed = 45;
        int hgv_per_hour = 40;
        double wav_speed = 35;
        int wav_per_hour = 100;
        double wbv_speed = 32;
        int wbv_per_hour = 100;
        int FreqParam = 8000;
        double Temperature = -5;
        String RoadSurface = "NL03";
        double Pm_stud = 0.5;
        double Ts_stud = 4.;
        double Junc_dist = 200;
        int Junc_type = 1;

        RoadCnossosParameters rsParameters = new RoadCnossosParameters(lv_speed, mv_speed, hgv_speed, wav_speed, wbv_speed, lv_per_hour, mv_per_hour, hgv_per_hour, wav_per_hour, wbv_per_hour, FreqParam, Temperature, RoadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
        rsParameters.setSlopePercentage_without_limit(-7);
        rsParameters.setFileVersion(1);
        assertEquals(58.8222, RoadCnossos.evaluate(rsParameters), EPSILON_TEST1);

    }

    /** based on CNOSSOS_Road_Output.csv and the CNOSSOS_DLL_CONSOLE.exe**/
    @Test
    public void T05() throws IOException {
        double lv_speed = 40;
        int lv_per_hour = 58;
        double mv_speed = 43;
        int mv_per_hour = 50;
        double hgv_speed = 45;
        int hgv_per_hour = 40;
        double wav_speed = 35;
        int wav_per_hour = 100;
        double wbv_speed = 32;
        int wbv_per_hour = 100;
        int FreqParam = 63;
        double Temperature = -5;
        String RoadSurface = "NL03";
        double Pm_stud = 0.5;
        double Ts_stud = 4.;
        double Junc_dist = 50;
        int Junc_type = 1;
        RoadCnossosParameters rsParameters = new RoadCnossosParameters(lv_speed, mv_speed, hgv_speed, wav_speed, wbv_speed, lv_per_hour, mv_per_hour, hgv_per_hour, wav_per_hour, wbv_per_hour, FreqParam, Temperature, RoadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
        rsParameters.setSlopePercentage_without_limit(-7);
        rsParameters.setFileVersion(1);
        assertEquals(82.785, RoadCnossos.evaluate(rsParameters), EPSILON_TEST1);
    }

    /** based on CNOSSOS_Road_Output.csv and the CNOSSOS_DLL_CONSOLE.exe**/
    @Test
    public void T06() throws IOException {
        double lv_speed = 70;
        int lv_per_hour = 1000;
        double mv_speed = 70;
        int mv_per_hour = 1000;
        double hgv_speed = 70;
        int hgv_per_hour = 1000;
        double wav_speed = 70;
        int wav_per_hour = 1000;
        double wbv_speed = 70;
        int wbv_per_hour = 1000;
        int FreqParam = 4000;
        double Temperature = 15;
        String RoadSurface = "NL01";
        double Pm_stud = 0.5;
        double Ts_stud = 4.;
        double Junc_dist = 30;
        int Junc_type = 2;
        RoadCnossosParameters rsParameters = new RoadCnossosParameters(lv_speed, mv_speed, hgv_speed, wav_speed, wbv_speed, lv_per_hour, mv_per_hour, hgv_per_hour, wav_per_hour, wbv_per_hour, FreqParam, Temperature, RoadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
        rsParameters.setSlopePercentage_without_limit(10);
        rsParameters.setFileVersion(1);
        assertEquals(85.4991, RoadCnossos.evaluate(rsParameters), EPSILON_TEST1);
    }

    @Test
    public void TestTableIII() {
        int Freq = 125;
        String VehCat = "2";
        final Double coeff = RoadCnossos.getCoeff("ar", Freq, VehCat, 1);
        assertEquals(88.7, coeff, EPSILON_TEST1);

    }


    @Test
    public void CnossosEmissionTest() throws IOException {
        String vehCat = "1";
        double vehiclePerHour = 1000;
        double vehicleSpeed = 20;
        double tsStud = 0.5;
        String surfRef = "NL01";
        double temperature = -5;
        double pmStud = 1;
        double slope = -15;
        double juncDist = 200;
        int juncType = 1;
        double[] expectedValues = new double[]{88.421, 77.1136, 75.5712, 75.6919, 73.6689, 71.3471, 68.1195, 63.4796};
        for (int idFreq = 1; idFreq < FREQUENCIES.length; idFreq++) {
            RoadCnossosParameters rsParameters = new RoadCnossosParameters(vehicleSpeed, vehicleSpeed, vehicleSpeed,
                    vehicleSpeed, vehicleSpeed, "1".equals(vehCat) ? vehiclePerHour : 0,
                    "2".equals(vehCat) ? vehiclePerHour : 0, "3".equals(vehCat) ? vehiclePerHour : 0,
                    "4a".equals(vehCat) ? vehiclePerHour : 0, "4b".equals(vehCat) ? vehiclePerHour : 0,
                    FREQUENCIES[idFreq], temperature, surfRef, tsStud, pmStud, juncDist, juncType);
            rsParameters.setSlopePercentage(slope);
            rsParameters.setFileVersion(1);
            assertEquals(expectedValues[idFreq], RoadCnossos.evaluate(rsParameters), EPSILON_TEST1, String.format("%d Hz", FREQUENCIES[idFreq]));
        }
    }

    @Test
    public void CnossosEmissionTestwithSlope() throws IOException {
        String vehCat = "1";
        double vehiclePerHour = 1000;
        double vehicleSpeed = 20;
        double tsStud = 0.5;
        String surfRef = "NL01";
        double temperature = -5;
        double pmStud = 1;
        double slope = -15;
        double juncDist = 200;
        int juncType = 1;
        double[] expectedValues = new double[]{88.421, 77.09, 75.54, 75.01, 72.79, 71.13, 68.07, 63.44};
        for (int idFreq = 1; idFreq < FREQUENCIES.length; idFreq++) {
            RoadCnossosParameters rsParameters = new RoadCnossosParameters(vehicleSpeed, vehicleSpeed, vehicleSpeed,
                    vehicleSpeed, vehicleSpeed, "1".equals(vehCat) ? vehiclePerHour : 0,
                    "2".equals(vehCat) ? vehiclePerHour : 0, "3".equals(vehCat) ? vehiclePerHour : 0,
                    "4a".equals(vehCat) ? vehiclePerHour : 0, "4b".equals(vehCat) ? vehiclePerHour : 0,
                    FREQUENCIES[idFreq], temperature, surfRef, tsStud, pmStud, juncDist, juncType);
            rsParameters.setSlopePercentage(slope);
            rsParameters.setWay(3);
            rsParameters.setFileVersion(1);
            double result = RoadCnossos.evaluate(rsParameters);
            assertEquals(expectedValues[idFreq], result, EPSILON_TEST1, String.format("%d Hz", FREQUENCIES[idFreq]));
        }
    }

    @Test
    public void testWrongPavement() throws IOException {
        String vehCat="1";
        double vehiclePerHour = 1000;
        double vehicleSpeed = 20;
        double tsStud = 0.5;
        String surfRef = "wrongPavement";
        double temperature = -5;
        double pmStud = 1;
        double slope = -15;
        double juncDist = 200;
        int juncType = 1;
        double[] expectedValues = new double[]{88.421,77.09,75.54,75.01,72.79,71.13,68.07,63.44};
        for(int idFreq = 1; idFreq < FREQUENCIES.length; idFreq++) {
            RoadCnossosParameters rsParameters = new RoadCnossosParameters(vehicleSpeed, vehicleSpeed, vehicleSpeed,
                    vehicleSpeed, vehicleSpeed, "1".equals(vehCat) ? vehiclePerHour : 0,
                    "2".equals(vehCat) ? vehiclePerHour : 0, "3".equals(vehCat) ? vehiclePerHour : 0,
                    "4a".equals(vehCat) ? vehiclePerHour : 0, "4b".equals(vehCat) ? vehiclePerHour : 0,
                    FREQUENCIES[idFreq], temperature, surfRef, tsStud, pmStud, juncDist, juncType);
            rsParameters.setSlopePercentage(slope);
            rsParameters.setWay(3);
            rsParameters.setFileVersion(1);
            assertThrows(IOException.class, () -> RoadCnossos.evaluate(rsParameters));
        }
    }
}