/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;

/**
 * A rupture in the topographic profile
 */
public class CutPointTopography extends CutPoint {

    /**
     * Empty constructor for deserialization
     */
    public CutPointTopography() {

    }

    public CutPointTopography(Coordinate coordinate) {
        super(coordinate);
        this.zGround = coordinate.z;
    }

    @Override
    public String toString() {
        return "CutPointTopography{" +
                "groundCoefficient=" + groundCoefficient +
                ", zGround=" + zGround +
                ", coordinate=" + coordinate +
                '}';
    }
}
