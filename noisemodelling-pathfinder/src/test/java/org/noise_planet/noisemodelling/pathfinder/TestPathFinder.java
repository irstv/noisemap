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
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.pathfinder.PathFinder.splitLineStringIntoPoints;


public class TestPathFinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPathFinder.class);

    @Test
    public void testMeanPlane() {
        Coordinate sGround = new Coordinate(10, 10, 0);
        Coordinate rGround = new Coordinate(200, 50, 10);
        LineSegment segBottom = new LineSegment(new Coordinate(120, -20, 0),
                new Coordinate(120, 80, 0));
        LineSegment segTop = new LineSegment(new Coordinate(185, -5, 10),
                new Coordinate(185, 75, 10));
        LineSegment SgroundRGround = new LineSegment(sGround,
                rGround);

        Coordinate O1 = segBottom.lineIntersection(SgroundRGround);
        O1.z = segBottom.p0.z;
        Coordinate O2 = segTop.lineIntersection(SgroundRGround);
        O2.z = segTop.p0.z;
        List<Coordinate> uv = new ArrayList<>();
        uv.add(new Coordinate(sGround.distance(sGround), sGround.z));
        uv.add(new Coordinate(sGround.distance(O1), O1.z));
        uv.add(new Coordinate(sGround.distance(O2), O2.z));
        uv.add(new Coordinate(sGround.distance(rGround), rGround.z));

        double[] ab = JTSUtility.getMeanPlaneCoefficients(uv.toArray(new Coordinate[uv.size()]));
        double slope = ab[0];
        double intercept = ab[1];

        assertEquals(0.05, slope, 0.01);
        assertEquals(-2.83, intercept, 0.01);

        uv = new ArrayList<>();
        uv.add(new Coordinate(sGround.distance(sGround), sGround.z));
        uv.add(new Coordinate(sGround.distance(O1), O1.z));
        uv.add(new Coordinate(sGround.distance(O2), O2.z));

        ab = JTSUtility.getMeanPlaneCoefficients(uv.toArray(new Coordinate[uv.size()]));
        slope = ab[0];
        intercept = ab[1];
        assertEquals(0.05, slope, 0.01);
        assertEquals(-2.33, intercept, 0.01);
    }

    /**
     * Test vertical edge diffraction ray computation
     *
     * @throws ParseException
     */
    @Test
    public void TestcomputeVerticalEdgeDiffraction() throws ParseException {
        GeometryFactory factory = new GeometryFactory();
        WKTReader wktReader = new WKTReader(factory);
        //Create obstruction test object
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addBuilding(wktReader.read("POLYGON((5 6, 6 5, 7 5, 7 8, 6 8, 5 7, 5 6))"), 4, -1);
        profileBuilder.addBuilding(wktReader.read("POLYGON((9 7, 11 7, 11 11, 9 11, 9 7))"), 4, -1);
        profileBuilder.addBuilding(wktReader.read("POLYGON((12 8, 13 8, 13 10, 12 10, 12 8))"), 4, -1);
        profileBuilder.addBuilding(wktReader.read("POLYGON((10 4, 11 4, 11 6, 10 6, 10 4))"), 4, -1);
        profileBuilder.finishFeeding();

        PathFinder computeRays = new PathFinder(new Scene(profileBuilder));
        Coordinate p1 = new Coordinate(2, 6.5, 1.6);
        Coordinate p2 = new Coordinate(14, 6.5, 1.6);

        List<Coordinate> ray = computeRays.computeSideHull(true, p1, p2, profileBuilder);
        int i = 0;
        assertEquals(0, p1.distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(9, 11).distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(11, 11).distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(13, 10).distance(ray.get(i++)), 0.02);
        assertEquals(0, p2.distance(ray.get(i)), 0.02);

        ray = computeRays.computeSideHull(false, p1, p2, profileBuilder);
        i = 0;
        assertEquals(0, p1.distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(6, 5).distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(10, 4).distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(11, 4).distance(ray.get(i++)), 0.02);
        assertEquals(0, p2.distance(ray.get(i)), 0.02);

        ray = computeRays.computeSideHull(false, p2, p1, profileBuilder);
        i = 0;
        assertEquals(0, p2.distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(13, 10).distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(11, 11).distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(9, 11).distance(ray.get(i++)), 0.02);
        assertEquals(0, p1.distance(ray.get(i)), 0.02);

        ray = computeRays.computeSideHull(true, p2, p1, profileBuilder);
        i = 0;
        assertEquals(0, p2.distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(11, 4).distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(10, 4).distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(6, 5).distance(ray.get(i++)), 0.02);
        assertEquals(0, p1.distance(ray.get(i)), 0.02);
    }

    @Test
    public void TestSplitLineSourceIntoPoints() {
        GeometryFactory factory = new GeometryFactory();
        List<Coordinate> sourcePoints = new ArrayList<>();
        // source line is split in 3 parts of 2.5 meters
        // This is because minimal receiver-source distance is equal to 5 meters
        // The constrain is distance / 2.0 so 2.5 meters
        // The source length is equals to 5 meters
        // It can be equally split in 2 segments of 2.5 meters each, for each segment the nearest point is retained
        LineString geom = factory.createLineString(new Coordinate[]{new Coordinate(1,2,0),
                new Coordinate(4,2,0), new Coordinate(4, 0, 0)});
        Coordinate receiverCoord = new Coordinate(-4, 2, 0);
        Coordinate nearestPoint = JTSUtility.getNearestPoint(receiverCoord, geom);
        double segmentSizeConstraint = Math.max(1, receiverCoord.distance3D(nearestPoint) / 2.0);
        assertEquals(2.5, splitLineStringIntoPoints(geom , segmentSizeConstraint, sourcePoints), 1e-6);
        assertEquals(2, sourcePoints.size());
        assertEquals(0, new Coordinate(2.25, 2, 0).distance3D(sourcePoints.get(0)), 1e-6);
        assertEquals(0, new Coordinate(4, 1.25, 0).distance3D(sourcePoints.get(1)), 1e-6);
    }

    @Test
    public void TestSplitRegression() throws ParseException {
        LineString geom = (LineString)new WKTReader().read("LINESTRING (26.3 175.5 0.0000034909259558, 111.9 90.9 0, 123 -70.9 0, 345.2 -137.8 0)");
        double constraint = 82.98581729762442;
        List<Coordinate> pts = new ArrayList<>();
        splitLineStringIntoPoints(geom, constraint, pts);
        for(Coordinate pt : pts) {
            assertNotNull(pt);
        }
        assertEquals(7, pts.size());
    }

    /**
     * Test vertical edge diffraction ray computation
     *
     */
    @Test
    public void TestcomputeVerticalEdgeDiffractionRayOverBuilding() throws ParseException {
        GeometryFactory factory = new GeometryFactory();
        WKTReader wktReader = new WKTReader(factory);
        //Scene dimension
        //Envelope cellEnvelope = new Envelope(new Coordinate(0, 0, 0.), new Coordinate(20, 15, 0.));
        //Create obstruction test object
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addBuilding(wktReader.read("POLYGON((5 5, 7 5, 7 6, 8 6, 8 8, 5 8, 5 5))"), 4.3);
        profileBuilder.addBuilding(wktReader.read("POLYGON((9 7, 10 7, 10 9, 9 9, 9 7))"), 4.3);
        profileBuilder.finishFeeding();

        Scene processData = new Scene(profileBuilder);
        PathFinder computeRays = new PathFinder(processData);
        Coordinate p1 = new Coordinate(4, 3, 3);
        Coordinate p2 = new Coordinate(13, 10, 6.7);

        // Check the computation of convex corners of a building
        List<Coordinate> b1OffsetRoof = profileBuilder.getWideAnglePointsByBuilding(1, Math.PI * (1 + 1 / 16.0), Math.PI * (2 - (1 / 16.)));
        int i = 0;
        assertEquals(0, new Coordinate(5, 5).distance(b1OffsetRoof.get(i++)), 2 * ProfileBuilder.wideAngleTranslationEpsilon);
        assertEquals(0, new Coordinate(7, 5).distance(b1OffsetRoof.get(i++)), 2 * ProfileBuilder.wideAngleTranslationEpsilon);
        assertEquals(0, new Coordinate(8, 6).distance(b1OffsetRoof.get(i++)), 2 * ProfileBuilder.wideAngleTranslationEpsilon);
        assertEquals(0, new Coordinate(8, 8).distance(b1OffsetRoof.get(i++)), 2 * ProfileBuilder.wideAngleTranslationEpsilon);
        assertEquals(0, new Coordinate(5, 8).distance(b1OffsetRoof.get(i++)), 2 * ProfileBuilder.wideAngleTranslationEpsilon);
        assertEquals(0, new Coordinate(5, 5).distance(b1OffsetRoof.get(i++)), 2 * ProfileBuilder.wideAngleTranslationEpsilon);


        List<Coordinate> ray = computeRays.computeSideHull(true, p1, p2, profileBuilder);
        i = 0;
        assertEquals(0, p1.distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(5, 8).distance(ray.get(i++)), 0.02);
        assertEquals(0, p2.distance(ray.get(i++)), 0.02);


        ray = computeRays.computeSideHull(false, p1, p2, profileBuilder);
        i = 0;
        assertEquals(0, p1.distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(7, 5).distance(ray.get(i++)), 0.02);
        assertEquals(0, p2.distance(ray.get(i++)), 0.02);


        ray = computeRays.computeSideHull(false, p2, p1, profileBuilder);
        i = 0;
        assertEquals(0, p2.distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(5, 8).distance(ray.get(i++)), 0.02);
        assertEquals(0, p1.distance(ray.get(i++)), 0.02);

        ray = computeRays.computeSideHull(true, p2, p1, profileBuilder);
        i = 0;
        assertEquals(0, p2.distance(ray.get(i++)), 0.02);
        assertEquals(0, new Coordinate(7, 5).distance(ray.get(i++)), 0.02);
        assertEquals(0, p1.distance(ray.get(i++)), 0.02);
    }

    /**
     * Test vertical edge diffraction ray computation with receiver in concave building
     * This configuration is not supported currently, so it must return no rays.
     *
     * @throws ParseException
     */
    @Test
    public void TestConcaveVerticalEdgeDiffraction() throws ParseException {
        GeometryFactory factory = new GeometryFactory();
        WKTReader wktReader = new WKTReader(factory);
        //Scene dimension
        //Envelope cellEnvelope = new Envelope(new Coordinate(0, 0, 0.), new Coordinate(20, 15, 0.));
        //Create obstruction test object
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addBuilding(wktReader.read("POLYGON((5 6, 4 5, 7 5, 7 8, 4 8, 5 7, 5 6))"), 4);
        profileBuilder.addBuilding(wktReader.read("POLYGON((9 7, 11 7, 11 11, 9 11, 9 7))"), 4);
        profileBuilder.addBuilding(wktReader.read("POLYGON((12 8, 13 8, 13 10, 12 10, 12 8))"), 4);
        profileBuilder.addBuilding(wktReader.read("POLYGON((10 4, 11 4, 11 6, 10 6, 10 4))"), 4);
        profileBuilder.finishFeeding();

        Scene processData = new Scene(profileBuilder);
        PathFinder computeRays = new PathFinder(processData);
        Coordinate p1 = new Coordinate(4.5, 6.5, 1.6);
        Coordinate p2 = new Coordinate(14, 6.5, 1.6);

        List<Coordinate> ray = computeRays.computeSideHull(true, p1, p2, profileBuilder);
        assertTrue(ray.isEmpty());
        ray = computeRays.computeSideHull(false, p1, p2, profileBuilder);
        assertTrue(ray.isEmpty());
        ray = computeRays.computeSideHull(false, p2, p1, profileBuilder);
        assertTrue(ray.isEmpty());
        ray = computeRays.computeSideHull(true, p2, p1, profileBuilder);
        assertTrue(ray.isEmpty());
    }

    /**
     * Test vertical edge diffraction ray computation.
     * If the diffraction plane goes under the ground, reject the path
     * @throws ParseException
     */
    @Test
    public void TestVerticalEdgeDiffractionAirplaneSource() throws ParseException {
        GeometryFactory factory = new GeometryFactory();
        WKTReader wktReader = new WKTReader(factory);
        //Scene dimension
        Envelope cellEnvelope = new Envelope();
        Coordinate source = new Coordinate(223512.78, 6757739.7, 500.0);
        Coordinate receiver = new Coordinate(223392.04632028608, 6757724.944483406, 2.0);
        //Create obstruction test object
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addBuilding(wktReader.read("POLYGON ((223393 6757706, 223402 6757696, 223409 6757703, 223411 6757705, 223414 6757702, 223417 6757704, 223421 6757709, 223423 6757712, 223437 6757725, 223435 6757728, 223441 6757735, 223448 6757741, 223439 6757751, 223433 6757745, 223432 6757745, 223430 6757747, 223417 6757734, 223402 6757720, 223404 6757717, 223393 6757706)) "), 13);

        cellEnvelope.expandToInclude(source);
        cellEnvelope.expandToInclude(receiver);
        cellEnvelope.expandBy(1200);

        profileBuilder.finishFeeding();

        Scene processData = new Scene(profileBuilder);
        // new ArrayList<>(), manager, sourcesIndex, srclst, new ArrayList<>(), new ArrayList<>(), 0, 99, 1000,1000,0,0,new double[0],0,0,new EmptyProgressVisitor(), new ArrayList<>(), true

        PathFinder computeRays = new PathFinder(processData);

        List<Coordinate> ray = computeRays.computeSideHull(false, receiver, source, profileBuilder);
        assertTrue(ray.isEmpty());

    }
}