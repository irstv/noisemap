/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.propagation.AttenuationParameters;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Data input for a propagation Path process.
 *@author Pierre Aumond
 */
public class AttenuationCnossosParameters extends AttenuationParameters {

    // Thermodynamic constants
    // Wind rose for each directions
    public static final double[] DEFAULT_WIND_ROSE = new double[]{0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5};
    private static final double angle_section = (2 * Math.PI) / DEFAULT_WIND_ROSE.length;
    /** Temperature in celsius */
    private double defaultOccurance = 0.5;

    private boolean gDisc = true;     // choose between accept G discontinuity or not
    private boolean prime2520 = false; // choose to use prime values to compute eq. 2.5.20
    /** probability occurrence favourable condition */
    private double[] windRose  = DEFAULT_WIND_ROSE;

    public AttenuationCnossosParameters() {
        this(false);
    }


    public AttenuationCnossosParameters(boolean thirdOctave) {
        if(!thirdOctave) {
            // Default frequencies are in octave bands
            freq_lvl = Arrays.asList(asOctaveBands(Scene.DEFAULT_FREQUENCIES_THIRD_OCTAVE));
            freq_lvl_exact = Arrays.asList(asOctaveBands(Scene.DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE));
            freq_lvl_a_weighting = Arrays.asList(asOctaveBands(Scene.DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE));
        } else {
            // third octave bands
            freq_lvl = Arrays.asList(Scene.DEFAULT_FREQUENCIES_THIRD_OCTAVE);
            freq_lvl_exact = Arrays.asList(Scene.DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE);
            freq_lvl_a_weighting = Arrays.asList(Scene.DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE);
        }
        //init();
    }

    /**
     * Copy constructor
     * @param other
     */
    public AttenuationCnossosParameters(AttenuationCnossosParameters other) {
        this.freq_lvl = other.freq_lvl;
        this.freq_lvl_exact = other.freq_lvl_exact;
        this.freq_lvl_a_weighting = other.freq_lvl_a_weighting;
        this.defaultOccurance = other.defaultOccurance;
        this.gDisc = other.gDisc;
        this.prime2520 = other.prime2520;
        this.windRose = other.windRose;
    }

    /**
     * @param freq_lvl Frequency values for column names
     * @param freq_lvl_exact Exact frequency values for computations
     * @param freq_lvl_a_weighting A weighting values
     */
    public AttenuationCnossosParameters(List<Integer> freq_lvl, List<Double> freq_lvl_exact,
                                        List<Double> freq_lvl_a_weighting) {
        this.freq_lvl = Collections.unmodifiableList(freq_lvl);
        this.freq_lvl_exact = Collections.unmodifiableList(freq_lvl_exact);
        this.freq_lvl_a_weighting = Collections.unmodifiableList(freq_lvl_a_weighting);
    }

    /**
     * get the rose index to search the mean occurrence p of favourable conditions in the direction of the path (S,R):
     * @param receiver
     * @param source
     * @return rose index
     */
    public static int getRoseIndex(Coordinate receiver, Coordinate source) {
        return getRoseIndex(Angle.angle(receiver, source));
    }

    /**
     * The north slice is the last array index not the first one
     * Ex for slice width of 20°:
     *      - The first column 20° contain winds between 10 to 30 °
     *      - The last column 360° contains winds between 350° to 360° and 0 to 10°
     * get the rose index to search the mean occurrence p of favourable conditions in the direction of the angle:
     * @return rose index
     */
    public static int getRoseIndex(double angle) {
        // Angle from cos -1 sin 0
        double angleRad = -(angle - Math.PI);
        // Offset angle by PI / 2 (North),
        // the north slice ranges is [PI / 2 + angle_section / 2; PI / 2 - angle_section / 2]
        angleRad -= (Math.PI / 2 - angle_section / 2);
        // Fix out of bounds angle 0-2Pi
        if(angleRad < 0) {
            angleRad += Math.PI * 2;
        }
        int index = (int)(angleRad / angle_section) - 1;
        if(index < 0) {
            index = DEFAULT_WIND_ROSE.length - 1;
        }
        return index;
    }

