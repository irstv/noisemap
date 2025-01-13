/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.railway.nmpb;
/**
 * Railway noise evaluation from NMPB reference : COMMISSION DIRECTIVE (EU) 2015/996
 * of 19 May 2015 establishing common noise assessment methods according to Directive 2002/49/EC
 * of the European Parliament and of the Council
 *
 * amending, for the purposes of adapting to scientific and technical progress, Annex II to
 * Directive 2002/49/EC of the European Parliament and of the Council as regards
 * common noise assessment methods
 *
 * part 2.3. Railway noise
 *
 * Return the dB value corresponding to the parameters
 * @author Adrien Le Bellec, Université Gustave Eiffel
 * @author Olivier Chiello, Université Gustave Eiffel
 */

import org.noise_planet.noisemodelling.emission.directivity.DirectivitySphere;

import java.util.Arrays;

import static org.noise_planet.noisemodelling.emission.utils.Utils.sumDbArray;

/**
 * Data result stockage
 */
public class RailWayNMPBParameters {

    /**
     * H_INDEX of noise source type (TrainNoiseSource)
     * 0 - Low height (0.5 m) A
     * 1 - High height (4 m) B
     */
    public static final int[] TRAIN_NOISE_SOURCE_H_INDEX = new int[]{0, 0, 1, 0, 1, 0};
    public static final Integer[] DEFAULT_FREQUENCIES_THIRD_OCTAVE = new Integer[]{50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000};
    private double[] lWRolling = new double[DEFAULT_FREQUENCIES_THIRD_OCTAVE.length];
    private double[] lWTractionA = new double[DEFAULT_FREQUENCIES_THIRD_OCTAVE.length];
    ;
    private double[] lWTractionB = new double[DEFAULT_FREQUENCIES_THIRD_OCTAVE.length];
    ;
    private double[] lWAerodynamicA = new double[DEFAULT_FREQUENCIES_THIRD_OCTAVE.length];
    ;
    private double[] lWAerodynamicB = new double[DEFAULT_FREQUENCIES_THIRD_OCTAVE.length];
    ;
    private double[] lWBridge = new double[DEFAULT_FREQUENCIES_THIRD_OCTAVE.length];
    ;
    public RailWayNMPBParameters() {
        Arrays.fill(lWRolling, -99.99);
        Arrays.fill(lWTractionA, -99.99);
        Arrays.fill(lWTractionB, -99.99);
        Arrays.fill(lWAerodynamicA, -99.99);
        Arrays.fill(lWAerodynamicB, -99.99);
        Arrays.fill(lWBridge, -99.99);

        setLWRolling(lWRolling);
        setLWTractionA(lWTractionA);
        setLWTractionB(lWTractionB);
        setLWAerodynamicA(lWAerodynamicA);
        setLWAerodynamicB(lWAerodynamicB);
        setLWBridge(lWBridge);

    }
    ;


    public RailWayNMPBParameters(double[] lWRolling, double[] lWTractionA, double[] lWTractionB, double[] lWAerodynamicA, double[] lWAerodynamicB, double[] lWBridge) {
        setLWRolling(lWRolling);
        setLWTractionA(lWTractionA);
        setLWTractionB(lWTractionB);
        setLWAerodynamicA(lWAerodynamicA);
        setLWAerodynamicB(lWAerodynamicB);
        setLWBridge(lWBridge);
    }

    /**
     * Sum two train emission instances
     * @param railWayLW1 Emission 1
     * @param railWayLW2 Emission 2
     * @return Merged level
     */
    public static RailWayNMPBParameters sumRailWayLW(RailWayNMPBParameters railWayLW1, RailWayNMPBParameters railWayLW2) {
        RailWayNMPBParameters railWayLW = new RailWayNMPBParameters();

        railWayLW.setLWRolling(sumDbArray(railWayLW1.getLWRolling(), railWayLW2.getLWRolling()));
        railWayLW.setLWAerodynamicA(sumDbArray(railWayLW1.getLWAerodynamicA(), railWayLW2.getLWAerodynamicA()));
        railWayLW.setLWAerodynamicB(sumDbArray(railWayLW1.getLWAerodynamicB(), railWayLW2.getLWAerodynamicB()));
        railWayLW.setLWBridge(sumDbArray(railWayLW1.getLWBridge(), railWayLW2.getLWBridge()));
        railWayLW.setLWTractionA(sumDbArray(railWayLW1.getLWTractionA(), railWayLW2.getLWTractionA()));
        railWayLW.setLWTractionB(sumDbArray(railWayLW1.getLWTractionB(), railWayLW2.getLWTractionB()));

        return railWayLW;
    }

    /**
     * Compute the attenuation for the specified noise source and parameters
     * @param noiseSource Noise source category
     * @param phi (0 2π) 0 is front
     * @param theta (-π/2 π/2) 0 is horizontal π is top
     * @param frequency Emission frequency
     * @return Directional sound power
     */
    public static Double getDirectionAttenuation(TrainNoiseSource noiseSource, double phi, double theta, double frequency) {
        if (noiseSource == TrainNoiseSource.BRIDGE) {
            return 0.0;
        }
        theta = Math.min(Math.max(theta, -(Math.PI / 2)), Math.PI / 2);
        phi = Math.max(0, Math.min(phi, 2 * Math.PI));
        int height_index = TRAIN_NOISE_SOURCE_H_INDEX[noiseSource.ordinal()];
        double attHorizontal = 10 * Math.log10(0.01 + 0.99 * Math.pow(Math.sin(phi), 2));
        double attVertical = 0;
        if (height_index == 0) {
            if (theta > 0) {
                attVertical = (40.0 / 3.0)
                        * (2.0 / 3.0 * Math.sin(2 * theta) - Math.sin(theta))
                        * Math.log10((frequency + 600.0) / 200.0);
            }
        } else if (height_index == 1 && noiseSource == TrainNoiseSource.AERODYNAMICB) {// for aerodynamic effect only
            if (theta < 0) {
                attVertical = 10 * Math.log10(Math.pow(Math.cos(theta), 2));
            }
        }
        return attHorizontal + attVertical;
    }

    public double[] getLWRolling() {
        return lWRolling;
    }

    public void setLWRolling(double[] LWRolling) {
        this.lWRolling = LWRolling;
    }

    public double[] getLWTractionA() {
        return lWTractionA;
    }

    public void setLWTractionA(double[] LWTractionA) {
        this.lWTractionA = LWTractionA;
    }

    public double[] getLWTractionB() {
        return lWTractionB;
    }

    public void setLWTractionB(double[] LWTractionB) {
        this.lWTractionB = LWTractionB;
    }

    public double[] getLWAerodynamicA() {
        return lWAerodynamicA;
    }

    public void setLWAerodynamicA(double[] LWAerodynamicA) {
        this.lWAerodynamicA = LWAerodynamicA;
    }

    public double[] getLWAerodynamicB() {
        return lWAerodynamicB;
    }

    public void setLWAerodynamicB(double[] LWAerodynamicB) {
        this.lWAerodynamicB = LWAerodynamicB;
    }

    public double[] getLWBridge() {
        return lWBridge;
    }

    public void setLWBridge(double[] LWBridge) {
        this.lWBridge = LWBridge;
    }


    public enum TrainNoiseSource {
        ROLLING,
        TRACTIONA,
        TRACTIONB,
        AERODYNAMICA,
        AERODYNAMICB,
        BRIDGE
    }


}
