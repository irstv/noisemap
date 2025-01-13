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
import com.fasterxml.jackson.annotation.JsonInclude;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import java.util.Collections;
import java.util.List;

public class CutPointReflection extends CutPoint {
    /**
     * x,y,z coordinates of the top segment of the wall that reflect the vertical cut plane
     * z is altitude
     */
    public LineSegment wall;

    /** Unique external identifier of the wall. Could be the primary key of the related building in the database */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long wallPk = null;

    /**
     * Empty constructor for deserialization
     */
    public CutPointReflection() {

    }

    /**
     * Constructor
     * @param cutPoint copy attributes
     * @param wall
     * @param wallAlpha
     */
    public CutPointReflection(CutPoint cutPoint, LineSegment wall, List<Double> wallAlpha) {
        super(cutPoint);
        this.wall = wall;
        this.wallAlpha = wallAlpha;
    }

    public CutPointReflection setPk(long pk) {
        this.wallPk = pk;
        return this;
    }

    /** Wall absorption coefficient per frequency band.*/
    public List<Double> wallAlpha = Collections.emptyList();


    /**
     * @return Convert alpha values to a java array
     */
    public double[] alphaAsArray() {
        return wallAlpha.stream().mapToDouble(aDouble -> aDouble).toArray();
    }

    /**
     * Sets the wall alpha.
     * @param wallAlpha The wall alpha.
     */
    public void setWallAlpha(List<Double> wallAlpha) {
        this.wallAlpha = wallAlpha;
    }

    @Override
    public String toString() {
        return "CutPointReflection{" +
                "\nwall=" + wall +
                (wallPk == null ? "" : "\n, wallPrimaryKey=" + wallPk) +
                "\n, wallAlpha=" + wallAlpha +
                "\n, coordinate=" + coordinate +
                "\n, zGround=" + zGround +
                "\n, groundCoefficient=" + groundCoefficient +
                "\n}";
    }
}
