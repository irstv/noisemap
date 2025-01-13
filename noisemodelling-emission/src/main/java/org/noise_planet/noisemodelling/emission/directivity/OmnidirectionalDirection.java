/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.directivity;


public class OmnidirectionalDirection implements DirectivitySphere {


    /**
     * Returns the attenuation in dB of the directivity pattern at a given angle (phi, theta).
     * @param frequency Frequency in Hertz
     * @param phi (0 2π) with 0 is front
     * @param theta (-π/2 π/2) with 0 is horizontal; π is top
     * @return
     */
    @Override
    public double getAttenuation(double frequency, double phi, double theta) {
        return 0;
    }

    /**
     * Returns the attenuation in dB of the directivity pattern at a given angle (phi, theta).
     * @param frequencies Frequency array in Hertz (same order will be returned)
     * @param phi (0 2π) 0 is front
     * @param theta (-π/2 π/2) 0 is horizontal π is top
     * @return Attenuation in dB for each frequency
     */
    @Override
    public double[] getAttenuationArray(double[] frequencies, double phi, double theta) {
        return new double[frequencies.length];
    }

    /**
     * Check if this sphere is capable of producing an attenuation for this frequency
     * @param frequency Frequency in Hertz
     * @return True
     */
    @Override
    public boolean coverFrequency(double frequency) {
        return true;
    }
}
