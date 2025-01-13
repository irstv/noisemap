/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.path;

import org.locationtech.jts.algorithm.Intersection;
import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.ItemVisitor;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Wall;

import java.util.ArrayList;
import java.util.List;

public class MirrorReceiversCompute {
    private static final double DEFAULT_CIRCLE_POINT_ANGLE = Math.PI / 24;
    STRtree mirrorReceiverTree;
    public static final int DEFAULT_MIRROR_RECEIVER_CAPACITY = 50000;
    private int mirrorReceiverCapacity = DEFAULT_MIRROR_RECEIVER_CAPACITY;
    private final Coordinate receiverCoordinate;
    private final List<Wall> buildWalls;
    private final double maximumDistanceFromWall;
    private final double maximumPropagationDistance;
    int numberOfImageReceivers = 0;

    public static Polygon createWallReflectionVisibilityCone(Coordinate receiverImage, LineSegment wall,
                                                             double maximumPropagationDistance,
                                                             double maximumDistanceFromWall) {
        double distanceMin = wall.distance(receiverImage);

        GeometryFactory factory = new GeometryFactory();
        if(distanceMin > maximumPropagationDistance) {
            return factory.createPolygon();
        }
        ArrayList<Coordinate> circleSegmentPoints = new ArrayList<>();

        Vector2D rP0 = new Vector2D(receiverImage, wall.p0).normalize();
        Vector2D rP1 = new Vector2D(receiverImage, wall.p1).normalize();
        double angleSign = rP0.angleTo(rP1) >= 0 ? 1 : -1;
        int numberOfStep = Math.max(1, (int)(Math.abs(rP0.angleTo(rP1)) / DEFAULT_CIRCLE_POINT_ANGLE));
        Coordinate lastWallIntersectionPoint = new Coordinate();
        for(int angleStep = 0 ; angleStep <= numberOfStep; angleStep++) {
            Vector2D newPointTranslationVector = rP0.rotate(DEFAULT_CIRCLE_POINT_ANGLE * angleSign * angleStep);
            if(angleStep == numberOfStep) {
                newPointTranslationVector = rP1;
            } else if(angleStep == 0) {
                newPointTranslationVector = rP0;
            }
            Coordinate newPoint = newPointTranslationVector.translate(receiverImage);
            Coordinate wallIntersectionPoint = Intersection.intersection(wall.p0, wall.p1, receiverImage, newPoint);
            if(wallIntersectionPoint != null) {
                double wallIntersectionPointDistance = wallIntersectionPoint.distance(receiverImage);
                if (wallIntersectionPointDistance < maximumPropagationDistance) {
                    double vectorLength = Math.min(wallIntersectionPointDistance + maximumDistanceFromWall, maximumPropagationDistance);
                    newPoint = newPointTranslationVector.multiply(vectorLength).translate(receiverImage);
                    if (circleSegmentPoints.isEmpty()) {
                        circleSegmentPoints.add(wallIntersectionPoint);
                    }
                    lastWallIntersectionPoint = wallIntersectionPoint;
                    circleSegmentPoints.add(newPoint);
                }
            }
        }
        if(!circleSegmentPoints.isEmpty()) {
            circleSegmentPoints.add(lastWallIntersectionPoint);
            circleSegmentPoints.add(circleSegmentPoints.get(0));
            Coordinate[] conePolygon = circleSegmentPoints.toArray(new Coordinate[0]);
            return factory.createPolygon(conePolygon);
        } else {
            return factory.createPolygon();
        }
    }
    /**
     * Generate all image receivers from the provided list of walls
     * @param buildWalls
     * @param receiverCoordinates
     * @param reflectionOrder
     */
    public MirrorReceiversCompute(List<Wall> buildWalls, Coordinate receiverCoordinates,
                                  int reflectionOrder, double maximumPropagationDistance,
                                  double maximumDistanceFromWall) {
        GeometryFactory gf = new GeometryFactory();
        this.receiverCoordinate = receiverCoordinates;
        this.buildWalls = buildWalls;
        this.maximumDistanceFromWall = maximumDistanceFromWall;
        this.maximumPropagationDistance = maximumPropagationDistance;
        mirrorReceiverTree = new STRtree();
        ArrayList<MirrorReceiver> parentsToProcess = new ArrayList<>();
        for(int currentDepth = 0; currentDepth < reflectionOrder; currentDepth++) {
            if(currentDepth == 0) {
                parentsToProcess.add(null);
            }
            ArrayList<MirrorReceiver> nextParentsToProcess = new ArrayList<>();
            for(MirrorReceiver parent : parentsToProcess) {
                for (Wall wall : buildWalls) {
                    if(parent != null) {
                        // check if the wall is visible from the previous image receiver
                        if(!parent.getImageReceiverVisibilityCone().intersects(
                                wall.getLineSegment().toGeometry(new GeometryFactory()))) {
                            continue; // this wall is out of the bound of the receiver visibility
                        }
                    }
                    Coordinate receiverImage;
                    if (parent != null) {
                        if(wall == parent.getWall()) {
                            continue;
                        } else {
                            receiverImage = parent.getReceiverPos();
                        }
                    } else {
                        receiverImage = receiverCoordinates;
                    }
                    //Calculate the coordinate of projection
                    Coordinate proj = wall.getLineSegment().project(receiverImage);
                    Coordinate rcvMirror = new Coordinate(2 * proj.x - receiverImage.x,
                            2 * proj.y - receiverImage.y, receiverImage.z);
                    if(wall.getLineSegment().distance(rcvMirror) > maximumPropagationDistance) {
                        // wall is too far from the receiver image, there is no receiver image
                        continue;
                    }
                    // Walls that belong to a building (polygon) does not create image receiver
                    // from the two sides of the wall
                    // Exterior polygons are CW we can check if the receiver is on the reflective side of the wall
                    // (on the exterior side of the wall)
                    if(wall.getType() == ProfileBuilder.IntersectionType.BUILDING &&
                            !wallPointTest(wall.getLineSegment(), receiverImage)) {
                        continue;
                    }
                    // create the visibility cone of this receiver image
                    Polygon imageReceiverVisibilityCone = createWallReflectionVisibilityCone(rcvMirror,
                            wall.getLineSegment(), maximumPropagationDistance, maximumDistanceFromWall);
                    MirrorReceiver receiverResultNext = new MirrorReceiver(rcvMirror, parent, wall);
                    receiverResultNext.setImageReceiverVisibilityCone(imageReceiverVisibilityCone);
                    mirrorReceiverTree.insert(imageReceiverVisibilityCone.getEnvelopeInternal(),receiverResultNext.copyWithoutCone());
                    nextParentsToProcess.add(receiverResultNext);
                    numberOfImageReceivers++;
                    if(numberOfImageReceivers >= mirrorReceiverCapacity) {
                        return;
                    }
                }
            }
            parentsToProcess = nextParentsToProcess;
        }
        mirrorReceiverTree.build();
    }
    /**
     * Occlusion test between one wall and a viewer.
     * Simple Feature Access (ISO 19125-1) say that:
     * On polygon exterior ring are CCW, and interior rings are CW.
     * @param wall1 Wall segment
     * @param pt Observer
     * @return True if the wall is oriented to the point, false if the wall Occlusion Culling (transparent)
     */
    public static boolean wallPointTest(LineSegment wall1, Coordinate pt) {
        return org.locationtech.jts.algorithm.Orientation.isCCW(new Coordinate[]{wall1.getCoordinate(0),
                wall1.getCoordinate(1), pt, wall1.getCoordinate(0)});
    }

