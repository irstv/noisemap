/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.road;


import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossos;
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossosParameters;
import org.noise_planet.noisemodelling.emission.road.cnossosvar.RoadVehicleCnossosvar;
import org.noise_planet.noisemodelling.emission.road.cnossosvar.RoadVehicleCnossosvarParameters;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Test the Road model CNOSSOS as implemented in RoadVehicleCnossosVar.java
 * @author Pierre Aumond, Univ. Gustave eiffel
 * @author Arnaud Can, Univ. Gustave eiffel
 */

public class RoadVehicleCnossosvarTest {
    private static final double EPSILON_TEST1 = 0.1;

    /**
     * Test if static LW computation = dynamic LW computation
     * @throws IOException
     */
   @Test
    public void T02_OneVeh() throws IOException {
        double lv_speed = 50;
        int lv_per_hour = 50000;
        double mv_speed = 10;
        int mv_per_hour = 0;
        double hgv_speed = 10;
        int hgv_per_hour = 0;
        double wav_speed = 10;
        int wav_per_hour = 0;
        double wbv_speed = 10;
        int wbv_per_hour = 0;
        int FreqParam = 500;
        double Temperature = 15;
        String RoadSurface = "DEF";
        double Pm_stud = 0.;
        double Ts_stud = 0.;
        double Junc_dist = 200;
        int Junc_type = 1;
        RoadCnossosParameters rsParameters_stat = new RoadCnossosParameters(lv_speed, mv_speed, hgv_speed, wav_speed, wbv_speed, lv_per_hour, mv_per_hour, hgv_per_hour, wav_per_hour, wbv_per_hour, FreqParam, Temperature, RoadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
        rsParameters_stat.setSlopePercentage(0);
        rsParameters_stat.setFileVersion(2);
        rsParameters_stat.setTemperature(Temperature);
        rsParameters_stat.setRoadSurface(RoadSurface);

        double speed = 50;
        int acc = 1;
        boolean Stud = false;
        String veh_type = "1";
        int acc_type = 1;
        double LwStd = 0;
        int VehId = 10;
        RoadVehicleCnossosvarParameters rsParameters_dyn = new RoadVehicleCnossosvarParameters(speed, acc, veh_type, acc_type, Stud, LwStd, VehId);
        rsParameters_dyn.setSlopePercentage(0);
        rsParameters_dyn.setFileVersion(2);
        rsParameters_dyn.setFrequency(FreqParam);
        rsParameters_dyn.setTemperature(Temperature);
        rsParameters_dyn.setRoadSurface(RoadSurface);
        rsParameters_dyn.setJunc_dist(250);
        double res = RoadVehicleCnossosvar.evaluate(rsParameters_dyn);
        double res2 = RoadCnossos.evaluate(rsParameters_stat);
        assertEquals(res2, res, EPSILON_TEST1);


    }


    @Test
    public void testRoadNoise1() throws IOException {
        double speed = 50;
        int acc = 1;
        int FreqParam = 500;
        double Temperature = 0;
        String RoadSurface = "DEF";
        boolean Stud = true;
        double Junc_dist = 200;
        int Junc_type = 1;
        String veh_type = "1";
        int acc_type = 1;
        double LwStd = 1;
        int VehId = 10;

        RoadVehicleCnossosvarParameters rsParameters = new RoadVehicleCnossosvarParameters(speed, acc, veh_type, acc_type, Stud, LwStd, VehId);
        rsParameters.setSlopePercentage(0);
        rsParameters.setFileVersion(2);
        rsParameters.setFrequency(FreqParam);
        rsParameters.setTemperature(Temperature);
        rsParameters.setRoadSurface(RoadSurface);
        rsParameters.setJunc_dist(Junc_dist);
        rsParameters.setJunc_type(Junc_type);
        assertEquals(94.35, RoadVehicleCnossosvar.evaluate(rsParameters), EPSILON_TEST1);
    }

    @Test
    public void testRoadNoise2_speed0() throws IOException {
        double speed = 0;
        int acc = 1;
        int FreqParam = 500;
        double Temperature = 0;
        String RoadSurface = "DEF";
        boolean Stud = false;
        double Junc_dist = 200;
        int Junc_type = 1;
        String veh_type = "3";
        int acc_type = 1;
        double LwStd = 1;
        int VehId = 10;

        RoadVehicleCnossosvarParameters rsParameters = new RoadVehicleCnossosvarParameters(speed, acc, veh_type, acc_type, Stud, LwStd, VehId);
        rsParameters.setFrequency(FreqParam);
        rsParameters.setTemperature(Temperature);
        rsParameters.setRoadSurface(RoadSurface);
        rsParameters.setJunc_dist(Junc_dist);
        rsParameters.setJunc_type(Junc_type);
        rsParameters.setSlopePercentage(0);
        assertEquals(100.08, RoadVehicleCnossosvar.evaluate(rsParameters), EPSILON_TEST1);
    }
}