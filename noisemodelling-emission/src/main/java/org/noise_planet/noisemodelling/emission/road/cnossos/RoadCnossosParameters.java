/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */


package org.noise_planet.noisemodelling.emission.road.cnossos;

import java.io.IOException;

/**
 * RoadSource parameters for CNOSSOS method
 *
 * @author Nicolas Fortin, Université Gustave Eiffel
 * @author Pierre Aumond, Université Gustave Eiffel
 */

public class RoadCnossosParameters {
    private double lvPerHour; // Qm  It shall be expressed as yearly average per hour, per time period (day-evening-night), per vehicle class and per source line. For all categories, input traffic flow data derived from traffic counting or from traffic models shall be used.
    private double mvPerHour; // Qm yearly average per hour
    private double hgvPerHour; // Qm yearly average per hour
    private double wavPerHour; // Qm yearly average per hour
    private double wbvPerHour; // Qm yearly average per hour


    private int frequency; // Frequency in Hz
    private double temperature; // Temperature in °C
    private String roadSurface; // Road surface identifier, see RoadCnossos_2020.json for name list
    private double tsStud; // Period (in months) where the average ratio of the total volume of light vehicles per hour equipped with studded tyres
    private double qStudRatio; // Average ratio of the total volume of light vehicles per hour equipped with studded tyres during the period Ts_stud (in months)


    private double Junc_dist = 250; // Distance to junction
    private int Junc_type; // Junction type (k=1 traffic lights, k=2 roundabout)

    private double slopePercentage = 0; // slope s (in %), In the case of a bi-directional traffic flow, it is necessary to split the flow into two components and correct half for uphill and half for downhill.
    private int way = 1; // 1 = direct, 2 = inverse, 3 = double

    private double speedLv; // cat 1 vehicle speed vm (in km/h)
    private double speedMv; // cat 2 vehicle speed  (in km/h)
    private double speedHgv; // cat 3 vehicle speed  (in km/h)
    private double speedWav; // cat 4a vehicle speed  (in km/h)
    private double speedWbv; // cat 4b vehicle speed  (in km/h)

    private int fileVersion = 2; // default coefficient version (1 = 2015, 2 = 2020)

    /**
     * Utility class
     */
    public RoadCnossosParameters() {
    }

