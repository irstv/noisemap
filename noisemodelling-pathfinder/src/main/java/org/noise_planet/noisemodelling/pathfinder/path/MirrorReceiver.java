/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.path;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Wall;

import java.util.Objects;

/**
 *  Information for Receiver image.
 * @author Nicolas Fortin
 */
public class MirrorReceiver {


    public Coordinate receiverPos;
    public Coordinate reflectionPosition = new Coordinate(Coordinate.NULL_ORDINATE, Coordinate.NULL_ORDINATE, Coordinate.NULL_ORDINATE);
    public final MirrorReceiver parentMirror;
    public final Wall wall;
    /**
     * This data is not stored in the RTREE as it is not used after the creation of the index
     */
    Polygon imageReceiverVisibilityCone;

    /**
     * @return coordinate of mirrored receiver
     */
    public Coordinate getReceiverPos() {
        return receiverPos;
    }

    public void setReceiverPos(Coordinate receiverPos) {
        this.receiverPos = receiverPos;
    }

    /**
     * @return The coordinate of the reflexion of the ray on the mirror receiver. To be known the source point must have been defined
     */
    public Coordinate getReflectionPosition() {
        return reflectionPosition;
    }

    /**
     * @param reflectionPosition The coordinate of the reflexion of the ray on the mirror receiver. To be known the source point must have been defined
     */
    public void setReflectionPosition(Coordinate reflectionPosition) {
        this.reflectionPosition = reflectionPosition;
    }

    public MirrorReceiver copyWithoutCone() {
        return new MirrorReceiver(receiverPos, parentMirror == null ? null : parentMirror.copyWithoutCone(),
                wall);
    }
    /**
     * @return Other MirrorReceiver index, -1 for the first reflexion
     */
    public MirrorReceiver getParentMirror() {
        return parentMirror;
    }

    /**
     * @return Wall index of the last mirrored processed
     */
    public Wall getWall() {
        return wall;
    }

    /**
     * @param receiverPos coordinate of mirrored receiver
     * @param parentMirror Parent receiver, null for the first reflexion
     * @param wallId Wall index of the last mirrored processed
     * @param buildingId building that belongs to this wall
     */
    public MirrorReceiver(Coordinate receiverPos, MirrorReceiver parentMirror, Wall wall) {
        this.receiverPos = receiverPos;
        this.parentMirror = parentMirror;
        this.wall = wall;
    }

    public Polygon getImageReceiverVisibilityCone() {
        return imageReceiverVisibilityCone;
    }

    public void setImageReceiverVisibilityCone(Polygon imageReceiverVisibilityCone) {
        this.imageReceiverVisibilityCone = imageReceiverVisibilityCone;
    }

    /**
     * Copy constructor
     * @param cpy ref
     */
    public MirrorReceiver(MirrorReceiver cpy) {
        this.receiverPos = new Coordinate(cpy.receiverPos);
        this.parentMirror = cpy.parentMirror;
        this.wall = cpy.wall;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MirrorReceiver that = (MirrorReceiver) o;
        return Objects.equals(receiverPos, that.receiverPos) && Objects.equals(parentMirror, that.parentMirror)
                && Objects.equals(wall, that.wall) &&
                Objects.equals(imageReceiverVisibilityCone, that.imageReceiverVisibilityCone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiverPos, parentMirror, wall, imageReceiverVisibilityCone);
    }

    public ProfileBuilder.IntersectionType getType() {
        return wall.getType();
    }
}
