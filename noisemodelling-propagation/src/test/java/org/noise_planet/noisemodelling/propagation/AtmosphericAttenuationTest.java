/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.propagation;

import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Test atmospheric attenuation with ISO 9313-1:1993 P.5-12 as reference values
 */
public class AtmosphericAttenuationTest {
    private static final double EPSILON = 0.1;
    private static final List<Double> freq_lvl_exact = Arrays.asList(AttenuationCnossosParameters.asOctaveBands(
            Scene.DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE));

    @Test
    public void atmoTestMinus20degree() {
        double temperature = -20;
        double humidity = 70;
        double pressure = 101325;
        final double[] expected = new double[] {0.173,0.514,1.73,5.29,11.5,16.6,20.2,27.8};
        for(int idfreq=0;idfreq< expected.length;idfreq++) {
            double freq = freq_lvl_exact.get(idfreq);
            double coefAttAtmos = AttenuationCnossosParameters.getCoefAttAtmos(freq, humidity,pressure,temperature+ AttenuationCnossosParameters.K_0);
            assertEquals(expected[idfreq], coefAttAtmos, EPSILON);
        }
    }

    @Test
    public void atmoTestMinus15degree() {
        double temperature = -15;
        double humidity = 50;
        double pressure = 101325;
        final double[] expected = new double[] {0.188,0.532,1.76,5.61,13.2,20.5,25.2,33.2};
        for(int idfreq=0;idfreq< expected.length;idfreq++) {
            double freq = freq_lvl_exact.get(idfreq);
            double coefAttAtmos = AttenuationCnossosParameters.getCoefAttAtmos(freq, humidity,pressure,temperature+ AttenuationCnossosParameters.K_0);
            assertEquals(expected[idfreq], coefAttAtmos, EPSILON);
        }
    }

    @Test
    public void atmoTest0degree() {
        double temperature = 0;
        double humidity = 60;
        double pressure = 101325;
        final double[] expected = new double[] {0.165,0.401,0.779,1.78,5.5,19.3,63.3,154.4};
        for(int idfreq=0;idfreq< expected.length;idfreq++) {
            double freq = freq_lvl_exact.get(idfreq);
            double coefAttAtmos = AttenuationCnossosParameters.getCoefAttAtmos(freq, humidity,pressure,temperature+ AttenuationCnossosParameters.K_0);
            assertEquals(expected[idfreq], coefAttAtmos, EPSILON);
        }
    }

    @Test
    public void atmoTest20degree() {
        double temperature = 20;
        double humidity = 80;
        double pressure = 101325;
        final double[] expected = new double[] {0.079,0.302,1.04,2.77,5.15,8.98,21.3,68.6};
        for(int idfreq=0;idfreq< expected.length;idfreq++) {
            double freq = freq_lvl_exact.get(idfreq);
            double coefAttAtmos = AttenuationCnossosParameters.getCoefAttAtmos(freq, humidity,pressure,temperature+ AttenuationCnossosParameters.K_0);
            assertEquals(expected[idfreq], coefAttAtmos, EPSILON);
        }
    }


    @Test
    public void atmoTestCnossos() {
        double temperature = 10;
        double humidity = 70;
        double pressure = 101325;
        final double[] expected = new double[] {0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        for(int idfreq=0;idfreq< expected.length;idfreq++) {
            double freq = freq_lvl_exact.get(idfreq);
            double coefAttAtmos = AttenuationCnossosParameters.getCoefAttAtmos(freq, humidity,pressure,temperature+ AttenuationCnossosParameters.K_0);
            assertEquals(expected[idfreq], coefAttAtmos, EPSILON);
        }
    }
}