    /**
     * Simplest road noise evaluation
     * Vehicles category Table 2.2.a Directive 2015/Amendments 2020
     * lv : Passenger cars, delivery vans ≤ 3.5 tons, SUVs , MPVs including trailers and caravans
     * mv: Medium heavy vehicles, delivery vans &gt; 3.5 tons,  buses, touring cars, etc. with two axles and twin tyre mounting on rear axle
     * hgv: Heavy duty vehicles, touring cars, buses, with three or more axles
     * wav:  mopeds, tricycles or quads ≤ 50 cc
     * wbv:  motorcycles, tricycles or quads &gt; 50 cc
     *
     * @param lv_speed    Average light vehicle speed
     * @param mv_speed    Average medium vehicle speed
     * @param hgv_speed   Average heavy goods vehicle speed
     * @param wav_speed   Average light 2 wheels vehicle speed
     * @param wbv_speed   Average heavy 2 wheels vehicle speed
     * @param lvPerHour   Average light vehicle per hour
     * @param mvPerHour   Average heavy vehicle per hour
     * @param hgvPerHour  Average heavy vehicle per hour
     * @param wavPerHour  Average heavy vehicle per hour
     * @param wbvPerHour  Average heavy vehicle per hour
     * @param frequency   Studied Frequency (must be octave band)
     * @param Temperature Temperature (Celsius)
     * @param roadSurface roadSurface empty default, NL01 FR01 .. (look at src/main/resources/org/noise_planet/noisemodelling/emission/RoadCnossos_2020.json)
     * @param Ts_stud     A limited period Ts (in months) over the year where a average proportion pm of light vehicles are equipped with studded tyres and during .
     * @param Pm_stud     Average proportion of vehicles equipped with studded tyres
     * @param Junc_dist   Distance to the junction (in m) near an intersection, the road segment should be cut into small parts of 10 m..
     * @param Junc_type   Type of junction (1 =traffic lights ; 2 = roundabout), take into account the effect of acceleration and deceleration near the intersection.
     */
    public RoadCnossosParameters(double lv_speed, double mv_speed, double hgv_speed, double wav_speed, double wbv_speed, double lvPerHour, double mvPerHour, double hgvPerHour, double wavPerHour, double wbvPerHour, int frequency, double Temperature, String roadSurface, double Ts_stud, double Pm_stud, double Junc_dist, int Junc_type) {

        if (lvPerHour < 0)
            throw new IllegalArgumentException("The flow rate of light vehicles is less than zero on one section.");
        if (mvPerHour < 0)
            throw new IllegalArgumentException("The flow rate of medium vehicles is less than zero on one section.");
        if (hgvPerHour < 0)
            throw new IllegalArgumentException("The flow rate of heavy vehicles is less than zero on one section.");
        if (wavPerHour < 0)
            throw new IllegalArgumentException("The flow rate of 2W(a) vehicles is less than zero on one section.");
        if (wbvPerHour < 0)
            throw new IllegalArgumentException("The flow rate of 2W(b) vehicles is less than zero on one section.");
        if (lv_speed < 0)
            throw new IllegalArgumentException("The speed light vehicles is less than zero on one section.");
        if (mv_speed < 0)
            throw new IllegalArgumentException("The speed of medium vehicles is less than zero on one section.");
        if (hgv_speed < 0)
            throw new IllegalArgumentException("The speed of heavy vehicles is less than zero on one section.");
        if (wav_speed < 0)
            throw new IllegalArgumentException("The speed of 2W(a) vehicles is less than zero on one section.");
        if (wbv_speed < 0)
            throw new IllegalArgumentException("The speed of 2W(b) vehicles is less than zero on one section.");
        if (Ts_stud < 0 || Ts_stud > 12)
            throw new IllegalArgumentException("The number of months of snow tire use is impossible for a section (<0 or >12).");
        if (Junc_type < 0 || Junc_type > 2) throw new IllegalArgumentException("Unlnown Junction type for a section.");
        this.lvPerHour = Math.max(0, lvPerHour);
        this.mvPerHour = Math.max(0, mvPerHour);
        this.hgvPerHour = Math.max(0, hgvPerHour);
        this.wavPerHour = Math.max(0, wavPerHour);
        this.wbvPerHour = Math.max(0, wbvPerHour);
        this.frequency = Math.max(0, frequency);
        this.temperature = Temperature;
        this.roadSurface = roadSurface;
        this.tsStud = Math.max(0, Math.min(12, Ts_stud));
        this.qStudRatio = Math.max(0, Math.min(1, Pm_stud));
        this.Junc_dist = Math.max(0, Junc_dist);
        this.Junc_type = Math.max(0, Math.min(2, Junc_type));
        this.speedLv = lv_speed;
        this.speedMv = mv_speed;
        this.speedHgv = hgv_speed;
        this.speedWav = wav_speed;
        this.speedWbv = wbv_speed;
    }

