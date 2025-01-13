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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author SU Qi
 */
public class Test3DPropagation {

    @Test
    public void testChangePlan() {
        GeometryFactory factory = new GeometryFactory();
        List<Coordinate> coords = JTSUtility.getNewCoordinateSystem(Arrays.asList(new Coordinate(5, 5, 5), new Coordinate(10, 5, 6)));
        List<Coordinate> coordsInv = JTSUtility.getNewCoordinateSystem(Arrays.asList(new Coordinate(10, 5, 6), new Coordinate(5, 5, 5)));
        assertEquals(coords.get(0).y, coordsInv.get(1).y);
        assertEquals(factory.createLineString(coords.toArray(new Coordinate[coords.size()])).getLength(),
                factory.createLineString(coordsInv.toArray(new Coordinate[coordsInv.size()])).getLength(), 1e-12);
        coords = JTSUtility.getNewCoordinateSystem(Arrays.asList(new Coordinate(5, 5, 5), new Coordinate(6, 5, 5.5), new Coordinate(10, 5, 6)));
        coordsInv = JTSUtility.getNewCoordinateSystem(Arrays.asList(new Coordinate(10, 5, 6), new Coordinate(6, 5, 5.5), new Coordinate(5, 5, 5)));
        assertEquals(factory.createLineString(coords.toArray(new Coordinate[coords.size()])).getLength(),
                factory.createLineString(coordsInv.toArray(new Coordinate[coordsInv.size()])).getLength(), 1e-12);
    }

    @Test
    public void testChangePlan2() {
        Coordinate[] pts = new Coordinate[] { new Coordinate(38.0, 14.0, 1.0),
                new Coordinate(56.78816787229409, 17.25389284165093, 2.857552975310886),
                new Coordinate(60.30669482663231, 17.8632609156269, 5.0),
                new Coordinate(61.01835202171596, 17.986511690717474, 5.0),
                new Coordinate(61.62431382421983, 18.09145724926706, 5.0),
                new Coordinate(66.03640021193571, 18.85557945699466, 2.340442869150613),
                new Coordinate(107.0, 25.95, 4.0)};
        List<Coordinate> coords = JTSUtility.getNewCoordinateSystem(Arrays.asList(pts));
        assertEquals(pts[0].distance3D(pts[2]), coords.get(0).distance(coords.get(2)), 1e-6);
    }
    
}
