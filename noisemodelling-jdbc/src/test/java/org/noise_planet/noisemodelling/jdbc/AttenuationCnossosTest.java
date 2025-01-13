/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.math.Vector3D;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.propagation.AttenuationVisitor;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunayError;
import org.noise_planet.noisemodelling.propagation.cnossos.PointPath;
import org.noise_planet.noisemodelling.propagation.cnossos.SegmentPath;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilderDecorator;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.WallAbsorption;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.QueryRTree;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;
import org.noise_planet.noisemodelling.propagation.Attenuation;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import static java.lang.Double.NaN;
import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.jdbc.Utils.*;
import static org.noise_planet.noisemodelling.pathfinder.path.Scene.DEFAULT_FREQUENCIES_THIRD_OCTAVE;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.dbaToW;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.multArray;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.sumArray;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.sumDbArray;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.wToDba;

/**
 * Test class evaluation and testing attenuation values.
 */
public class AttenuationCnossosTest {

    /**
     *  Error for planes values
     */
    public static final double DELTA_PLANES = 0.1;

    /**
     *  Error for coordinates
     */
    public static final double DELTA_COORDS = 0.1;

    /**
     *  Error for G path value
     */
    public static final double DELTA_G_PATH = 0.02;

    private final static Logger LOGGER = LoggerFactory.getLogger(AttenuationCnossosTest.class);
    public static final double ERROR_EPSILON_HIGHEST = 1e5;
    public static final double ERROR_EPSILON_VERY_HIGH = 15;
    public static final double ERROR_EPSILON_HIGH = 3;
    public static final double ERROR_EPSILON_MEDIUM = 1;
    public static final double ERROR_EPSILON_LOW = 0.5;
    public static final double ERROR_EPSILON_VERY_LOW = 0.1;
    public static final double ERROR_EPSILON_LOWEST = 0.02;

