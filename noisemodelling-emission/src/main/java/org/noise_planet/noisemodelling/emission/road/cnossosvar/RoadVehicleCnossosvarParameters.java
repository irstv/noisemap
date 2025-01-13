/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.emission.road.cnossosvar;

import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossosParameters;

/**
 * Parameters for CNOSSOS variant method for one unique vehicle
 * This class extends the class RoadCnossosParameters
 * @author Nicolas Fortin
 * @author Pierre Aumond
 * @author Arnaud Can
 */

public class RoadVehicleCnossosvarParameters extends RoadCnossosParameters {
    private final double speed;
    private final double acceleration;
    private final String veh_type;
    private final int acc_type;
    private final boolean Stud;
    private final double LwStd;
    private final int VehId;
    private int surfaceAge;

    /**
     * Simplest road noise evaluation
     * @param speed Vehicle speed
     * @param acceleration Vehicle acceleration
     * @param veh_type Vehicle type (CNOSSOS categories)
     * @param acc_type Acceleration mode (1 = Distance to Junction (CNOSSOS), 2= Correction from IMAGINE with bounds , 3 = Correction from IMAGINE without bounds)
     * @param Stud True = equipped with studded tyres
     * @param LwStd Standard Deviation of Lw
     * @param VehId Vehicle ID used as a seed for LwStd
     */
    public RoadVehicleCnossosvarParameters(double speed, double acceleration, String veh_type, int acc_type, boolean Stud, double LwStd, int VehId) {
        super();
        this.speed = speed;
        this.acceleration = acceleration;
        this.veh_type = veh_type;
        this.acc_type = acc_type;
        this.Stud = Stud;
        this.LwStd = LwStd;
        this.VehId = VehId;
    }

    /**
     * @return Road surface age
     */
    public int getSurfaceAge() {
        return surfaceAge;
    }

    /**
     * @param surfaceAge Road surface age in years, from 1 to 10 years.
     */
    public void setSurfaceAge(int surfaceAge) {
        this.surfaceAge = Math.max(1, Math.min(10, surfaceAge));
    }

    public double getLwStd() {
        return LwStd;
    }

    public int getVehId() {
        return VehId;
    }

    public double getSpeed() {
        return speed;
    }

    public int getAcc_type() {
        return acc_type;
    }

    public String getVeh_type() {
        return veh_type;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public boolean getStud() {
        return Stud;
    }

}

