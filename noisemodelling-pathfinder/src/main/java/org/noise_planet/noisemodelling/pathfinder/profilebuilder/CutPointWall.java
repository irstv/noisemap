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

public class CutPointWall  extends CutPoint {
    /**
     * x,y,z coordinates of the top segment of the wall that intersects the vertical cut plane
     * z is altitude
     */
    public LineSegment wall;

    /** Wall absorption coefficient per frequency band.*/
    public List<Double> wallAlpha = Collections.emptyList();

    /**
     * Obstacle index in the subdomain
     * @see ProfileBuilder#processedWalls
     */
    @JsonIgnore
    public int processedWallIndex = -1;

    /** This point encounter this kind of limit
     * - We can enter or exit a polygon
     * - pass a line (a wall without width) */
    public enum INTERSECTION_TYPE {BUILDING_ENTER, BUILDING_EXIT, THIN_WALL_ENTER_EXIT}

    public INTERSECTION_TYPE intersectionType = INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT;

    /** Database primary key value of the obstacle */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long wallPk = null;

    /**
     * Empty constructor for deserialization
     */
    public CutPointWall() {
    }

    public CutPointWall(int processedWallIndex, Coordinate intersection, LineSegment wallSegment, List<Double> wallAlpha) {
        this.wall = wallSegment;
        this.coordinate = intersection;
        this.processedWallIndex = processedWallIndex;
        this.wallAlpha = wallAlpha;
    }

    /**
     *
     * @param pk External primary key value, will be updated if >= 0
     * @return this
     */
    public CutPointWall setPk(long pk) {
        if(pk >= 0) {
            this.wallPk = pk;
        }
        return this;
    }

    /**
     * @return Convert alpha values to a java array
     */
    public double[] alphaAsArray() {
        return wallAlpha.stream().mapToDouble(aDouble -> aDouble).toArray();
    }

    @Override
    public String toString() {
        return "CutPointWall{" +
                "groundCoefficient=" + groundCoefficient +
                ", zGround=" + zGround +
                ", coordinate=" + coordinate +
                ", processedWallIndex=" + processedWallIndex +
                ", wallAlpha=" + wallAlpha +
                ", wall=" + wall +
                '}';
    }
}
