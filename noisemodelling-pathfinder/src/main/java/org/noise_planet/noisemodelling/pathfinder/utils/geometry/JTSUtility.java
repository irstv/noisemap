/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder.utils.geometry;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.math.Vector3D;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Nicolas Fortin
 */
public class JTSUtility {
    /** Intersection test for topology triangle navigation */
    public static final double TRIANGLE_INTERSECTION_EPSILON = 1e-7;

    /**
     * Utility class
     **/
    private JTSUtility() {}


    /**
     * Get distance between a segment (p1,p2) and a point (point) with point perpendicular to (p1,p2)
     * @param p1 origin segment
     * @param p2 destination segment
     * @param point reference point
     * @return DistanceInfo[0]=distance; DistanceInfo[1]=sign;
     */
    private static double[] distance3D(Coordinate p1, Coordinate p2, Coordinate point) {
        double[] DistanceInfo = new double[2];
        double x1 = p1.getX();
        double y1 = p1.getY();
        double z1 = p1.getZ();

        double x2 = p2.getX();
        double y2 = p2.getY();
        double z2 = p2.getZ();

        double x0 = point.getX();
        double y0 = point.getY();
        double z0 = point.getZ();

        // Vector representing the LineSegment
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;

        // Vector from the start point of the LineSegment to the Point
        double px = x0 - x1;
        double py = y0 - y1;
        double pz = z0 - z1;

        // Compute the dot product of the vectors
        double dotProduct = dx * px + dy * py + dz * pz;

        // Calculate the projection of the Point onto the LineSegment
        double t = dotProduct / (dx * dx + dy * dy + dz * dz);

        // Calculate the closest point on the LineSegment to the Point
        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        double closestZ = z1 + t * dz;

        // Calculate the distance between the closest point and the Point
        double distance = Math.sqrt((x0 - closestX) * (x0 - closestX)
                + (y0 - closestY) * (y0 - closestY)
                + (z0 - closestZ) * (z0 - closestZ));
        double sign = z0 - closestZ;
        DistanceInfo[0]=distance;
        DistanceInfo[1]=sign;
        return DistanceInfo;
    }

    /**
     * Compute a and b linear function of the line p1 p2
     * @param p1 p1
     * @param p2 p2 with p2.x != p1.x
     * @return [a,b] linear function parameters.
     */
    public static double[] getLinearFunction(Coordinate p1, Coordinate p2) {
        if(Double.compare(p1.x, p2.x) == 0) {
            throw new IllegalArgumentException("X value must be different to compute linear function parameters");
        }
        double a = (p2.y - p1.y) / (p2.x - p1.x);
        double b = (p2.x * p1.y - p1.x * p2.y) / (p2.x - p1.x);
        return new double[]{a, b};
    }


    /**
     *
     * @param segment
     * @param p
     * @return
     */
    public static Coordinate getNearestPoint(LineSegment segment, Coordinate p) {
        double segmentLengthFraction = Math.min(1.0, Math.max(0, segment.projectionFactor(p)));
        return new Coordinate(segment.p0.x + segmentLengthFraction * (segment.p1.x - segment.p0.x),
                segment.p0.y + segmentLengthFraction * (segment.p1.y - segment.p0.y),
                segment.p0.z + segmentLengthFraction * (segment.p1.z - segment.p0.z));
    }


    /**
     *
     * @param from
     * @param to
     * @return
     */
    public static Coordinate getNearestPoint(Coordinate from, LineString to) {
        Coordinate[] coordinates = to.getCoordinates();
        Coordinate closestPoint = null;
        double closestPointDistance = Double.MAX_VALUE;
        for(int i=0; i < coordinates.length - 1; i++) {
            final Coordinate a = coordinates[i];
            final Coordinate b =  coordinates[i+1];
            Coordinate closestPointOnSegment = getNearestPoint(new LineSegment(a, b), from);
            double closestPointOnSegmentDistance = closestPointOnSegment.distance3D(from);
            if(closestPoint == null || closestPointOnSegmentDistance < closestPointDistance) {
                closestPoint = closestPointOnSegment;
                closestPointDistance = closestPointOnSegmentDistance;
            }
        }
        return closestPoint;
    }

