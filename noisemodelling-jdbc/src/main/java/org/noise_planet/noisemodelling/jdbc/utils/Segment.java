/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

import java.util.ArrayList;
import java.util.List;


public class Segment {
    Coordinate p0;
    Coordinate p1;
    List<Coordinate> controlPoints = new ArrayList<>();

    public Segment(Coordinate p0, Coordinate p1) {
        this.p0 = p0;
        this.p1 = p1;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Segment)) {
            return false;
        }
        Segment other = (Segment) obj;
        return (this.p0.equals(other.p0) && this.p1.equals(other.p1)) ||
                (this.p1.equals(other.p0) && this.p0.equals(other.p1));
    }

    Envelope getEnvelope(){
        return new Envelope(p0, p1);
    }

    /**
     *
     * @param controlPoint1
     * @param controlPoint2
     */
    public void addControlPoints(Coordinate controlPoint1, Coordinate controlPoint2) {
        controlPoints.add(controlPoint1);
        controlPoints.add(controlPoint2);
    }

    public List<Coordinate> getControlPoints() {
        return controlPoints;
    }
}