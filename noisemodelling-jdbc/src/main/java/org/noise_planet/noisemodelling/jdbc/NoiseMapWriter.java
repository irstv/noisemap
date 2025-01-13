/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.noise_planet.noisemodelling.jdbc.utils.StringPreparedStatements;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.propagation.Attenuation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.zip.GZIPOutputStream;

import static org.noise_planet.noisemodelling.jdbc.NoiseMapMaker.BATCH_MAX_SIZE;
import static org.noise_planet.noisemodelling.jdbc.NoiseMapMaker.WRITER_CACHE;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.*;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.dbaToW;

/**
 * Process that run SQL query to feed tables
 */
public class NoiseMapWriter implements Runnable {
    Logger LOGGER = LoggerFactory.getLogger(NoiseMapWriter.class);
    File sqlFilePath;
    private Connection connection;
    NoiseMapParameters noiseMapParameters;
    AttenuatedPaths AttenuatedPaths;
    double[] a_weighting;
    boolean started = false;
    Writer o;
    int srid;

    /**
     * Constructs a new NoiseMapWriter object with the specified parameters.
     * @param connection        the database connection used for writing data
     * @param noiseMapParameters the parameters defining the noise map computation
     * @param AttenuatedPaths   the attenuated paths containing computed noise data
     * @param srid the spatial reference identifier (SRID) for geometric data
     */
    public NoiseMapWriter(Connection connection, NoiseMapParameters noiseMapParameters, AttenuatedPaths AttenuatedPaths, int srid) {
        this.connection = connection;
        this.sqlFilePath = noiseMapParameters.sqlOutputFile;
        this.noiseMapParameters = noiseMapParameters;
        this.AttenuatedPaths = AttenuatedPaths;
        a_weighting = new double[noiseMapParameters.attenuationCnossosParametersDay.freq_lvl_a_weighting.size()];
        for(int idfreq = 0; idfreq < a_weighting.length; idfreq++) {
            a_weighting[idfreq] = noiseMapParameters.attenuationCnossosParametersDay.freq_lvl_a_weighting.get(idfreq);
        }
        this.srid = srid;
    }

    /**
     * Processes the stack of CnossosPath objects and inserts their data into the rays table.
     * @param stack the stack of CnossosPath objects containing the data to be inserted into the rays table
     * @throws SQLException if an SQL exception occurs while executing the INSERT query
     */
    void processRaysStack(ConcurrentLinkedDeque<CnossosPath> stack) throws SQLException {
        StringBuilder query = new StringBuilder("INSERT INTO " + noiseMapParameters.raysTable +
                "(the_geom , IDRECEIVER , IDSOURCE");
        if(noiseMapParameters.exportProfileInRays) {
            query.append(", GEOJSON");
        }
        if(noiseMapParameters.exportAttenuationMatrix) {
            query.append(", LEQ, PERIOD");
        }
        query.append(") VALUES (?, ?, ?");
        if(noiseMapParameters.exportProfileInRays) {
            query.append(", ?");
        }
        if(noiseMapParameters.exportAttenuationMatrix) {
            query.append(", ?, ?");
        }
        query.append(");");
        // PK, GEOM, ID_RECEIVER, ID_SOURCE
        PreparedStatement ps;
        if(sqlFilePath == null) {
            ps = connection.prepareStatement(query.toString());
        } else {
            ps = new StringPreparedStatements(o, query.toString());
        }
        int batchSize = 0;
        while(!stack.isEmpty()) {
            CnossosPath row = stack.pop();
            AttenuatedPaths.queueSize.decrementAndGet();
            int parameterIndex = 1;
            LineString lineString = row.asGeom();
            lineString.setSRID(srid);
            ps.setObject(parameterIndex++, lineString);
            ps.setLong(parameterIndex++, row.getIdReceiver());
            ps.setLong(parameterIndex++, row.getIdSource());
            if(noiseMapParameters.exportProfileInRays) {
                String geojson = "";
                try {
                    geojson = row.profileAsJSON(noiseMapParameters.geojsonColumnSizeLimit);
                } catch (IOException ex) {
                    //ignore
                }
                ps.setString(parameterIndex++, geojson);
            }
            if(noiseMapParameters.exportAttenuationMatrix) {
                double globalValue = sumDbArray(row.aGlobal);
                ps.setDouble(parameterIndex++, globalValue);
                ps.setString(parameterIndex++, row.getTimePeriod());
            }
            ps.addBatch();
            batchSize++;
            if (batchSize >= BATCH_MAX_SIZE) {
                ps.executeBatch();
                ps.clearBatch();
                batchSize = 0;
            }
        }
        if (batchSize > 0) {
            ps.executeBatch();
        }

    }

