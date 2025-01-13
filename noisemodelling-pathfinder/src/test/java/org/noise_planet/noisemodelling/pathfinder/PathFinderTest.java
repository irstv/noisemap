/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.math.Vector3D;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReflection;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointWall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilderDecorator;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.CoordinateMixin;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.LineSegmentMixin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PathFinderTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PathFinderTest.class);

    /**
     * Overwrite project resource expected test cases
     */
    public boolean overwriteTestCase = false;

    /**
     *  Error for coordinates
     */
    public static final double DELTA_COORDS = 0.1;

    /**
     *  Error for planes values
     */
    public static final double DELTA_PLANES = 0.1;

    private void assertCutProfile(String utName, CutProfile cutProfile) throws IOException {
        String testCaseFileName = utName + ".json";
        if(overwriteTestCase) {
            URL resourcePath = PathFinder.class.getResource("test_cases");
            if(resourcePath != null) {
                File destination = new File(resourcePath.getFile(), testCaseFileName);
                try (FileWriter utFile = new FileWriter(destination)){
                    utFile.write(cutProfileAsJson(cutProfile));
                }
                LOGGER.warn("{} written in \n{}", testCaseFileName, destination);
            }
        }
        assertCutProfile(PathFinder.class.getResourceAsStream("test_cases/"+testCaseFileName),
                cutProfile);
    }

    public static String cutProfileAsJson(CutProfile cutProfile) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Coordinate.class, CoordinateMixin.class);
        mapper.addMixIn(LineSegment.class, LineSegmentMixin.class);
        ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();
        return writer.writeValueAsString(cutProfile);
    }

    public static void assertCutProfile(InputStream expected, CutProfile got) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        CutProfile cutProfile = mapper.readValue(expected, CutProfile.class);
        assertCutProfile(cutProfile, got);
    }

    public static void assertCutProfile(CutProfile expected, CutProfile got) {
        assertNotNull(expected);
        assertNotNull(got);
        assertEquals(expected.cutPoints.size(), got.cutPoints.size(), "Not the same number of cut points");
        for (int i = 0; i < expected.cutPoints.size(); i++) {
            CutPoint expectedCutPoint = expected.cutPoints.get(i);
            CutPoint gotCutPoint = got.cutPoints.get(i);
            assertInstanceOf(expectedCutPoint.getClass(), gotCutPoint);
            assert3DCoordinateEquals(expectedCutPoint+"!="+gotCutPoint, expectedCutPoint.coordinate,
                    gotCutPoint.coordinate, DELTA_COORDS);
            assertEquals(expectedCutPoint.zGround, gotCutPoint.zGround, 0.01, "zGround");
            assertEquals(expectedCutPoint.groundCoefficient, gotCutPoint.groundCoefficient, 0.01, "groundCoefficient");

            if(expectedCutPoint instanceof CutPointSource) {
                CutPointSource expectedCutPointSource = (CutPointSource) expectedCutPoint;
                CutPointSource gotCutPointSource = (CutPointSource) gotCutPoint;
                assertEquals(expectedCutPointSource.li, gotCutPointSource.li,0.01);
                assertEquals(expectedCutPointSource.orientation.yaw, gotCutPointSource.orientation.yaw,0.01);
                assertEquals(expectedCutPointSource.orientation.pitch, gotCutPointSource.orientation.pitch,0.01);
                assertEquals(expectedCutPointSource.orientation.roll, gotCutPointSource.orientation.roll,0.01);
            } else if (expectedCutPoint instanceof CutPointWall) {
                CutPointWall expectedCutPointWall = (CutPointWall) expectedCutPoint;
                CutPointWall gotCutPointWall = (CutPointWall) gotCutPoint;
                assert3DCoordinateEquals(expectedCutPointWall+"!="+gotCutPointWall, expectedCutPointWall.wall.p0,
                        gotCutPointWall.wall.p0, DELTA_COORDS);
                assert3DCoordinateEquals(expectedCutPointWall+"!="+gotCutPointWall, expectedCutPointWall.wall.p1,
                        gotCutPointWall.wall.p1, DELTA_COORDS);
                assertArrayEquals(expectedCutPointWall.alphaAsArray(), gotCutPointWall.alphaAsArray(), 0.01);
            } else if (expectedCutPoint instanceof CutPointReflection) {
                CutPointReflection expectedCutPointReflection = (CutPointReflection) expectedCutPoint;
                CutPointReflection gotCutPointReflection = (CutPointReflection) gotCutPoint;
                assert3DCoordinateEquals(expectedCutPointReflection+"!="+gotCutPointReflection,
                        expectedCutPointReflection.wall.p0, gotCutPointReflection.wall.p0, DELTA_COORDS);
                assert3DCoordinateEquals(expectedCutPointReflection+"!="+gotCutPointReflection,
                        expectedCutPointReflection.wall.p1, gotCutPointReflection.wall.p1, DELTA_COORDS);
                assertArrayEquals(expectedCutPointReflection.alphaAsArray(), gotCutPointReflection.alphaAsArray(), 0.01);
            }
        }

    }


    /**
     * Test TC01 -- Reflecting ground (G = 0)
     */
    @Test
    public void TC01() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder().finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 4)
                .setGs(0.0)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertCutProfile("TC01_Direct", propDataOut.cutProfiles.getFirst());
    }

    /**
     * Test TC02 -- Mixed ground (G = 0,5)
     */
    @Test
    public void TC02() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder().finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 4)
                .setGs(0.5)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertCutProfile("TC02_Direct", propDataOut.cutProfiles.getFirst());
    }



    /**
     * Test TC03 -- Mixed ground (G = 0,5)
     */
    @Test
    public void TC03() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder().finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 4)
                .setGs(1.0)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertCutProfile("TC03_Direct", propDataOut.cutProfiles.getFirst());
    }

    /**
     * Test TC04 -- Flat ground with spatially varying acoustic properties
     */
    @Test
    public void TC04() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                //Ground effects
                .addGroundEffect(0.0, 50.0, -20.0, 80.0, 0.2)
                .addGroundEffect(50.0, 150.0, -20.0, 80.0, 0.5)
                .addGroundEffect(150.0, 225.0, -20.0, 80.0, 0.9)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 4)
                .setGs(0.2)
                .vEdgeDiff(true)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertCutProfile("TC04_Direct", propDataOut.cutProfiles.getFirst());

    }


    public static void addGroundAttenuationTC5(ProfileBuilder profileBuilder) {
        profileBuilder
                .addGroundEffect(0.0, 50.0, -20.0, 80.0, 0.9)
                .addGroundEffect(50.0, 150.0, -20.0, 80.0, 0.5)
                .addGroundEffect(150.0, 225.0, -20.0, 80.0, 0.2);
    }

    public static void addTopographicTC5Model(ProfileBuilder profileBuilder) {
        profileBuilder
                // top horizontal line
                .addTopographicLine(0, 80, 0, 120, 80, 0)
                .addTopographicLine(120, 80, 0, 225, 80, 0)
                // bottom horizontal line
                .addTopographicLine(225, -20, 0, 120, -20, 0)
                .addTopographicLine(120, -20, 0, 0, -20, 0)
                // right vertical line
                .addTopographicLine(225, 80, 0, 225, -20, 0)
                // left vertical line
                .addTopographicLine(0, -20, 0, 0, 80, 0)
                // center vertical line
                .addTopographicLine(120, -20, 0, 120, 80, 0)
                // elevated rectangle
                .addTopographicLine(185, -5, 10, 205, -5, 10)
                .addTopographicLine(205, -5, 10, 205, 75, 10)
                .addTopographicLine(205, 75, 10, 185, 75, 10)
                .addTopographicLine(185, 75, 10, 185, -5, 10);

        // ramp connection
        profileBuilder.addTopographicLine(120, 80, 0, 185, 75, 10)
                .addTopographicLine(120, -20, 0, 185, -5, 10)
                .addTopographicLine(205, 75, 10, 225, 80, 0)
                .addTopographicLine(205, -5, 10, 225, -20, 0);
    }


    private static Coordinate from3DVector(Vector3D vector3D) {
        return new Coordinate(vector3D.getX(), vector3D.getY(), vector3D.getZ());
    }

    private static Vector3D to3DVector(Vector2D vector2D) {
        return new Vector3D(vector2D.getX(), vector2D.getY(), 0);
    }

    /**
     * Move linesegment to match the expected distance from source
     * @param sourceReceiver
     * @param segmentToMove
     * @param expectedDistance
     */
    private static void fixLineSegment(LineSegment sourceReceiver, LineSegment segmentToMove, double expectedDistance) {
        Coordinate[] closestPoints = sourceReceiver.closestPoints(segmentToMove);
        // create a translation vector to fix the distance
        Vector3D fixVector = to3DVector(Vector2D.create(sourceReceiver.p0, sourceReceiver.p1).normalize()
                .multiply(expectedDistance-closestPoints[0].distance(sourceReceiver.p0)));
        segmentToMove.p0 = from3DVector(Vector3D.create(segmentToMove.p0).add(fixVector));
        segmentToMove.p1 = from3DVector(Vector3D.create(segmentToMove.p1).add(fixVector));
    }

    public static void makeParallel(LineSegment reference, LineSegment toEdit) {
        // edit second point position in order to have the second line parallel to the first line
        toEdit.p1 =
                Vector2D.create(reference.p0, reference.p1).normalize()
                        .multiply(toEdit.getLength())
                        .add(Vector2D.create(toEdit.p0)).toCoordinate();
        toEdit.p1.z = toEdit.p0.z;
    }


    public static void addTopographicTC23Model(ProfileBuilder profileBuilder) {
        // Create parallel lines for the slope edge because unit test table values are rounded and
        // the rounding make the lines non-parallel and at the wrong distance

        // we will use expected distance on Z Profile to construct the lines
        Coordinate source = new Coordinate(38, 14, 1);
        Coordinate receiver = new Coordinate(107, 25.95, 4);
        LineSegment sourceReceiver = new LineSegment(source, receiver);
        Double[] expectedDistance = new Double[] {14.21, 22.64, 23.98, 32.3};

        // base line for constructing expected results
        LineSegment leftTopographicVerticalLine = new LineSegment(new Coordinate(46.27, 36.28,0),
                new Coordinate(59.6, -9.87, 0));
        LineSegment leftShortTopographicVerticalLine = new LineSegment(new Coordinate(54.68, 37.59, 5),
                new Coordinate(67.35, -6.83, 5));
        LineSegment rightShortTopographicVerticalLine = new LineSegment(new Coordinate(55.93, 37.93, 5),
                new Coordinate(68.68, -6.49, 5));
        LineSegment rightTopographicVerticalLine = new LineSegment(new Coordinate(63.71, 41.16, 0),
                new Coordinate(76.84, -5.28,0));

        // Fix lines
        fixLineSegment(sourceReceiver, leftTopographicVerticalLine, expectedDistance[0]);

        makeParallel(leftTopographicVerticalLine, leftShortTopographicVerticalLine);
        fixLineSegment(sourceReceiver, leftShortTopographicVerticalLine, expectedDistance[1]);

        makeParallel(leftTopographicVerticalLine, rightShortTopographicVerticalLine);
        fixLineSegment(sourceReceiver, rightShortTopographicVerticalLine, expectedDistance[2]);

        makeParallel(leftTopographicVerticalLine, rightTopographicVerticalLine);
        fixLineSegment(sourceReceiver, rightTopographicVerticalLine, expectedDistance[3]);

        profileBuilder.addTopographicLine(30, -14, 0, 122, -14, 0);
        profileBuilder.addTopographicLine(122, -14, 0, 122, 45, 0);
        profileBuilder.addTopographicLine(122, 45, 0, 30, 45, 0);
        profileBuilder.addTopographicLine(30, 45, 0, 30, -14, 0);
        profileBuilder.addTopographicLine(leftTopographicVerticalLine.p1, rightTopographicVerticalLine.p0);
        profileBuilder.addTopographicLine(rightTopographicVerticalLine);
        profileBuilder.addTopographicLine(rightTopographicVerticalLine.p0, leftTopographicVerticalLine.p0);
        profileBuilder.addTopographicLine(leftTopographicVerticalLine);
        profileBuilder.addTopographicLine(leftTopographicVerticalLine.p1, leftShortTopographicVerticalLine.p0);
        profileBuilder.addTopographicLine(leftShortTopographicVerticalLine.p0, rightShortTopographicVerticalLine.p0);
        profileBuilder.addTopographicLine(rightShortTopographicVerticalLine.p0, rightTopographicVerticalLine.p1);
        profileBuilder.addTopographicLine(leftTopographicVerticalLine.p1, leftShortTopographicVerticalLine.p1);
        profileBuilder.addTopographicLine(leftShortTopographicVerticalLine.p1, rightShortTopographicVerticalLine.p1);
        profileBuilder.addTopographicLine(rightShortTopographicVerticalLine.p1, rightTopographicVerticalLine.p0);
        profileBuilder.addTopographicLine(leftShortTopographicVerticalLine);
        profileBuilder.addTopographicLine(rightShortTopographicVerticalLine);
    }

    /**
     * Test TC05 -- Ground with spatially varying heights and acoustic properties
     */
    @Test
    public void TC05() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        addTopographicTC5Model(profileBuilder);
        addGroundAttenuationTC5(profileBuilder);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 14)
                .setGs(0.9)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertCutProfile("TC05_Direct", propDataOut.cutProfiles.getFirst());
    }



    /**
     * Test TC06 -- Reduced receiver height to include diffraction in some frequency bands
     */
    @Test
    public void TC06() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        addTopographicTC5Model(profileBuilder);
        addGroundAttenuationTC5(profileBuilder);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 11.5)
                .setGs(0.9)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertCutProfile("TC06_Direct", propDataOut.cutProfiles.getFirst());

    }

    /**
     * Test TC07 -- Flat ground with spatially varying acoustic properties and long barrier
     */
    @Test
    public void TC07() throws Exception {

        GeometryFactory factory = new GeometryFactory();

        //Create profile builder
        ProfileBuilder profileBuilder = new ProfileBuilder()

                // Add building
                .addWall(new Coordinate[]{
                                new Coordinate(100, 240, 0),
                                new Coordinate(265, -180, 0)},
                        6, 1)
                // Add ground effect
                .addGroundEffect(factory.toGeometry(new Envelope(0, 50, -250, 250)), 0.9)
                .addGroundEffect(factory.toGeometry(new Envelope(50, 150, -250, 250)), 0.5)
                .addGroundEffect(factory.toGeometry(new Envelope(150, 225, -250, 250)), 0.2)

                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 4)
                .setGs(0.9)
                .hEdgeDiff(true)
                .vEdgeDiff(false)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertCutProfile("TC07_Direct", propDataOut.cutProfiles.getFirst());
    }

    /**
     * Test TC08 -- Flat ground with spatially varying acoustic properties and short barrier
     */
    @Test
    public void TC08() throws Exception {

        GeometryFactory factory = new GeometryFactory();

        //Create profile builder
        ProfileBuilder profileBuilder = new ProfileBuilder()

                // Add building
                .addWall(new Coordinate[]{
                                new Coordinate(175, 50, 0),
                                new Coordinate(190, 10, 0)},
                        6, 1)
                // Add ground effect
                .addGroundEffect(factory.toGeometry(new Envelope(0, 50, -250, 250)), 0.9)
                .addGroundEffect(factory.toGeometry(new Envelope(50, 150, -250, 250)), 0.5)
                .addGroundEffect(factory.toGeometry(new Envelope(150, 225, -250, 250)), 0.2)

                .finishFeeding();


        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 4)
                .setGs(0.9)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC08_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC08_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC08_Left", propDataOut.cutProfiles.poll());

    }

    /**
     * Test TC09 -- Ground with spatially varying heights and and acoustic properties and short barrier
     */
    @Test
    public void TC09() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        addTopographicTC5Model(profileBuilder);
        addGroundAttenuationTC5(profileBuilder);
        // add wall
        profileBuilder.addWall(new Coordinate[]{
                                new Coordinate(175, 50, 17),
                                new Coordinate(190, 10, 14)},
                        1)

                //.setzBuildings(true)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 14)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.9)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC09_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC09_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC09_Left", propDataOut.cutProfiles.poll());

    }

    /**
     * Test TC10 -- Flat ground with homogeneous acoustic properties and cubic building – receiver at low height
     */
    @Test
    public void TC10() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBuilding(new Coordinate[]{
                        new Coordinate(55, 5, 10),
                        new Coordinate(65, 5, 10),
                        new Coordinate(65, 15, 10),
                        new Coordinate(55, 15, 10),
                });

        profileBuilder.addGroundEffect(0.0, 100.0, 0.0, 100.0, 0.5);

        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(50, 10, 1)
                .addReceiver(70, 10, 4)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.5)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);


        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC10_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC10_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC10_Left", propDataOut.cutProfiles.poll());

    }

    /**
     * Test TC11 -- Flat ground with homogeneous acoustic properties and cubic building – receiver at low height
     */
    @Test
    public void TC11() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBuilding(new Coordinate[]{
                        new Coordinate(55, 5, 10),
                        new Coordinate(65, 5, 10),
                        new Coordinate(65, 15, 10),
                        new Coordinate(55, 15, 10),
                });
        profileBuilder.addGroundEffect(0.0, 100.0, 0.0, 100.0, 0.5);

        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(50, 10, 1)
                .addReceiver(70, 10, 15)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.5)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        // Run computation
        computeRays.run(propDataOut);

        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC11_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC11_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC11_Left", propDataOut.cutProfiles.poll());
    }

    /**
     * Test TC12 -- Flat ground with homogeneous acoustic properties and polygonal object – receiver at low height
     */
    @Test
    public void TC12() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBuilding(new Coordinate[]{
                        new Coordinate(11.0, 15.5, 10),
                        new Coordinate(12.0, 13.0, 10),
                        new Coordinate(14.5, 12.0, 10),
                        new Coordinate(17.0, 13.0, 10),
                        new Coordinate(18.0, 15.5, 10),
                        new Coordinate(17.0, 18.0, 10),
                        new Coordinate(14.5, 19.0, 10),
                        new Coordinate(12.0, 18.0, 10),
                });

        profileBuilder.addGroundEffect(0.0, 50, 0.0, 50, 0.5);

        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(0, 10, 1)
                .addReceiver(30, 20, 6)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.5)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);


        //Run computation
        computeRays.run(propDataOut);

        //Expected values

        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC12_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC12_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC12_Left", propDataOut.cutProfiles.poll());
    }

    /**
     * Test TC13 -- Ground with spatially varying heights and acoustic properties and polygonal object
     */
    @Test
    public void TC13() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBuilding(new Coordinate[]{
                        new Coordinate(169.4, 41.0, 30),
                        new Coordinate(172.5, 33.5, 30),
                        new Coordinate(180.0, 30.4, 30),
                        new Coordinate(187.5, 33.5, 30),
                        new Coordinate(190.6, 41.0, 30),
                        new Coordinate(187.5, 48.5, 30),
                        new Coordinate(180.0, 51.6, 30),
                        new Coordinate(172.5, 48.5, 30),
                });
        profileBuilder.addGroundEffect(0, 50, -20, 80, 0.5)
                .addGroundEffect(50, 150, -20, 80, 0.9)
                .addGroundEffect(150, 225, -20, 80, 0.2);
        addTopographicTC5Model(profileBuilder);
        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 28.5)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.5)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC13_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC13_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC13_Left", propDataOut.cutProfiles.poll());
    }

    /**
     * Test TC14 -- Flat ground with homogeneous acoustic properties and polygonal building – receiver at large height
     * Wrong value of z1 in Cnossos document for the 3 paths
     */
    @Test
    public void TC14() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBuilding(new Coordinate[]{
                        new Coordinate(11.0, 15.5, 10),
                        new Coordinate(12.0, 13.0, 10),
                        new Coordinate(14.5, 12.0, 10),
                        new Coordinate(17.0, 13.0, 10),
                        new Coordinate(18.0, 15.5, 10),
                        new Coordinate(17.0, 18.0, 10),
                        new Coordinate(14.5, 19.0, 10),
                        new Coordinate(12.0, 18.0, 10),
                });
        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(8, 10, 1)
                .addReceiver(25, 20, 23)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.2)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC14_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC14_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC14_Left", propDataOut.cutProfiles.poll());

    }

    /**
     * Test TC15 -- Flat ground with homogeneous acoustic properties and four buildings
     * right : error in value of b cnossos table 149 right path
     */
    @Test
    public void TC15() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBuilding(new Coordinate[]{
                        new Coordinate(55.0, 5.0, 8),
                        new Coordinate(65.0, 5.0, 8),
                        new Coordinate(65.0, 15.0, 8),
                        new Coordinate(55.0, 15.0, 8),
                })
                .addBuilding(new Coordinate[]{
                        new Coordinate(70.0, 14.5, 12),
                        new Coordinate(80.0, 10.2, 12),
                        new Coordinate(80.0, 20.2, 12),
                })
                .addBuilding(new Coordinate[]{
                        new Coordinate(90.1, 19.5, 10),
                        new Coordinate(93.3, 17.8, 10),
                        new Coordinate(87.3, 6.6, 10),
                        new Coordinate(84.1, 8.3, 10),
                });
        profileBuilder.addGroundEffect(0, 100, 0.0, 150, 0.5);
        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();


        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(50, 10, 1)
                .addReceiver(100, 15, 5)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.5)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);


        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC15_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC15_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC15_Left", propDataOut.cutProfiles.poll());
    }

    /**
     * Test TC16 -- Reflecting barrier on ground with spatially varying heights and acoustic properties
     */
    @Test
    public void TC16() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        addTopographicTC5Model(profileBuilder);
        addGroundAttenuationTC5(profileBuilder);

        profileBuilder.addWall(new Coordinate[]{
                        new Coordinate(114, 52, 15),
                        new Coordinate(170, 60, 15)
                }, 15, Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5), -1);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 14)
                .setGs(0.9)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);


        //Expected values


        assertEquals(2, propDataOut.getCutProfiles().size());

        assertCutProfile("TC16_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC16_Reflection", propDataOut.cutProfiles.poll());
    }

    /**
     * TC17 - Reflecting barrier on ground with spatially varying heights and acoustic properties reduced receiver height
     *
     * No data provided usable for testing.
     */
    //TODO : no data provided in the document for this test.
    @Test
    public void TC17() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();

        addTopographicTC5Model(profileBuilder);
        addGroundAttenuationTC5(profileBuilder);

        profileBuilder.addWall(new Coordinate[]{
                        new Coordinate(114, 52, 15),
                        new Coordinate(170, 60, 15)
                }, 15, Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5), -1);
        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 11.5)
                .setGs(0.9)
                .build();
        rayData.reflexionOrder=1;

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);


        assertEquals(2, propDataOut.getCutProfiles().size());

        assertCutProfile("TC17_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC17_Reflection", propDataOut.cutProfiles.poll());

    }

    /**
     * TC18 - Screening and reflecting barrier on ground with spatially varying heights and
     * acoustic properties
     */

    @Test
    public void TC18() throws Exception {
        //Profile building
        ProfileBuilder builder = new ProfileBuilder();
        addGroundAttenuationTC5(builder);
        addTopographicTC5Model(builder);
                // Add building
        builder.addWall(new Coordinate[]{
                        new Coordinate(114, 52, 15),
                        new Coordinate(170, 60, 15)}, Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5), 1)

                .addWall(new Coordinate[]{
                        new Coordinate(87, 50,12),
                        new Coordinate(92, 32,12)}, Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5), 2)
                //.setzBuildings(true)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(builder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 12)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.9)
                .build();
        rayData.reflexionOrder=1;

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);


        assertEquals(2, propDataOut.getCutProfiles().size());

        assertCutProfile("TC18_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC18_Reflection", propDataOut.cutProfiles.poll());
    }

    /**
     * TC18 - Screening and reflecting barrier on ground with spatially varying heights and
     * acoustic properties. This scenario is modified with the reflexion screen too low on one corner to have a valid
     * reflexion caused by height modification from the diffraction on the first wall
     */

    @Test
    public void TC18Altered() throws Exception {
        //Profile building
        ProfileBuilder builder = new ProfileBuilder();
        addGroundAttenuationTC5(builder);
        addTopographicTC5Model(builder);
        // Add building
        builder.addWall(new Coordinate[]{
                        new Coordinate(114, 52, 9),
                        new Coordinate(170, 60, 15)}, Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5), 1)

                .addWall(new Coordinate[]{
                        new Coordinate(87, 50,12),
                        new Coordinate(92, 32,12)}, Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5), 2)
                //.setzBuildings(true)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(builder)
                .addSource(10, 10, 1)
                .addReceiver(200, 50, 12)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.9)
                .build();
        rayData.reflexionOrder=1;

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(2, propDataOut.getCutProfiles().size());

        assertCutProfile("TC18Altered_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC18Altered_Left", propDataOut.cutProfiles.poll());

    }


    /**
     * TC19 - Complex object and 2 barriers on ground with spatially varying heights and
     * acoustic properties:
     * erreur Cnossos: left path -> gPath table 207
     */
    @Test
    public void TC19() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        addTopographicTC5Model(profileBuilder);
        addGroundAttenuationTC5(profileBuilder);

        profileBuilder.addBuilding(new Coordinate[]{
                        new Coordinate(100, 24, 12),
                        new Coordinate(118, 24, 12),
                        new Coordinate(118, 30, 12),
                        new Coordinate(100, 30, 12),
                })
                .addBuilding(new Coordinate[]{
                        new Coordinate(110, 15, 7),
                        new Coordinate(118, 15, 7),
                        new Coordinate(118, 24, 7),
                        new Coordinate(110, 24, 7),
                })
                .addBuilding(new Coordinate[]{
                        new Coordinate(100, 9, 12),
                        new Coordinate(118, 9, 12),
                        new Coordinate(118, 15, 12),
                        new Coordinate(100, 15, 12),
                })
                .addWall(new Coordinate[]{
                        new Coordinate(156.00, 28.00, 14),
                        new Coordinate(145.00, 7.00, 14),
                }, -1)
                .addWall(new Coordinate[]{
                        new Coordinate(175.00, 35.00, 14.5),
                        new Coordinate(188.00, 19.00, 14.5),
                }, -1)
                .setzBuildings(true)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 30, 14)
                .hEdgeDiff(true)
                //.vEdgeDiff(true)
                .setGs(0.9)
                .build();
        rayData.reflexionOrder=1;


        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);


        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC19_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC19_Right", propDataOut.cutProfiles.poll());

        //Different value with the TC because their z-profile left seems to be false, it follows the building top
        // border while it should not
        // assertCutProfile("TC19_Left", propDataOut.cutProfiles.poll());
    }

    /**
     * TC20 - Ground with spatially varying heights and acoustic properties
     */
    @Test
    public void TC20() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        addTopographicTC5Model(profileBuilder);
        addGroundAttenuationTC5(profileBuilder);
        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 25, 14)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.9)
                .build();

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(1, propDataOut.getCutProfiles().size());

        assertCutProfile("TC20_Direct", propDataOut.cutProfiles.poll());
    }

    /**
     * TC21 - Building on ground with spatially varying heights and acoustic properties
     * problème ISO
     */
    @Test
    public void TC21()  throws Exception {
        //Profile building

        // the rounding of the unit test input data lead to errors. We had to move two vertex to match with the expected intersection
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBuilding(new Coordinate[]{
                        new Coordinate(167.2, 39.5, 11.5),
                        new Coordinate(151.575, 48.524, 11.5),
                        new Coordinate(141.1, 30.3, 11.5),
                        new Coordinate(156.657, 21.3409, 11.5),
                        new Coordinate(159.7, 26.5, 11.5),
                        new Coordinate(151.0, 31.5, 11.5),
                        new Coordinate(155.5, 39.3, 11.5),
                        new Coordinate(164.2, 34.3, 11.5)
                });

        addTopographicTC5Model(profileBuilder);
        addGroundAttenuationTC5(profileBuilder);

        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(10, 10, 1)
                .addReceiver(200, 25, 14)
                .hEdgeDiff(true)
                //.vEdgeDiff(true)
                .setGs(0.9)
                .build();
        rayData.reflexionOrder=0;

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC21_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC21_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC21_Left", propDataOut.cutProfiles.poll());
    }

    @Test
    public void TC22() throws Exception {

        ProfileBuilder builder = new ProfileBuilder();

        // Add building
        builder.addBuilding(new Coordinate[]{
                        new Coordinate(197, 36.0, 20),
                        new Coordinate(179, 36, 20),
                        new Coordinate(179, 15, 20),
                        new Coordinate(197, 15, 20),
                        new Coordinate(197, 21, 20),
                        new Coordinate(187, 21, 20),
                        new Coordinate(187, 30, 20),
                        new Coordinate(197, 30, 20),
                        new Coordinate(197, 36, 20)},-1)

                .addGroundEffect(0.0, 50.0, -20.0, 80.0, 0.9)
                .addGroundEffect(50.0, 150.0, -20.0, 80.0, 0.5)
                .addGroundEffect(150.0, 225.0, -20.0, 80.0, 0.2)

                .addTopographicLine(0, 80, 0, 255, 80, 0)
                .addTopographicLine(225, 80, 0, 225, -20, 0)
                .addTopographicLine(225, -20, 0, 0, -20, 0)
                .addTopographicLine(0, -20, 0, 0, 80, 0)
                .addTopographicLine(120, -20, 0, 120, 80, 0)
                .addTopographicLine(185, -5, 10, 205, -5, 10)
                .addTopographicLine(205, -5, 10, 205, 75, 10)
                .addTopographicLine(205, 74, 10, 185, 75, 10)
                .addTopographicLine(185, 75, 10, 185, -5, 10);
        builder.setzBuildings(true);
        builder.finishFeeding();

        //  .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(builder)
                .addSource(10, 10, 1)
                .addReceiver(187.05, 25, 14)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.9)
                .build();

        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);


        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC22_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC22_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC22_Left", propDataOut.cutProfiles.poll());
    }

    @Test
    public void TC23() throws Exception {

        GeometryFactory factory = new GeometryFactory();

        // Add building 20% abs
        List<Double> buildingsAbs = Collections.nCopies(8, 0.2);

        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder();

        builder.addBuilding(new Coordinate[]{
                        new Coordinate(75, 34, 9),
                        new Coordinate(110, 34, 9),
                        new Coordinate(110, 26, 9),
                        new Coordinate(75, 26, 9)},buildingsAbs)
                .addBuilding(new Coordinate[]{
                        new Coordinate(83, 18, 8),
                        new Coordinate(118, 18, 8),
                        new Coordinate(118, 10, 8),
                        new Coordinate(83, 10, 8)},buildingsAbs)
                // Ground Surface
                .addGroundEffect(factory.createPolygon(new Coordinate[]{
                        new Coordinate(59.6, -9.87, 0), // 5
                        new Coordinate(76.84, -5.28, 0), // 5-6
                        new Coordinate(63.71, 41.16, 0), // 6-7
                        new Coordinate(46.27, 36.28, 0), // 7-8
                        new Coordinate(59.6, -9.87, 0)
                }), 1.)
                .addGroundEffect(factory.createPolygon(new Coordinate[]{
                        new Coordinate(30, -14, 0), // 5
                        new Coordinate(122, -14, 0), // 5-6
                        new Coordinate(122, 45, 0), // 6-7
                        new Coordinate(30, 45, 0), // 7-8
                        new Coordinate(30, -14, 0)
                }), 0.);
        addTopographicTC23Model(builder);
        builder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(builder)
                .addSource(38, 14, 1)
                .addReceiver(107, 25.95, 4)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.)
                .build();
        rayData.reflexionOrder=0;


        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(1, propDataOut.getCutProfiles().size());

        assertCutProfile("TC23_Direct", propDataOut.cutProfiles.poll());

    }

    @Test
    public void TC24() throws Exception {
        GeometryFactory factory = new GeometryFactory();

        // Add building 20% abs
        List<Double> buildingsAbs = Collections.nCopies(8, 0.2);

        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder();



        builder.addBuilding(new Coordinate[]{
                        new Coordinate(75, 34, 9),
                        new Coordinate(110, 34, 9),
                        new Coordinate(110, 26, 9),
                        new Coordinate(75, 26, 9)},buildingsAbs)
                .addBuilding(new Coordinate[]{
                        new Coordinate(83, 18, 6),
                        new Coordinate(118, 18, 6),
                        new Coordinate(118, 10, 6),
                        new Coordinate(83, 10, 6)},buildingsAbs)
                // Ground Surface
                .addGroundEffect(factory.createPolygon(new Coordinate[]{
                        new Coordinate(59.6, -9.87, 0), // 5
                        new Coordinate(76.84, -5.28, 0), // 5-6
                        new Coordinate(63.71, 41.16, 0), // 6-7
                        new Coordinate(46.27, 36.28, 0), // 7-8
                        new Coordinate(59.6, -9.87, 0)
                }), 1.)
                .addGroundEffect(factory.createPolygon(new Coordinate[]{
                        new Coordinate(30, -14, 0), // 5
                        new Coordinate(122, -14, 0), // 5-6
                        new Coordinate(122, 45, 0), // 6-7
                        new Coordinate(30, 45, 0), // 7-8
                        new Coordinate(30, -14, 0)
                }), 0.);
        builder.setzBuildings(true);
        addTopographicTC23Model(builder);
        builder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(builder)
                .addSource(38, 14, 1)
                .addReceiver(106, 18.5, 4)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.)
                .build();
        rayData.reflexionOrder=1;
        rayData.computeHorizontalDiffraction=false;
        rayData.computeVerticalDiffraction=true;

        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(2, propDataOut.getCutProfiles().size());

        assertCutProfile("TC24_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC24_Reflection", propDataOut.cutProfiles.poll());
    }

    @Test
    public void TC25() throws Exception {

        // Add building 20% abs
        List<Double> buildingsAbs = Collections.nCopies(8, 0.2);

        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder();

        builder.addBuilding(new Coordinate[]{
                        new Coordinate(75, 34, 0),
                        new Coordinate(110, 34, 0),
                        new Coordinate(110, 26, 0),
                        new Coordinate(75, 26, 0)}, 9, buildingsAbs)
                .addBuilding(new Coordinate[]{
                        new Coordinate(83, 18, 0),
                        new Coordinate(118, 18, 0),
                        new Coordinate(118, 10, 0),
                        new Coordinate(83, 10, 0)}, 6, buildingsAbs)
                // Ground Surface

                .addWall(new Coordinate[]{
                        new Coordinate(59.19, 24.47, 5),
                        new Coordinate(64.17, 6.95, 5)
                }, 0)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(builder)
                .addSource(38, 14, 1)
                .addReceiver(106, 18.5, 4)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.)
                .build();
        rayData.reflexionOrder=1;


        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(4, propDataOut.getCutProfiles().size());

        assertCutProfile("TC25_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC25_Right", propDataOut.cutProfiles.poll());
        assertCutProfile("TC25_Left", propDataOut.cutProfiles.poll());
        assertCutProfile("TC25_Reflection", propDataOut.cutProfiles.poll());
    }

    /**
     * No datas cnossos for test
     */
    @Test
    public void TC26() throws Exception {

        GeometryFactory factory = new GeometryFactory();
        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder();

        // Add building
        // screen
        builder.addWall(new Coordinate[]{
                        new Coordinate(74.0, 52.0, 6),
                        new Coordinate(130.0, 60.0, 8)}, Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5), -1)

                .addGroundEffect(factory.toGeometry(new Envelope(0, 50, -10, 100)), 0.0)
                .addGroundEffect(factory.toGeometry(new Envelope(50, 150, -10, 100)), 0.5)
                .setzBuildings(true)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(builder)
                .addSource(10, 10, 0.05)
                .addReceiver(120, 50, 8)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.)
                .build();
        rayData.reflexionOrder=1;
        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(2, propDataOut.getCutProfiles().size());

        assertCutProfile("TC26_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC26_Reflection", propDataOut.cutProfiles.poll());
    }

    /**
     *
     */
    @Test
    public void TC27() throws Exception {

        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder();

        // Add building
        // screen
        builder.addWall(new Coordinate[]{
                        new Coordinate(114.0, 52.0, 2.5),
                        new Coordinate(170.0, 60.0, 4.5)},
                Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5), -1)

                .addTopographicLine(80.0, 20.0, -0.5, 110.0, 20.0, -0.5)
                .addTopographicLine(110.0, 20.0, -0.5, 111.0, 20.0, 0.0)
                .addTopographicLine(111.0, 20.0, 0.0, 215.0, 20.0, 0.0)
                .addTopographicLine(215.0, 20.0, 0.0, 215.0, 80.0, 0.0)
                .addTopographicLine(215.0, 80.0, 0.0, 111.0, 80.0, 0.0)
                .addTopographicLine(111.0, 80.0, 0.0, 110.0, 80.0, -0.5)
                .addTopographicLine(110.0, 80.0, -0.5, 80.0, 80.0, -0.5)
                .addTopographicLine(80.0, 80.0, -0.5, 80.0, 20.0, -0.5)
                .addTopographicLine(110.0, 20.0, -0.5, 110.0, 80.0, -0.5)
                .addTopographicLine(111.0, 20.0, 0.0, 111.0, 80.0, 0.0)

                .addGroundEffect(80, 110, 20, 80, 0.0)
                .addGroundEffect(110, 215, 20, 80, 1.0)
                .setzBuildings(true)
                .finishFeeding();


        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(builder)
                .addSource(105, 35, -0.45)
                .addReceiver(200, 50, 4)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.)
                .build();
        rayData.reflexionOrder=1;

        //Out and computation settings
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        computeRays.run(propDataOut);

        assertEquals(2, propDataOut.getCutProfiles().size());

        assertCutProfile("TC27_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC27_Reflection", propDataOut.cutProfiles.poll());
    }

    /**
     * error:       if b = 0.68: -> z2 = 0.32. In Cnossos z2 = 1.32 if b = 0.68
     */
    @Test
    public void TC28() throws Exception {
        GeometryFactory factory = new GeometryFactory();


        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder();

        // Add building
        builder.addBuilding(new Coordinate[]{
                        new Coordinate(113, 10, 0),
                        new Coordinate(127, 16, 0),
                        new Coordinate(102, 70, 0),
                        new Coordinate(88, 64, 0)}, 6, -1)

                .addBuilding(new Coordinate[]{
                        new Coordinate(176, 19, 0),
                        new Coordinate(164, 88, 0),
                        new Coordinate(184, 91, 0),
                        new Coordinate(196, 22, 0)}, 10, -1)

                .addBuilding(new Coordinate[]{
                        new Coordinate(250, 70, 0),
                        new Coordinate(250, 180, 0),
                        new Coordinate(270, 180, 0),
                        new Coordinate(270, 70, 0)}, 14, -1)

                .addBuilding(new Coordinate[]{
                        new Coordinate(332, 32, 0),
                        new Coordinate(348, 126, 0),
                        new Coordinate(361, 108, 0),
                        new Coordinate(349, 44, 0)}, 10, -1)

                .addBuilding(new Coordinate[]{
                        new Coordinate(400, 5, 0),
                        new Coordinate(400, 85, 0),
                        new Coordinate(415, 85, 0),
                        new Coordinate(415, 5, 0)}, 9, -1)

                .addBuilding(new Coordinate[]{
                        new Coordinate(444, 47, 0),
                        new Coordinate(436, 136, 0),
                        new Coordinate(516, 143, 0),
                        new Coordinate(521, 89, 0),
                        new Coordinate(506, 87, 0),
                        new Coordinate(502, 127, 0),
                        new Coordinate(452, 123, 0),
                        new Coordinate(459, 48, 0)}, 12, -1)

                .addBuilding(new Coordinate[]{
                        new Coordinate(773, 12, 0),
                        new Coordinate(728, 90, 0),
                        new Coordinate(741, 98, 0),
                        new Coordinate(786, 20, 0)}, 14, -1)

                .addBuilding(new Coordinate[]{
                        new Coordinate(972, 82, 0),
                        new Coordinate(979, 121, 0),
                        new Coordinate(993, 118, 0),
                        new Coordinate(986, 79, 0)}, 8, -1)
                .addGroundEffect(-11, 1011, -300, 300,0.5);


        builder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(builder)
                .addSource(0, 50, 4)
                .addReceiver(1000, 100, 1)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.5)
                .build();
        rayData.reflexionOrder=1;
        PathFinderVisitor propDataOut = new PathFinderVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        computeRays.run(propDataOut);

        // Expected Values

        assertEquals(3, propDataOut.getCutProfiles().size());

        assertCutProfile("TC28_Direct", propDataOut.cutProfiles.poll());
        assertCutProfile("TC28_Right", propDataOut.cutProfiles.poll());


        // Error in CNOSSOS unit test, left diffraction is going over a building but not in their 3D view !
        // Why the weird left path in homogeneous ? it is not explained.
        //assertCutProfile("TC28_Left", propDataOut.cutProfiles.poll());

    }


    public static void assertZProfil(List<Coordinate> expectedZProfile, List<Coordinate> actualZ_profile) {
        assertZProfil(expectedZProfile, actualZ_profile, DELTA_COORDS);
    }

    public static void assertZProfil(List<Coordinate> expectedZProfile, List<Coordinate> actualZ_profile, double delta) {
        if (expectedZProfile.size() != actualZ_profile.size()){
            assertEquals(expectedZProfile.size(), actualZ_profile.size(), "Expected zprofil count is different than actual zprofil count.");
        }
        for (int i = 0; i < actualZ_profile.size(); i++) {
            assertEquals(expectedZProfile.get(i).x, actualZ_profile.get(i).x, delta, String.format(Locale.ROOT, "Coord X point %d", i));
            assertEquals(expectedZProfile.get(i).y, actualZ_profile.get(i).y, delta, String.format(Locale.ROOT, "Coord Y point %d", i));
        }
    }

    public static void assertMirrorPoint(Coordinate expectedSprime, Coordinate expectedRprime,Coordinate actualSprime, Coordinate actualRprime) {
        assertCoordinateEquals("Sprime ",expectedSprime, actualSprime, DELTA_COORDS);
        assertCoordinateEquals("Rprime ",expectedRprime, actualRprime, DELTA_COORDS);
    }

    public static void assertCoordinateEquals(String message,Coordinate expected, Coordinate actual, double toleranceX) {
        double diffX = Math.abs(expected.getX() - actual.getX());
        double diffY = Math.abs(expected.getY() - actual.getY());

        if (diffX > toleranceX || diffY > toleranceX) {
            String result = String.format(Locale.ROOT, "Expected coordinate: (%.3f, %.3f), Actual coordinate: (%.3f, %.3f)",
                    expected.getX(), expected.getY(), actual.getX(), actual.getY());
            throw new AssertionError(message+result);
        }
    }

    public static void assert3DCoordinateEquals(String message,Coordinate expected, Coordinate actual, double tolerance) {

        if (CGAlgorithms3D.distance(expected, actual) > tolerance) {
            String result = String.format(Locale.ROOT, "Expected coordinate: %s, Actual coordinate: %s",
                    expected, actual);
            throw new AssertionError(message+result);
        }
    }
