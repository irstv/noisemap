/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

/**
 * Pivot point on the vertical profile. It is near a vertical edge of a wall.
 */
public class CutPointVEdgeDiffraction  extends CutPoint {

    /**
     * Empty constructor for deserialization
     */
    public CutPointVEdgeDiffraction() {
    }

    public CutPointVEdgeDiffraction(CutPoint source) {
        super(source);
    }

    @Override
    public String toString() {
        return "CutPointVEdgeDiffraction{" +
                ", coordinate=" + coordinate +
                ", zGround=" + zGround +
                ", groundCoefficient=" + groundCoefficient +
                '}';
    }
}
