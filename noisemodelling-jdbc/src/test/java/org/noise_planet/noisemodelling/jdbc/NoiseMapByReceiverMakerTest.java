/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTWriter;
import org.noise_planet.noisemodelling.jdbc.Utils.JDBCComputeRaysOut;
import org.noise_planet.noisemodelling.jdbc.Utils.JDBCPropagationData;
import org.noise_planet.noisemodelling.pathfinder.*;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.GroundAbsorption;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;
import org.noise_planet.noisemodelling.propagation.Attenuation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.jdbc.Utils.getRunScriptRes;

public class NoiseMapByReceiverMakerTest {

    private Connection connection;

    @BeforeEach
    public void tearUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(NoiseMapByReceiverMakerTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(connection != null) {
            connection.close();
        }
    }



    /**
     * DEM is 22m height between sources and receiver. There is a direct field propagation over the building
     * @throws SQLException
     */
    @Test
    public void testDemTopOfBuilding() throws Exception {
        try(Statement st = connection.createStatement()) {
            st.execute(getRunScriptRes("scene_with_dem.sql"));
            st.execute("DROP TABLE IF EXISTS RECEIVERS");
            st.execute("CREATE TABLE RECEIVERS(the_geom GEOMETRY(POINTZ), GID SERIAL PRIMARY KEY)");
            st.execute("INSERT INTO RECEIVERS(the_geom) VALUES ('POINTZ(-72 41 11)')");
            st.execute("INSERT INTO RECEIVERS(the_geom) VALUES ('POINTZ(-9 41 1.6)')");
            st.execute("INSERT INTO RECEIVERS(the_geom) VALUES ('POINTZ(70 11 7)')");
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS", "SOUND_SOURCE", "RECEIVERS");
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(true);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(true);
            noiseMapByReceiverMaker.setSoundReflectionOrder(0);
            noiseMapByReceiverMaker.setReceiverHasAbsoluteZCoordinates(true);
            noiseMapByReceiverMaker.setSourceHasAbsoluteZCoordinates(false);
            noiseMapByReceiverMaker.setHeightField("HEIGHT");

            noiseMapByReceiverMaker.setDemTable("DEM");
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(false);
            noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor());

            noiseMapByReceiverMaker.setComputeRaysOutFactory(new JDBCComputeRaysOut(true));
            noiseMapByReceiverMaker.setPropagationProcessDataFactory(new JDBCPropagationData());

            List<Attenuation.SourceReceiverAttenuation> allLevels = new ArrayList<>();
            List<CnossosPath> propaMap = Collections.synchronizedList(new ArrayList<CnossosPath>());
            Set<Long> receivers = new HashSet<>();
            noiseMapByReceiverMaker.setThreadCount(1);
            RootProgressVisitor progressVisitor = new RootProgressVisitor(noiseMapByReceiverMaker.getGridDim() * noiseMapByReceiverMaker.getGridDim(), true, 5);
            for(int i=0; i < noiseMapByReceiverMaker.getGridDim(); i++) {
                for(int j=0; j < noiseMapByReceiverMaker.getGridDim(); j++) {
                    IComputePathsOut out = noiseMapByReceiverMaker.evaluateCell(connection, i, j, progressVisitor, receivers);
                    if(out instanceof Attenuation) {
                        allLevels.addAll(((Attenuation) out).getVerticesSoundLevel());
                        propaMap.addAll(((Attenuation) out).getPropagationPaths());
                    }
                }
            }


            DataOutputStream outputBin = new DataOutputStream(new FileOutputStream("./target/test-resources/propaMap.bin"));
            //PropagationPath.writePropagationPathListStream(outputBin, propaMap);
            propaMap.clear();
            DataInputStream input = new DataInputStream(new FileInputStream("./target/test-resources/propaMap.bin"));
            //PropagationPath.readPropagationPathListStream(input, propaMap);


            assertEquals(3, allLevels.size());
        }
    }

    @Test
    public void testGroundSurface() throws Exception {
        try(Statement st = connection.createStatement()) {
            st.execute(String.format("CALL SHPREAD('%s', 'LANDCOVER2000')", NoiseMapByReceiverMakerTest.class.getResource("landcover2000.shp").getFile()));
            st.execute(getRunScriptRes("scene_with_landcover.sql"));
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS", "ROADS_GEOM", "RECEIVERS");
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(true);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(true);
            noiseMapByReceiverMaker.setSoundReflectionOrder(1);
            noiseMapByReceiverMaker.setReceiverHasAbsoluteZCoordinates(false);
            noiseMapByReceiverMaker.setSourceHasAbsoluteZCoordinates(false);
            noiseMapByReceiverMaker.setHeightField("HEIGHT");
            noiseMapByReceiverMaker.setSoilTableName("LAND_G");
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(true);
            noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor());

            noiseMapByReceiverMaker.setComputeRaysOutFactory(new JDBCComputeRaysOut(false));
            noiseMapByReceiverMaker.setPropagationProcessDataFactory(new JDBCPropagationData());

            Set<Long> receivers = new HashSet<>();
            noiseMapByReceiverMaker.setThreadCount(1);
            RootProgressVisitor progressVisitor = new RootProgressVisitor(noiseMapByReceiverMaker.getGridDim() * noiseMapByReceiverMaker.getGridDim(), true, 5);
            double expectedMaxArea = Math.pow(noiseMapByReceiverMaker.getGroundSurfaceSplitSideLength(), 2);
            for(int i=0; i < noiseMapByReceiverMaker.getGridDim(); i++) {
                for(int j=0; j < noiseMapByReceiverMaker.getGridDim(); j++) {
                    IComputePathsOut out = noiseMapByReceiverMaker.evaluateCell(connection, i, j, progressVisitor, receivers);
                    if(out instanceof Attenuation) {
                        Attenuation rout = (Attenuation) out;
                        for(GroundAbsorption soil : rout.inputData.profileBuilder.getGroundEffects()) {
                            assertTrue(soil.getGeometry().getArea() < expectedMaxArea);
                        }
                    }
                }
            }

        }
    }

    @Test
    public void testNoiseMapBuilding() throws Exception {
        try(Statement st = connection.createStatement()) {
            st.execute(String.format("CALL SHPREAD('%s', 'LANDCOVER2000')", NoiseMapByReceiverMakerTest.class.getResource("landcover2000.shp").getFile()));
            st.execute(getRunScriptRes("scene_with_landcover.sql"));
            DelaunayReceiversMaker noisemap = new DelaunayReceiversMaker("BUILDINGS", "ROADS_GEOM");
            noisemap.setReceiverHasAbsoluteZCoordinates(false);
            noisemap.setSourceHasAbsoluteZCoordinates(false);
            noisemap.setHeightField("HEIGHT");
            noisemap.initialize(connection, new EmptyProgressVisitor());

            AtomicInteger pk = new AtomicInteger(0);
            for(int i=0; i < noisemap.getGridDim(); i++) {
                for(int j=0; j < noisemap.getGridDim(); j++) {
                    noisemap.generateReceivers(connection, i, j, "NM_RECEIVERS", "TRIANGLES", pk);
                }
            }
            assertNotSame(0, pk.get());
        }
    }

    private static String createSource(Geometry source, double lvl, Orientation sourceOrientation, int directivityId) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ROADS_GEOM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, YAW REAL, PITCH REAL, ROLL REAL, DIR_ID INT");
        StringBuilder values = new StringBuilder("(row_number() over())::int, ST_SETSRID('");
        values.append(new WKTWriter(3).write(source));
        values.append("', 2154) THE_GEOM, ");
        values.append(sourceOrientation.yaw);
        values.append(" YAW, ");
        values.append(sourceOrientation.pitch);
        values.append(" PITCH, ");
        values.append(sourceOrientation.roll);
        values.append(" ROLL, ");
        values.append(directivityId);
        values.append(" DIR_ID");
        AttenuationCnossosParameters data = new AttenuationCnossosParameters(false);
        for(String period : new String[] {"D", "E", "N"}) {
            for (int freq : data.freq_lvl) {
                String fieldName = "LW" + period + freq;
                sb.append(", ");
                sb.append(fieldName);
                sb.append(" real");
                values.append(", ");
                values.append(String.format(Locale.ROOT, "%.2f", lvl));
                values.append(" ");
                values.append(fieldName);
            }
        }
        sb.append(") AS select ");
        sb.append(values.toString());
        return sb.toString();
    }


    @Test
    public void testPointDirectivity() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE BUILDINGS(pk serial  PRIMARY KEY, the_geom geometry, height real)");
            st.execute(createSource(new GeometryFactory().createPoint(new Coordinate(223915.72,6757480.22,0.0 )),
                    91, new Orientation(90,15,0),
                    4));
            st.execute("create table receivers(id serial PRIMARY KEY, the_geom GEOMETRY(POINTZ));\n" +
                    "insert into receivers(the_geom) values ('POINTZ (223915.72 6757490.22 0.0)');" +
                    "insert into receivers(the_geom) values ('POINTZ (223925.72 6757480.22 0.0)');");
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS", "ROADS_GEOM", "RECEIVERS");
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(false);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(false);
            noiseMapByReceiverMaker.setSoundReflectionOrder(0);
            noiseMapByReceiverMaker.setReceiverHasAbsoluteZCoordinates(false);
            noiseMapByReceiverMaker.setMaximumPropagationDistance(1000);
            noiseMapByReceiverMaker.setSourceHasAbsoluteZCoordinates(false);
            noiseMapByReceiverMaker.setHeightField("HEIGHT");

            NoiseMapParameters noiseMapParameters = new NoiseMapParameters(NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_DEN);
            noiseMapParameters.setExportRaysMethod(NoiseMapParameters.ExportRaysMethods.TO_MEMORY);

            noiseMapParameters.setCoefficientVersion(1);
            NoiseMapMaker noiseMapMaker = new NoiseMapMaker(connection, noiseMapParameters);
            // Use train directivity functions instead of discrete directivity
            noiseMapMaker.insertTrainDirectivity();

            noiseMapByReceiverMaker.setPropagationProcessDataFactory(noiseMapMaker);
            noiseMapByReceiverMaker.setComputeRaysOutFactory(noiseMapMaker);

            noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor());
            Set<Long> receivers = new HashSet<>();
            noiseMapByReceiverMaker.setThreadCount(1);
            noiseMapByReceiverMaker.setGridDim(1);
            RootProgressVisitor progressVisitor = new RootProgressVisitor((long) noiseMapByReceiverMaker.getGridDim() * noiseMapByReceiverMaker.getGridDim(), true, 5);
            //System.out.println("size = "+ noiseMapByReceiverMaker.getGridDim());
            for(int i=0; i < noiseMapByReceiverMaker.getGridDim(); i++) {
                for(int j=0; j < noiseMapByReceiverMaker.getGridDim(); j++) {
                    IComputePathsOut out = noiseMapByReceiverMaker.evaluateCell(connection, i, j, progressVisitor, receivers);
                    if(out instanceof NoiseMap) {
                        NoiseMap rout = (NoiseMap) out;

                        Attenuation.SourceReceiverAttenuation sl = rout.attenuatedPaths.lDenLevels.pop();
                        assertEquals(1, sl.receiverId);
                        assertEquals(73.3, sl.value[0], 1);
                        sl = rout.attenuatedPaths.lDenLevels.pop();
                        assertEquals(2, sl.receiverId);
                        assertEquals(53.3, sl.value[0], 1);
                        assertTrue(rout.attenuatedPaths.lDenLevels.isEmpty());

                        List<CnossosPath> pathsParameters = rout.getPropagationPaths();
                        assertEquals(2 , pathsParameters.size());

                        CnossosPath pathParameters = pathsParameters.remove(0);
                        assertEquals(1, pathParameters.getIdReceiver());
                        assertEquals(new Orientation(90, 15, 0), pathParameters.getSourceOrientation());
                        pathParameters = pathsParameters.remove(0);
                        assertEquals(2, pathParameters.getIdReceiver());
                        assertEquals(new Orientation(90, 15, 0), pathParameters.getSourceOrientation());

                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static void assertOrientationEquals(Orientation orientationA, Orientation orientationB, double epsilon) {
        assertArrayEquals(new double[]{orientationA.yaw, orientationA.pitch, orientationA.roll},
                new double[]{orientationB.yaw, orientationB.pitch, orientationB.roll}, epsilon, orientationA+" != "+orientationB);
    }


    @Test
    public void testLineDirectivity() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE BUILDINGS(pk serial PRIMARY KEY, the_geom geometry, height real)");
            st.execute(createSource(new GeometryFactory().createLineString(
                    new Coordinate[]{new Coordinate(223915.72,6757480.22 ,5),
                            new Coordinate(223920.72,6757485.22, 5.1 )}), 91,
                    new Orientation(0,0,0),4));
            st.execute("create table receivers(id serial PRIMARY KEY, the_geom GEOMETRY(pointZ));\n" +
                    "insert into receivers(the_geom) values ('POINTZ (223922.55 6757495.27 4.0)');" +
                    "insert into receivers(the_geom) values ('POINTZ (223936.42 6757471.91 4.0)');");
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS", "ROADS_GEOM", "RECEIVERS");
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(false);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(false);
            noiseMapByReceiverMaker.setSoundReflectionOrder(0);
            noiseMapByReceiverMaker.setReceiverHasAbsoluteZCoordinates(false);
            noiseMapByReceiverMaker.setMaximumPropagationDistance(1000);
            noiseMapByReceiverMaker.setSourceHasAbsoluteZCoordinates(false);
            noiseMapByReceiverMaker.setHeightField("HEIGHT");

            NoiseMapParameters noiseMapParameters = new NoiseMapParameters(NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_DEN);
            noiseMapParameters.setCoefficientVersion(1);
            noiseMapParameters.setExportAttenuationMatrix(false);
            noiseMapParameters.setExportRaysMethod(org.noise_planet.noisemodelling.jdbc.NoiseMapParameters.ExportRaysMethods.TO_MEMORY);
            NoiseMapMaker noiseMapMaker = new NoiseMapMaker(connection, noiseMapParameters);
            // Use train directivity functions instead of discrete directivity
            noiseMapMaker.insertTrainDirectivity();
            noiseMapByReceiverMaker.setPropagationProcessDataFactory(noiseMapMaker);
            noiseMapByReceiverMaker.setComputeRaysOutFactory(noiseMapMaker);

            Set<Long> receivers = new HashSet<>();
            noiseMapByReceiverMaker.setThreadCount(1);
            RootProgressVisitor progressVisitor = new RootProgressVisitor(noiseMapByReceiverMaker.getGridDim() * noiseMapByReceiverMaker.getGridDim(), true, 5);

            noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor());

            Envelope compEnv = new Envelope(new Coordinate(223915.72,6757480.22 ,5));
            compEnv.expandBy(500);
            noiseMapByReceiverMaker.setMainEnvelope(compEnv);

            noiseMapByReceiverMaker.setGridDim(1);

            for(int i=0; i < noiseMapByReceiverMaker.getGridDim(); i++) {
                for(int j=0; j < noiseMapByReceiverMaker.getGridDim(); j++) {
                    //System.out.println("here");
                    IComputePathsOut out = noiseMapByReceiverMaker.evaluateCell(connection, i, j, progressVisitor, receivers);
                    if(out instanceof NoiseMap) {
                        NoiseMap rout = (NoiseMap) out;

                        assertEquals(2, rout.attenuatedPaths.lDenLevels.size());

                        Attenuation.SourceReceiverAttenuation sl = rout.attenuatedPaths.lDenLevels.pop();
                        assertEquals(1, sl.receiverId);
                        assertEquals(68.3, sl.value[0], 1);
                        sl = rout.attenuatedPaths.lDenLevels.pop();
                        assertEquals(2, sl.receiverId);
                        assertEquals(70.8, sl.value[0], 1);

                        assertEquals(3 , rout.pathParameters.size());
                        List<CnossosPath> pathsParameters = rout.getPropagationPaths();
                        //System.out.println("size "+pathsParameters.size());
                        CnossosPath pathParameters = pathsParameters.remove(0);
                        assertEquals(1, pathParameters.getIdReceiver());
                        assertEquals(0, new Coordinate(0, 5.07).distance(pathParameters.getPointList().get(0).coordinate), 0.1);
                        // This is source orientation, not relevant to receiver position
                        assertOrientationEquals(new Orientation(45, 0.81, 0), pathParameters.getSourceOrientation(), 0.01);
                        assertOrientationEquals(new Orientation(336.9922375343167,-4.684918495003125,0.0), pathParameters.raySourceReceiverDirectivity, 0.01);

                        pathParameters = pathsParameters.remove(0);;
                        assertEquals(1, pathParameters.getIdReceiver());
                        assertEquals(0, new Coordinate(0, 5.02).
                                distance(pathParameters.getPointList().get(0).coordinate), 0.1);
                        assertOrientationEquals(new Orientation(45, 0.81, 0), pathParameters.getSourceOrientation(), 0.01);
                        assertOrientationEquals(new Orientation(330.2084079818916,-5.947213381005439,0.0), pathParameters.raySourceReceiverDirectivity, 0.01);
                        pathParameters = pathsParameters.remove(0);
                        assertEquals(2, pathParameters.getIdReceiver());
                        assertOrientationEquals(new Orientation(45, 0.81, 0), pathParameters.getSourceOrientation(), 0.01);
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }


    @Test
    public void testPointRayDirectivity() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE BUILDINGS(pk serial  PRIMARY KEY, the_geom geometry, height real)");
            // create source point direction east->90°
            st.execute(createSource(new GeometryFactory().createPoint(new Coordinate(3.5,3,1.0 )),
                    91, new Orientation(90,0,0),4));
            st.execute("create table receivers(id serial PRIMARY KEY, the_geom GEOMETRY(POINTZ));\n" +
                    "insert into receivers(the_geom) values ('POINTZ (4.5 3 1.0)');" + //front
                    "insert into receivers(the_geom) values ('POINTZ (2.5 3 1.0)');" + //behind
                    "insert into receivers(the_geom) values ('POINTZ (3.5 2 1.0)');" + //right
                    "insert into receivers(the_geom) values ('POINTZ (3.5 4 1.0)');"); //left
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS", "ROADS_GEOM", "RECEIVERS");
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(false);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(false);
            noiseMapByReceiverMaker.setSoundReflectionOrder(0);
            noiseMapByReceiverMaker.setReceiverHasAbsoluteZCoordinates(false);
            noiseMapByReceiverMaker.setMaximumPropagationDistance(1000);
            noiseMapByReceiverMaker.setSourceHasAbsoluteZCoordinates(false);
            noiseMapByReceiverMaker.setHeightField("HEIGHT");

            NoiseMapParameters noiseMapParameters = new NoiseMapParameters(org.noise_planet.noisemodelling.jdbc.NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_DEN);
            noiseMapParameters.setCoefficientVersion(1);
            noiseMapParameters.setExportAttenuationMatrix(false);
            noiseMapParameters.setExportRaysMethod(NoiseMapParameters.ExportRaysMethods.TO_MEMORY);
            NoiseMapMaker noiseMapMaker = new NoiseMapMaker(connection, noiseMapParameters);
            // Use train directivity functions instead of discrete directivity
            noiseMapMaker.insertTrainDirectivity();

            noiseMapByReceiverMaker.setPropagationProcessDataFactory(noiseMapMaker);
            noiseMapByReceiverMaker.setComputeRaysOutFactory(noiseMapMaker);

            noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor());

            Set<Long> receivers = new HashSet<>();
            noiseMapByReceiverMaker.setThreadCount(1);
            RootProgressVisitor progressVisitor = new RootProgressVisitor(noiseMapByReceiverMaker.getGridDim() * noiseMapByReceiverMaker.getGridDim(), true, 5);
            for(int i=0; i < noiseMapByReceiverMaker.getGridDim(); i++) {
                for(int j=0; j < noiseMapByReceiverMaker.getGridDim(); j++) {
                    IComputePathsOut out = noiseMapByReceiverMaker.evaluateCell(connection, i, j, progressVisitor, receivers);
                    if(out instanceof NoiseMap) {
                        NoiseMap rout = (NoiseMap) out;
                        List<CnossosPath> pathsParameters = rout.getPropagationPaths();
                        assertEquals(4 , pathsParameters.size());
                        CnossosPath pathParameters = pathsParameters.remove(0);
                        assertEquals(1, pathParameters.getIdReceiver());
                        // receiver is front of source
                        assertEquals(new Orientation(0, 0, 0), pathParameters.getRaySourceReceiverDirectivity());
                        pathParameters = pathsParameters.remove(0);
                        assertEquals(2, pathParameters.getIdReceiver());
                        // receiver is behind of the source
                        assertEquals(new Orientation(180, 0, 0), pathParameters.getRaySourceReceiverDirectivity());
                        pathParameters = pathsParameters.remove(0);
                        assertEquals(3, pathParameters.getIdReceiver());
                        // receiver is on the right of the source
                        assertEquals(new Orientation(90, 0, 0), pathParameters.getRaySourceReceiverDirectivity());
                        pathParameters = pathsParameters.remove(0);
                        assertEquals(4, pathParameters.getIdReceiver());
                        // receiver is on the left of the source
                        assertEquals(new Orientation(360-90, 0, 0), pathParameters.getRaySourceReceiverDirectivity());
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }
}