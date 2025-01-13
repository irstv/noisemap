/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.utils;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.math.Vector2D;

import java.util.ArrayList;

public class MakeParallelLines {
    /**
     * @param lineString LineString to offset
     * @param distance Distance from linestring, negative to set the line on opposite side
     * @return Offset linestring
     */
    public static LineString MakeParallelLine(LineString lineString, double distance) {
        Coordinate[] points = lineString.getCoordinates();
        if(points.length < 2) {
            return lineString.getFactory().createLineString();
        }

        ArrayList<Coordinate> newPoints = new ArrayList<>(Math.max(10, points.length));

        // First point is perpendicular to first segment
        newPoints.add(new LineSegment(points[0], points[1]).pointAlongOffset(0, distance));

        for(int i = 1; i < points.length - 1; i++) {
            Coordinate a = points[i-1];
            Coordinate b = points[i];
            Coordinate c = points[i+1];

            if(Math.abs(Angle.angleBetween(a, b, c) - Math.PI) < 1e-12) {
                // Collinear
                newPoints.add(new LineSegment(b, c).pointAlongOffset(0, distance));
            } else {
                Coordinate p = Triangle.angleBisector(a, b, c);
                Vector2D bi = Vector2D.create(b, p).normalize().multiply(distance);
                // Test if the interior point is on exterior of triangle
                if (!Orientation.isCCW(new Coordinate[]{a, b, c, a})) {
                    bi = bi.multiply(-1);
                }
                Coordinate splitPt = Vector2D.create(b).add(bi).toCoordinate();
                newPoints.add(splitPt);
            }
        }

        // Last point is perpendicular to last segment
        newPoints.add(new LineSegment(points[points.length - 2], points[points.length - 1]).pointAlongOffset(1, distance));

        return lineString.getFactory().createLineString(newPoints.toArray(new Coordinate[]{}));
    }
}