    private static final double[] HOM_WIND_ROSE = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    private static final double[] FAV_WIND_ROSE = new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};

    private static final double HUMIDITY = 70;
    private static final double TEMPERATURE = 10;
    private static final double[] SOUND_POWER_LEVELS = new double[]{93, 93, 93, 93, 93, 93, 93, 93};
    private static final double[] A_WEIGHTING = new double[]{-26.2, -16.1, -8.6, -3.2, 0.0, 1.2, 1.0, -1.1};



    private static void assertDoubleArrayEquals(String valueName, double[] expected, double [] actual, double delta) {
        assertEquals(expected.length, actual.length, valueName + ": Different array length;");
        for(int i=0; i< expected.length; i++) {
            if(!Double.isNaN(expected[i])){
                assertEquals(expected[i], actual[i], delta, valueName + ": Arrays first differed at element ["+i+"];");
            }
        }
    }
    private static void writeResultsToRst(String fileName, String testName, String variableName, double[] expected, double[] actual) {
        try {
            FileWriter writer = new FileWriter(fileName, true);
            writer.append("Test Case: " + testName + "\n");
            writer.append("Variable: " + variableName + "\n");
            writer.append("Expected: " + Arrays.toString(expected) + "\n");
            writer.append("Actual: " + Arrays.toString(actual) + "\n");
            writer.append("\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Test body-barrier effect
     * NMPB08 – Railway Emission Model
     * Programmers Guide
     */
    //long startTime = System.currentTimeMillis();
    @Test
    public void testBodyBarrier() {


        // Hard barrier
        List<Double> alphas = Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder
                .addWall(new Coordinate[]{
                        new Coordinate(3, -100, 0),
                        new Coordinate(3, 100, 0)
                }, 2.5,alphas,1)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(0.5, 0, 0.)
                .addReceiver(25, 0, 4)
                .setGs(1.0)
                .build();
        rayData.reflexionOrder=1;
        rayData.maxSrcDist = 1000;
        rayData.setComputeHorizontalDiffraction(false);
        rayData.setComputeVerticalDiffraction(true);
        rayData.setBodyBarrier(true);

        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        //Run computation
        Attenuation propDataOut0 = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays0 = new PathFinder(rayData);
        computeRays0.setThreadCount(1);
        computeRays0.run(propDataOut0);
        double[] values0 = propDataOut0.receiversAttenuationLevels.pop().value;

        // Barrier, no interaction
        rayData.setBodyBarrier(false);
        Attenuation propDataOut1 = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays1 = new PathFinder(rayData);
        computeRays1.setThreadCount(1);
        computeRays1.run(propDataOut1);
        double[] values1 = propDataOut1.receiversAttenuationLevels.pop().value;

        // Soft barrier (a=0.5)

        alphas = Arrays.asList(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
        //Profile building
        ProfileBuilder profileBuilder2 = new ProfileBuilder();
        profileBuilder2
                .addWall(new Coordinate[]{
                        new Coordinate(3, -100, 0),
                        new Coordinate(3, 100, 0)
                }, 2.5,alphas,1)
                .finishFeeding();
        //Propagation data building
        Scene rayData2 = new ProfileBuilderDecorator(profileBuilder2)
                .addSource(0.5, 0, 0.)
                .addReceiver(25, 0, 4)
                .setGs(1.0)
                .build();
        rayData2.reflexionOrder=1;
        rayData2.maxSrcDist = 1000;
        rayData2.setComputeHorizontalDiffraction(false);
        rayData2.setComputeVerticalDiffraction(true);
        rayData2.setBodyBarrier(true);

        Attenuation propDataOut2 = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays2 = new PathFinder(rayData2);
        computeRays2.run(propDataOut2);
        double[] values2 = propDataOut2.receiversAttenuationLevels.pop().value;

        // No barrier
        ProfileBuilder profileBuilder3 = new ProfileBuilder();
        profileBuilder3
                .addWall(new Coordinate[]{
                        new Coordinate(100, -100, 0),
                        new Coordinate(100, 100, 0)
                }, 0,alphas,1)
                .finishFeeding();
        Scene rayData3 = new ProfileBuilderDecorator(profileBuilder3)
                .addSource(0.5, 0, 0.)
                .addReceiver(25, 0, 4)
                .setGs(1.0)
                .build();
        rayData3.reflexionOrder=1;
        rayData3.maxSrcDist = 1000;
        rayData3.setComputeHorizontalDiffraction(false);
        rayData3.setComputeVerticalDiffraction(true);
        rayData3.setBodyBarrier(false);
        Attenuation propDataOut3 = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays3 = new PathFinder(rayData3);
        computeRays3.run(propDataOut3);
        double[] values3 = propDataOut3.receiversAttenuationLevels.pop().value;


        double[] values0A = sumArray(values0, new double[]{-26.2,-16.1,-8.6,3.2,0,1.2,1.0,-1.1});
        double[] values1A = sumArray(values1, new double[]{-26.2,-16.1,-8.6,3.2,0,1.2,1.0,-1.1});
        double[] values2A = sumArray(values2, new double[]{-26.2,-16.1,-8.6,3.2,0,1.2,1.0,-1.1});
        double[] values3A = sumArray(values3, new double[]{-26.2,-16.1,-8.6,3.2,0,1.2,1.0,-1.1});
        double r0A = wToDba(sumArray(dbaToW(values0A)));
        double r1A = wToDba(sumArray(dbaToW(values1A)));
        double r2A = wToDba(sumArray(dbaToW(values2A)));
        double r3A = wToDba(sumArray(dbaToW(values3A)));

        /*assertEquals(19.2,r3A-r1A,0.5);
        assertEquals(11.7,r0A-r1A,1);
        assertEquals(6.6,r2A-r1A,1);*/


    }

    /**
     * Test reflexion ray has contribution :
     *
     *   ------------------------------
     *               /\
     *              | R
     *              | |
     *              |/
     *              S
     */
    @Test
    public void testSimpleReflexion() {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder
                .addWall(new Coordinate[]{
                        new Coordinate(-100, 40, 10),
                        new Coordinate(100, 40, 10)
                }, -1)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(30, -10, 2)
                .addReceiver(30, 20, 2)
                .setGs(0.0)
                .build();
        rayData.reflexionOrder=0;

        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        //Run computation
        Attenuation propDataOut0 = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays0 = new PathFinder(rayData);
        computeRays0.setThreadCount(1);
        rayData.reflexionOrder=0;
        computeRays0.run(propDataOut0);
        double[] values0 = propDataOut0.receiversAttenuationLevels.pop().value;

        Attenuation propDataOut1 = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays1 = new PathFinder(rayData);
        computeRays1.setThreadCount(1);
        rayData.reflexionOrder=1;
        computeRays1.run(propDataOut1);
        double[] values1 = propDataOut1.receiversAttenuationLevels.pop().value;
        assertNotEquals(values0[0], values1[0]);
        assertNotEquals(values0[1], values1[1]);
        assertNotEquals(values0[2], values1[2]);
        assertNotEquals(values0[3], values1[3]);
        assertNotEquals(values0[4], values1[4]);
        assertNotEquals(values0[5], values1[5]);
        assertNotEquals(values0[6], values1[6]);
        assertNotEquals(values0[7], values1[7]);
    }

    /**
     * Test identic rays : to the east, to the west
     */
    @Test
    public void eastWestTest() {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder
                .addBuilding(new Coordinate[]{
                        new Coordinate(10, -5),
                        new Coordinate(20, -5),
                        new Coordinate(20, 5),
                        new Coordinate(10, 5)
                }, 0.0)
                .addBuilding(new Coordinate[]{
                        new Coordinate(-10, -5),
                        new Coordinate(-20, -5),
                        new Coordinate(-20, 5),
                        new Coordinate(-10, 5)
                }, 0.0)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(0, 0, 2)
                .addReceiver(30, 0, 2)
                .addReceiver(-30, 0, 2)
                .setGs(0.0)
                .build();

        rayData.reflexionOrder = 0;

        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        //Out and computation settings
        Attenuation propDataOut = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);
        SegmentPath s0 = propDataOut.getPropagationPaths().get(0).getSRSegment();
        SegmentPath s1 = propDataOut.getPropagationPaths().get(1).getSRSegment();
        assertEquals(s0.dp, s1.dp);
        assertEquals(s0.testFormH, s1.testFormH);
        assertArrayEquals(propDataOut.receiversAttenuationLevels.pop().value, propDataOut.receiversAttenuationLevels.pop().value, Double.MIN_VALUE);
    }

    /**
     * Test identic rays : to the east, to the west
     */
    @Test
    public void northSouthTest() {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder
                .addBuilding(new Coordinate[]{
                        new Coordinate(-5, 10),
                        new Coordinate(-5, 20),
                        new Coordinate(5, 20),
                        new Coordinate(5, 10)
                }, 1.0)
                .addBuilding(new Coordinate[]{
                        new Coordinate(-5, -10 ),
                        new Coordinate(-5, -20 ),
                        new Coordinate(5, -20),
                        new Coordinate(5, -10)
                }, 1.0)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(0, 0, 2)
                .addReceiver(0, 30, 2)
                .addReceiver(0, -30, 2)
                .setGs(0.0)
                .build();

        rayData.reflexionOrder = 0;

        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        //Out and computation settings
        Attenuation propDataOut = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(2, propDataOut.getPropagationPaths().size());

        SegmentPath s0 = propDataOut.getPropagationPaths().get(0).getSRSegment();
        SegmentPath s1 = propDataOut.getPropagationPaths().get(1).getSRSegment();
        assertEquals(s0.dp, s1.dp);
        assertEquals(s0.testFormH, s1.testFormH);
        assertArrayEquals(propDataOut.receiversAttenuationLevels.pop().value, propDataOut.receiversAttenuationLevels.pop().value, Double.MIN_VALUE);
    }

    @Test
    public void northSouthGroundTest() {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder
                .addGroundEffect(-50, 50, -5, 5, 0.5)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(0, 0, 2)
                .addReceiver(0, 30, 2)
                .addReceiver(0, -30, 2)
                .setGs(0.0)
                .build();

        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        //Out and computation settings
        Attenuation propDataOut = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertArrayEquals(propDataOut.receiversAttenuationLevels.pop().value, propDataOut.receiversAttenuationLevels.pop().value, Double.MIN_VALUE);
    }

    @Test
    public void eastWestGroundTest() {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder
                .addGroundEffect(-5, 5, -50, 50, 0.5)
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(0, 0, 2)
                .addReceiver(30, 0, 2)
                .addReceiver(-30, 0, 2)
                .setGs(0.0)
                .build();

        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        //Out and computation settings
        Attenuation propDataOut = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertArrayEquals(propDataOut.receiversAttenuationLevels.pop().value, propDataOut.receiversAttenuationLevels.pop().value, Double.MIN_VALUE);
    }


    private static CutProfile loadCutProfile(String utName) throws IOException {
        String testCaseFileName = utName + ".json";
        try(InputStream inputStream = PathFinder.class.getResourceAsStream("test_cases/"+testCaseFileName)) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(inputStream, CutProfile.class);
        }
    }

    private static Attenuation computeCnossosPath(String... utNames)
            throws IOException {
        //Create profile builder
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .build();

        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        //Out and computation settings
        Attenuation propDataOut = new Attenuation(true, true, attData, rayData);

        AttenuationVisitor attenuationVisitor = new AttenuationVisitor(propDataOut, propDataOut.genericMeteoData);
        for (String utName : utNames) {
            CutProfile cutProfile = loadCutProfile(utName);
            attenuationVisitor.onNewCutPlane(cutProfile);
        }
        // merge attenuation per receiver
        attenuationVisitor.finalizeReceiver(0);

        return propDataOut;
    }

    /**
     * Test TC01 -- Reflecting ground (G = 0)
     */
    @Test
    public void TC01() throws IOException {

        Attenuation propDataOut = computeCnossosPath("TC01_Direct");

        //Expected values
        double[][][] pts = new double[][][]{
                {{0.00, 1.00}, {194.16, 4.00}} //Path 1 : direct
        };
        double[][] gPaths = new double[][]{
                {0.0} //Path 1 : direct
        };

        //Assertion
        assertPaths(pts, gPaths, propDataOut.getPropagationPaths());

        //Expected values
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00};
        double[] expectedCfH = new double[]{194.16, 194.16, 194.16, 194.16, 194.16, 194.16, 194.16, 194.16};
        double[] expectedAGroundH = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00};
        double[] expectedCfF = new double[]{194.16, 194.16, 194.16, 194.16, 194.16, 194.16, 194.16, 194.16};
        double[] expectedAGroundF = new double[]{-4.36, -4.36, -4.36, -4.36, -4.36, -4.36, -4.36, -4.36};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.36, 22.70};
        double[] expectedADiv = new double[]{56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76};
        double[] expectedABoundaryH = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        double[] expectedABoundaryF = new double[]{-4.36, -4.36, -4.36, -4.36, -4.36, -4.36, -4.36, -4.36};
        double[] expectedLH = new double[]{39.21, 39.16, 39.03, 38.86, 38.53, 37.36, 32.87, 16.54};
        double[] expectedLF = new double[]{40.58, 40.52, 40.40, 40.23, 39.89, 38.72, 34.24, 17.90};
        double[] expectedL = new double[]{39.95, 39.89, 39.77, 39.60, 39.26, 38.09, 33.61, 17.27};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);


        //Actual values
        double[] actualWH = propDataOut.getPropagationPaths().get(0).groundAttenuation.wH;
        double[] actualCfH = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfH;
        double[] actualAGroundH = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundH;
        double[] actualWF = propDataOut.getPropagationPaths().get(0).groundAttenuation.wF;
        double[] actualCfF = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfF;
        double[] actualAGroundF = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = propDataOut.getPropagationPaths().get(0).aAtm;
        double[] actualADiv = propDataOut.getPropagationPaths().get(0).aDiv;
        double[] actualABoundaryH = propDataOut.getPropagationPaths().get(0).double_aBoundaryH;
        double[] actualABoundaryF = propDataOut.getPropagationPaths().get(0).double_aBoundaryF;
        double[] actualLH = addArray(propDataOut.getPropagationPaths().get(0).aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(propDataOut.getPropagationPaths().get(0).aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(propDataOut.getPropagationPaths().get(0).aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AAtm", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryF", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH", expectedLH, actualLH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LF", expectedLF, actualLF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("L", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

    }

/**
* Test TC02 -- Mixed ground (G = 0.5)
*/
    @Test
    public void TC02() throws IOException {

        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC02_Direct");

        //Expected values
        double[][][] pts = new double[][][]{
                {{0.00, 1.00}, {194.16, 4.00}} //Path 1 : direct
        };
        double[][] gPaths = new double[][]{
                {0.5} //Path 1 : direct
        };

        //Assertion
        assertPaths(pts, gPaths, propDataOut.getPropagationPaths());

        //Expected values
        double[] expectedWH = new double[]{8.2e-05, 4.5e-04, 2.5e-03, 0.01, 0.08, 0.41, 2.10, 10.13};
        double[] expectedCfH = new double[]{199.17, 213.44, 225.43, 134.05, 23.76, 2.49, 0.47, 0.10};
        double[] expectedAGroundH = new double[]{-1.50, -1.50, -1.50, 0.85, 5.71, -1.50, -1.50, -1.50};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        double[] expectedCfF = new double[]{199.17, 213.44, 225.43, 134.05, 23.76, 2.49, 0.47, 0.10};
        double[] expectedAGroundF = new double[]{-2.18, -2.18, -2.18, -2.18, -0.93, -2.18, -2.18, -2.18};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.36, 22.70};
        double[] expectedADiv = new double[]{56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76};
        double[] expectedABoundaryH = new double[]{-1.50, -1.50, -1.50, 0.85, 5.71, -1.50, -1.50, -1.50};
        double[] expectedABoundaryF = new double[]{-2.18, -2.18, -2.18, -2.18, -0.93, -2.18, -2.18, -2.18};
        double[] expectedLH = new double[]{37.71, 37.66, 37.53, 35.01, 29.82, 35.86, 31.37, 15.04};
        double[] expectedLF = new double[]{38.39, 38.34, 38.22, 38.04, 36.45, 36.54, 32.05, 15.72};
        double[] expectedL = new double[]{38.07, 38.01, 37.89, 36.79, 34.29, 36.21, 31.73, 15.39};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);

        //Actual values
        double[] actualWH = propDataOut.getPropagationPaths().get(0).groundAttenuation.wH;
        double[] actualCfH = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfH;
        double[] actualAGroundH = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundH;
        double[] actualWF = propDataOut.getPropagationPaths().get(0).groundAttenuation.wF;
        double[] actualCfF = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfF;
        double[] actualAGroundF = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = propDataOut.getPropagationPaths().get(0).aAtm;
        double[] actualADiv = propDataOut.getPropagationPaths().get(0).aDiv;
        double[] actualABoundaryH = propDataOut.getPropagationPaths().get(0).double_aBoundaryH;
        double[] actualABoundaryF = propDataOut.getPropagationPaths().get(0).double_aBoundaryF;
        double[] actualLH = addArray(propDataOut.getPropagationPaths().get(0).aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(propDataOut.getPropagationPaths().get(0).aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(propDataOut.getPropagationPaths().get(0).aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);
        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

    }

    /**
     * Test TC03 -- Porous ground (G = 1)
     */
    @Test
    public void TC03() throws IOException {

        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath( "TC03_Direct");

        //Expected values
        double[][][] pts = new double[][][]{
                {{0.00, 1.00}, {194.16, 4.00}} //Path 1 : direct
        };
        double[][] gPaths = new double[][]{
                {1.0} //Path 1 : direct
        };

        //Assertion
        assertPaths(pts, gPaths, propDataOut.getPropagationPaths());

        //Expected values
        double[] expectedWH = new double[]{4.9e-04, 2.7e-03, 1.5e-02, 0.08, 0.41, 2.02, 9.06, 35.59};
        double[] expectedCfH = new double[]{214.47, 224.67, 130.15, 22.76, 2.48, 0.49, 0.11, 0.03};
        double[] expectedAGroundH = new double[]{0.00, 0.00, 1.59, 9.67, 5.03, 0.00, 0.00, 0.00};
        double[] expectedWF = new double[]{0.00, 0.00, 0.01, 0.08, 0.41, 2.02, 9.06, 35.59};
        double[] expectedCfF = new double[]{214.47, 224.67, 130.15, 22.76, 2.48, 0.49, 0.11, 0.03};
        double[] expectedAGroundF = new double[]{0.00, 0.00, 0.00, 4.23, 0.00, 0.00, 0.00, 0.00};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.36, 22.70};
        double[] expectedADiv = new double[]{56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76};
        double[] expectedABoundaryH = new double[]{0.00, 0.00, 1.59, 9.67, 5.03, 0.00, 0.00, 0.00};
        double[] expectedABoundaryF = new double[]{0.00, 0.00, 0.00, 4.23, 0.00, 0.00, 0.00, 0.00};
        double[] expectedLH = new double[]{36.21, 36.16, 34.45, 26.19, 30.49, 34.36, 29.87, 13.54};
        double[] expectedLF = new double[]{36.21, 36.16, 36.03, 31.63, 35.53, 34.36, 29.87, 13.54};
        double[] expectedL = new double[]{36.21, 36.16, 35.31, 29.71, 33.70, 34.36, 29.87, 13.54};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);
        //Actual values
        double[] actualWH = propDataOut.getPropagationPaths().get(0).groundAttenuation.wH;
        double[] actualCfH = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfH;
        double[] actualAGroundH = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundH;
        double[] actualWF = propDataOut.getPropagationPaths().get(0).groundAttenuation.wF;
        double[] actualCfF = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfF;
        double[] actualAGroundF = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = propDataOut.getPropagationPaths().get(0).aAtm;
        double[] actualADiv = propDataOut.getPropagationPaths().get(0).aDiv;
        double[] actualABoundaryH = propDataOut.getPropagationPaths().get(0).double_aBoundaryH;
        double[] actualABoundaryF = propDataOut.getPropagationPaths().get(0).double_aBoundaryF;
        double[] actualLH = addArray(propDataOut.getPropagationPaths().get(0).aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(propDataOut.getPropagationPaths().get(0).aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(propDataOut.getPropagationPaths().get(0).aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);
        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

    }

    /**
     * Test TC04 -- Flat ground with spatially varying acoustic properties
     */
    @Test
    public void TC04() throws IOException {

        //Out and computation settings
        Attenuation propDataOut = computeCnossosPath("TC04_Direct");

        //Expected values
        double[][][] pts = new double[][][]{
                {{0.00, 1.00}, {194.16, 4.00}} //Path 1 : direct
        };
        double[][] gPaths = new double[][]{
                {0.2*(40.88/194.16) + 0.5*(102.19/194.16) + 0.9*(51.09/194.16)} //Path 1 : direct
        };

        //Assertion
        assertPaths(pts, gPaths, propDataOut.getPropagationPaths());

        //Expected values
        double[] expectedWH = new double[]{1.0e-04, 5.6e-04, 3.1e-03, 0.02, 0.09, 0.50, 2.53, 11.96};
        double[] expectedCfH = new double[]{200.18, 216.12, 221.91, 116.87, 17.87, 2.02, 0.39, 0.08};
        double[] expectedAGroundH = new double[]{-1.37, -1.37, -1.37, 1.77, 6.23, -1.37, -1.37, -1.37};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.02, 0.09, 0.50, 2.53, 11.96};
        double[] expectedCfF = new double[]{200.18, 216.12, 221.91, 116.87, 17.87, 2.02, 0.39, 0.08};
        double[] expectedAGroundF = new double[]{-2.00, -2.00, -2.00, -2.00, -0.95, -2.00, -2.00, -2.00};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.36, 22.70};
        double[] expectedADiv = new double[]{56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76};
        double[] expectedABoundaryH = new double[]{-1.37, -1.37, -1.37, 1.77, 6.23, -1.37, -1.37, -1.37};
        double[] expectedABoundaryF = new double[]{-2.00, -2.00, -2.00, -2.00, -0.95, -2.00, -2.00, -2.00};
        double[] expectedLH = new double[]{37.59, 37.53, 37.41, 34.10, 29.29, 35.73, 31.25, 14.91};
        double[] expectedLF = new double[]{38.21, 38.15, 38.03, 37.86, 36.48, 36.36, 31.87, 15.54};
        double[] expectedL = new double[]{37.91, 37.85, 37.73, 36.37, 34.23, 36.06, 31.57, 15.24};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);
        //Actual values
        double[] actualWH = propDataOut.getPropagationPaths().get(0).groundAttenuation.wH;
        double[] actualCfH = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfH;
        double[] actualAGroundH = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundH;
        double[] actualWF = propDataOut.getPropagationPaths().get(0).groundAttenuation.wF;
        double[] actualCfF = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfF;
        double[] actualAGroundF = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = propDataOut.getPropagationPaths().get(0).aAtm;
        double[] actualADiv = propDataOut.getPropagationPaths().get(0).aDiv;
        double[] actualABoundaryH = propDataOut.getPropagationPaths().get(0).double_aBoundaryH;
        double[] actualABoundaryF = propDataOut.getPropagationPaths().get(0).double_aBoundaryF;
        double[] actualLH = addArray(propDataOut.getPropagationPaths().get(0).aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(propDataOut.getPropagationPaths().get(0).aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(propDataOut.getPropagationPaths().get(0).aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);
        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

    }

    /**
     * Test TC05 -- Reduced receiver height to include diffraction in some frequency bands
     */
    @Test
    public void TC05() throws IOException {

        //Out and computation settings
        Attenuation propDataOut = computeCnossosPath("TC05_Direct");

        //Expected values
        double[][][] pts = new double[][][]{
                {{0.00, 1.00}, {194.16, 14.00}} //Path 1 : direct
        };
        double[][] gPaths = new double[][]{
                {0.51},{0.64}
                //{(0.9*40.88 + 0.5*102.19 + 0.2*51.09)/194.16} //Path 1 : direct
        };
        /* Table 18 */
        double [][] meanPlanes = new double[][]{
                //  a      b    zs    zr      dp    Gp   Gp'
                {0.05, -2.83, 3.83, 6.16, 194.59, 0.51, 0.64}
        };

        //Assertion
        assertPaths(pts, gPaths, propDataOut.getPropagationPaths()); // table17
        assertPlanes(meanPlanes, propDataOut.getPropagationPaths().get(0).getSRSegment()); // table 18
        assertPlanes(meanPlanes, propDataOut.getPropagationPaths().get(0).getSegmentList()); // table 18

        //Expected values
        double[] expectedWH = new double[]{1.6e-04, 8.7e-04, 4.8e-03, 0.03, 0.14, 0.75, 3.70, 16.77};
        double[] expectedCfH = new double[]{203.37, 222.35, 207.73, 82.09, 9.63, 1.33, 0.27, 0.06};
        double[] expectedAGroundH = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.16, 10.35};
        double[] expectedCfF = new double[]{199.73, 214.27, 225.54, 131.93, 22.89, 2.42, 0.46, 0.10};
        double[] expectedAGroundF = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.38, 22.75};
        double[] expectedADiv = new double[]{56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78};
        double[] expectedABoundaryH = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};
        double[] expectedABoundaryF = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};
        double[] expectedLH = new double[]{37.26, 37.21, 37.08, 36.91, 36.57, 35.41, 30.91, 14.54};
        double[] expectedLF = new double[]{37.26, 37.21, 37.08, 36.91, 36.57, 35.41, 30.91, 14.54};
        double[] expectedL = new double[]{37.26, 37.21, 37.08, 36.91, 36.57, 35.41, 30.91, 14.54};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);
        //Actual values
        double[] actualWH = propDataOut.getPropagationPaths().get(0).groundAttenuation.wH;
        double[] actualCfH = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfH;
        double[] actualAGroundH = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundH;
        double[] actualWF = propDataOut.getPropagationPaths().get(0).groundAttenuation.wF;
        double[] actualCfF = propDataOut.getPropagationPaths().get(0).groundAttenuation.cfF;
        double[] actualAGroundF = propDataOut.getPropagationPaths().get(0).groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = propDataOut.getPropagationPaths().get(0).aAtm;
        double[] actualADiv = propDataOut.getPropagationPaths().get(0).aDiv;
        double[] actualABoundaryH = propDataOut.getPropagationPaths().get(0).double_aBoundaryH;
        double[] actualABoundaryF = propDataOut.getPropagationPaths().get(0).double_aBoundaryF;
        double[] actualLH = addArray(propDataOut.getPropagationPaths().get(0).aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(propDataOut.getPropagationPaths().get(0).aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(propDataOut.getPropagationPaths().get(0).aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);
        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryF", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH", expectedLH, actualLH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LF", expectedLF, actualLF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("L", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        double[] diffL = diffArray(expectedL, actualL);
        double[] diffLa = diffArray(expectedLA, actualLA);
        double[] valL = getMaxValeurAbsolue(diffL);
        double[] valLA = getMaxValeurAbsolue(diffLa);

    }

    /**
     * Test TC06 -- Reduced receiver height to include diffraction in some frequency bands
     */
    @Test
    public void TC06() throws IOException {

        //Out and computation settings
        Attenuation propDataOut = computeCnossosPath("TC06_Direct");

        assertEquals(1, propDataOut.getPropagationPaths().size());
        assertEquals(2, propDataOut.getPropagationPaths().get(0).getSegmentList().size());

        // Test R-CRIT table 27
        Coordinate D = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).r;
        Coordinate Sp = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).sPrime;
        Coordinate Rp = propDataOut.getPropagationPaths().get(0).getSegmentList().get(1).rPrime ;

        double deltaD = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).d
                + propDataOut.getPropagationPaths().get(0).getSegmentList().get(1).d -
                propDataOut.getPropagationPaths().get(0).getSRSegment().d;
        double deltaDE = Sp.distance(D) + D.distance(Rp) - Sp.distance(Rp);
        List<Integer> res1 = new ArrayList<>(3) ;
        List<Integer> res2 = new ArrayList<>(3);

        for(int f : propDataOut.inputData.freq_lvl) {
            if(deltaD > -(340./f) / 20) {
                res1.add(1);
            }
            if (!(deltaD > (((340./f) / 4) - deltaDE))){
                res2.add(0);
            }
        }
        //computeRays.
        //Expected values
        double[][][] pts = new double[][][]{
                {{0.00, 1.00}, {178.84, 10.0}, {194.16, 11.5}} //Path 1 : direct
        };

        /* Table 23 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(112.41, 0.00));
        expectedZProfile.add(new Coordinate(178.84, 10.00));
        expectedZProfile.add(new Coordinate(194.16, 10.00));

        /* Table 25 */
        Coordinate expectedSPrime =new Coordinate(0.31,-5.65);
        Coordinate expectedRPrime =new Coordinate(194.16,8.5);

        assertMirrorPoint(expectedSPrime,expectedRPrime,
                propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).sPrime,
                propDataOut.getPropagationPaths().get(0).getSegmentList().get(1).rPrime);


        /* Table 24 */
        double [][] srMeanPlanes = new double[][]{
                //  a      b    zs    zr      dp    Gp   Gp'
                {0.05, -2.83, 3.83, 3.66, 194.45, 0.51, 0.56}
        };
        double [][] segmentsMeanPlanes = new double[][]{
                //  a      b    zs    zr      dp    Gp   Gp'
                {0.05, -2.33, 3.33, 3.95, 179.06, 0.53, 0.60},
                {0.00, 10.00, 0.00, 1.50, 015.33, 0.20,  NaN}
        };

        //Assertion
        assertZProfil(expectedZProfile, propDataOut.getPropagationPaths().get(0).getCutProfile().computePts2DGround());
        assertPlanes(srMeanPlanes, propDataOut.getPropagationPaths().get(0).getSRSegment());
        assertPlanes(segmentsMeanPlanes, propDataOut.getPropagationPaths().get(0).getSegmentList());

        //Expected values
        double[] expectedDeltaDiffSR = new double[]{0., 0., 0., 3.16, 0.56, 0., 0., 0.};
        double[] expectedAGroundSO = new double[]{0., 0., 0., 2.74, -1.21, 0., 0., 0.};
        double[] expectedAGroundOR = new double[]{0., 0., 0., -2.40, -2.40, 0., 0., 0.};
        double[] expectedDeltaDiffSPrimeR = new double[]{0., 0., 0., 4.71, 4.65, 0., 0., 0.};
        double[] expectedDeltaDiffSRPrime = new double[]{0., 0., 0., 10.83, 13.26, 0., 0., 0.};
        double[] expectedDeltaGroundSO = new double[]{0., 0., 0., 2.23, -0.77, 0., 0., 0.};
        double[] expectedDeltaGroundOR = new double[]{0., 0., 0., -1.07, -0.62, 0., 0., 0.};
        double[] expectedADiff = new double[]{0., 0., 0., 4.31, -0.83, 0., 0., 0.};

        double[] expectedWH = new double[]{1.1e-04, 6.0e-04, 3.4e-03, Double.NaN, Double.NaN, 0.53, 2.70, 12.70};
        double[] expectedCfH = new double[]{200.89, 217.45, 220.41, Double.NaN, Double.NaN, 1.88, 0.37, 0.08};
        double[] expectedAGroundH = new double[]{-1.32, -1.32, -1.32, Double.NaN, -Double.NaN, -1.32, -1.32, -1.32};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.16, 10.35};
        double[] expectedCfF = new double[]{199.59, 214.11, 225.39, 131.90, 22.89, 2.42, 0.46, 0.10};
        double[] expectedAGroundF = new double[]{-1.32, -1.32, -1.29, -1.05, -1.32, -1.32, -1.32, -1.32};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.37, 22.73};
        double[] expectedADiv = new double[]{56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78};
        double[] expectedABoundaryH = new double[]{-1.32, -1.32, -1.32, 4.31, -0.83, -1.32, -1.32, -1.32};
        double[] expectedABoundaryF = new double[]{-1.32, -1.32, -1.29, -1.05, -1.32, -1.32, -1.32, -1.32};
        double[] expectedLH = new double[]{37.53, 37.47, 37.35, 31.54, 36.34, 35.67, 31.18, 14.82};
        double[] expectedLF = new double[]{37.53, 37.47, 37.31, 36.89, 36.84, 35.67, 31.18, 14.82};
        double[] expectedL = new double[]{37.53, 37.47, 37.33, 34.99, 36.60, 35.67, 31.18, 14.82};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);
        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSR = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSO = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundOR = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeR = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrime = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSO = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundOR = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiff = proPath.aBoundaryH.aDiff;

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);
        //Assertions

        assertEquals(2, proPath.getSegmentList().size());

        // Segment S-O
        SegmentPath SO = proPath.getSegmentList().get(0);
        assertEquals(0.05, SO.a, ERROR_EPSILON_LOWEST);
        assertEquals(-2.33, SO.b, ERROR_EPSILON_LOWEST);
        assertEquals(0.31, SO.sPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-5.65, SO.sPrime.y, ERROR_EPSILON_LOWEST);
        assertEquals(178.84, SO.r.x, ERROR_EPSILON_LOWEST);
        assertEquals(10, SO.r.y, ERROR_EPSILON_LOWEST);

        // Segment 0-R
        SegmentPath OR = proPath.getSegmentList().get(1);
        assertEquals(0.0, OR.a, ERROR_EPSILON_LOWEST);
        assertEquals(10.00, OR.b, ERROR_EPSILON_LOWEST);
        assertEquals(178.84, OR.s.x, ERROR_EPSILON_LOWEST);
        assertEquals(10, OR.s.y, ERROR_EPSILON_LOWEST);
        assertEquals(194.16, OR.rPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(8.50, OR.rPrime.y, ERROR_EPSILON_LOWEST);

        // Segment S-R
        SegmentPath SR = proPath.getSRSegment();
        assertEquals(0.05, SR.a, ERROR_EPSILON_LOWEST);
        assertEquals(-2.83, SR.b, ERROR_EPSILON_LOWEST);


        assertDoubleArrayEquals("DeltaDiffSR", expectedDeltaDiffSR, actualDeltaDiffSR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSO", expectedAGroundSO, actualAGroundSO, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundOR", expectedAGroundOR, actualAGroundOR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeR", expectedDeltaDiffSPrimeR, actualDeltaDiffSPrimeR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrime", expectedDeltaDiffSRPrime, actualDeltaDiffSRPrime, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSO", expectedDeltaGroundSO, actualDeltaGroundSO, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundOR", expectedDeltaGroundOR, actualDeltaGroundOR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiff", expectedADiff, actualADiff, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

    }

    /**
     * Test TC07 -- Flat ground with spatially varying acoustic properties and long barrier
     */
    @Test
    public void TC07() throws IOException {

        //Out and computation settings
        Attenuation propDataOut = computeCnossosPath("TC07_Direct");

        //Expected values

        /* Table 33 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(170.23, 0.00));
        expectedZProfile.add(new Coordinate(170.23, 6.00));
        expectedZProfile.add(new Coordinate(170.23, 0.00));
        expectedZProfile.add(new Coordinate(194.16, 0.00));

        assertZProfil(expectedZProfile,
                Arrays.asList(propDataOut.getPropagationPaths().get(0).getSRSegment().getPoints2DGround()));

        /* Table 34 */
        Coordinate expectedSPrime =new Coordinate(0.00,-1.00);
        Coordinate expectedRPrime =new Coordinate(194.16,-4.00);

        assertMirrorPoint(expectedSPrime,
                expectedRPrime,propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).sPrime,
                propDataOut.getPropagationPaths().get(0).getSegmentList().
                        get(propDataOut.getPropagationPaths().get(0).getSegmentList().size()-1).rPrime);



        double[][] gPaths = new double[][]{
                {0.55, 0.20},{0.61,  NaN} //Path 1 : direct
        };

        /* Table 35 */
        double [][] segmentsMeanPlanes = new double[][]{
                //  a     b    zs    zr      dp    Gp   Gp'
                {0.00, 0.00, 1.00, 6.00, 170.23, 0.55, 0.61},
                {0.00, 0.00, 6.00, 4.00, 023.93, 0.20,  NaN}
        };


        //Assertion
        assertPlanes(segmentsMeanPlanes, propDataOut.getPropagationPaths().get(0).getSegmentList());

        //Expected values
        double[] expectedDeltaDiffSRH = new double[]{6.01, 6.96, 8.41, 10.36, 12.72, 15.37, 18.19, 21.10};
        double[] expectedAGroundSOH = new double[]{-1.16, -1.16, -1.16, -1.16, 1.45, -1.16, -1.16, -1.16};
        double[] expectedAGroundORH = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRH = new double[]{6.24, 7.32, 8.92, 11.00, 13.46, 16.16, 19.01, 21.94};
        double[] expectedDeltaDiffSRPrimeH = new double[]{12.54, 15.13, 17.94, 20.85, 23.80, 26.78, 29.78, 32.78};
        double[] expectedDeltaGroundSOH = new double[]{-1.13, -1.11, -1.09, -1.08, 1.32, -1.06, -1.06, -1.06};
        double[] expectedDeltaGroundORH = new double[]{-1.22, -1.02, -0.88, -0.79, -0.74, -0.71, -0.70, -0.69};
        double[] expectedADiffH = new double[]{3.67, 4.83, 6.44, 8.49, 13.30, 13.60, 16.43, 19.35};

        double[] expectedDeltaDiffSRF = new double[]{5.67, 6.40, 7.58, 9.27, 11.43, 13.94, 16.68, 19.55};
        double[] expectedAGroundSOF = new double[]{-1.16, -1.16, -1.16, -1.16, -1.16, -1.16, -1.16, -1.16};
        double[] expectedAGroundORF = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRF = new double[]{5.91, 6.81, 8.19, 10.07, 12.39, 15.01, 17.81, 20.71};
        double[] expectedDeltaDiffSRPrimeF = new double[]{12.46, 15.05, 17.86, 20.76, 23.71, 26.70, 29.69, 32.70};
        double[] expectedDeltaGroundSOF = new double[]{-1.12, -1.11, -1.08, -1.06, -1.04, -1.03, -1.02, -1.02};
        double[] expectedDeltaGroundORF = new double[]{-1.18, -0.96, -0.81, -0.71, -0.65, -0.61, -0.60, -0.59};
        double[] expectedADiffF = new double[]{3.36, 4.33, 5.69, 7.50, 9.74, 12.30, 15.06, 17.94};

        double[] expectedWH = new double[]{1.1e-04, 6.0e-04, 3.4e-03, Double.NaN, Double.NaN, 0.53, 2.70, 12.70};
        double[] expectedCfH = new double[]{200.89, 217.45, 220.41, Double.NaN, Double.NaN, 1.88, 0.37, 0.08};
        double[] expectedAGroundH = new double[]{-1.32, -1.32, -1.32, Double.NaN, -Double.NaN, -1.32, -1.32, -1.32};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.16, 10.35};
        double[] expectedCfF = new double[]{199.59, 214.11, 225.39, 131.90, 22.89, 2.42, 0.46, 0.10};
        double[] expectedAGroundF = new double[]{-1.32, -1.32, -1.29, -1.05, -1.32, -1.32, -1.32, -1.32};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.36, 22.70};
        double[] expectedADiv = new double[]{56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78};
        double[] expectedABoundaryH = new double[]{3.67, 4.83, 6.44, 8.49, 13.30, 13.60, 16.43, 19.35};
        double[] expectedABoundaryF = new double[]{3.36, 4.33, 5.69, 7.50, 9.74, 12.30, 15.06, 17.94};
        double[] expectedLH = new double[]{32.54, 31.32, 29.60, 27.37, 22.22, 20.76, 13.44, -5.81};
        double[] expectedLF = new double[]{32.85, 31.83, 30.35, 28.36, 25.78, 22.06, 14.81, -4.41};
        double[] expectedL = new double[]{32.70, 31.58, 29.99, 27.89, 24.36, 21.46, 14.18, -5.05};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);

        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        //Disabled because only diffraction
        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);
        //Assertions
        assertEquals(0.00, proPath.getSegmentList().get(0).sPrime.x, ERROR_EPSILON_LOW);
        assertEquals(-1.00, proPath.getSegmentList().get(0).sPrime.y, ERROR_EPSILON_LOW);
        assertEquals(194.16, proPath.getSegmentList().get(1).rPrime.x, ERROR_EPSILON_LOW);
        assertEquals(-4.00, proPath.getSegmentList().get(1).rPrime.y, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("DeltaDiffSRH", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiffH", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOF", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORF", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOF", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORF", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiffF", expectedADiffF, actualADiffF, ERROR_EPSILON_VERY_LOW);

        //Disabled because only diffraction
        /*assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);*/

        assertDoubleArrayEquals("AlphaAtm", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF", expectedLF, actualLF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("L", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        double[] diffL = diffArray(expectedL, actualL);
        double[] diffLa = diffArray(expectedLA, actualLA);

    }

    /**
     * Test TC08 -- Flat ground with spatially varying acoustic properties and short barrier
     */
    @Test
    public void TC08() throws IOException {

        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC08_Direct", "TC08_Right", "TC08_Left");

        //Expected values

        /*Table 41 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(170.49, 0.00));
        expectedZProfile.add(new Coordinate(170.49, 6.00));
        expectedZProfile.add(new Coordinate(170.49, 0.00));
        expectedZProfile.add(new Coordinate(194.16, 0.00));

        /* Table 42 */
        Coordinate expectedSPrime =new Coordinate(0.00,-1.00);
        Coordinate expectedRPrime =new Coordinate(194.16,-4.00);

        assertEquals(3, propDataOut.getPropagationPaths().size());


        assertMirrorPoint(expectedSPrime,expectedRPrime,
                propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).sPrime,
                propDataOut.getPropagationPaths().get(0).getSegmentList().
                        get(propDataOut.getPropagationPaths().get(0).getSegmentList().size()-1).rPrime);

        /* Table 43 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b    zs    zr      dp    Gp   Gp'
                {0.00, 0.00, 1.00, 6.00, 170.49, 0.55, 0.61},
                {0.00, 0.00, 6.00, 4.00, 023.68, 0.20,  NaN}
        };
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a     b    zs    zr      dp    Gp   Gp'
                {0.00, 0.00, 1.00, 4.00, 221.23, 0.46, 0.46}
        };
        double [][] segmentsMeanPlanes2 = new double[][]{
                //  a     b    zs    zr      dp    Gp   Gp'
                {0.00, 0.00, 1.00, 4.00, 194.78, 0.51, 0.51}
        };

        //Assertion

        assertZProfil(expectedZProfile, propDataOut.getPropagationPaths().get(0).getCutProfile().computePts2DGround());
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());
        assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment());
        assertPlanes(segmentsMeanPlanes2, propDataOut.getPropagationPaths().get(2).getSRSegment());

        assertEquals(3, propDataOut.getPropagationPaths().size());

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{6.02, 6.97, 8.42, 10.38, 12.75, 15.40, 18.21, 21.12};
        double[] expectedAGroundSOH = new double[]{-1.16, -1.16, -1.16, -1.16, 1.46, -1.16, -1.16, -1.16};
        double[] expectedAGroundORH = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRH = new double[]{6.25, 7.33, 8.93, 11.01, 13.47, 16.18, 19.03, 21.96};
        double[] expectedDeltaDiffSRPrimeH = new double[]{12.57, 15.17, 17.98, 20.89, 23.84, 26.83, 29.82, 32.83};
        double[] expectedDeltaGroundSOH = new double[]{-1.13, -1.11, -1.10, -1.08, 1.33, -1.06, -1.06, -1.06};
        double[] expectedDeltaGroundORH = new double[]{-1.21, -1.01, -0.87, -0.79, -0.74, -0.71, -0.70, -0.69};
        double[] expectedADiffH = new double[]{3.68, 4.84, 6.45, 8.51, 13.34, 13.62, 16.46, 19.38};

        double[] expectedDeltaDiffSRF = new double[]{5.68, 6.41, 7.60, 9.30, 11.47, 13.99, 16.73, 19.60};
        double[] expectedAGroundSOF = new double[]{-1.16, -1.16, -1.16, -1.16, -1.16, -1.16, -1.16, -1.16};
        double[] expectedAGroundORF = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRF = new double[]{5.92, 6.82, 8.21, 10.10, 12.42, 15.04, 17.84, 20.75};
        double[] expectedDeltaDiffSRPrimeF = new double[]{12.50, 15.09, 17.90, 20.80, 23.76, 26.74, 29.74, 32.74};
        double[] expectedDeltaGroundSOF = new double[]{-1.12, -1.11, -1.09, -1.06, -1.05, -1.03, -1.03, -1.02};
        double[] expectedDeltaGroundORF = new double[]{-1.18, -0.96, -0.81, -0.71, -0.65, -0.61, -0.60, -0.59};
        double[] expectedADiffF = new double[]{3.37, 4.34, 5.71, 7.53, 9.78, 12.34, 15.11, 17.99};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.36, 22.70};
        double[] expectedADiv = new double[]{56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76};
        double[] expectedABoundaryH = new double[]{3.68, 4.84, 6.45, 8.51, 13.34, 13.62, 16.43, 19.38};
        double[] expectedABoundaryF = new double[]{3.37, 4.34, 5.71, 7.53, 9.78, 12.34, 15.11, 17.99};
        double[] expectedLH = new double[]{32.54, 31.31, 29.58, 27.35, 22.19, 20.74, 13.42, -5.84};
        double[] expectedLF = new double[]{32.84, 31.81, 30.32, 28.33, 25.74, 22.02, 14.76, -4.45};
        double[] expectedL = new double[]{32.69, 31.57, 29.97, 27.87, 24.32, 21.42, 14.14, -5.09};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);
        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);
        //Assertions
        assertEquals(0.13, proPath.deltaH, ERROR_EPSILON_LOWEST);
        assertEquals(0.00, proPath.getSegmentList().get(0).sPrime.x, ERROR_EPSILON_LOW);
        assertEquals(-1.00, proPath.getSegmentList().get(0).sPrime.y, ERROR_EPSILON_LOW);
        assertEquals(194.16, proPath.getSegmentList().get(1).rPrime.x, ERROR_EPSILON_LOW);
        assertEquals(-4.00, proPath.getSegmentList().get(1).rPrime.y, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : right lateral
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.06, 0.34, 1.76, 8.58};
        double[] expectedCfH = new double[]{226.58, 242.17, 257.73, 159.33, 29.64, 3.03, 0.57, 0.12};
        double[] expectedAGroundH = new double[]{-1.61, -1.61, -1.61, 0.75, 6.25, -0.39, -1.61, -1.61};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.06, 0.34, 1.76, 8.58};
        double[] expectedCfF = new double[]{226.58, 242.17, 257.73, 159.33, 29.64, 3.03, 0.57, 0.12};
        double[] expectedAGroundF = new double[]{-2.65, -2.65, -2.65, -2.65, -1.30, -2.65, -2.65, -2.65};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.03, 0.09, 0.23, 0.43, 0.81, 2.14, 7.25, 25.86};
        expectedADiv = new double[]{56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76};
        expectedDeltaDiffSRH = new double[]{23.09, 26.03, 29.03, 32.03, 35.03, 38.04, 41.05, 44.06};
        expectedDeltaDiffSRF = new double[]{23.09, 26.03, 29.03, 32.03, 35.03, 38.04, 41.05, 44.06};
        expectedLH = new double[]{14.73, 11.73, 8.59, 3.03, -5.86, -3.56, -10.45, -32.07};
        expectedLF = new double[]{15.77, 12.77, 9.63, 6.43, 1.69, -1.29, -9.41, -31.03};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(1);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        //Assertions
        assertEquals(27.07, proPath.deltaH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRF - right lateral", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - right lateral", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        //Path2 : left lateral
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.17, 10.40};
        expectedCfH = new double[]{199.96, 214.57, 225.67, 131.50, 22.70, 2.41, 0.46, 0.10};
        expectedAGroundH = new double[]{-1.48, -1.48, -1.48, 1.01, 5.84, -1.48, -1.48, -1.48};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.17, 10.40};
        expectedCfF = new double[]{199.96, 214.57, 225.67, 131.50, 22.70, 2.41, 0.46, 0.10};
        expectedAGroundF = new double[]{-2.16, -2.16, -2.16, -2.16, -0.92, -2.16, -2.16, -2.16};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.38, 0.71, 1.88, 6.38, 22.77};
        expectedADiv = new double[]{56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76, 56.76};
        expectedDeltaDiffSRH = new double[]{8.78, 10.81, 13.24, 15.93, 18.77, 21.69, 24.66, 27.64};
        expectedDeltaDiffSRF = new double[]{8.78, 10.81, 13.24, 15.93, 18.77, 21.69, 24.66, 27.64};
        expectedLH = new double[]{28.91, 26.83, 24.28, 18.92, 10.92, 14.14, 6.68, -12.70};
        expectedLF = new double[]{29.59, 27.51, 24.96, 22.09, 17.68, 14.82, 7.36, -12.02};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        //Assertions
        assertEquals(0.61, proPath.deltaH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - left lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - left lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - left lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - left lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - left lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRH - left lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRF - left lateral", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - left lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - left lateral", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});
        assertArrayEquals(  new double[]{8.17,16.86,22.51,25.46,24.87,23.44,15.93,-5.43},L, ERROR_EPSILON_VERY_LOW);
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

    /**
     * Test TC09 -- Ground with spatially varying heights and and acoustic properties and short barrier
     */
    @Test
    public void TC09() throws IOException {
        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC09_Direct", "TC09_Right", "TC09_Left");

        assertEquals(3, propDataOut.getPropagationPaths().size());

        //Expected values

        /* Table 59 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(112.41, 0.00));
        expectedZProfile.add(new Coordinate(170.49, 8.74));
        expectedZProfile.add(new Coordinate(170.49, 16.63));
        expectedZProfile.add(new Coordinate(170.49, 8.74));
        expectedZProfile.add(new Coordinate(178.84, 10.00));
        expectedZProfile.add(new Coordinate(194.16, 10.00));

        /* Table 61 */
        Coordinate expectedSPrime =new Coordinate(0.24,-4.92);
        Coordinate expectedRPrime =new Coordinate(194.48,6.59);

        assertMirrorPoint(expectedSPrime,expectedRPrime,propDataOut.getPropagationPaths().get(0).
                getSegmentList().get(0).sPrime,propDataOut.getPropagationPaths().get(0).getSegmentList().
                get(propDataOut.getPropagationPaths().get(0).getSegmentList().size()-1).rPrime);


        /* Table 60 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b    zs    zr      dp    Gp   Gp'
                {0.04, -1.96, 2.96, 11.68, 170.98, 0.55, 0.76},
                {0.04, 1.94, 7.36, 3.71, 23.54, 0.20,  0.20}
        };
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a     b    zs    zr      dp    Gp   Gp'
                {0.06, -3.10, 4.09, 3.77, 221.62, 0.46, 0.49}
        };
        double [][] segmentsMeanPlanes2 = new double[][]{
                //  a     b    zs    zr      dp    Gp   Gp'
                {0.05, -2.82, 3.81, 6.23, 195.20, 0.51, 0.64}
        };

        //Assertion
        assertZProfil(expectedZProfile, Arrays.asList(propDataOut.getPropagationPaths().get(0).getSRSegment()
                .getPoints2DGround()));
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());
        assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment());
        assertPlanes(segmentsMeanPlanes2, propDataOut.getPropagationPaths().get(2).getSRSegment());

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{7.90, 9.67, 11.92, 14.49, 17.26, 20.15, 23.09, 26.07};
        double[] expectedAGroundSOH = new double[]{-0.71, -0.71, -0.71, -0.71, -0.71, -0.71, -0.71, -0.71};
        double[] expectedAGroundORH = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRH = new double[]{8.65, 10.64, 13.05, 15.72, 18.56, 21.48, 24.44, 27.43};
        double[] expectedDeltaDiffSRPrimeH = new double[]{13.55, 16.23, 19.09, 22.01, 24.98, 27.97, 30.97, 33.98};
        double[] expectedDeltaGroundSOH = new double[]{-0.65, -0.64, -0.63, -0.62, -0.62, -0.61, -0.61, -0.61};
        double[] expectedDeltaGroundORH = new double[]{-1.33, -1.21, -1.13, -1.09, -1.07, -1.06, -1.05, -1.05};
        double[] expectedADiffH = new double[]{5.91, 7.82, 10.16, 12.78, 15.58, 18.48, 21.43, 23.34};

        double[] expectedDeltaDiffSRF = new double[]{7.68, 9.39, 11.57, 14.10, 16.85, 19.73, 22.67, 25.64};
        double[] expectedAGroundSOF = new double[]{-0.71, -0.71, -0.71, -0.71, -0.71, -0.71, -0.71, -0.71};
        double[] expectedAGroundORF = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRF = new double[]{8.47, 10.41, 12.79, 15.44, 18.26, 21.17, 24.13, 27.12};
        double[] expectedDeltaDiffSRPrimeF = new double[]{13.50, 16.17, 19.02, 21.95, 24.92, 27.91, 30.91, 33.91};
        double[] expectedDeltaGroundSOF = new double[]{-0.65, -0.63, -0.62, -0.61, -0.61, -0.61, -0.60, -0.60};
        double[] expectedDeltaGroundORF = new double[]{-1.31, -1.18, -1.10, -1.05, -1.03, -1.02, -1.01, -1.01};
        double[] expectedADiffF = new double[]{5.72, 7.57, 9.85, 12.44, 15.22, 18.11, 21.05, 23.39};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.38, 22.75};
        double[] expectedADiv = new double[]{56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78};
        double[] expectedABoundaryH = new double[]{5.91, 7.82, 10.16, 12.78, 15.58, 18.48, 21.43, 23.34};
        double[] expectedABoundaryF = new double[]{5.72, 7.57, 9.85, 12.44, 15.22, 18.11, 21.05, 23.39};
        double[] expectedLH = new double[]{30.28, 28.31, 25.86, 23.07, 19.93, 15.86, 8.41, -9.87};
        double[] expectedLF = new double[]{30.47, 28.57, 26.16, 23.40, 20.29, 16.23, 8.79, -9.92};
        double[] expectedL = new double[]{30.38, 28.44, 26.01, 23.24, 20.11, 16.05, 8.60, -9.89};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);
        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);

        //Assertions
        assertEquals(0.24, proPath.getSegmentList().get(0).sPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-4.92, proPath.getSegmentList().get(0).sPrime.y, ERROR_EPSILON_LOWEST);
        assertEquals(194.48, proPath.getSegmentList().get(1).rPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(6.59, proPath.getSegmentList().get(1).rPrime.y, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        double[] diffL = diffArray(expectedL, actualL);
        double[] diffLa = diffArray(expectedLA, actualLA);
        double[] valL = getMaxValeurAbsolue(diffL);
        double[] valLA = getMaxValeurAbsolue(diffLa);

        //Path1 : right lateral Table 67
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.07, 0.39, 2.00, 9.66};
        double[] expectedCfH = new double[]{227.72, 244.70, 256.12, 145.81, 24.37, 2.61, 0.50, 0.10};
        double[] expectedAGroundH = new double[]{-1.53, -1.53, -1.53, 2.33, -1.53, -1.53, -1.53, -1.53};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.06, 0.34, 1.76, 8.58};
        double[] expectedCfF = new double[]{226.99, 242.62, 258.17, 159.45, 29.63, 3.03, 0.57, 0.12};
        double[] expectedAGroundF = new double[]{-1.53, -1.38, -1.35, -1.53, -1.53, -1.53, -1.53, -1.53};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.03, 0.09, 0.23, 0.43, 0.81, 2.14, 7.26, 25.91};
        expectedADiv = new double[]{56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78};
        expectedDeltaDiffSRH = new double[]{23.08, 26.03, 29.02, 32.02, 35.03, 38.04, 41.05, 44.06};
        expectedDeltaDiffSRF = new double[]{23.08, 26.03, 29.02, 32.02, 35.03, 38.04, 41.05, 44.06};
        expectedLH = new double[]{14.64, 11.63, 8.50, 1.44, 1.91, -2.43, -10.56, -32.21};
        expectedLF = new double[]{14.64, 11.48, 8.31, 5.30, 1.91, -2.43, -10.56, -32.21};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(1);


        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRF - right lateral", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - right lateral", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        //Path2 : left lateral
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.03, 0.14, 0.75, 3.72, 16.84};
        expectedCfH = new double[]{204.07, 223.16, 208.01, 81.73, 9.55, 1.33, 0.27, 0.06};
        expectedAGroundH = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.17, 10.40};
        expectedCfF = new double[]{200.40, 215.06, 226.13, 131.61, 22.68, 2.41, 0.46, 0.10};
        expectedAGroundF = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.38, 0.71, 1.89, 6.40, 22.82};
        expectedADiv = new double[]{56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78};
        expectedDeltaDiffSRH = new double[]{8.79, 10.81, 13.24, 15.93, 18.77, 21.70, 24.66, 27.65};
        expectedDeltaDiffSRF = new double[]{8.79, 10.81, 13.24, 15.93, 18.78, 21.70, 24.66, 27.65};
        expectedLH = new double[]{28.47, 26.39, 23.84, 20.97, 17.79, 13.70, 6.22, -13.19};
        expectedLF = new double[]{28.47, 26.39, 23.84, 20.97, 17.79, 13.70, 6.22, -13.19};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - left lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - left lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - left lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - left lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - left lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRH - left lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRF - left lateral", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - left lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - left lateral", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});

        assertArrayEquals(  new double[]{6.41,14.50,19.52,22.09,22.16,19.28,11.62,-9.31},L, ERROR_EPSILON_VERY_LOW);
    }

    /**
     * Test TC10 -- Flat ground with homogeneous acoustic properties and cubic building – receiver at low height
     */
    @Test
    public void TC10() throws IOException {
        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC10_Direct", "TC10_Right", "TC10_Left");

        //Expected values

        /* Table 74 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(5, 0.00));
        expectedZProfile.add(new Coordinate(5, 10.00));
        expectedZProfile.add(new Coordinate(15, 10));
        expectedZProfile.add(new Coordinate(15, 0));
        expectedZProfile.add(new Coordinate(20, 0));

        /* Table 75 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b    zs     zr    dp    Gp   Gp'
                {0.00, 0.00, 1.00, 10.00, 5.00, 0.50, 0.50},
                {0.00, 0.00, 10.00, 4.00, 5.00, 0.50,  NaN}
        };
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a     b    zs    zr     dp    Gp   Gp'
                {0.00, 0.00, 1.00, 4.00, 24.15, 0.50, 0.50}
        };
        double [][] segmentsMeanPlanes2 = new double[][]{
                //  a     b    zs    zr     dp    Gp   Gp'
                {0.00, 0.00, 1.00, 4.00, 24.15, 0.50, 0.50}
        };



        //Assertion

        assertZProfil(expectedZProfile, Arrays.asList(propDataOut.getPropagationPaths().get(0).getSRSegment()
                .getPoints2DGround()));
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());
        assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment());
        assertPlanes(segmentsMeanPlanes2, propDataOut.getPropagationPaths().get(2).getSRSegment());

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{18.23, 21.88, 26.33, 30.63, 34.21, 37.39, 40.45, 43.47};
        double[] expectedAGroundSOH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedAGroundORH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedDeltaDiffSPrimeRH = new double[]{18.91, 22.58, 27.03, 31.33, 34.92, 38.10, 41.16, 44.18};
        double[] expectedDeltaDiffSRPrimeH = new double[]{20.80, 24.51, 28.97, 33.28, 36.87, 40.05, 43.11, 46.13};
        double[] expectedDeltaGroundSOH = new double[]{-1.40, -1.39, -1.39, -1.39, -1.39, -1.39, -1.39, -1.39};
        double[] expectedDeltaGroundORH = new double[]{-1.14, -1.13, -1.13, -1.13, -1.13, -1.13, -1.13, -1.13};
        double[] expectedADiffH = new double[]{15.69, 19.36, 22.48, 22.48, 22.48, 22.48, 22.48, 22.48};

        double[] expectedDeltaDiffSRF = new double[]{18.23, 21.88, 26.33, 30.63, 34.21, 37.39, 40.45, 43.47};
        double[] expectedAGroundSOF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedAGroundORF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedDeltaDiffSPrimeRF = new double[]{18.91, 22.58, 27.03, 31.33, 34.92, 38.10, 41.16, 44.18};
        double[] expectedDeltaDiffSRPrimeF = new double[]{20.80, 24.51, 28.97, 33.28, 36.87, 40.05, 43.11, 46.13};
        double[] expectedDeltaGroundSOF = new double[]{-1.40, -1.39, -1.39, -1.39, -1.39, -1.39, -1.39, -1.39};
        double[] expectedDeltaGroundORF = new double[]{-1.14, -1.13, -1.13, -1.13, -1.13, -1.13, -1.13, -1.13};
        double[] expectedADiffF = new double[]{15.69, 19.36, 22.48, 22.48, 22.48, 22.48, 22.48, 22.48};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.00, 0.01, 0.02, 0.04, 0.07, 0.20, 0.66, 2.36};
        double[] expectedADiv = new double[]{37.12, 37.12, 37.12, 37.12, 37.12, 37.12, 37.12, 37.12};
        double[] expectedABoundaryH = new double[]{15.69, 19.36, 22.48, 22.48, 22.48, 22.48, 22.48, 22.48};
        double[] expectedABoundaryF = new double[]{15.69, 19.36, 22.48, 22.48, 22.48, 22.48, 22.48, 22.48};
        double[] expectedLH = new double[]{40.19, 36.52, 33.38, 33.36, 33.33, 33.21, 32.74, 31.04};
        double[] expectedLF = new double[]{40.19, 36.52, 33.38, 33.36, 33.33, 33.21, 32.74, 31.04};
        double[] expectedL = new double[]{40.19, 36.52, 33.38, 33.36, 33.33, 33.21, 32.74, 31.04};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);

        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);

        //Assertions
        assertEquals(0.00, proPath.getSRSegment().sPrime.x, ERROR_EPSILON_MEDIUM);
        assertEquals(-1.00, proPath.getSRSegment().sPrime.y, ERROR_EPSILON_HIGHEST);
        assertEquals(20.00, proPath.getSRSegment().rPrime.x, ERROR_EPSILON_LOW);
        assertEquals(-4.00, proPath.getSRSegment().rPrime.y, ERROR_EPSILON_HIGHEST);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : right lateral
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        double[] expectedCfH = new double[]{24.24, 24.59, 26.01, 28.28, 20.54, 5.05, 0.52, 0.10};
        double[] expectedAGroundH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        double[] expectedCfF = new double[]{24.24, 24.59, 26.01, 28.28, 20.54, 5.05, 0.52, 0.10};
        double[] expectedAGroundF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.00, 0.01, 0.03, 0.05, 0.09, 0.24, 0.80, 2.84};
        expectedADiv = new double[]{37.12, 37.12, 37.12, 37.12, 37.12, 37.12, 37.12, 37.12};
        expectedDeltaDiffSRH = new double[]{15.59, 19.16, 23.56, 27.83, 31.40, 34.58, 37.63, 40.65};
        expectedLH = new double[]{41.79, 38.22, 33.80, 29.51, 25.90, 22.57, 18.96, 13.89};
        expectedLF = new double[]{41.79, 38.22, 33.80, 29.51, 25.90, 22.57, 18.96, 13.89};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(1);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - right lateral", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        //Path2 : left lateral
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        expectedCfH = new double[]{24.24, 24.59, 26.01, 28.28, 20.54, 5.05, 0.52, 0.10};
        expectedAGroundH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        expectedCfF = new double[]{24.24, 24.59, 26.01, 28.28, 20.54, 5.05, 0.52, 0.10};
        expectedAGroundF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};

        expectedLH = new double[]{41.79, 38.22, 33.80, 29.51, 25.90, 22.57, 18.96, 13.89};
        expectedLF = new double[]{41.79, 38.22, 33.80, 29.51, 25.90, 22.57, 18.96, 13.89};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        //Values are different because CNOSSOS doesn't seem to use the rubber band methods.
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - right lateral", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93,93,93,93,93,93,93,93});
        assertArrayEquals(  new double[]{46.09,42.49,38.44,35.97,34.67,33.90,33.09,31.20},L, ERROR_EPSILON_LOWEST);
    }

    private static void exportRays(String path, Attenuation attenuation) throws IOException {
        JsonMapper.Builder builder = JsonMapper.builder();
        JsonMapper mapper = builder.build();
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), attenuation);
    }

    /**
     * Test TC11 -- Flat ground with homogeneous acoustic properties and cubic building – receiver at large height
     */
    @Test
    public void TC11() throws IOException {
        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC11_Direct", "TC11_Right", "TC11_Left");


        /* Table 85 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(5, 0.00));
        expectedZProfile.add(new Coordinate(5, 10.00));
        expectedZProfile.add(new Coordinate(15, 10.00));
        expectedZProfile.add(new Coordinate(15, 0));
        expectedZProfile.add(new Coordinate(20, 0));

        List<Coordinate> expectedZProfileRight = Arrays.asList(
                new Coordinate(0,0),
                new Coordinate(7.07,0),
                new Coordinate(14.93,0),
                new Coordinate(14.94,0),
                new Coordinate(14.94,10),
                new Coordinate(17.55,10),
                new Coordinate(17.55,0),
                new Coordinate(23.65,0)
        );

        List<Coordinate> expectedZProfileLeft = Arrays.asList(
                new Coordinate(0,0),
                new Coordinate(7.07,0),
                new Coordinate(14.93,0),
                new Coordinate(14.94,0),
                new Coordinate(14.94,10),
                new Coordinate(17.55,10),
                new Coordinate(17.55,0),
                new Coordinate(23.65,0)
        );

        /* Table 86 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b    zs     zr    dp    Gp   Gp'
                {0.00, 0.00, 1.00, 10.00, 5.00, 0.50, 0.50},
                {-0.89, 17.78, 2.49, 11.21, 7.89, 0.17,  NaN}
        };
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a      b    zs     zr     dp    Gp    Gp'
                {0.10, -0.13, 1.13, 12.59, 24.98, 0.44, 0.50}
        };
        double [][] segmentsMeanPlanes2 = new double[][]{
                //  a      b    zs     zr     dp    Gp    Gp'
                {0.10, -0.13, 1.13, 12.59, 24.98, 0.44, 0.50}
        };

        //Assertion
        assertZProfil(expectedZProfile, propDataOut.getPropagationPaths().get(0).getCutProfile().computePts2DGround());
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());

        assertZProfil(expectedZProfileRight, propDataOut.getPropagationPaths().get(1).getCutProfile().computePts2DGround());
        assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment());

        assertZProfil(expectedZProfileLeft, propDataOut.getPropagationPaths().get(2).getCutProfile().computePts2DGround());
        assertPlanes(segmentsMeanPlanes2, propDataOut.getPropagationPaths().get(2).getSRSegment());

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{11.92, 14.46, 17.23, 20.11, 23.06, 26.04, 29.03, 32.03};
        double[] expectedAGroundSOH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedAGroundORH = new double[]{-2.50, -2.50, -2.50, -2.50, -2.50, -2.50, -2.50, -2.50};
        double[] expectedDeltaDiffSPrimeRH = new double[]{12.99, 15.63, 18.46, 21.37, 24.34, 27.32, 30.32, 33.33};
        double[] expectedDeltaDiffSRPrimeH = new double[]{20.92, 23.84, 26.82, 29.82, 32.82, 35.83, 38.84, 41.85};
        double[] expectedDeltaGroundSOH = new double[]{-1.34, -1.32, -1.32, -1.31, -1.31, -1.31, -1.31, -1.31};
        double[] expectedDeltaGroundORH = new double[]{-0.97, -0.93, -0.91, -0.90, -0.89, -0.89, -0.89, -0.89};
        double[] expectedADiffH = new double[]{9.61, 12.20, 15.00, 17.90, 20.86, 22.80, 22.80, 22.80};

        double[] expectedDeltaDiffSRF = new double[]{11.92, 14.46, 17.23, 20.11, 23.06, 26.04, 29.03, 32.03};
        double[] expectedAGroundSOF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedAGroundORF = new double[]{-2.50, -2.50, -2.50, -2.50, -2.50, -2.50, -2.50, -2.50};
        double[] expectedDeltaDiffSPrimeRF = new double[]{12.99, 15.63, 18.46, 21.37, 24.34, 27.32, 30.32, 33.33};
        double[] expectedDeltaDiffSRPrimeF = new double[]{20.92, 23.84, 26.82, 29.82, 32.82, 35.83, 38.84, 41.85};
        double[] expectedDeltaGroundSOF = new double[]{-1.34, -1.32, -1.32, -1.31, -1.31, -1.31, -1.31, -1.31};
        double[] expectedDeltaGroundORF = new double[]{-0.97, -0.93, -0.91, -0.90, -0.89, -0.89, -0.89, -0.89};
        double[] expectedADiffF = new double[]{9.61, 12.20, 15.00, 17.90, 20.86, 22.80, 22.80, 22.80};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.00, 0.01, 0.03, 0.05, 0.09, 0.24, 0.80, 2.85};
        double[] expectedADiv = new double[]{38.75, 38.75, 38.75, 38.75, 38.75, 38.75, 38.75, 38.75};
        double[] expectedABoundaryH = new double[]{9.61, 12.20, 15.00, 17.90, 20.86, 22.80, 22.80, 22.80};
        double[] expectedABoundaryF = new double[]{9.61, 12.20, 15.00, 17.90, 20.86, 22.80, 22.80, 22.80};
        double[] expectedLH = new double[]{44.64, 42.04, 39.22, 36.30, 33.30, 31.21, 30.64, 28.59};
        double[] expectedLF = new double[]{44.64, 42.04, 39.22, 36.30, 33.30, 31.21, 30.64, 28.59};
        double[] expectedL = new double[]{44.64, 42.04, 39.22, 36.30, 33.30, 31.21, 30.64, 28.59};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);

        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);

        //Assertions
        assertEquals(0.00, proPath.getSRSegment().sPrime.x, ERROR_EPSILON_HIGH);
        assertEquals(-1.00, proPath.getSRSegment().sPrime.y, ERROR_EPSILON_HIGHEST);
        assertEquals(5.10, proPath.getSRSegment().rPrime.x, ERROR_EPSILON_HIGHEST);
        assertEquals(-1.76, proPath.getSRSegment().rPrime.y, ERROR_EPSILON_HIGHEST);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : right lateral
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.40, 2.07, 9.99};
        double[] expectedCfH = new double[]{25.07, 25.45, 26.93, 29.26, 21.08, 5.11, 0.53, 0.10};
        double[] expectedAGroundH = new double[]{-1.51, -1.51, -1.51, -1.51, -1.51, -1.51, -1.51, -1.51};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.05, 0.31, 1.60, 7.90};
        double[] expectedCfF = new double[]{25.05, 25.34, 26.56, 29.11, 23.63, 7.01, 0.74, 0.13};
        double[] expectedAGroundF = new double[]{-1.51, -1.51, -1.51, -1.51, -1.51, -1.51, -1.51, -1.51};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.00, 0.01, 0.03, 0.05, 0.10, 0.27, 0.90, 3.22};
        expectedADiv = new double[]{38.75, 38.75, 38.75, 38.75, 38.75, 38.75, 38.75, 38.75};
        expectedDeltaDiffSRH = new double[]{14.47, 17.92, 22.26, 26.57, 30.18, 33.37, 36.43, 39.45};
        expectedLH = new double[]{41.28, 37.82, 33.47, 29.14, 25.48, 22.12, 18.43, 13.09};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(1);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);

        //Path2 : left lateral
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.40, 2.07, 9.99};
        expectedCfH = new double[]{25.07, 25.45, 26.93, 29.26, 21.08, 5.11, 0.53, 0.10};
        expectedAGroundH = new double[]{-1.51, -1.51, -1.51, -1.51, -1.51, -1.51, -1.51, -1.51};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.05, 0.31, 1.60, 7.90};
        expectedCfF = new double[]{25.05, 25.34, 26.56, 29.11, 23.63, 7.01, 0.74, 0.13};
        expectedAGroundF = new double[]{-1.51, -1.51, -1.51, -1.51, -1.51, -1.51, -1.51, -1.51};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, sumArray(SOUND_POWER_LEVELS, A_WEIGHTING));
        assertArrayEquals(  new double[]{21.28,28.39,32.47,34.51,34.54,33.37,32.14,27.73},L, ERROR_EPSILON_VERY_LOW);
    }

    /**
     * Test TC12 -- Flat ground with homogeneous acoustic properties and polygonal object – receiver at low height
     */
    @Test
    public void TC12() throws IOException {
        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC12_Direct", "TC12_Right", "TC12_Left");

        /* Table 100 */
        List<Coordinate> expectedZProfile = Arrays.asList(
                new Coordinate(0.00, 0.00),
                new Coordinate(12.26, 0.00),
                new Coordinate(12.26, 10.00),
                new Coordinate(18.82, 10),
                new Coordinate(18.82, 0),
                new Coordinate(31.62, 0));

        List<Coordinate> expectedZProfileSO = Arrays.asList(
                new Coordinate(0.00, 0.00),
                new Coordinate(12.26, 0.00));

        List<Coordinate> expectedZProfileOnR = Arrays.asList(
                new Coordinate(18.82, 0),
                new Coordinate(31.62, 0));

        /* Table 101 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b    zs    zr     dp    Gp   Gp'
                {0.00, 0.00, 1.00, 10.0, 12.26, 0.50, 0.50},
                {0.00, 0.00, 10.0, 6.00, 12.80, 0.50,  NaN}
        };
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a     b    zs    zr     dp    Gp    Gp'
                {0.00, 0.00, 1.00, 6.00, 32.11, 0.50, 0.50}
        };
        double [][] segmentsMeanPlanes2 = new double[][]{
                //  a     b    zs    zr     dp    Gp    Gp'
                {0.00, 0.00, 1.00, 6.00, 32.66, 0.50, 0.50}
        };

        //Assertion
        assertEquals(3, propDataOut.getPropagationPaths().size());

        CnossosPath directPath = propDataOut.getPropagationPaths().get(0);
        assertZProfil(expectedZProfile, Arrays.asList(directPath.getSRSegment().getPoints2DGround()));
        assertZProfil(expectedZProfileSO, Arrays.asList(directPath.getSegmentList().get(0).getPoints2DGround()));
        assertZProfil(expectedZProfileOnR, Arrays.asList(directPath.getSegmentList().
                get(directPath.getSegmentList().size() - 1).getPoints2DGround()));

        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());

        assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment());
        assertPlanes(segmentsMeanPlanes2, propDataOut.getPropagationPaths().get(2).getSRSegment());

        assertEquals(3, propDataOut.getPropagationPaths().get(0).getSegmentList().size());
        Coordinate sPrime = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).sPrime;
        Coordinate rPrime = propDataOut.getPropagationPaths().get(0).getSegmentList().get(2).rPrime;

        assertCoordinateEquals("TC12 Table 102 S' S->O", new Coordinate(0, -1), sPrime, DELTA_COORDS);
        assertCoordinateEquals("TC12 Table 102 R' O->R", new Coordinate(31.62, -6), rPrime, DELTA_COORDS);

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{14.37, 17.50, 21.47, 25.97, 29.98, 33.36, 36.47, 39.50};
        double[] expectedAGroundSOH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedAGroundORH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedDeltaDiffSPrimeRH = new double[]{15.34, 18.53, 22.52, 27.04, 31.05, 34.43, 37.54, 40.58};
        double[] expectedDeltaDiffSRPrimeH = new double[]{18.98, 22.28, 26.34, 30.89, 34.91, 38.29, 41.41, 44.44};
        double[] expectedDeltaGroundSOH = new double[]{-1.35, -1.35, -1.34, -1.34, -1.34, -1.34, -1.34, -1.34};
        double[] expectedDeltaGroundORH = new double[]{-0.91, -0.90, -0.89, -0.88, -0.88, -0.88, -0.88, -0.88};
        double[] expectedADiffH = new double[]{12.10, 15.26, 19.24, 22.78, 22.78, 22.78, 22.78, 22.78};

        double[] expectedDeltaDiffSRF = new double[]{14.37, 17.50, 21.47, 25.97, 29.98, 33.36, 36.47, 39.50};
        double[] expectedAGroundSOF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedAGroundORF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedDeltaDiffSPrimeRF = new double[]{15.34, 18.53, 22.52, 27.04, 31.05, 34.43, 37.54, 40.58};
        double[] expectedDeltaDiffSRPrimeF = new double[]{18.98, 22.28, 26.34, 30.89, 34.91, 38.29, 41.41, 44.44};
        double[] expectedDeltaGroundSOF = new double[]{-1.35, -1.35, -1.34, -1.34, -1.34, -1.34, -1.34, -1.34};
        double[] expectedDeltaGroundORF = new double[]{-0.91, -0.90, -0.89, -0.88, -0.88, -0.88, -0.88, -0.88};
        double[] expectedADiffF = new double[]{12.10, 15.26, 19.24, 22.78, 22.78, 22.78, 22.78, 22.78};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.00, 0.01, 0.03, 0.06, 0.12, 0.31, 1.05, 3.74};
        double[] expectedADiv = new double[]{41.11, 41.11, 41.11, 41.11, 41.11, 41.11, 41.11, 41.11};
        double[] expectedABoundaryH = new double[]{12.10, 15.26, 19.24, 22.78, 22.78, 22.78, 22.78, 22.78};
        double[] expectedABoundaryF = new double[]{12.10, 15.26, 19.24, 22.78, 22.78, 22.78, 22.78, 22.78};
        double[] expectedLH = new double[]{39.78, 36.62, 32.62, 29.05, 29.00, 28.80, 28.06, 25.37};
        double[] expectedLF = new double[]{39.78, 36.62, 32.62, 29.05, 29.00, 28.80, 28.06, 25.37};
        double[] expectedL = new double[]{39.78, 36.62, 32.62, 29.05, 29.00, 28.80, 28.06, 25.37};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);

        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);

        //Assertions
        assertEquals(3, proPath.getSegmentList().size());
        SegmentPath SR = proPath.getSRSegment();
        SegmentPath SO = proPath.getSegmentList().get(0);
        SegmentPath OR = proPath.getSegmentList().get(2);
        // Table 103
        assertEquals(32.02, SR.d, ERROR_EPSILON_LOWEST);
        assertEquals(3.17, proPath.deltaH, ERROR_EPSILON_LOWEST);
        assertEquals(6.55, proPath.e, ERROR_EPSILON_LOW);
        assertEquals(0.00, SO.sPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-1.00, SO.sPrime.y, ERROR_EPSILON_LOWEST);
        assertEquals(31.62, OR.rPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-6, OR.rPrime.y, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : right lateral
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        double[] expectedCfH = new double[]{32.26, 32.87, 35.13, 37.43, 23.55, 4.65, 0.49, 0.10};
        double[] expectedAGroundH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        double[] expectedCfF = new double[]{32.26, 32.87, 35.13, 37.43, 23.55, 4.65, 0.49, 0.10};
        double[] expectedAGroundF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};

        // Table 108
        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.00, 0.01, 0.03, 0.06, 0.12, 0.31, 1.06, 3.80};
        expectedADiv = new double[]{41.11, 41.11, 41.11, 41.11, 41.11, 41.11, 41.11, 41.11};
        expectedDeltaDiffSRH = new double[]{8.17, 10.09, 12.67, 16.13, 20.46, 24.62, 28.11, 31.25};
        expectedLH = new double[]{45.22, 43.29, 40.69, 37.20, 32.81, 28.46, 24.22, 18.34};
        expectedLF = new double[]{45.22, 43.29, 40.69, 37.20, 32.81, 28.46, 24.22, 18.34};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(1);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);

        //Assertions
        assertEquals(2.74, proPath.e, ERROR_EPSILON_LOWEST);
        assertEquals(0.48, proPath.deltaH, ERROR_EPSILON_LOWEST);
        assertEquals(0.48, proPath.deltaF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LF - right lateral", expectedLF, actualLF, ERROR_EPSILON_LOW);

        //Path2 : left lateral
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        expectedCfH = new double[]{32.82, 33.45, 35.78, 38.05, 23.71, 4.62, 0.49, 0.10};
        expectedAGroundH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        expectedCfF = new double[]{32.82, 33.45, 35.78, 38.05, 23.71, 4.62, 0.49, 0.10};
        expectedAGroundF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        //Assertions
        assertEquals(2.74, proPath.e, ERROR_EPSILON_LOWEST);
        assertEquals(1.03, proPath.deltaH, ERROR_EPSILON_LOWEST);
        assertEquals(1.03, proPath.deltaF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("WH - left lateral", expectedWH, actualWH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("CfH - left lateral", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH - left lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF - left lateral", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - left lateral", expectedCfF, actualCfF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundF - left lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, sumArray(SOUND_POWER_LEVELS, A_WEIGHTING));
        assertArrayEquals(new double[]{21.81, 29.66, 34.31, 36.14, 35.57, 33.72, 31.12, 25.37},L, ERROR_EPSILON_VERY_LOW);
    }

    /**
     * Test TC13 -- Ground with spatially varying heights and acoustic properties and polygonal
     * building
     */
    @Test
    public void TC13() throws IOException {
        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC13_Direct", "TC13_Right", "TC13_Left");


        //Expected values

        /* Table 117 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(112.41, 0.00));
        expectedZProfile.add(new Coordinate(164.07, 7.8));
        expectedZProfile.add(new Coordinate(164.07, 30.00));
        expectedZProfile.add(new Coordinate(181.83, 30));
        expectedZProfile.add(new Coordinate(181.83, 10));
        expectedZProfile.add(new Coordinate(194.16, 10));

        /* Table 118 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a      b    zs     zr      dp    Gp   Gp'
                {0.04, -1.68, 2.68, 25.86, 164.99, 0.71, 0.54},
                {0.00, 10.00, 20.0, 18.50, 12.33, 0.20,  NaN}
        };
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a      b    zs     zr      dp    Gp    Gp'
                {0.06, -2.99, 3.98, 19.83, 201.30, 0.61, 0.53}
        };
        double [][] segmentsMeanPlanes2 = new double[][]{
                //  a      b    zs     zr      dp    Gp    Gp'
                {0.05, -2.82, 3.82, 20.69, 196.29, 0.63, 0.54}
        };

        //Assertion
        assertZProfil(expectedZProfile, Arrays.asList(propDataOut.getPropagationPaths().get(0)
                .getSRSegment().getPoints2DGround()));
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());
        assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment());
        assertPlanes(segmentsMeanPlanes2, propDataOut.getPropagationPaths().get(2).getSRSegment());

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{9.76, 13.15, 17.15, 20.71, 23.88, 26.92, 29.93, 32.94};
        double[] expectedAGroundSOH = new double[]{-1.38, -1.38, -1.38, -1.38, -1.38, -1.38, -1.38, -1.38};
        double[] expectedAGroundORH = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRH = new double[]{10.52, 14.08, 18.17, 21.75, 24.94, 27.98, 31.00, 34.01};
        double[] expectedDeltaDiffSRPrimeH = new double[]{24.55, 28.89, 33.30, 37.00, 40.23, 43.30, 46.32, 49.34};
        double[] expectedDeltaGroundSOH = new double[]{-1.27, -1.25, -1.24, -1.23, -1.23, -1.23, -1.23, -1.23};
        double[] expectedDeltaGroundORH = new double[]{-0.49, -0.44, -0.42, -0.41, -0.41, -0.41, -0.41, -0.41};
        double[] expectedADiffH = new double[]{8.00, 11.46, 15.50, 19.06, 22.24, 23.36, 23.36, 23.36};

        double[] expectedDeltaDiffSRF = new double[]{9.54, 12.88, 16.85, 20.42, 23.57, 26.60, 29.62, 32.62};
        double[] expectedAGroundSOF = new double[]{-1.38, -1.38, -1.38, -1.38, -1.38, -1.38, -1.38, -1.38};
        double[] expectedAGroundORF = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRF = new double[]{10.34, 13.86, 17.93, 21.51, 24.69, 27.74, 30.75, 33.76};
        double[] expectedDeltaDiffSRPrimeF = new double[]{24.55, 28.88, 33.30, 37.00, 40.22, 43.29, 46.32, 49.33};
        double[] expectedDeltaGroundSOF = new double[]{-1.27, -1.24, -1.23, -1.22, -1.22, -1.22, -1.22, -1.22};
        double[] expectedDeltaGroundORF = new double[]{-0.48, -0.43, -0.41, -0.40, -0.40, -0.40, -0.40, -0.39};
        double[] expectedADiffF = new double[]{7.80, 11.21, 15.22, 18.78, 21.95, 23.38, 23.38, 23.38};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.38, 0.72, 1.90, 6.43, 22.92};
        double[] expectedADiv = new double[]{56.85, 56.85, 56.85, 56.85, 56.85, 56.85, 56.85, 56.85};
        double[] expectedABoundaryH = new double[]{8.00, 11.46, 15.50, 19.06, 22.24, 23.36, 23.36, 23.36};
        double[] expectedABoundaryF = new double[]{7.80, 11.21, 15.22, 18.78, 21.95, 23.38, 23.38, 23.38};
        double[] expectedLH = new double[]{28.13, 24.61, 20.45, 16.71, 13.19, 10.90, 6.36, -10.13};
        double[] expectedLF = new double[]{28.33, 24.86, 20.73, 17.00, 13.49, 10.87, 6.34, -10.16};
        double[] expectedL = new double[]{28.23, 24.73, 20.59, 16.85, 13.34, 10.88, 6.35, -10.14};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);

        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);

        //Assertions
        assertEquals(0.19, proPath.getSegmentList().get(0).sPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-4.35, proPath.getSegmentList().get(0).sPrime.y, ERROR_EPSILON_LOWEST);
        assertEquals(194.16, proPath.getSegmentList().get(2).rPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-8.50, proPath.getSegmentList().get(2).rPrime.y, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);


        //Path1 : right lateral
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.02, 0.09, 0.48, 2.42, 11.40};
        double[] expectedCfH = new double[]{207.46, 223.88, 230.36, 122.25, 18.84, 2.12, 0.41, 0.09};
        double[] expectedAGroundH = new double[]{-1.40, -1.40, -1.40, -1.40, -1.40, -1.40, -1.40, -1.40};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.02, 0.13, 0.67, 3.32, 15.27};
        double[] expectedCfF = new double[]{209.72, 228.83, 218.90, 92.01, 11.30, 1.50, 0.30, 0.07};
        double[] expectedAGroundF = new double[]{-1.40, -1.40, -1.40, -1.40, -1.40, -1.40, -1.40, -1.40};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.02, 0.08, 0.21, 0.39, 0.74, 1.95, 6.62, 23.60};
        expectedADiv = new double[]{56.85, 56.85, 56.85, 56.85, 56.85, 56.85, 56.85, 56.85};
        expectedDeltaDiffSRH = new double[]{16.88, 20.30, 24.57, 29.02, 32.80, 36.06, 39.13, 42.16};
        expectedLH = new double[]{20.65, 17.17, 12.77, 8.14, 4.02, -0.45, -8.20, -28.21};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(1);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);

        //Path2 : left lateral
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.02, 0.09, 0.48, 2.46, 11.67};
        expectedCfH = new double[]{202.25, 218.20, 224.79, 119.88, 18.59, 2.08, 0.41, 0.09};
        expectedAGroundH = new double[]{-1.39, -1.39, -1.39, -1.39, -1.39, -1.39, -1.39, -1.39};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.03, 0.14, 0.72, 3.56, 16.24};
        expectedCfF = new double[]{204.90, 223.86, 211.09, 85.42, 10.19, 1.39, 0.28, 0.06};
        expectedAGroundF = new double[]{-1.39, -1.39, -1.39, -1.39, -1.39, -1.39, -1.39, -1.39};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.02, 0.08, 0.21, 0.38, 0.72, 1.90, 6.46, 23.03};
        expectedADiv = new double[]{56.85, 56.85, 56.85, 56.85, 56.85, 56.85, 56.85, 56.85};
        expectedDeltaDiffSRH = new double[]{9.89, 12.15, 14.74, 17.53, 20.42, 23.37, 26.35, 29.35};
        expectedLH = new double[]{27.63, 25.32, 22.60, 19.64, 16.40, 12.27, 4.74, -14.83};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});
        assertArrayEquals(  new double[]{5.14,12.29,16.39,18.47,18.31,15.97,9.72,-9.92},L, ERROR_EPSILON_VERY_LOW);
    }

    /**
     * Test TC14 -- Flat ground with homogeneous acoustic properties and polygonal building –
     * receiver at large height
     */
    @Test
    public void TC14() throws IOException {

        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC14_Direct", "TC14_Right", "TC14_Left");


        //Expected values

        /* Table 132 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(5.39, 0.00));
        expectedZProfile.add(new Coordinate(5.39, 10.00));
        expectedZProfile.add(new Coordinate(11.49, 10.0));
        expectedZProfile.add(new Coordinate(11.49, 0.0));
        expectedZProfile.add(new Coordinate(19.72, 0));

        /* Table 133 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a       b    zs     zr    dp    Gp   Gp'
                {0.00,   0.00, 1.00, 10.00, 5.39, 0.20, 0.20},
                {-1.02, 17.11, 1.08, 18.23, 0.72, 0.11,  NaN} // Fix Cnossos document Zs is 1.08 not 0
        };
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a      b    zs     zr     dp    Gp    Gp'
                {-0.02, 1.13, 0.10, 22.32, 19.57, 0.18, 0.20} // Fix Cnossos document Zs is 0.1 not 0
        };
        double [][] segmentsMeanPlanes2 = new double[][]{
                //  a     b    zs     zr      dp    Gp    Gp'
                {0.00, 1.35, 0.32, 21.69, 22.08, 0.17, 0.20} // Fix Cnossos document Zs is 0.32 not 0
        };


        //Assertion
        // Wrong value of z1 in Cnossos document for the 3 paths
        assertZProfil(expectedZProfile, Arrays.asList(propDataOut.getPropagationPaths().get(0)
                .getSRSegment().getPoints2DGround()));
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());
        assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment());
        assertPlanes(segmentsMeanPlanes2, propDataOut.getPropagationPaths().get(2).getSRSegment());

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{7.15, 8.65, 10.67, 13.08, 15.76, 18.59, 21.51, 24.48};
        double[] expectedAGroundSOH = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedAGroundORH = new double[]{-2.66, -2.66, -2.66, -2.66, -2.66, -2.66, -2.66, -2.66};
        double[] expectedDeltaDiffSPrimeRH = new double[]{8.43, 10.37, 12.73, 15.38, 18.20, 21.11, 24.07, 27.05};
        double[] expectedDeltaDiffSRPrimeH = new double[]{21.88, 24.81, 27.80, 30.80, 33.80, 36.81, 39.82, 42.83};
        double[] expectedDeltaGroundSOH = new double[]{-2.11, -2.02, -1.94, -1.90, -1.87, -1.87, -1.85, -1.84};
        double[] expectedDeltaGroundORH = new double[]{-0.55, -0.47, -0.42, -0.39, -0.38, -0.37, -0.37, -0.37};
        double[] expectedADiffH = new double[]{4.49, 6.17, 8.30, 10.79, 13.51, 16.37, 19.30, 22.27};

        double[] expectedDeltaDiffSRF = new double[]{7.14, 8.65, 10.66, 13.07, 15.75, 18.58, 21.50, 24.47};
        double[] expectedAGroundSOF = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedAGroundORF = new double[]{-2.66, -2.66, -2.66, -2.66, -2.66, -2.66, -2.66, -2.66};
        double[] expectedDeltaDiffSPrimeRF = new double[]{8.43, 10.36, 12.73, 15.38, 18.19, 21.10, 24.06, 27.05};
        double[] expectedDeltaDiffSRPrimeF = new double[]{21.88, 24.81, 27.80, 30.80, 33.80, 36.81, 39.82, 42.83};
        double[] expectedDeltaGroundSOF = new double[]{-2.11, -2.02, -1.94, -1.90, -1.87, -1.86, -1.85, -1.84};
        double[] expectedDeltaGroundORF = new double[]{-0.55, -0.47, -0.42, -0.39, -0.38, -0.37, -0.37, -0.37};
        double[] expectedADiffF = new double[]{4.48, 6.16, 8.30, 10.78, 13.50, 16.36, 19.29, 22.26};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.00, 0.01, 0.03, 0.06, 0.11, 0.29, 0.97, 3.45};
        double[] expectedADiv = new double[]{40.41, 40.41, 40.41, 40.41, 40.41, 40.41, 40.41, 40.41};
        double[] expectedABoundaryH = new double[]{4.49, 6.17, 8.30, 10.79, 13.51, 16.37, 19.30, 22.27};
        double[] expectedABoundaryF = new double[]{4.48, 6.16, 8.30, 10.78, 13.50, 16.36, 19.29, 22.26};
        double[] expectedLH = new double[]{48.10, 46.41, 44.26, 41.74, 38.97, 35.94, 32.33, 26.87};
        double[] expectedLF = new double[]{48.10, 46.42, 44.26, 41.75, 38.98, 35.95, 32.33, 26.88};


        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        //Assertions
        assertEquals(0.00, proPath.getSegmentList().get(0).sPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-1.00, proPath.getSegmentList().get(0).sPrime.y, ERROR_EPSILON_LOWEST);
        assertEquals(-6.35, proPath.getSegmentList().get(1).rPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-2.48, proPath.getSegmentList().get(1).rPrime.y, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);


        //Path1 : right lateral
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.00, 0.01, 0.04, 0.23, 1.23};
        double[] expectedCfH = new double[]{19.57, 19.60, 19.73, 20.34, 22.18, 21.49, 9.40, 1.20};
        double[] expectedAGroundH = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.00, 0.01, 0.03, 0.18, 0.98};
        double[] expectedCfF = new double[]{19.57, 19.59, 19.70, 20.20, 21.86, 22.22, 11.29, 1.67};
        double[] expectedAGroundF = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.00, 0.01, 0.03, 0.06, 0.11, 0.29, 0.98, 3.48};
        expectedADiv = new double[]{40.41, 40.41, 40.41, 40.41, 40.41, 40.41, 40.41, 40.41};
        expectedDeltaDiffSRH = new double[]{6.76, 8.13, 10.15, 13.04, 17.02, 21.28, 24.93, 28.14};
        expectedDeltaDiffSRF = new double[]{6.76, 8.13, 10.15, 13.04, 17.02, 21.28, 24.93, 28.14};
        expectedLH = new double[]{48.23, 46.85, 44.81, 41.89, 37.86, 33.42, 29.09, 23.37};
        expectedLF = new double[]{48.23, 46.85, 44.81, 41.89, 37.86, 33.42, 29.09, 23.37};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(1);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        //Assertions
        assertEquals(2.21, proPath.e, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRF - right lateral", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - right lateral", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        //Path2 : left lateral
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.00, 0.01, 0.04, 0.23, 1.22};
        expectedCfH = new double[]{22.09, 22.12, 22.28, 23.04, 25.19, 23.72, 9.58, 1.15};
        expectedAGroundH = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.00, 0.01, 0.03, 0.16, 0.87};
        expectedCfF = new double[]{22.09, 22.11, 22.23, 22.79, 24.66, 25.08, 12.76, 1.88};
        expectedAGroundF = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.00, 0.01, 0.03, 0.06, 0.11, 0.30, 1.02, 3.65};
        expectedADiv = new double[]{40.41, 40.41, 40.41, 40.41, 40.41, 40.41, 40.41, 40.41};
        expectedDeltaDiffSRH = new double[]{11.85, 14.39, 17.19, 20.19, 23.58, 27.70, 32.24, 36.23};
        expectedDeltaDiffSRF = new double[]{11.85, 14.39, 17.19, 20.19, 23.58, 27.70, 32.24, 36.23};
        expectedLH = new double[]{43.14, 40.59, 37.77, 34.74, 31.30, 26.99, 21.73, 15.12};
        expectedLF = new double[]{43.14, 40.59, 37.77, 34.74, 31.30, 26.99, 21.73, 15.12};

        //Actual values
        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualDeltaDiffSRF = proPath.aBoundaryH.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH", expectedCfH, actualCfH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundH", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF", expectedCfF, actualCfF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundF", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRF - right lateral", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - right lateral", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});
        assertArrayEquals(  new double[]{25.61,34.06,39.39,42.04,41.86,39.42,35.26,27.57},L, ERROR_EPSILON_VERY_LOW);
    }

    /**
     * Test TC15 -- Flat ground with homogeneous acoustic properties and four buildings
     */
    @Test
    public void TC15() throws IOException {

        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC15_Direct", "TC15_Right", "TC15_Left");

        /* Table 148 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.00, 0.00));
        expectedZProfile.add(new Coordinate(5.02, 0.00));
        expectedZProfile.add(new Coordinate(5.02, 8.00));
        expectedZProfile.add(new Coordinate(15.07, 8.0));
        expectedZProfile.add(new Coordinate(15.08, 0.0));
        expectedZProfile.add(new Coordinate(24.81, 0.0));
        expectedZProfile.add(new Coordinate(24.81, 12.0));
        expectedZProfile.add(new Coordinate(30.15, 12.00));
        expectedZProfile.add(new Coordinate(30.15, 0.00));
        expectedZProfile.add(new Coordinate(37.19, 0.0));
        expectedZProfile.add(new Coordinate(37.19, 10.0));
        expectedZProfile.add(new Coordinate(41.52, 10.0));
        expectedZProfile.add(new Coordinate(41.52, 0.0));
        expectedZProfile.add(new Coordinate(50.25, 0.0));

        /* Table 149 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b     zs    zr    dp    Gp   Gp'
                {0.00, 0.00,  1.00, 8.00, 5.02, 0.50, 0.50},
                {0.00, 0.00, 10.00, 5.00, 8.73, 0.50,  NaN}
        };
        double [][] segmentsMeanPlanes1 = new double[][]{ // right
                //  a      b    zs    zr     dp    Gp    Gp'
                {0.08, -1.19, 2.18, 2.01, 54.80, 0.46, 0.48}
        };
        double [][] segmentsMeanPlanes2 = new double[][]{ // left
                //  a     b    zs    zr     dp    Gp    Gp'
                {0.00, 0.00, 1.00, 5.00, 53.60, 0.50, 0.50}
        };


        //Assertion
        assertZProfil(expectedZProfile, Arrays.asList(propDataOut.getPropagationPaths().get(0).
                getSRSegment().getPoints2DGround()));
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());
        assertPlanes(segmentsMeanPlanes2, propDataOut.getPropagationPaths().get(2).getSRSegment()); // left
        //assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment()); // right : error in value of b cnossos

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{18.64, 22.86, 26.49, 29.68, 32.74, 35.76, 38.77, 41.78};
        double[] expectedAGroundSOH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedAGroundORH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        double[] expectedDeltaDiffSPrimeRH = new double[]{19.67, 23.92, 27.55, 30.75, 33.81, 36.83, 39.84, 42.85};
        double[] expectedDeltaDiffSRPrimeH = new double[]{22.21, 26.50, 30.14, 33.35, 36.41, 39.43, 42.44, 45.45};
        double[] expectedDeltaGroundSOH = new double[]{-1.34, -1.34, -1.34, -1.34, -1.34, -1.34, -1.34, -1.34};
        double[] expectedDeltaGroundORH = new double[]{-1.02, -1.02, -1.01, -1.01, -1.01, -1.01, -1.01, -1.01};
        double[] expectedADiffH = new double[]{16.27, 20.51, 22.65, 22.65, 22.65, 22.65, 22.65, 22.65};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.01, 0.02, 0.05, 0.10, 0.18, 0.49, 1.65, 5.89};
        double[] expectedADiv = new double[]{45.05, 45.05, 45.05, 45.05, 45.05, 45.05, 45.05, 45.05};
        double[] expectedABoundaryH = new double[]{16.27, 20.51, 22.65, 22.65, 22.65, 22.65, 22.65, 22.65};
        double[] expectedABoundaryF = new double[]{16.27, 20.51, 22.65, 22.65, 22.65, 22.65, 22.65, 22.65};
        double[] expectedLH = new double[]{31.67, 27.42, 25.25, 25.20, 25.12, 24.81, 23.65, 19.41};
        double[] expectedLF = new double[]{31.67, 27.42, 25.25, 25.20, 25.12, 24.81, 23.65, 19.41};
        double[] expectedL = new double[]{31.67, 27.42, 25.25, 25.20, 25.12, 24.81, 23.65, 19.41};
        double[] expectedLA = sumArray(expectedL,A_WEIGHTING);
        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);
        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryH;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = sumArray(actualL,A_WEIGHTING);

        //Assertions
        assertEquals(0.00, proPath.getSegmentList().get(0).sPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-1.00, proPath.getSegmentList().get(0).sPrime.y, ERROR_EPSILON_LOWEST);
        assertEquals(50.25, proPath.getSegmentList().get(proPath.getSegmentList().size()-1).rPrime.x, ERROR_EPSILON_LOWEST);
        assertEquals(-5.00, proPath.getSegmentList().get(proPath.getSegmentList().size()-1).rPrime.y, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        double[] diffL = diffArray(expectedL, actualL);
        double[] diffLa = diffArray(expectedLA, actualLA);
        double[] valL = getMaxValeurAbsolue(diffL);
        double[] valLA = getMaxValeurAbsolue(diffLa);

        //Path1 : right lateral
        //Expected values - right lateral
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.07, 0.37, 1.92, 9.32};
        double[] expectedCfH = new double[]{55.20, 56.69, 61.53, 61.63, 29.93, 4.28, 0.52, 0.11};
        double[] expectedAGroundH = new double[]{-1.56, -1.56, -1.32, -1.32, -1.56, -1.56, -1.56, -1.56};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.06, 0.33, 1.69, 8.30};
        double[] expectedCfF = new double[]{55.15, 56.48, 61.02, 62.61, 33.11, 5.18, 0.59, 0.12};
        double[] expectedAGroundF = new double[]{-1.56, -1.30, -1.08, -1.56, -1.56, -1.56, -1.56, -1.56};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.01, 0.02, 0.06, 0.11, 0.20, 0.53, 1.80, 6.41};
        expectedADiv = new double[]{45.05, 45.05, 45.05, 45.05, 45.05, 45.05, 45.05, 45.05};
        expectedDeltaDiffSRH = new double[]{17.54, 20.82, 25.57, 28.82, 31.9, 34.91, 37.92, 40.93};
        expectedLH = new double[]{31.97, 27.66, 23.64, 20.26, 17.42, 14.07, 9.79, 2.17};

        //Actual values
        //Actual values - right lateral
        proPath = propDataOut.getPropagationPaths().get(1);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH - right lateral", expectedWH, actualWH, ERROR_EPSILON_MEDIUM);
        assertDoubleArrayEquals("CfH - right lateral", expectedCfH, actualCfH, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_MEDIUM);
        assertDoubleArrayEquals("WF - right lateral", expectedWF, actualWF, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("CfF - right lateral", expectedCfF, actualCfF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("AlphaAtm - right lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - right lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - right lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundH - right lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundF - right lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_MEDIUM);
        assertDoubleArrayEquals("DeltaDiffSRH - right lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("LH - right lateral", expectedLH, actualLH, ERROR_EPSILON_LOW);

        //Path2 : left lateral
        //Expected values - left lateral
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        expectedCfH = new double[]{54.02, 55.58, 60.47, 59.60, 27.53, 3.75, 0.47, 0.10};
        expectedAGroundH = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.41, 2.10, 10.13};
        expectedCfF = new double[]{54.02, 55.58, 60.47, 59.60, 27.53, 3.75, 0.47, 0.10};
        expectedAGroundF = new double[]{-1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50, -1.50};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.01, 0.02, 0.06, 0.10, 0.20, 0.52, 1.76, 6.28};
        expectedADiv = new double[]{45.05, 45.05, 45.05, 45.05, 45.05, 45.05, 45.05, 45.05};
        expectedDeltaDiffSRH = new double[]{16.63, 20.81, 24.44, 27.65, 30.70, 33.72, 36.73, 39.74};
        expectedLH = new double[]{32.81, 28.62, 24.95, 21.70, 18.55, 15.21, 10.96, 3.43};

        //Actual values
        //Actual values - left lateral
        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);

        //Assertions
        assertDoubleArrayEquals("WH - left lateral", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - left lateral", expectedCfH, actualCfH, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("AGroundH - left lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - left lateral", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - left lateral", expectedCfF, actualCfF, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("AGroundF - left lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - left lateral", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - left lateral", expectedAAtm, actualAAtm, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("ADiv - left lateral", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - left lateral", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF - left lateral", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRH - left lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("LH - left lateral", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});
        assertArrayEquals(new double[]{10.75,16.57,20.81,24.51,26.55,26.78,25.04,18.50},L, ERROR_EPSILON_VERY_LOW);
    }

    /**
     * Reflecting barrier on ground with spatially varying heights and acoustic properties
     */
    @Test
    public void TC16() throws IOException {
        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC16_Direct", "TC16_Reflection");

        /* Table 163 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.0, 0.0));
        expectedZProfile.add(new Coordinate(112.41, 0.0));
        expectedZProfile.add(new Coordinate(178.84, 10));
        expectedZProfile.add(new Coordinate(194.16, 10));

        /* Table 169 */
        List<Coordinate> expectedZProfileReflection = new ArrayList<>();
        expectedZProfileReflection.add(new Coordinate(0.0, 0.0));
        expectedZProfileReflection.add(new Coordinate(117.12, 0.0));
        expectedZProfileReflection.add(new Coordinate(129.75, 1.82));
        expectedZProfileReflection.add(new Coordinate(129.75, 1.82));
        expectedZProfileReflection.add(new Coordinate(129.75, 1.82));
        expectedZProfileReflection.add(new Coordinate(183.01, 10));
        expectedZProfileReflection.add(new Coordinate(198.04, 10));

        /* Table 166 */
        Coordinate expectedSPrime =new Coordinate(0.42,-6.64);
        Coordinate expectedRPrime =new Coordinate(194.84,1.70);

        assertMirrorPoint(expectedSPrime,expectedRPrime,propDataOut.getPropagationPaths().get(0).getSRSegment().
                sPrime,propDataOut.getPropagationPaths().get(0).getSRSegment().rPrime);


        /* Table 165 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.05, -2.83, 3.83, 6.16, 194.59, 0.54, 0.64}
        };

        /* Table 171 */
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.05, -2.80, 3.80, 6.37, 198.45, 0.51, 0.65}
        };

        //Assertion

        // Check SR direct line
        List<Coordinate> result = propDataOut.getPropagationPaths().get(0).getCutProfile().computePts2DGround();
        assertZProfil(expectedZProfile,result);
        assertEquals(2, propDataOut.getPropagationPaths().size());
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSRSegment());

        // Check reflection path
        result = propDataOut.getPropagationPaths().get(1).getCutProfile().computePts2DGround();
        assertZProfil(expectedZProfileReflection, result);
        assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment());

        //Expected values
        //Path0 : vertical plane
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.03, 0.14, 0.75, 3.70, 16.77};
        double[] expectedCfH = new double[]{203.37, 222.35, 207.73, 82.09, 9.63, 1.33, 0.27, 0.06};
        double[] expectedAGroundH = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.16, 10.35};
        double[] expectedCfF = new double[]{199.73, 214.27, 225.54, 131.93, 22.89, 2.42, 0.46, 0.10};
        double[] expectedAGroundF = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.38, 0.71, 1.88, 6.38, 22.75};
        double[] expectedADiv = new double[]{56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78};
        double[] expectedABoundaryH = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};
        double[] expectedABoundaryF = new double[]{-1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07, -1.07};
        double[] expectedLH = new double[]{37.26, 37.21, 37.08, 36.91, 36.57, 35.41, 30.91, 14.54};
        double[] expectedLF = new double[]{37.26, 37.21, 37.08, 36.91, 36.57, 35.41, 30.91, 14.54};
        double[] expectedL = new double[]{37.26, 37.21, 37.08, 36.91, 36.57, 35.41, 30.91, 14.54};
        double[] expectedLA = new double[]{11.06, 21.11, 28.48, 33.71, 36.57, 36.61, 31.91, 13.44};

        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);
        double[] directLA = actualLA;

        //Assertions
        assertEquals(0.40, proPath.getSegmentList().get(0).sPrime.x, ERROR_EPSILON_VERY_LOW);
        assertEquals(-6.58, proPath.getSegmentList().get(0).sPrime.y, ERROR_EPSILON_VERY_LOW);
        assertEquals(198.71, proPath.getSegmentList().get(proPath.getSegmentList().size()-1).rPrime.x, ERROR_EPSILON_VERY_HIGH);
        assertEquals(1.27, proPath.getSegmentList().get(proPath.getSegmentList().size()-1).rPrime.y, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("WH - vertical plane", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - vertical plane", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - vertical plane", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - vertical plane", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - vertical plane", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF - vertical plane", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : reflexion Table 175
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.03, 0.14, 0.76, 3.73, 16.91};
        expectedCfH = new double[]{207.64, 227.15, 210.40, 81.36, 9.40, 1.32, 0.27, 0.06};
        expectedAGroundH = new double[]{-1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.43, 2.20, 10.56};
        expectedCfF = new double[]{203.91, 219.10, 229.36, 130.79, 21.96, 2.36, 0.45, 0.09};
        expectedAGroundF = new double[]{-1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06};
        double [] expectedLabs = new double[]{-0.46, -0.97, -1.55, -2.22, -3.01, -3.98, -5.23, -3.01};
        double[] expectedRetroDiffH = new double[]{0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00};
        double[] expectedRetroDiffF = new double[]{0.68, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00};
        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.02, 0.08, 0.21, 0.38, 0.73, 1.92, 6.50, 23.20};
        expectedADiv = new double[]{56.95, 56.95, 56.95, 56.95, 56.95, 56.95, 56.95, 56.95};
        expectedABoundaryH = new double[]{-1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06};
        expectedABoundaryF = new double[]{-1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06};
        expectedLH = new double[]{36.63, 36.06, 35.35, 34.51, 33.37, 31.21, 25.37, 10.90};
        expectedLF = new double[]{35.94, 36.06, 35.35, 34.51, 33.37, 31.21, 25.37, 10.90};
        expectedL = new double[]{36.30, 36.06, 35.35, 34.51, 33.37, 31.21, 25.37, 10.90};
        expectedLA = new double[]{10.10, 19.96, 26.75, 31.31, 33.37, 32.41, 26.37, 9.80};

        proPath = propDataOut.getPropagationPaths().get(1);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualABoundaryH = proPath.double_aBoundaryH;
        actualABoundaryF = proPath.double_aBoundaryF;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);
        double[] reflexionLA = actualLA;

        assertDoubleArrayEquals("WH - reflexion", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - reflexion", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - reflexion", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - reflexion", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - reflexion", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundF - reflexion", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - reflexion", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - reflexion", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - reflexion", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - reflexion", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryF - reflexion", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("dLretrodif H - reflexion", expectedRetroDiffH, proPath.aRetroDiffH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("dLretrodif F - reflexion", expectedRetroDiffF, proPath.aRetroDiffF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("A Labs - reflexion", expectedLabs, proPath.aRef, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - reflexion", expectedLH, actualLH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LF - reflexion", expectedLF, actualLF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("L - reflexion", expectedL, actualL, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LA - reflexion", expectedLA, actualLA, ERROR_EPSILON_LOWEST);

        double[] LA = sumDbArray(directLA, reflexionLA);

        assertArrayEquals(  new double[]{13.62,23.58,30.71,35.68,38.27,38.01,32.98,15.00},LA, ERROR_EPSILON_LOWEST);
    }

    /**
     * TC17 - Reflecting barrier on ground with spatially varying heights and acoustic properties
     * reduced receiver height
     */
    @Test
    public void TC17() throws IOException {
        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC17_Direct", "TC17_Reflection");

        // Expected Values

        /* Table 178 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.0, 0.0));
        expectedZProfile.add(new Coordinate(112.41, 0.0));
        expectedZProfile.add(new Coordinate(178.84, 10));
        expectedZProfile.add(new Coordinate(194.16, 10));

        //Assertion
        assertZProfil(expectedZProfile, Arrays.asList(propDataOut.getPropagationPaths().get(0).
                getSRSegment().getPoints2DGround()));

        // Test R-CRIT table 179
        Coordinate D = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).r;
        Coordinate Sp = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).sPrime;
        Coordinate Rp = propDataOut.getPropagationPaths().get(0).getSegmentList().get(1).rPrime ;

        double deltaD = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).d
                + propDataOut.getPropagationPaths().get(0).getSegmentList().get(1).d -
                propDataOut.getPropagationPaths().get(0).getSRSegment().d;
        double deltaDE = Sp.distance(D) + D.distance(Rp) - Sp.distance(Rp);
        List<Integer> res1 = new ArrayList<>(3) ;
        List<Integer> res2 = new ArrayList<>(3);

        for(int f : propDataOut.inputData.freq_lvl) {
            if(-deltaD > -(340./f) / 20) {
                res1.add(1);
            }
            if (!(deltaD > (((340./f) / 4) - deltaDE))){
                res2.add(0);
            }
        }

        //Expected values
        //Path0 : vertical plane
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, NaN, NaN, 0.53, 2.70, 12.70};
        double[] expectedCfH = new double[]{200.89, 217.45, 220.41, NaN, NaN, 1.88, 0.37, 0.08};
        double[] expectedAGroundH = new double[]{-1.32, -1.32, -1.32, NaN, NaN, -1.32, -1.32, -1.32};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.16, 10.35};
        double[] expectedCfF = new double[]{199.59, 214.11, 225.39, 131.90, 22.89, 2.42, 0.46, 0.10};
        double[] expectedAGroundF = new double[]{-1.32, -1.32, -1.29, -1.05, -1.32, -1.32, -1.32, -1.32};

        double[] expectedDeltaDiffSR = new double[]{0., 0., 0., 3.16, 0.56, 0., 0., 0.};
        double[] expectedAGroundSO = new double[]{0., 0., 0., 2.74, -1.21, 0., 0., 0.};
        double[] expectedAGroundOR = new double[]{0., 0., 0., -2.40, -2.40, 0., 0., 0.};
        double[] expectedDeltaDiffSPrimeR = new double[]{0., 0., 0., 4.71, 4.65, 0., 0., 0.};
        double[] expectedDeltaDiffSRPrime = new double[]{0., 0., 0., 10.83, 13.26, 0., 0., 0.};
        double[] expectedDeltaGroundSO = new double[]{0., 0., 0., 2.23, -0.77, 0., 0., 0.};
        double[] expectedDeltaGroundOR = new double[]{0., 0., 0., -1.07, -0.62, 0., 0., 0.};
        double[] expectedADiff = new double[]{0., 0., 0., 4.31, -0.83, 0., 0., 0.};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.88, 6.37, 22.73};
        double[] expectedADiv = new double[]{56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78, 56.78};
        double[] expectedABoundaryH = new double[]{-1.32, -1.32, -1.32, 4.31, -0.83, -1.32, -1.32, -1.32};
        double[] expectedABoundaryF = new double[]{-1.32, -1.32, -1.32, -1.05, -1.32, -1.32, -1.32, -1.32};
        double[] expectedLH = new double[]{37.53, 37.47, 37.35, 31.54, 36.34, 35.67, 31.18, 14.82};
        double[] expectedLF = new double[]{37.53, 37.47, 37.31, 36.89, 36.84, 35.67, 31.18, 14.82};
        double[] expectedL = new double[]{37.53, 37.47, 37.33, 34.99, 36.60, 35.67, 31.18, 14.82};
        double[] expectedLA = new double[]{11.33, 21.37, 28.73, 31.79, 36.60, 36.87, 32.18, 13.72};

        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        double[] actualDeltaDiffSR = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSO = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundOR = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeR = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrime = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSO = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundOR = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiff = proPath.aBoundaryH.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);
        double[] directLA = actualLA;


        //Assertions

        assertDoubleArrayEquals("WH - vertical plane", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - vertical plane", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - vertical plane", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - vertical plane", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - vertical plane", expectedCfF, actualCfF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundF - vertical plane", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("DeltaDiffSR - vertical plane", expectedDeltaDiffSR, actualDeltaDiffSR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSO - vertical plane", expectedAGroundSO, actualAGroundSO, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundOR - vertical plane", expectedAGroundOR, actualAGroundOR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeR - vertical plane", expectedDeltaDiffSPrimeR, actualDeltaDiffSPrimeR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrime - vertical plane", expectedDeltaDiffSRPrime, actualDeltaDiffSRPrime, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSO - vertical plane", expectedDeltaGroundSO, actualDeltaGroundSO, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundOR - vertical plane", expectedDeltaGroundOR, actualDeltaGroundOR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiff - vertical plane", expectedADiff, actualADiff, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : reflexion
        expectedWH = new double[]{0.00, 0.00, 0.00, NaN, 0.10, 0.55, 2.77, 12.97};
        expectedCfH = new double[]{205.15, 222.41, 223.52, NaN, 15.34, 1.83, 0.36, 0.08};
        expectedAGroundH = new double[]{-1.31, -1.31, -1.31, NaN, -1.31, -1.31, -1.31, -1.31};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.43, 2.20, 10.56};
        expectedCfF = new double[]{203.77, 218.95, 229.22, 130.76, 21.97, 2.36, 0.45, 0.09};
        expectedAGroundF = new double[]{-1.31, -1.31, -1.26, -1.28, -1.31, -1.31, -1.31, -1.31};

        expectedDeltaDiffSR = new double[]{0., 0., 0., 2.92, 0., 0., 0., 0.};
        expectedAGroundSO = new double[]{0., 0., 0., 2.66, 0., 0., 0., 0.};
        expectedAGroundOR = new double[]{0., 0., 0., -2.40, 0., 0., 0., 0.};
        expectedDeltaDiffSPrimeR = new double[]{0., 0., 0., 4.65, 0., 0., 0., 0.};
        expectedDeltaDiffSRPrime = new double[]{0., 0., 0., 10.81, 0., 0., 0., 0.};
        expectedDeltaGroundSO = new double[]{0., 0., 0., 2.12, 0., 0., 0., 0.};
        expectedDeltaGroundOR = new double[]{0., 0., 0., -1.05, 0., 0., 0., 0.};
        expectedADiff = new double[]{0., 0., 0., 3.99, 0., 0., 0., 0.};

        expectedAlphaAtm = new double[]{0.1, 0.4, 1., 1.9, 3.7, 9.7, 32.8, 116.9};
        expectedAAtm = new double[]{0.02, 0.08, 0.21, 0.38, 0.73, 1.92, 6.50, 23.18};
        expectedADiv = new double[]{56.95, 56.95, 56.95, 56.95, 56.95, 56.95, 56.95, 56.95};
        expectedABoundaryH = new double[]{-1.31, -1.31, -1.31, 3.99, -1.31, -1.31, -1.31, -1.31};
        expectedABoundaryF = new double[]{-1.31, -1.31, -1.26, -1.28, -1.31, -1.31, -1.31, -1.31};
        expectedLH = new double[]{36.88, 36.31, 35.60, 29.46, 33.62, 31.46, 25.63, 11.17};
        expectedLF = new double[]{36.88, 36.31, 35.56, 34.73, 33.62, 31.46, 25.63, 11.17};
        expectedLA = new double[]{10.68, 20.21, 26.98, 29.65, 33.62, 32.66, 26.63, 10.07};

        proPath = propDataOut.getPropagationPaths().get(1);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualDeltaDiffSR = proPath.aBoundaryH.deltaDiffSR;
        actualAGroundSO = proPath.aBoundaryH.aGroundSO;
        actualAGroundOR = proPath.aBoundaryH.aGroundOR;
        actualDeltaDiffSPrimeR = proPath.aBoundaryH.deltaDiffSPrimeR;
        actualDeltaDiffSRPrime = proPath.aBoundaryH.deltaDiffSRPrime;
        actualDeltaGroundSO = proPath.aBoundaryH.deltaGroundSO;
        actualDeltaGroundOR = proPath.aBoundaryH.deltaGroundOR;
        actualADiff = proPath.aBoundaryH.aDiff;
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualABoundaryH = proPath.double_aBoundaryH;
        actualABoundaryF = proPath.double_aBoundaryF;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);
        double[] reflexionLA = actualLA;

        double[] LA = sumDbArray(directLA,reflexionLA);

        assertDoubleArrayEquals("WH - reflexion", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - reflexion", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - reflexion", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - reflexion", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - reflexion", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - reflexion", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSR - reflexion", expectedDeltaDiffSR, actualDeltaDiffSR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSO - reflexion", expectedAGroundSO, actualAGroundSO, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundOR - reflexion", expectedAGroundOR, actualAGroundOR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeR - reflexion", expectedDeltaDiffSPrimeR, actualDeltaDiffSPrimeR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrime - reflexion", expectedDeltaDiffSRPrime, actualDeltaDiffSRPrime, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSO - reflexion", expectedDeltaGroundSO, actualDeltaGroundSO, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundOR - reflexion", expectedDeltaGroundOR, actualDeltaGroundOR, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiff - reflexion", expectedADiff, actualADiff, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AAtm - reflexion", expectedAAtm, actualAAtm, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("ADiv - reflexion", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - reflexion", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("ABoundaryF - reflexion", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - reflexion", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - reflexion", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - reflexion", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        assertArrayEquals(  new double[]{14.02, 23.84, 30.95, 33.86, 38.37, 38.27, 33.25, 15.28},LA, ERROR_EPSILON_VERY_LOW);
    }

    /**
     * TC18 - Screening and reflecting barrier on ground with spatially varying heights and
     * acoustic properties
     */
    @Test
    public void TC18() throws IOException {
        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC18_Direct", "TC18_Reflection");

        assertEquals(2, propDataOut.getPropagationPaths().size());

        assertEquals(2, propDataOut.getPropagationPaths().size());

        // Expected Values

        /* Table 193  Z Profile SR */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.0, 0.0));
        expectedZProfile.add(new Coordinate(112.41, 0.0));
        expectedZProfile.add(new Coordinate(178.84, 10));
        expectedZProfile.add(new Coordinate(194.16, 10));

        CutProfile cutProfile = propDataOut.getPropagationPaths().get(0).getCutProfile();
        List<Coordinate> result = cutProfile.computePts2DGround();
        assertZProfil(expectedZProfile, result);


        /* Table 194 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a      b    zs    zr      dp    Gp    Gp'
                {0.05, -2.83, 3.83, 4.16, 194.48, 0.51, 0.58}
        };

        /* Table 197 */
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a      b    zs    zr      dp    Gp    Gp'
                {0.0, 0.0, 1.0, 12.0, 85.16, 0.7, 0.86},
                {0.11, -12.03, 14.16, 1.29, 112.14, 0.37,  NaN}
        };




        // S-R (not the rayleigh segments SO OR)
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSRSegment());

        CnossosPath reflectionPath = propDataOut.getPropagationPaths().get(1);
        // Check reflexion mean planes
        assertPlanes(segmentsMeanPlanes1, reflectionPath.getSegmentList());

        assertEquals(4,  reflectionPath.getPointList().size());

        PointPath reflectionPoint = reflectionPath.getPointList().get(2);
        assertEquals(PointPath.POINT_TYPE.REFL, reflectionPoint.type);

        assert3DCoordinateEquals("Reflection position TC18 ",
                new Coordinate(129.75,12), reflectionPoint.coordinate, DELTA_COORDS);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});

        // Table 195
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.02, 0.11, 0.58, 2.94, 13.68};
        double[] expectedCfH = new double[]{201.47,218.76,217.74,102.89,14.04,1.71,0.34,0.07};
        double[] expectedAGroundH = new double[]{-1.26,-1.26,-1.26,2.12,-1.26,-1.26,-1.26,-1.26};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.16, 10.35};
        double[] expectedCfF = new double[]{199.62,214.14,225.42,131.91,22.89,2.42,0.46,0.10};
        double[] expectedAGroundF = new double[]{-1.26,-1.26,-1.26,-1.26,-1.26,-1.26,-1.26,-1.26};

        // Table 196
        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08,0.20,0.37,0.71,1.88,6.37,22.73};
        double[] expectedADiv = new double[]{56.78,56.78,56.78,56.78,56.78,56.78,56.78,56.78};
        double[] expectedABoundaryH = new double[]{-1.26,-1.26,-1.26,2.12,-1.26,-1.26,-1.26,-1.26};
        double[] expectedABoundaryF = new double[]{-1.26,-1.26,-1.26,-1.26,-1.26,-1.26,-1.26,-1.26};
        double[] expectedLH = new double[]{37.46,37.40,37.28,33.73,36.77,35.6,31.11,14.75};
        double[] expectedLF = new double[]{37.46,37.40,37.28,37.11,36.77,35.6,31.11,14.75};
        double[] expectedL = new double[]{37.46,37.40,37.28,35.74,36.77,35.6,31.11,14.75};
        double[] expectedLA = new double[]{11.26,21.30,28.68,32.54,36.77,36.80,32.11,13.65};

        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("WH - vertical plane", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - vertical plane", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - vertical plane", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - vertical plane", expectedCfF, actualCfF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - vertical plane", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - vertical plane", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);


        //Actual values - Reflexion left lateral
        proPath = propDataOut.getPropagationPaths().get(1);


        SegmentPath ReflectionSO = proPath.getSegmentList().get(0);
        SegmentPath ReflectionOnR = proPath.getSegmentList().get(1);

        assertEquals(0.7, ReflectionSO.gPath, ERROR_EPSILON_LOWEST);
        assertEquals(0.86, ReflectionSO.gPathPrime, ERROR_EPSILON_LOWEST);
        assertEquals(85.16, ReflectionSO.dp, ERROR_EPSILON_LOWEST);
        assertEquals(0.37, ReflectionOnR.gPath, ERROR_EPSILON_LOWEST);
        assertEquals(112.14, ReflectionOnR.dp, ERROR_EPSILON_LOWEST);

        // Table 198
        double[] expectedDeltaDiffSRH = new double[]{7.77,9.5,11.71,14.26,17.02,19.90,22.84,25.82};
        double[] expectedAGroundSOH = new double[]{-0.43,-0.43, -0.43, -0.43, -0.43, -0.43, -0.43, -0.43};
        double[] expectedAGroundORH = new double[]{-1.90,-1.90, -1.90, -1.90, -1.90, -1.90,-1.90,-1.90};
        double[] expectedDeltaDiffSPrimeRH = new double[]{8.54,10.51,12.90,15.56,18.38,21.30,24.26,27.25};
        double[] expectedDeltaDiffSRPrimeH = new double[]{8.53,10.49,12.88,15.54,18.36,21.27,24.24,27.22};
        double[] expectedDeltaGroundSOH = new double[]{-0.39,-0.38,-0.38,-0.37,-0.37,-0.37,-0.37,-0.37};
        double[] expectedDeltaGroundORH = new double[]{-1.75,-1.71,-1.68,-1.66,-1.65,-1.65,-1.64,-1.64};
        double[] expectedADiffH = new double[]{5.62,7.40,9.65,12.22,15.00,17.88,20.83,22.99};

        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        assertDoubleArrayEquals("AGroundSOH - Reflection left lateral", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORH - Reflection left lateral", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRH - Reflection left lateral", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - Reflection left lateral", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - Reflection left lateral", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH - Reflection left lateral", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORH - Reflection left lateral", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - Reflection left lateral", expectedADiffH, actualADiffH, ERROR_EPSILON_LOW);


        // Table 199
        double[] expectedDeltaDiffSRF = new double[]{7.22,8.76,10.80,13.24,15.93,18.77,21.69,24.65};
        double[] expectedAGroundSOF = new double[]{-0.43,-0.43, -0.43, -0.43, -0.43, -0.43, -0.43, -0.43};
        double[] expectedAGroundORF  = new double[]{-1.90,-1.90, -1.90, -1.90, -1.90, -1.90,-1.90,-1.90};
        double[] expectedDeltaDiffSPrimeRF = new double[]{8.09,9.93,12.22,14.82,17.61,20.51,23.46,26.44};
        double[] expectedDeltaDiffSRPrimeF = new double[]{8.08,9.91,12.20,14.80,17.59,20.48,23.43,26.41};
        double[] expectedDeltaGroundSOF = new double[]{-0.39,-0.38,-0.37,-0.36,-0.36,-0.35,-0.35,-0.35};
        double[] expectedDeltaGroundORF = new double[]{-1.74,-1.69,-1.64,-1.61,-1.60,-1.59,-1.58,-1.58};
        double[] expectedADiffF = new double[]{5.09,6.70,8.79,11.26,13.97,16.82,19.75,22.72};


        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        assertDoubleArrayEquals("DeltaDiffSRF - Reflection left lateral", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundSOF - Reflection left lateral", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundORF - Reflection left lateral", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - Reflection left lateral", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - Reflection left lateral", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOF - Reflection left lateral", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundORF - Reflection left lateral", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffF - Reflection left lateral", expectedADiffF, actualADiffF, ERROR_EPSILON_LOW);

        // Table 200
        expectedAlphaAtm = new double[]{0.1,0.4,1.0,1.9,3.7,9.7,32.8,116.9};
        expectedAAtm = new double[]{0.02,0.08,0.21,0.38,0.73,1.92,6.5,23.18};
        expectedADiv = new double[]{56.95,56.95,56.95,56.95,56.95,56.95,56.95,56.95};
        expectedABoundaryH = new double[]{5.62,7.40,9.65,12.22,15.00,17.88,20.83,22.99};
        expectedABoundaryF = new double[]{5.09,6.70,8.79,11.26,13.97,16.82,19.75,22.72};
        expectedLH = new double[]{27.49,27.6,24.64,21.23,17.32,12.27,3.49,-13.13};
        expectedLF = new double[]{27.71,28.30,25.5,22.19,18.34,13.33,4.57,-12.86};
        expectedL = new double[]{27.6,27.97,25.09,21.74,17.86,12.83,4.07,-13.00};
        expectedLA = new double[]{1.40,11.87,16.49,18.54,17.86,14.03,5.07,-14.10};
        double[] expectedRetroDiffH = new double[]{2.47, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00};
        double[] expectedRetroDiffF = new double[]{2.77, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00};

        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualABoundaryH = proPath.double_aBoundaryH;
        actualABoundaryF = proPath.double_aBoundaryF;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("AAtm - reflexion", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - reflexion", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - reflexion", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - reflexion", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("dLretrodif H - reflexion", expectedRetroDiffH, proPath.aRetroDiffH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("dLretrodif F - reflexion", expectedRetroDiffF, proPath.aRetroDiffF, ERROR_EPSILON_LOWEST);        assertDoubleArrayEquals("LH - reflexion", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - reflexion", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - reflexion", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - reflexion", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        assertArrayEquals(  new double[]{11.69,21.77,28.93,32.71,36.83,36.83,32.12,13.66},L, ERROR_EPSILON_VERY_LOW);
    }

    /**
     * TC18 - Screening and reflecting barrier on ground with spatially varying heights and
     * acoustic properties. This scenario is modified with the reflexion screen too low on one corner to have a valid
     * reflexion caused by height modification from the diffraction on the first wall
     */

    @Test
    public void TC18Altered() {
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

        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        //Out and computation settings
        Attenuation propDataOut = new Attenuation(true, true, attData, rayData);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        //Run computation
        computeRays.run(propDataOut);

        assertEquals(1, propDataOut.getPropagationPaths().size());

        // Expected Values

        /* Table 193  Z Profile SR */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.0, 0.0));
        expectedZProfile.add(new Coordinate(112.41, 0.0));
        expectedZProfile.add(new Coordinate(178.84, 10));
        expectedZProfile.add(new Coordinate(194.16, 10));

        CutProfile cutProfile = propDataOut.getPropagationPaths().get(0).getCutProfile();
        List<Coordinate> result = cutProfile.computePts2DGround();
        assertZProfil(expectedZProfile, result);


        /* Table 194 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a      b    zs    zr      dp    Gp    Gp'
                {0.05, -2.83, 3.83, 4.16, 194.48, 0.51, 0.58}
        };




        // S-R (not the rayleigh segments SO OR)
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSRSegment());

    }
    /**
     * TC19 - Complex object and 2 barriers on ground with spatially varying heights and
     * acoustic properties
     */
    //TODO : the error is due to the left VDiff path which z-path seems to be false in the document
    @Test
    public void TC19() throws IOException {
        //Out and computation settings
        Attenuation propDataOut =  computeCnossosPath("TC19_Direct", "TC19_Right");

        // Should be 3 but left path could not be defined
        // assertEquals(3, propDataOut.pathParameters.size());


        //Expected values

        /* Table 208 */
        List<Coordinate> expectedZProfile = Arrays.asList(
                new Coordinate(0.00, 0.00),
                new Coordinate(100.55, 0.00),
                new Coordinate(100.55, 7.00),
                new Coordinate(108.60, 7.00),
                new Coordinate(108.60, 0.0),
                new Coordinate(110.61, 0.0),
                new Coordinate(145.34, 5.31),
                new Coordinate(145.34, 14.00),
                new Coordinate(145.34, 5.31),
                new Coordinate(171.65, 9.34),
                new Coordinate(171.66, 14.50),
                new Coordinate(171.66, 9.34),
                new Coordinate(175.97, 10),
                new Coordinate(191.05, 10));

        /* Table 205 */
        List<Coordinate> expectedZProfileSO = Arrays.asList(
                new Coordinate(0.00, 0.00),
                new Coordinate(100.55, 0.00),
                new Coordinate(100.55, 7.00),
                new Coordinate(108.60, 7.00),
                new Coordinate(108.60, 0.0),
                new Coordinate(110.61, 0.0),
                new Coordinate(145.34, 5.31),
                new Coordinate(145.34, 14.00),
                new Coordinate(145.34, 5.31));

        List<Coordinate> expectedZProfileOR = Arrays.asList(
                new Coordinate(171.66, 9.34),
                new Coordinate(175.97, 10),
                new Coordinate(191.05, 10));

        List<Coordinate> expectedZProfileRight = Arrays.asList(
                new Coordinate(0, 0),
                new Coordinate(110.03, 0),
                new Coordinate(135.03, 3.85),
                new Coordinate(176.56, 10),
                new Coordinate(179.68, 10),
                new Coordinate(195.96, 10));

        List<Coordinate> expectedZProfileLeft = Arrays.asList(
                new Coordinate(0, 0),
                new Coordinate(93.44, 0),
                new Coordinate(93.44, 12.00),
                new Coordinate(109.23, 12.00),
                new Coordinate(109.23, 0),
                new Coordinate(111.26, 0),
                new Coordinate(166.88, 8.46),
                new Coordinate(177.08, 10.00),
                new Coordinate(192.38, 10));

        /* Table 209 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a      b     zs     zr      dp    Gp   Gp'
                {0.03, -1.09,  2.09, 10.89, 145.65, 0.57, 0.78},
                {0.02,  6.42,  4.76,  3.89,  19.38, 0.20,  NaN}
        };
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a      b    zs    zr      dp    Gp    Gp'
                {0.06, -2.92, 3.92, 5.66, 196.38, 0.50, 0.62}
        };
        double [][] segmentsMeanPlanes2 = new double[][]{
                //  a      b    zs    zr      dp    Gp    Gp'
                {0.06, -2.01, 3.00, 5.00, 192.81, 0.46, 0.55}
        };

        // Should be 3 but left path could not be defined
        assertEquals(2, propDataOut.getPropagationPaths().size());

        //Assertion
        assertZProfil(expectedZProfile, propDataOut.getPropagationPaths().get(0).getCutProfile().computePts2DGround());
        assertZProfil(expectedZProfileRight, propDataOut.getPropagationPaths().get(1).getCutProfile().computePts2DGround());
        // Error in ISO
        // The iso is making the ray do a diffraction on the horizontal edge of the building then a diffraction on
        // the last wall. The hull is ignoring the 12 meters building on the left side.
        // assertZProfil(expectedZProfileLeft, propDataOut.getPropagationPaths().get(2).getCutProfile().computePts2DGround());

        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());
        assertPlanes(segmentsMeanPlanes1, propDataOut.getPropagationPaths().get(1).getSRSegment());

        // Error in ISO
        // The iso is making the ray do a diffraction on the horizontal edge of the building then a diffraction on
        // the last wall. The hull is ignoring the 12 meters building on the left side.
        // assertZProfil(expectedZProfileLeft, propDataOut.getPropagationPaths().get(2).getCutProfile().computePts2DGround());
        // assertPlanes(segmentsMeanPlanes2, propDataOut.getPropagationPaths().get(2).getSRSegment());

        //Expected values
        //Path0 : vertical plane
        // Table 211
        double[] expectedDeltaDiffSRH = new double[]{6.67, 8.83, 11.68, 14.56, 17.43, 20.35, 23.31, 26.29};
        double[] expectedAGroundSOH = new double[]{-0.67, -0.67, -0.67, -0.67, -0.67, -0.67, -0.67, -0.67};
        double[] expectedAGroundORH = new double[]{-2.02, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRH = new double[]{7.61, 10.35, 13.58, 16.64, 19.61, 22.57, 25.55, 28.55};
        double[] expectedDeltaDiffSRPrimeH = new double[]{14.32, 18.51, 22.43, 25.77, 28.86, 31.89, 34.91, 37.92};
        double[] expectedDeltaGroundSOH = new double[]{-0.60, -0.56, -0.54, -0.53, -0.52, -0.52, -0.52, -0.52};
        double[] expectedDeltaGroundORH = new double[]{-0.90, -0.86, -0.77, -0.73, -0.71, -0.70, -0.70, -0.70};
        double[] expectedADiffH = new double[]{5.17, 7.41, 10.38, 13.30, 16.20, 19.13, 22.09, 23.78};

        double[] expectedDeltaDiffSRF = new double[]{5.89, 7.40, 9.69, 12.24, 14.94, 17.79, 20.66, 23.62};
        double[] expectedAGroundSOF = new double[]{-0.67, -0.67, -0.67, -0.67, -0.67, -0.67, -0.67, -0.67};
        double[] expectedAGroundORF = new double[]{-2.08, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRF = new double[]{7.00, 9.39, 12.40, 15.35, 18.27, 21.21, 24.17, 27.26};
        double[] expectedDeltaDiffSRPrimeF = new double[]{14.20, 18.38, 22.29, 25.63, 28.73, 31.75, 34.77, 37.78};
        double[] expectedDeltaGroundSOF = new double[]{-0.59, -0.53, -0.49, -0.47, -0.46, -0.45, -0.45, -0.45};
        double[] expectedDeltaGroundORF = new double[]{-0.86, -0.75, -0.62, -0.57, -0.55, -0.53, -0.53, -0.53};
        double[] expectedADiffF = new double[]{4.44, 6.12, 8.57, 11.19, 13.93, 16.77, 19.68, 22.64};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.70, 1.85, 6.28, 22.38};
        double[] expectedADiv = new double[]{56.64, 56.64, 56.64, 56.64, 56.64, 56.64, 56.64, 56.64};
        double[] expectedABoundaryH = new double[]{5.17, 7.41, 10.38, 13.30, 16.20, 19.13, 22.09, 23.78};
        double[] expectedABoundaryF = new double[]{4.44, 6.12, 8.57, 11.19, 13.93, 16.77, 19.68, 22.64};
        double[] expectedLH = new double[]{31.16, 28.87, 25.78, 22.69, 19.46, 15.38, 7.99, -9.81};
        double[] expectedLF = new double[]{31.89, 30.16, 27.59, 24.79, 21.73, 17.74, 10.40, -8.67};
        double[] expectedL = new double[]{31.54, 29.56, 26.78, 23.87, 20.74, 16.72, 9.36, -9.20};
        double[] expectedLA = new double[]{5.34, 13.46, 18.18, 20.67, 20.74, 17.92, 10.36, -10.30};

        //Actual values
        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        // Check gpath
        assertEquals(3, proPath.getSegmentList().size());
        assertEquals(0.57, proPath.getSegmentList().get(0).gPath, ERROR_EPSILON_LOWEST);
        assertEquals(0.2, proPath.getSegmentList().get(2).gPath, ERROR_EPSILON_LOWEST);


        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);
        double[] directLA = actualLA;

        //Assertions

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : lateral right
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.02, 0.13, 0.70, 3.46, 15.82};
        double[] expectedCfH = new double[]{204.75, 223.53, 212.65, 88.09, 10.70, 1.43, 0.29, 0.06};
        double[] expectedAGroundH = new double[]{-1.13, -1.13, -0.97, -0.65, -1.13, -1.13, -1.13, -1.13};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.40, 2.07, 9.97};
        double[] expectedCfF = new double[]{201.41, 215.78, 228.09, 136.20, 24.26, 2.53, 0.48, 0.10};
        double[] expectedAGroundF = new double[]{-1.13, -1.13, -1.13, -1.13, -1.13, -1.13, -1.13, -1.13};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.38, 0.72, 1.90, 6.44, 22.95};
        expectedADiv = new double[]{56.64, 56.64, 56.64, 56.64, 56.64, 56.64, 56.64, 56.64};
        expectedADiffH = new double[]{18.69, 22.74, 26.20, 29.33, 32.37, 35.39, 38.40, 41.41};
        expectedADiffF = new double[]{18.69, 22.74, 26.20, 29.33, 32.37, 35.39, 38.40, 41.41};
        expectedLH = new double[]{18.77, 14.67, 10.93, 7.29, 4.39, 0.20, -7.35, -26.88};
        expectedLF = new double[]{18.77, 14.67, 11.08, 7.77, 4.39, 0.20, -7.35, -26.88};
        expectedLA = new double[]{-7.43, -1.43, 2.41, 4.34, 4.39, 1.40, -6.35, -27.98};

        proPath = propDataOut.getPropagationPaths().get(1);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualADiffH = proPath.aDifH;
        actualADiffF = proPath.aDifF;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);
        double[] rightLA = actualLA;

        assertDoubleArrayEquals("WH - lateral right", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - lateral right", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - lateral right", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("WF - lateral right", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - lateral right", expectedCfF, actualCfF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("AGroundF - lateral right", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOW);

        assertDoubleArrayEquals("AlphaAtm - lateral right", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - lateral right", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - lateral right", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiffH - lateral right", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - lateral right", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - lateral right", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - lateral right", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path2 : lateral left
        // ISSUE path CNOSSOS error
        //expectedWH = new double[]{0.00, 0.00, 0.00, 0.02, 0.10, 0.51, 2.61, 12.30};
        //expectedCfH = new double[]{198.93, 214.98, 219.72, 113.73, 17.06, 1.95, 0.38, 0.08};
        //expectedAGroundH = new double[]{-1.35, -1.35, -1.35, 1.32, -1.35, -1.35, -1.35, -1.35};
        //expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.06, 0.34, 1.77, 8.65};
        //expectedCfF = new double[]{196.96, 209.53, 225.50, 149.30, 30.69, 3.06, 0.56, 0.12};
        //expectedAGroundF = new double[]{-1.35, -1.35, -1.35, -1.35, -1.35, -1.35, -1.35, -1.35};
        //
        //expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        //expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.71, 1.86, 6.32, 22.54};
        //expectedADiv = new double[]{56.64, 56.64, 56.64, 56.64, 56.64, 56.64, 56.64, 56.64};
        //expectedABoundaryH = new double[]{-1.35, -1.35, -1.35, 1.32, -1.35, -1.35, -1.35, -1.35};
        //expectedABoundaryF = new double[]{-1.35, -1.35, -1.35, -1.35, -1.35, -1.35, -1.35, -1.35};
        //expectedLH = new double[]{26.60, 24.10, 21.27, 15.57, 14.99, 10.86, 3.41, -15.80};
        //expectedLF = new double[]{26.60, 24.10, 21.27, 18.25, 14.99, 10.86, 3.41, -15.80};
        //actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        //expectedLA = new double[]{0.40, 8.00, 12.67, 13.91, 14.99, 12.06, 4.41, -9.38};

        //proPath = propDataOut.getPropagationPaths().get(2);
        //
        //actualWH = proPath.groundAttenuation.wH;
        //actualCfH = proPath.groundAttenuation.cfH;
        //actualAGroundH = proPath.groundAttenuation.aGroundH;
        //actualWF = proPath.groundAttenuation.wF;
        //actualCfF = proPath.groundAttenuation.cfF;
        //actualAGroundF = proPath.groundAttenuation.aGroundF;
        //
        //actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        //actualAAtm = proPath.aAtm;
        //actualADiv = proPath.aDiv;
        //actualABoundaryH = proPath.double_aBoundaryF;
        //actualABoundaryF = proPath.double_aBoundaryF;
        //actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        //actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        //actualLA = addArray(actualL, A_WEIGHTING);
        //double[] leftLA = actualLA;
        //
        double[] LA = sumDbArray(directLA,rightLA);

        //Different value with the TC because their z-profile left seems to be false, it follows the building top
        // border while it should not

        //assertDoubleArrayEquals("WH - lateral left", expectedWH, actualWH, ERROR_EPSILON_VERY_HIGH);
        //assertDoubleArrayEquals("CfH - lateral left", expectedCfH, actualCfH, ERROR_EPSILON_HIGHEST);
        //assertDoubleArrayEquals("AGroundH - lateral left", expectedAGroundH, actualAGroundH, ERROR_EPSILON_HIGH);
        //assertDoubleArrayEquals("WF - lateral left", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        //assertDoubleArrayEquals("CfF - lateral left", expectedCfF, actualCfF, ERROR_EPSILON_LOW);
        //assertDoubleArrayEquals("AGroundF - lateral left", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOW);
        //
        //assertDoubleArrayEquals("AlphaAtm - lateral left", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        //assertDoubleArrayEquals("AAtm - lateral left", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        //assertDoubleArrayEquals("ADiv - lateral left", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        //assertDoubleArrayEquals("ABoundaryH - lateral left", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_HIGHEST);
        //assertDoubleArrayEquals("ABoundaryF - lateral left", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_HIGHEST);
        //assertDoubleArrayEquals("LH - lateral left", expectedLH, actualLH, ERROR_EPSILON_HIGH);
        //assertDoubleArrayEquals("LF - lateral left", expectedLF, actualLF, ERROR_EPSILON_LOW);
        //assertDoubleArrayEquals("LA - lateral left", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        assertArrayEquals(  new double[]{6.72, 14.66, 19.34, 21.58, 21.84, 19.00, 11.42, -9.38},LA, ERROR_EPSILON_HIGH);

    }

    /**
     * TC20 - Ground with spatially varying heights and acoustic properties
     */
    @Test
    public void TC20() throws IOException {
        Attenuation propDataOut =  computeCnossosPath("TC20_Direct");

        //Expected values

        /* Table 221 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.0, 0.0));
        expectedZProfile.add(new Coordinate(110.34, 0.0));
        expectedZProfile.add(new Coordinate(175.54, 10));
        expectedZProfile.add(new Coordinate(190.59, 10));

        /* Table 230 S -> R TC21 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.06, -2.84, 3.84, 6.12, 191.02, 0.50, 0.65}
        };

        //Assertion
        assertZProfil(expectedZProfile, Arrays.asList(propDataOut.getPropagationPaths().get(0)
                .getSRSegment().getPoints2DGround()));
        assertPlanes(segmentsMeanPlanes0, propDataOut.getPropagationPaths().get(0).getSegmentList());

        //Expected values
        //Path0 : vertical plane
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.03, 0.15, 0.76, 3.76, 17.01};
        double[] expectedCfH = new double[]{199.65, 218.28, 203.92, 80.61, 9.46, 1.31, 0.27, 0.06};
        double[] expectedAGroundH = new double[]{-1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.08, 0.42, 2.16, 10.35};
        double[] expectedCfF = new double[]{195.99, 210.11, 221.65, 131.04, 23.06, 2.42, 0.46, 0.10};
        double[] expectedAGroundF = new double[]{-1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.70, 1.85, 6.26, 22.33};
        double[] expectedADiv = new double[]{56.62, 56.62, 56.62, 56.62, 56.62, 56.62, 56.62, 56.62};
        double[] expectedABoundaryH = new double[]{-1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06};
        double[] expectedABoundaryF = new double[]{-1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06};
        double[] expectedLH = new double[]{37.41, 37.35, 37.23, 37.06, 36.73, 35.59, 31.17, 15.10};
        double[] expectedLF = new double[]{37.41, 37.35, 37.23, 37.06, 36.73, 35.59, 31.17, 15.10};
        double[] expectedL = new double[]{37.41, 37.35, 37.23, 37.06, 36.73, 35.59, 31.17, 15.10};
        double[] expectedLA = new double[]{11.21, 21.25, 28.63, 33.86, 36.73, 36.79, 32.17, 14.00};

        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("WH - vertical plane", expectedWH, actualWH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("CfH - vertical plane", expectedCfH, actualCfH, ERROR_EPSILON_MEDIUM);
        assertDoubleArrayEquals("AGroundH - vertical plane", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - vertical plane", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - vertical plane", expectedCfF, actualCfF, ERROR_EPSILON_MEDIUM);
        assertDoubleArrayEquals("AGroundF - vertical plane", expectedAGroundF, actualAGroundF, ERROR_EPSILON_LOWEST);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        double[] diffL = diffArray(expectedL, actualL);
        double[] diffLa = diffArray(expectedLA, actualLA);
        double[] valL = getMaxValeurAbsolue(diffL);
        double[] valLA = getMaxValeurAbsolue(diffLa);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});

        assertArrayEquals(  new double[]{11.21,21.25,28.63,33.86,36.73,36.79,32.17,14},L, ERROR_EPSILON_VERY_LOW);
    }

    /**
     * TC21 - Building on ground with spatially varying heights and acoustic properties
     * ERROR_EPSILON_MEDIUM because we add favorable contribution for left and right paths but not in the reference document
     */
    @Test
    public void TC21() throws IOException {
        Attenuation propDataOut =  computeCnossosPath("TC21_Direct", "TC21_Right", "TC21_Left");

        assertEquals(3, propDataOut.getPropagationPaths().size());

        // Test R-CRIT table 235
        Coordinate D = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).r;
        Coordinate Sp = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).sPrime;
        Coordinate Rp = propDataOut.getPropagationPaths().get(0).getSegmentList().get(1).rPrime ;

        double deltaD = propDataOut.getPropagationPaths().get(0).getSegmentList().get(0).d +
                D.distance(propDataOut.getPropagationPaths().get(0).getPointList().get(2).coordinate)
                - propDataOut.getPropagationPaths().get(0).getSRSegment().d;
        double deltaDE = Sp.distance(D) + D.distance(Rp) - Sp.distance(Rp);
        List<Integer> res1 = new ArrayList<>(3) ;
        List<Integer> res2 = new ArrayList<>(3);

        for(int f : propDataOut.inputData.freq_lvl) {
            if(deltaD > -(340./f) / 20) {
                res1.add(1);
            }
            if (!(deltaD > (((340./f) / 4) - deltaDE))){
                res2.add(0);
            }
        }
        //Expected values

        /* Table 228 */
        List<Coordinate> expectedZProfileSR = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(110.34, 0.0),
                new Coordinate(146.75, 5.58),
                new Coordinate(146.75, 11.50),
                new Coordinate(147.26, 11.50),
                new Coordinate(147.26, 5.66),
                new Coordinate(175.54, 10),
                new Coordinate(190.59, 10));


        /* Table 225 */
        List<Coordinate> expectedZProfileSO = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(110.34, 0.0),
                new Coordinate(146.75, 5.58));

        List<Coordinate> expectedZProfileOR = Arrays.asList(
                new Coordinate(146.75, 11.50),
                new Coordinate(147.26, 11.50),
                new Coordinate(147.26, 5.66),
                new Coordinate(175.54, 10),
                new Coordinate(190.59, 10));

        List<Coordinate> expectedZProfileRight = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(110.33, 0.0),
                new Coordinate(147.10, 5.64),
                new Coordinate(175.54, 10.0),
                new Coordinate(190.59, 10.0));

        List<Coordinate> expectedZProfileLeft = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(114.0, 0.0),
                new Coordinate(146.72, 4.86),
                new Coordinate(183.89, 10.0),
                new Coordinate(200.57, 10.0));

        /* Table 229 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.02, -1.04, 2.04, 9.07, 146.96, 0.60, 0.77},
                {0.10, -8.64, 5.10, 3.12, 43.87, 0.20, NaN}
        };

        /* Table 230  S -> R */
        double [][] segmentsMeanPlanes1 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.06, -2.84, 3.84, 6.12, 191.02, 0.5, 0.65}
        };

        //Assertion Direct
        CnossosPath directPath = propDataOut.getPropagationPaths().get(0);
        assertZProfil(expectedZProfileSR, Arrays.asList(directPath.getSRSegment().getPoints2DGround()), 0.01);
        assertZProfil(expectedZProfileSO, Arrays.asList(directPath.getSegmentList().get(0).getPoints2DGround()),
                0.01);
        assertZProfil(expectedZProfileOR, Arrays.asList(
                        directPath.getSegmentList().get(directPath.getSegmentList().size() - 1).getPoints2DGround()),
                0.01);
        assertPlanes(segmentsMeanPlanes1, directPath.getSRSegment());
        assertPlanes(segmentsMeanPlanes0,directPath.getSegmentList());

        //Assertion Right
        CnossosPath rightPath = propDataOut.getPropagationPaths().get(1);
        assertZProfil(expectedZProfileRight, Arrays.asList(rightPath.getSRSegment().getPoints2DGround()), 0.01);

        //Assertion Left
        CnossosPath leftPath = propDataOut.getPropagationPaths().get(2);
        assertZProfil(expectedZProfileLeft, Arrays.asList(leftPath.getSRSegment().getPoints2DGround()), 0.01);

        //Expected values
        //Path0 : vertical plane
        double[] expectedWH = new double[]{NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN};
        double[] expectedCfH = new double[]{NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN};
        double[] expectedAGroundH = new double[]{NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN};
        double[] expectedWF = new double[]{0.00, 0.00, NaN, 0.01, 0.08, 0.42, 2.15, 10.33};
        double[] expectedCfF = new double[]{195.98, 210.08, NaN, 131.26, 23.14, 2.43, 0.46, 0.10};
        double[] expectedAGroundF = new double[]{-1.06, -1.06, NaN, -1.06, -1.06, -1.06, -1.06, -1.06};

        double[] expectedDeltaDiffSRH = new double[]{4.81, 4.85, 4.92, 5.06, 5.34, 5.84, 6.69, 8.02};
        double[] expectedAGroundSOH = new double[]{-0.70, -0.70, -0.70, -0.70, -0.70, -0.70, -0.70, -0.70};
        double[] expectedAGroundORH = new double[]{-0.47, -1.57, -2.41, -2.41, -2.41, -2.41, -2.41, -2.41};
        double[] expectedDeltaDiffSPrimeRH = new double[]{5.08, 5.37, 5.89, 6.78, 8.15, 10.03, 12.34, 14.95};
        double[] expectedDeltaDiffSRPrimeH = new double[]{7.81, 9.55, 11.77, 14.33, 17.09, 19.97, 22.92, 25.89};
        double[] expectedDeltaGroundSOH = new double[]{-0.68, -0.66, -0.63, -0.58, -0.51, -0.44, -0.37, -0.32};
        double[] expectedDeltaGroundORH = new double[]{-0.33, -0.95, -1.18, -0.91, -0.69, -0.53, -0.42, -0.35};
        double[] expectedADiffH = new double[]{3.80, 3.24, 3.11, 3.58, 4.13, 4.87, 5.90, 7.35};

        double[] expectedDeltaDiffSRF = new double[]{0.00, 0.00, 0.64, 0.00, 0.00, 0.00, 0.00, 0.00};
        double[] expectedAGroundSOF = new double[]{NaN, NaN, -0.71, NaN, NaN, NaN, NaN, NaN};
        double[] expectedAGroundORF = new double[]{NaN, NaN, -2.40, NaN, NaN, NaN, NaN, NaN};
        double[] expectedDeltaDiffSPrimeRF = new double[]{NaN, NaN, 2.86, NaN, NaN, NaN, NaN, NaN};
        double[] expectedDeltaDiffSRPrimeF = new double[]{NaN, NaN, 10.88, NaN, NaN, NaN, NaN, NaN};
        double[] expectedDeltaGroundSOF = new double[]{NaN, NaN, -0.55, NaN, NaN, NaN, NaN, NaN};
        double[] expectedDeltaGroundORF = new double[]{NaN, NaN, -0.81, NaN, NaN, NaN, NaN, NaN};
        double[] expectedADiffF = new double[]{NaN, NaN, -0.72, NaN, NaN, NaN, NaN, NaN};

        // Table 238
        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.70, 1.85, 6.26, 22.33};
        double[] expectedADiv = new double[]{56.62, 56.62, 56.62, 56.62, 56.62, 56.62, 56.62, 56.62};
        double[] expectedABoundaryH = new double[]{3.80, 3.24, 3.11, 3.58, 4.13, 4.87, 5.90, 7.35};
        double[] expectedABoundaryF = new double[]{-1.06, -1.06, -0.72, -1.06, -1.06, -1.06, -1.06, -1.06};
        double[] expectedLH = new double[]{32.56, 33.06, 33.07, 32.43, 31.54, 29.66, 24.22, 6.70};
        double[] expectedLF = new double[]{37.41, 37.36, 36.90, 37.07, 36.74, 35.56, 31.18, 15.11};
        double[] expectedL = new double[]{35.63, 35.72, 35.39, 35.34, 34.88, 33.57, 28.96, 12.68};
        double[] expectedLA = new double[]{9.43, 19.62, 26.79, 32.14, 34.88, 34.77, 29.96, 11.58};

        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("WH - vertical plane", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - vertical plane", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - vertical plane", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - vertical plane", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - vertical plane", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - vertical plane", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);


        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : lateral right
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.03, 0.15, 0.76, 3.76, 17.00};
        expectedCfH = new double[]{199.64, 218.28, 203.93, 80.63, 9.47, 1.31, 0.27, 0.06};
        expectedAGroundH = new double[]{-1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06, -1.06};
        expectedWF = new double[]{NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN};
        expectedCfF = new double[]{NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN};
        expectedAGroundF = new double[]{NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.02, 0.08, 0.20, 0.37, 0.70, 1.85, 6.26, 22.33};
        expectedADiv = new double[]{56.62, 56.62, 56.62, 56.62, 56.62, 56.62, 56.62, 56.62};
        expectedLH = new double[]{32.63, 32.56, 32.43, 32.22, 31.82, 30.53, 25.85, 9.29};
        expectedLA = new double[]{3.42, 13.45, 20.82, 26.01, 28.81, 28.72, 23.84, 5.18};

        proPath = propDataOut.getPropagationPaths().get(1);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);

        assertDoubleArrayEquals("WH - lateral right", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - lateral right", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - lateral right", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - lateral right", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - lateral right", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - lateral right", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - lateral right", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - lateral right", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - lateral right", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - lateral right", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        //assertDoubleArrayEquals("LA - lateral right", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path2 : lateral left
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.02, 0.13, 0.7, 3.48, 15.89};
        expectedCfH = new double[]{209.75, 229.14, 216.34, 87.81, 10.49, 1.42, 0.29, 0.06};
        expectedAGroundH = new double[]{-1.12, -1.12, -1.02, -0.79, -1.12, -1.12, -1.12, -1.12};
        expectedWF = new double[]{NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN};
        expectedCfF = new double[]{NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN};
        expectedAGroundF = new double[]{NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.02, 0.08, 0.21, 0.39, 0.74, 1.94, 6.59, 23.49};
        expectedADiv = new double[]{56.62, 56.62, 56.62, 56.62, 56.62, 56.62, 56.62, 56.62};
        expectedLH = new double[]{18.62, 15.68, 12.48, 9.08, 6.07, 1.86, -5.79, -25.71};
        expectedLA = new double[]{-10.59, -3.44, 0.87, 2.87, 3.06, 0.05, -7.81, -29.82};

        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);

        assertDoubleArrayEquals("WH - lateral left", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - lateral left", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - lateral left", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF - lateral left", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - lateral left", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - lateral left", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - lateral left", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - lateral left", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - lateral left", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - lateral left", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        //assertDoubleArrayEquals("LA - lateral left", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        double[] LA = addArray(addArray(propDataOut.receiversAttenuationLevels.getFirst().value, SOUND_POWER_LEVELS),
                A_WEIGHTING);
        assertArrayEquals(new double[]{10.44, 20.58, 27.78, 33.09, 35.84, 35.73, 30.91, 12.48},LA, ERROR_EPSILON_MEDIUM);
    }

    /**
     * TC22 - Building with receiver backside on ground with spatially varying heights and
     * acoustic properties
     */
    @Test
    public void TC22() throws IOException {
        Attenuation propDataOut =  computeCnossosPath("TC22_Direct", "TC22_Right", "TC22_Left");

        // Expected Values

        /* Table 248 */
        List<Coordinate> expectedZProfile = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(110.39, 0.0),
                new Coordinate(169.60, 9.08),
                new Coordinate(169.61, 20),
                new Coordinate(177.63, 20),
                new Coordinate(177.64, 10),
                new Coordinate(177.68, 10));

        /* Table 245 */
        List<Coordinate> expectedZProfileSO1 = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(110.39, 0.0),
                new Coordinate(169.60, 9.08));


        List<Coordinate> expectedZProfileOnR = Arrays.asList(
                new Coordinate(177.64, 10),
                new Coordinate(177.68, 10));

        List<Coordinate> expectedZProfileRight = Arrays.asList(
                new Coordinate(0, 0),
                new Coordinate(110.04, 0),
                new Coordinate(175.06, 10),
                new Coordinate(187.07, 10),
                new Coordinate(193.08, 10),
                new Coordinate(203.80, 10));

        List<Coordinate> expectedZProfileLeft = Arrays.asList(
                new Coordinate(0, 0),
                new Coordinate(111.29, 0),
                new Coordinate(170.99, 9.08),
                new Coordinate(176.99, 10),
                new Coordinate(188.99, 10),
                new Coordinate(195.00, 10),
                new Coordinate(206.14, 10));

        /* Table 249 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.04, -2.06, 3.06, 14.75, 170.26, 0.54, 0.79},
                {0.0, 10, 10, 4.00, 0.05, 0.20, NaN}
        };
        double [][] SRRightMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.06, -3.05, 4.04, 4.93, 204.22, 0.48, 0.58}
        };
        double [][] SRLeftMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.06, -3.05, 4.04, 4.93, 206.55, 0.48, 0.58}
        };

        // Must have direct path + diffraction left + diffraction right
        assertEquals(3, propDataOut.getPropagationPaths().size());

        CnossosPath directPropagationPath = propDataOut.getPropagationPaths().get(0);
        SegmentPath SRSegment = directPropagationPath.getSRSegment();

        // Asserts
        // SR
        assertZProfil(expectedZProfile, Arrays.asList(SRSegment.getPoints2DGround()));

        // SO1
        assertZProfil(expectedZProfileSO1,
                Arrays.asList(directPropagationPath.getSegmentList().get(0).getPoints2DGround()));

        // OnR
        assertZProfil(expectedZProfileOnR,
                Arrays.asList(directPropagationPath.getSegmentList().get(
                        directPropagationPath.getSegmentList().size() - 1).getPoints2DGround()));

        assertPlanes(segmentsMeanPlanes0, directPropagationPath.getSegmentList());

        // Check diffraction on horizontal plane
        CnossosPath rightPropagationPath = propDataOut.getPropagationPaths().get(1);
        assertZProfil(expectedZProfileRight,
                Arrays.asList(rightPropagationPath.getSRSegment().getPoints2DGround()));
        assertPlanes(SRRightMeanPlanes0, rightPropagationPath.getSRSegment());

        CnossosPath leftPropagationPath = propDataOut.getPropagationPaths().get(2);
        assertZProfil(expectedZProfileLeft,
                Arrays.asList(leftPropagationPath.getSRSegment().getPoints2DGround()));
        assertPlanes(SRLeftMeanPlanes0, leftPropagationPath.getSRSegment());

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{17.34, 20.76, 25.01, 29.48, 33.28, 36.55, 39.63, 42.66};
        double[] expectedAGroundSOH = new double[]{-0.64, -0.64, -0.64, -0.64, -0.64, -0.64, -0.64, -0.64};
        double[] expectedAGroundORH = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRH = new double[]{17.49, 20.91, 25.17, 29.64, 33.43, 36.70, 39.78, 42.81};
        double[] expectedDeltaDiffSRPrimeH = new double[]{20.79, 24.29, 28.58, 33.06, 36.87, 40.14, 43.22, 46.25};
        double[] expectedDeltaGroundSOH = new double[]{-0.63, -0.63, -0.63, -0.63, -0.63, -0.63, -0.63, -0.63};
        double[] expectedDeltaGroundORH = new double[]{-1.68, -1.67, -1.66, -1.66, -1.66, -1.66, -1.66, -1.66};
        double[] expectedADiffH = new double[]{15.03, 18.46, 22.71, 22.71, 22.71, 22.71, 22.71, 22.71};

        double[] expectedDeltaDiffSRF = new double[]{17.33, 20.75, 25.00, 29.47, 33.27, 36.54, 39.62, 42.65};
        double[] expectedAGroundSOF = new double[]{-0.64, -0.64, -0.64, -0.64, -0.64, -0.64, -0.64, -0.64};
        double[] expectedAGroundORF = new double[]{-2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        double[] expectedDeltaDiffSPrimeRF = new double[]{17.48, 20.90, 25.16, 29.63, 33.43, 36.70, 39.78, 42.80};
        double[] expectedDeltaDiffSRPrimeF = new double[]{20.79, 24.28, 28.58, 33.06, 36.86, 40.13, 43.21, 46.24};
        double[] expectedDeltaGroundSOF = new double[]{-0.63, -0.63, -0.63, -0.63, -0.63, -0.63, -0.63, -0.63};
        double[] expectedDeltaGroundORF = new double[]{-1.68, -1.67, -1.66, -1.66, -1.66, -1.66, -1.66, -1.66};
        double[] expectedADiffF = new double[]{15.02, 18.45, 22.71, 22.71, 22.71, 22.71, 22.71, 22.71};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.02, 0.07, 0.19, 0.34, 0.65, 1.72, 5.84, 20.82};
        double[] expectedADiv = new double[]{56.02, 56.02, 56.02, 56.02, 56.02, 56.02, 56.02, 56.02};
        double[] expectedABoundaryH = new double[]{15.03, 18.46, 22.71, 22.71, 22.71, 22.71, 22.71, 22.71};
        double[] expectedABoundaryF = new double[]{15.02, 18.45, 22.71, 22.71, 22.71, 22.71, 22.71, 22.71};
        double[] expectedLH = new double[]{21.93, 18.45, 14.09, 13.93, 13.62, 12.55, 8.43, -6.55};
        double[] expectedLF = new double[]{21.94, 18.46, 14.09, 13.93, 13.62, 12.55, 8.43, -6.55};
        double[] expectedL = new double[]{21.94, 18.46, 14.09, 13.93, 13.62, 12.55, 8.43, -6.55};
        double[] expectedLA = new double[]{-4.26, 2.36, 5.49, 10.73, 13.62, 13.75, 9.43, -7.65};

        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);


        double[] diffL = diffArray(expectedL, actualL);
        double[] diffLa = diffArray(expectedLA, actualLA);
        double[] valL = getMaxValeurAbsolue(diffL);
        double[] valLA = getMaxValeurAbsolue(diffLa);

        //Path1 : lateral right

        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.02, 0.11, 0.60, 3.00, 13.93};
        double[] expectedCfH = new double[]{212.03, 230.71, 226.18, 101.93, 13.28, 1.67, 0.33, 0.07};
        double[] expectedAGroundH = new double[]{-1.25, -1.25, -1.03, 0.77, -1.25, -1.25, -1.25, -1.25};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.07, 0.38, 1.96, 9.49};
        double[] expectedCfF = new double[]{209.34, 224.10, 237.46, 143.50, 25.94, 2.69, 0.51, 0.11};
        double[] expectedAGroundF = new double[]{-1.25, -1.17, -1.25, -1.25, -1.25, -1.25, -1.25, -1.25};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.02, 0.08, 0.21, 0.39, 0.75, 1.97, 6.70, 23.88};
        expectedADiv = new double[]{56.02, 56.02, 56.02, 56.02, 56.02, 56.02, 56.02, 56.02};
        expectedLH = new double[]{15.12, 11.76, 7.43, 0.88, -1.57, -6.24, -14.10, -34.33};
        expectedLF = new double[]{15.12, 11.69, 7.64, 2.90, -1.57, -6.24, -14.10, -34.33};

        proPath = propDataOut.getPropagationPaths().get(1);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        assertDoubleArrayEquals("WH - lateral right", expectedWH, actualWH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("CfH - lateral right", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH - lateral right", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF - lateral right", expectedWF, actualWF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("CfF - lateral right", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - lateral right", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - lateral right", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - lateral right", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - lateral right", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - lateral right", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - lateral right", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        //Path2 : lateral left
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.02, 0.11, 0.59, 2.96, 13.76};
        expectedCfH = new double[]{214.41, 233.28, 228.92, 103.46, 13.51, 1.70, 0.34, 0.07};
        expectedAGroundH = new double[]{-1.26, -1.26, -1.05, 0.86, -1.26, -1.26, -1.26, -1.26};
        expectedWF = new double[]{0.00, 0.00, 0.00, 0.01, 0.07, 0.38, 1.96, 9.49};
        expectedCfF = new double[]{211.78, 226.80, 240.03, 144.13, 25.83, 2.69, 0.51, 0.11};
        expectedAGroundF = new double[]{-1.26, -1.18, -1.26, -1.26, -1.26, -1.26, -1.26, -1.26};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.03, 0.08, 0.22, 0.40, 0.76, 2.00, 6.77, 24.16};
        expectedADiv = new double[]{56.02, 56.02, 56.02, 56.02, 56.02, 56.02, 56.02, 56.02};
        expectedLH = new double[]{13.40, 8.86, 4.40, -1.13, -2.50, -6.78, -14.58, -34.97};
        expectedLF = new double[]{13.40, 8.78, 4.61, 0.99, -2.50, -6.78, -14.58, -34.97};

        proPath = propDataOut.getPropagationPaths().get(2);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualWF = proPath.groundAttenuation.wF;
        actualCfF = proPath.groundAttenuation.cfF;
        actualAGroundF = proPath.groundAttenuation.aGroundF;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);

        assertDoubleArrayEquals("WH - lateral left", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - lateral left", expectedCfH, actualCfH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH - lateral left", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("WF - lateral left", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - lateral left", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - lateral left", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - lateral left", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - lateral left", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - lateral left", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - lateral left", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - lateral left", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});
        assertArrayEquals(  new double[]{-2.96,3.56,6.73,11.17,13.85,13.86,9.48,-7.64},L, ERROR_EPSILON_VERY_LOW);

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
        Double[] expectedDistance = new Double[]{14.21, 22.64, 23.98, 32.3};

        // base line for constructing expected results
        LineSegment leftTopographicVerticalLine = new LineSegment(new Coordinate(46.27, 36.28, 0),
                new Coordinate(59.6, -9.87, 0));
        LineSegment leftShortTopographicVerticalLine = new LineSegment(new Coordinate(54.68, 37.59, 5),
                new Coordinate(67.35, -6.83, 5));
        LineSegment rightShortTopographicVerticalLine = new LineSegment(new Coordinate(55.93, 37.93, 5),
                new Coordinate(68.68, -6.49, 5));
        LineSegment rightTopographicVerticalLine = new LineSegment(new Coordinate(63.71, 41.16, 0),
                new Coordinate(76.84, -5.28, 0));

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
     * TC23 – Two buildings behind an earth-berm on flat ground with homogeneous acoustic properties
     */
    /** Error Favorable condition delta_rPrimeH # delta_rPrimeF(CNOSSOS ?) */
    @Test
    public void TC23() throws IOException {
        Attenuation propDataOut =  computeCnossosPath("TC23_Direct");

        assertEquals(1, propDataOut.getPropagationPaths().size());


        // Expected Value

        /* Table 264 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.0, 0.0));
        expectedZProfile.add(new Coordinate(14.21, 0.0));
        expectedZProfile.add(new Coordinate(22.64, 5.0));
        expectedZProfile.add(new Coordinate(23.98, 5.0));
        expectedZProfile.add(new Coordinate(32.30, 0.0));
        expectedZProfile.add(new Coordinate(70.03, 0.0));

        /* Table 268 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.19, -1.17, 2.13, 1.94, 22.99, 0.37, 0.07},
                {-0.05, 2.89, 3.35, 4.73, 46.04, 0.18, NaN}
        };

        assertPlanes(segmentsMeanPlanes0,propDataOut.getPropagationPaths().get(0).getSegmentList());
        assertZProfil(expectedZProfile, Arrays.asList(propDataOut.getPropagationPaths().get(0)
                .getSRSegment().getPoints2DGround()), 0.01);

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{7.17, 8.69, 10.78, 13.46, 16.98, 21.34, 25.53, 29.05};
        double[] expectedAGroundSOH = new double[]{-1.02, -0.08, -0.73, -2.79, -2.79, -2.79, -2.79, -2.79};
        double[] expectedAGroundORH = new double[]{-0.28, -1.00, -2.46, -2.46, -2.46, -2.46, -2.46, -2.46};
        double[] expectedDeltaDiffSPrimeRH = new double[]{10.54, 12.93, 15.67, 18.78, 22.56, 27.04, 31.28, 34.81};
        double[] expectedDeltaDiffSRPrimeH = new double[]{10.86, 13.28, 16.05, 19.18, 22.97, 27.46, 31.70, 35.22};
        double[] expectedDeltaGroundSOH = new double[]{-0.71, -0.05, -0.42, -1.62, -1.58, -1.56, -1.55, -1.55};
        double[] expectedDeltaGroundORH = new double[]{-0.18, -0.60, -1.42, -1.36, -1.32, -1.30, -1.30, -1.29};
        double[] expectedADiffH = new double[]{6.28, 8.04, 8.93, 10.48, 14.09, 18.48, 22.15, 22.16};

        double[] expectedDeltaDiffSRF = new double[]{7.10, 8.59, 10.59, 12.99, 15.66, 18.49, 21.41, 24.37};
        double[] expectedAGroundSOF = new double[]{-0.96, -0.13, -1.09, -2.79, -2.79, -2.79, -2.79, -2.79};
        double[] expectedAGroundORF = new double[]{-0.54, -1.28, -2.39, -2.39, -2.39, -2.39, -2.39, -2.39};
        double[] expectedDeltaDiffSPrimeRF = new double[]{10.51, 12.87, 15.53, 18.36, 21.27, 24.23, 27.22, 30.22};
        double[] expectedDeltaDiffSRPrimeF = new double[]{10.86, 13.27, 15.96, 18.81, 21.73, 24.70, 27.68, 30.68};
        double[] expectedDeltaGroundSOF = new double[]{-0.66, -0.08, -0.64, -1.61, -1.57, -1.55, -1.54, -1.53};
        double[] expectedDeltaGroundORF = new double[]{-0.35, -0.77, -1.37, -1.30, -1.27, -1.25, -1.24, -1.24};
        double[] expectedADiffF = new double[]{6.09, 7.74, 8.59, 10.07, 12.82, 15.69, 18.63, 21.60};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.01, 0.03, 0.07, 0.14, 0.26, 0.68, 2.30, 8.19};
        double[] expectedADiv = new double[]{47.91, 47.91, 47.91, 47.91, 47.91, 47.91, 47.91, 47.91};
        double[] expectedABoundaryH = new double[]{6.28, 8.04, 8.93, 10.48, 14.09, 18.48, 22.15, 22.16};
        double[] expectedABoundaryF = new double[]{6.09, 7.74, 8.59, 10.07, 12.82, 15.69, 18.63, 21.60};
        double[] expectedLH = new double[]{38.80, 37.02, 36.08, 34.47, 30.75, 25.93, 20.64, 14.74};
        double[] expectedLF = new double[]{38.99, 37.32, 36.42, 34.88, 32.01, 28.72, 24.16, 15.29};
        double[] expectedL = new double[]{38.90, 37.17, 36.26, 34.68, 31.42, 27.54, 22.75, 15.02};
        double[] expectedLA = new double[]{12.70, 21.07, 27.66, 31.48, 31.42, 28.74, 23.75, 13.92};

        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);

        double[] diffL = diffArray(expectedL, actualL);
        double[] diffLa = diffArray(expectedLA, actualLA);
        double[] valL = getMaxValeurAbsolue(diffL);
        double[] valLA = getMaxValeurAbsolue(diffLa);


        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("ADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_VERY_HIGH);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_HIGH);
    }

    /**
     * TC24 – Two buildings behind an earth-berm on flat ground with homogeneous acoustic properties – receiver position modified
     */
    @Test
    public void TC24() throws IOException {
        Attenuation propDataOut =  computeCnossosPath("TC24_Direct", "TC24_Reflection");

        /* Table 279 */
        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.0, 0.0));
        expectedZProfile.add(new Coordinate(14.46, 0.0));
        expectedZProfile.add(new Coordinate(23.03, 5.0));
        expectedZProfile.add(new Coordinate(24.39, 5.0));
        expectedZProfile.add(new Coordinate(32.85, 0.0));
        expectedZProfile.add(new Coordinate(45.10, 0.0));
        expectedZProfile.add(new Coordinate(45.10, 6.0));
        expectedZProfile.add(new Coordinate(60.58, 6.0));
        expectedZProfile.add(new Coordinate(60.58, 0.0));
        expectedZProfile.add(new Coordinate(68.15, 0.0));


        /* Table 287 Z-Profile SO */
        List<Coordinate> expectedZProfileSO = new ArrayList<>();
        expectedZProfileSO.add(new Coordinate(0.0, 0.0));
        expectedZProfileSO.add(new Coordinate(14.13, 0.0));
        expectedZProfileSO.add(new Coordinate(22.51, 5.0));

        List<Coordinate> expectedZProfileOR = new ArrayList<>();
        expectedZProfileOR.add(new Coordinate(22.51, 5.0));
        expectedZProfileOR.add(new Coordinate(23.84, 5.0));
        expectedZProfileOR.add(new Coordinate(32.13, 0.0));
        expectedZProfileOR.add(new Coordinate(43.53, 0.0));
        expectedZProfileOR.add(new Coordinate(70.74, 0.0));

        List<Coordinate> result = propDataOut.getPropagationPaths().get(0).getCutProfile().computePts2DGround();
        assertZProfil(expectedZProfile,result);

        /* Table 280 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.18, -1.17, 2.13, 1.94, 23.37, 0.37, 0.07},
                {0.0, 0.0, 6.0, 4.0, 7.57, 0.00, NaN}
        };
        assertPlanes(segmentsMeanPlanes0,propDataOut.getPropagationPaths().get(0).getSegmentList());

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{10.18, 13.64, 16.95, 20.02, 23.02, 26.01, 29.00, 32.01};
        double[] expectedAGroundSOH = new double[]{-1.05, -0.09, -0.69, -2.79, -2.79, -2.79, -2.79, -2.79};
        double[] expectedAGroundORH = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        double[] expectedDeltaDiffSPrimeRH = new double[]{13.22, 17.14, 20.64, 23.79, 26.82, 29.83, 32.84, 35.85};
        double[] expectedDeltaDiffSRPrimeH = new double[]{18.53, 22.73, 26.34, 29.53, 32.59, 35.61, 38.62, 41.63};
        double[] expectedDeltaGroundSOH = new double[]{-0.75, -0.06, -0.46, -1.90, -1.90, -1.89, -1.89, -1.89};
        double[] expectedDeltaGroundORH = new double[]{-1.27, -1.17, -1.14, -1.12, -1.12, -1.11, -1.11, -1.11};
        double[] expectedADiffH = new double[]{8.16, 12.40, 15.36, 16.99, 20.00, 22.00, 22.00, 22.00};

        double[] expectedDeltaDiffSRF = new double[]{10.11, 13.55, 16.86, 19.93, 22.92, 25.91, 28.91, 31.91};
        double[] expectedAGroundSOF = new double[]{-0.98, -0.14, -1.05, -2.79, -2.79, -2.79, -2.79, -2.79};
        double[] expectedAGroundORF = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        double[] expectedDeltaDiffSPrimeRF = new double[]{13.19, 17.10, 20.60, 23.75, 26.79, 29.79, 32.80, 35.81};
        double[] expectedDeltaDiffSRPrimeF = new double[]{18.52, 22.72, 26.33, 29.52, 32.58, 35.60, 38.61, 41.62};
        double[] expectedDeltaGroundSOF = new double[]{-0.70, -0.10, -0.70, -1.89, -1.89, -1.88, -1.88, -1.88};
        double[] expectedDeltaGroundORF = new double[]{-1.27, -1.17, -1.13, -1.11, -1.11, -1.10, -1.10, -1.10};
        double[] expectedADiffF = new double[]{8.15, 12.29, 15.04, 16.92, 19.93, 22.02, 22.02, 22.02};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.01, 0.03, 0.07, 0.13, 0.25, 0.66, 2.24, 7.97};
        double[] expectedADiv = new double[]{47.68, 47.68, 47.68, 47.68, 47.68, 47.68, 47.68, 47.68};
        double[] expectedABoundaryH = new double[]{8.16, 12.40, 15.36, 16.99, 20.00, 22.00, 22.00, 22.00};
        double[] expectedABoundaryF = new double[]{8.15, 12.29, 15.04, 16.92, 19.93, 22.02, 22.02, 22.02};
        double[] expectedLH = new double[]{37.16, 32.90, 29.89, 28.20, 25.07, 22.67, 21.09, 15.35};
        double[] expectedLF = new double[]{37.17, 33.00, 30.22, 28.27, 25.14, 22.65, 21.07, 15.33};
        double[] expectedL = new double[]{37.16, 32.95, 30.06, 28.23, 25.11, 22.66, 21.08, 15.34};
        double[] expectedLA = new double[]{10.96, 16.85, 21.46, 25.03, 25.11, 23.86, 22.08, 14.24};

        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);
        double[] directLA = actualLA;
        double[] diffLV = diffArray(expectedL, actualL);


        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : reflexion
        expectedDeltaDiffSRH = new double[]{7.18, 8.71, 10.80, 13.49, 17.00, 21.36, 25.56, 29.08};
        expectedAGroundSOH = new double[]{-1.01, -0.08, -0.75, -2.79, -2.79, -2.79, -2.79, -2.79};
        expectedAGroundORH = new double[]{-0.27, -0.94, -2.47, -2.47, -2.47, -2.47, -2.47, -2.47};
        expectedDeltaDiffSPrimeRH = new double[]{10.58, 12.96, 15.71, 18.82, 22.59, 27.07, 31.31, 34.85};
        expectedDeltaDiffSRPrimeH = new double[]{10.80, 13.22, 15.98, 19.11, 22.88, 27.37, 31.61, 35.15};
        expectedDeltaGroundSOH = new double[]{-0.70, -0.05, -0.43, -1.62, -1.58, -1.56, -1.55, -1.55};
        expectedDeltaGroundORH = new double[]{-0.18, -0.57, -1.45, -1.38, -1.34, -1.32, -1.32, -1.32};
        expectedADiffH = new double[]{6.30, 8.09, 8.93, 10.49, 14.08, 18.48, 22.13, 22.14};

        expectedDeltaDiffSRF = new double[]{7.12, 8.61, 10.62, 13.02, 15.69, 18.52, 21.44, 24.40};
        expectedAGroundSOF = new double[]{-0.95, -0.13, -1.10, -2.79, -2.79, -2.79, -2.79, -2.79};
        expectedAGroundORF = new double[]{-0.52, -1.21, -2.40, -2.40, -2.40, -2.40, -2.40, -2.40};
        expectedDeltaDiffSPrimeRF = new double[]{10.55, 12.91, 15.57, 18.40, 21.32, 24.28, 27.26, 30.26};
        expectedDeltaDiffSRPrimeF = new double[]{10.81, 13.21, 15.90, 18.74, 21.66, 24.63, 27.61, 30.61};
        expectedDeltaGroundSOF = new double[]{-0.65, -0.08, -0.64, -1.61, -1.57, -1.55, -1.54, -1.53};
        expectedDeltaGroundORF = new double[]{-0.34, -0.73, -1.39, -1.33, -1.29, -1.27, -1.26, -1.26};
        expectedADiffF = new double[]{6.12, 7.80, 8.59, 10.08, 12.38, 15.70, 18.64, 21.61};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.01, 0.03, 0.07, 0.14, 0.26, 0.68, 2.32, 8.28};
        expectedADiv = new double[]{48.00, 48.00, 48.00, 48.00, 48.00, 48.00, 48.00, 48.00};
        expectedABoundaryH = new double[]{6.30, 8.09, 8.93, 10.49, 14.08, 18.48, 22.13, 22.14};
        expectedABoundaryF = new double[]{6.12, 7.80, 8.59, 10.08, 12.83, 15.70, 18.64, 21.61};
        expectedLH = new double[]{37.72, 35.91, 35.03, 33.41, 29.69, 24.87, 19.58, 13.62};
        expectedLF = new double[]{37.90, 36.20, 35.37, 33.81, 30.94, 27.64, 23.07, 14.14};
        expectedL = new double[]{37.81, 36.06, 35.20, 33.61, 30.36, 26.47, 21.67, 13.89};
        expectedLA = new double[]{11.61, 19.96, 26.60, 30.41, 30.36, 27.67, 22.67, 12.79};

        proPath = propDataOut.getPropagationPaths().get(1);

        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        actualADiffH = proPath.aBoundaryH.aDiff;

        actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        actualADiffF = proPath.aBoundaryF.aDiff;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualABoundaryH = proPath.double_aBoundaryH;
        actualABoundaryF = proPath.double_aBoundaryF;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("DeltaDiffSRH reflection plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH reflection plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH reflection plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH reflection plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH reflection plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundSOH reflection plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH reflection plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("actualADiffH reflection plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF reflection plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("AGroundSOF reflection plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("AGroundORF reflection plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF reflection plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF reflection plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("DeltaGroundSOF reflection plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("DeltaGroundORF reflection plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("ADiffF reflection plane", expectedADiffF, actualADiffF, ERROR_EPSILON_VERY_HIGH);

        assertDoubleArrayEquals("AlphaAtm reflection plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm reflection plane", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv reflection plane", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH reflection plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF reflection plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_HIGH);

        assertDoubleArrayEquals("LH - Reflection plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF reflection plane", expectedLF, actualLF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("L reflection plane", expectedL, actualL, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("LA reflection plane", expectedLA, actualLA, ERROR_EPSILON_HIGH);

    }

    /**
     * Replacement of the earth-berm by a barrier
     */
    @Test
    public void TC25() throws IOException {
        Attenuation propDataOut =  computeCnossosPath("TC25_Direct", "TC25_Right", "TC25_Left", "TC25_Reflection");

        // Should find Direct,Left/Right diffraction and one reflection
        assertEquals(4, propDataOut.getPropagationPaths().size());

        // Expected Values

        /* Table 300 */
        List<Coordinate> expectedZProfileSO = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(23.77, 0.0));

        List<Coordinate> expectedZProfileONR = Arrays.asList(
                new Coordinate(60.58, 0.0),
                new Coordinate(68.15, 0.0));

        List<Coordinate> expectedZProfileRight = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(27.10, 0.0),
                new Coordinate(81.02, 0.0),
                new Coordinate(89.03, 0.0),
                new Coordinate(101.05, 0.0));

        List<Coordinate> expectedZProfileLeft = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(23.64, 0.0),
                new Coordinate(70.83, 0.0));

        /* Table 301 */
        List<Coordinate> expectedZProfile = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(23.77, 0.0),
                new Coordinate(23.77, 5),
                new Coordinate(23.77, 0.0),
                new Coordinate(45.10, 0.0),
                new Coordinate(45.10, 6.0),
                new Coordinate(60.58, 6.0),
                new Coordinate(60.58, 0.0),
                new Coordinate(68.15, 0.0));

        /* Table 302 */
        Coordinate expectedSPrime = new Coordinate(0.00,-1.00);
        Coordinate expectedRPrime = new Coordinate(68.15,-4.0);

        assertMirrorPoint(expectedSPrime,expectedRPrime,propDataOut.getPropagationPaths().get(0)
                .getSegmentList().get(0).sPrime,propDataOut.getPropagationPaths().get(0).
                getSegmentList().get(propDataOut.getPropagationPaths().get(0).getSegmentList().size()-1).rPrime);


        /* Table 303 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.0, 0.0, 1.0, 5.0, 23.77, 0.0, 0.0},
                {0.0, 0.0, 6.0, 4.0, 7.57, 0.0, NaN}
        };

        /* Table 311 */
        double [][] segmentsMeanPlanesReflection = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.0, 0.0, 1.0, 5.0, 23.24, 0.0, 0.0},
                {0.0, 0.0, 5.0, 4.0, 47.49, 0.0, NaN}
        };

        CnossosPath directPath = propDataOut.getPropagationPaths().get(0);
        assertZProfil(expectedZProfile, Arrays.asList(directPath.getSRSegment().getPoints2DGround()));
        assertZProfil(expectedZProfileSO, Arrays.asList(directPath.getSegmentList().get(0).getPoints2DGround()));
        assertZProfil(expectedZProfileONR, Arrays.asList(directPath.getSegmentList().get(
                directPath.getSegmentList().size() - 1).getPoints2DGround()));
        assertPlanes(segmentsMeanPlanes0, directPath.getSegmentList());

        CnossosPath rightPath = propDataOut.getPropagationPaths().get(1);
        assertZProfil(expectedZProfileRight, Arrays.asList(rightPath.getSRSegment().getPoints2DGround()));


        CnossosPath leftPath = propDataOut.getPropagationPaths().get(2);
        assertZProfil(expectedZProfileLeft, Arrays.asList(leftPath.getSRSegment().getPoints2DGround()));


        CnossosPath reflectionPath = propDataOut.getPropagationPaths().get(3);
        assertPlanes(segmentsMeanPlanesReflection, reflectionPath.getSegmentList());

        //Expected values
        //Path0 : vertical plane
        double[] expectedDeltaDiffSRH = new double[]{10.09, 13.54, 16.87, 19.94, 22.94, 25.93, 28.93, 31.93};
        double[] expectedAGroundSOH = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        double[] expectedAGroundORH = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        double[] expectedDeltaDiffSPrimeRH = new double[]{11.48, 15.20, 18.63, 21.75, 24.77, 27.78, 30.78, 33.78};
        double[] expectedDeltaDiffSRPrimeH = new double[]{18.47, 22.70, 26.32, 29.52, 32.58, 35.60, 38.61, 41.62};
        double[] expectedDeltaGroundSOH = new double[]{-2.62, -2.55, -2.52, -2.51, -2.50, -2.50, -2.50, -2.50};
        double[] expectedDeltaGroundORH = new double[]{-1.27, -1.17, -1.13, -1.11, -1.11, -1.10, -1.10, -1.10};
        double[] expectedADiffH = new double[]{6.21, 9.83, 13.22, 16.32, 19.33, 21.40, 21.40, 21.40};

        double[] expectedDeltaDiffSRF = new double[]{10.03, 13.46, 16.78, 19.85, 22.84, 25.83, 28.83, 31.83};
        double[] expectedAGroundSOF = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        double[] expectedAGroundORF = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        double[] expectedDeltaDiffSPrimeRF = new double[]{11.43, 15.14, 18.57, 21.69, 24.71, 27.71, 30.72, 33.72};
        double[] expectedDeltaDiffSRPrimeF = new double[]{18.46, 22.69, 26.31, 29.51, 32.57, 35.59, 38.60, 41.61};
        double[] expectedDeltaGroundSOF = new double[]{-2.61, -2.54, -2.51, -2.50, -2.50, -2.49, -2.49, -2.49};
        double[] expectedDeltaGroundORF = new double[]{-1.26, -1.16, -1.12, -1.10, -1.10, -1.09, -1.09, -1.09};
        double[] expectedADiffF = new double[]{6.15, 9.76, 13.15, 16.24, 19.25, 21.41, 21.42, 21.42};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.01, 0.03, 0.07, 0.13, 0.25, 0.66, 2.24, 7.97};
        double[] expectedADiv = new double[]{47.68, 47.68, 47.68, 47.68, 47.68, 47.68, 47.68, 47.68};
        double[] expectedABoundaryH = new double[]{6.21, 9.83, 13.22, 16.32, 19.33, 21.40, 21.40, 21.40};
        double[] expectedABoundaryF = new double[]{6.15, 9.76, 13.15, 16.24, 19.25, 21.41, 21.42, 21.42};
        double[] expectedLH = new double[]{39.11, 35.47, 32.03, 28.87, 25.74, 23.27, 21.69, 15.95};
        double[] expectedLF = new double[]{39.16, 35.53, 32.11, 28.95, 25.82, 23.25, 21.67, 15.93};
        double[] expectedL = new double[]{39.13, 35.50, 32.07, 28.91, 25.78, 23.26, 21.68, 15.94};
        double[] expectedLA = new double[]{12.93, 19.40, 23.47, 25.71, 25.78, 24.46, 22.68, 14.84};

        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        double[] actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        double[] actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        double[] actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        double[] actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        double[] actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        double[] actualADiffH = proPath.aBoundaryH.aDiff;

        double[] actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        double[] actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        double[] actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        double[] actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        double[] actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        double[] actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        double[] actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        double[] actualADiffF = proPath.aBoundaryF.aDiff;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);
        double[] directLA = actualLA;
        double[] diffVerticalL = diffArray(expectedL,actualL);
        assertDoubleArrayEquals("DeltaDiffSRH - vertical plane", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - vertical plane", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - vertical plane", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - vertical plane", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - vertical plane", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundSOH - vertical plane", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - vertical plane", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("actualADiffH - vertical plane", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - vertical plane", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundSOF - vertical plane", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORF - vertical plane", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - vertical plane", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - vertical plane", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaGroundSOF - vertical plane", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORF - vertical plane", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiffF - vertical plane", expectedADiffF, actualADiffF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : lateral right

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.01, 0.04, 0.11, 0.19, 0.37, 0.98, 3.31, 11.82};
        expectedADiv = new double[]{47.68, 47.68, 47.68, 47.68, 47.68, 47.68, 47.68, 47.68};
        double[] expectedAGroundH = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        expectedLH = new double[]{20.84, 17.03, 13.68, 10.51, 7.31, 3.68, -1.66, -13.18};
        expectedLF = new double[]{20.84, 17.03, 13.68, 10.51, 7.31, 3.68, -1.66, -13.18};

        proPath = propDataOut.getPropagationPaths().get(1);

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);

        //Path2 : lateral left

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.01, 0.03, 0.07, 0.14, 0.26, 0.69, 2.32, 8.29};
        expectedADiv = new double[]{47.68, 47.68, 47.68, 47.68, 47.68, 47.68, 47.68, 47.68};
        expectedAGroundH = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        expectedLH = new double[]{34.73, 32.02, 29.13, 26.13, 23.04, 19.63, 14.99, 6.02};
        expectedLF = new double[]{34.73, 32.02, 29.13, 26.13, 23.04, 19.63, 14.99, 6.02};

        proPath = propDataOut.getPropagationPaths().get(2);

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualAGroundH = proPath.groundAttenuation.aGroundH;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("AlphaAtm - lateral right", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - lateral right", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - lateral right", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundH - lateral right", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - lateral right", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - lateral right", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);

        //Path3 : reflexion
        expectedDeltaDiffSRH = new double[]{7.11, 8.60, 10.60, 13.01, 15.68, 18.51, 21.42, 24.39};
        expectedAGroundSOH = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        expectedAGroundORH = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        expectedDeltaDiffSPrimeRH = new double[]{8.70, 10.71, 13.12, 15.80, 18.64, 21.56, 24.52, 27.51};
        expectedDeltaDiffSRPrimeH = new double[]{10.21, 12.52, 15.15, 17.95, 20.86, 23.82, 26.80, 29.80};
        expectedDeltaGroundSOH = new double[]{-2.56, -2.44, -2.34, -2.27, -2.23, -2.21, -2.20, -2.20};
        expectedDeltaGroundORH = new double[]{-2.20, -2.03, -1.90, -1.82, -1.78, -1.75, -1.74, -1.74};
        expectedADiffH = new double[]{2.34, 4.14, 6.37, 8.91, 11.66, 14.54, 17.48, 20.45};

        expectedDeltaDiffSRF = new double[]{7.05, 8.51, 10.49, 12.88, 15.54, 18.36, 21.28, 24.24};
        expectedAGroundSOF = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        expectedAGroundORF = new double[]{-3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00, -3.00};
        expectedDeltaDiffSPrimeRF = new double[]{8.66, 10.65, 13.06, 15.74, 18.57, 21.49, 24.45, 27.44};
        expectedDeltaDiffSRPrimeF = new double[]{10.18, 12.48, 15.11, 17.91, 20.82, 23.77, 26.76, 29.75};
        expectedDeltaGroundSOF = new double[]{-2.56, -2.43, -2.32, -2.26, -2.22, -2.20, -2.19, -2.18};
        expectedDeltaGroundORF = new double[]{-2.20, -2.02, -1.89, -1.81, -1.76, -1.74, -1.72, -1.72};
        expectedADiffF = new double[]{2.29, 4.07, 6.28, 8.82, 11.56, 14.43, 17.37, 20.34};

        expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        expectedAAtm = new double[]{0.01, 0.03, 0.07, 0.14, 0.26, 0.68, 2.32, 8.28};
        expectedADiv = new double[]{48.00, 48.00, 48.00, 48.00, 48.00, 48.00, 48.00, 48.00};
        expectedABoundaryH = new double[]{2.34, 4.14, 6.37, 8.91, 11.66, 14.54, 17.48, 20.45};
        expectedABoundaryF = new double[]{2.29, 4.07, 6.28, 8.82, 11.56, 14.43, 17.37, 20.34};
        expectedLH = new double[]{41.68, 39.86, 37.59, 34.98, 32.11, 28.81, 24.23, 15.30};
        expectedLF = new double[]{41.73, 39.93, 37.67, 35.08, 32.21, 28.92, 24.34, 15.41};
        expectedL = new double[]{41.70, 39.90, 37.63, 35.03, 32.16, 28.86, 24.29, 15.36};
        expectedLA = new double[]{15.5, 23.80, 29.03, 31.83, 32.16, 30.06, 25.29, 14.26};

        proPath = propDataOut.getPropagationPaths().get(3);

        actualDeltaDiffSRH = proPath.aBoundaryH.deltaDiffSR;
        actualAGroundSOH = proPath.aBoundaryH.aGroundSO;
        actualAGroundORH = proPath.aBoundaryH.aGroundOR;
        actualDeltaDiffSPrimeRH = proPath.aBoundaryH.deltaDiffSPrimeR;
        actualDeltaDiffSRPrimeH = proPath.aBoundaryH.deltaDiffSRPrime;
        actualDeltaGroundSOH = proPath.aBoundaryH.deltaGroundSO;
        actualDeltaGroundORH = proPath.aBoundaryH.deltaGroundOR;
        actualADiffH = proPath.aBoundaryH.aDiff;

        actualDeltaDiffSRF = proPath.aBoundaryF.deltaDiffSR;
        actualAGroundSOF = proPath.aBoundaryF.aGroundSO;
        actualAGroundORF = proPath.aBoundaryF.aGroundOR;
        actualDeltaDiffSPrimeRF = proPath.aBoundaryF.deltaDiffSPrimeR;
        actualDeltaDiffSRPrimeF = proPath.aBoundaryF.deltaDiffSRPrime;
        actualDeltaGroundSOF = proPath.aBoundaryF.deltaGroundSO;
        actualDeltaGroundORF = proPath.aBoundaryF.deltaGroundOR;
        actualADiffF = proPath.aBoundaryF.aDiff;

        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualABoundaryH = proPath.double_aBoundaryH;
        actualABoundaryF = proPath.double_aBoundaryF;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("DeltaDiffSRH - reflexion", expectedDeltaDiffSRH, actualDeltaDiffSRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundSOH - reflexion", expectedAGroundSOH, actualAGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORH - reflexion", expectedAGroundORH, actualAGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRH - reflexion", expectedDeltaDiffSPrimeRH, actualDeltaDiffSPrimeRH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("DeltaDiffSRPrimeH - reflexion", expectedDeltaDiffSRPrimeH, actualDeltaDiffSRPrimeH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundSOH - reflexion", expectedDeltaGroundSOH, actualDeltaGroundSOH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORH - reflexion", expectedDeltaGroundORH, actualDeltaGroundORH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("actualADiffH - reflexion", expectedADiffH, actualADiffH, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("DeltaDiffSRF - reflexion", expectedDeltaDiffSRF, actualDeltaDiffSRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundSOF - reflexion", expectedAGroundSOF, actualAGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundORF - reflexion", expectedAGroundORF, actualAGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSPrimeRF - reflexion", expectedDeltaDiffSPrimeRF, actualDeltaDiffSPrimeRF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaDiffSRPrimeF - reflexion", expectedDeltaDiffSRPrimeF, actualDeltaDiffSRPrimeF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("DeltaGroundSOF - reflexion", expectedDeltaGroundSOF, actualDeltaGroundSOF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("DeltaGroundORF - reflexion", expectedDeltaGroundORF, actualDeltaGroundORF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiffF - reflexion", expectedADiffF, actualADiffF, ERROR_EPSILON_VERY_LOW);

        assertDoubleArrayEquals("AlphaAtm - reflexion", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - reflexion", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - reflexion", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH - reflexion", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - reflexion", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - reflexion", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - reflexion", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - reflexion", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - reflexion", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});

        assertArrayEquals(new double[]{17.96, 25.65, 30.56, 33.22, 33.48, 31.52, 27.51, 17.80}, L, ERROR_EPSILON_LOWEST);
    }


    /**
     * TC26 – Road source with influence of retrodiffraction
     * Issue we compute and add favorable contribution, on reflexion path but not in test case reference
     * */
    @Test
    public void TC26() throws IOException {

        Attenuation propDataOut =  computeCnossosPath("TC26_Direct", "TC26_Reflection");

        assertEquals(2, propDataOut.getPropagationPaths().size());

        //Expected values
        //Path0 : vertical plane
        double[] expectedWH = new double[]{0.00, 0.00, 0.00, 0.00, 0.00, 0.02, 0.12, 0.65};
        double[] expectedCfH = new double[]{117.15, 117.59, 119.77, 127.98, 136.45, 84.82, 15.70, 1.58};
        double[] expectedAGroundH = new double[]{-2.54, -2.54, -2.54, -2.54, -2.54, -2.54, -2.54, -2.54};
        double[] expectedWF = new double[]{0.00, 0.00, 0.00, 0.00, 0.02, 0.13, 0.72, 3.73};
        double[] expectedCfF = new double[]{117.69, 120.15, 129.07, 135.46, 78.05, 13.23, 1.40, 0.27};
        double[] expectedAGroundF = new double[]{-2.54, -2.54, -2.54, -2.54, -2.54, -2.54, -1.89, -2.54};

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.01, 0.05, 0.12, 0.23, 0.43, 1.13, 3.84, 13.71};
        double[] expectedADiv = new double[]{52.39, 52.39, 52.39, 52.39, 52.39, 52.39, 52.39, 52.39};
        double[] expectedABoundaryH = new double[]{-2.54, -2.54, -2.54, -2.54, -2.54, -2.54, -2.54, -2.54};
        double[] expectedABoundaryF = new double[]{-2.54, -2.54, -2.54, -2.54, -2.54, -2.54, -1.89, -2.54};
        double[] expectedLH = new double[]{43.14, 43.10, 43.03, 42.92, 42.72, 42.02, 39.31, 29.44};
        double[] expectedLF = new double[]{43.14, 43.10, 43.03, 42.92, 42.72, 42.02, 38.65, 29.44};
        double[] expectedL = new double[]{43.14, 43.10, 43.03, 42.92, 42.72, 42.02, 38.99, 29.44};
        double[] expectedLA = new double[]{16.94, 27.00, 34.43, 39.72, 42.72, 43.22, 39.99, 28.34};

        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualWH = proPath.groundAttenuation.wH;
        double[] actualCfH = proPath.groundAttenuation.cfH;
        double[] actualAGroundH = proPath.groundAttenuation.aGroundH;
        double[] actualWF = proPath.groundAttenuation.wF;
        double[] actualCfF = proPath.groundAttenuation.cfF;
        double[] actualAGroundF = proPath.groundAttenuation.aGroundF;

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);
        double[] directLA = actualLA;
        double[] diffVerticalL = diffArray(expectedL,actualL);

        double[] diffL = diffArray(expectedL, actualL);
        double[] diffLa = diffArray(expectedLA, actualLA);
        double[] valL = getMaxValeurAbsolue(diffL);
        double[] valLA = getMaxValeurAbsolue(diffLa);

        assertDoubleArrayEquals("WH - vertical plane", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - vertical plane", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - vertical plane", expectedAGroundH, actualAGroundH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("WF - vertical plane", expectedWF, actualWF, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfF - vertical plane", expectedCfF, actualCfF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AGroundF - vertical plane", expectedAGroundF, actualAGroundF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_LOW);

        //Path1 : reflexion
        // Table 323
        expectedWH = new double[]{0.00, 0.00, 0.00, 0.00, 0.00, 0.02, 0.13, 0.69};
        expectedCfH = new double[]{121.79, 122.30, 124.78, 133.86, 141.12, 82.68, 14.13, 1.46};
        expectedAGroundH = new double[]{-2.53, -2.53, -2.53, -2.53, -2.53, -2.53, -2.53, -2.53};
        expectedAlphaAtm = new double[]{0.1, 0.4, 1., 1.9, 3.7, 9.7, 32.8, 116.9};
        expectedAAtm = new double[]{0.01, 0.05, 0.13, 0.24, 0.45, 1.18, 4.00, 14.25};
        expectedADiv = new double[]{52.72, 52.72, 52.72, 52.72, 52.72, 52.72, 52.72, 52.72};
        expectedABoundaryH = new double[]{-2.53, -2.53, -2.53, -2.53, -2.53, -2.53, -2.53, -2.53};
        expectedLH = new double[]{37.60, 37.10, 36.53, 35.94, 35.34, 34.57, 33.34, 25.54};
        expectedL = new double[]{34.59, 34.09, 33.53, 32.94, 32.33, 31.56, 30.33, 22.54};
        expectedLA = new double[]{8.39, 17.99, 24.93, 29.74, 32.33, 32.76, 31.33, 21.44};

        proPath = propDataOut.getPropagationPaths().get(1);

        actualWH = proPath.groundAttenuation.wH;
        actualCfH = proPath.groundAttenuation.cfH;
        actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualABoundaryH = proPath.double_aBoundaryH;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("WH - reflexion", expectedWH, actualWH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("CfH - reflexion", expectedCfH, actualCfH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AGroundH - reflexion", expectedAGroundH, actualAGroundH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AlphaAtm - reflexion", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("AAtm - reflexion", expectedAAtm, actualAAtm, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ADiv - reflexion", expectedADiv, actualADiv, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("ABoundaryH - reflexion", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LH - reflexion", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("L - reflexion", expectedL, actualL, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("LA - reflexion", expectedLA, actualLA, ERROR_EPSILON_HIGH);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});

        assertArrayEquals(  new double[]{17.50,27.52,34.89,40.14,43.10,43.59,40.55,29.15},L, ERROR_EPSILON_LOW);
    }


    /**
     * TC27 – Road source with influence of retrodiffraction
     * Issue with wrong agroundF for reflection path
     * */
    @Test
    public void TC27() throws IOException {
        Attenuation propDataOut =  computeCnossosPath("TC27_Direct", "TC27_Reflection");

        assertEquals(2, propDataOut.getPropagationPaths().size());

        /* Table 331 */
        Coordinate expectedSPrime =new Coordinate(0.01,-0.69);
        Coordinate expectedRPrime =new Coordinate(96.18,-4.0);

        /* Table 329 */
        double [][] segmentsMeanPlanesH = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.04, -0.57, 0.12, 0.35, 6.09, 0.17, 0.07},
                {0.0, 0.0, 0.0, 4.0, 90.10, 1.0, 1.0}
        };

        CnossosPath directPath = propDataOut.getPropagationPaths().get(0);

        assertPlanes(segmentsMeanPlanesH, directPath.getSegmentList());
        assertMirrorPoint(expectedSPrime,expectedRPrime,directPath.getSegmentList().get(0).sPrime,
                directPath.getSegmentList().get(directPath.getSegmentList().size()-1).rPrime);

        segmentsMeanPlanesH = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.03, -0.57, 0.12, 0.35, 6.65, 0.14, 0.08},
                {0, 0, 0, 4, 94.01, 1, NaN}
        };
        Coordinate expectedSPrimeSR =new Coordinate(0,0.22);
        Coordinate expectedRPrimeSR =new Coordinate(100.66,-3.89);
        Coordinate expectedSPrimeSO =new Coordinate(0.01,-0.69);
        Coordinate expectedRPrimeOR =new Coordinate(100.65,-4.0);

        CnossosPath reflectionPath = propDataOut.getPropagationPaths().get(1);

        assertPlanes(segmentsMeanPlanesH, reflectionPath.getSegmentList());

        SegmentPath sr = reflectionPath.getSRSegment();
        assertMirrorPoint(expectedSPrimeSR,expectedRPrimeSR,sr.sPrime,
                sr.rPrime);
        assertEquals(2, reflectionPath.getSegmentList().size());

        SegmentPath so = reflectionPath.getSegmentList().get(0);
        SegmentPath or = reflectionPath.getSegmentList().get(reflectionPath.getSegmentList().size() - 1);

        assertMirrorPoint(expectedSPrimeSO,expectedRPrimeOR,so.sPrime,
                or.rPrime);


        //Expected values
        //Path0 : vertical plane

        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.01, 0.04, 0.10, 0.19, 0.35, 0.93, 3.16, 11.25};
        double[] expectedADiv = new double[]{50.67, 50.67, 50.67, 50.67, 50.67, 50.67, 50.67, 50.67};
        double[] expectedABoundaryH = new double[]{2.05, 2.10, 2.21, 2.44, 6.08, 7.81, 7.70, 8.63};
        double[] expectedABoundaryF = new double[]{-0.69, -0.69, -0.69, -0.69, 4.08, 4.17, -0.79, -0.69};
        double[] expectedLH = new double[]{40.27, 40.19, 40.02, 39.71, 35.90, 33.59, 31.47, 22.45};
        double[] expectedLF = new double[]{43.01, 42.98, 42.92, 42.84, 37.90, 37.23, 39.96, 31.77};
        double[] expectedL = new double[]{41.85, 41.81, 41.71, 41.55, 37.01, 35.78, 37.53, 29.24};
        double[] expectedLA = new double[]{15.65, 25.71, 33.11, 38.35, 37.01, 36.98, 38.53, 28.14};

        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        double[] actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        double[] actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        double[] actualLA = addArray(actualL, A_WEIGHTING);
        double[] diffVerticalL = diffArray(expectedL,actualL);
        double[] directLA = actualLA;

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_LOW);

        double[] diff = diffArray(expectedLA, actualLA);

        //Path1 : reflexion
        expectedAlphaAtm = new double[]{0.1, 0.4, 1, 1.9, 3.7, 9.7, 32.8, 116.9};
        expectedAAtm = new double[]{0.01, 0.04, 0.11, 0.19, 0.37, 0.97, 3.3, 11.78};
        expectedADiv = new double[]{51.06, 51.06, 51.06, 51.06, 51.06, 51.06, 51.06, 51.06};
        expectedABoundaryH = new double[]{2.06, 2.10, 2.19, 2.36, 6.12, 7.70, 7.45, 8.15};
        expectedABoundaryF = new double[]{-0.59, -0.59, -0.59, -0.59, 4.43, 2.99, 0.42, -0.59};
        double[] expectedDLabs = new double[] {-0.46, -0.97, -1.55, -2.22, -3.01, -3.98, -5.23, -3.01};
        expectedLH = new double[]{35.56, 36.12, 38.09, 37.16, 32.44, 29.29, 25.96, 19.00};
        expectedLF = new double[]{37.83, 37.89, 38.82, 40.11, 34.12, 34.00, 32.98, 27.54};
        expectedLA = new double[]{10.64, 21.00, 29.97, 35.68, 33.36, 33.45, 31.76, 24.17};

        proPath = propDataOut.getPropagationPaths().get(1);

        actualAAtm = proPath.aAtm;
        actualADiv = proPath.aDiv;
        actualABoundaryH = proPath.double_aBoundaryH;
        actualABoundaryF = proPath.double_aBoundaryF;
        actualLH = addArray(proPath.aGlobalH, SOUND_POWER_LEVELS);
        actualLF = addArray(proPath.aGlobalF, SOUND_POWER_LEVELS);
        actualL = addArray(proPath.aGlobal, SOUND_POWER_LEVELS);
        actualLA = addArray(actualL, A_WEIGHTING);
        double[] diffReflexionL = diffArray(expectedL,actualL);

        assertDoubleArrayEquals("AAtm - reflection", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - reflection", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - reflection", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryF - reflection", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("dLabs - reflection", expectedDLabs, proPath.aRef, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("LH - reflection", expectedLH, actualLH, ERROR_EPSILON_LOW);
        assertDoubleArrayEquals("LF - reflection", expectedLF, actualLF, ERROR_EPSILON_HIGH);
        assertDoubleArrayEquals("LA - reflection", expectedLA, actualLA, ERROR_EPSILON_HIGH);

        double[] LA = sumDbArray(directLA,actualLA);
        double[] diffLa = diffArray(new double[]{16.84,26.97,34.79,40.23,38.57,38.58,39.36,29.60}, LA);
        double[] valLV = getMaxValeurAbsolue(diffVerticalL);
        double[] valLR = getMaxValeurAbsolue(diffReflexionL);
        double[] valLA = getMaxValeurAbsolue(diffLa);

        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{93-26.2,93-16.1,93-8.6,93-3.2,93,93+1.2,93+1.0,93-1.1});

        assertArrayEquals(  new double[]{16.84,26.97,34.79,40.23,38.57,38.58,39.36,29.60},L, ERROR_EPSILON_LOW);
    }

    /**
     * TC28 Propagation over a large distance with many buildings between source and
     * receiver
     *
     * Issue with favorable paths
     */
    @Test
    public void TC28() throws IOException {
        Attenuation propDataOut =  computeCnossosPath("TC28_Direct", "TC28_Right");

        /* Table 346 */
        List<Coordinate> expectedZProfile = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(92.46, 0.0),
                new Coordinate(92.46, 6.0),
                new Coordinate(108.88, 6.0),
                new Coordinate(108.88, 0.0),
                new Coordinate(169.35, 0.0),
                new Coordinate(169.35, 10.0),
                new Coordinate(189.72, 10.0),
                new Coordinate(189.72, 0),
                new Coordinate(338.36, 0.0),
                new Coordinate(338.36, 10.0),
                new Coordinate(353.88, 10.0),
                new Coordinate(353.88, 0.0),
                new Coordinate(400.5, 0.0),
                new Coordinate(400.5, 9.0),
                new Coordinate(415.52, 9.0),
                new Coordinate(415.52, 0.0),
                new Coordinate(442.3, 0.0),
                new Coordinate(442.3, 12.0),
                new Coordinate(457.25, 12.0),
                new Coordinate(457.25, 0.0),
                new Coordinate(730.93, 0.0),
                new Coordinate(730.93, 14.0),
                new Coordinate(748.07, 14.0),
                new Coordinate(748.07, 0.0),
                new Coordinate(976.22, 0.0),
                new Coordinate(976.22, 8.0),
                new Coordinate(990.91, 8.0),
                new Coordinate(990.91, 0.0),
                new Coordinate(1001.25, 0.0));

        /* Table 347 */
        List<Coordinate> expectedZProfileSO = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(92.46, 0.0),
                new Coordinate(92.46, 6.0),
                new Coordinate(108.88, 6.0),
                new Coordinate(108.88, 0.0),
                new Coordinate(169.35, 0.0));

        List<Coordinate> expectedZProfileOR = Arrays.asList(
                new Coordinate(990.91, 0.0),
                new Coordinate(1001.25, 0.0));

        List<Coordinate> expectedZProfileRight = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(119.89, 0.0),
                new Coordinate(406.93, 0.0),
                new Coordinate(421.93, 0.0),
                new Coordinate(780.00, 0.0),
                new Coordinate(1003.29, 0.0),
                new Coordinate(1028.57, 0.0));

        List<Coordinate> expectedZProfileLeft = Arrays.asList(
                new Coordinate(0.0, 0.0),
                new Coordinate(168.36, 0.0),
                new Coordinate(256.17, 0.0),
                new Coordinate(256.17, 14.0),
                new Coordinate(276.59, 14.0),
                new Coordinate(276.59, 0.0),
                new Coordinate(356.24, 0.0),
                new Coordinate(444.81, 0.0),
                new Coordinate(525.11, 0.0),
                new Coordinate(988.63, 0.0),
                new Coordinate(1002.95, 0.0),
                new Coordinate(1022.31, 0.0));

        /* Table 348 */
        double [][] segmentsMeanPlanes0 = new double[][]{
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.0, 0.25, 3.75, 9.09, 169.37, 0.45, 0.48},
                {0.0, 0.0, 8.0, 1.0, 10.34, 0.5, NaN}
        };

        double [][] segmentsMeanPlanes1 = new double[][]{ // Right
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.0, 0.0, 4.0, 1.0, 1028.57, 0.5, 0.5}
        };

        double [][] segmentsMeanPlanes2 = new double[][]{ // left
                //  a     b     zs    zr      dp    Gp   Gp'
                {0.0, 0.68, 3.32, 1.12, 1022.31, 0.49, 0.49}
        };

        CnossosPath SR = propDataOut.getPropagationPaths().get(0);

        assertZProfil(expectedZProfile, Arrays.asList(SR.getSRSegment().getPoints2DGround()));
        assertZProfil(expectedZProfileSO, Arrays.asList(SR.getSegmentList().get(0).getPoints2DGround()));
        assertZProfil(expectedZProfileOR, Arrays.asList(
                SR.getSegmentList().get(SR.getSegmentList().size() - 1).getPoints2DGround()));



        assertPlanes(segmentsMeanPlanes0,SR.getSegmentList());

        CnossosPath pathRight = propDataOut.getPropagationPaths().get(1);
        assertZProfil(expectedZProfileRight, Arrays.asList(pathRight.getSRSegment().getPoints2DGround()));
        assertPlanes(segmentsMeanPlanes1, pathRight.getSRSegment());


        // CnossosPath pathLeft = propDataOut.getPropagationPaths().get(2);
        // Error in CNOSSOS unit test, left diffraction is going over a building but not in their 3D view !
        // Why the weird left path in homogeneous ? it is not explained.
        // assertZProfil(expectedZProfileLeft, Arrays.asList(pathLeft.getSRSegment().getPoints2DGround()));
        // assertPlanes(segmentsMeanPlanes2,propDataOut.getPropagationPaths().get(2).getSRSegment()); // if b = 0.68: -> z2 = 0.32. In Cnossos z2 = 1.32 if b = 0.68

        //Expected values
        //Path0 : vertical plane
        double[] expectedAlphaAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.66, 32.77, 116.88};
        double[] expectedAAtm = new double[]{0.12, 0.41, 1.04, 1.93, 3.66, 9.68, 32.81, 117.03};
        double[] expectedADiv = new double[]{71.01, 71.01, 71.01, 71.01, 71.01, 71.01, 71.01, 71.01};
        double[] expectedABoundaryH = new double[]{14.85, 17.56, 20.52, 22.31, 22.31, 22.31, 22.32, 22.32};
        double[] expectedABoundaryF = new double[]{7.48, 10.11, 12.94, 15.86, 18.82, 19.78, 19.78, 19.78};
        double[] expectedLH = new double[]{64.02, 61.02, 57.43, 54.75, 53.01, 47.00, 23.86, -60.35};
        double[] expectedLF = new double[]{71.39, 68.46, 65.00, 61.20, 56.51, 49.54, 26.40, -57.82};
        double[] expectedL = new double[]{69.11, 66.17, 62.69, 59.08, 55.10, 48.45, 25.31, -58.90};
        double[] expectedLA = new double[]{42.91, 50.07, 54.09, 55.88, 55.10, 49.65, 26.31, -60.00};

        CnossosPath proPath = propDataOut.getPropagationPaths().get(0);

        double[] actualAlphaAtm = propDataOut.genericMeteoData.getAlpha_atmo();
        double[] actualAAtm = proPath.aAtm;
        double[] actualADiv = proPath.aDiv;
        double[] actualABoundaryH = proPath.double_aBoundaryH;
        double[] actualABoundaryF = proPath.double_aBoundaryF;
        double[] actualLH = addArray(proPath.aGlobalH, new double[]{150,150,150,150,150,150,150,150});
        double[] actualLF = addArray(proPath.aGlobalF, new double[]{150,150,150,150,150,150,150,150});
        double[] actualL = addArray(proPath.aGlobal, new double[]{150,150,150,150,150,150,150,150});
        double[] actualLA = addArray(actualL, A_WEIGHTING);

        assertDoubleArrayEquals("AlphaAtm - vertical plane", expectedAlphaAtm, actualAlphaAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("AAtm - vertical plane", expectedAAtm, actualAAtm, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ADiv - vertical plane", expectedADiv, actualADiv, ERROR_EPSILON_LOWEST);
        assertDoubleArrayEquals("ABoundaryH - vertical plane", expectedABoundaryH, actualABoundaryH, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("ABoundaryF - vertical plane", expectedABoundaryF, actualABoundaryF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("LH - vertical plane", expectedLH, actualLH, ERROR_EPSILON_VERY_LOW);
        assertDoubleArrayEquals("LF - vertical plane", expectedLF, actualLF, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("L - vertical plane", expectedL, actualL, ERROR_EPSILON_VERY_HIGH);
        assertDoubleArrayEquals("LA - vertical plane", expectedLA, actualLA, ERROR_EPSILON_VERY_HIGH);


        double[] diffL = diffArray(expectedL, actualL);
        double[] diffLa = diffArray(expectedLA, actualLA);
        double[] valL = getMaxValeurAbsolue(diffL);
        double[] valLA = getMaxValeurAbsolue(diffLa);



        double[] L = addArray(propDataOut.getVerticesSoundLevel().get(0).value, new double[]{150-26.2,150-16.1,150-8.6,150-3.2,150,150+1.2,150+1.0,150-1.1});

        assertArrayEquals(  new double[]{43.56,50.59,54.49,56.14,55.31,49.77,23.37,-59.98},L, ERROR_EPSILON_VERY_HIGH);


    }

    /**
     * TC28 Propagation over a large distance with many buildings between source and
     * receiver
     */
    @Test
    public void TestFavorableConditionAttenuationRose() {
        GeometryFactory factory = new GeometryFactory();

        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder();

        builder.finishFeeding();

        //Propagation data building
        Vector3D northReceiver = new Vector3D(0, 100, 4);
        List<Vector3D> receivers = new ArrayList<>();
        receivers.add(northReceiver);
        receivers.add(Orientation.rotate(new Orientation(45, 0, 0), northReceiver)); // NW
        receivers.add(Orientation.rotate(new Orientation(90, 0, 0), northReceiver)); // W
        receivers.add(Orientation.rotate(new Orientation(135, 0, 0), northReceiver)); // SW
        receivers.add(Orientation.rotate(new Orientation(180, 0, 0), northReceiver)); // S
        receivers.add(Orientation.rotate(new Orientation(225, 0, 0), northReceiver)); // SE
        receivers.add(Orientation.rotate(new Orientation(270, 0, 0), northReceiver)); // E
        receivers.add(Orientation.rotate(new Orientation(315, 0, 0), northReceiver)); // NE
        ProfileBuilderDecorator propagationDataBuilder = new ProfileBuilderDecorator(builder)
                .addSource(0, 0, 4);
        for(Vector3D receiver : receivers) {
            propagationDataBuilder.addReceiver(receiver.getX(), receiver.getY(), receiver.getZ());
        }
        Scene rayData = propagationDataBuilder.hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.5)
                .build();
        rayData.reflexionOrder=1;
        rayData.maxSrcDist = 1500;

        double[][] windRoseTest = new double[receivers.size()][];
        // generate favorable condition for each direction
        for(int idReceiver : IntStream.range(0, receivers.size()).toArray()) {
            windRoseTest[idReceiver] = new double[AttenuationCnossosParameters.DEFAULT_WIND_ROSE.length];
            double angle = Math.atan2(receivers.get(idReceiver).getY(), receivers.get(idReceiver).getX());
            Arrays.fill(windRoseTest[idReceiver], 1);
            int roseIndex = AttenuationCnossosParameters.getRoseIndex(angle);
            windRoseTest[idReceiver][roseIndex] = 0.5;
        }
        for(int idReceiver : IntStream.range(0, receivers.size()).toArray()) {
            double[] favorableConditionDirections = windRoseTest[idReceiver];
            //Propagation process path data building
            AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
            attData.setHumidity(HUMIDITY);
            attData.setTemperature(TEMPERATURE);
            attData.setWindRose(favorableConditionDirections);

            //Out and computation settings
            Attenuation propDataOut = new Attenuation(true, true, attData, rayData);
            PathFinder computeRays = new PathFinder(rayData);
            computeRays.setThreadCount(1);
            computeRays.run(propDataOut);

            int maxPowerReceiverIndex = -1;
            double maxGlobalValue = Double.NEGATIVE_INFINITY;
            for (Attenuation.SourceReceiverAttenuation v : propDataOut.getVerticesSoundLevel()) {
                double globalValue = AcousticIndicatorsFunctions.sumDbArray(v.value);
                if (globalValue > maxGlobalValue) {
                    maxGlobalValue = globalValue;
                    maxPowerReceiverIndex = (int) v.receiverId;
                }
            }
            assertEquals(idReceiver, maxPowerReceiverIndex);
        }
    }
//
//    /**
//     * Test optimisation feature {@link Scene#maximumError}
//     * This feature is disabled and all sound sources are computed
//     */
//    @Test
//    public void testIgnoreNonSignificantSources() throws LayerDelaunayError {
//
//        GeometryFactory factory = new GeometryFactory();
//        //Scene dimension
//        Envelope cellEnvelope = new Envelope(new Coordinate(-1200, -1200, 0.), new Coordinate(1200, 1200, 0.));
//
//        //Create obstruction test object
//        ProfileBuilder builder = new ProfileBuilder();
//
//        builder.addGroundEffect(factory.toGeometry(new Envelope(0, 50, -250, 250)), 0.9);
//        builder.addGroundEffect(factory.toGeometry(new Envelope(50, 150, -250, 250)), 0.5);
//        builder.addGroundEffect(factory.toGeometry(new Envelope(150, 225, -250, 250)), 0.2);
//
//        builder.finishFeeding();
//
//        double[] roadLvl = new double[]{25.65, 38.15, 54.35, 60.35, 74.65, 66.75, 59.25, 53.95};
//        for(int i = 0; i < roadLvl.length; i++) {
//            roadLvl[i] = dbaToW(roadLvl[i]);
//        }
//
//        DirectPropagationProcessData rayData = new DirectPropagationProcessData(builder);
//        rayData.addReceiver(new Coordinate(0, 0, 4));
//        rayData.addSource(factory.createPoint(new Coordinate(10, 10, 1)), roadLvl);
//        rayData.addSource(factory.createPoint(new Coordinate(1100, 1100, 1)), roadLvl);
//        rayData.setComputeHorizontalDiffraction(true);
//        rayData.setComputeVerticalDiffraction(true);
//
//        rayData.maxSrcDist = 2000;
//        rayData.maximumError = 3; // 3 dB error max
//
//        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
//        attData.setHumidity(70);
//        attData.setTemperature(10);
//        RayOut propDataOut = new RayOut(true, attData, rayData);
//        PathFinder computeRays = new PathFinder(rayData);
//        computeRays.setThreadCount(1);
//        computeRays.run(propDataOut);
//
//        // Second source has not been computed because at best it would only increase the received level of only 0.0004 dB
//        assertEquals(1, propDataOut.receiversAttenuationLevels.size());
//
//        //TODO check the expected level and the delta should be reduced to at least 0.1
//        assertEquals(44.07, wToDba(sumArray(roadLvl.length, dbaToW(propDataOut.getVerticesSoundLevel().get(0).value))), 3);
//    }

    @Test
    public void testRoseIndex() {
        double angle_section = (2 * Math.PI) / AttenuationCnossosParameters.DEFAULT_WIND_ROSE.length;
        double angleStart = Math.PI / 2 - angle_section / 2;
        for(int i = 0; i < AttenuationCnossosParameters.DEFAULT_WIND_ROSE.length; i++) {
            double angle = angleStart - angle_section * i - angle_section / 3;
            int index = AttenuationCnossosParameters.getRoseIndex(new Coordinate(0, 0), new Coordinate(Math.cos(angle), Math.sin(angle)));
            assertEquals(i, index);angle = angleStart - angle_section * i - angle_section * 2.0/3.0;
            index = AttenuationCnossosParameters.getRoseIndex(new Coordinate(0, 0), new Coordinate(Math.cos(angle), Math.sin(angle)));
            assertEquals(i, index);
        }
    }

    /**
     * Check if Li coefficient computation and line source subdivision are correctly done
     * @throws LayerDelaunayError
     */
    @Test
    public void testSourceLines()  throws LayerDelaunayError, IOException, ParseException {

        // First Compute the scene with only point sources at 1m each
        GeometryFactory factory = new GeometryFactory();
        WKTReader wktReader = new WKTReader(factory);
        LineString geomSource = (LineString)wktReader.read("LINESTRING (51 40.5 0.05, 51 55.5 0.05)");
        //Scene dimension
        Envelope cellEnvelope = new Envelope(new Coordinate(-1200, -1200, 0.), new Coordinate(1200, 1200, 0.));

        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder();

        builder.addGroundEffect(factory.toGeometry(new Envelope(0, 50, -250, 250)), 0.9);
        builder.addGroundEffect(factory.toGeometry(new Envelope(50, 150, -250, 250)), 0.5);
        builder.addGroundEffect(factory.toGeometry(new Envelope(150, 225, -250, 250)), 0.2);

        builder.finishFeeding();

        double[] roadLvl = new double[]{25.65, 38.15, 54.35, 60.35, 74.65, 66.75, 59.25, 53.95};
        for(int i = 0; i < roadLvl.length; i++) {
            roadLvl[i] = dbaToW(roadLvl[i]);
        }

        DirectPropagationProcessData rayData = new DirectPropagationProcessData(builder);
        rayData.addReceiver(new Coordinate(50, 50, 0.05));
        rayData.addReceiver(new Coordinate(48, 50, 4));
        rayData.addReceiver(new Coordinate(44, 50, 4));
        rayData.addReceiver(new Coordinate(40, 50, 4));
        rayData.addReceiver(new Coordinate(20, 50, 4));
        rayData.addReceiver(new Coordinate(0, 50, 4));

        List<Coordinate> srcPtsRef = new ArrayList<>();
        PathFinder.splitLineStringIntoPoints(geomSource, 1.0, srcPtsRef);
        for(Coordinate srcPtRef : srcPtsRef) {
            rayData.addSource(factory.createPoint(srcPtRef), roadLvl);
        }

        rayData.setComputeHorizontalDiffraction(true);
        rayData.setComputeVerticalDiffraction(true);
        rayData.maxSrcDist = 2000;

        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(70);
        attData.setTemperature(10);

        RayOut propDataOut = new RayOut(true, attData, rayData);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.makeRelativeZToAbsolute();
        computeRays.setThreadCount(1);
        computeRays.run(propDataOut);


        // Second compute the same scene but with a line source
        rayData.clearSources();
        rayData.addSource(geomSource, roadLvl);
        RayOut propDataOutTest = new RayOut(true, attData, rayData);
        computeRays.run(propDataOutTest);

        // Merge levels for each receiver for point sources
        Map<Long, double[]> levelsPerReceiver = new HashMap<>();
        for(Attenuation.SourceReceiverAttenuation lvl : propDataOut.receiversAttenuationLevels) {
            if(!levelsPerReceiver.containsKey(lvl.receiverId)) {
                levelsPerReceiver.put(lvl.receiverId, lvl.value);
            } else {
                // merge
                levelsPerReceiver.put(lvl.receiverId, sumDbArray(levelsPerReceiver.get(lvl.receiverId),
                        lvl.value));
            }
        }


        // Merge levels for each receiver for lines sources
        Map<Long, double[]> levelsPerReceiverLines = new HashMap<>();
        for(Attenuation.SourceReceiverAttenuation lvl : propDataOutTest.receiversAttenuationLevels) {
            if(!levelsPerReceiverLines.containsKey(lvl.receiverId)) {
                levelsPerReceiverLines.put(lvl.receiverId, lvl.value);
            } else {
                // merge
                levelsPerReceiverLines.put(lvl.receiverId, sumDbArray(levelsPerReceiverLines.get(lvl.receiverId),
                        lvl.value));
            }
        }

        assertEquals(6, levelsPerReceiverLines.size());
        assertEquals(6, levelsPerReceiver.size());

//        KMLDocument.exportScene("target/testSourceLines.kml", manager, propDataOutTest);
//        KMLDocument.exportScene("target/testSourceLinesPt.kml", manager, propDataOut);
//        // Uncomment for printing maximum error
//        for(int i = 0; i < levelsPerReceiver.size(); i++) {
//            LOGGER.info(String.format("%d error %.2f", i,  getMaxError(levelsPerReceiver.get(i), levelsPerReceiverLines.get(i))));
//        }

        for(int i = 0; i < levelsPerReceiver.size(); i++) {
            assertArrayEquals(levelsPerReceiver.get(i), levelsPerReceiverLines.get(i), 0.2);
        }
    }



    /**
     * Test of convergence of power at receiver when increasing the reflection order
     * Event at 100 order of reflection then final noise level should not be
     * superior to 3.0 decibels compared to direct power
     */
    @Test
    public void testReflexionConvergence() {
        //Profile building
        List<Integer> alphaWallFrequencies = Arrays.asList(AttenuationCnossosParameters.asOctaveBands(
                DEFAULT_FREQUENCIES_THIRD_OCTAVE));
        List<Double> alphaWall = new ArrayList<>(alphaWallFrequencies.size());
        for(int frequency : alphaWallFrequencies) {
            alphaWall.add(WallAbsorption.getWallAlpha(100000, frequency));
        }

        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addWall(new Coordinate[]{
                        new Coordinate(6, 0, 4),
                        new Coordinate(-5, 12, 4),
                }, 8, alphaWall, 0)
                .addWall(new Coordinate[]{
                        new Coordinate(14, 4, 4),
                        new Coordinate(3, 16, 4),
                }, 8, alphaWall, 1);
        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(8, 5.5, 0.1)
                .addReceiver(4.5, 8, 1.6)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.5)
                .build();
        rayData.maxSrcDist = 100*800;
        rayData.maxRefDist = 100*800;
        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        double firstPowerAtReceiver = 0;
        for(int i = 0; i < 100; i++) {

            //Out and computation settings
            Attenuation propDataOut = new Attenuation(true, true, attData, rayData);
            rayData.reflexionOrder = i;
            PathFinder computeRays = new PathFinder(rayData);
            computeRays.setThreadCount(1);

            //Run computation
            computeRays.run(propDataOut);

            //Actual values
            // number of propagation paths between two walls = reflectionOrder * 2 + 1
            assertEquals(i * 2 + 1, propDataOut.getPropagationPaths().size());

            double[] sourcePower = new double[alphaWall.size()];
            double[] receiverPower = new double[alphaWall.size()];
            Arrays.fill(sourcePower, 70.0);
            for(CnossosPath proPath : propDataOut.getPropagationPaths()) {
                double[] attenuationGlobal = proPath.aGlobal;
                double[] contributionPower = AcousticIndicatorsFunctions.sumArray(attenuationGlobal, sourcePower);
                receiverPower = AcousticIndicatorsFunctions.sumDbArray(receiverPower, contributionPower);
            }
            double globalPowerAtReceiver = AcousticIndicatorsFunctions.wToDba(AcousticIndicatorsFunctions.sumArray(AcousticIndicatorsFunctions.dbaToW(receiverPower)));
            if(i == 0) {
                firstPowerAtReceiver = globalPowerAtReceiver;
            } else {
                assertEquals(firstPowerAtReceiver, globalPowerAtReceiver, 3.0);
            }
        }
    }


    /**
     * Test reported issue with receiver over building
     */
    @Test
    public void testReceiverOverBuilding() throws LayerDelaunayError, ParseException {

        GeometryFactory factory = new GeometryFactory();
        //Scene dimension
        Envelope cellEnvelope = new Envelope(new Coordinate(-1200, -1200, 0.), new Coordinate(1200, 1200, 0.));

        WKTReader wktReader = new WKTReader();
        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder();

        builder.addGroundEffect(factory.toGeometry(new Envelope(0, 50, -250, 250)), 0.9);
        builder.addGroundEffect(factory.toGeometry(new Envelope(50, 150, -250, 250)), 0.5);
        builder.addGroundEffect(factory.toGeometry(new Envelope(150, 225, -250, 250)), 0.2);

        builder.addBuilding(wktReader.read("POLYGON ((-111 -35, -111 82, 70 82, 70 285, 282 285, 282 -35, -111 -35))"), 10, -1);

        builder.finishFeeding();

        double[] roadLvl = new double[]{25.65, 38.15, 54.35, 60.35, 74.65, 66.75, 59.25, 53.95};
        for(int i = 0; i < roadLvl.length; i++) {
            roadLvl[i] = dbaToW(roadLvl[i]);
        }

        DirectPropagationProcessData rayData = new DirectPropagationProcessData(builder);
        rayData.addReceiver(new Coordinate(162, 80, 150));
        rayData.addSource(factory.createPoint(new Coordinate(-150, 200, 1)), roadLvl);
        rayData.setComputeHorizontalDiffraction(true);
        rayData.setComputeVerticalDiffraction(true);

        rayData.maxSrcDist = 2000;

        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(70);
        attData.setTemperature(10);
        RayOut propDataOut = new RayOut(true, attData, rayData);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);
        computeRays.run(propDataOut);

        assertEquals(1, propDataOut.receiversAttenuationLevels.size());

        assertEquals(14.6, wToDba(sumArray(roadLvl.length, dbaToW(propDataOut.getVerticesSoundLevel().get(0).value))), 0.1);
    }

    private static double getMaxError(double[] ref, double[] result) {
        assertEquals(ref.length, result.length);
        double max = Double.MIN_VALUE;
        for(int i=0; i < ref.length; i++) {
            max = Math.max(max, Math.abs(ref[i] - result[i]));
        }
        return max;
    }

    private static final class RayOut extends Attenuation {
        private DirectPropagationProcessData processData;

        public RayOut(boolean keepRays, AttenuationCnossosParameters pathData, DirectPropagationProcessData processData) {
            super(keepRays, pathData, processData);
            this.processData = processData;
        }

        @Override
        public double[] computeCnossosAttenuation(AttenuationCnossosParameters data, int sourceId, double sourceLi, List<CnossosPath> pathParameters) {
            double[] attenuation = super.computeCnossosAttenuation(data, sourceId, sourceLi, pathParameters);
            return wToDba(multArray(processData.wjSources.get((int)sourceId), dbaToW(attenuation)));
        }
    }

    private static final class DirectPropagationProcessData extends Scene {
        private List<double[]> wjSources = new ArrayList<>();

        public DirectPropagationProcessData(ProfileBuilder builder) {
            super(builder);
        }

        public void addSource(Geometry geom, double[] spectrum) {
            super.addSource(geom);
            wjSources.add(spectrum);
        }

        public void addSource(Geometry geom, List<Double> spectrum) {
            super.addSource(geom);
            double[] wj = new double[spectrum.size()];
            for(int i=0; i < spectrum.size(); i++) {
                wj[i] = spectrum.get(i);
            }
            wjSources.add(wj);
        }

        public void clearSources() {
            wjSources.clear();
            sourceGeometries.clear();
            sourcesIndex = new QueryRTree();
        }

    }


    /**
     * Test NaN regression issue
     * ByteArrayOutputStream bos = new ByteArrayOutputStream();
     * propath.writeStream(new DataOutputStream(bos));
     * new String(Base64.getEncoder().encode(bos.toByteArray()));
     */
    //@Test
    public void TestRegressionNaN() throws LayerDelaunayError, IOException {
        String path = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABkELTp9wo7AcQVnI2rXCgfo/qZmZmZmZmgAAAAAAAAAAAAAAAAAAAAAACH/4" +
                "AAAAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gAAAAAAAD/////AAAAAEELUD" +
                "JSoUA3QVnItqDcGhJAJdiQBvXwS0AVTjoMf9fiAAAAAAAAAAAACH/4AAAAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gA" +
                "AAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gAAAAAAAD/////AAAAA0ELUoGFTOGrQVnIga50fzdANmqD/Me4pUActzMeCMRaAAAAAA" +
                "AAAAAACH/4AAAAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gAAAAAAAD/////" +
                "AAAAA0ELUo/NRf1KQVnIgGcH8SZANmqD/Me4pUAe4TEhnNY1AAAAAAAAAAAACH/4AAAAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AA" +
                "AAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gAAAAAAAD/////AAAAA0ELU1RrgqjDQVnIbssqD85AMNkgsNSQIkAlRqCv" +
                "boWkAAAAAAAAAAAACH/4AAAAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gAAA" +
                "AAAAD/////AAAAA0ELU3djM9QGQVnIa6l2eGhALdnXzMMRgUAl2dfMwxGBAAAAAAAAAAAACH/4AAAAAAAAf/gAAAAAAAB/+AAAA" +
                "AAAAH/4AAAAAAAAf/gAAAAAAAB/+AAAAAAAAH/4AAAAAAAAf/gAAAAAAAD/////AAAABAAAAAU/wBvxnrf6hkBJLxOOJzAAwGIL" +
                "Ic/cAABAJRdmLHGkLkELTp9xTapHQVnI2rWzSOi/jTelQ3f5WD/hNUTOrUdwQFJ6WqIZaADAanpOelWAAD/odGaWtL+wQQtQMkh" +
                "BG1pBWci2ocn7h0ASDrQig+tyAAAAAAAAAAA/+uzqKo4AAMATSo/AwAAAP/Qml6qEHQRBC1J9yb/w6kFZyIIECGuLQBb5xfFciCQ" +
                "AAAAAAAAAAEA4e506acAAwFGKjYvwAABABNd1zN/3IEELUo8N2d3pQVnIgHgsr3lAIC98kZ14SQAAAAAAAAAAQBFrZiGsAADAKP" +
                "YLaTAAAD/SoWVrORNgQQtTU81vkgNBWchu2VI9a0AlRuFfAqJXAAAABT/TBsY8SUi2QGNg7UkPZADAe8S0CsKAAEAVMZUczyA2Q" +
                "QtOn1fPWpRBWcjat/vFwUAKJWO95msGP9MGxjxJSLZAY2CN2WJwAMB7xCtJq4AAQCDgvd4PLeBBC06fP717akFZyNq6I58WQBny" +
                "M91nx0E/0wbGPElItkBjYUy4vFgAwHvFPMvZgABAAUNc+v/JNkELTp9Xz1qUQVnI2rf7xcFACiVjveZrBj/TBsY8SUi2QGNfsm" +
                "l4cADAe8Lw2QoAAEAuAc8nHC4hQQtOn3aZdepBWcjatTnckL+z+60sjk/gP9MGxjxJSLZAY2GkR76YAMB7xbpDLYAAQBuZiTof" +
                "xetBC06fV89alEFZyNq3+8XBQAolY73mawY=";

        CnossosPath propPath = new CnossosPath();
        propPath.readStream(new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(path))));

        AttenuationCnossosParameters pathData = new AttenuationCnossosParameters();
        AttenuationCnossos.evaluate(propPath, pathData);
        double[] aGlobalMeteoHom = AttenuationCnossos.getaGlobal();
        for (int i = 0; i < aGlobalMeteoHom.length; i++) {
            assertFalse(Double.isNaN(aGlobalMeteoHom[i]), String.format("freq %d Hz with nan value", pathData.freq_lvl.get(i)));
        }

    }


    /**
     * Assertions for a list of {@link CnossosPath}.
     * @param expectedPts    Array of arrays of array of expected coordinates (xyz) of points of paths. To each path
     *                       corresponds an array of points. To each point corresponds an array of coordinates (xyz).
     * @param expectedGPaths Array of arrays of gPaths values. To each path corresponds an arrays of gPath values.
     * @param actualPathParameters    Computed arrays of {@link CnossosPath}.
     */
    private static void assertPaths(double[][][] expectedPts, double[][] expectedGPaths, List<CnossosPath> actualPathParameters) {
        assertEquals(expectedPts.length, actualPathParameters.size(), "Expected path count is different than actual path count.");
        for(int i=0; i<expectedPts.length; i++) {
            CnossosPath pathParameters = actualPathParameters.get(i);
            for(int j=0; j<expectedPts[i].length; j++){
                PointPath point = pathParameters.getPointList().get(j);
                assertEquals(expectedPts[i][j][0], point.coordinate.x, DELTA_COORDS, "Path "+i+" point "+j+" coord X");
                assertEquals(expectedPts[i][j][1], point.coordinate.y, DELTA_COORDS, "Path "+i+" point "+j+" coord Y");            }
            assertEquals(expectedGPaths[i].length, pathParameters.getSegmentList().size(), "Expected path["+i+"] segments count is different than actual path segment count.");
            for(int j=0; j<expectedGPaths[i].length; j++) {
                assertEquals(expectedGPaths[i][j], pathParameters.getSegmentList().get(j).gPath, DELTA_G_PATH, "Path " + i + " g path " + j);
            }
        }
    }


    /**
     * Assertions for a list of {@link CnossosPath}.
     * @param expectedPts    Array of arrays of array of expected coordinates (xyz) of points of paths. To each path
     *                       corresponds an array of points. To each point corresponds an array of coordinates (xyz).
     * @param actualPathParameters    Computed arrays of {@link CnossosPath}.
     */
    private static void assertPaths(double[][][] expectedPts, List<CnossosPath> actualPathParameters) {
        assertEquals(expectedPts.length, actualPathParameters.size(), "Expected path count is different than actual path count.");
        for(int i=0; i<expectedPts.length; i++) {
            CnossosPath pathParameters = actualPathParameters.get(i);
            for(int j=0; j<expectedPts[i].length; j++){
                PointPath point = pathParameters.getPointList().get(j);
                assertEquals(expectedPts[i][j][0], point.coordinate.x, DELTA_COORDS, "Path "+i+" point "+j+" coord X");
                assertEquals(expectedPts[i][j][1], point.coordinate.y, DELTA_COORDS, "Path "+i+" point "+j+" coord Y");
            }
        }
    }
    private static void assertPlanes(double[][] expectedPlanes, List<SegmentPath> segments) {
        assertPlanes(expectedPlanes, segments.toArray(new SegmentPath[0]));
    }

    private static void assertPlane(double[] expectedPlane, SegmentPath segment) {
        assertEquals(expectedPlane[0], segment.a, DELTA_PLANES, "a");
        assertEquals(expectedPlane[1], segment.b, DELTA_PLANES, "b");
        assertEquals(expectedPlane[2], segment.zsH, DELTA_PLANES, "zs");
        assertEquals(expectedPlane[3], segment.zrH, DELTA_PLANES, "zr");
        assertEquals(expectedPlane[4], segment.dp, DELTA_PLANES, "dp");
        assertEquals(expectedPlane[5], segment.gPath, DELTA_PLANES, "gPath");
        if(!Double.isNaN(expectedPlane[6])) {
            assertEquals(expectedPlane[6], segment.gPathPrime, DELTA_PLANES, "gPrimePath");
        }
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

    private static void assertPlanes(double[][] expectedPlanes, SegmentPath... segments) {
        assertPlane(expectedPlanes[0], segments[0]);
        if(segments.length>1) {
            assertPlane(expectedPlanes[1], segments[segments.length - 1]);
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
}

