/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.railway.cnossos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.noise_planet.noisemodelling.emission.LineSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;

import static java.lang.Math.min;
import static org.noise_planet.noisemodelling.emission.utils.interpLinear.interpLinear;

/**
 * Railway noise evaluation from Cnossos reference : COMMISSION DIRECTIVE (EU) 2015/996
 * of 19 May 2015 establishing common noise assessment methods according to Directive 2002/49/EC
 * of the European Parliament and of the Council
 *
 * amending, for the purposes of adapting to scientific and technical progress, Annex II to
 * Directive 2002/49/EC of the European Parliament and of the Council as regards
 * common noise assessment methods
 *
 * part 2.3. Railway noise
 *
 * Return the dB value corresponding to the parameters
 * @author Adrien Le Bellec, Université Gustave Eiffel
 * @author Olivier Chiello, Université Gustave Eiffel
 */

public class RailwayCnossos extends org.noise_planet.noisemodelling.emission.railway.Railway {

    public RailwayCnossos() {
    }


    /**
     *
     * @param inputStream
     * @return
     */
    private static JsonNode parse(InputStream inputStream) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(inputStream);
        } catch (IOException ex) {
            return NullNode.getInstance();
        }
    }

    /**
     *
     * @param iterator
     * @return an iterator
     * @param <T>
     */
    public static <T> Iterable<T> iteratorToIterable(Iterator<T> iterator) {
        return () -> iterator;
    }

    /**
     * get Wheel Roughness by wavenumber - Only CNOSSOS
     * @param typeVehicle
     * @param fileVersion
     * @param lambdaId
     * @return
     */
    public Double getWheelRoughness(String typeVehicle, String fileVersion, int lambdaId) { //
        int refId = getVehicleNode(typeVehicle).get("RefRoughness").intValue();
        return getRailWayData().get("Vehicle").get("WheelRoughness").get(String.valueOf(refId)).get("Values").get(lambdaId).doubleValue();
    }

    /**
     * get Contact Filter by wavenumber - Only CNOSSOS
     * @param typeVehicle type of a vehicle, for example : SNCF2
     * @param lambdaId wavenumber (between 1 and 34 corresponding the normalised third octave bands from 2000mm to 0.8mm).
     * @return contact filter
     */
    public Double getContactFilter(String typeVehicle, int lambdaId) { //
        int refId = getVehicleNode(typeVehicle).get("RefContact").intValue();
        return getRailWayData().get("Vehicle").get("ContactFilter").get(String.valueOf(refId)).get("Values").get(lambdaId).doubleValue();
    }

    /**
     * get Track Roughness Filter by wavenumber - Only CNOSSOS
     * @param trackRoughnessId
     * @param lambdaId
     * @return
     */
    public Double getTrackRoughness(int trackRoughnessId, int lambdaId) { //
        return getRailWayData().get("Track").get("RailRoughness").get(String.valueOf(trackRoughnessId)).get("Values").get(lambdaId).doubleValue();
    }

    /**
     * Axles number by vehicles
     * @param typeVehicle
     * @return
     */
    public double getAxlesPerVeh(String typeVehicle) { //
        return getVehicleNode(typeVehicle).get("NbAxlePerVeh").doubleValue();
    }

    /**
     * get Nb of coach by vehicle
     * @param typeVehicle
     * @return
     */
    public int getNbCoach(String typeVehicle) { //
        int nbCoach;
        try {
            nbCoach = getVehicleData().get(typeVehicle).get("NbCoach").intValue();
        } catch (Exception e) {
            nbCoach = 1;
        }

        return nbCoach;
    }

    /**
     *
     * @param typeVehicle
     * @param runningCondition
     * @param sourceHeightId
     * @param fileVersion
     * @param freqId
     * @return
     */
    public double getTractionNoise(String typeVehicle, int runningCondition, String sourceHeightId, String fileVersion, int freqId) { //
        int refId = getVehicleNode(typeVehicle).get("RefTraction").intValue();
        double tractionSpectre =0;

        String condition = "ConstantSpeed";
        if (refId != 0) {
            switch (runningCondition) {
                case 0:
                    condition = "ConstantSpeed";
                    break;
                case 1:
                    condition = "AccelerationSpeed";
                    break;
                case 3:
                    condition = "DecelerationSpeed";
                    break;
                case 4:
                    condition = "IdlingSpeed";
                    break;
            }
            try {
                tractionSpectre = getRailWayData().get("Vehicle").get(condition).get(String.valueOf(refId)).get("Values").get(sourceHeightId).get(freqId).doubleValue();
            } catch (NullPointerException ex) {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "Could not find traction spectrum for the following parameters " +
                        "getRailWayData(%d).get(\"Vehicle\").get(%s).get(String.valueOf" +
                        "(%d)).get(\"Values\").get(%s).get(%d)", fileVersion, condition, refId, sourceHeightId, freqId));
            }
        }
        return tractionSpectre;
    }

    /**
     * retrieve the aerodynamic noise value for a specific type of vehicle, source height, file version, and frequency ID
     * by accessing the corresponding data from the vehicle node, railway data, and noise values.
     * @param typeVehicle
     * @param sourceHeightId
     * @param fileVersion
     * @param freqId
     * @return
     */
    public double getAerodynamicNoise(String typeVehicle, String sourceHeightId, String fileVersion, int freqId) { //
        int refId = getVehicleNode(typeVehicle).get("RefAerodynamic").intValue();
        return getRailWayData().get("Vehicle").get("AerodynamicNoise").get(String.valueOf(refId)).get("Values").get(sourceHeightId).get(freqId).doubleValue();
    }

    /**
     * retrieves the structural constant for a specific bridge and frequency ID
     * by accessing the corresponding data from the railway track's bridge constants.
     * @param bridgeId
     * @param freqId
     * @return
     */
    public Double getBridgeStructural(int bridgeId, int freqId) {
        return getRailWayData().get("Track").get("BridgeConstant").get(String.valueOf(bridgeId)).get("Values").get(freqId).doubleValue();
    }


    /**
     * fetches and returns the transfer value from the railway data for a specific track transfer ID and frequency ID.
     * @param trackTransferId
     * @param freqId
     * @return
     */
    public Double getTrackTransfer(int trackTransferId,  int freqId) { //
        return getRailWayData().get("Track").get("TrackTransfer").get(String.valueOf(trackTransferId)).get("Spectre").get(freqId).doubleValue();
    }

    /**
     *  fetches and returns the impact noise value from the railway data for a specific impact noise ID and frequency ID.
     * @param impactNoiseId
     * @param freqId
     * @return
     */
    public Double getImpactNoise(int impactNoiseId,  int freqId) { //
        return getRailWayData().get("Track").get("ImpactNoise").get(String.valueOf(impactNoiseId)).get("Values").get(freqId).doubleValue();
    }

    /**
     * retrieve and return the transfer value associated with a given vehicle type and a specific frequency ID.
     * @param typeVehicle
     * @param freqId
     * @return
     */
    public Double getVehTransfer(String typeVehicle,  int freqId) {
        int RefTransfer = getVehicleNode(typeVehicle).get("RefTransfer").intValue();
        return getRailWayData().get("Vehicle").get("Transfer").get(String.valueOf(RefTransfer)).get("Spectre").get(freqId).doubleValue();

    }


    /**
     * calculates the total roughness level for a specific type of vehicle,
     * track roughness, vehicle file version, and lambda ID by retrieving the wheel roughness and track roughness
     * @param typeVehicle
     * @param trackRoughnessId
     * @param vehicleFileVersion
     * @param idLambda
     * @return
     */
    public Double getLRoughness(String typeVehicle, int trackRoughnessId, String vehicleFileVersion,  int idLambda) { //
        double wheelRoughness = getWheelRoughness(typeVehicle, vehicleFileVersion, idLambda);
        double trackRoughness = getTrackRoughness(trackRoughnessId, idLambda);
        return 10 * Math.log10(Math.pow(10, wheelRoughness / 10) + Math.pow(10, trackRoughness / 10));
    }

    /**
     * Check if it exists some NaN values in the Roughness sound level.
     * They appear when calculating the roughness in the frequency domain by interpolating the roughness in the wavelength domain
     * @param roughnessLtot
     * @return
     */
    private double[] checkNanValue(double[] roughnessLtot) {
        int indice_NaN = 0;
        for (int i = 0; i < roughnessLtot.length-2; i++) {
            if (Double.isNaN(roughnessLtot[i])) {
                indice_NaN++;
            }
        }
        for (int i = 0; i < indice_NaN; i++) {
            roughnessLtot[i] = roughnessLtot[indice_NaN + 1];
        }
        return roughnessLtot;
    }

    /**
     * Evaluate the sound level for one Vehicle
     * @param vehicleParameters Vehicle Noise emission parameters
     * @param trackParameters Track Noise emission parameters
     * constant speed
     *
     * @return LWRoll / LWTraction A and B / LWAerodynamic A and B / LWBridge level in dB
     **/
    public RailWayCnossosParameters evaluate(RailwayVehicleCnossosParameters vehicleParameters, RailwayTrackCnossosParameters trackParameters) throws IOException {

        String vehicleFileVersion = vehicleParameters.getFileVersion();
        String trackFileVersion = trackParameters.getFileVersion();
        String typeVehicle = vehicleParameters.getTypeVehicle();

        double speedVehicle = vehicleParameters.getSpeedVehicle();
        double vehPerHour = vehicleParameters.getNumberVehicle();
        double axlesPerVeh = getAxlesPerVeh(typeVehicle);
        int runningCondition = vehicleParameters.getRunningCondition();

        double speedTrack = trackParameters.getSpeedTrack();
        double speedCommercial = trackParameters.getSpeedCommercial();
        int trackRoughnessId = trackParameters.getRailRoughness();
        int trackTransferId = trackParameters.getTrackTransfer();
        int impactId = trackParameters.getImpactNoise();
        int bridgeId = trackParameters.getBridgeTransfert();
        int curvature = trackParameters.getCurvature();

        // get speed of the vehicle
        double speed = min(speedVehicle, min(speedTrack, speedCommercial));

        boolean isTunnel = trackParameters.getIsTunnel();
        // %% Take into account the number of coach and the number of units
        // 10*log10(NbUnit*NbCoach);

        RailWayCnossosParameters railWayParameters = new RailWayCnossosParameters();

        if (isTunnel) {
            return railWayParameters;
        } else {
            //  Rolling noise calcul
            double[] lW = getLWRolling(typeVehicle, trackRoughnessId, impactId,  curvature, speed, trackTransferId, trackFileVersion, axlesPerVeh);
            railWayParameters.addRailwaySource("ROLLING", new LineSource(lW,4, "ROLLING"));
            lW = getLWTraction(typeVehicle,  runningCondition,  "A", vehicleFileVersion);
            railWayParameters.addRailwaySource("TRACTIONA", new LineSource(lW,0.05, "TRACTIONA"));
            lW =  getLWTraction(typeVehicle,  runningCondition,  "B", vehicleFileVersion);
            railWayParameters.addRailwaySource("TRACTIONB", new LineSource(lW,4, "TRACTIONB"));
            lW = getLWAero(typeVehicle,   speed, "A", vehicleFileVersion);
            railWayParameters.addRailwaySource("AERODYNAMICA", new LineSource(lW,0.05, "AERODYNAMICA"));
            lW = getLWAero(typeVehicle,   speed, "B", vehicleFileVersion);
            railWayParameters.addRailwaySource("AERODYNAMICB", new LineSource(lW,4, "AERODYNAMICB"));
            lW =  getLWBridge(typeVehicle, trackRoughnessId, impactId, bridgeId, speed, trackFileVersion,axlesPerVeh);
            railWayParameters.addRailwaySource("BRIDGE", new LineSource(lW,4, "BRIDGE"));

            railWayParameters.appendVperHour(vehPerHour*getNbCoach(typeVehicle), speed);
            return railWayParameters;
        }
    }

    /**
     * traction or Aerodynamic Level.
     * @param typeVehicle vehicle data base
     * @param height height source
     * @return lWSpectre(freq) (Traction or Aerodynamic)
     **/
    private double[] getLWTraction(String typeVehicle, int runningCondition, String height, String fileVersion) {
        double[] lWSpectre = new double[24];
        for (int idFreq = 0; idFreq < 24; idFreq++) {
            lWSpectre[idFreq] = getTractionNoise(typeVehicle, runningCondition, height, fileVersion, idFreq);
        }
        return lWSpectre;
    }

    /**
     * traction or Aerodynamic Level.
     * @param typeVehicle vehicle data base
     * @param speed min speed between vehicle and track
     * @param height height source
     * @return lWSpectre(freq) (Traction or Aerodynamic)
     **/
    private double[] getLWAero(String typeVehicle, double speed, String height, String fileVersion) {
        double[] lWSpectre = new double[24];

        for (int idFreq = 0; idFreq < 24; idFreq++) {

            int refId = getVehicleNode(typeVehicle).get("RefAerodynamic").intValue();
            if (speed < 200 || refId == 0) {
                lWSpectre[idFreq] = -99;
            } else {
                lWSpectre[idFreq] = getAerodynamicNoise(typeVehicle,  height, fileVersion, idFreq);
                double v0Aero = Double.parseDouble(getRailWayData().get("Vehicle").get("AerodynamicNoise").get(String.valueOf(refId)).get("V0").asText());
                double alphaAero = Double.parseDouble(getRailWayData().get("Vehicle").get("AerodynamicNoise").get(String.valueOf(refId)).get("Alpha").asText());
                lWSpectre[idFreq] = lWSpectre[idFreq] + alphaAero * Math.log10(speed / v0Aero);
            }
        }

        return lWSpectre;
    }


    /**
     * Rolling Level.
     * @param typeVehicle vehicle data base
     * @param trackRoughnessId track Roughness reference
     * @param impactId  impact reference
     * @param speed  min speed between vehicle and track
     *
     *               Step 1
     * wavelength to frequecy (evaluateRoughnessLtotFreq)
     *               Step 2
     * calcul sound power of wheel and bogie emission
     * calcul sound power of rail sleeper and ballast/slab emission
     * todo add sound power of superstructure emission ?
     *
     * @return lWRoll(freq)
     **/


    private double[] getLWRolling(String typeVehicle, int trackRoughnessId, int impactId, int curvature, double speed, int trackTransferId, String trackFileVersion, double axlesPerVeh) {

        double[] trackTransfer = new double[24];
        double[] lWTr = new double[24];
        double[] vehTransfer = new double[24];
        double[] lWVeh = new double[24];
        double[] lW = new double[24];

        // roughnessLtot = CNOSSOS p.19 (2.3.7)
        double[] roughnessLtot = checkNanValue(getLWRoughness(typeVehicle, trackRoughnessId, impactId, speed, trackFileVersion));

        for (int idFreq = 0; idFreq < 24; idFreq++) {
            // lWTr = CNOSSOS p.20 (2.3.8)
            trackTransfer[idFreq] = getTrackTransfer(trackTransferId,  idFreq);
            lWTr[idFreq] = roughnessLtot[idFreq] + trackTransfer[idFreq] + 10 * Math.log10(axlesPerVeh);

            // lWVeh = CNOSSOS p.20 (2.3.9)
            vehTransfer[idFreq] = getVehTransfer(typeVehicle,  idFreq);
            lWVeh[idFreq] = roughnessLtot[idFreq] + vehTransfer[idFreq] + 10 * Math.log10(axlesPerVeh);
            // lWRoll = CNOSSOS p.19 (2.3.7)
            lW[idFreq] = 10 * Math.log10(Math.pow(10, lWTr[idFreq] / 10) + Math.pow(10, lWVeh[idFreq] / 10));
            if (curvature == 1) {
                lW[idFreq] = lW[idFreq] + 5;
            } else if (curvature == 2) {
                lW[idFreq] = lW[idFreq] + 8;
            } else if (curvature == 3) {
                lW[idFreq] = lW[idFreq] + 8;
            }
        }

        return lW;
    }


    /**
     * method calculates the overall sound power level for a specific type of vehicle, track roughness,
     * impact ID, bridge ID, speed, track file version, and number of axles per vehicle
     * @param typeVehicle
     * @param trackRoughnessId
     * @param impactId
     * @param bridgeId
     * @param speed
     * @param trackFileVersion
     * @param axlesPerVeh
     * @return
     */
    private double[] getLWBridge(String typeVehicle, int trackRoughnessId, int impactId, int bridgeId, double speed, String trackFileVersion, double axlesPerVeh) {

        double[] lW = new double[24];

        // roughnessLtot = CNOSSOS p.19 (2.3.7)
        double[] roughnessLtot = checkNanValue(getLWRoughness(typeVehicle, trackRoughnessId, impactId, speed, trackFileVersion));

        double[] lWBridge = new double[24];
        for (int idFreq = 0; idFreq < 24; idFreq++) {
            lW[idFreq] = -99;
        }
        if (trackFileVersion == "EU") {
            if (bridgeId == 3 || bridgeId == 4) {
                for (int idFreq = 0; idFreq < 24; idFreq++) {
                    lWBridge[idFreq] = getBridgeStructural(bridgeId, idFreq);
                    lW[idFreq] = roughnessLtot[idFreq] + lWBridge[idFreq] + 10 * Math.log10(axlesPerVeh);
                }
            }
        }

        return lW;
    }



    /**
     * Roughness Level.
     * linear interpolation wavelength to frequency
     * @param typeVehicle vehicle data base
     * @param trackRoughnessId track Roughness reference
     * @param impactId  impact reference
     * @param speed  impact reference
     * @return Lroughness(freq)
     **/
    private double[] getLWRoughness(String typeVehicle, int trackRoughnessId, int impactId, double speed, String trackFileVersion) {

        double[] roughnessTotLambda = new double[35];
        double[] roughnessLtot = new double[35];
        double[] contactFilter = new double[35];
        double[] lambdaToFreqLog = new double[35];
        double[] freqMedLog = new double[24];
        double[] Lambda = new double[35];

        double m = 33;
        for (int idLambda = 0; idLambda < 35; idLambda++) {
            Lambda[idLambda] = Math.pow(10, m / 10);
            lambdaToFreqLog[idLambda] = Math.log10(speed / Lambda[idLambda] * 1000 / 3.6);

            roughnessTotLambda[idLambda] = Math.pow(10, getLRoughness(typeVehicle, trackRoughnessId,  trackFileVersion, idLambda) / 10);

            contactFilter[idLambda] = getContactFilter(typeVehicle,  idLambda);
            roughnessLtot[idLambda] = 10 * Math.log10(roughnessTotLambda[idLambda]) + contactFilter[idLambda];
            if (impactId != 0) {
                roughnessLtot[idLambda] =  10 * Math.log10(Math.pow(10,roughnessLtot[idLambda]/ 10) + Math.pow(10, getImpactNoise(impactId,  idLambda) / 10));
            }
            roughnessLtot[idLambda] = Math.pow(10, roughnessLtot[idLambda] / 10);
            m--;
        }
        for (int idFreqMed = 0; idFreqMed < 24; idFreqMed++) {
            freqMedLog[idFreqMed] = Math.log10(Math.pow(10, (17 + Double.valueOf(idFreqMed)) / 10));
        }

        double[] roughnessLtotFreq = interpLinear(lambdaToFreqLog, roughnessLtot, freqMedLog);

        for (int idRoughnessLtotFreq = 0; idRoughnessLtotFreq < 24; idRoughnessLtotFreq++) {
            roughnessLtotFreq[idRoughnessLtotFreq] = 10 * Math.log10(roughnessLtotFreq[idRoughnessLtotFreq]);
        }
        return roughnessLtotFreq;
    }

}

