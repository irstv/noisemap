/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.emission.railway.nmpb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import static java.lang.Math.min;
import static org.noise_planet.noisemodelling.emission.utils.interpLinear.interpLinear;

/**
 * Railway noise evaluation from NMPB reference : COMMISSION DIRECTIVE (EU) 2015/996
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

public class RailwayNMPB {
    private JsonNode NMPBRailWayData;
    private JsonNode NMPBRailWayData2020;
    private JsonNode NMPBRailWayDataSncf;
    private JsonNode NMPBVehicleData;
    private JsonNode NMPBTrainData;


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

    public static <T> Iterable<T> iteratorToIterable(Iterator<T> iterator) {
        return () -> iterator;
    }


    /**
     *
     * @param NMPBVehicleData
     * @param NMPBTrainData
     */
    public void setEvaluateRailwaySourceNMPB(InputStream NMPBVehicleData, InputStream NMPBTrainData) {
        this.NMPBVehicleData = parse(NMPBVehicleData);
        this.NMPBTrainData = parse(NMPBTrainData);
    }

    /**
     * Get the NMPB coefficients from a specific file version (French have their own NMPB coefficients).
     * @param fileVersion 1=RailwayNMPBEU_2020.json; other = RailwayNMPBSNCF_2021.json
     * @return get the NMPB Coefficients
     */
    public JsonNode getNMPBRailWayData(int fileVersion) {
        if (fileVersion == 1) {
            if (NMPBRailWayData2020 == null) {
                NMPBRailWayData2020 = parse(RailwayNMPB.class.getResourceAsStream("RailwayNMPBEU_2020.json"));
            }
            return NMPBRailWayData2020;
        } else if (fileVersion == 2) {
            if (NMPBRailWayDataSncf == null) {
                NMPBRailWayDataSncf = parse(RailwayNMPB.class.getResourceAsStream("RailwayNMPBSNCF_2021.json"));
            }
            return NMPBRailWayDataSncf;
        } else {
            if (NMPBRailWayData == null) {
                NMPBRailWayData = parse(RailwayNMPB.class.getResourceAsStream("RailwayNMPBSNCF_2021.json"));
            }
            return NMPBRailWayData;
        }
    }

    public JsonNode getNMPBVehicleNode(String typeVehicle) {
        JsonNode vehicle = getNMPBVehicleData().get(typeVehicle);
        if (vehicle == null) {
            throw new IllegalArgumentException(String.format("Vehicle %s not found must be one of :\n -%s", typeVehicle,
                    String.join("\n -", iteratorToIterable(getNMPBVehicleData().fieldNames()))));
        }
        return vehicle;
    }

    /**
     * Specific to French adaptation of the NMPB method
     * Get the SNCF vehicles characteristics
     * @return Coefficients related to the characteristics of SNCF vehicles
     */
    public JsonNode getNMPBVehicleData() {
        if (NMPBVehicleData == null) {
            NMPBVehicleData = parse(RailwayNMPB.class.getResourceAsStream("RailwayVehiclesNMPB.json"));
        }
        return NMPBVehicleData;
    }

    /**
     * Specific to French adaptation of the NMPB method
     * Get the SNCF composition of a train (one train can contains one or more vehicles)
     * @return SNCF composition of a train
     */
    public JsonNode getNMPBTrainData() {
        if (NMPBTrainData == null) {
            NMPBTrainData = parse(RailwayNMPB.class.getResourceAsStream("RailwayTrainsets.json"));
        }
        return NMPBTrainData;
    }

    /**
     *
     * @param trainName
     * @return
     */
    public Map<String, Integer> getVehicleFromTrain(String trainName) {
        Map<String, Integer> vehicles = null;
        for (Iterator<Map.Entry<String, JsonNode>> it = getNMPBTrainData().fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> elt = it.next();
            if (trainName.equals(elt.getKey())) {

                ObjectMapper mapper = new ObjectMapper();
                vehicles = mapper.convertValue(elt.getValue(), new TypeReference<Map<String, Integer>>() {
                });
                break;
            }
        }
        return vehicles;
    }


    /**
     * Find if a specific vehicle is in the Vehicle List
     * @param vehicleName Name of a Vehicule
     * @return true if in list
     */
    public boolean isInVehicleList(String vehicleName) {
        boolean inlist = false;
        for (Iterator<Map.Entry<String, JsonNode>> it = getNMPBVehicleData().fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> elt = it.next();
            if (vehicleName.equals(elt.getKey())) {
                inlist = true;
                break;
            }
        }
        return inlist;
    }

    /**
     *
     * @param typeVehicle
     * @param refType
     * @param spectreVer
     * @param lambdaId
     * @return
     */
    public Double getLambdaValue(String typeVehicle, String refType, int spectreVer, int lambdaId) { //
        int refId = getNMPBVehicleNode(typeVehicle).get(refType).intValue();
        String ref = "";
        if (refType.equals("RefRoughness")) {
            ref = "WheelRoughness";
        } else if (refType.equals("RefContact")) {
            ref = "ContactFilter";
        }
        return getNMPBRailWayData(spectreVer).get("Vehicle").get(ref).get(String.valueOf(refId)).get("Values").get(lambdaId).doubleValue();
    }

    /**
     *
     * @param trackRoughnessId
     * @param spectreVer
     * @param lambdaId
     * @return
     */
    public Double getTrackRoughness(int trackRoughnessId, int spectreVer, int lambdaId) { //
        return getNMPBRailWayData(spectreVer).get("Track").get("RailRoughness").get(String.valueOf(trackRoughnessId)).get("Values").get(lambdaId).doubleValue();
    }

    /**
     *
     * @param typeVehicle
     * @return
     */
    public double getAxlesPerVeh(String typeVehicle) { //
        return getNMPBVehicleNode(typeVehicle).get("NbAxlePerVeh").doubleValue();
    }

    /**
     *
     * @param typeVehicle
     * @return
     */
    public int getNbCoach(String typeVehicle) { //
        int nbCoach;
        try {
            nbCoach = getNMPBVehicleData().get(typeVehicle).get("NbCoach").intValue();
        } catch (Exception e) {
            nbCoach = 1;
        }

        return nbCoach;
    }

    /**
     *
     * @param typeVehicle
     * @param ref
     * @param runningCondition
     * @param sourceHeight
     * @param spectreVer
     * @param freqId
     * @return
     */
    public double getSpectre(String typeVehicle, String ref, int runningCondition, String sourceHeight, int spectreVer, int freqId) { //
        int refId = getNMPBVehicleNode(typeVehicle).get(ref).intValue();
        if (ref.equals("RefTraction")) {
            double tractionSpectre = 0;
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
                    tractionSpectre = getNMPBRailWayData(spectreVer).get("Vehicle").get(condition).get(String.valueOf(refId)).get("Values").get(sourceHeight).get(freqId).doubleValue();
                } catch (NullPointerException ex) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT, "Could not find traction spectrum for the following parameters " +
                            "getNMPBRailWayData(%d).get(\"Vehicle\").get(%s).get(String.valueOf" +
                            "(%d)).get(\"Values\").get(%s).get(%d)", spectreVer, condition, refId, sourceHeight, freqId));
                }
            }
            return tractionSpectre;
        } else if (ref.equals("RefAerodynamic")) {
            double aerodynamicNoise;
            aerodynamicNoise = getNMPBRailWayData(spectreVer).get("Vehicle").get("AerodynamicNoise").get(String.valueOf(refId)).get("Values").get(sourceHeight).get(freqId).doubleValue();
            return aerodynamicNoise;
        } else {
            return 0;
        }
    }

    /**
     *
     * @param typeVehicle
     * @param ref
     * @param spectreVer
     * @param aeroInf
     * @return
     */
    public double getAeroV0Alpha(String typeVehicle, String ref, int spectreVer, String aeroInf) {
        int refId = getNMPBVehicleNode(typeVehicle).get(ref).intValue();
        return Double.parseDouble(getNMPBRailWayData(spectreVer).get("Vehicle").get("AerodynamicNoise").get(String.valueOf(refId)).get(aeroInf).asText());
    }

    /**
     *
     * @param bridgeId
     * @param spectreVer
     * @param freqId
     * @return
     */
    public Double getBridgeStructural(int bridgeId, int spectreVer, int freqId) {
        return getNMPBRailWayData(spectreVer).get("Track").get("BridgeConstant").get(String.valueOf(bridgeId)).get("Values").get(freqId).doubleValue();
    }

    /**
     *
     * @param trackTransferId
     * @param spectreVer
     * @param freqId
     * @return
     */
    public Double getTrackTransfer(int trackTransferId, int spectreVer, int freqId) { //
        return getNMPBRailWayData(spectreVer).get("Track").get("TrackTransfer").get(String.valueOf(trackTransferId)).get("Spectre").get(freqId).doubleValue();
    }

    /**
     *
     * @param impactNoiseId
     * @param spectreVer
     * @param freqId
     * @return
     */
    public Double getImpactNoise(int impactNoiseId, int spectreVer, int freqId) { //
        return getNMPBRailWayData(spectreVer).get("Track").get("ImpactNoise").get(String.valueOf(impactNoiseId)).get("Values").get(freqId).doubleValue();
    }

    /**
     *
     * @param typeVehicle
     * @param spectreVer
     * @param freqId
     * @return
     */
    public Double getVehTransfer(String typeVehicle, int spectreVer, int freqId) {
        int RefTransfer = getNMPBVehicleNode(typeVehicle).get("RefTransfer").intValue();
        return getNMPBRailWayData(spectreVer).get("Vehicle").get("Transfer").get(String.valueOf(RefTransfer)).get("Spectre").get(freqId).doubleValue();

    }

    /**
     *
     * @param typeVehicle
     * @param trackRoughnessId
     * @param spectreVer
     * @param idLambda
     * @return
     */
    public Double getLRoughness(String typeVehicle, int trackRoughnessId, int spectreVer, int idLambda) { //
        double wheelRoughness = getLambdaValue(typeVehicle, "RefRoughness", spectreVer, idLambda);
        double trackRoughness = getTrackRoughness(trackRoughnessId, spectreVer, idLambda);
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
        for (int i = 0; i < 24; i++) {
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
    public RailWayNMPBParameters evaluate(RailwayVehicleNMPBParameters vehicleParameters, RailwayTrackNMPBParameters trackParameters) throws IOException {

        final String fileVersion = "";//vehicleParameters.getFileVersion();

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


        // if (isTunnel) {
            double[] lWSpectre = new double[24];
            for (int idFreq = 0; idFreq < 24; idFreq++) {
                lWSpectre[idFreq] = -99;
            }
            RailWayNMPBParameters lWRailWay = new RailWayNMPBParameters(lWSpectre, lWSpectre, lWSpectre, lWSpectre, lWSpectre, lWSpectre);
            return lWRailWay;
    //    } else {
            /*
            //  Rolling noise calcul
            double[] lWRolling = evaluateLWroughness("Rolling", typeVehicle, trackRoughnessId, impactId, bridgeId, curvature, speed, trackTransferId, spectreVer, axlesPerVeh);
            // Traction noise calcul
            double[] lWTractionA = evaluateLWSpectre(typeVehicle, "RefTraction", runningCondition, speed, 0, spectreVer);
            double[] lWTractionB = evaluateLWSpectre(typeVehicle, "RefTraction", runningCondition, speed, 1, spectreVer);
            // Aerodynamic noise calcul
            double[] lWAerodynamicA = evaluateLWSpectre(typeVehicle, "RefAerodynamic", runningCondition, speed, 0, spectreVer);
            double[] lWAerodynamicB = evaluateLWSpectre(typeVehicle, "RefAerodynamic", runningCondition, speed, 1, spectreVer);
            // Bridge noise calcul
            double[] lWBridge = evaluateLWroughness("Bridge", typeVehicle, trackRoughnessId, impactId, bridgeId, curvature, speed, trackTransferId, spectreVer, axlesPerVeh);

            for (int i = 0; i < lWRolling.length; i++) {
                lWRolling[i] = Vperhour2NoiseLevel(lWRolling[i], vehPerHour * getNbCoach(typeVehicle), speed);
                lWTractionA[i] = Vperhour2NoiseLevel(lWTractionA[i], vehPerHour * getNbCoach(typeVehicle), speed);
                lWTractionB[i] = Vperhour2NoiseLevel(lWTractionB[i], vehPerHour * getNbCoach(typeVehicle), speed);
                lWAerodynamicA[i] = Vperhour2NoiseLevel(lWAerodynamicA[i], vehPerHour * getNbCoach(typeVehicle), speed);
                lWAerodynamicB[i] = Vperhour2NoiseLevel(lWAerodynamicB[i], vehPerHour * getNbCoach(typeVehicle), speed);
                lWBridge[i] = Vperhour2NoiseLevel(lWBridge[i], vehPerHour * getNbCoach(typeVehicle), speed);
            }

            RailWayNMPBParameters lWRailWay = new RailWayNMPBParameters(lWRolling, lWTractionA, lWTractionB, lWAerodynamicA, lWAerodynamicB, lWBridge);
           return lWRailWay; */
     //   }
    }

    /**
     * traction or Aerodynamic Level.
     * @param typeVehicle vehicle data base
     * @param ref "Traction" "Aerodynamic"
     * @param speed min speed between vehicle and track
     * @param height height source
     * @return lWSpectre(freq) (Traction or Aerodynamic)
     **/
    private double[] evaluateLWSpectre(String typeVehicle, String ref, int runningCondition, double speed, int height, int spectreVer) {
        double[] lWSpectre = new double[24];

        for (int idFreq = 0; idFreq < 24; idFreq++) {
            if (!ref.equals("RefAerodynamic")) {
                if (height == 0) {
                    lWSpectre[idFreq] = getSpectre(typeVehicle, ref, runningCondition, "A", spectreVer, idFreq);
                } else if (height == 1) {
                    lWSpectre[idFreq] = getSpectre(typeVehicle, ref, runningCondition, "B", spectreVer, idFreq);
                }
            } else {
                int refId = getNMPBVehicleNode(typeVehicle).get(ref).intValue();
                if (speed < 200 || refId == 0) {
                    lWSpectre[idFreq] = -99;
                } else {
                    if (height == 0) {
                        lWSpectre[idFreq] = getSpectre(typeVehicle, ref, runningCondition, "A", spectreVer, idFreq);
                    } else if (height == 1) {
                        lWSpectre[idFreq] = getSpectre(typeVehicle, ref, runningCondition, "B", spectreVer, idFreq);
                    }
                    double v0Aero = getAeroV0Alpha(typeVehicle, ref, spectreVer, "V0");
                    double alphaAero = getAeroV0Alpha(typeVehicle, ref, spectreVer, "Alpha");
                    lWSpectre[idFreq] = lWSpectre[idFreq] + alphaAero * Math.log10(speed / v0Aero);
                }
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

    private double[] evaluateLWroughness(String ref, String typeVehicle, int trackRoughnessId, int impactId, int bridgeId, int curvature, double speed, int trackTransferId, int spectreVer, double axlesPerVeh) {
        double[] trackTransfer = new double[24];
        double[] lWTr = new double[24];
        double[] vehTransfer = new double[24];
        double[] lWVeh = new double[24];
        double[] lW = new double[24];

        // roughnessLtot = NMPB p.19 (2.3.7)
        double[] roughnessLtot = checkNanValue(evaluateRoughnessLtotFreq(typeVehicle, trackRoughnessId, impactId, speed, spectreVer));
        if (ref.equals("Rolling")) {
            for (int idFreq = 0; idFreq < 24; idFreq++) {
                // lWTr = NMPB p.20 (2.3.8)
                trackTransfer[idFreq] = getTrackTransfer(trackTransferId, spectreVer, idFreq);
                lWTr[idFreq] = roughnessLtot[idFreq] + trackTransfer[idFreq] + 10 * Math.log10(axlesPerVeh);

                // lWVeh = NMPB p.20 (2.3.9)
                vehTransfer[idFreq] = getVehTransfer(typeVehicle, spectreVer, idFreq);
                lWVeh[idFreq] = roughnessLtot[idFreq] + vehTransfer[idFreq] + 10 * Math.log10(axlesPerVeh);
                // lWRoll = NMPB p.19 (2.3.7)
                lW[idFreq] = 10 * Math.log10(Math.pow(10, lWTr[idFreq] / 10) + Math.pow(10, lWVeh[idFreq] / 10));
                if (curvature == 1) {
                    lW[idFreq] = lW[idFreq] + 5;
                } else if (curvature == 2) {
                    lW[idFreq] = lW[idFreq] + 8;
                } else if (curvature == 3) {
                    lW[idFreq] = lW[idFreq] + 8;
                }
            }
        } else if (ref.equals("Bridge")) {
            double[] lWBridge = new double[24];
            for (int idFreq = 0; idFreq < 24; idFreq++) {
                lW[idFreq] = -99;
            }
            if (spectreVer == 1) {
                if (bridgeId == 3 || bridgeId == 4) {
                    for (int idFreq = 0; idFreq < 24; idFreq++) {
                        lWBridge[idFreq] = getBridgeStructural(bridgeId, spectreVer, idFreq);
                        lW[idFreq] = roughnessLtot[idFreq] + lWBridge[idFreq] + 10 * Math.log10(axlesPerVeh);
                    }
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
    private double[] evaluateRoughnessLtotFreq(String typeVehicle, int trackRoughnessId, int impactId, double speed, int spectreVer) {

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

            roughnessTotLambda[idLambda] = Math.pow(10, getLRoughness(typeVehicle, trackRoughnessId, spectreVer, idLambda) / 10);
            if (impactId != 0) {
                roughnessTotLambda[idLambda] = roughnessTotLambda[idLambda] + Math.pow(10, getImpactNoise(impactId, spectreVer, idLambda) / 10);
            }

            contactFilter[idLambda] = getLambdaValue(typeVehicle, "RefContact", spectreVer, idLambda);
            roughnessLtot[idLambda] = 10 * Math.log10(roughnessTotLambda[idLambda]) + contactFilter[idLambda];
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