    /**
     * NFS 31-133 P.69 Annex E
     * @param xzList Line coordinates in the same plan of the line formed by the first and the last point.
     *                           X must be incremental.
     * @return [a,b] Linear function parameters produced by least square regression of provided points.
     */
    public static double[] getLinearRegressionPolyline(List<Coordinate> xzList) {
        // Linear regression
        SimpleRegression simpleRegression = new SimpleRegression();
        for(Coordinate p : xzList) {
            simpleRegression.addData(p.x, p.y);
        }
        return new double[] {simpleRegression.getSlope(), simpleRegression.getIntercept()};
    }

    /**
     * Project point on y=ax+b plane
     * @param a a plane parameter
     * @param b b plane parameter
     * @param point Point to project
     * @return Projected point
     */
    public static Coordinate makeProjectedPoint(double a, double b, Coordinate point) {
        LineSegment plane = new LineSegment(new Coordinate(point.x - 1,
                a*(point.x - 1)+b),new Coordinate(point.x + 1,a*(point.x + 1)+b));
        return plane.project(point);
    }

    /**
     * Make image (like line y=ax+b would be a mirror) of point
     * @param a a linear parameter
     * @param b b linear parameter
     * @param point Point to project
     * @return Mirrored point position
     */
    public static Coordinate makePointImage(double a, double b, Coordinate point) {
        LineSegment pointPlane = new LineSegment(point, makeProjectedPoint(a,b, point));
        return pointPlane.pointAlong(2);
    }


    public static boolean dotInTri(Coordinate p, Coordinate a, Coordinate b,
                                   Coordinate c) {
        return dotInTri(p, a, b, c, null);
    }

    public static Vector3D getTriangleNormal(Coordinate p1, Coordinate p2, Coordinate p3) {
        Vector3D a = new Vector3D(p1, p2);
        Vector3D b = new Vector3D(p1, p3);
        return new Vector3D(a.getY() * b.getZ() - a.getZ() * b.getY(),
                a.getZ() * b.getX() - a.getX() * b.getZ(),
                a.getX()*b.getY() - a.getY() * b.getX()).normalize();
    }

    /**
     * Fast dot in triangle test
     * http://www.blackpawn.com/texts/pointinpoly/default.html
     *
     * @param p coordinate of the point
     * @param a coordinate of the A vertex of triangle
     * @param b coordinate of the B vertex of triangle
     * @param c coordinate of the C vertex of triangle
     * @return True if dot is in triangle
     */
    public static boolean dotInTri(Coordinate p, Coordinate a, Coordinate b,
                                   Coordinate c, AtomicReference<Double> error) {
        Vector2D v0 = new Vector2D(c.x - a.x, c.y - a.y);
        Vector2D v1 = new Vector2D(b.x - a.x, b.y - a.y);
        Vector2D v2 = new Vector2D(p.x - a.x, p.y - a.y);

        // Compute dot products
        double dot00 = v0.dot(v0);
        double dot01 = v0.dot(v1);
        double dot02 = v0.dot(v2);
        double dot11 = v1.dot(v1);
        double dot12 = v1.dot(v2);

        // Compute barycentric coordinates
        double invDenom = 1 / (dot00 * dot11 - dot01 * dot01);
        double u = (dot11 * dot02 - dot01 * dot12) * invDenom;
        double v = (dot00 * dot12 - dot01 * dot02) * invDenom;

        if(error != null) {
            double err = 0;
            err += Math.max(0, -u);
            err += Math.max(0, -v);
            err += Math.max(0, (u + v) - 1);
            error.set(err);
        }

        // Check if point is in triangle
        return (u > (0. - TRIANGLE_INTERSECTION_EPSILON)) && (v > (0. - TRIANGLE_INTERSECTION_EPSILON))
                && (u + v < (1. + TRIANGLE_INTERSECTION_EPSILON));

    }

