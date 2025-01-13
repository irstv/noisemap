/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.road.cnossosvar;

import java.io.IOException;
import java.util.Random;

import static org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossos.*;
import static org.noise_planet.noisemodelling.emission.utils.Utils.sumDbValues;

/**
 * Return the emission sound level for one unique vehicle
 * The used method is very close to CNOSSOS (CNOSSOS variant) with some adjustements,
 * especially to take into account accelerations or the variability on emission sound levels between the vehicle
 * of a same category
 * @author Nicolas Fortin, Université Gustave Eiffel
 * @author Pierre Aumond, Université Gustave Eiffel
 * @author Arnaud Can, Université Gustave Eiffel
 */

public class RoadVehicleCnossosvar {

    /**
     * Emission sound level for one unique vehicle
     * @param parameters Noise emission parameters
     * @return Noise level in dB
     */
    public static double evaluate(RoadVehicleCnossosvarParameters parameters) throws IOException {
        final boolean Stud = parameters.getStud();
        final double Junc_dist = parameters.getJunc_dist();
        final int Junc_type = parameters.getJunc_type();
        final int acc_type = parameters.getAcc_type();
        final String veh_type = parameters.getVeh_type();
        final int VehId = parameters.getVehId();
        final double acceleration = parameters.getAcceleration();
        double speed = parameters.getSpeed();

        final int freqParam = parameters.getFrequency();
        final double Temperature = parameters.getTemperature();
        final String roadSurface = parameters.getRoadSurface();
        final int coeffVer = parameters.getFileVersion();

        // ///////////////////////
        // Noise road/tire CNOSSOS
        double RoadLvl; // Lw/m (1 veh/h)

        // Noise level
        RoadLvl = getNoiseLvl(getCoeff("ar", freqParam, veh_type, coeffVer), getCoeff("br", freqParam, veh_type, coeffVer), speed, 70.);

        // Correction by temperature p. 36
        switch (veh_type) {
            case "1":
                RoadLvl = RoadLvl + 0.08 * (20 - Temperature); // K = 0.08  p. 36
                break;
            case "2":
                RoadLvl = RoadLvl + 0.04 * (20 - Temperature); // K = 0.04 p. 36
                break;
            case "3":
                RoadLvl = RoadLvl + 0.04 * (20 - Temperature); // K = 0.04 p. 36
                break;
            default:
                break;
        }


        // Rolling noise acceleration correction
        double coefficientJunctionDistance = Math.max(1 - Math.abs(Junc_dist) / 100, 0);
        RoadLvl = RoadLvl + getCr(veh_type, Junc_type, coeffVer) * coefficientJunctionDistance;


        //Studied tyres
        if (veh_type.equals("1")) { // because studded tyres are only on Cat 1 vehicle
            if (Stud) {
                double speedStud = (speed >= 90) ? 90 : speed;
                speedStud = (speedStud <= 50) ? 50 : speedStud;
                double deltaStud = getNoiseLvl(getCoeff("a", freqParam, veh_type, coeffVer), getCoeff("b", freqParam, veh_type, coeffVer), speedStud, 70.);
                RoadLvl = RoadLvl + Math.pow(10, deltaStud / 10);
            }
        }

        //Road surface correction on rolling noise
        RoadLvl = RoadLvl + getNoiseLvl(getA_RoadSurfaceCoeff(freqParam, veh_type, roadSurface, coeffVer), getB_RoadSurfaceCoeff(veh_type, roadSurface, coeffVer), speed, 70.);


        // ///////////////////////
        // Noise motor
        // Calculate the emission powers of motors lights vehicles and heavies goods vehicles.
        double MotorLvl;
        RoadLvl = (speed <= 20) ? 0 : RoadLvl;
        speed = (speed <= 20) ? 20 : speed; // Because when vehicles are stopped they still emit motor sounds.
        // default or steady speed.
        MotorLvl = getCoeff("ap", freqParam, veh_type, coeffVer) + getCoeff("bp", freqParam, veh_type, coeffVer) * (speed - 70) / 70;

        // Propulsion noise acceleration correction
        double aMax;
        switch (acc_type) {
            case 1:
                if (veh_type.equals("1") || veh_type.equals("2") || veh_type.equals("3")) {
                   MotorLvl = MotorLvl + getCp(veh_type, Junc_type, coeffVer) * coefficientJunctionDistance;
                }
                break;
            case 2:
                switch (veh_type) {
                    case "1":
                        aMax = 2;
                        if (acceleration >= -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 5.0;
                        }
                        if (acceleration >= -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 2.0;
                        }
                        if (acceleration < -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + -1 * 5.0;
                        }
                        if (acceleration < -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + -1 * 2.0;
                        }
                        break;
                    case "2":
                    case "3":
                        aMax = 1;
                        if (acceleration >= -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 7.0;
                        }
                        if (acceleration >= -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 3.0;
                        }
                        if (acceleration < -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + -1 * 7.0;
                        }
                        if (acceleration < -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + -1 * 3.0;
                        }
                        break;
                    case "4a":
                    case "4b":
                        aMax = 4;
                        if (acceleration >= -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 5.0;
                        }
                        if (acceleration >= -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 2.0;
                        }
                        if (acceleration < -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + -1 * 5.0;
                        }
                        if (acceleration < -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + -1 * 2.0;
                        }
                        break;
                    default:
                        break;
                }
                break;
            case 3:
                switch (veh_type) {
                    case "1":
                    case "4a":
                    case "4b":
                        aMax = 10;
                        if (acceleration >= -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 5.0;
                        }
                        if (acceleration >= -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 2.0;
                        }
                        if (acceleration < -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + -1 * 5.0;
                        }
                        if (acceleration < -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + -1 * 2.0;
                        }
                        break;
                    case "2":
                    case "3":
                        aMax = 10;
                        if (acceleration >= -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 7.0;
                        }
                        if (acceleration >= -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + Math.min(acceleration, aMax) * 3.0;
                        }
                        if (acceleration < -1 & freqParam < 250) {
                            MotorLvl = MotorLvl + -1 * 7.0;
                        }
                        if (acceleration < -1 & freqParam >= 250) {
                            MotorLvl = MotorLvl + -1 * 3.0;
                        }
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }


        // Correction gradient
        switch (veh_type) {
            case "1":
                if (parameters.getSlopePercentage() < -6) {
                    // downwards 2% <= p <= 6%
                    // Steady and deceleration, the same formulae
                    MotorLvl = MotorLvl + (Math.min(12, -parameters.getSlopePercentage()) - 6) / 1;
                } else if (parameters.getSlopePercentage() <= 2) {
                    // 0% <= p <= 2%
                    MotorLvl = MotorLvl + 0.;
                } else {
                    // upwards 2% <= p <= 6%
                    MotorLvl = MotorLvl + ((speed / 100) * ((Math.min(12, parameters.getSlopePercentage()) - 2) / 1.5));
                }
                break;
            case "2":
                if (parameters.getSlopePercentage() < -4) {
                    // Steady and deceleration, the same formulae
                    MotorLvl = MotorLvl + ((speed - 20) / 100) * (Math.min(12, -1 * parameters.getSlopePercentage()) - 4) / 0.7;
                } else if (parameters.getSlopePercentage() <= 0) {
                    MotorLvl = MotorLvl + 0.;

                } else {
                    MotorLvl = MotorLvl + (speed / 100) * (Math.min(12, parameters.getSlopePercentage())) / 1;
                }
                break;
            case "3":
                if (parameters.getSlopePercentage() < -4) {
                    // Steady and deceleration, the same formulae
                    MotorLvl = MotorLvl + ((speed - 10) / 100) * (Math.min(12, -1 * parameters.getSlopePercentage()) - 4) / 0.5;
                } else if (parameters.getSlopePercentage() <= 0) {
                    MotorLvl = MotorLvl + 0.;
                } else {
                    MotorLvl = MotorLvl + (speed / 100) * (Math.min(12, parameters.getSlopePercentage())) / 0.8;
                }
                break;
            default:
                break;
        }


        // Correction road on propulsion noise
        MotorLvl = MotorLvl + Math.min(getA_RoadSurfaceCoeff(freqParam, veh_type, roadSurface, coeffVer), 0.);

        // add a random variation of LW sound level depending of the vehId following the LW standard error (LwStd)
        Random r = new Random(VehId);
        double deltaLwdistrib = 0.115 * Math.pow(parameters.getLwStd(), 2.0); // Gozalo, G. R., Aumond, P., & Can, A. (2020). Variability in sound power levels: Implications for static and dynamic traffic models. Transportation Research Part D: Transport and Environment, 84, 102339.

        return sumDbValues(RoadLvl, MotorLvl) - deltaLwdistrib + r.nextGaussian() * parameters.getLwStd();
    }


}