    /**
     * Create new array by taking middle third octave bands
     *
     * @param thirdOctaveBands Third octave bands array
     * @return Octave bands array
     */
    public static Integer[] asOctaveBands(Integer[] thirdOctaveBands) {
        Integer[] octaveBands = new Integer[thirdOctaveBands.length / 3];
        int j = 0;
        for (int i = 1; i < thirdOctaveBands.length - 1; i += 3) {
            octaveBands[j++] = thirdOctaveBands[i];
        }
        return octaveBands;
    }

    /**
     * Create new array by taking middle third octave bands
     *
     * @param thirdOctaveBands Third octave bands array
     * @return Octave bands array
     */
    public static Double[] asOctaveBands(Double[] thirdOctaveBands) {
        Double[] octaveBands = new Double[thirdOctaveBands.length / 3];
        int j = 0;
        for (int i = 1; i < thirdOctaveBands.length - 1; i += 3) {
            octaveBands[j++] = thirdOctaveBands[i];
        }
        return octaveBands;
    }

    /**
     * Compute sound celerity in air ISO 9613-1:1993(F)
     * @param k Temperature in kelvin
     * @return Sound celerity in m/s
     */
    static double computeCelerity(double k) {
        return 343.2 * Math.sqrt(k/Kref);
    }

    /**
     *
     * @param freq Frequency (Hz)
     * @param humidity Humidity %
     * @param pressure Pressure in pascal
     * @param T_kel Temperature in kelvin
     * @return Atmospheric absorption dB/km
     */
    public static double getCoefAttAtmosCnossos(double freq, double humidity, double pressure, double T_kel) {
        double tcor = T_kel/ Kref ;
        double xmol = humidity * Math.pow (10., 4.6151 - 6.8346 * Math.pow (K01 / T_kel, 1.261));

        double frqO = 24. + 40400. * xmol * ((.02 + xmol) / (0.391 + xmol)) ;
        double frqN = Math.pow (tcor,-0.5) * (9. + 280. * xmol * Math.exp (-4.17 * (Math.pow (tcor,-1./3.) - 1.))) ;


        double a1 = 0.01275 * Math.exp (-2239.1 / T_kel) / (frqO + (freq * freq / frqO)) ;
        double a2 = 0.10680 * Math.exp (-3352.0 / T_kel) / (frqN + (freq * freq / frqN)) ;
        double a0 = 8.686 * freq * freq
                * (1.84e-11 * Math.pow(tcor,0.5) + Math.pow(tcor,-2.5) * (a1 + a2)) ;

        return a0 * 1000;
    }