    public int getMirrorReceiverCapacity() {
        return mirrorReceiverCapacity;
    }

    public void setMirrorReceiverCapacity(int mirrorReceiverCapacity) {
        this.mirrorReceiverCapacity = mirrorReceiverCapacity;
    }

    public void exportVisibility(StringBuilder sb, double maxPropagationDistance,
                                 double maxPropagationDistanceFromWall, int t, List<MirrorReceiver> MirrorReceiverList, boolean includeHeader) {
        WKTWriter wktWriter = new WKTWriter();
        GeometryFactory factory = new GeometryFactory();
        if(includeHeader) {
            sb.append("the_geom,type,ref_index,ref_order,wall_id,t\n");
        }
        int refIndex = 0;
        for (MirrorReceiver res : MirrorReceiverList) {
            Polygon visibilityCone = createWallReflectionVisibilityCone(
                    res.getReceiverPos(), res.getWall().getLineSegment(),
                    maxPropagationDistance, maxPropagationDistanceFromWall);
            if(!visibilityCone.isEmpty()) {
                int refOrder=1;
                MirrorReceiver parent = res.getParentMirror();
                while (parent != null) {
                    refOrder++;
                    parent = parent.getParentMirror();
                }

                while(res != null) {
                    sb.append("\"");
                    sb.append(wktWriter.write(visibilityCone));
                    sb.append("\",0");
                    sb.append(",").append(refIndex);
                    sb.append(",").append(refOrder);
                    sb.append(",").append(res.getWall().getProcessedWallIndex());
                    sb.append(",").append(t).append("\n");
                    sb.append("\"");
                    sb.append(wktWriter.write(factory.createPoint(res.getReceiverPos()).buffer(0.1,
                            12, BufferParameters.CAP_ROUND)));
                    sb.append("\",4");
                    sb.append(",").append(refIndex);
                    sb.append(",").append(refOrder);
                    sb.append(",").append(res.getWall().getProcessedWallIndex());
                    sb.append(",").append(t).append("\n");
                    sb.append("\"");
                    sb.append(wktWriter.write(factory.createLineString(new Coordinate[]{res.getWall().p0, res.getWall().p1}).
                            buffer(0.05, 8, BufferParameters.CAP_SQUARE)));
                    sb.append("\",1");
                    sb.append(",").append(refIndex);
                    sb.append(",").append(refOrder);
                    sb.append(",").append(res.getWall().getProcessedWallIndex());
                    sb.append(",").append(t).append("\n");
                    res = res.getParentMirror();
                    if(res != null) {
                        visibilityCone = createWallReflectionVisibilityCone(
                                res.getReceiverPos(), res.getWall().getLineSegment(),
                                maxPropagationDistance, maxPropagationDistanceFromWall);
                    }
                    refOrder-=1;
                }
                refIndex+=1;
            }
        }
        sb.append("\"");
        sb.append(wktWriter.write(factory.createPoint(receiverCoordinate).buffer(0.1, 12, BufferParameters.CAP_ROUND)));
        sb.append("\",2");
        sb.append(",").append(t).append("\n");
    }

