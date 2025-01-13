/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import org.apache.commons.math3.geometry.euclidean.threed.Line;
import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.h2gis.api.ProgressVisitor;
import org.locationtech.jts.algorithm.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.math.Vector3D;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.path.*;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiversCompute;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.*;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProfilerThread;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ReceiverStatsMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Double.isNaN;
import static java.lang.Math.*;
import static org.noise_planet.noisemodelling.pathfinder.PathFinder.ComputationSide.LEFT;
import static org.noise_planet.noisemodelling.pathfinder.PathFinder.ComputationSide.RIGHT;

/**
 * @author Nicolas Fortin
 * @author Pierre Aumond
 * @author Sylvain Palominos
 */
public class PathFinder {
    // distance from wall for reflection points and diffraction points
    private static final double NAVIGATION_POINT_DISTANCE_FROM_WALLS = ProfileBuilder.MILLIMETER;
    private static final double epsilon = 1e-7;
    private static final double MAX_RATIO_HULL_DIRECT_PATH = 4;
    public static final Logger LOGGER = LoggerFactory.getLogger(PathFinder.class);

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public Scene getData() {
        return data;
    }
    /** Propagation data to use for computation. */
    private final Scene data;

    /** Number of thread used for ray computation. */
    private int threadCount ;
    private ProfilerThread profilerThread;

    /**
     * Create new instance from the propagation data.
     * @param data Propagation data used for ray computation.
     */
    public PathFinder(Scene data) {
        this.data = data;
        this.threadCount = Runtime.getRuntime().availableProcessors();
    }

    /**
     * Computation stacks and timing are collected by this class in order
     * to profile the execution of the simulation
     * @return Instance of ProfilerThread or null
     */
    public ProfilerThread getProfilerThread() {
        return profilerThread;
    }

    /**
     * Computation stacks and timing are collected by this class in order
     * to profile the execution of the simulation
     * @param profilerThread Instance of ProfilerThread
     */
    public void setProfilerThread(ProfilerThread profilerThread) {
        this.profilerThread = profilerThread;
    }