    /**
     * Pop values from stack and insert rows
     * @param tableName Table to feed
     * @param stack Stack to pop from
     * @throws SQLException Got an error
     */
    void processStack(String tableName, ConcurrentLinkedDeque<Attenuation.SourceReceiverAttenuation> stack) throws SQLException {
        StringBuilder query = new StringBuilder("INSERT INTO ");
        query.append(tableName);
        query.append(" VALUES (? "); // ID_RECEIVER
        if(!noiseMapParameters.mergeSources) {
            query.append(", ?"); // ID_SOURCE
        }
        if(noiseMapParameters.exportReceiverPosition) {
            query.append(", ?"); // THE_GEOM
        }
        if (!noiseMapParameters.computeLAEQOnly) {
            query.append(", ?".repeat(noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size())); // freq value
            query.append(", ?, ?);"); // laeq, leq
        }else{
            query.append(", ?);"); // laeq, leq
        }
        PreparedStatement ps;
        if(sqlFilePath == null) {
            ps = connection.prepareStatement(query.toString());
        } else {
            ps = new StringPreparedStatements(o, query.toString());
        }
        int batchSize = 0;
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), srid);
        while(!stack.isEmpty()) {
            Attenuation.SourceReceiverAttenuation row = stack.pop();
            AttenuatedPaths.queueSize.decrementAndGet();
            int parameterIndex = 1;
            ps.setLong(parameterIndex++, row.receiverId);
            if(!noiseMapParameters.mergeSources) {
                ps.setLong(parameterIndex++, row.sourceId);
            }
            if(noiseMapParameters.exportReceiverPosition) {
                ps.setObject(parameterIndex++,  row.receiverPosition != null ?
                        factory.createPoint(row.receiverPosition):
                        factory.createPoint());
            }
            if (!noiseMapParameters.computeLAEQOnly){
                for(int idfreq = 0; idfreq < noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size(); idfreq++) {
                    double value = row.value[idfreq];
                    if(!Double.isFinite(value)) {
                        value = -99.0;
                        row.value[idfreq] = value;
                    }
                    ps.setDouble(parameterIndex++, value);
                }

            }
            // laeq value
            double value = wToDba(sumArray(dbaToW(sumArray(row.value, a_weighting))));
            if(!Double.isFinite(value)) {
                value = -99;
            }
            ps.setDouble(parameterIndex++, value);

            // leq value
            if (!noiseMapParameters.computeLAEQOnly) {
                ps.setDouble(parameterIndex++, wToDba(sumArray(dbaToW(row.value))));
            }

            ps.addBatch();
            batchSize++;
            if (batchSize >= BATCH_MAX_SIZE) {
                ps.executeBatch();
                ps.clearBatch();
                batchSize = 0;
            }
        }
        if (batchSize > 0) {
            ps.executeBatch();
        }
    }

    /**
     * Generates the SQL statement for creating a table based on the specified table name and configuration parameters.
     * @param tableName the name of the table to create
     * @return the SQL statement for creating the table
     */
    private String forgeCreateTable(String tableName) {
        StringBuilder sb = new StringBuilder("create table ");
        sb.append(tableName);
        if(!noiseMapParameters.mergeSources) {
            sb.append(" (IDRECEIVER bigint NOT NULL");
            sb.append(", IDSOURCE bigint NOT NULL");
        } else {
            sb.append(" (IDRECEIVER bigint NOT NULL");
        }
        if(noiseMapParameters.exportReceiverPosition) {
            sb.append(", THE_GEOM GEOMETRY(POINTZ,");
            sb.append(srid);
            sb.append(")");
        }
        if (noiseMapParameters.computeLAEQOnly){
            sb.append(", LAEQ REAL");
            sb.append(");");
        } else {
            for (int idfreq = 0; idfreq < noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size(); idfreq++) {
                sb.append(", HZ");
                sb.append(noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.get(idfreq));
                sb.append(" REAL");
            }
            sb.append(", LAEQ REAL, LEQ REAL");
            sb.append(");");
        }
        return sb.toString();
    }

    /**
     * Creates a primary key or index on the specified table depending on the configuration.
     * @param tableName
     * @return the SQL statement for creating the primary key or index     */
    private String forgePkTable(String tableName) {
        if (noiseMapParameters.mergeSources) {
            return "ALTER TABLE " + tableName + " ADD PRIMARY KEY(IDRECEIVER);";
        } else {
            return "CREATE INDEX ON " + tableName + " (IDRECEIVER);";
        }
    }

    /**
     * Executes the specified SQL query.
     * @param query
     * @throws SQLException
     * @throws IOException
     */
    private void processQuery(String query) throws SQLException, IOException {
        if(sqlFilePath == null) {
            try(Statement sql = connection.createStatement()) {
                sql.execute(query);
            }
        } else {
            o.write(query+"\n");
        }
    }

    /**
     * Initializes the noise map calculation by setting up required database tables based on the specified parameters.
     * @throws SQLException
     * @throws IOException
     */
    public void init() throws SQLException, IOException {
        if(noiseMapParameters.getExportRaysMethod() == org.noise_planet.noisemodelling.jdbc.NoiseMapParameters.ExportRaysMethods.TO_RAYS_TABLE) {
            if(noiseMapParameters.dropResultsTable) {
                String q = String.format("DROP TABLE IF EXISTS %s;", noiseMapParameters.raysTable);
                processQuery(q);
            }
            StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS " + noiseMapParameters.raysTable + "(pk bigint auto_increment, the_geom " +
                    "geometry(LINESTRING Z,");
            sb.append(srid);
            sb.append("), IDRECEIVER bigint NOT NULL, IDSOURCE bigint NOT NULL");
            if(noiseMapParameters.exportProfileInRays) {
                sb.append(", GEOJSON VARCHAR");
            }
            if(noiseMapParameters.exportAttenuationMatrix) {
                sb.append(", LEQ DOUBLE, PERIOD VARCHAR");
            }
            sb.append(");");
            processQuery(sb.toString());
        }
        if(noiseMapParameters.computeLDay) {
            if(noiseMapParameters.dropResultsTable) {
                String q = String.format("DROP TABLE IF EXISTS %s;", noiseMapParameters.lDayTable);
                processQuery(q);
            }
            String q = forgeCreateTable(noiseMapParameters.lDayTable);
            processQuery(q);
        }
        if(noiseMapParameters.computeLEvening) {
            if(noiseMapParameters.dropResultsTable) {
                String q = String.format("DROP TABLE IF EXISTS %s;", noiseMapParameters.lEveningTable);
                processQuery(q);
            }
            String q = forgeCreateTable(noiseMapParameters.lEveningTable);
            processQuery(q);
        }
        if(noiseMapParameters.computeLNight) {
            if(noiseMapParameters.dropResultsTable) {
                String q = String.format("DROP TABLE IF EXISTS %s;", noiseMapParameters.lNightTable);
                processQuery(q);
            }
            String q = forgeCreateTable(noiseMapParameters.lNightTable);
            processQuery(q);
        }
        if(noiseMapParameters.computeLDEN) {
            if(noiseMapParameters.dropResultsTable) {
                String q = String.format("DROP TABLE IF EXISTS %s;", noiseMapParameters.lDenTable);
                processQuery(q);
            }
            String q = forgeCreateTable(noiseMapParameters.lDenTable);
            processQuery(q);
        }
    }

    /**
     * Main loop for processing attenuated paths and stacking results.
     * @throws SQLException
     * @throws IOException
     */
    void mainLoop() throws SQLException, IOException {
        while (!noiseMapParameters.aborted) {
            started = true;
            try {
                if(!AttenuatedPaths.lDayLevels.isEmpty()) {
                    processStack(noiseMapParameters.lDayTable, AttenuatedPaths.lDayLevels);
                } else if(!AttenuatedPaths.lEveningLevels.isEmpty()) {
                    processStack(noiseMapParameters.lEveningTable, AttenuatedPaths.lEveningLevels);
                } else if(!AttenuatedPaths.lNightLevels.isEmpty()) {
                    processStack(noiseMapParameters.lNightTable, AttenuatedPaths.lNightLevels);
                } else if(!AttenuatedPaths.lDenLevels.isEmpty()) {
                    processStack(noiseMapParameters.lDenTable, AttenuatedPaths.lDenLevels);
                } else if(!AttenuatedPaths.rays.isEmpty()) {
                    processRaysStack(AttenuatedPaths.rays);
                } else {
                    if(noiseMapParameters.exitWhenDone) {
                        break;
                    } else {
                        Thread.sleep(50);
                    }
                }
            } catch (InterruptedException ex) {
                // ignore
                break;
            }
        }
    }

    /**
     * Creates primary keys for the computed noise level tables.
     * @throws SQLException
     * @throws IOException
     */
    void createKeys()  throws SQLException, IOException {
        // Set primary keys
        LOGGER.info("Write done, apply primary keys");
        if(noiseMapParameters.computeLDay) {
            processQuery(forgePkTable(noiseMapParameters.lDayTable));
        }
        if(noiseMapParameters.computeLEvening) {
            processQuery(forgePkTable(noiseMapParameters.lEveningTable));
        }
        if(noiseMapParameters.computeLNight) {
            processQuery(forgePkTable(noiseMapParameters.lNightTable));
        }
        if(noiseMapParameters.computeLDEN) {
            processQuery(forgePkTable(noiseMapParameters.lDenTable));
        }
    }

    /**
     * Gets an OutputStreamWriter for writing data to a file stream.
     * @return an OutputStreamWriter for writing data to a file stream
     * @throws IOException if an I/O error occurs while creating the stream
     */
    OutputStreamWriter getStream() throws IOException {
        if(noiseMapParameters.sqlOutputFileCompression) {
            return new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(sqlFilePath), WRITER_CACHE));
        } else {
            return new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(sqlFilePath), WRITER_CACHE));
        }
    }

    /**
     * Executes the SQL writing process.
     */
    @Override
    public void run() {
        // Drop and create tables
        if(sqlFilePath == null) {
            try {
                init();
                mainLoop();
                createKeys();
            } catch (SQLException e) {
                LOGGER.error("SQL Writer exception", e);
                LOGGER.error(e.getLocalizedMessage(), e.getNextException());
                noiseMapParameters.aborted = true;
            } catch (Throwable e) {
                LOGGER.error("Got exception on result writer, cancel calculation", e);
                noiseMapParameters.aborted = true;
            }
        } else {
            try(OutputStreamWriter bw = getStream()) {
                o = bw;
                init();
                mainLoop();
                createKeys();
            } catch (SQLException e) {
                LOGGER.error("SQL Writer exception", e);
                LOGGER.error(e.getLocalizedMessage(), e.getNextException());
                noiseMapParameters.aborted = true;
            } catch (Throwable e) {
                LOGGER.error("Got exception on result writer, cancel calculation", e);
                noiseMapParameters.aborted = true;
            }
        }
        // LOGGER.info("Exit TableWriter");
    }
}