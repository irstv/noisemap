/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.railway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

/**
 * Railway noise evaluation from Cnossos reference : COMMISSION DIRECTIVE (EU) 2015/996
 * of 19 May 2015 establishing common noise assessment methods according to Directive 2002/49/EC
 * of the European Parliament and of the Council
 * amending, for the purposes of adapting to scientific and technical progress, Annex II to
 * Directive 2002/49/EC of the European Parliament and of the Council as regards
 * common noise assessment methods
 * part 2.3. Railway noise
 * Return the dB value corresponding to the parameters
 * @author Adrien Le Bellec, Université Gustave Eiffel
 * @author Olivier Chiello, Université Gustave Eiffel
 */

public class Railway {
    private JsonNode railWayData;
    private JsonNode vehicleData;
    private JsonNode trainsetData;

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

    public void setVehicleDataFile(String VehicleData) {
        this.vehicleData = parse(Railway.class.getResourceAsStream(VehicleData));
    }
    public void setTrainSetDataFile(String TrainsetData) {
        this.trainsetData = parse(Railway.class.getResourceAsStream(TrainsetData));
    }
    public void setRailwayDataFile(String RailWayData) {
        this.railWayData = parse(Railway.class.getResourceAsStream(RailWayData));
    }

    public JsonNode getVehicleNode(String typeVehicle) {
        JsonNode vehicle = vehicleData.get(typeVehicle);
        if (vehicle == null) {
            throw new IllegalArgumentException(String.format("Vehicle %s not found must be one of :\n -%s", typeVehicle,
                    String.join("\n -", iteratorToIterable(vehicleData.fieldNames()))));
        }
        return vehicle;
    }

    /**
     * Get the CNOSSOS coefficients from a specific file version (French have their own CNOSSOS coefficients).
     * @return get the CNOSSOS Coefficients
     */
    public JsonNode getRailWayData() {
        return this.railWayData;
    }

    /**
     * Get the vehicles attributes
     * @return Coefficients related to the characteristics of vehicles
     */
    public JsonNode getVehicleData() {
        return this.vehicleData;
    }

    /**
     * Specific to French adaptation of the CNOSSOS method
     * Get the SNCF composition of a train (one train can contains one or more vehicles)
     * @return SNCF composition of a train
     */
    public JsonNode getTrainsetData() {
        return this.trainsetData;
    }

    /**
     * Get vehicle from a trainset
     * @param trainName Name of a Trainset
     * @return a map of  vehicles , number of vehicles
     */
    public Map<String, Integer> getVehicleFromTrainset(String trainName) {
        Map<String, Integer> vehicles = null;
        for (Iterator<Map.Entry<String, JsonNode>> it = getTrainsetData().fields(); it.hasNext(); ) {
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
     * @param vehicleName Name of a Vehicle
     * @return true if in list
     */
    public boolean isInVehicleList(String vehicleName) {
        boolean inlist = false;
        for (Iterator<Map.Entry<String, JsonNode>> it = getVehicleData().fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> elt = it.next();
            if (vehicleName.equals(elt.getKey())) {
                inlist = true;
                break;
            }
        }
        return inlist;
    }



    /**
     * Evaluate the sound level for one Vehicle
     * @param vehicleParameters Vehicle Noise emission parameters
     * @param trackParameters Track Noise emission parameters
     * constant speed
     *
     * @return LWRoll / LWTraction A and B / LWAerodynamic A and B / LWBridge level in dB
     **/
    public RailWayParameters evaluate(RailwayVehicleParameters vehicleParameters, RailwayTrackParameters trackParameters) throws IOException {

        String typeVehicle = vehicleParameters.getTypeVehicle();
        double speedVehicle = vehicleParameters.getSpeedVehicle();
        double vehPerHour = vehicleParameters.getNumberVehicle();

        boolean isTunnel = trackParameters.getIsTunnel();

        if (isTunnel) {
            RailWayParameters lWRailWay = new RailWayParameters();
            return lWRailWay;
        } else {
            RailWayParameters lWRailWay = new RailWayParameters();
            return lWRailWay;
        }
    }


}


