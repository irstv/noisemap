/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.utils;

import java.io.IOException;

/**
 * Some basic tools useful for the emission module
 *  @author Nicolas Fortin, Université Gustave Eiffel
 *  @author Pierre Aumond, Université Gustave Eiffel
 */

public class Utils {

    /**
     * Convert dB to W
     * @param dB
     * @return W (double)
     */
    public static double dbToW(double dB) {
        return Math.pow(10., dB / 10.);
    }

    /**
     * Convert W to dB
     * @param w
     * @return dB (double)
     */
    public static double wToDb(double w) {
        return 10 * Math.log10(w);
    }

    /**
     * Compute the slope between to z points (for a road or rail segment)
     * @param beginZ Z start
     * @param endZ Z end
     * @param road_length_2d Road length (projected to Z axis)
     * @return Slope percentage
     */
    public static double computeSlope(double beginZ, double endZ, double road_length_2d) {
        return (endZ - beginZ) / road_length_2d * 100.;
    }

    /**
     * Compute Noise Level from flow_rate and speed Eq 2.2.1 from Directive 2015
     * @param LWim LW,i,m is the directional sound power of a single vehicle and is expressed in dB (re. 10–12 W/m).
     * @param Qm Traffic flow data Qm shall be expressed as yearly average per hour, per time period (day-evening-night), per vehicle class and per source line.
     * @param vm The speed vm is a representative speed per vehicle category: in most cases the lower of the maximum legal speed for the section of road and the maximum legal speed for the vehicle category. If measurement data is unavailable, the maximum legal speed for the vehicle category shall be used.
     * @return emission sound level in dB/m
     * @throws IOException if speed &lt; 0 km/h
     */
    public static Double Vperhour2NoiseLevel(double LWim, double Qm, double vm) throws IOException {
        if (vm < 0) {
            throw new IOException("Error : speed < 0 km/h");
        }
        return LWim + 10 * Math.log10(Qm / (1000 * vm));
    }

    /**
     * Compute Noise Level from flow_rate and speed Eq 2.2.1 from Directive 2015
     * @param LWimf LW,i,m is the directional sound power of a single vehicle and is expressed in dB (re. 10–12 W/m) and for every freq
     * @param Qm Traffic flow data Qm shall be expressed as yearly average per hour, per time period (day-evening-night), per vehicle class and per source line.
     * @param vm The speed vm is a representative speed per vehicle category: in most cases the lower of the maximum legal speed for the section of road and the maximum legal speed for the vehicle category. If measurement data is unavailable, the maximum legal speed for the vehicle category shall be used.
     * @return emission sound level in dB/m
     * @throws IOException if speed &lt; 0 km/h
     */
    public static double[] Vperhour2NoiseLevelAllFreq(double[] LWimf, double Qm, double vm) throws IOException {
        double[] LWimf_return = new double[LWimf.length];
        if (vm < 0) {
            throw new IOException("Error : speed < 0 km/h");
        }
        for (int f = 1; f < LWimf.length; f++) {
            LWimf_return[f] = LWimf[f] + 10 * Math.log10(Qm / (1000 * vm));
        }
        return LWimf_return;
    }


    /**
     * Energetic sum of dBA array
     * @param array1
     * @param array2
     * @return energetic sum of dBA array
     */
    public static double[] sumDbArray(double[] array1, double[] array2) {
        if (array1.length != array2.length) {
            throw new IllegalArgumentException("Not same size array");
        }
        double[] sum = new double[array1.length];
        for (int i = 0; i < array1.length; i++) {
            sum[i] = wToDba(dbaToW(array1[i]) + dbaToW(array2[i]));
        }
        return sum;
    }

    /**
     * Convert dB to W
     * Equivalent to dbToW
     * @param dBA
     * @return
     */
    public static double dbaToW(double dBA) {
        return Math.pow(10., dBA / 10.);
    }

    /**
     * Convert an array of dB values to W
     * @param dBA array of dB values
     * @return array of W values
     */
    public static double[] dbaToW(double[] dBA) {
        double[] ret = new double[dBA.length];
        for (int i = 0; i < dBA.length; i++) {
            ret[i] = dbaToW(dBA[i]);
        }
        return ret;
    }

    /**
     * Convert W to dB
     * Equivalent to wToDb
     * @param w
     * @return
     */
    public static double wToDba(double w) {
        return 10 * Math.log10(w);
    }

    /**
     * Convert an array of W values to dB
     * @param w array of w values
     * @return array of dB values
     */
    public static double[] wToDba(double[] w) {
        double[] ret = new double[w.length];
        for (int i = 0; i < w.length; i++) {
            ret[i] = wToDba(w[i]);
        }
        return ret;
    }

    /**
     * Energetic sum of 2 dB values
     * @param dB1 First value in dB
     * @param dB2 Second value in dB
     * @return
     */
    public static Double sumDbValues(Double dB1, Double dB2) {
        return wToDb(dbToW(dB1) + dbToW(dB2));
    }

    /**
     * Energetic sum of 5 dB values
     * @param dB1 value in dB
     * @param dB2 value in dB
     * @param dB3 value in dB
     * @param dB4 value in dB
     * @param dB5 value in dB
     * @return
     */
    public static Double sumDb5(Double dB1, Double dB2, Double dB3, Double dB4, Double dB5) {
        return wToDb(dbToW(dB1) + dbToW(dB2) + dbToW(dB3) + dbToW(dB4) + dbToW(dB5));
    }
    /**
     * trainpWperFreq
     */
    public static double[] trainLWmperFreq(double[] LWi, double nBUnit, double deltaT) throws IOException {
        double[] LW = new double[LWi.length];
        for (int idFreq = 0; idFreq < 24; idFreq++) {
            LW[idFreq] = 10*Math.log10(Math.pow(10, (LWi[idFreq] + 10 * Math.log10(nBUnit) + deltaT) / 10));
        }
        return LW;
    }

}
