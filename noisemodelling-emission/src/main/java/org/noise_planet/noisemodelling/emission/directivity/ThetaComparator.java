/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.emission.directivity;

import java.io.Serializable;
import java.util.Comparator;

public  class ThetaComparator implements Comparator<DirectivityRecord>, Serializable {

    /**
     * Compare two directivity record
     * @param o1 the first object to be compared.
     * @param o2 the second object to be compared.
     * @return 1 or 0 or -1
     */
    @Override
    public int compare(DirectivityRecord o1, DirectivityRecord o2) {
        final int thetaCompare = Double.compare(o1.theta, o2.theta);
        if (thetaCompare != 0) {
            return thetaCompare;
        }
        return Double.compare(o1.phi, o2.phi);
    }

}