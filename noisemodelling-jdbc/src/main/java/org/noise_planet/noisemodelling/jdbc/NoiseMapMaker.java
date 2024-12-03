/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.utilities.GeometryTableUtilities;
import org.h2gis.utilities.JDBCUtilities;
import org.noise_planet.noisemodelling.emission.LineSource;
import org.noise_planet.noisemodelling.emission.directivity.DirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.cnossos.RailwayCnossosDirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.OmnidirectionalDirection;
import org.noise_planet.noisemodelling.pathfinder.*;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProfilerThread;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 *
 */
public class NoiseMapMaker implements NoiseMapByReceiverMaker.PropagationProcessDataFactory, NoiseMapByReceiverMaker.IComputeRaysOutFactory, ProfilerThread.Metric {
    NoiseMapParameters noiseMapParameters;
    NoiseMapWriter noiseMapWriter;
    Thread tableWriterThread;
    Connection connection;
    static final int BATCH_MAX_SIZE = 500;
    static final int WRITER_CACHE = 65536;
    AttenuatedPaths AttenuatedPaths = new AttenuatedPaths();
    int srid;
    List<String> noiseSource = Arrays.asList("ROLLING","TRACTIONA", "TRACTIONB","AERODYNAMICA","AERODYNAMICB","BRIDGE");


    /**
     * Attenuation and other attributes relative to direction on sphere
     */
    public Map<Integer, DirectivitySphere> directionAttributes = new HashMap<>();


    public NoiseMapMaker(Connection connection, NoiseMapParameters NoiseMapParameters) {
        this.noiseMapParameters = NoiseMapParameters;
        this.connection = connection;
    }

    @Override
    public String[] getColumnNames() {
        return new String[] {"jdbc_stack"};
    }

    @Override
    public String[] getCurrentValues() {
        return new String[] {Long.toString(AttenuatedPaths.queueSize.get())};
    }

    @Override
    public void tick(long currentMillis) {

    }

    public AttenuatedPaths getLdenData() {
        return AttenuatedPaths;
    }

    /**
     * Inserts directivity attributes for noise sources for trains into the directionAttributes map.
     */

    public void insertTrainDirectivity() {
        directionAttributes.clear();
        directionAttributes.put(0, new OmnidirectionalDirection());
        int i=1;
        for(String typeSource : noiseSource) {
            directionAttributes.put(i, new RailwayCnossosDirectivitySphere(new LineSource(typeSource)));
            i++;
        }
    }

    /**
     * Initializes the NoiseMap parameters and attenuation data based on the input mode specified in the NoiseMap parameters.
     * @param connection   the database connection to be used for initialization.
     * @param noiseMapByReceiverMaker the noise map by receiver maker object associated with the computation process.
     * @throws SQLException
     */

