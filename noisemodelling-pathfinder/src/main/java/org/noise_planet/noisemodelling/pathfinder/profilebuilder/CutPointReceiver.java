/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.locationtech.jts.geom.Coordinate;

public class CutPointReceiver  extends CutPoint {

    /**
     * External identifier of the receiver (from table)
     */
    public long receiverPk = -1;

    public CutPointReceiver() {

    }

    public CutPointReceiver(Coordinate location) {
        this.coordinate = location;
    }


    public CutPointReceiver(CutPoint receiver) {
        super(receiver);
    }

    /**
     * Index in the subdomain
     */
    @JsonIgnore
    public int id = -1;

    @Override
    public String toString() {
        return "CutPointReceiver{" +
                "\ngroundCoefficient=" + groundCoefficient +
                "\n, zGround=" + zGround +
                "\n, coordinate=" + coordinate +
                "\n, id=" + id +
                "\n, receiverPk=" + receiverPk +
                "\n}\n";
    }
}