    /**
     * get the speed of the road segments depending on its type or subtype
     * @param sLv Speed in the junction section
     * @param speedmax Maximum speed authorized
     * @param type Road type (1=Highway, 2=2x2, etc.)
     * @param subtype Road subtype (1= 2x2 way 110 km/h, 2=2x2 way 90km/h off belt-way, etc.)
     * @return
     * @throws IllegalArgumentException
     */
    private static double getVPl(double sLv, double speedmax, int type, int subtype) throws IllegalArgumentException {
        switch (type) {
            case 1:
                return Math.min(sLv, 100); // Highway 2x2 130 km/h
            case 2:
                switch (subtype) {
                    case 1:
                        return Math.min(sLv, 90); // 2x2 way 110 km/h
                    case 2:
                        return Math.min(sLv, 90); // 2x2 way 90km/h off belt-way
                    case 3:
                        if (speedmax < 80) {
                            return Math.min(sLv, 70);
                        } // Belt-way 70 km/h
                        else {
                            return Math.min(sLv, 85);
                        } // Belt-way 90 km/h
                }
                break;
            case 3:
                switch (subtype) {
                    case 1:
                        return sLv; // Interchange ramp
                    case 2:
                        return sLv; // Off boulevard roundabout circular junction
                    case 7:
                        return sLv; // inside-boulevard roundabout circular junction
                }
                break;
            case 4:
                switch (subtype) {
                    case 1:
                        return Math.min(sLv, 90); // lower level 2x1 way 7m 90km/h
                    case 2:
                        return Math.min(sLv, 90); // Standard 2x1 way 90km/h
                    case 3:
                        if (speedmax < 70) {
                            return Math.min(sLv, 60);
                        } // 2x1 way 60 km/h
                        else {
                            return Math.min(sLv, 80);
                        } // 2x1 way 80 km/h
                }
                break;
            case 5:
                switch (subtype) {
                    case 1:
                        return Math.min(sLv, 70); // extra boulevard 70km/h
                    case 2:
                        return Math.min(sLv, 50); // extra boulevard 50km/h
                    case 3:
                        return Math.min(sLv, 50); // extra boulevard Street 50km/h
                    case 4:
                        return Math.min(sLv, 50); // extra boulevard Street <50km/h
                    case 6:
                        return Math.min(sLv, 50); // in boulevard 70km/h
                    case 7:
                        return Math.min(sLv, 50); // in boulevard 50km/h
                    case 8:
                        return Math.min(sLv, 50); // in boulevard Street 50km/h
                    case 9:
                        return Math.min(sLv, 50); // in boulevard Street <50km/h
                }
                break;
            case 6:
                switch (subtype) {
                    case 1:
                        return Math.min(sLv, 50); // Bus-way boulevard 70km/h
                    case 2:
                        return Math.min(sLv, 50); // Bus-way boulevard 50km/h
                    case 3:
                        return Math.min(sLv, 50); // Bus-way extra boulevard Street
                    // 50km/h
                    case 4:
                        return Math.min(sLv, 50); // Bus-way extra boulevard Street
                    // <50km/h
                    case 8:
                        return Math.min(sLv, 50); // Bus-way in boulevard Street 50km/h
                    case 9:
                        return Math.min(sLv, 50); // Bus-way in boulevard Street <50km/h
                }
                break;
        }
        throw new IllegalArgumentException("Unknown road type, please check (type="
                + type + ",subtype=" + subtype + ").");
    }

    public int getFileVersion() {
        return this.fileVersion;
    }

    /**
     * set Coefficient version  (1 = 2015, 2 = 2020)
     *
     * @param fileVersion
     */
    public void setFileVersion(int fileVersion) {
        this.fileVersion = fileVersion;
    }

    /**
     * Compute {@link RoadCnossosParameters#speedHgv}
     * and {@link RoadCnossosParameters#speedLv} from theses parameters
     *
     * @param speed_junction   Speed in the junction section
     * @param speed_max        Maximum speed authorized
     * @param copound_roadtype Road surface type.
     * @param is_queue         If true use speed_junction in speedLoad
     */
    public void setSpeedFromRoadCaracteristics(double speedLoad, double speed_junction, boolean is_queue, double speed_max, int copound_roadtype) {
        // Separation of main index and sub index
        final int roadtype = copound_roadtype / 10;
        final int roadSubType = copound_roadtype - (roadtype * 10);
        if (speed_junction > 0. && is_queue) {
            speedLv = speed_junction;
        } else if (speedLoad > 0.) {
            speedLv = speedLoad;
        } else {
            speedLv = speed_max;
        }
        speedHgv = getVPl(speedLv, speed_max, roadtype, roadSubType);
        speedMv = getVPl(speedLv, speed_max, roadtype, roadSubType);
        speedWav = getVPl(speedLv, speed_max, roadtype, roadSubType);
        speedWbv = getVPl(speedLv, speed_max, roadtype, roadSubType);
    }