//
//    private void exportScene(String name, ProfileBuilder builder, PathFinderVisitor result) throws IOException {
//        try {
//            Coordinate proj = new Coordinate( 351714.794877, 6685824.856402, 0);
//            FileOutputStream outData = new FileOutputStream(name);
//            KMLDocument kmlDocument = new KMLDocument(outData);
//            //kmlDocument.doTransform(builder.getTriangles());
//            kmlDocument.setInputCRS("EPSG:2154");
//            //kmlDocument.setInputCRS("EPSG:" + crs);
//            kmlDocument.setOffset(proj);
//            kmlDocument.writeHeader();
//            if(builder != null) {
//                kmlDocument.writeTopographic(builder.getTriangles(), builder.getVertices());
//                kmlDocument.writeBuildings(builder);
//                kmlDocument.writeWalls(builder);
//                //kmlDocument.writeProfile(PathFinder.getData().profileBuilder.getProfile(rayData.sourceGeometries.get(0).getCoordinate(), rayData.receivers.get(0), computeRays.getData().gS);
//                //kmlDocument.writeProfile("S:0 R:0", builder.getProfile(result.getInputData().sourceGeometries.get(0).getCoordinate(),result.getInputData().receivers.get(0)));
//            }
//            if(result != null) {
//                kmlDocument.writeRays(result.getCutPlanes());
//            }
//            kmlDocument.writeFooter();
//        } catch (XMLStreamException | CoordinateOperationException | CRSException ex) {
//            throw new IOException(ex);
//        }
//    }

    @Test
    public void setOverwriteTestCase() {
        // Disable overwrite state when pushing your code (you are not testing with the commited json)
        assertFalse(overwriteTestCase);
    }
}
