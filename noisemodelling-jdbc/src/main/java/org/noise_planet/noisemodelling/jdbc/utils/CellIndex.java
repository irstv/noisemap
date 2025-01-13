/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */


package org.noise_planet.noisemodelling.jdbc.utils;

import java.util.Objects;


public class CellIndex implements Comparable<CellIndex> {
    int longitudeIndex;
    int latitudeIndex;

    public CellIndex(int longitudeIndex, int latitudeIndex) {
        this.longitudeIndex = longitudeIndex;
        this.latitudeIndex = latitudeIndex;
    }

    @Override
    public String toString() {
        return String.format("CellIndex(%d, %d);", longitudeIndex, latitudeIndex);
    }

    public int getLongitudeIndex() {
        return longitudeIndex;
    }

    public int getLatitudeIndex() {
        return latitudeIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CellIndex cellIndex = (CellIndex) o;
        return longitudeIndex == cellIndex.longitudeIndex && latitudeIndex == cellIndex.latitudeIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(longitudeIndex, latitudeIndex);
    }

    /**
     * Compare latitudeIndex values of two instances of CellIndex
     * @param o the object to be compared.
     * @return
     */
    @Override
    public int compareTo(CellIndex o) {
        int comp = Integer.compare(latitudeIndex, o.latitudeIndex);
        if(comp != 0) {
            return comp;
        } else {
            return Integer.compare(longitudeIndex, o.longitudeIndex);
        }
    }
}
