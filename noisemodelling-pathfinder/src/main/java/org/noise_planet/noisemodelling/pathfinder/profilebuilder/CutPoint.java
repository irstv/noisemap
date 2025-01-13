/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiver;

import java.util.List;

/**
 * On the vertical cut profile, this is one of the point
 * This abstract class is implemented with specific attributes depending on the intersection object
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CutPointSource.class, name = "Source"),
        @JsonSubTypes.Type(value = CutPointReceiver.class, name = "Receiver"),
        @JsonSubTypes.Type(value = CutPointWall.class, name = "Wall"),
        @JsonSubTypes.Type(value = CutPointReflection.class, name = "Reflection"),
        @JsonSubTypes.Type(value = CutPointGroundEffect.class, name = "GroundEffect"),
        @JsonSubTypes.Type(value = CutPointTopography.class, name = "Topography"),
        @JsonSubTypes.Type(value = CutPointVEdgeDiffraction.class, name = "VEdgeDiffraction")
})
public abstract class CutPoint implements Comparable<CutPoint> {
    /** {@link Coordinate} of the cut point. */
    public Coordinate coordinate = new Coordinate();

    /** Topographic height of the point. */
    public double zGround = Double.NaN;

    /**
     * Ground effect coefficient.
     * G=1.0 Soft, uncompacted ground (pasture, loose soil); snow etc
     * G=0.7 Compacted soft ground (lawns, park areas):
     * G=0.3 Compacted dense ground (gravel road, compacted soil):
     * G=0.0 Hard surfaces (asphalt, concrete, top of buildings):
     **/
    public double groundCoefficient = Double.NaN;

    public CutPoint() {
    }

    public CutPoint(Coordinate coordinate) {
        this.coordinate = coordinate;
    }

    /**
     * Copy constructor
     * @param other Other instance to copy
     */
    @SuppressWarnings("IncompleteCopyConstructor")
    public CutPoint(CutPoint other) {
        this.coordinate = other.coordinate.copy();
        this.zGround = other.zGround;
        this.groundCoefficient = other.groundCoefficient;
    }

    public void setCoordinate(Coordinate coordinate) {
        this.coordinate = coordinate;
    }

    /**
     * Sets the ground coefficient of this point.
     * @param groundCoefficient The ground coefficient of this point.
     */
    public void setGroundCoefficient(double groundCoefficient) {
        this.groundCoefficient = groundCoefficient;
    }

    /**
     * Sets the topographic height.
     * @param zGround The topographic height.
     */
    public void setZGround(double zGround) {
        this.zGround = zGround;
    }


    /**
     * Retrieve the coordinate of the point.
     * @return The coordinate of the point.
     */
    public Coordinate getCoordinate(){
        return coordinate;
    }

    /**
     * Retrieve the ground effect coefficient of the point. If there is no coefficient, returns 0.
     * @return Ground effect coefficient or NaN.
     */
    public double getGroundCoefficient() {
        return groundCoefficient;
    }

    /**
     * Retrieve the topographic height of the point.
     * @return The topographic height of the point.
     */
    public Double getzGround() {
        return zGround;
    }

    /**
     *
     * @param cutPoint the object to be compared.
     * @return
     */
    @Override
    public int compareTo(CutPoint cutPoint) {
        return this.coordinate.compareTo(cutPoint.coordinate);
    }

    @Override
    public String toString() {
        return "CutPoint{" +
                "coordinate=" + coordinate +
                ", zGround=" + zGround +
                ", groundCoefficient=" + groundCoefficient +
                '}';
    }
}