    /**
     * ChangeCoordinateSystem, use original coordinate in 3D to change into a new markland in 2D
     * with new x' computed by algorithm and y' is original height of point.
     * @param  listPoints X Y Z points, all should be on the same plane as first and last points.
     * @return X Z projected points
     */
    public static List<Coordinate> getNewCoordinateSystem(List<Coordinate> listPoints) {
        return getNewCoordinateSystem(listPoints, 0);
    }

    /**
     * ChangeCoordinateSystem, use original coordinate in 3D to change into a new markland in 2D
     * with new x' computed by algorithm and y' is original height of point.
     * @param  listPoints X Y Z points, all should be on the same plane as first and last points.
     * @param tolerance Simplify the point list by not adding points where the distance from the line segments
     *                 formed from the previous and the next point is inferior to this tolerance (remove intermediate collinear points)
     * @return X Z projected points
     */
    public static List<Coordinate> getNewCoordinateSystem(List<Coordinate> listPoints, double tolerance) {
        if(listPoints.isEmpty()) {
            return new ArrayList<>();
        }
        List<Coordinate> newCoord = new ArrayList<>(listPoints.size());
        newCoord.add(new Coordinate(0, listPoints.get(0).z));
        for (int idPoint = 1; idPoint < listPoints.size(); idPoint++) {
            final Coordinate pt = listPoints.get(idPoint);
            // Get 2D distance
            newCoord.add(new Coordinate(newCoord.get(idPoint - 1).x + pt.distance(listPoints.get(idPoint - 1)), pt.z));
        }
        if(tolerance > 0) {
            // remove collinear points using tolerance
            for (int idPoint = 1; idPoint < newCoord.size() - 1;) {
                final Coordinate previous = newCoord.get(idPoint - 1);
                final Coordinate current = newCoord.get(idPoint);
                final Coordinate next = newCoord.get(idPoint+1);
                final LineSegment lineSegment = new LineSegment(previous, next);
                if(lineSegment.distance(current) < tolerance) {
                    newCoord.remove(idPoint);
                } else {
                    idPoint++;
                }
            }
        }
        return newCoord;
    }

    /**
     * ChangeCoordinateSystem, use original coordinate in 3D to change into a new markland in 2D with new x' computed by algorithm and y' is original height of point.
     * Attention this function can just be used when the points in the same plane.
     * "http://en.wikipedia.org/wiki/Rotation_matrix"
     * @param  listPoints X Y Z points, all should be on the same plane as first and last points.
     * @return X Z projected points
     */
    public static List<Coordinate> getOldCoordinateSystemList(List<Coordinate> listPoints, double angle) {
        List<Coordinate> newCoord = new ArrayList<>(listPoints.size());
        //get angle by ray source-receiver with the X-axis.
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        for (Coordinate listPoint : listPoints) {
            double newX = (listPoint.x - listPoints.get(listPoints.size()-1).x) *-cos;
            double newY = (listPoint.x - listPoints.get(listPoints.size()-1).x) *sin;

            newCoord.add(new Coordinate(newX,newY, listPoint.y));
        }
        return newCoord;
    }


    /**
     * ChangeCoordinateSystem, use original coordinate in 3D to change into a new markland in 2D with new x' computed by algorithm and y' is original height of point.
     * Attention this function can just be used when the points in the same plane.
     * "http://en.wikipedia.org/wiki/Rotation_matrix"
     * @param  Point X Y Z points, all should be on the same plane as first and last points.
     * @return X Z projected points
     */
    public static Coordinate getOldCoordinateSystem(Coordinate Point, double angle) {
        //get angle by ray source-receiver with the X-axis.
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        return new Coordinate( (Point.x ) *cos, (Point.x ) *sin, Point.y);
    }

