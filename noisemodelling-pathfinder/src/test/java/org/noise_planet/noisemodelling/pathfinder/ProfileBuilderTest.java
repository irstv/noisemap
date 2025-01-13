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
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Building;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.noise_planet.noisemodelling.pathfinder.PathFinderTest.assertZProfil;

/**
 * Test class dedicated to {@link ProfileBuilder}.
 */
public class ProfileBuilderTest {

    /** JTS WKT reader. */
    private static final WKTReader READER = new WKTReader();
    /** Delta value. */
    private static final double DELTA = 1e-8;
    private Logger logger = LoggerFactory.getLogger(ProfileBuilderTest.class);

    /**
     * Test the building adding to a {@link ProfileBuilder}.
     * Polygons are normalized according to ISO, outer ring must be CCW and inner rings are CW
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void buildingAddingTest() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder(3, 3, 3, 2);
        profileBuilder.addBuilding(READER.read("POLYGON((1 1,5 1,5 5,1 5,1 1))"), 10, -1);
        profileBuilder.addBuilding(READER.read("POLYGON((10 10,15 10,15 15,10 15,10 10))"), 23, -1);
        profileBuilder.addBuilding(READER.read("POLYGON((6 8,8 10,8 4,6 8))"), 56, -1);

        profileBuilder.finishFeeding();

        List<Building> list = profileBuilder.getBuildings();
        assertEquals(3, list.size());
        assertEquals("POLYGON ((1 1, 1 5, 5 5, 5 1, 1 1))", list.get(0).getGeometry().toText());
        assertEquals(10, list.get(0).getGeometry().getCoordinate().z, 0);
        assertEquals("POLYGON ((10 10, 10 15, 15 15, 15 10, 10 10))", list.get(1).getGeometry().toText());
        assertEquals(23, list.get(1).getGeometry().getCoordinate().z, 0);
        assertEquals("POLYGON ((6 8, 8 10, 8 4, 6 8))", list.get(2).getGeometry().toText());
        assertEquals(56, list.get(2).getGeometry().getCoordinate().z, 0);
    }

    /**
     * Test the finish of {@link ProfileBuilder} feeding.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void finishBuildingFeedingTest() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder(3, 3, 3, 2);
        profileBuilder.addBuilding(READER.read("POLYGON((1 1,5 1,5 5,1 5,1 1))"), 10);
        assertNotNull(profileBuilder.finishFeeding());
        profileBuilder.addBuilding(READER.read("POLYGON((10 10,15 10,15 15,10 15,10 10))"), 23);
        profileBuilder.addBuilding(READER.read("POLYGON((6 8,8 10,8 4,6 8))"), 56);

        List<Building> list = profileBuilder.getBuildings();
        assertEquals(1, list.size());
    }

    /**
     * Test the topographic adding to a {@link ProfileBuilder}.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void topoAddingTest() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder(3, 3, 3, 2);
        profileBuilder.addTopographicLine((LineString) READER.read("LINESTRING (4 1 1.5, 5 7 1.0, 8 9 1.5)"));
        profileBuilder.addTopographicPoint(new Coordinate(7, 9, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(2, 4, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(6, 1, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(4, 4, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(2, 5, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(1, 9, 2.0));
        profileBuilder.addTopographicPoint(new Coordinate(8, 2, 2.0));
        profileBuilder.finishFeeding();

        assertEquals(11, profileBuilder.getTriangles().size());
    }

    /**
     * Test the finish of {@link ProfileBuilder} feeding.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void topoBuildingFeedingTest() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder(3, 3, 3, 2);
        profileBuilder.addTopographicLine((LineString) READER.read("LINESTRING (4 1 1.5, 5 7 1.0, 8 9 1.5)"));
        profileBuilder.addTopographicPoint(new Coordinate(7, 9, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(2, 4, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(6, 1, 3.0));
        assertNotNull(profileBuilder.finishFeeding());
        profileBuilder.addTopographicPoint(new Coordinate(4, 4, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(2, 5, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(1, 9, 2.0));
        profileBuilder.addTopographicPoint(new Coordinate(8, 2, 2.0));

        assertEquals(4, profileBuilder.getTriangles().size());
    }


    /**
     * Test the topographic cut profile generation.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void topoCutProfileTest() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder(3, 3, 3, 2);
        profileBuilder.addTopographicLine((LineString) READER.read("LINESTRING (4 1 1.5, 5 7 1.0, 8 9 1.5)"));
        profileBuilder.addTopographicPoint(new Coordinate(7, 9, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(2, 4, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(6, 1, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(4, 4, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(2, 5, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(1, 9, 2.0));
        profileBuilder.addTopographicPoint(new Coordinate(8, 2, 2.0));
        profileBuilder.finishFeeding();

        CutProfile profile = profileBuilder.getProfile(new Coordinate(0, 1, 0.1), new Coordinate(8, 10, 0.3));
        List<CutPoint> pts = profile.cutPoints;
        assertEquals(0.0, pts.get(0).getCoordinate().x, DELTA);
        assertEquals(1.0, pts.get(0).getCoordinate().y, DELTA);
        assertEquals(0.1, pts.get(0).getCoordinate().z, DELTA);
        assertEquals(8.0, pts.get(pts.size() - 1).getCoordinate().x, DELTA);
        assertEquals(10.0, pts.get(pts.size() - 1).getCoordinate().y, DELTA);
        assertEquals(0.3, pts.get(pts.size() - 1).getCoordinate().z, DELTA);
    }

    /**
     * Test the ground adding to a {@link ProfileBuilder}.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void groundAddingTest() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder(3, 3, 3, 2);
        profileBuilder.addGroundEffect(READER.read("POLYGON((-1 7, -0.5 8, 0 8.5, 1 9, 1.5 7, 2 6, 2.5 7, 3 9, 5.5 8.5, 7 7, 7 6, 5 5, 5 4, 4 2, 2 3, 1 5, 0 6, -1 7))"), 0.5);
        profileBuilder.addGroundEffect(READER.read("POLYGON((8 1, 7 2, 7 4.5, 8 5, 9 4.5, 10 3.5, 9.5 2, 8 1))"), 0.25);
        profileBuilder.finishFeeding();

        assertEquals(2, profileBuilder.getGroundEffects().size());
    }

    /**
     * Test the finish of {@link ProfileBuilder} feeding.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void groundBuildingFeedingTest() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder(3, 3, 3, 2);
        profileBuilder.addGroundEffect(READER.read("POLYGON((-1 7, -0.5 8, 0 8.5, 1 9, 1.5 7, 2 6, 2.5 7, 3 9, 5.5 8.5, 7 7, 7 6, 5 5, 5 4, 4 2, 2 3, 1 5, 0 6, -1 7))"), 0.5);
        assertNotNull(profileBuilder.finishFeeding());
        profileBuilder.addGroundEffect(READER.read("POLYGON((8 1, 7 2, 7 4.5, 8 5, 9 4.5, 10 3.5, 9.5 2, 8 1))"), 0.25);

        assertEquals(1, profileBuilder.getGroundEffects().size());
    }

    /**
     * Test the ground cut profile generation.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void groundCutProfileTest() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder(3, 3, 3, 2);
        profileBuilder.addGroundEffect(READER.read("POLYGON((-1 7, -0.5 8, 0 8.5, 1 9, 1.5 7, 2 6, 2.5 7, 3 9, 5.5 8.5, 7 7, 7 6, 5 5, 5 4, 4 2, 2 3, 1 5, 0 6, -1 7))"), 0.5);
        profileBuilder.addGroundEffect(READER.read("POLYGON((8 1, 7 2, 7 4.5, 8 5, 9 4.5, 10 3.5, 9.5 2, 8 1))"), 0.25);
        profileBuilder.finishFeeding();

        CutProfile profile = profileBuilder.getProfile(new Coordinate(0, 1, 0.1), new Coordinate(8, 10, 0.3));
        List<CutPoint> pts = profile.cutPoints;
        assertEquals(4, pts.size());
        assertEquals(0.0, pts.get(0).getCoordinate().x, DELTA);
        assertEquals(1.0, pts.get(0).getCoordinate().y, DELTA);
        assertEquals(0.1, pts.get(0).getCoordinate().z, DELTA);
        assertEquals(8.0, pts.get(3).getCoordinate().x, DELTA);
        assertEquals(10.0, pts.get(3).getCoordinate().y, DELTA);
        assertEquals(0.3, pts.get(3).getCoordinate().z, DELTA);
    }



    /**
     * Test the cut profile generation.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void allCutProfileTest() throws Exception {
        ProfileBuilder profileBuilder = new ProfileBuilder(3, 3, 3, 2);

        profileBuilder.addBuilding(READER.read("POLYGON((2 2 10, 1 3 15, 2 4 10, 3 3 12, 2 2 10))"), 10);
        profileBuilder.addBuilding(READER.read("POLYGON((4.5 7, 4.5 8.5, 6.5 8.5, 4.5 7))"), 3.3);
        profileBuilder.addBuilding(READER.read("POLYGON((7 6, 10 6, 10 2, 7 2, 7 6))"), 5.6);

        profileBuilder.addTopographicLine((LineString) READER.read("LINESTRING (4 1 1.5, 5 7 1.0, 8 9 1.5)"));
        profileBuilder.addTopographicPoint(new Coordinate(7, 9, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(2, 4, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(6, 1, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(4, 4, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(2, 5, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(1, 9, 2.0));
        profileBuilder.addTopographicPoint(new Coordinate(8, 2, 2.0));

        profileBuilder.addGroundEffect(READER.read("POLYGON((-1 -1, -1 2, 2 2, 2 -1, -1 -1))"), 0.6);
        profileBuilder.addGroundEffect(READER.read("POLYGON((-1 7, -0.5 8, 0 8.5, 1 9, 1.5 7, 2 6, 2.5 7, 3 9, 5.5 8.5, 7 7, 7 6, 5 5, 5 4, 4 2, 2 3, 1 5, 0 6, -1 7))"), 0.5);
        profileBuilder.addGroundEffect(READER.read("POLYGON((8 1, 7 2, 7 4.5, 8 5, 9 4.5, 10 3.5, 9.5 2, 8 1))"), 0.25);
        profileBuilder.finishFeeding();

        CutProfile profile = profileBuilder.getProfile(new Coordinate(0, 1, 0.1), new Coordinate(8, 10, 0.3));

        List<CutPoint> pts = profile.cutPoints;
        assertEquals(0.0, pts.get(0).getCoordinate().x, DELTA);
        assertEquals(1.0, pts.get(0).getCoordinate().y, DELTA);
        assertEquals(0.1, pts.get(0).getCoordinate().z, DELTA);
        assertEquals(8.0, pts.get(pts.size() - 1).getCoordinate().x, DELTA);
        assertEquals(10.0, pts.get(pts.size() - 1).getCoordinate().y, DELTA);
        assertEquals(0.3, pts.get(pts.size() - 1).getCoordinate().z, DELTA);

    }

    @Test
    public void testProfileTopographicGroundEffectWall() throws Exception {

        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                //Ground effects
                .addGroundEffect(0.0, 50.0, -20.0, 80.0, 0.9)
                .addGroundEffect(50.0, 150.0, -20.0, 80.0, 0.5)
                .addGroundEffect(150.0, 225.0, -20.0, 80.0, 0.2)
                //Topography
                .addTopographicLine(0, 80, 0, 225, 80, 0)
                .addTopographicLine(225, 80, 0, 225, -20, 0)
                .addTopographicLine(225, -20, 0, 0, -20, 0)
                .addTopographicLine(0, -20, 0, 0, 80, 0)
                .addTopographicLine(120, -20, 0, 120, 80, 0)
                .addTopographicLine(185, -5, 10, 205, -5, 10)
                .addTopographicLine(205, -5, 10, 205, 75, 10)
                .addTopographicLine(205, 75, 10, 185, 75, 10)
                .addTopographicLine(185, 75, 10, 185, -5, 10)
                // Add building
                .addWall(new Coordinate[]{
                                new Coordinate(175, 50, 17),
                                new Coordinate(190, 10, 14)},
                        1)
                .finishFeeding();

        Coordinate receiver = new Coordinate(200, 50, 14);
        Coordinate source = new Coordinate(10, 10, 1);
        CutProfile cutProfile = profileBuilder.getProfile(source, receiver, 0, false);
        assertEquals(7, cutProfile.cutPoints.size());
        PathFinderTest.assert3DCoordinateEquals("", new Coordinate(10, 10, 1), cutProfile.cutPoints.get(0).getCoordinate(), 0.01);
        PathFinderTest.assert3DCoordinateEquals("", new Coordinate(50, 18.421, 0), cutProfile.cutPoints.get(1).getCoordinate(), 0.01);
        PathFinderTest.assert3DCoordinateEquals("", new Coordinate(120, 33.158, 0), cutProfile.cutPoints.get(2).getCoordinate(), 0.01);
        PathFinderTest.assert3DCoordinateEquals("", new Coordinate(150, 39.474, 4.616), cutProfile.cutPoints.get(3).getCoordinate(), 0.01);
        PathFinderTest.assert3DCoordinateEquals("", new Coordinate(176.83, 45.122, 16.634), cutProfile.cutPoints.get(4).getCoordinate(), 0.01);
        PathFinderTest.assert3DCoordinateEquals("", new Coordinate(185, 46.842, 10), cutProfile.cutPoints.get(5).getCoordinate(), 0.01);
        PathFinderTest.assert3DCoordinateEquals("", new Coordinate(200, 50, 14), cutProfile.cutPoints.get(6).getCoordinate(), 0.01);
    }

    @Test
    public void testRelativeSourceLineProjection() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder();
        PathFinderTest.addTopographicTC5Model(profileBuilder);
        profileBuilder.finishFeeding();
        Scene scene = new Scene(profileBuilder);
        WKTReader wktReader = new WKTReader();
        Geometry geometry = wktReader.read("MultiLineStringZ ((10 10 1, 200 50 1))");
        scene.addSource(1L, geometry);
        PathFinder pathFinder = new PathFinder(scene);
        assertEquals(2, scene.sourceGeometries.get(0).getNumPoints());
        pathFinder.makeSourceRelativeZToAbsolute();
        // The source line should now be made of 4 points (2 points being created by the elevated DEM)
        assertEquals(4, scene.sourceGeometries.get(0).getNumPoints());
        List<Coordinate> expectedProfile = Arrays.asList(
                new Coordinate(10.0, 10.0, 1.0),
                new Coordinate(120.0, 33.16, 1.0),
                new Coordinate(185.0, 46.84, 11.0),
                new Coordinate(200.0, 50.0, 11.0));
        assertZProfil(expectedProfile, Arrays.asList(scene.sourceGeometries.get(0).getCoordinates()));
    }


    @Test
    public void test2DGroundProfile() {

        //Profile building (from TC15)
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

        CutProfile cutProfile = profileBuilder.getProfile(new Coordinate(50,10,1), new Coordinate(100, 15, 5));

        assertEquals(9, cutProfile.cutPoints.size());

        List<Integer> index = new ArrayList<>(cutProfile.cutPoints.size());
        List<Coordinate> zProfile = cutProfile.computePts2DGround(index);

        assertEquals(cutProfile.cutPoints.size(), index.size());

        /* Table 148 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(5.02, 0.00));
        expectedZProfile.add(new Coordinate(5.02, 8.00));
        expectedZProfile.add(new Coordinate(15.07, 8.0));
        expectedZProfile.add(new Coordinate(15.08, 0.0));
        expectedZProfile.add(new Coordinate(24.81, 0.0));
        expectedZProfile.add(new Coordinate(24.81, 12.0));
        expectedZProfile.add(new Coordinate(30.15, 12.0));
        expectedZProfile.add(new Coordinate(30.15, 0.00));
        expectedZProfile.add(new Coordinate(37.19, 0.0));
        expectedZProfile.add(new Coordinate(37.19, 10.0));
        expectedZProfile.add(new Coordinate(41.52, 10.0));
        expectedZProfile.add(new Coordinate(41.52, 0.0));
        expectedZProfile.add(new Coordinate(50.25, 0.0));

        //Assertion
        assertZProfil(expectedZProfile, zProfile);

        assertArrayEquals(new int[]{0, 2, 4, 6, 8, 10, 12, 12, 13},
                index.stream().mapToInt(Integer::intValue).toArray());


    }
}