    /**
     * Eq. 2.2.13
     * Set the Slope Percentage without the limit of [-12% ; 12%]
     * @param slopePercentage Gradient percentage of road
     */
    public void setSlopePercentage_without_limit(double slopePercentage) {
        this.slopePercentage = slopePercentage;
    }

    public double getLvPerHour() {
        return lvPerHour;
    }

    public double getMvPerHour() {
        return mvPerHour;
    }

    public double getHgvPerHour() {
        return hgvPerHour;
    }

    public double getWavPerHour() {
        return wavPerHour;
    }

    public double getWbvPerHour() {
        return wbvPerHour;
    }

    public double getSlopePercentage() {
        return slopePercentage;
    }

    /**
     * Eq. 2.2.13
     * Set the Slope Percentage
     * Limitation to the accepted slope from -12% to 12%
     * @param slopePercentage Gradient percentage of road from -12 % to 12 %
     */
    public void setSlopePercentage(double slopePercentage) {
        this.slopePercentage = Math.min(12., Math.max(-12., slopePercentage));
    }

    public double getWay() {
        return way;
    }

    /**
     * Set way of the road section
     * 1= from the smallest Primary Key to the largest
     * 2 = opposite way
     * 3 = both ways
     * @param way
     */
    public void setWay(int way) {
        this.way = way;
    }

    /**
     * Get the speed for light vehicle
     * For speeds less than 20 km/h it shall have the same sound power level as defined by the formula for vm = 20 km/h.
     * @return
     */
    public double getSpeedLv() throws IOException {
        if (speedLv < 20) {
            speedLv = 20;
        }
        return speedLv;
    }

    /**
     * Get the speed for medium vehicle
     * For speeds less than 20 km/h it shall have the same sound power level as defined by the formula for vm = 20 km/h.
     * @return
     */
    public double getSpeedMv() throws IOException {
        if (speedMv < 20) {
            speedMv = 20;
        }
        return speedMv;
    }

    /**
     * Get the speed for heavy vehicle
     * For speeds less than 20 km/h it shall have the same sound power level as defined by the formula for vm = 20 km/h.
     * @return
     */
    public double getSpeedHgv() throws IOException {
        if (speedHgv < 20) {
            speedHgv = 20;
        }
        return speedHgv;
    }

    /**
     * Get the speed for two wheels vehicle (type a)
     * For speeds less than 20 km/h it shall have the same sound power level as defined by the formula for vm = 20 km/h.
     * @return
     */
    public double getSpeedWav() throws IOException {
        if (speedWav < 20) {
            speedWav = 20;
        }
        return speedWav;
    }

    /**
     * Get the speed for two wheels vehicle (type b)
     * For speeds less than 20 km/h it shall have the same sound power level as defined by the formula for vm = 20 km/h.
     * @return
     */
    public double getSpeedWbv() throws IOException {
        if (speedWbv < 20) {
            speedWbv = 20;
        }
        return speedWbv;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getRoadSurface() {
        return roadSurface;
    }

    public void setRoadSurface(String roadSurface) {
        this.roadSurface = roadSurface;
    }

    public double getTsStud() {
        return tsStud;
    }

    public double getqStudRatio() {
        return qStudRatio;
    }

    public double getJunc_dist() {
        return Junc_dist;
    }

    public void setJunc_dist(double junc_dist) {
        Junc_dist = junc_dist;
    }

    public int getJunc_type() {
        return Junc_type;
    }

    public void setJunc_type(int junc_type) {
        Junc_type = junc_type;
    }

}

