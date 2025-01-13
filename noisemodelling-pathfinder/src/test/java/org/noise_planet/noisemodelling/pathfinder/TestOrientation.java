/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.math.Vector3D;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


public class TestOrientation {

    @Test
    public void testOrientationOps() {
        // North Vector
        Orientation orientationA = new Orientation(0, 0, 0);
        Orientation rotated = Orientation.fromVector(Orientation.rotate(orientationA, new Vector3D(0,1,0).normalize()), 0);
        assertEquals(0, rotated.yaw, 1e-6);
        assertEquals(0, rotated.pitch, 1e-6);


        rotated = Orientation.fromVector(Orientation.rotate(orientationA, new Vector3D(1, 0,0).normalize()), 0);
        assertEquals(90, rotated.yaw, 1e-6);
        assertEquals(0, rotated.pitch, 1e-6);

        orientationA = new Orientation(90, 0, 0);
        rotated = Orientation.fromVector(Orientation.rotate(orientationA, new Vector3D(0,1,0).normalize()), 0);
        assertEquals(90, rotated.yaw, 1e-6);
        assertEquals(0, rotated.pitch, 1e-6);

        orientationA = new Orientation(0, 45, 0);
        rotated = Orientation.fromVector(Orientation.rotate(orientationA, new Vector3D(0,1,0).normalize()), 0);
        assertEquals(0, rotated.yaw, 1e-6);
        assertEquals(45, rotated.pitch, 1e-6);

        orientationA = new Orientation(0, 0, 0);
        rotated = Orientation.fromVector(Orientation.rotate(orientationA, new Vector3D(0,1,1).normalize()), 0);
        assertEquals(0, rotated.yaw, 1e-6);
        assertEquals(45, rotated.pitch, 1e-6);

        rotated = Orientation.fromVector(Orientation.rotate(new Orientation(0, 45, 0), new Vector3D(0,1,0).normalize()), 0);
        assertEquals(0, rotated.yaw, 1e-6);
        assertEquals(45, rotated.pitch, 1e-6);

        orientationA = new Orientation(0, 0, 45);
        rotated = Orientation.fromVector(Orientation.rotate(orientationA, new Vector3D(1,0,0).normalize()), 0);
        assertEquals(90, rotated.yaw, 1e-6);
        assertEquals(45, rotated.pitch, 1e-6);
        assertEquals(0, rotated.roll, 1e-6);
    }


    @Test
    public void testOrientationTranspose() {
        Orientation orientationA = new Orientation(90, 0, 0);
        Orientation rotated = Orientation.fromVector(Orientation.rotate(orientationA, new Vector3D(1, 0, 0).normalize(), true), 0);
        assertEquals(0, rotated.yaw, 1e-6);
        assertEquals(0, rotated.pitch, 1e-6);

        orientationA = new Orientation(0, -45, 0);
        rotated = Orientation.fromVector(Orientation.rotate(orientationA, new Vector3D(0,1,0).normalize(), true), 0);
        assertEquals(0, rotated.yaw, 1e-6);
        assertEquals(45, rotated.pitch, 1e-6);

        orientationA = new Orientation(0, 0, 45);
        rotated = Orientation.fromVector(Orientation.rotate(orientationA, new Vector3D(1, 0, 1).normalize(), true), 0);
        assertEquals(90, rotated.yaw, 1e-6);
        assertEquals(0, rotated.pitch, 1e-6);
    }

    @Test
    public void testReverse1() {
        Vector3D original = new Vector3D(2, 3, 1).normalize();
        Orientation orientation = Orientation.fromVector(original, 0);
        Vector3D generated = Orientation.toVector(orientation);
        assertEquals(original.getX(), generated.getX(), 1e-6);
        assertEquals(original.getY(), generated.getY(), 1e-6);
        assertEquals(original.getZ(), generated.getZ(), 1e-6);
    }

