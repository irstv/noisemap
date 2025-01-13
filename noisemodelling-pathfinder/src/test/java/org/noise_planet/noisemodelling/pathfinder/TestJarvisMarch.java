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
import org.locationtech.jts.geom.LineSegment;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJarvisMarch {

    @Test
    public void testRegression1() {
        List<Coordinate> coordinateList = Arrays.asList(
        new Coordinate(298573.5099455929, 6711012.823892152, 14.82478856405234),
        new Coordinate(298554.4267015595, 6710812.192061609, 12.116495822790334),
        new Coordinate(298553.7577195117, 6710805.158713786, 12.116495822790334),
        new Coordinate(298547.73648829164, 6710741.854453793, 8.657163413302916),
        new Coordinate(298546.74974392366, 6710731.480309414, 8.657163413302916),
        new Coordinate(298545.3842122291, 6710717.12378161, 8.308393445414493),
        new Coordinate(298544.55241827975, 6710708.378709629, 8.308393445414493),
        new Coordinate(298544.19949135307, 6710704.668209715, 8.091067641961098),
        new Coordinate(298543.79961363634, 6710700.464092315, 8.091067641961098),
        new Coordinate(298210.91687022796, 6707200.698850637, 0.05));

        List<Coordinate> newPoints = JTSUtility.getNewCoordinateSystem(coordinateList);

        double[] pointsX;
        pointsX = new double[newPoints.size()];
        double[] pointsY;
        pointsY = new double[newPoints.size()];

        for (int i = 0; i < newPoints.size(); i++) {
            pointsX[i] = newPoints.get(i).x;
            if (!Double.isNaN(newPoints.get(i).y)) {
                pointsY[i] = newPoints.get(i).y;
            } else {
                pointsY[i] = 0.;
            }
            newPoints.get(i).setCoordinate(new Coordinate(pointsX[i], pointsY[i]));
        }
        //algorithm JarvisMarch to get the convex hull
        //JarvisMarch jm = new JarvisMarch(new JarvisMarch.Points(pointsX, pointsY));
        double angle = new LineSegment(coordinateList.get(coordinateList.size() - 1), coordinateList.get(0)).angle();
        List<Coordinate> pts = JTSUtility.getXAscendingHullPoints(newPoints.toArray(new Coordinate[newPoints.size()]));
        JTSUtility.getOldCoordinateSystem(pts.get(0), angle);
        assertEquals(2, pts.size());
        assertEquals(new Coordinate(0.0, 0.0, 14.82478856405234), JTSUtility.getOldCoordinateSystem(pts.get(0), angle));
        assertEquals(new Coordinate(362.59307536494487, 3812.125041514635, 0.05), JTSUtility.getOldCoordinateSystem(pts.get(1), angle));
    }
}
