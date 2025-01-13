/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder.utils.geometry;


import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.locationtech.jts.math.Vector3D;

import java.util.Objects;

/**
 * When providing Orientation to a sound source there is 2 cases
 * - Sound source is a point. The final orientation is the same, relative to the north and clock-wise to east.
 * - Sound source is a line. The final orientation is rotated by the line direction.
 * So 0 degrees point the end of the line segment.
 */
public class Orientation {
    public double yaw;
    public double pitch;
    public double roll;

    /**
     * Default constructor
     */
    public Orientation() {
    }

    /**
     * @param yaw     Orientation using degrees east of true north (0 is north, 90 is east)
     * @param pitch Vertical orientation in degrees. (0 flat, 90 vertical top, -90 vertical bottom)
     * @param roll        Longitudinal axis in degrees. A positive value lifts the left wing and lowers the right wing.
     */
    public Orientation(double yaw, double pitch, double roll) {
        this.yaw = (360 + yaw) % 360;
        this.pitch = Math.min(90, Math.max(-90, pitch));
        this.roll = (360 + roll) % 360;
    }

    @Override
    public String toString() {
        return "Orientation("+yaw+","+pitch+","+roll+")";
    }

    /**
     * Rotate the vector by the provided orientation and return the result vector orientation
     * @param orientation Rotation to apply
     * @param vector Vector to rotate
     * @return New vector orientation
     */
    public static Vector3D rotate(Orientation orientation, Vector3D vector) {
        return rotate(orientation, vector, false);
    }

    /**
     * Rotate the vector by the provided orientation and return the result vector orientation
     * @param orientation Rotation to apply
     * @param vector Vector to rotate
     * @param inverse True to inverse rotation
     * @return New vector orientation
     */
    public static Vector3D rotate(Orientation orientation, Vector3D vector, boolean inverse) {
        // Coordinate system of the orientation is Y+ North X+ East
        // Y+ must be yaw = 0
        // X+ must be yaw = 90
        double[] b = new double[]{vector.getY(), vector.getX(), vector.getZ()};
        final double yaw = Math.toRadians(orientation.yaw);
        final double pitch = Math.toRadians(orientation.pitch);
        final double roll = Math.toRadians(orientation.roll);
        final double c1 = Math.cos(yaw);
        final double s1 = Math.sin(yaw);
        final double c2 = Math.cos(- pitch);
        final double s2 = Math.sin(- pitch);
        final double c3 = Math.cos(roll);
        final double s3 = Math.sin(roll);
        // https://en.wikipedia.org/wiki/Euler_angles#Rotation_matrix
        double[][] a = new double[][]{
                {c1 * c2, c1 * s2 * s3 - s1 * c3, c1 * s2 * c3 + s1 * s3},
                {s1 * c2, s1 * s2 * s3 + c1 * c3, s1 * s2 * c3 - c1 * s3},
                {-s2, c2 * s3, c2 * c3}
        };
        RealMatrix matrixA = new Array2DRowRealMatrix(a);
        if(inverse) {
            matrixA = matrixA.transpose();
        }
        RealMatrix matrixB = new Array2DRowRealMatrix(b);
        RealMatrix res = matrixA.multiply(matrixB);
        return new Vector3D(res.getEntry(1, 0),
                res.getEntry(0, 0),
                res.getEntry(2, 0));
    }


    /**
     *
     * @param vector
     * @param roll
     * @return
     */
    public static Orientation fromVector(Vector3D vector, double roll) {
        double newYaw = Math.atan2(vector.getX(), vector.getY());
        double newPitch = Math.asin(vector.getZ());
        return new Orientation(Math.toDegrees(newYaw), Math.toDegrees(newPitch), roll);
    }


    /**
     *
     * @param orientation
     * @return
     */
    public static Vector3D toVector(Orientation orientation) {
        return rotate(orientation, new Vector3D(0, 1, 0));
    }


    /**
     * Compare two orientations
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Orientation that = (Orientation) o;
        return Double.compare(that.yaw, yaw) == 0 &&
                Double.compare(that.pitch, pitch) == 0 &&
                Double.compare(that.roll, roll) == 0;
    }


    /**
     *
     * @return
     */
    @Override
    public int hashCode() {
        return Objects.hash(yaw, pitch, roll);
    }
}
