package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.api.ProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;
import org.noise_planet.noisemodelling.pathfinder.IComputePathsOut;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.noise_planet.noisemodelling.propagation.Attenuation;

import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.jdbc.Utils.getRunScriptRes;

public class RegressionTest {

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
     * Got reflection index out of bound exception in this scenario in the past (source->reflection->h diffraction->receiver)
     */
    @Test
    public void testScenarioOutOfBoundException() throws Exception {
        try(Statement st = connection.createStatement()) {
            st.execute(getRunScriptRes("regression_nan/lw_roads.sql"));

            // Init NoiseModelling
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS",
                    "LW_ROADS", "RECEIVERS");

            noiseMapByReceiverMaker.setMaximumPropagationDistance(500.0);
            noiseMapByReceiverMaker.setSoundReflectionOrder(1);
            noiseMapByReceiverMaker.setThreadCount(1);
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(true);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(true);
            // Building height field name
            noiseMapByReceiverMaker.setHeightField("HEIGHT");


            // Init custom input in order to compute more than just attenuation
            // LW_ROADS contain Day Evening Night emission spectrum
            NoiseMapParameters noiseMapParameters = new NoiseMapParameters(NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_DEN);
            noiseMapParameters.setExportRaysMethod(NoiseMapParameters.ExportRaysMethods.TO_MEMORY);

            noiseMapParameters.setComputeLDay(false);
            noiseMapParameters.setComputeLEvening(false);
            noiseMapParameters.setComputeLNight(false);
            noiseMapParameters.setComputeLDEN(true);
            noiseMapParameters.keepAbsorption = true;

            NoiseMapMaker noiseMapMaker = new NoiseMapMaker(connection, noiseMapParameters);

            noiseMapByReceiverMaker.setPropagationProcessDataFactory(noiseMapMaker);
            noiseMapByReceiverMaker.setComputeRaysOutFactory(noiseMapMaker);

            RootProgressVisitor progressLogger = new RootProgressVisitor(1, true, 1);

            noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor());

            noiseMapParameters.getPropagationProcessPathData(NoiseMapParameters.TIME_PERIOD.DAY).setTemperature(20);
            noiseMapParameters.getPropagationProcessPathData(NoiseMapParameters.TIME_PERIOD.EVENING).setTemperature(16);
            noiseMapParameters.getPropagationProcessPathData(NoiseMapParameters.TIME_PERIOD.NIGHT).setTemperature(10);

            noiseMapByReceiverMaker.setGridDim(1);

            // Set of already processed receivers
            Set<Long> receivers = new HashSet<>();

            // Fetch cell identifiers with receivers
            Map<CellIndex, Integer> cells = noiseMapByReceiverMaker.searchPopulatedCells(connection);
            ProgressVisitor progressVisitor = progressLogger.subProcess(cells.size());
            assertEquals(1, cells.size());
            for(CellIndex cellIndex : new TreeSet<>(cells.keySet())) {
                // Run ray propagation
                IComputePathsOut out = noiseMapByReceiverMaker.evaluateCell(connection, cellIndex.getLatitudeIndex(),
                        cellIndex.getLongitudeIndex(), progressVisitor, receivers);
                assertInstanceOf(NoiseMap.class, out);
                NoiseMap rout = (NoiseMap) out;
                assertEquals(1, rout.attenuatedPaths.lDenLevels.size());
                Attenuation.SourceReceiverAttenuation sl = rout.attenuatedPaths.lDenLevels.pop();
                assertEquals(36.77, AcousticIndicatorsFunctions.sumDbArray(sl.value), AttenuationCnossosTest.ERROR_EPSILON_LOWEST);
            }


        }
    }


}
