/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc.railway;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.noise_planet.noisemodelling.emission.railway.RailWayParameters;
import org.noise_planet.noisemodelling.emission.railway.cnossos.RailWayCnossosParameters;

import java.util.ArrayList;
import java.util.List;

import static org.noise_planet.noisemodelling.jdbc.utils.MakeParallelLines.MakeParallelLine;


public class RailWayLWGeom {
    RailWayCnossosParameters railWayLW;
    RailWayCnossosParameters railWayLWDay;
    RailWayCnossosParameters railWayLWEvening;
    RailWayCnossosParameters railWayLWNight;
    List<LineString> geometry;
    int pk = -1;
    int nbTrack;
    String idSection;
    double distance = 2;
    double gs = 1.0;

    // Default constructor
    public RailWayLWGeom() {

    }


    /**
     * Constructs a new ailWayLWGeom object by copying the attributes of another RailWayLWGeom object.
     *  * <p>
     * @param other
     */
    public RailWayLWGeom(RailWayLWGeom other) {
        this.railWayLW = other.railWayLW;
        this.railWayLWDay = other.railWayLWDay;
        this.railWayLWEvening = other.railWayLWEvening;
        this.railWayLWNight = other.railWayLWNight;
        this.geometry = other.geometry;
        this.pk = other.pk;
        this.nbTrack = other.nbTrack;
        this.idSection = other.idSection;
        this.distance = other.distance;
        this.gs = other.gs;
    }

    public double getGs() {
        return gs;
    }

    public void setGs(double gs) {
        this.gs = gs;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public RailWayParameters getRailWayLW() {
        return railWayLW;
    }

    public void setRailWayLW(RailWayCnossosParameters railWayLW) {
        this.railWayLW = railWayLW;
    }
    public RailWayParameters getRailWayLWDay() {
        return railWayLWDay;
    }

    public void setRailWayLWDay(RailWayCnossosParameters railWayLWDay) {
        this.railWayLWDay = railWayLWDay;
    }
    public RailWayParameters getRailWayLWEvening() {
        return railWayLWEvening;
    }

    public void setRailWayLWEvening(RailWayCnossosParameters railWayLWEvening) {
        this.railWayLWEvening = railWayLWEvening;
    }
    public RailWayParameters getRailWayLWNight() {
        return railWayLWNight;
    }

    public void setRailWayLWNight(RailWayCnossosParameters railWayLWNight) {
        this.railWayLWNight = railWayLWNight;
    }

    public int getNbTrack() {
        return nbTrack;
    }

    public String getIdSection() {
        return idSection;
    }

    public void setIdSection(String idSection) {
        this.idSection = idSection;
    }
    public void setNbTrack(int nbTrack) {
        this.nbTrack = nbTrack;
    }

    public List<LineString> getGeometry() {
        return  geometry;
    }


    public int getPK() {
        return pk;
    }

    public int setPK(int pk) {
        return this.pk=pk;
    }

    public void setGeometry(List<LineString> geometry) {
        this.geometry = geometry;
    }


    /**
     * Retrieves the geometry of the railway line with multiple tracks.
     * @return a list of LineString geometries
     */
    public List<LineString> getRailWayLWGeometry() {
        List<LineString> geometries = new ArrayList<>();


        boolean even = false;
        if (nbTrack % 2 == 0) even = true;

        if (nbTrack == 1) {
            geometries.addAll(getGeometry());
            return geometries;
        }else {

            if (even) {
                for (int j=0; j < nbTrack/2 ; j++){
                    for (LineString subGeom : getGeometry()) {
                        geometries.add( MakeParallelLine(subGeom, ( distance / 2) + distance * j));
                        geometries.add(MakeParallelLine(subGeom, -((distance / 2) + distance * j)));
                    }
                }
            } else {
                for (int j=1; j <= ((nbTrack-1)/2) ; j++) {
                    for (LineString subGeom : getGeometry()) {
                        geometries.add( MakeParallelLine(subGeom,  distance * j));
                        geometries.add(MakeParallelLine(subGeom, -( distance * j)));
                    }
                }
                LineMerger centerLine = new LineMerger();
                centerLine.add(getGeometry());
                geometries.addAll(centerLine.getMergedLineStrings());
            }
            return geometries;
        }
    }

}