    @Override
    public void initialize(Connection connection, NoiseMapByReceiverMaker noiseMapByReceiverMaker) throws SQLException {
        if(noiseMapParameters.input_mode == org.noise_planet.noisemodelling.jdbc.NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_DEN) {
            // Fetch source fields
            List<String> sourceField = JDBCUtilities.getColumnNames(connection, noiseMapByReceiverMaker.getSourcesTableName());
            this.srid = GeometryTableUtilities.getSRID(connection, noiseMapByReceiverMaker.getSourcesTableName());
            List<Integer> frequencyValues = new ArrayList<>();
            List<Integer> allFrequencyValues = Arrays.asList(Scene.DEFAULT_FREQUENCIES_THIRD_OCTAVE);
            String period = "";
            if (noiseMapParameters.computeLDay || noiseMapParameters.computeLDEN) {
                period = "D";
            } else if (noiseMapParameters.computeLEvening) {
                period = "E";
            } else if (noiseMapParameters.computeLNight) {
                period = "N";
            }
            String freqField = noiseMapParameters.lwFrequencyPrepend + period;
            if (!period.isEmpty()) {
                for (String fieldName : sourceField) {
                    if (fieldName.startsWith(freqField)) {
                        int freq = Integer.parseInt(fieldName.substring(freqField.length()));
                        int index = allFrequencyValues.indexOf(freq);
                        if (index >= 0) {
                            frequencyValues.add(freq);
                        }
                    }
                }
            }
            // Sort frequencies values
            Collections.sort(frequencyValues);
            // Get associated values for each frequency
            List<Double> exactFrequencies = new ArrayList<>();
            List<Double> aWeighting = new ArrayList<>();
            for (int freq : frequencyValues) {
                int index = allFrequencyValues.indexOf(freq);
                exactFrequencies.add(Scene.DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE[index]);
                aWeighting.add(Scene.DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE[index]);
            }
            if(frequencyValues.isEmpty()) {
                throw new SQLException("Source table "+ noiseMapByReceiverMaker.getSourcesTableName()+" does not contains any frequency bands");
            }
            // Instance of PropagationProcessPathData maybe already set
            for(NoiseMapParameters.TIME_PERIOD timePeriod : NoiseMapParameters.TIME_PERIOD.values()) {
                if (noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod) == null) {
                    AttenuationCnossosParameters attenuationCnossosParameters = new AttenuationCnossosParameters(frequencyValues, exactFrequencies, aWeighting);
                    noiseMapParameters.setPropagationProcessPathData(timePeriod, attenuationCnossosParameters);
                    noiseMapByReceiverMaker.setPropagationProcessPathData(timePeriod, attenuationCnossosParameters);
                } else {
                    noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod).setFrequencies(frequencyValues);
                    noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod).setFrequenciesExact(exactFrequencies);
                    noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod).setFrequenciesAWeighting(aWeighting);
                    noiseMapParameters.setPropagationProcessPathData(timePeriod, noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod));
                }
            }
        } else if(noiseMapParameters.input_mode == org.noise_planet.noisemodelling.jdbc.NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_HZ) {
            // Fetch source fields
            List<String> sourceField = JDBCUtilities.getColumnNames(connection, noiseMapByReceiverMaker.getSourcesTableName());
            this.srid = GeometryTableUtilities.getSRID(connection, noiseMapByReceiverMaker.getSourcesTableName());
            List<Integer> frequencyValues = new ArrayList<>();
            List<Integer> allFrequencyValues = Arrays.asList(Scene.DEFAULT_FREQUENCIES_THIRD_OCTAVE);

            String freqField = "HZ";

            for (String fieldName : sourceField) {
                if (fieldName.startsWith(freqField)) {
                    int freq = Integer.parseInt(fieldName.substring(freqField.length()));
                    int index = allFrequencyValues.indexOf(freq);
                    if (index >= 0) {
                        frequencyValues.add(freq);
                    }
                }
            }

            // Sort frequencies values
            Collections.sort(frequencyValues);
            // Get associated values for each frequency
            List<Double> exactFrequencies = new ArrayList<>();
            List<Double> aWeighting = new ArrayList<>();
            for (int freq : frequencyValues) {
                int index = allFrequencyValues.indexOf(freq);
                exactFrequencies.add(Scene.DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE[index]);
                aWeighting.add(Scene.DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE[index]);
            }
            if(frequencyValues.isEmpty()) {
                throw new SQLException("Source table "+ noiseMapByReceiverMaker.getSourcesTableName()+" does not contains any frequency bands");
            }
            // Instance of PropagationProcessPathData maybe already set
            for(NoiseMapParameters.TIME_PERIOD timePeriod : NoiseMapParameters.TIME_PERIOD.values()) {
                if (noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod) == null) {
                    AttenuationCnossosParameters attenuationCnossosParameters = new AttenuationCnossosParameters(frequencyValues, exactFrequencies, aWeighting);
                    noiseMapParameters.setPropagationProcessPathData(timePeriod, attenuationCnossosParameters);
                    noiseMapByReceiverMaker.setPropagationProcessPathData(timePeriod, attenuationCnossosParameters);
                } else {
                    noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod).setFrequencies(frequencyValues);
                    noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod).setFrequenciesExact(exactFrequencies);
                    noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod).setFrequenciesAWeighting(aWeighting);
                    noiseMapParameters.setPropagationProcessPathData(timePeriod, noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod));
                }
            }
        } else {
            for(NoiseMapParameters.TIME_PERIOD timePeriod : NoiseMapParameters.TIME_PERIOD.values()) {
                if (noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod) == null) {
                    // Traffic flow cnossos frequencies are octave bands from 63 to 8000 Hz
                    AttenuationCnossosParameters attenuationCnossosParameters = new AttenuationCnossosParameters(false);
                    noiseMapParameters.setPropagationProcessPathData(timePeriod, attenuationCnossosParameters);
                    noiseMapByReceiverMaker.setPropagationProcessPathData(timePeriod, attenuationCnossosParameters);
                } else {
                    noiseMapParameters.setPropagationProcessPathData(timePeriod, noiseMapByReceiverMaker.getPropagationProcessPathData(timePeriod));
                }
            }
        }
    }

    /**
     * Start creating and filling database tables
     */
    public void start() {
        if(noiseMapParameters.getPropagationProcessPathData(NoiseMapParameters.TIME_PERIOD.DAY) == null) {
            throw new IllegalStateException("start() function must be called after NoiseMapByReceiverMaker initialization call");
        }
        noiseMapWriter = new NoiseMapWriter(connection, noiseMapParameters, AttenuatedPaths, srid);
        noiseMapParameters.exitWhenDone = false;
        tableWriterThread = new Thread(noiseMapWriter);
        tableWriterThread.start();
        while (!noiseMapWriter.started && !noiseMapParameters.aborted) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                // ignore
                break;
            }
        }
    }

    /**
     * Write the last results and stop the sql writing thread
     */
    public void stop() {
        noiseMapParameters.exitWhenDone = true;
        while (tableWriterThread != null && tableWriterThread.isAlive()) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                // ignore
                break;
            }
        }
    }


    /**
     * Creates a new instance of NoiseEmissionMaker using the provided ProfileBuilder and NoiseMapParameters.
     * @param builder the profile builder used to construct the scene.
     * @return A new instance of NoiseEmissionMaker initialized with the provided ProfileBuilder and NoiseMapParameters.
     */
    @Override
    public NoiseEmissionMaker create(ProfileBuilder builder) {
        NoiseEmissionMaker noiseEmissionMaker = new NoiseEmissionMaker(builder, noiseMapParameters);
        noiseEmissionMaker.setDirectionAttributes(directionAttributes);
        return noiseEmissionMaker;
    }

    /**
     *  Creates a new instance of IComputePathsOut using the provided Scene data and AttenuationCnossosParameters for different time periods.
     * @param threadData       the scene data for the current computation thread.
     * @param pathDataDay      the attenuation parameters for daytime computation.
     * @param pathDataEvening  the attenuation parameters for evening computation.
     * @param pathDataNight    the attenuation parameters for nighttime computation.
     * @return A new instance of IComputePathsOut initialized with the provided parameters.
     */
    @Override
    public IComputePathsOut create(Scene threadData, AttenuationCnossosParameters pathDataDay,
                                   AttenuationCnossosParameters pathDataEvening, AttenuationCnossosParameters pathDataNight) {
        return new NoiseMap(pathDataDay, pathDataEvening, pathDataNight,
                (NoiseEmissionMaker)threadData, AttenuatedPaths, noiseMapParameters);
    }


}
