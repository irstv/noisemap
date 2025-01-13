/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */


package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;

import java.util.Comparator;

public class CutPointDistanceComparator implements Comparator<CutPoint> {
    private final Coordinate reference;

    public CutPointDistanceComparator(Coordinate reference) {
        this.reference = reference;
    }

    @Override
    public int compare(CutPoint o1, CutPoint o2) {
        return Double.compare(o1.coordinate.distance(reference), o2.coordinate.distance(reference));
    }
}
