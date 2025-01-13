/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Geometry;


public class GroundAbsorption {
    /** Ground effect area footprint. */
    final Geometry geom;
    /** Ground effect coefficient. */
    final double coef;

    /**
     * Main constructor
     * @param geom Ground effect area footprint.
     * @param coef Ground effect coefficient.
     */
    public GroundAbsorption(Geometry geom, double coef) {
        this.geom = geom;
        this.geom.normalize();
        this.coef = coef;
    }

    /**
     * Retrieve the ground effect area footprint.
     * @return The ground effect area footprint.
     */
    public Geometry getGeometry() {
        return geom;
    }

    /**
     * Retrieve the ground effect coefficient.
     * @return The ground effect coefficient.
     */
    public double getCoefficient(){
        return coef;
    }
}