    /**
     * calculate the mean plane y = A.x + B for a sequence of terrain points projected on the
     * unfolded propagation plane ; using (x,y) coordinates as in section  VI.2.2.c of the
     * JRC-2012 reference report.
     * @param profile u v coordinates @see {{@link #getNewCoordinateSystem(List)}}
     * @return Coefficient A and B
     */
    public static double[] getMeanPlaneCoefficients (Coordinate[] profile)
    {
        int n = profile.length - 1 ;
        if(n == 0) {
            return new double[] {0, profile[0].y};
        }
        double valA1 = 0;
        double valA2 = 0;
        double valB1 = 0;
        double valB2 = 0;
        /*
         * equation VI-3
         */
        for (int i = 0 ; i < n ; i++)
        {
            Coordinate p1 = profile[i];
            Coordinate p2 = profile[i+1];
            double dx = p2.x - p1.x ;
            if (dx > 0)
            {
                double ai = (p2.y - p1.y) / dx;
                double bi = p1.y - ai * p1.x;
                double vald2 = Math.pow (p2.x, 2) - Math.pow (p1.x, 2);
                double vald3 = Math.pow (p2.x, 3) - Math.pow (p1.x, 3);
                valA1 += ai * vald3 ;
                valA2 += bi * vald2;
                valB1 += ai * vald2;
                valB2 += bi * dx;
            }
        }
        double valA = 2/3. * valA1 + valA2;
        double valB = valB1 + 2 * valB2;
        double dist3 = Math.pow (profile[n].x - profile[0].x, 3) ;
        double dist4 = Math.pow (profile[n].x - profile[0].x, 4) ;

        /*
         * equation VI-4
         */
        double A = 3 * (2 * valA - valB * (profile[n].x + profile[0].x)) / dist3 ;
        double B = 2 * valB * (Math.pow(profile[n].x, 3) - Math.pow(profile[0].x, 3)) / dist4
                - 3 * valA * (profile[n].x + profile[0].x) / dist3;
        return new double[] {A, B};
    }

    /**
     * @param coordinates Coordinates
     * @return Parts of the clock-wise ConvexHull where x value are increasing from the minimum X value
     */
    public static List<Coordinate> getXAscendingHullPoints(Coordinate[] coordinates){
        ConvexHull convexHull = new ConvexHull(coordinates, new GeometryFactory());
        Geometry hullGeom = convexHull.getConvexHull();
        Coordinate[] hull = hullGeom.getCoordinates();
        if(hull.length > 3 && Orientation.isCCW(hull)) {
            Coordinate temp;
            for (int i = 0; i < hull.length / 2; i++) {
                temp = hull[i];
                hull[i] = hull[hull.length - 1 - i];
                hull[hull.length - 1 - i] = temp;
            }
        }
        // Find minimal x index
        int index = -1;
        double minX = Double.MAX_VALUE;
        for(int i=0; i < hull.length; i++) {
            if(hull[i].x < minX) {
                index = i;
                minX = hull[i].x;
            }
        }
        List<Coordinate> offsetHull = new ArrayList<>(hull.length);
        double lastX = Double.NEGATIVE_INFINITY;
        for(int i = index; i < hull.length; i++) {
            if(hull[i].x > lastX) {
                offsetHull.add(hull[i]);
                lastX = hull[i].x;
            } else {
                break;
            }
        }
        for(int i = 0; i < index; i++) {
            if(hull[i].x > lastX) {
                offsetHull.add(hull[i]);
                lastX = hull[i].x;
            } else {
                break;
            }
        }
        return offsetHull;
    }

    /**
     * compute the distance between two points of dimension three
     * @param c0
     * @param c1
     * @return the distance in double
     */
    public static double dist3D(Coordinate c0, Coordinate c1) {
        return Math.sqrt((c1.x-c0.x)*(c1.x-c0.x) + (c1.y-c0.y)*(c1.y-c0.y) + (c1.z-c0.z)*(c1.z-c0.z));
    }

    /**
     * compute the distance between two points in two dimensions
     * @param c0
     * @param c1
     * @return the distance in double
     */
    public static Double dist2D(Coordinate c0, Coordinate c1) {
        return Math.sqrt((c1.x-c0.x)*(c1.x-c0.x) + (c1.y-c0.y)*(c1.y-c0.y));
    }

    /**
     *
     * @param p0
     * @param p1
     * @return
     */
    public static double getSlope(Coordinate p0, Coordinate p1) {
        return (p1.y-p0.y)/(p1.x-p0.x);
    }
}
