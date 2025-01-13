/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.spatial.convert.ST_Force3D;
import org.h2gis.functions.spatial.edit.ST_UpdateZ;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.SpatialResultSet;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.noise_planet.noisemodelling.emission.directivity.DirectivitySphere;
import org.noise_planet.noisemodelling.emission.railway.RailWayParameters;
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossos;
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossosParameters;
import org.noise_planet.noisemodelling.emission.utils.Utils;
import org.noise_planet.noisemodelling.jdbc.railway.RailWayLWGeom;
import org.noise_planet.noisemodelling.jdbc.railway.RailWayLWIterator;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.dbaToW;

/**
 * Read source database and compute the sound emission spectrum of roads sources
 */
public class NoiseEmissionMaker extends Scene {
    public Map<String, Integer> sourceFields = null;

    // Source value in energetic  e = pow(10, dbVal / 10.0)
    public List<double[]> wjSourcesD = new ArrayList<>();
    public List<double[]> wjSourcesE = new ArrayList<>();
    public List<double[]> wjSourcesN = new ArrayList<>();

    /**
     * Attenuation and other attributes relative to direction on sphere
     */
    public Map<Integer, DirectivitySphere> directionAttributes = new HashMap<>();

    public NoiseMapParameters noiseMapParameters;

    /**
     *  Create NoiseEmissionMaker constructor
     * @param builder
     * @param noiseMapParameters
     */
    public NoiseEmissionMaker(ProfileBuilder builder, NoiseMapParameters noiseMapParameters) {
        super(builder, noiseMapParameters.attenuationCnossosParametersDay.freq_lvl);
        this.noiseMapParameters = noiseMapParameters;
    }

