
package org.noise_planet.noisemodelling.propagation;

import org.noise_planet.noisemodelling.pathfinder.path.Scene;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Data input for a propagation Path process.
 *@author Pierre Aumond
 */
public class AttenuationParameters {

    // Thermodynamic constants
	public static final double K_0 = 273.15;	// Absolute zero in Celsius
    public static final  double Pref = 101325;	// Standard atmosphere atm (Pa)
    public static final  double Kref = 293.15;	// Reference ambient atmospheric temperature (K)
    public static final  double FmolO = 0.209;	// Mole fraction of oxygen
    public static final  double FmolN = 0.781;	// Mole fraction of nitrogen
    public static final  double KvibO = 2239.1;// Vibrational temperature of oxygen (K)
    public static final  double KvibN = 3352.0;// Vibrational temperature of the nitrogen (K)
    public static final  double K01 = 273.16;  // Isothermal temperature at the triple point (K)
    public static final double a8 = (2 * Math.PI / 35.0) * 10 * Math.log10(Math.pow(Math.exp(1),2));
    /** Frequency bands values, by third octave */
    public List<Integer> freq_lvl;
    public List<Double> freq_lvl_exact;
    public List<Double> freq_lvl_a_weighting;
    // Wind rose for each directions
    public static final double[] DEFAULT_WIND_ROSE = new double[]{0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5};
    /** Temperature in celsius */
    public double temperature = 15;
    public double celerity = 340;
    public double humidity = 70;
    public double pressure = Pref;
    public double[] alpha_atmo;
    public double defaultOccurance = 0.5;

    public boolean gDisc = true;     // choose between accept G discontinuity or not
    public boolean prime2520 = false; // choose to use prime values to compute eq. 2.5.20
    /** probability occurrence favourable condition */
    public double[] windRose  = DEFAULT_WIND_ROSE;

    public AttenuationParameters() {
        this(false);
    }


    public AttenuationParameters(boolean thirdOctave) {
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
        init();
    }

    /**
     * Copy constructor
     * @param other
     */
    public AttenuationParameters(AttenuationParameters other) {
        this.freq_lvl = other.freq_lvl;
        this.freq_lvl_exact = other.freq_lvl_exact;
        this.freq_lvl_a_weighting = other.freq_lvl_a_weighting;
        this.temperature = other.temperature;
        this.celerity = other.celerity;
        this.humidity = other.humidity;
        this.pressure = other.pressure;
        this.alpha_atmo = other.alpha_atmo;
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
    public AttenuationParameters(List<Integer> freq_lvl, List<Double> freq_lvl_exact,
                                 List<Double> freq_lvl_a_weighting) {
        this.freq_lvl = Collections.unmodifiableList(freq_lvl);
        this.freq_lvl_exact = Collections.unmodifiableList(freq_lvl_exact);
        this.freq_lvl_a_weighting = Collections.unmodifiableList(freq_lvl_a_weighting);
        init();
    }

    void init() {
        this.alpha_atmo = getAtmoCoeffArray(freq_lvl_exact,  temperature,  pressure,  humidity);
    }

    public List<Integer> getFrequencies() {
        return freq_lvl;
    }

    public void setFrequencies(List<Integer> freq_lvl) {
        this.freq_lvl = freq_lvl;
    }

    public List<Double> getFrequenciesExact() {
        return freq_lvl_exact;
    }

    public void setFrequenciesExact(List<Double> freq_lvl_exact) {
        this.freq_lvl_exact = freq_lvl_exact;
        this.alpha_atmo = getAtmoCoeffArray(freq_lvl_exact,  temperature,  pressure,  humidity);
    }

    public List<Double> getFrequenciesAWeighting() {
        return freq_lvl_a_weighting;
    }

    public void setFrequenciesAWeighting(List<Double> freq_lvl_a_weighting) {
        this.freq_lvl_a_weighting = freq_lvl_a_weighting;
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
     * Set relative humidity in percentage.
     * @param humidity relative humidity in percentage. 0-100
     */
    public AttenuationParameters setHumidity(double humidity) {

        this.humidity = humidity;
        this.alpha_atmo = getAtmoCoeffArray(freq_lvl_exact,  temperature,  pressure,  humidity);
        return this;
    }

 //   /**
   //  * @param pressure Atmospheric pressure in pa. 1 atm is PropagationProcessData.Pref
   //  */
    public AttenuationParameters setPressure(double pressure) {
        this.pressure = pressure;
        this.alpha_atmo = getAtmoCoeffArray(freq_lvl_exact,  temperature,  pressure,  humidity);
        return this;
    }

    public double[] getWindRose() {
        return windRose;
    }

    public void setWindRose(double[] windRose) {
        if(windRose.length != this.windRose.length) {
            throw new IllegalArgumentException(String.format("Wind roses length is not compatible %d!=%d",windRose.length,this.windRose.length));
        }
        this.windRose = windRose;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getCelerity() {
        return celerity;
    }

    public double getHumidity() {
        return humidity;
    }

    public double getPressure() {
        return pressure;
    }

    public boolean isPrime2520() {
        return prime2520;
    }

    public boolean isgDisc() {
        return gDisc;
    }

    public void setgDisc(boolean gDisc) {
        this.gDisc = gDisc;
    }

    /**
     * @return Default favorable probability (0-1)
     */

    public double getDefaultOccurance() {
        return defaultOccurance;
    }

    /**
     * @param defaultOccurance Default favorable probability (0-1)
     */

    public void setDefaultOccurance(double defaultOccurance) {
        this.defaultOccurance = defaultOccurance;
    }

    public AttenuationParameters setGDisc(boolean gDisc) {
        this.gDisc = gDisc;
        return this;
    }

    public AttenuationParameters setPrime2520(boolean prime2520) {
        this.prime2520 = prime2520;
        return this;
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
     * @param temperature Temperature in ° celsius
     */
    public AttenuationParameters setTemperature(double temperature) {
        this.temperature = temperature;
        this.celerity = computeCelerity(temperature + K_0);
        this.alpha_atmo = getAtmoCoeffArray(freq_lvl_exact,  temperature,  pressure,  humidity);
        return this;
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
     *
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

    public static double[] getAtmoCoeffArray(List<Double> freq_lvl, double temperature, double pressure, double humidity){
        double[] alpha_atmo;
        // Compute atmospheric alpha value by specified frequency band
        alpha_atmo = new double[freq_lvl.size()];
        for (int idfreq = 0; idfreq < freq_lvl.size(); idfreq++) {
            alpha_atmo[idfreq] = getAlpha(freq_lvl.get(idfreq), temperature, pressure, humidity);
        }
        return alpha_atmo;
    }

    /**
     * get the atmospheric attenuation coefficient in dB/km at the nominal centre frequency for each frequency band, in accordance with ISO 9613-1.
     * @return alpha_atmo
     */
    public double[] getAlpha_atmo() {
        return alpha_atmo;
    }


}
