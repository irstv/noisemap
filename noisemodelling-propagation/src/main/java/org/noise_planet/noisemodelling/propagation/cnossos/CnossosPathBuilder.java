package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.math.Vector3D;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReflection;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointTopography;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointVEdgeDiffraction;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointWall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.*;
import static java.lang.Math.max;
import static org.noise_planet.noisemodelling.propagation.cnossos.PointPath.POINT_TYPE.*;
import static org.noise_planet.noisemodelling.pathfinder.utils.geometry.GeometryUtils.projectPointOnLine;

/**
 * Generate a CnossosPath from a vertical cut plane data
 */
public class CnossosPathBuilder {
    public static final double ALPHA0 = 2e-4;
    private static final double EPSILON = 1e-7;

    public static void computeRayleighDiff(SegmentPath srSeg, CutProfile cutProfile, CnossosPath pathParameters,
                                     LineSegment dSR, List<SegmentPath> segments, List<PointPath> points,
                                     List<Coordinate> pts2D, Coordinate[] pts2DGround, List<Integer> cut2DGroundIndex,
                                           List<Integer> frequencyTable) {
        final List<CutPoint> cuts = cutProfile.cutPoints;

        Coordinate src = pts2D.get(0);
        Coordinate rcv = pts2D.get(pts2D.size() - 1);
        CutPoint srcCut = cutProfile.getSource();
        CutPoint rcvCut = cutProfile.getReceiver();
        for (int i0Cut = 1; i0Cut < cuts.size() - 1; i0Cut++) {
            int iO = cut2DGroundIndex.get(i0Cut);
            Coordinate o = pts2DGround[iO];

            double dSO = src.distance(o);
            double dOR = o.distance(rcv);
            double deltaH = dSR.orientationIndex(o) * (dSO + dOR - srSeg.d);
            boolean rcrit = false;
            for(int f : frequencyTable) {
                if(deltaH > -(340./f) / 20) {
                    rcrit = true;
                    break;
                }
            }
            if (rcrit) {
                rcrit = false;
                //Add point path

                //Plane S->O
                Coordinate[] soCoords = Arrays.copyOfRange(pts2DGround, 0, iO + 1);
                double[] abs = JTSUtility.getMeanPlaneCoefficients(soCoords);
                SegmentPath seg1 = computeSegment(src, o, abs);

                //Plane O->R
                Coordinate[] orCoords = Arrays.copyOfRange(pts2DGround, iO, pts2DGround.length);
                double[] abr = JTSUtility.getMeanPlaneCoefficients(orCoords);
                SegmentPath seg2 = computeSegment(o, rcv, abr);

                Coordinate srcPrime = new Coordinate(src.x + (seg1.sMeanPlane.x - src.x) * 2, src.y + (seg1.sMeanPlane.y - src.y) * 2);
                Coordinate rcvPrime = new Coordinate(rcv.x + (seg2.rMeanPlane.x - rcv.x) * 2, rcv.y + (seg2.rMeanPlane.y - rcv.y) * 2);

                LineSegment dSPrimeRPrime = new LineSegment(srcPrime, rcvPrime);
                srSeg.dPrime = srcPrime.distance(rcvPrime);
                seg1.dPrime = srcPrime.distance(o);
                seg2.dPrime = o.distance(rcvPrime);

                double deltaPrimeH = dSPrimeRPrime.orientationIndex(o) * (seg1.dPrime + seg2.dPrime - srSeg.dPrime);
                for(int f : frequencyTable) {
                    if(deltaH > (340./f) / 4 - deltaPrimeH) {
                        rcrit = true;
                        break;
                    }
                }
                if (rcrit) {
                    pathParameters.deltaH = deltaH;
                    pathParameters.deltaPrimeH = deltaPrimeH;
                    seg1.setGpath(cutProfile.getGPath(srcCut, cuts.get(i0Cut), Scene.DEFAULT_G_BUILDING), srcCut.getGroundCoefficient());
                    seg2.setGpath(cutProfile.getGPath(cuts.get(i0Cut), rcvCut, Scene.DEFAULT_G_BUILDING), srcCut.getGroundCoefficient());

                    if(dSR.orientationIndex(o) == 1) {
                        pathParameters.deltaF = toCurve(dSO, srSeg.d) + toCurve(dOR, srSeg.d) - toCurve(srSeg.d, srSeg.d);
                    }
                    else {
                        Coordinate pA = dSR.pointAlong((o.x-src.x)/(rcv.x-src.x));
                        pathParameters.deltaF =2*toCurve(src.distance(pA), srSeg.d) + 2*toCurve(pA.distance(rcv), srSeg.d) - toCurve(dSO, srSeg.d) - toCurve(dOR, srSeg.d) - toCurve(srSeg.d, srSeg.d);
                    }

                    LineSegment sPrimeR = new LineSegment(seg1.sPrime, rcv);
                    double dSPrimeO = seg1.sPrime.distance(o);
                    double dSPrimeR = seg1.sPrime.distance(rcv);
                    pathParameters.deltaSPrimeRH = sPrimeR.orientationIndex(o)*(dSPrimeO + dOR - dSPrimeR);

                    LineSegment sRPrime = new LineSegment(src, seg2.rPrime);
                    double dORPrime = o.distance(seg2.rPrime);
                    double dSRPrime = src.distance(seg2.rPrime);
                    pathParameters.deltaSRPrimeH = sRPrime.orientationIndex(o)*(dSO + dORPrime - dSRPrime);

                    if(dSPrimeRPrime.orientationIndex(o) == 1) {
                        pathParameters.deltaPrimeF = toCurve(seg1.dPrime, srSeg.dPrime) + toCurve(seg2.dPrime, srSeg.dPrime) - toCurve(srSeg.dPrime, srSeg.dPrime);
                    }
                    else {
                        Coordinate pA = dSPrimeRPrime.pointAlong((o.x-srcPrime.x)/(rcvPrime.x-srcPrime.x));
                        pathParameters.deltaPrimeF =2*toCurve(srcPrime.distance(pA), srSeg.dPrime) + 2*toCurve(pA.distance(srcPrime), srSeg.dPrime) - toCurve(seg1.dPrime, srSeg.dPrime) - toCurve(seg2.dPrime, srSeg.d) - toCurve(srSeg.dPrime, srSeg.dPrime);
                    }

                    segments.add(seg1);
                    segments.add(seg2);

                    points.add(new PointPath(o, o.z, new ArrayList<>(), DIFH_RCRIT));
                }
            }
        }
    }


