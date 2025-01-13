/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.railway;
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

import org.noise_planet.noisemodelling.emission.LineSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.noise_planet.noisemodelling.emission.utils.Utils.Vperhour2NoiseLevel;
import static org.noise_planet.noisemodelling.emission.utils.Utils.sumDbArray;

/**
 * Data result stockage
 */
public class RailWayParameters {
    public Map<String, LineSource> railwaySourceList = new HashMap<>();


    public Map<String, LineSource> getRailwaySourceList() {
        return railwaySourceList;
    }

    public void setRailwaySourceList(Map<String, LineSource> railwaySourceList) {
        this.railwaySourceList = railwaySourceList;
    }

    /**
     * method adds a railway source to the list of railway sources, associating it with a specified ID.
     * @param ID
     * @param lineSource
     */
    public void addRailwaySource(String ID, LineSource lineSource) {
        this.railwaySourceList.put(ID, lineSource);
    }

    /**
     *
     * @param lineSource1
     * @param lineSource2
     * @return an instance of RailWayParameters
     */
    public RailWayParameters sumRailwaySource(RailWayParameters lineSource1, RailWayParameters lineSource2) {
        if (!lineSource2.getRailwaySourceList().isEmpty()){
            for (Map.Entry<String, LineSource> railwaySourceEntry : lineSource1.getRailwaySourceList().entrySet()) {
                double[]  lW1 = railwaySourceEntry.getValue().getlW();
                double[]  lW2 = lineSource2.getRailwaySourceList().get(railwaySourceEntry.getKey()).getlW();
                lineSource1.getRailwaySourceList().get(railwaySourceEntry.getKey()).setlW(sumDbArray(lW1, lW2));
            }
        }
        return lineSource1;
    }

    /**
     *
     * @param Qm
     * @param vm
     * @throws IOException
     */
    public void appendVperHour(double Qm, double vm) throws IOException {
        for (Map.Entry<String, LineSource> railwaySourceEntry : railwaySourceList.entrySet()) {
            double[] lW ;
            lW = railwaySourceEntry.getValue().getlW();
            for (int i = 0; i < railwaySourceEntry.getValue().getlW().length; i++) {
                lW[i] = Vperhour2NoiseLevel(lW[i], Qm, vm);
            }
            railwaySourceEntry.getValue().setlW(lW);
        }
    }

}