    /**
     * Sets the number of thread to use.
     * @param threadCount Number of thread.
     */
    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Run computation and store the results in the given output.
     * @param computeRaysOut Result output.
     */
    public void run(IComputePathsOut computeRaysOut) {
        ProgressVisitor visitor = data.cellProg;
        ThreadPool threadManager = new ThreadPool(threadCount, threadCount + 1, Long.MAX_VALUE, TimeUnit.SECONDS);
        int maximumReceiverBatch = (int) ceil(data.receivers.size() / (double) threadCount);
        int endReceiverRange = 0;
        //Launch execution of computation by batch
        List<Future<Boolean>> tasks = new ArrayList<>();
        while (endReceiverRange < data.receivers.size()) {
            //Break if the progress visitor is cancelled
            if (visitor != null && visitor.isCanceled()) {
                break;
            }
            int newEndReceiver = min(endReceiverRange + maximumReceiverBatch, data.receivers.size());
            ThreadPathFinder batchThread = new ThreadPathFinder(endReceiverRange, newEndReceiver,
                    this, visitor, computeRaysOut, data);
            if (threadCount != 1) {
                tasks.add(threadManager.submitBlocking(batchThread));
            } else {
                try {
                    batchThread.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            endReceiverRange = newEndReceiver;
        }
        //Once the execution ends, shutdown the thread manager and await termination
        threadManager.shutdown();
        try {
            if(!threadManager.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                LOGGER.warn("Timeout elapsed before termination.");
            }
        } catch (InterruptedException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
        // Must raise an exception if one the thread raised an exception
        for (Future<Boolean> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

    }

    /**
     * Compute the rays to the given receiver.
     * @param rcv     Receiver point.
     * @param dataOut Computation output.
     * @param visitor Progress visitor used for cancellation and progression managing.
     */
    public void computeRaysAtPosition(ReceiverPointInfo rcv, IComputePathsOut dataOut, ProgressVisitor visitor) {
        MirrorReceiversCompute receiverMirrorIndex = null;

        if(data.reflexionOrder > 0) {
            Envelope receiverPropagationEnvelope = new Envelope(rcv.getCoordinates());
            receiverPropagationEnvelope.expandBy(data.maxSrcDist);
            List<Wall> buildWalls = data.profileBuilder.getWallsIn(receiverPropagationEnvelope);
            receiverMirrorIndex = new MirrorReceiversCompute(buildWalls, rcv.position, data.reflexionOrder,
                    data.maxSrcDist, data.maxRefDist);
        }

        //Compute the source search area
        double searchSourceDistance = data.maxSrcDist;
        Envelope receiverSourceRegion = new Envelope(
                rcv.getCoordinates().x - searchSourceDistance,
                rcv.getCoordinates().x + searchSourceDistance,
                rcv.getCoordinates().y - searchSourceDistance,
                rcv.getCoordinates().y + searchSourceDistance
        );
        Iterator<Integer> regionSourcesLst = data.sourcesIndex.query(receiverSourceRegion);
        List<SourcePointInfo> sourceList = new ArrayList<>();
        //Already processed Raw source (line and/or points)
        HashSet<Integer> processedLineSources = new HashSet<>();
        while (regionSourcesLst.hasNext()) {
            Integer srcIndex = regionSourcesLst.next();
            if (!processedLineSources.contains(srcIndex)) {
                processedLineSources.add(srcIndex);
                Geometry source = data.sourceGeometries.get(srcIndex);
                if (source instanceof Point) {
                    Coordinate ptpos = source.getCoordinate();
                    if (ptpos.distance(rcv.getCoordinates()) < data.maxSrcDist) {
                        Orientation orientation = null;
                        if(data.sourcesPk.size() > srcIndex) {
                            orientation = data.sourceOrientation.get(data.sourcesPk.get(srcIndex));
                        }
                        if(orientation == null) {
                            orientation = new Orientation(0,0, 0);
                        }
                        insertPtSource(ptpos, srcIndex, sourceList, 1., orientation);
                    }
                } else if (source instanceof LineString) {
                    addLineSource((LineString) source, rcv.getCoordinates(), srcIndex, sourceList);
                } else if (source instanceof MultiLineString) {
                    for (int id = 0; id < source.getNumGeometries(); id++) {
                        Geometry subGeom = source.getGeometryN(id);
                        if (subGeom instanceof LineString) {
                            addLineSource((LineString) subGeom, rcv.getCoordinates(), srcIndex, sourceList);
                        }
                    }
                } else {
                    throw new IllegalArgumentException(
                            String.format("Sound source %s geometry are not supported", source.getGeometryType()));
                }
            }
        }
        // Sort sources by power contribution descending
        Collections.sort(sourceList);

        // For each Pt Source - Pt Receiver
        AtomicInteger raysCount = new AtomicInteger(0);
        for (SourcePointInfo src : sourceList) {
            IComputePathsOut.PathSearchStrategy strategy = rcvSrcPropagation(src, rcv, dataOut, raysCount, receiverMirrorIndex);
            // If the delta between already received power and maximal potential power received is inferior to data.maximumError
            if ((visitor != null && visitor.isCanceled()) ||  !strategy.equals(IComputePathsOut.PathSearchStrategy.CONTINUE)) {
                break; //Stop looking for more rays
            }
        }

        if(profilerThread != null &&
                profilerThread.getMetric(ReceiverStatsMetric.class) != null) {
            profilerThread.getMetric(ReceiverStatsMetric.class).onReceiverRays(rcv.getId(), raysCount.get());
        }

        // No more rays for this receiver
        dataOut.finalizeReceiver(rcv.getId());
    }

    /**
     * Calculation of the propagation between the given source and receiver. The result is registered in the given
     * output.
     * @param src     Source point.
     * @param rcv     Receiver point.
     * @param dataOut Output.
     * @return Continue or not looking for propagation paths
     */
    private IComputePathsOut.PathSearchStrategy rcvSrcPropagation(SourcePointInfo src,
                                                                  ReceiverPointInfo rcv,
                                                                  IComputePathsOut dataOut, AtomicInteger raysCount,
                                                                  MirrorReceiversCompute receiverMirrorIndex) {
        IComputePathsOut.PathSearchStrategy strategy = IComputePathsOut.PathSearchStrategy.CONTINUE;
        double propaDistance = src.getCoord().distance(rcv.getCoordinates());
        if (propaDistance < data.maxSrcDist) {
            // Process direct : horizontal and vertical diff
            strategy = directPath(src, rcv, data.computeVerticalDiffraction,
                    data.computeHorizontalDiffraction, dataOut);
            if(!strategy.equals(IComputePathsOut.PathSearchStrategy.CONTINUE)) {
                return strategy;
            }
            // Process reflection
            if (data.reflexionOrder > 0) {
                strategy = computeReflexion(rcv, src, receiverMirrorIndex, dataOut);
                if(!strategy.equals(IComputePathsOut.PathSearchStrategy.CONTINUE)) {
                    return strategy;
                }
            }
        }
        return strategy;
    }

    /**
     * Direct Path computation.
     * @param src Source point coordinate.
     * @param rcv Receiver point coordinate.
     * @param verticalDiffraction Enable vertical diffraction
     * @param horizontalDiffraction Enable horizontal diffraction
     * @return Calculated propagation paths.
     */
    public IComputePathsOut.PathSearchStrategy directPath(SourcePointInfo src, ReceiverPointInfo rcv,
                                                          boolean verticalDiffraction, boolean horizontalDiffraction,
                                                          IComputePathsOut dataOut) {

        IComputePathsOut.PathSearchStrategy strategy = IComputePathsOut.PathSearchStrategy.CONTINUE;

        CutProfile cutProfile = data.profileBuilder.getProfile(src.position, rcv.position, data.gS, !verticalDiffraction);
        if(cutProfile.getSource() != null) {
            cutProfile.getSource().id = src.getSourceIndex();
            cutProfile.getSource().li = src.li;
            cutProfile.getSource().orientation = src.getOrientation();
            if(src.sourceIndex >= 0 && src.sourceIndex < data.sourcesPk.size()) {
                cutProfile.getSource().sourcePk = data.sourcesPk.get(src.getSourceIndex());
            }
        }

        if(cutProfile.getReceiver() != null) {
            cutProfile.getReceiver().id = rcv.getId();
            if(rcv.receiverIndex >= 0 && rcv.receiverIndex < data.receiversPk.size()) {
                cutProfile.getReceiver().receiverPk = data.receiversPk.get(rcv.getId());
            }
        }


        if(verticalDiffraction || cutProfile.isFreeField()) {
            strategy = dataOut.onNewCutPlane(cutProfile);
            if(!strategy.equals(IComputePathsOut.PathSearchStrategy.CONTINUE)) {
                return strategy;
            }
        }

        // do not do horizontal plane diffraction if there is no obstacles between source and receiver
        // ISO/TR 17534-4:2020
        // "As a general principle, lateral diffraction is considered only if the direct line of sight
        // between source and receiver is blocked and does not penetrate the terrain profile.
        // In addition, the source must not be a mirror source due to reflection"
        if (horizontalDiffraction && !cutProfile.isFreeField()) {
            CutProfile cutProfileRight = computeVEdgeDiffraction(rcv, src, data, RIGHT);
            if (cutProfileRight != null) {
                strategy = dataOut.onNewCutPlane(cutProfileRight);
                if(!strategy.equals(IComputePathsOut.PathSearchStrategy.CONTINUE)) {
                    return strategy;
                }
            }
            CutProfile cutProfileLeft = computeVEdgeDiffraction(rcv, src, data, LEFT);
            if (cutProfileLeft != null) {
                strategy = dataOut.onNewCutPlane(cutProfileLeft);
                if(!strategy.equals(IComputePathsOut.PathSearchStrategy.CONTINUE)) {
                    return strategy;
                }
            }
        }

        return strategy;
    }

    /**
     * Compute horizontal diffraction (diffraction of vertical edge.)
     * @param rcv Receiver coordinates.
     * @param src Source coordinates.
     * @param data     Propagation data.
     * @param side     Side to compute. From Source to receiver coordinates
     * @return The propagation path of the horizontal diffraction.
     */
    public CutProfile computeVEdgeDiffraction(ReceiverPointInfo rcv, SourcePointInfo src,
                                               Scene data, ComputationSide side) {

        List<Coordinate> coordinates = computeSideHull(side == LEFT, new Coordinate(src.position),
                new Coordinate(rcv.position), data.profileBuilder);

        List<CutPoint> cutPoints = new ArrayList<>();

        if(coordinates.size() > 2) {
            // Fetch vertical profile between each point of the diffraction path
            for(int i=0; i<coordinates.size()-1; i++) {
                CutProfile profile = data.profileBuilder.getProfile(coordinates.get(i), coordinates.get(i+1), data.gS,
                        false);

                // Push new plane (except duplicate points for intermediate segments)
                if( i > 0 ) {
                    // update first point as it is not source but diffraction point
                    cutPoints.add(new CutPointVEdgeDiffraction(profile.getSource()));
                } else {
                    profile.getSource().id = src.sourceIndex;
                    cutPoints.add(profile.getSource());
                }
                cutPoints.addAll(profile.cutPoints.subList(1, profile.cutPoints.size() - 1));
                if(i+1 == coordinates.size() - 1) {
                    // we keep the last point as it is really the receiver
                    profile.getReceiver().id = rcv.receiverIndex;
                    cutPoints.add(profile.getReceiver());
                }
            }
            CutProfile mainProfile = new CutProfile((CutPointSource) cutPoints.get(0),
                    (CutPointReceiver) cutPoints.get(cutPoints.size() -  1));
            mainProfile.insertCutPoint(false,
                    cutPoints.subList(1, cutPoints.size() - 1).toArray(CutPoint[]::new));

            mainProfile.getReceiver().id = rcv.receiverIndex;
            if(rcv.receiverIndex >= 0 && rcv.receiverIndex < data.receiversPk.size()) {
                mainProfile.getReceiver().receiverPk = data.receiversPk.get(rcv.receiverIndex);
            }
            mainProfile.getSource().id = src.sourceIndex;
            if(src.sourceIndex >= 0 && src.sourceIndex < data.sourcesPk.size()) {
                mainProfile.getSource().sourcePk = data.sourcesPk.get(src.sourceIndex);
            }

            mainProfile.getSource().orientation = src.orientation;
            mainProfile.getSource().li = src.li;

            return mainProfile;
        }
        return null;
    }


    /**
     * Compute Side Hull
     * Create a line between p1 and p2. Find the first intersection of this line with a building then create a ConvexHull
     * with the points of buildings in intersection. While there is an intersection add more points to the convex hull.
     * The side diffraction path is found when there is no more intersection.
     *
     * @param left If true return the path on the left side between p1 and p2; else on the right side
     * @param p1   First point
     * @param p2   Second point
     * @return
     */
    public List<Coordinate> computeSideHull(boolean left, Coordinate p1, Coordinate p2, ProfileBuilder profileBuilder) {
        if (p1.equals(p2)) {
            return new ArrayList<>();
        }

        // Intersection test cache
        Set<LineSegment> freeFieldSegments = new HashSet<>();

        List<Coordinate> input = new ArrayList<>();

        Coordinate[] coordinates = new Coordinate[0];
        int indexp1 = 0;
        int indexp2 = 0;

        boolean convexHullIntersects = true;

        input.add(p1);
        input.add(p2);

        Plane cutPlane = computeZeroRadPlane(p1, p2);

        BuildingIntersectionPathVisitor buildingIntersectionPathVisitor = new BuildingIntersectionPathVisitor(p1, p2, left,
                profileBuilder, input, cutPlane);

        data.profileBuilder.getWallsOnPath(p1, p2, buildingIntersectionPathVisitor);

        int k;
        while (convexHullIntersects) {
            ConvexHull convexHull = new ConvexHull(input.toArray(new Coordinate[0]), GEOMETRY_FACTORY);
            Geometry convexhull = convexHull.getConvexHull();

            if (convexhull.getLength() / p1.distance(p2) > MAX_RATIO_HULL_DIRECT_PATH) {
                return new ArrayList<>();
            }

            convexHullIntersects = false;
            coordinates = convexhull.getCoordinates();

            input.clear();
            input.addAll(Arrays.asList(coordinates));

            indexp1 = -1;
            for (int i = 0; i < coordinates.length - 1; i++) {
                if (coordinates[i].equals(p1)) {
                    indexp1 = i;
                    break;
                }
            }
            if (indexp1 == -1) {
                // P1 does not belong to convex vertices, cannot compute diffraction
                // TODO handle concave path
                return new ArrayList<>();
            }
            // Transform array to set p1 at index=0
            Coordinate[] coordinatesShifted = new Coordinate[coordinates.length];
            // Copy from P1 to end in beginning of new array
            int len = (coordinates.length - 1) - indexp1;
            System.arraycopy(coordinates, indexp1, coordinatesShifted, 0, len);
            // Copy from 0 to P1 in the end of array
            System.arraycopy(coordinates, 0, coordinatesShifted, len, coordinates.length - len - 1);
            coordinatesShifted[coordinatesShifted.length - 1] = coordinatesShifted[0];
            coordinates = coordinatesShifted;
            indexp1 = 0;
            indexp2 = -1;
            for (int i = 1; i < coordinates.length - 1; i++) {
                if (coordinates[i].equals(p2)) {
                    indexp2 = i;
                    break;
                }
            }
            if (indexp2 == -1) {
                // P2 does not belong to convex vertices, cannot compute diffraction
                // TODO handle concave path
                return new ArrayList<>();
            }
            for (k = 0; k < coordinates.length - 1; k++) {
                LineSegment freeFieldTestSegment = new LineSegment(coordinates[k], coordinates[k + 1]);

                // Ignore intersection if iterating over other side (not parts of what is returned)
                if (left && k < indexp2 || !left && k >= indexp2) {
                    if (!freeFieldSegments.contains(freeFieldTestSegment)) {

                        int inputPointsBefore = input.size();

                        // Visit buildings that are between the provided hull points
                        profileBuilder.getWallsOnPath(coordinates[k], coordinates[k + 1], buildingIntersectionPathVisitor);

                        if (inputPointsBefore == input.size()) {
                            freeFieldSegments.add(freeFieldTestSegment);
                        } else {
                            convexHullIntersects = true;
                            break;
                        }
                    }
                }
            }
        }
        // Check for invalid coordinates
        for (Coordinate p : coordinates) {
            if (p.z < 0) {
                return new ArrayList<>();
            }
        }

        List<Coordinate> sideHullPath;
        if (left) {
            sideHullPath = Arrays.asList(Arrays.copyOfRange(coordinates, indexp1, indexp2 + 1));
        } else {
            List<Coordinate> inversePath = Arrays.asList(Arrays.copyOfRange(coordinates, indexp2, coordinates.length));
            Collections.reverse(inversePath);
            sideHullPath = inversePath;
        }
        return  sideHullPath;
    }

    /**
     *
     * @param p0
     * @param p1
     * @return
     */
    public static Plane computeZeroRadPlane(Coordinate p0, Coordinate p1) {
        org.apache.commons.math3.geometry.euclidean.threed.Vector3D s = new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(p0.x, p0.y, p0.z);
        org.apache.commons.math3.geometry.euclidean.threed.Vector3D r = new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(p1.x, p1.y, p1.z);
        double angle = atan2(p1.y - p0.y, p1.x - p0.x);
        // Compute rPrime, the third point of the plane that is at -PI/2 with SR vector
        org.apache.commons.math3.geometry.euclidean.threed.Vector3D rPrime = s.add(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(cos(angle - PI / 2), sin(angle - PI / 2), 0));
        Plane p = new Plane(r, s, rPrime, 1e-6);
        // Normal of the cut plane should be upward
        if (p.getNormal().getZ() < 0) {
            p.revertSelf();
        }
        return p;
    }

    /**
     * Remove points that are left or right of the provided segment
     * @param sr Source receiver segment
     * @param left Side to keep
     * @param segmentsCoordinates Roof points
     * @return Only points of the requested side
     */
    public static List<Coordinate> filterPointsBySide(LineSegment sr, boolean left,
                                                      List<Coordinate> segmentsCoordinates) {
        List<Coordinate> keptSegments = new ArrayList<>(segmentsCoordinates.size());
        for(Coordinate vertex : segmentsCoordinates) {
            int orientationIndex = sr.orientationIndex(vertex);
            if((orientationIndex == 1 && left) || (orientationIndex == -1 && !left)) {
                keptSegments.add(vertex);
            }
        }
        return keptSegments;
    }

    /**
     *
     * @param plane 3D plane with position and normal vector
     * @param roofPts Top altitude coordinates that create segments of verticals walls, these walls will be cut by
     *               the plane.
     * @return Remaining segments coordinates after the plane cutting
     */
    public static List<Coordinate> cutRoofPointsWithPlane(Plane plane, List<Coordinate> roofPts) {
        List<Coordinate> polyCut = new ArrayList<>(roofPts.size());
        double lastOffset = 0;
        Coordinate cPrev = null;
        for (Coordinate cCur : roofPts) {
            double offset = plane.getOffset(coordinateToVector(cCur));
            if (cPrev != null && ((offset >= 0 && lastOffset < 0) || (offset < 0 && lastOffset >= 0))) {
                // Interpolate vector
                org.apache.commons.math3.geometry.euclidean.threed.Vector3D i = plane.intersection(new Line(coordinateToVector(cPrev), coordinateToVector(cCur), epsilon));
                polyCut.add(new Coordinate(i.getX(), i.getY(), i.getZ()));
            }
            if (offset >= 0) {
                org.apache.commons.math3.geometry.euclidean.threed.Vector3D i = plane.intersection(new Line(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(cCur.x, cCur.y, Double.MIN_VALUE), coordinateToVector(cCur), epsilon));
                if (i != null) polyCut.add(new Coordinate(i.getX(), i.getY(), i.getZ()));
            }
            lastOffset = offset;
            cPrev = cCur;
        }
        return polyCut;
    }

    /**
     *
     * @param p coordinate
     * @return the three dimensions vector of p
     */
    public static org.apache.commons.math3.geometry.euclidean.threed.Vector3D coordinateToVector(Coordinate p) {
        return new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(p.x, p.y, p.z);
    }

    /**
     * Add points to the main profile, the last point is before the reflection on the wall
     * @param sourceOrReceiverPoint The point location recognised as a reflection point (currently is categorized as source or receiver)
     * @param mainProfileCutPoints The profile to add reflection point
     * @param mirrorReceiver Associated mirror receiver
     */
    private void insertReflectionPointAttributes(CutPoint sourceOrReceiverPoint, List<CutPoint> mainProfileCutPoints, MirrorReceiver mirrorReceiver) {
        CutPointReflection reflectionPoint = new CutPointReflection(sourceOrReceiverPoint,
                mirrorReceiver.getWall().getLineSegment(), mirrorReceiver.getWall().getAlphas());
        if(mirrorReceiver.wall.primaryKey >= 0) {
            reflectionPoint.wallPk = mirrorReceiver.wall.primaryKey;
        }
        mainProfileCutPoints.add(reflectionPoint);
    }


    /**
     *
     * @param rcv Receiver data
     * @param src Source data
     * @param receiverMirrorIndex Reflection information
     * @param dataOut Where to push cut profile
     * @return Skip or continue looking for vertical cut
     */
    public IComputePathsOut.PathSearchStrategy computeReflexion(ReceiverPointInfo rcv,
                                                                SourcePointInfo src,
                                                                MirrorReceiversCompute receiverMirrorIndex,
                                                                IComputePathsOut dataOut) {

        // Compute receiver mirror
        LineIntersector linters = new RobustLineIntersector();
        //Keep only building walls which are not too far.
        List<MirrorReceiver> mirrorResults = receiverMirrorIndex.findCloseMirrorReceivers(src.position);

        for (MirrorReceiver receiverReflection : mirrorResults) {
            Wall seg = receiverReflection.getWall();
            List<MirrorReceiver> rayPath = new ArrayList<>();
            MirrorReceiver receiverReflectionCursor = receiverReflection;
            // Test whether intersection point is on the wall
            // segment or not
            Coordinate destinationPt = new Coordinate(src.position);

            linters.computeIntersection(seg.p0, seg.p1,
                    receiverReflection.getReceiverPos(),
                    destinationPt);
            while (linters.hasIntersection()) {
                // There are a probable reflection point on the segment
                Coordinate reflectionPt = new Coordinate(
                        linters.getIntersection(0));
                if (reflectionPt.equals(destinationPt)) {
                    break;
                }
                Coordinate vec_epsilon = new Coordinate(
                        reflectionPt.x - destinationPt.x,
                        reflectionPt.y - destinationPt.y);
                double length = vec_epsilon
                        .distance(new Coordinate(0., 0., 0.));
                // Normalize vector
                vec_epsilon.x /= length;
                vec_epsilon.y /= length;
                // Multiply by epsilon in meter
                vec_epsilon.x *= NAVIGATION_POINT_DISTANCE_FROM_WALLS;
                vec_epsilon.y *= NAVIGATION_POINT_DISTANCE_FROM_WALLS;
                // Translate reflection pt by epsilon to get outside
                // the wall
                reflectionPt.x -= vec_epsilon.x;
                reflectionPt.y -= vec_epsilon.y;
                // Compute Z interpolation
                reflectionPt.setOrdinate(Coordinate.Z, Vertex.interpolateZ(linters.getIntersection(0),
                        receiverReflectionCursor.getReceiverPos(), destinationPt));
                MirrorReceiver reflResult = new MirrorReceiver(receiverReflectionCursor);
                reflResult.setReflectionPosition(reflectionPt);
                rayPath.add(reflResult);
                if (receiverReflectionCursor
                        .getParentMirror() == null) { // Direct to the receiver
                    break; // That was the last reflection
                } else {
                    // There is another reflection
                    destinationPt.setCoordinate(reflectionPt);
                    // Move reflection information cursor to a
                    // reflection closer
                    receiverReflectionCursor = receiverReflectionCursor.getParentMirror();
                    // Update intersection data
                    seg = receiverReflectionCursor.getWall();
                    linters.computeIntersection(seg.p0, seg.p1,
                            receiverReflectionCursor
                                    .getReceiverPos(),
                            destinationPt
                    );
                }
            }
            // Compute direct path between source and first reflection point, add profile to the data
            CutProfile cutProfile = data.profileBuilder.getProfile(src.position, rayPath.get(0).getReflectionPosition(),
                    data.gS, !data.computeVerticalDiffraction);
            if(!cutProfile.isFreeField() && !data.computeVerticalDiffraction) {
                // (maybe there is a blocking building/dem, and we disabled diffraction)
                continue;
            }

            // Add points to the main profile, remove the last point, or it will be duplicated later
            List<CutPoint> mainProfileCutPoints = new ArrayList<>(
                    cutProfile.cutPoints.subList(0, cutProfile.cutPoints.size() - 1));

            // Add intermediate reflections
            boolean validReflection = true;
            for (int idPt = 0; idPt < rayPath.size() - 1; idPt++) {
                MirrorReceiver firstPoint = rayPath.get(idPt);
                MirrorReceiver secondPoint = rayPath.get(idPt + 1);
                cutProfile = data.profileBuilder.getProfile(firstPoint.getReflectionPosition(),
                        secondPoint.getReflectionPosition(), data.gS, !data.computeVerticalDiffraction);
                if(!cutProfile.isFreeField() && !data.computeVerticalDiffraction) {
                    // (maybe there is a blocking building/dem, and we disabled diffraction)
                    continue;
                }
                if(!cutProfile.isFreeField() && !data.computeVerticalDiffraction) {
                    // (maybe there is a blocking building/dem, and we disabled diffraction)
                    validReflection = false;
                    break;
                }
                insertReflectionPointAttributes(cutProfile.cutPoints.get(0), mainProfileCutPoints, firstPoint);

                mainProfileCutPoints.addAll(cutProfile.cutPoints.subList(1, cutProfile.cutPoints.size() - 1));
            }
            if(!validReflection) {
                continue;
            }
            // Compute direct path between receiver and last reflection point, add profile to the data
            cutProfile = data.profileBuilder.getProfile(rayPath.get(rayPath.size() - 1).getReflectionPosition(),
                    rcv.position, data.gS, !data.computeVerticalDiffraction);
            if(!cutProfile.isFreeField() && !data.computeVerticalDiffraction) {
                // (maybe there is a blocking building/dem, and we disabled diffraction)
                continue;
            }
            insertReflectionPointAttributes(cutProfile.cutPoints.get(0), mainProfileCutPoints, rayPath.get(rayPath.size() - 1));
            mainProfileCutPoints.addAll(cutProfile.cutPoints.subList(1, cutProfile.cutPoints.size()));

            // A valid propagation path as been found (without looking at occlusion)
            CutProfile mainProfile = new CutProfile((CutPointSource) mainProfileCutPoints.get(0),
                    (CutPointReceiver) mainProfileCutPoints.get(mainProfileCutPoints.size() - 1));

            mainProfile.insertCutPoint(false, mainProfileCutPoints.subList(1,
                    mainProfileCutPoints.size() - 1).toArray(CutPoint[]::new));

            mainProfile.getReceiver().id = rcv.receiverIndex;
            if(rcv.receiverIndex >= 0 && rcv.receiverIndex < data.receiversPk.size()) {
                mainProfile.getReceiver().receiverPk = data.receiversPk.get(rcv.receiverIndex);
            }
            mainProfile.getSource().id = src.sourceIndex;
            if(src.sourceIndex >= 0 && src.sourceIndex < data.sourcesPk.size()) {
                mainProfile.getSource().sourcePk = data.sourcesPk.get(src.sourceIndex);
            }

            mainProfile.getSource().orientation = src.orientation;
            mainProfile.getSource().li = src.li;

            IComputePathsOut.PathSearchStrategy strategy = dataOut.onNewCutPlane(mainProfile);
            if(!strategy.equals(IComputePathsOut.PathSearchStrategy.CONTINUE)) {
                return strategy;
            }
        }
        return IComputePathsOut.PathSearchStrategy.CONTINUE;
    }

    /**
     * @param geom Geometry
     * @param segmentSizeConstraint Maximal distance between points
     * @return Fixed distance between points
     * @param pts computed points
     */
    public static double splitLineStringIntoPoints(LineString geom, double segmentSizeConstraint,
                                                   List<Coordinate> pts) {
        // If the linear sound source length is inferior than half the distance between the nearest point of the sound
        // source and the receiver then it can be modelled as a single point source
        double geomLength = geom.getLength();
        if (geomLength < segmentSizeConstraint) {
            // Return mid point
            Coordinate[] points = geom.getCoordinates();
            double segmentLength = 0;
            final double targetSegmentSize = geomLength / 2.0;
            for (int i = 0; i < points.length - 1; i++) {
                Coordinate a = points[i];
                final Coordinate b = points[i + 1];
                double length = a.distance3D(b);
                if (length + segmentLength > targetSegmentSize) {
                    double segmentLengthFraction = (targetSegmentSize - segmentLength) / length;
                    Coordinate midPoint = new Coordinate(a.x + segmentLengthFraction * (b.x - a.x),
                            a.y + segmentLengthFraction * (b.y - a.y),
                            a.z + segmentLengthFraction * (b.z - a.z));
                    pts.add(midPoint);
                    break;
                }
                segmentLength += length;
            }
            return geom.getLength();
        } else {
            double targetSegmentSize = geomLength / ceil(geomLength / segmentSizeConstraint);
            Coordinate[] points = geom.getCoordinates();
            double segmentLength = 0.;

            // Mid point of segmented line source
            Coordinate midPoint = null;
            for (int i = 0; i < points.length - 1; i++) {
                Coordinate a = points[i];
                final Coordinate b = points[i + 1];
                double length = a.distance3D(b);
                if (isNaN(length)) {
                    length = a.distance(b);
                }
                while (length + segmentLength > targetSegmentSize) {
                    double segmentLengthFraction = (targetSegmentSize - segmentLength) / length;
                    Coordinate splitPoint = new Coordinate();
                    splitPoint.x = a.x + segmentLengthFraction * (b.x - a.x);
                    splitPoint.y = a.y + segmentLengthFraction * (b.y - a.y);
                    splitPoint.z = a.z + segmentLengthFraction * (b.z - a.z);
                    if (midPoint == null && length + segmentLength > targetSegmentSize / 2) {
                        segmentLengthFraction = (targetSegmentSize / 2.0 - segmentLength) / length;
                        midPoint = new Coordinate(a.x + segmentLengthFraction * (b.x - a.x),
                                a.y + segmentLengthFraction * (b.y - a.y),
                                a.z + segmentLengthFraction * (b.z - a.z));
                    }
                    pts.add(midPoint);
                    a = splitPoint;
                    length = a.distance3D(b);
                    if (isNaN(length)) {
                        length = a.distance(b);
                    }
                    segmentLength = 0;
                    midPoint = null;
                }
                if (midPoint == null && length + segmentLength > targetSegmentSize / 2) {
                    double segmentLengthFraction = (targetSegmentSize / 2.0 - segmentLength) / length;
                    midPoint = new Coordinate(a.x + segmentLengthFraction * (b.x - a.x),
                            a.y + segmentLengthFraction * (b.y - a.y),
                            a.z + segmentLengthFraction * (b.z - a.z));
                }
                segmentLength += length;
            }
            if (midPoint != null) {
                pts.add(midPoint);
            }
            return targetSegmentSize;
        }
    }


    /**
     * Apply a linestring over the digital elevation model by offsetting the z value with the ground elevation.
     * @param lineString
     * @param profileBuilder
     * @param epsilon ignore elevation point where linear interpolation distance is inferior that this value
     * @return computed lineString
     */
    private static LineString splitLineSource(LineString lineString, ProfileBuilder profileBuilder, double epsilon) {
        ArrayList<Coordinate> newGeomCoordinates = new ArrayList<>();
        Coordinate[] coordinates = lineString.getCoordinates();
        for(int idPoint = 0; idPoint < coordinates.length - 1; idPoint++) {
            Coordinate p0 = coordinates[idPoint];
            Coordinate p1 = coordinates[idPoint + 1];
            List<Coordinate> groundProfileCoordinates = new ArrayList<>();
            profileBuilder.fetchTopographicProfile(groundProfileCoordinates, p0, p1, false);
            newGeomCoordinates.ensureCapacity(newGeomCoordinates.size() + groundProfileCoordinates.size());
            if(groundProfileCoordinates.size() < 2) {
                newGeomCoordinates.add(p0);
                newGeomCoordinates.add(p1);
            } else {
                if (idPoint == 0) {
                    newGeomCoordinates.add(new Coordinate(p0.x, p0.y, p0.z + groundProfileCoordinates.get(0).z));
                }
                Coordinate previous = groundProfileCoordinates.get(0);
                for (int groundPoint = 1; groundPoint < groundProfileCoordinates.size() - 1; groundPoint++) {
                    final Coordinate current = groundProfileCoordinates.get(groundPoint);
                    final Coordinate next = groundProfileCoordinates.get(groundPoint + 1);
                    // Do not add topographic points which are simply the linear interpolation between two points
                    // triangulation add a lot of interpolated lines from line segment DEM
                    if (CGAlgorithms3D.distancePointSegment(current, previous, next) >= epsilon) {
                        // interpolate the Z (height) values of the source then add the altitude
                        previous = current;
                        newGeomCoordinates.add(
                                new Coordinate(current.x, current.y, current.z + Vertex.interpolateZ(current, p0, p1)));
                    }
                }
                newGeomCoordinates.add(new Coordinate(p1.x, p1.y, p1.z +
                        groundProfileCoordinates.get(groundProfileCoordinates.size() - 1).z));
            }
        }
        return GEOMETRY_FACTORY.createLineString(newGeomCoordinates.toArray(new Coordinate[0]));
    }

    /**
     * Update ground Z coordinates of sound sources absolute to sea levels
     */
    public void makeSourceRelativeZToAbsolute() {
        List<Geometry> sourceCopy = new ArrayList<>(data.sourceGeometries.size());
        for (Geometry source : data.sourceGeometries) {
            Geometry offsetGeometry = source.copy();
            if(source instanceof LineString) {
                offsetGeometry = splitLineSource((LineString) source, data.profileBuilder, ProfileBuilder.MILLIMETER);
            } else if(source instanceof MultiLineString) {
                LineString[] newGeom = new LineString[source.getNumGeometries()];
                for(int idGeom = 0; idGeom < source.getNumGeometries(); idGeom++) {
                    newGeom[idGeom] = splitLineSource((LineString) source.getGeometryN(idGeom),
                            data.profileBuilder, ProfileBuilder.MILLIMETER);
                }
                offsetGeometry = GEOMETRY_FACTORY.createMultiLineString(newGeom);
            }
            // Offset the geometry with value of elevation for each coordinate
            sourceCopy.add(offsetGeometry);
        }
        data.sourceGeometries = sourceCopy;
    }

    /**
     * Update ground Z coordinates of sound sources and receivers absolute to sea levels
     */
    public void makeRelativeZToAbsolute() {
        makeSourceRelativeZToAbsolute();
        makeReceiverRelativeZToAbsolute();
    }

    /**
     * Update ground Z coordinates of receivers absolute to sea levels
     */
    public void makeReceiverRelativeZToAbsolute() {
        for(Coordinate receiver : data.receivers) {
            receiver.setZ(receiver.getZ() + data.profileBuilder.getZGround(receiver));
        }
    }


    /**
     * Compute maximal power at freefield at the receiver position with reflective ground
     * @param source
     * @param sourceId
     * @param sourceList
     * @param li
     * @param orientation
     * @return
     */
    private static void insertPtSource(Coordinate source, Integer sourceId,
                                         List<SourcePointInfo> sourceList, double li, Orientation orientation) {
        sourceList.add(new SourcePointInfo(sourceId, source, li, orientation));
    }

    /**
     * Compute li to equation 4.1 NMPB 2008 (June 2009)
     * @param source
     * @param receiverCoord
     * @param srcIndex
     * @param sourceList
     * @return
     */
    private void addLineSource(LineString source, Coordinate receiverCoord, int srcIndex, List<SourcePointInfo> sourceList) {
        ArrayList<Coordinate> pts = new ArrayList<>();
        Coordinate nearestPoint = JTSUtility.getNearestPoint(receiverCoord, source);
        double segmentSizeConstraint = max(1, receiverCoord.distance3D(nearestPoint) / 2.0);
        if (isNaN(segmentSizeConstraint)) {
            segmentSizeConstraint = max(1, receiverCoord.distance(nearestPoint) / 2.0);
        }
        double li = splitLineStringIntoPoints(source, segmentSizeConstraint, pts);
        for (int ptIndex = 0; ptIndex < pts.size(); ptIndex++) {
            Coordinate pt = pts.get(ptIndex);
            if (pt.distance(receiverCoord) < data.maxSrcDist) {
                // use the orientation computed from the line source coordinates
                Vector3D v;
                if(ptIndex == 0) {
                    v = new Vector3D(source.getCoordinates()[0], pts.get(ptIndex));
                } else {
                    v = new Vector3D(pts.get(ptIndex - 1), pts.get(ptIndex));
                }
                Orientation orientation;
                if(data.sourcesPk.size() > srcIndex && data.sourceOrientation.containsKey(data.sourcesPk.get(srcIndex))) {
                    // If the line source already provide an orientation then alter the line orientation
                    orientation = data.sourceOrientation.get(data.sourcesPk.get(srcIndex));
                    orientation = Orientation.fromVector(
                            Orientation.rotate(new Orientation(orientation.yaw, orientation.roll, 0),
                                    v.normalize()), orientation.roll);
                } else {
                    orientation = Orientation.fromVector(Orientation.rotate(new Orientation(0,0,0), v.normalize()), 0);
                }
                insertPtSource(pt, srcIndex, sourceList, li, orientation);
            }
        }
    }

    public enum ComputationSide {LEFT, RIGHT}


    /**
     * Attribute of the receiver point
     */
    public static final class ReceiverPointInfo {
        public int receiverIndex;
        public Coordinate position;

        public ReceiverPointInfo(int sourcePrimaryKey, Coordinate position) {
            this.receiverIndex = sourcePrimaryKey;
            this.position = position;
        }

        public Coordinate getCoordinates() {
            return position;
        }

        public int getId() {
            return receiverIndex;
        }
    }

    /**
     * Attributes of the source point
     */
    public static final class SourcePointInfo implements Comparable<SourcePointInfo> {
        public final double li;
        public int sourceIndex;
        Coordinate position;
        Orientation orientation;

        /**
         * @param sourcePrimaryKey
         * @param position
         */
        public SourcePointInfo(int sourcePrimaryKey, Coordinate position, double li, Orientation orientation) {
            this.sourceIndex = sourcePrimaryKey;
            this.position = position;
            if (isNaN(position.z)) {
                this.position = new Coordinate(position.x, position.y, 0);
            }
            this.li = li;
            this.orientation = orientation;
        }

        public Orientation getOrientation() {
            return orientation;
        }

        public Coordinate getCoord() {
            return position;
        }

        public int getSourceIndex() {
            return sourceIndex;
        }

        /**
         *
         * @param sourcePointInfo the object to be compared.
         * @return 1, 0 or -1
         */
        @Override
        public int compareTo(SourcePointInfo sourcePointInfo) {
            return Integer.compare(sourceIndex, sourcePointInfo.sourceIndex);
        }
    }
}