    /**
     * Compute the segment path
     * @param src
     * @param rcv
     * @param meanPlane
     * @return the calculated segment
     */
    public static SegmentPath computeSegment(Coordinate src, Coordinate rcv, double[] meanPlane) {
        return computeSegment(src, rcv, meanPlane, 0, 0);
    }

    /**
     * Compute the segment path with more attribute
     * @param src
     * @param rcv
     * @param meanPlane
     * @param gPath
     * @param gS
     * @return the computed segment path
     */

    public static SegmentPath computeSegment(Coordinate src, Coordinate rcv, double[] meanPlane, double gPath, double gS) {
        SegmentPath seg = new SegmentPath();
        Coordinate sourcePointOnMeanPlane = projectPointOnLine(src, meanPlane[0], meanPlane[1]);
        Coordinate receiverPointOnMeanPlane = projectPointOnLine(rcv, meanPlane[0], meanPlane[1]);
        Vector2D sourceToProjectedPoint = Vector2D.create(src, sourcePointOnMeanPlane);
        Vector2D receiverToProjectedPoint = Vector2D.create(rcv, receiverPointOnMeanPlane);
        seg.s = src;
        seg.r = rcv;
        seg.sMeanPlane = sourcePointOnMeanPlane;
        seg.rMeanPlane = receiverPointOnMeanPlane;
        seg.sPrime = Vector2D.create(sourcePointOnMeanPlane).add(sourceToProjectedPoint).toCoordinate();
        seg.rPrime = Vector2D.create(receiverPointOnMeanPlane).add(receiverToProjectedPoint).toCoordinate();

        seg.d = src.distance(rcv);
        seg.dp =sourcePointOnMeanPlane.distance(receiverPointOnMeanPlane);
        seg.zsH = src.distance(sourcePointOnMeanPlane);
        seg.zrH = rcv.distance(receiverPointOnMeanPlane);
        seg.a = meanPlane[0];
        seg.b = meanPlane[1];
        seg.testFormH = seg.dp/(30*(seg.zsH +seg.zrH));
        seg.gPath = gPath;
        seg.gPathPrime = seg.testFormH <= 1 ? seg.gPath*(seg.testFormH) + gS*(1-seg.testFormH) : seg.gPath; // 2.5.14
        double deltaZT = 6e-3 * seg.dp / (seg.zsH + seg.zrH);
        double deltaZS = ALPHA0 * pow((seg.zsH / (seg.zsH + seg.zrH)), 2) * (seg.dp*seg.dp / 2); //2.5.19
        seg.zsF = seg.zsH + deltaZS + deltaZT;
        double deltaZR = ALPHA0 * pow((seg.zrH / (seg.zsH + seg.zrH)), 2) * (seg.dp*seg.dp / 2);
        seg.zrF = seg.zrH + deltaZR + deltaZT;
        seg.testFormF = seg.dp/(30*(seg.zsF +seg.zrF));
        return seg;
    }

