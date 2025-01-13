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
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Double.isNaN;


public final class ElevationFilter implements CoordinateSequenceFilter {
    AtomicBoolean geometryChanged = new AtomicBoolean(false);
    ProfileBuilder profileBuilder;
    boolean resetZ;
    /**
     * Keep track of DEM triangle index to fetch less often the rtree
     */
    AtomicInteger triangleHint = new AtomicInteger(-1);

    /**
     * Constructor
     *
     * @param profileBuilder Initialised instance of profileBuilder
     * @param resetZ              If filtered geometry contain Z and resetZ is false, do not update Z.
     */
    public ElevationFilter(ProfileBuilder profileBuilder, boolean resetZ) {
        this.profileBuilder = profileBuilder;
        this.resetZ = resetZ;
    }

    public void reset() {
        geometryChanged.set(false);
    }


    /**
     *
     * @param coordinateSequence  the <code>CoordinateSequence</code> to which the filter is applied
     * @param i the index of the coordinate to apply the filter to
     */
    @Override
    public void filter(CoordinateSequence coordinateSequence, int i) {
        Coordinate pt = coordinateSequence.getCoordinate(i);
        double zGround = profileBuilder.getZGround(pt, triangleHint);
        if (!isNaN(zGround) && (resetZ || isNaN(pt.getOrdinate(2)) || 0 ==  pt.getOrdinate(2))) {
            pt.setOrdinate(2, zGround + (isNaN(pt.getOrdinate(2)) ? 0 : pt.getOrdinate(2)));
            geometryChanged.set(true);
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean isGeometryChanged() {
        return geometryChanged.get();
    }

    public static class UpdateZ implements CoordinateSequenceFilter {

        boolean done = false;
        final double z;

        public UpdateZ(double z) {
            this.z = z;
        }

        @Override
        public boolean isGeometryChanged() {
            return true;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public void filter(CoordinateSequence seq, int i) {

            seq.setOrdinate(i, 2, z);

            if (i == seq.size()) {
                done = true;
            }
        }
    }
}