    /**
     * Generate Train emission from train geometry tracks and train traffic
     * @param connection
     * @param railSectionTableName
     * @param railTrafficTableName
     * @param outputTable
     * @throws SQLException
     */
    public static void makeTrainLWTable(Connection connection, String railSectionTableName, String railTrafficTableName, String outputTable) throws SQLException {

        // drop table LW_RAILWAY if exists and the create and prepare the table
        connection.createStatement().execute("drop table if exists " + outputTable);

        // Build and execute queries
        StringBuilder createTableQuery = new StringBuilder("create table "+outputTable+" (PK_SECTION int," +
                " the_geom GEOMETRY, DIR_ID int, GS double");
        StringBuilder insertIntoQuery = new StringBuilder("INSERT INTO "+outputTable+"(PK_SECTION, the_geom," +
                " DIR_ID, GS");
        StringBuilder insertIntoValuesQuery = new StringBuilder("?,?,?,?");
        for(int thirdOctave : DEFAULT_FREQUENCIES_THIRD_OCTAVE) {
            createTableQuery.append(", LWD");
            createTableQuery.append(thirdOctave);
            createTableQuery.append(" double precision");
            insertIntoQuery.append(", LWD");
            insertIntoQuery.append(thirdOctave);
            insertIntoValuesQuery.append(", ?");
        }
        for(int thirdOctave : DEFAULT_FREQUENCIES_THIRD_OCTAVE) {
            createTableQuery.append(", LWE");
            createTableQuery.append(thirdOctave);
            createTableQuery.append(" double precision");
            insertIntoQuery.append(", LWE");
            insertIntoQuery.append(thirdOctave);
            insertIntoValuesQuery.append(", ?");
        }
        for(int thirdOctave : DEFAULT_FREQUENCIES_THIRD_OCTAVE) {
            createTableQuery.append(", LWN");
            createTableQuery.append(thirdOctave);
            createTableQuery.append(" double precision");
            insertIntoQuery.append(", LWN");
            insertIntoQuery.append(thirdOctave);
            insertIntoValuesQuery.append(", ?");
        }

        createTableQuery.append(")");
        insertIntoQuery.append(") VALUES (");
        insertIntoQuery.append(insertIntoValuesQuery);
        insertIntoQuery.append(")");
        connection.createStatement().execute(createTableQuery.toString());

        // Get Class to compute LW
        RailWayLWIterator railWayLWIterator = new RailWayLWIterator(connection,railSectionTableName, railTrafficTableName);

        while (railWayLWIterator.hasNext()) {
            RailWayLWGeom railWayLWGeom = railWayLWIterator.next();

            RailWayParameters railWayLWDay = railWayLWGeom.getRailWayLWDay();
            RailWayParameters railWayLWEvening = railWayLWGeom.getRailWayLWEvening();
            RailWayParameters railWayLWNight = railWayLWGeom.getRailWayLWNight();
            List<LineString> geometries = railWayLWGeom.getRailWayLWGeometry();

            int pk = railWayLWGeom.getPK();
            double[] LWDay = new double[DEFAULT_FREQUENCIES_THIRD_OCTAVE.length];
            double[] LWEvening = new double[DEFAULT_FREQUENCIES_THIRD_OCTAVE.length];
            double[] LWNight = new double[DEFAULT_FREQUENCIES_THIRD_OCTAVE.length];
            Arrays.fill(LWDay, -99.00);
            Arrays.fill(LWEvening, -99.00);
            Arrays.fill(LWNight, -99.00);
            double heightSource = 0;
            int directivityId = 0;
            boolean day = (!railWayLWDay.getRailwaySourceList().isEmpty());
            boolean evening = (!railWayLWEvening.getRailwaySourceList().isEmpty());
            boolean night = (!railWayLWNight.getRailwaySourceList().isEmpty());
                for (int iSource = 0; iSource < 6; iSource++) {

                heightSource = 0;
                switch (iSource) {
                    case 0:
                        if (day) LWDay = railWayLWDay.getRailwaySourceList().get("ROLLING").getlW();
                        if (evening) LWEvening = railWayLWEvening.getRailwaySourceList().get("ROLLING").getlW();
                        if (night) LWNight = railWayLWNight.getRailwaySourceList().get("ROLLING").getlW();
                        if (day) heightSource = 4; //railWayLWDay.getRailwaySourceList().get("ROLLING").getSourceHeight();
                        directivityId = 1;
                        break;
                    case 1:
                        if (day) LWDay = railWayLWDay.getRailwaySourceList().get("TRACTIONA").getlW();
                        if (evening) LWEvening = railWayLWEvening.getRailwaySourceList().get("TRACTIONA").getlW();
                        if (night) LWNight = railWayLWNight.getRailwaySourceList().get("TRACTIONA").getlW();
                        heightSource = 0.5;
                        directivityId = 2;
                        break;
                    case 2:
                        if (day) LWDay = railWayLWDay.getRailwaySourceList().get("TRACTIONB").getlW();
                        if (evening) LWEvening = railWayLWEvening.getRailwaySourceList().get("TRACTIONB").getlW();
                        if (night) LWNight = railWayLWNight.getRailwaySourceList().get("TRACTIONB").getlW();
                        heightSource = 4;
                        directivityId = 3;
                        break;
                    case 3:
                        if (day) LWDay = railWayLWDay.getRailwaySourceList().get("AERODYNAMICA").getlW();
                        if (evening) LWEvening = railWayLWEvening.getRailwaySourceList().get("AERODYNAMICA").getlW();
                        if (night)  LWNight = railWayLWNight.getRailwaySourceList().get("AERODYNAMICA").getlW();
                        heightSource = 0.5;
                        directivityId = 4;
                        break;
                    case 4:
                        if (day) LWDay = railWayLWDay.getRailwaySourceList().get("AERODYNAMICB").getlW();
                        if (evening) LWEvening = railWayLWEvening.getRailwaySourceList().get("AERODYNAMICB").getlW();
                        if (night)  LWNight = railWayLWNight.getRailwaySourceList().get("AERODYNAMICB").getlW();
                        heightSource = 4;
                        directivityId = 5;
                        break;
                    case 5:
                        if (day) LWDay = railWayLWDay.getRailwaySourceList().get("BRIDGE").getlW();
                        if (evening) LWEvening = railWayLWEvening.getRailwaySourceList().get("BRIDGE").getlW();
                        if (night)  LWNight = railWayLWNight.getRailwaySourceList().get("BRIDGE").getlW();
                        heightSource = 0.5;
                        directivityId = 6;
                        break;
                }

                PreparedStatement ps = connection.prepareStatement(insertIntoQuery.toString());
                for (Geometry trackGeometry : geometries) {

                    Geometry sourceGeometry = ST_UpdateZ.updateZ(ST_Force3D.force3D(trackGeometry), heightSource).copy() ;

                    int cursor = 1;
                    ps.setInt(cursor++, pk);
                    ps.setObject(cursor++, sourceGeometry);
                    ps.setInt(cursor++, directivityId);
                    ps.setDouble(cursor++, railWayLWGeom.getGs());
                    for (double v : LWDay) {
                        ps.setDouble(cursor++, v);
                    }
                    for (double v : LWEvening) {
                        ps.setDouble(cursor++, v);
                    }
                    for (double v : LWNight) {
                        ps.setDouble(cursor++, v);
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }

        }

        // Add primary key to the LW table
        connection.createStatement().execute("ALTER TABLE "+outputTable+" ADD PK INT AUTO_INCREMENT PRIMARY KEY;");
    }

    /**
     * Sets the direction attributes for the receiver.
     * @param directionAttributes
     */
    public void setDirectionAttributes(Map<Integer, DirectivitySphere> directionAttributes) {
        this.directionAttributes = directionAttributes;
        // Check if the directivities contain all required frequencies
        directionAttributes.forEach((integer, directivitySphere) -> {
            freq_lvl.forEach(frequency->{
                if(!directivitySphere.coverFrequency(frequency)) {
                    throw new IllegalArgumentException(
                            String.format(Locale.ROOT,
                                    "The provided DirectivitySphere does not handle %d Hertz", frequency));
                }
            });
        });
    }

    /**
     * Adds a noise source with its properties to the noise map.
     * @param pk Unique source identifier
     * @param geom Source geometry
     * @param rs Additional attributes fetched from database
     * @throws SQLException
     * @throws IOException
     */
    @Override
    public void addSource(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException, IOException {
        super.addSource(pk, geom, rs);
        double[][] res = computeLw(rs);
        if(noiseMapParameters.computeLDay || noiseMapParameters.computeLDEN) {
            wjSourcesD.add(res[0]);
        }
        if(noiseMapParameters.computeLEvening || noiseMapParameters.computeLDEN) {
            wjSourcesE.add(res[1]);
        }
        if(noiseMapParameters.computeLNight || noiseMapParameters.computeLDEN) {
            wjSourcesN.add(res[2]);
        }
    }

    /**
     * Checks if the noise source at the specified index is omnidirectional.
     * @param srcIndex Source index in the list sourceGeometries
     * @return true if the noise source is omnidirectional, false otherwise.
     */
    @Override
    public boolean isOmnidirectional(int srcIndex) {
        return sourcesPk.size() > srcIndex && !sourceDirection.containsKey(sourcesPk.get(srcIndex));
    }

    /**
     *
     * @param srcIndex Source index in the list sourceGeometries
     * @param frequencies Frequency in Hertz
     * @param phi (0 2π) 0 is front
     * @param theta (-π/2 π/2) 0 is horizontal π is top
     * @return
     */
    @Override
    public double[] getSourceAttenuation(int srcIndex, double[] frequencies, double phi, double theta) {
        int directivityIdentifier = sourceDirection.get(sourcesPk.get(srcIndex));
        if(directionAttributes.containsKey(directivityIdentifier)) {
            return directionAttributes.get(directivityIdentifier).getAttenuationArray(frequencies, phi, theta);
        } else {
            // This direction identifier has not been found
            return new double[frequencies.length];
        }
    }

    /**
     * Retrieves the ground speed of the noise source at the specified index.
     * @param srcIndex
     * @return the ground speed of the noise source at the specified index.
     */
    @Override
    public double getSourceGs(int srcIndex){
        return sourceGs.get(sourcesPk.get(srcIndex));
    }

    /**
     * Retrieves the emissions for the specified period from the given result set
     * @param rs result set of source
     * @param period D or E or N
     * @param slope Gradient percentage of road from -12 % to 12 %
     * @return Emission spectrum in dB
     *
     */
    public double[] getEmissionFromResultSet(ResultSet rs, String period, double slope) throws SQLException, IOException {
        if (sourceFields == null) {
            sourceFields = new HashMap<>();
            int fieldId = 1;
            for (String fieldName : JDBCUtilities.getColumnNames(rs.getMetaData())) {
                sourceFields.put(fieldName.toUpperCase(), fieldId++);
            }
        }
        double[] lvl = new double[noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size()];
        // Set default values
        double tv = 0; // old format "total vehicles"
        double hv = 0; // old format "heavy vehicles"
        double lv_speed = 0;
        double mv_speed = 0;
        double hgv_speed = 0;
        double wav_speed = 0;
        double wbv_speed = 0;
        double lvPerHour = 0;
        double mvPerHour = 0;
        double hgvPerHour = 0;
        double wavPerHour = 0;
        double wbvPerHour = 0;
        double temperature = 20.0;
        String roadSurface = "NL08";
        double tsStud = 0;
        double pmStud = 0;
        double junctionDistance = 100; // no acceleration of deceleration changes with dist >= 100
        int junctionType = 2;
        int way = 3; // default value 2-way road

        // Read fields
        if(sourceFields.containsKey("LV_SPD_"+period)) {
            lv_speed = rs.getDouble(sourceFields.get("LV_SPD_"+period));
        }
        if(sourceFields.containsKey("MV_SPD_"+period)) {
            mv_speed = rs.getDouble(sourceFields.get("MV_SPD_"+period));
        }
        if(sourceFields.containsKey("HGV_SPD_"+period)) {
            hgv_speed = rs.getDouble(sourceFields.get("HGV_SPD_"+period));
        }
        if(sourceFields.containsKey("WAV_SPD_"+period)) {
            wav_speed = rs.getDouble(sourceFields.get("WAV_SPD_"+period));
        }
        if(sourceFields.containsKey("WBV_SPD_"+period)) {
            wbv_speed = rs.getDouble(sourceFields.get("WBV_SPD_"+period));
        }
        if(sourceFields.containsKey("LV_"+period)) {
            lvPerHour = rs.getDouble(sourceFields.get("LV_"+period));
        }
        if(sourceFields.containsKey("MV_"+period)) {
            mvPerHour = rs.getDouble(sourceFields.get("MV_"+period));
        }
        if(sourceFields.containsKey("HGV_"+period)) {
            hgvPerHour = rs.getDouble(sourceFields.get("HGV_"+period));
        }
        if(sourceFields.containsKey("WAV_"+period)) {
            wavPerHour = rs.getDouble(sourceFields.get("WAV_"+period));
        }
        if(sourceFields.containsKey("WBV_"+period)) {
            wbvPerHour = rs.getDouble(sourceFields.get("WBV_"+period));
        }
        if(sourceFields.containsKey("PVMT")) {
            roadSurface= rs.getString(sourceFields.get("PVMT"));
        }
        if(sourceFields.containsKey("TEMP_"+period)) {
            temperature = rs.getDouble(sourceFields.get("TEMP_"+period));
        }
        if(sourceFields.containsKey("TS_STUD")) {
            tsStud = rs.getDouble(sourceFields.get("TS_STUD"));
        }
        if(sourceFields.containsKey("PM_STUD")) {
            pmStud = rs.getDouble(sourceFields.get("PM_STUD"));
        }
        if(sourceFields.containsKey("JUNC_DIST")) {
            junctionDistance = rs.getDouble(sourceFields.get("JUNC_DIST"));
        }
        if(sourceFields.containsKey("JUNC_TYPE")) {
            junctionType = rs.getInt(sourceFields.get("JUNC_TYPE"));
        }

        if(sourceFields.containsKey("WAY")) {
            way = rs.getInt(sourceFields.get("WAY"));
        }

        if(sourceFields.containsKey("SLOPE")) {
            slope = rs.getDouble(sourceFields.get("SLOPE"));
        }else{
            way = 3;
        }


        // old fields
        if(sourceFields.containsKey("TV_"+period)) {
            tv = rs.getDouble(sourceFields.get("TV_"+period));
        }
        if(sourceFields.containsKey("HV_"+period)) {
            hv = rs.getDouble(sourceFields.get("HV_"+period));
        }
        if(sourceFields.containsKey("HV_SPD_"+period)) {
            hgv_speed = rs.getDouble(sourceFields.get("HV_SPD_"+period));
        }

        if(tv > 0) {
            lvPerHour = tv - (hv + mvPerHour + hgvPerHour + wavPerHour + wbvPerHour);
        }
        if(hv > 0) {
            hgvPerHour = hv;
        }
        // Compute emission
        int idFreq = 0;
        for (int freq : noiseMapParameters.attenuationCnossosParametersDay.freq_lvl) {
            RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(lv_speed, mv_speed, hgv_speed, wav_speed,
                    wbv_speed,lvPerHour, mvPerHour, hgvPerHour, wavPerHour, wbvPerHour, freq, temperature,
                    roadSurface, tsStud, pmStud, junctionDistance, junctionType);
            rsParametersCnossos.setSlopePercentage(slope);
            rsParametersCnossos.setWay(way);
            rsParametersCnossos.setFileVersion(noiseMapParameters.coefficientVersion);
            lvl[idFreq++] = RoadCnossos.evaluate(rsParametersCnossos);
        }
        return lvl;
    }

    /**
     * Computes the sound levels (Lw) for different periods based on the provided spatial result set.
     * @param rs
     * @return a two-dimensional array containing the sound levels (Ld, Le, Ln) for each frequency level.
     * @throws SQLException
     * @throws IOException
     */
    public double[][] computeLw(SpatialResultSet rs) throws SQLException, IOException {

        // Compute day average level
        double[] ld = new double[noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size()];
        double[] le = new double[noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size()];
        double[] ln = new double[noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size()];

        if (noiseMapParameters.input_mode == NoiseMapParameters.INPUT_MODE.INPUT_MODE_PROBA) {
            double val = dbaToW(90.0);
            for(int idfreq = 0; idfreq < noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size(); idfreq++) {
                ld[idfreq] = dbaToW(val);
                le[idfreq] = dbaToW(val);
                ln[idfreq] = dbaToW(val);
            }
        } else if (noiseMapParameters.input_mode == NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_DEN) {
            // Read average 24h traffic
            if(noiseMapParameters.computeLDay || noiseMapParameters.computeLDEN) {
                for (int idfreq = 0; idfreq < noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size(); idfreq++) {
                    ld[idfreq] = dbaToW(rs.getDouble(noiseMapParameters.lwFrequencyPrepend + "D" + noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.get(idfreq)));
                }
            }
            if(noiseMapParameters.computeLEvening || noiseMapParameters.computeLDEN) {
                for (int idfreq = 0; idfreq < noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size(); idfreq++) {
                    le[idfreq] = dbaToW(rs.getDouble(noiseMapParameters.lwFrequencyPrepend + "E" + noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.get(idfreq)));
                }
            }
            if(noiseMapParameters.computeLNight || noiseMapParameters.computeLDEN) {
                for (int idfreq = 0; idfreq < noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size(); idfreq++) {
                    ln[idfreq] = dbaToW(rs.getDouble(noiseMapParameters.lwFrequencyPrepend + "N" + noiseMapParameters.attenuationCnossosParametersDay.freq_lvl.get(idfreq)));
                }
            }
        } else if(noiseMapParameters.input_mode == NoiseMapParameters.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW) {
            // Extract road slope
            double slope = 0;
            try {
                Geometry g = rs.getGeometry();
                if(profileBuilder!=null && g != null && !g.isEmpty()) {
                    Coordinate[] c = g.getCoordinates();
                    if(c.length >= 2) {
                        double z0 = profileBuilder.getZ(c[0]);
                        double z1 = profileBuilder.getZ(c[1]);
                        if(!Double.isNaN(z0) && !Double.isNaN(z1)) {
                            slope = Utils.computeSlope(z0, z1, g.getLength());
                        }
                    }
                }
            } catch (SQLException ex) {
                // ignore
            }
            // Day
            ld = dbaToW(getEmissionFromResultSet(rs, "D", slope));

            // Evening
            le = dbaToW(getEmissionFromResultSet(rs, "E", slope));

            // Night
            ln = dbaToW(getEmissionFromResultSet(rs, "N", slope));

        }
        return new double[][] {ld, le, ln};
    }

    /**
     * Retrieves the maximal source power for the specified source ID.
     * @param sourceId
     * @return an array containing the maximal source power values for the specified source ID.
     */
    public double[] getMaximalSourcePower(int sourceId) {
        if(noiseMapParameters.computeLDay && sourceId < wjSourcesD.size()) {
            return wjSourcesD.get(sourceId);
        } else if(noiseMapParameters.computeLEvening && sourceId < wjSourcesE.size()) {
            return wjSourcesE.get(sourceId);
        } else if(noiseMapParameters.computeLNight && sourceId < wjSourcesN.size()) {
            return wjSourcesN.get(sourceId);
        } else {
            return new double[0];
        }
    }

}
