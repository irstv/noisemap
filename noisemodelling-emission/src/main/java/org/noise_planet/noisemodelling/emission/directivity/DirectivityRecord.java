/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */


package org.noise_planet.noisemodelling.emission.directivity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;


public class DirectivityRecord {
    double theta;
    double phi;
    double[] attenuation;

    /**
     * directivity record is the attenuation value for a specific angle (theta, phi) - a point of the directivity sphere
     *
     * @param theta (-π/2 π/2) 0 is horizontal; π is top
     * @param phi (0 2π) 0 is front
     * @param attenuation in dB
     */
    public DirectivityRecord(double theta, double phi, double[] attenuation) {
        this.theta = theta;
        this.phi = phi;
        this.attenuation = attenuation;
    }

    /**
     *
     * @return
     */
    public double getTheta() {
        return theta;
    }

    public double getPhi() {
        return phi;
    }

    /**
     * compare the values of theta et phi of the Object DirectivityRecord
     * @param o object
     * @return a boolean
     */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectivityRecord record = (DirectivityRecord) o;
        return Double.compare(record.theta, theta) == 0 &&
                Double.compare(record.phi, phi) == 0;
    }


    /**
     * generate a hash code for an object with theta and phi argument
     * @return
     */
    @Override
    public int hashCode() {
        return Objects.hash(theta, phi);
    }

    /**
     * generate a string representation of the object DirectivityRecord
     * @return a string
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "DirectivityRecord{theta=%.2f (%.2g°)" +
                        ", phi=%.2f (%.2g°) , attenuation=%s}", theta, Math.toDegrees(theta), phi, Math.toDegrees(phi),
                Arrays.toString(attenuation));
    }

    public double[] getAttenuation() {
        return attenuation;
    }
}