    /**
     * Eq.2.5.24 and Eq. 2.5.25
     * @param mn
     * @param d
     * @return
     */
    public static double toCurve(double mn, double d){
        return 2*max(1000, 8*d)* asin(mn/(2*max(1000, 8*d)));
    }

    /**
     * Given the vertical cut profile (can be a single plane or multiple like a folding panel) return the ray path
     * following Cnossos specification, or null if there is no valid path.
     * @param cutProfile Vertical cut of a domain
     * @param bodyBarrier
     * @return The cnossos path or null
     */
    public static CnossosPath computeAttenuationFromCutProfile(CutProfile cutProfile , boolean bodyBarrier, List<Integer> frequencyTable, double gS) {
        List<SegmentPath> segments = new ArrayList<>();
        List<PointPath> points = new ArrayList<>();
        final List<CutPoint> cutProfilePoints = cutProfile.cutPoints;

        List<Coordinate> pts2D = cutProfile.computePts2D();
        if(pts2D.size() != cutProfilePoints.size()) {
            throw new IllegalArgumentException("The two arrays size should be the same");
        }

        List<Integer> cut2DGroundIndex = new ArrayList<>(cutProfilePoints.size());
        Coordinate[] pts2DGround = cutProfile.computePts2DGround(cut2DGroundIndex).toArray(new Coordinate[0]);
        double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(pts2DGround);
        Coordinate firstPts2D = pts2D.get(0);
        Coordinate lastPts2D = pts2D.get(pts2D.size()-1);
        SegmentPath srPath = computeSegment(firstPts2D, lastPts2D, meanPlane, cutProfile.getGPath(), cutProfile.getSource().groundCoefficient);
        srPath.setPoints2DGround(pts2DGround);
        srPath.dc = CGAlgorithms3D.distance(cutProfile.getReceiver().getCoordinate(),
                cutProfile.getSource().getCoordinate());
        CnossosPath pathParameters = new CnossosPath();
        pathParameters.setCutProfile(cutProfile);
        pathParameters.setSourceOrientation(cutProfile.getSource().orientation);
        pathParameters.setFavorable(true);
        pathParameters.setPointList(points);
        pathParameters.setSegmentList(segments);
        pathParameters.setSRSegment(srPath);
        pathParameters.init(frequencyTable.size());
        pathParameters.angle= Angle.angle(cutProfile.getReceiver().getCoordinate(), cutProfile.getSource().getCoordinate());
        // Extract the first and last points to define the line segment
        Coordinate firstPt = pts2D.get(0);
        Coordinate lastPt = pts2D.get(pts2D.size() - 1);

        // Filter out points that are below the line segment
        List<Coordinate> convexHullInput = new ArrayList<>();
        // Add source position
        convexHullInput.add(pts2D.get(0));
        // Add valid diffraction point, building/walls/dem
        for (int idPoint=1; idPoint < cutProfilePoints.size() - 1; idPoint++) {
            CutPoint currentPoint = cutProfilePoints.get(idPoint);
            // We only add the point at the top of the wall, not the point at the bottom of the wall
            if(currentPoint instanceof CutPointTopography
                    || (currentPoint instanceof CutPointWall
                    && Double.compare(currentPoint.getCoordinate().z, currentPoint.getzGround()) != 0)) {
                convexHullInput.add(pts2D.get(idPoint));
            }
        }
        // Add receiver position
        convexHullInput.add(pts2D.get(pts2D.size() - 1));

        // Compute the convex hull using JTS
        List<Coordinate> convexHullPoints = new ArrayList<>();
        if(convexHullInput.size() > 2) {
            GeometryFactory geomFactory = new GeometryFactory();
            Coordinate[] coordsArray = convexHullInput.toArray(new Coordinate[0]);
            ConvexHull convexHull = new ConvexHull(coordsArray, geomFactory);
            Coordinate[] convexHullCoords = convexHull.getConvexHull().getCoordinates();
            int indexFirst = Arrays.asList(convexHull.getConvexHull().getCoordinates()).indexOf(firstPt);
            int indexLast = Arrays.asList(convexHull.getConvexHull().getCoordinates()).lastIndexOf(lastPt);
            if(indexFirst == -1 || indexLast == -1 || indexFirst > indexLast) {
                throw new IllegalArgumentException("Wrong input data " + cutProfile.toString());
            }
            convexHullCoords = Arrays.copyOfRange(convexHullCoords, indexFirst, indexLast + 1);
            CoordinateSequence coordSequence = geomFactory.getCoordinateSequenceFactory().create(convexHullCoords);
            Geometry geom = geomFactory.createLineString(coordSequence);
            Geometry uniqueGeom = geom.union(); // Removes duplicate coordinates
            convexHullCoords = uniqueGeom.getCoordinates();
            // Convert the result back to your format (List<Point2D> pts)
            if (convexHullCoords.length == 3) {
                convexHullPoints = Arrays.asList(convexHullCoords);
            } else {
                for (int j = 0; j < convexHullCoords.length; j++) {
                    // Check if the y-coordinate is valid (not equal to Double.MAX_VALUE and not infinite)
                    if (convexHullCoords[j].y == Double.MAX_VALUE || Double.isInfinite(convexHullCoords[j].y)) {
                        continue; // Skip this point as it's not part of the hull
                    }
                    convexHullPoints.add(convexHullCoords[j]);
                }
            }
        } else {
            convexHullPoints = convexHullInput;
        }
        List<Coordinate> pts = convexHullPoints;

        Coordinate src = cutProfile.getSource().getCoordinate();

        // Move then check reflection height if there is diffraction on the path
        if(pts.size() > 2) {
            for (int i = 1; i < pts.size(); i++) {
                int i0 = pts2D.indexOf(pts.get(i - 1));
                int i1 = pts2D.indexOf(pts.get(i));
                LineSegment segmentHull = new LineSegment(pts.get(i - 1), pts.get(i));
                for (int pointIndex = i0 + 1; pointIndex < i1; pointIndex++) {
                    final CutPoint currentPoint = cutProfilePoints.get(pointIndex);
                    // If the current point is the reflection point (not on the ground level)
                    if (currentPoint instanceof CutPointReflection &&
                            Double.compare(currentPoint.getCoordinate().z, currentPoint.getzGround()) != 0) {
                        CutPointReflection cutPointReflection = (CutPointReflection) currentPoint;
                        Coordinate interpolatedReflectionPoint = segmentHull.closestPoint(pts2D.get(pointIndex));
                        // Check if the new elevation of the reflection point is not higher than the wall
                        double wallAltitudeAtReflexionPoint = Vertex.interpolateZ(currentPoint.coordinate,
                                cutPointReflection.wall.p0, cutPointReflection.wall.p1);
                        if(wallAltitudeAtReflexionPoint + EPSILON >= interpolatedReflectionPoint.y) {
                            // update the reflection position
                            currentPoint.getCoordinate().setZ(interpolatedReflectionPoint.y);
                            pts2D.get(pointIndex).setY(interpolatedReflectionPoint.y);
                        } else {
                            // Reflection is not valid, so the whole path is not valid
                            return null;
                        }
                    }
                }
            }
        }

        // Create segments from each diffraction point to the receiver
        for (int i = 1; i < pts.size(); i++) {
            int i0 = pts2D.indexOf(pts.get(i - 1));
            int i1 = pts2D.indexOf(pts.get(i));
            int i0Ground = cut2DGroundIndex.get(i0);
            int i1Ground = cut2DGroundIndex.get(i1);
            final CutPoint cutPt0 = cutProfilePoints.get(i0);
            final CutPoint cutPt1 = cutProfilePoints.get(i1);
            // ground index may be near the diffraction point
            // Depending on the range, we have to pick the bottom of the wall or the top of the wall point
            if (i1Ground - 1 > i0Ground && cutPt1 instanceof CutPointWall) {
                final CutPointWall cutPt1Wall = (CutPointWall) cutPt1;
                if(cutPt1Wall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.BUILDING_ENTER)) {
                    i1Ground -= 1;
                } else if (cutPt1Wall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT)) {
                    i1Ground -= 2;
                }
            }
            if (points.isEmpty()) {
                // First segment, add the source point in the array
                points.add(new PointPath(pts2D.get(i0), cutPt0.getzGround(), SRCE));
                // look for the first reflection before the first diffraction, the source orientation is to the first reflection point
                Coordinate targetPosition = cutProfilePoints.get(i1).getCoordinate();
                for (int pointIndex = i0 + 1; pointIndex < i1; pointIndex++) {
                    final CutPoint currentPoint = cutProfilePoints.get(pointIndex);
                    if ((currentPoint instanceof CutPointReflection ||
                            currentPoint instanceof CutPointVEdgeDiffraction) &&
                            Double.compare(currentPoint.getCoordinate().z, currentPoint.getzGround()) != 0) {
                        // The first reflection (the one not at ground level)
                        // from the source coordinate is the direction of the propagation
                        targetPosition = currentPoint.getCoordinate();
                        break;
                    }
                }
                Orientation emissionDirection = computeOrientation(cutProfile.getSource().orientation,
                        cutProfilePoints.get(0).getCoordinate(), targetPosition);
                points.get(0).orientation = emissionDirection;
                pathParameters.raySourceReceiverDirectivity = emissionDirection;
                src = pts2D.get(i0);
            }
            // Add reflection/vertical edge diffraction points/segments between i0 i1
            int previousPivotPoint = i0;
            for (int pointIndex = i0 + 1; pointIndex < i1; pointIndex++) {
                final CutPoint currentPoint = cutProfilePoints.get(pointIndex);
                if (currentPoint instanceof CutPointReflection &&
                        Double.compare(currentPoint.getCoordinate().z, currentPoint.getzGround()) != 0) {
                    // If the current point is a reflection and not before/after the reflection
                    CutPointReflection cutPointReflection = (CutPointReflection) currentPoint;
                    double wallAltitudeAtReflexionPoint = Vertex.interpolateZ(cutPointReflection.coordinate,
                            cutPointReflection.wall.p0, cutPointReflection.wall.p1);
                    PointPath reflectionPoint = new PointPath(pts2D.get(pointIndex),currentPoint.getzGround(),
                            cutPointReflection.wallAlpha, REFL);
                    reflectionPoint.obstacleZ = wallAltitudeAtReflexionPoint;
                    points.add(reflectionPoint);
                } else if (currentPoint instanceof CutPointVEdgeDiffraction) {
                    // current point is a vertical edge diffraction (there is no additional points unlike reflection)
                    PointPath diffractionPoint = new PointPath(pts2D.get(pointIndex),currentPoint.getzGround(), new ArrayList<>(), DIFV);
                    points.add(diffractionPoint);
                    // Compute additional segment
                    Coordinate[] segmentGroundPoints = Arrays.copyOfRange(pts2DGround, i0Ground,cut2DGroundIndex.get(pointIndex) + 1);
                    meanPlane = JTSUtility.getMeanPlaneCoefficients(segmentGroundPoints);
                    SegmentPath seg = computeSegment(pts2D.get(previousPivotPoint), pts2D.get(pointIndex),
                            meanPlane, cutProfile.getGPath(cutPt0, cutProfilePoints.get(pointIndex), Scene.DEFAULT_G_BUILDING), gS);
                    seg.setPoints2DGround(segmentGroundPoints);
                    previousPivotPoint = pointIndex;
                    segments.add(seg);
                }
            }
            points.add(new PointPath(pts2D.get(i1), cutPt1.getzGround(), RECV));
            if(previousPivotPoint != i0 && i == pts.size() - 1) {
                // we added segments before i1 vertical plane diffraction point, but it is the last vertical plane
                // diffraction point and we must add the remaining segment between the last horizontal diffraction point
                // and the last point
                Coordinate[] segmentGroundPoints = Arrays.copyOfRange(pts2DGround, i1Ground, pts2DGround.length);
                meanPlane = JTSUtility.getMeanPlaneCoefficients(segmentGroundPoints);
                SegmentPath seg = computeSegment(pts2D.get(previousPivotPoint), pts2D.get(pts2D.size() - 1),
                        meanPlane, cutProfile.getGPath(cutPt1, cutProfilePoints.get(cutProfilePoints.size() - 1), Scene.DEFAULT_G_BUILDING),
                        gS);
                seg.setPoints2DGround(segmentGroundPoints);
                segments.add(seg);
            }
            if(pts.size() == 2) {
                // no diffraction over buildings/dem, we already computed SR segment
                break;
            }
            Coordinate[] segmentGroundPoints = Arrays.copyOfRange(pts2DGround, i0Ground,i1Ground + 1);
            meanPlane = JTSUtility.getMeanPlaneCoefficients(segmentGroundPoints);
            SegmentPath path = computeSegment(pts2D.get(i0), pts2D.get(i1), meanPlane,
                    cutProfile.getGPath(cutProfilePoints.get(i0), cutProfilePoints.get(i1), Scene.DEFAULT_G_BUILDING),
                    cutProfilePoints.get(i0).groundCoefficient);
            path.dc = cutPt0.getCoordinate().distance3D(cutPt1.getCoordinate());
            path.setPoints2DGround(segmentGroundPoints);
            segments.add(path);
            if (i != pts.size() - 1) {
                PointPath pt = points.get(points.size() - 1);
                pt.type = DIFH;
                pt.bodyBarrier = bodyBarrier;
            }
        }

        if(points.isEmpty()) {
            return null;
        }

        Coordinate rcv = points.get(points.size()-1).coordinate;
        PointPath p0 = points.stream().filter(p -> p.type.equals(DIFH)).findFirst().orElse(null);
        if(p0==null){
            // Direct propagation (no diffraction over obstructing objects)
            boolean horizontalPlaneDiffraction = cutProfile.cutPoints.stream()
                    .anyMatch(
                            cutPoint -> cutPoint instanceof CutPointVEdgeDiffraction);
            List<SegmentPath> rayleighSegments = new ArrayList<>();
            List<PointPath> rayleighPoints = new ArrayList<>();
            // do not check for rayleigh if the path is not direct between R and S
            if(!horizontalPlaneDiffraction) {
                // Check for Rayleigh criterion for segments computation
                LineSegment dSR = new LineSegment(firstPts2D, lastPts2D);
                // Look for diffraction over edge on free field (frequency dependent)
                computeRayleighDiff(srPath, cutProfile, pathParameters, dSR, rayleighSegments, rayleighPoints, pts2D,
                        pts2DGround, cut2DGroundIndex, frequencyTable);
            }
            if(rayleighSegments.isEmpty()) {
                // We don't have a Rayleigh diffraction over DEM. Only direct SR path
                if(segments.isEmpty()) {
                    segments.add(pathParameters.getSRSegment());
                }
                // Compute cumulated distance between the first diffraction and the last diffraction point
                pathParameters.e = 0;
                List<PointPath> diffPoints = points.stream().filter(pointPath -> pointPath.type != REFL).collect(Collectors.toList());
                for(int idPoint = 1; idPoint < diffPoints.size() - 2; idPoint++) {
                    pathParameters.e += diffPoints.get(idPoint).coordinate.distance(diffPoints.get(idPoint+1).coordinate);
                }
                long difVPointCount = pathParameters.getPointList().stream().
                        filter(pointPath -> pointPath.type.equals(DIFV)).count();
                double distance = difVPointCount == 0 ? pathParameters.getSRSegment().d : pathParameters.getSRSegment().dc;
                pathParameters.deltaH = segments.get(0).d + pathParameters.e + segments.get(segments.size()-1).d - distance;
                pathParameters.deltaF = pathParameters.deltaH;
            } else {
                segments.addAll(rayleighSegments);
                points.addAll(1, rayleighPoints);
            }
            return pathParameters;
        }
        Coordinate c0 = p0.coordinate;
        PointPath pn = points.stream().filter(p -> p.type.equals(DIFH)).reduce((first, second) -> second).orElse(null);
        if(pn==null){
            return null;
        }
        Coordinate cn = pn.coordinate;

        SegmentPath seg1 = segments.get(0);
        SegmentPath seg2 = segments.get(segments.size()-1);

        double dSO0 = seg1.d;
        double dOnR = seg2.d;
        LineSegment sr = new LineSegment(src, rcv);

        LineSegment sPrimeR = new LineSegment(seg1.sPrime, rcv);
        double dSPrimeR = seg1.sPrime.distance(rcv);
        double dSPrimeO = seg1.sPrime.distance(c0);
        // Compute cumulated distance between the first diffraction and the last diffraction point
        pathParameters.e = 0;
        List<PointPath> diffPoints = points.stream().filter(pointPath -> pointPath.type != REFL).collect(Collectors.toList());
        for(int idPoint = 1; idPoint < diffPoints.size() - 2; idPoint++) {
            pathParameters.e += diffPoints.get(idPoint).coordinate.distance(diffPoints.get(idPoint+1).coordinate);
        }
        pathParameters.deltaSPrimeRH = sPrimeR.orientationIndex(c0)*(dSPrimeO + pathParameters.e + dOnR - dSPrimeR);
        pathParameters.deltaSPrimeRF = toCurve(dSPrimeO, dSPrimeR) + toCurve(pathParameters.e, dSPrimeR) + toCurve(dOnR, dSPrimeR) - toCurve(dSPrimeR, dSPrimeR);

        LineSegment sRPrime = new LineSegment(src, seg2.rPrime);
        double dSRPrime = src.distance(seg2.rPrime);
        double dORPrime = cn.distance(seg2.rPrime);
        pathParameters.deltaSRPrimeH = (src.x>seg2.rPrime.x?-1:1)*sRPrime.orientationIndex(cn)*(dSO0 + pathParameters.e + dORPrime - dSRPrime);
        pathParameters.deltaSRPrimeF = toCurve(dSO0, dSRPrime) + toCurve(pathParameters.e, dSRPrime) + toCurve(dORPrime, dSRPrime) - toCurve(dSRPrime, dSRPrime);

        Coordinate srcPrime = new Coordinate(src.x + (seg1.sMeanPlane.x - src.x) * 2, src.y + (seg1.sMeanPlane.y - src.y) * 2);
        Coordinate rcvPrime = new Coordinate(rcv.x + (seg2.rMeanPlane.x - rcv.x) * 2, rcv.y + (seg2.rMeanPlane.y - rcv.y) * 2);

        LineSegment dSPrimeRPrime = new LineSegment(srcPrime, rcvPrime);
        srPath.dPrime = srcPrime.distance(rcvPrime);
        seg1.dPrime = srcPrime.distance(c0);
        seg2.dPrime = cn.distance(rcvPrime);


        long difVPointCount = pathParameters.getPointList().stream().
                filter(pointPath -> pointPath.type.equals(DIFV)).count();
        double distance = difVPointCount == 0 ? pathParameters.getSRSegment().d : pathParameters.getSRSegment().dc;
        pathParameters.deltaH = sr.orientationIndex(c0) * (dSO0 + pathParameters.e + dOnR - distance);
        if (sr.orientationIndex(c0) == 1) {
            pathParameters.deltaF = toCurve(seg1.d, srPath.d) + toCurve(pathParameters.e, srPath.d) + toCurve(seg2.d, srPath.d) - toCurve(srPath.d, srPath.d);
        } else {
            Coordinate pA = sr.pointAlong((c0.x - srcPrime.x) / (rcvPrime.x - srcPrime.x));
            pathParameters.deltaF = 2 * toCurve(srcPrime.distance(pA), srPath.dPrime) + 2 * toCurve(pA.distance(rcvPrime), srPath.dPrime) - toCurve(seg1.dPrime, srPath.dPrime) - toCurve(seg2.dPrime, srPath.dPrime) - toCurve(srPath.dPrime, srPath.dPrime);
        }

        pathParameters.deltaPrimeH = dSPrimeRPrime.orientationIndex(c0) * (seg1.dPrime + pathParameters.e + seg2.dPrime - srPath.dPrime);

        pathParameters.deltaPrimeH = dSPrimeRPrime.orientationIndex(c0) * (seg1.dPrime + seg2.dPrime - srPath.dPrime);
        if(dSPrimeRPrime.orientationIndex(c0) == 1) {
            pathParameters.deltaPrimeF = toCurve(seg1.dPrime, srPath.dPrime) + toCurve(seg2.dPrime, srPath.dPrime) - toCurve(srPath.dPrime, srPath.dPrime);
        } else {
            Coordinate pA = dSPrimeRPrime.pointAlong((c0.x-srcPrime.x)/(rcvPrime.x-srcPrime.x));
            pathParameters.deltaPrimeF =2*toCurve(srcPrime.distance(pA), srPath.dPrime) + 2*toCurve(pA.distance(srcPrime), srPath.dPrime) - toCurve(seg1.dPrime, srPath.dPrime) - toCurve(seg2.dPrime, srPath.d) - toCurve(srPath.dPrime, srPath.dPrime);
        }

        return pathParameters;
    }


    /**
     *
     * @param sourceOrientation
     * @param src
     * @param next
     * @return
     */
    private static Orientation computeOrientation(Orientation sourceOrientation, Coordinate src, Coordinate next){
        if(sourceOrientation == null) {
            return null;
        }
        Vector3D outgoingRay = new Vector3D(new Coordinate(next.x - src.x,
                next.y - src.y,
                next.z - src.z)).normalize();
        return Orientation.fromVector(Orientation.rotate(sourceOrientation, outgoingRay, true), 0);
    }
}
