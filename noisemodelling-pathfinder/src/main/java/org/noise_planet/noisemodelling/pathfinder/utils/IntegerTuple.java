package org.noise_planet.noisemodelling.pathfinder.utils;

import java.util.Objects;


public class IntegerTuple {
    int nodeIndexA;
    int nodeIndexB;
    int triangleIdentifier;


    /**
     * Compare two instance of IntegerTuple
     * @param o
     * @return a boolean
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntegerTuple that = (IntegerTuple) o;
        return nodeIndexA == that.nodeIndexA && nodeIndexB == that.nodeIndexB;
    }


    /**
     *
     * @return
     */
    @Override
    public String toString() {
        return "IntegerTuple{" + "nodeIndexA=" + nodeIndexA + ", nodeIndexB=" + nodeIndexB + ", " +
                "triangleIdentifier=" + triangleIdentifier + '}';
    }


    /**
     *
     * @return
     */
    @Override
    public int hashCode() {
        return Objects.hash(nodeIndexA, nodeIndexB);
    }

    /**
     * Create the constructor of IntegerTuple
     * @param nodeIndexA
     * @param nodeIndexB
     * @param triangleIdentifier
     */
    public IntegerTuple(int nodeIndexA, int nodeIndexB, int triangleIdentifier) {
        if(nodeIndexA < nodeIndexB) {
            this.nodeIndexA = nodeIndexA;
            this.nodeIndexB = nodeIndexB;
        } else {
            this.nodeIndexA = nodeIndexB;
            this.nodeIndexB = nodeIndexA;
        }
        this.triangleIdentifier = triangleIdentifier;
    }
}