    /**
     * Compute AAtm
     * @param frequency Frequency (Hz)
     * @param humidity Humidity %
     * @param pressure Pressure in pascal
     * @param T_kel Temperature in kelvin
     * @return Atmospheric absorption dB/km
     */
    public static double getCoefAttAtmos(double frequency, double humidity, double pressure, double T_kel) {

        final double Kelvin = 273.15;	//For converting to Kelvin
        final double e = 2.718282;

        double T_ref = Kelvin + 20;     //Reference temp = 20 degC
        double T_rel = T_kel / T_ref;   //Relative temp
        double T_01 = Kelvin + 0.01;    //Triple point isotherm temperature (Kelvin)
        double P_ref = 101.325;         //Reference atmospheric P = 101.325 kPa
        double P_rel = (pressure / 1e3) / P_ref;       //Relative pressure

        //Get Molecular Concentration of water vapour
        double P_sat_over_P_ref = Math.pow(10,((-6.8346 * Math.pow((T_01 / T_kel), 1.261)) + 4.6151));
        double H = humidity * (P_sat_over_P_ref/P_rel); 		// h from ISO 9613-1, Annex B, B.1

        //fro from ISO 9613-1, 6.2, eq.3
        double Fro = P_rel * (24 + 40400 * H * (0.02 + H) / (0.391 + H));

        //frn from ISO 9613-1, 6.2, eq.4
        double Frn = P_rel / Math.sqrt(T_rel) * (9 + 280 * H * Math.pow(e,(-4.17 * (Math.pow(T_rel,(-1.0/3.0)) - 1))));

        //xc, xo and xn from ISO 9613-1, 6.2, part of eq.5
        double Xc = 0.0000000000184 / P_rel * Math.sqrt(T_rel);
        double Xo = 0.01275 * Math.pow(e,(-2239.1 / T_kel)) * Math.pow((Fro + (frequency*frequency / Fro)), -1);
        double Xn = 0.1068 * Math.pow(e,(-3352.0 / T_kel)) * Math.pow((Frn + (frequency*frequency / Frn)), -1);

        //alpha from ISO 9613-1, 6.2, eq.5
        double Alpha = 20 * Math.log10(e) * frequency * frequency * (Xc + Math.pow(T_rel,(-5.0/2.0)) * (Xo + Xn));

        return Alpha * 1000;
    }

    /**
     * This function calculates the atmospheric attenuation coefficient of sound in air
     * ISO 9613-1:1993(F)
     * @param frequency acoustic frequency (Hz)
     * @param humidity relative humidity (in %) (0-100)
     * @param pressure atmospheric pressure (in Pa)
     * @param tempKelvin Temperature in Kelvin (in K)
     * @return atmospheric attenuation coefficient (db/km)
     * @author Judicaël Picaut, UMRAE
     */
    public static double getCoefAttAtmosSpps(double frequency, double humidity, double pressure, double tempKelvin) {
        // Sound celerity
        double cson = computeCelerity(tempKelvin);

        // Calculation of the molar fraction of water vapour
        double C = -6.8346 * Math.pow(K01 / tempKelvin, 1.261) + 4.6151;
        double Ps = Pref * Math.pow(10., C);
        double hmol = humidity * (Ps / Pref) * (pressure / Pref);

        // Classic and rotational absorption
        double Acr = (Pref / pressure) * (1.60E-10) * Math.sqrt(tempKelvin / Kref) * Math.pow(frequency, 2);

        // Vibratory oxygen absorption:!!123
        double Fr = (pressure / Pref) * (24. + 4.04E4 * hmol * (0.02 + hmol) / (0.391 + hmol));
        double Am = a8 * FmolO * Math.exp(-KvibO / tempKelvin) * Math.pow(KvibO / tempKelvin, 2);
        double AvibO = Am * (frequency / cson) * 2. * (frequency / Fr) / (1 + Math.pow(frequency / Fr, 2));

        // Vibratory nitrogen absorption
        Fr = (pressure / Pref) * Math.sqrt(Kref / tempKelvin) * (9. + 280. * hmol * Math.exp(-4.170 * (Math.pow(tempKelvin / Kref, -1. / 3.) - 1)));
        Am = a8 * FmolN * Math.exp(-KvibN / tempKelvin) * Math.pow(KvibN / tempKelvin, 2);
        double AvibN = Am * (frequency / cson) * 2. * (frequency / Fr) / (1 + Math.pow(frequency / Fr, 2));

        // Total absorption in dB/m
        double alpha = (Acr + AvibO + AvibN);

        return alpha * 1000;
    }

    /**
     * ISO-9613 p1
     * @param frequency acoustic frequency (Hz)
     * @param temperature Temperative in celsius
     * @param pressure atmospheric pressure (in Pa)
     * @param humidity relative humidity (in %) (0-100)
     * @return Attenuation coefficient dB/KM
     */
    public static double getAlpha(double frequency, double temperature, double pressure, double humidity) {
        return getCoefAttAtmos(frequency, humidity, pressure, temperature + K_0);
    }
}
