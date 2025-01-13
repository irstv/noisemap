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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;

public class ProfileBuilderDecorator {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private final Scene data;

    public ProfileBuilderDecorator(ProfileBuilder profileBuilder) {
        data = new Scene(profileBuilder);
    }

    /**
     *
     * @param x
     * @param y
     * @param z
     * @return
     */
    public ProfileBuilderDecorator addSource(double x, double y, double z) {
        data.addSource(FACTORY.createPoint(new Coordinate(x, y, z)));
        return this;
    }

    /**
     *
     * @param geom
     * @return
     */
    public ProfileBuilderDecorator addSource(Geometry geom) {
        data.addSource(geom);
        return this;
    }

    /**
     *
     * @param x
     * @param y
     * @param z
     * @return
     */
    public ProfileBuilderDecorator addReceiver(double x, double y, double z) {
        data.addReceiver(new Coordinate(x, y, z));
        return this;
    }

    /**
     *
     * @param hDiff
     * @return
     */
    public ProfileBuilderDecorator vEdgeDiff(boolean hDiff) {
        data.setComputeHorizontalDiffraction(hDiff);
        return this;
    }

    /**
     *
     * @param vDiff
     * @return
     */
    public ProfileBuilderDecorator hEdgeDiff(boolean vDiff) {
        data.setComputeVerticalDiffraction(vDiff);
        return this;
    }

    /**
     *
     * @param gs
     * @return
     */
    public ProfileBuilderDecorator setGs(double gs) {
        data.setGs(gs);
        return this;
    }

    /**
     *
     * @return
     */
    public Scene build() {
        return data;
    }
}