    @Test
    public void testReverse2() {
        Vector3D original = new Vector3D(2, -3, 1).normalize();
        Orientation orientation = Orientation.fromVector(original, 0);
        Vector3D generated = Orientation.toVector(orientation);
        assertEquals(original.getX(), generated.getX(), 1e-6);
        assertEquals(original.getY(), generated.getY(), 1e-6);
        assertEquals(original.getZ(), generated.getZ(), 1e-6);
    }

    @Test
    public void testReverse3() {
        Orientation sourceOrientation = new Orientation(90, 10, 0);
        Vector3D rayDirection = new Vector3D(0.5, -0.5, 0.33).normalize();
        Vector3D rotatedVector = Orientation.rotate(sourceOrientation, rayDirection, true);
        Orientation rotated = Orientation.fromVector(rotatedVector, 0);
        Vector3D generated = Orientation.rotate(sourceOrientation, Orientation.toVector(rotated), false);
        assertEquals(rayDirection.getX(), generated.getX(), 1e-6);
        assertEquals(rayDirection.getY(), generated.getY(), 1e-6);
        assertEquals(rayDirection.getZ(), generated.getZ(), 1e-6);
    }

    @Test
    public void testRotateNoOp() {
        Vector3D rayDirection = new Vector3D(0.640762396942007, -0.640762396942007, 0.42290318198172466).normalize();
        Vector3D generated = Orientation.rotate(new Orientation(0, 0, 0), rayDirection, false);
        assertEquals(rayDirection.getX(), generated.getX(), 1e-6);
        assertEquals(rayDirection.getY(), generated.getY(), 1e-6);
        assertEquals(rayDirection.getZ(), generated.getZ(), 1e-6);
        generated = Orientation.rotate(new Orientation(0, 0, 0), rayDirection, true);
        assertEquals(rayDirection.getX(), generated.getX(), 1e-6);
        assertEquals(rayDirection.getY(), generated.getY(), 1e-6);
        assertEquals(rayDirection.getZ(), generated.getZ(), 1e-6);
    }

    @Test
    public void testMultipleRotate() {
        Vector3D rayDirection = new Vector3D(0.640762396942007, -0.640762396942007, 0.42290318198172466).normalize();
        Vector3D generated = Orientation.rotate(new Orientation(10, 0, 0), rayDirection, false);
        assertNotEquals(rayDirection.getX(), generated.getX(), 1e-6);
        assertNotEquals(rayDirection.getY(), generated.getY(), 1e-6);
        generated = Orientation.rotate(new Orientation(-10, 0, 0), generated, false);
        assertEquals(rayDirection.getX(), generated.getX(), 1e-6);
        assertEquals(rayDirection.getY(), generated.getY(), 1e-6);
        assertEquals(rayDirection.getZ(), generated.getZ(), 1e-6);
        generated = Orientation.rotate(new Orientation(0, 10, 0), generated, false);
        assertNotEquals(rayDirection.getY(), generated.getY(), 1e-6);
        assertNotEquals(rayDirection.getZ(), generated.getZ(), 1e-6);
        generated = Orientation.rotate(new Orientation(0, -10, 0), generated, false);
        assertEquals(rayDirection.getX(), generated.getX(), 1e-6);
        assertEquals(rayDirection.getY(), generated.getY(), 1e-6);
        assertEquals(rayDirection.getZ(), generated.getZ(), 1e-6);
        generated = Orientation.rotate(new Orientation(10, 10, 0), generated, false);
        assertNotEquals(rayDirection.getX(), generated.getY(), 1e-6);
        assertNotEquals(rayDirection.getY(), generated.getY(), 1e-6);
        assertNotEquals(rayDirection.getZ(), generated.getZ(), 1e-6);
        generated = Orientation.rotate(new Orientation(10, 10, 0), generated, true);
        assertEquals(rayDirection.getX(), generated.getX(), 1e-6);
        assertEquals(rayDirection.getY(), generated.getY(), 1e-6);
        assertEquals(rayDirection.getZ(), generated.getZ(), 1e-6);
    }
}
