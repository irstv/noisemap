/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.locationtech.jts.geom.Coordinate;

public class CutPointGroundEffect extends CutPoint {

    /**
     * Obstacle index in the subdomain
     * @see ProfileBuilder#processedWalls
     */
    @JsonIgnore
    public int processedWallIndex = -1;

    public CutPointGroundEffect(int processedWallIndex, Coordinate intersectionCoordinate, double groundAbsorptionCoefficient) {
        super(intersectionCoordinate);
        this.groundCoefficient = groundAbsorptionCoefficient;
        this.processedWallIndex = processedWallIndex;
    }

    /**
     * Empty constructor for deserialization
     */
    public CutPointGroundEffect() {
    }

    @Override
    public String toString() {
        return "CutPointGroundEffect{" +
                "groundCoefficient=" + groundCoefficient +
                ", zGround=" + zGround +
                ", coordinate=" + coordinate +
                ", processedWallIndex=" + processedWallIndex +
                '}';
    }
}