    public List<MirrorReceiver> findCloseMirrorReceivers(Coordinate sourcePosition) {
        if(Double.isNaN(sourcePosition.z)) {
            throw new IllegalArgumentException("Not supported NaN z value");
        }
        Envelope env = new Envelope(sourcePosition);
        ReceiverImageVisitor receiverImageVisitor = new ReceiverImageVisitor(buildWalls, sourcePosition,
                receiverCoordinate, maximumDistanceFromWall, maximumPropagationDistance);
        mirrorReceiverTree.query(env, receiverImageVisitor);
        return receiverImageVisitor.result;
    }

    private static class ReceiverImageVisitor implements ItemVisitor {
        List<MirrorReceiver> result = new ArrayList<>();
        List<Wall> buildWalls;
        Coordinate source;
        Coordinate receiver;
        LineSegment sourceReceiverSegment;
        double maximumDistanceFromSegment;
        double maximumPropagationDistance;
        int visitedNode = 0;

        public ReceiverImageVisitor(List<Wall> buildWalls, Coordinate source, Coordinate receiver,
                                    double maximumDistanceFromSegment,
                                    double maximumPropagationDistance) {
            this.buildWalls = buildWalls;
            this.source = source;
            this.receiver = receiver;
            this.sourceReceiverSegment = new LineSegment(source, receiver);
            this.maximumDistanceFromSegment = maximumDistanceFromSegment;
            this.maximumPropagationDistance = maximumPropagationDistance;
        }

        @Override
        public void visitItem(Object item) {
            visitedNode++;
            // try to excluded walls without taking into account the topography and other factors

            MirrorReceiver receiverImage = (MirrorReceiver) item;
            // Check propagation distance
            if(receiverImage.getReceiverPos().distance3D(source) < maximumPropagationDistance) {
                // Check distance of walls
                MirrorReceiver currentReceiverImage = receiverImage;
                Coordinate reflectionPoint = source;
                while (currentReceiverImage != null) {
                    final Wall currentWall = currentReceiverImage.getWall();
                    final LineSegment currentWallLineSegment = currentWall.getLineSegment();
                    if (currentWallLineSegment.distance(sourceReceiverSegment) > maximumDistanceFromSegment) {
                        return;
                    }
                    // Check if reflection is placed on the wall segment
                    LineSegment srcMirrRcvLine = new LineSegment(currentReceiverImage.getReceiverPos(), reflectionPoint);
                    LineIntersector li = new RobustLineIntersector();
                    li.computeIntersection(currentWallLineSegment.p0, currentWallLineSegment.p1,
                            srcMirrRcvLine.p0, srcMirrRcvLine.p1);
                    if(!li.hasIntersection()) {
                        // No reflection on this wall
                        return;
                    } else{
                        // update reflection point for inferior reflection order
                        reflectionPoint = li.getIntersection(0);
                    }
                    currentReceiverImage = currentReceiverImage.getParentMirror();
                }
                // not rejected
                result.add(receiverImage);
            }
        }
    }
}
