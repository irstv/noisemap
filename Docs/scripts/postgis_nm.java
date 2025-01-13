package org.noise_planet.nmtutorial01;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.api.ProgressVisitor;
import org.h2gis.functions.io.csv.CSVDriverFunction;
import org.h2gis.functions.io.geojson.GeoJsonRead;
import org.h2gis.postgis_jts_osgi.DataSourceFactoryImpl;
import org.h2gis.utilities.SFSUtilities;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.jdbc.NoiseMapParameters
import org.noise_planet.noisemodelling.jdbc.NoiseMapMaker;
import org.noise_planet.noisemodelling.propagation.ComputeRaysOut;
import org.noise_planet.noisemodelling.propagation.IComputeRaysOut;
import org.noise_planet.noisemodelling.propagation.RootProgressVisitor;
import org.noise_planet.noisemodelling.jdbc.NoiseMapByReceiverMaker;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.Assert.assertTrue;

public class Main {
    static Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main() throws Exception {
        DataSourceFactoryImpl dataSourceFactory = new DataSourceFactoryImpl();
        Properties p = new Properties();
        p.setProperty("serverName", "localhost");
        p.setProperty("portNumber", "5432");
        p.setProperty("databaseName", "postgres");
        p.setProperty("user", "postgres");
        p.setProperty("password", "");
        try(Connection connection = SFSUtilities.wrapConnection(dataSourceFactory.createDataSource(p).getConnection())) {
            Statement sql = connection.createStatement();

            // Clean DB

            sql.execute("DROP TABLE IF EXISTS BUILDINGS");
            sql.execute("DROP TABLE IF EXISTS LW_ROADS");
            sql.execute("DROP TABLE IF EXISTS RECEIVERS");
            sql.execute("DROP TABLE IF EXISTS DEM");

            // Import BUILDINGS

            LOGGER.info("Import buildings");

            GeoJsonRead.readGeoJson(connection, Main.class.getResource("buildings.geojson").getFile(), "BUILDINGS");

            // Import noise source

            LOGGER.info("Import noise source");

            GeoJsonRead.readGeoJson(connection, Main.class.getResource("lw_roads.geojson").getFile(), "lw_roads");
            // Set primary key
            sql.execute("ALTER TABLE lw_roads ADD CONSTRAINT lw_roads_pk PRIMARY KEY (\"PK\");");

            // Import BUILDINGS

            LOGGER.info("Import evaluation coordinates");

            GeoJsonRead.readGeoJson(connection, Main.class.getResource("receivers.geojson").getFile(), "receivers");
            // Set primary key
            sql.execute("ALTER TABLE receivers ADD CONSTRAINT RECEIVERS_pk PRIMARY KEY (\"PK\");");

            // Import MNT

            LOGGER.info("Import digital elevation model");

            GeoJsonRead.readGeoJson(connection, Main.class.getResource("dem_lorient.geojson").getFile(), "dem");

            // Init NoiseModelling
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("buildings", "lw_roads", "receivers");

            noiseMapByReceiverMaker.setMaximumPropagationDistance(160.0d);
            noiseMapByReceiverMaker.setSoundReflectionOrder(0);
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(true);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(true);
            // Building height field name
            noiseMapByReceiverMaker.setHeightField("HEIGHT");
            // Point cloud height above sea level POINT(X Y Z)
            noiseMapByReceiverMaker.setDemTable("DEM");
            // Do not propagate for low emission or far away sources.
            // error in dB
            noiseMapByReceiverMaker.setMaximumError(0.1d);

            // Init custom input in order to compute more than just attenuation
            // LW_ROADS contain Day Evening Night emission spectrum
            NoiseMapParameters noiseMapParameters = new NoiseMapParameters(NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_DEN);

            noiseMapParameters.setComputeLDay(true);
            noiseMapParameters.setComputeLEvening(true);
            noiseMapParameters.setComputeLNight(true);
            noiseMapParameters.setComputeLDEN(true);

            NoiseMapMaker tableWriter = new NoiseMapMaker(connection, noiseMapParameters);

            tableWriter.setKeepRays(true);

            noiseMapByReceiverMaker.setPropagationProcessDataFactory(tableWriter);
            noiseMapByReceiverMaker.setComputeRaysOutFactory(tableWriter);

            RootProgressVisitor progressLogger = new RootProgressVisitor(1, true, 1);

            noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor());

            // force the creation of a 2x2 cells
            noiseMapByReceiverMaker.setGridDim(2);


            // Set of already processed receivers
            Set<Long> receivers = new HashSet<>();
            ProgressVisitor progressVisitor = progressLogger.subProcess(noiseMapByReceiverMaker.getGridDim()*noiseMapByReceiverMaker.getGridDim());
            LOGGER.info("start");
            long start = System.currentTimeMillis();

            // Iterate over computation areas
            try {
                tableWriter.start();
                for (int i = 0; i < noiseMapByReceiverMaker.getGridDim(); i++) {
                    for (int j = 0; j < noiseMapByReceiverMaker.getGridDim(); j++) {
                        // Run ray propagation
                        IComputeRaysOut out = noiseMapByReceiverMaker.evaluateCell(connection, i, j, progressVisitor, receivers);
                    }
                }
            } finally {
                tableWriter.stop();
            }
            long computationTime = System.currentTimeMillis() - start;
            logger.info(String.format(Locale.ROOT, "Computed in %d ms, %.2f ms per receiver",
                    computationTime,computationTime / (double)receivers.size()));
            // Export result tables as csv files
            CSVDriverFunction csv = new CSVDriverFunction();
            csv.exportTable(connection, noiseMapParameters.getlDayTable(), new File(noiseMapParameters.getlDayTable()+".csv"), new EmptyProgressVisitor());
            csv.exportTable(connection, noiseMapParameters.getlEveningTable(), new File(noiseMapParameters.getlEveningTable()+".csv"), new EmptyProgressVisitor());
            csv.exportTable(connection, noiseMapParameters.getlNightTable(), new File(noiseMapParameters.getlNightTable()+".csv"), new EmptyProgressVisitor());
            csv.exportTable(connection, noiseMapParameters.getlDenTable(), new File(noiseMapParameters.getlDenTable()+".csv"), new EmptyProgressVisitor());
        } catch (PSQLException ex) {
            if (ex.getCause() instanceof ConnectException) {
                // Connection issue ignore
                LOGGER.warn("Connection error to local PostGIS, ignored", ex);
            } else {
                throw ex;
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex.getNextException());
            throw ex;
        }
    }
}
