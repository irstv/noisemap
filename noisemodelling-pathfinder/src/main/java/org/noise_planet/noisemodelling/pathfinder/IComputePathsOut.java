/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;

import java.util.List;

/**
 * Instead of feeding a list and returning all vertical cut planes.
 * A visitor instance that implement this interface can skip planes and intervene in the search of cut planes.
 */
public interface IComputePathsOut {

    /**
     * A new vertical profile between a receiver and a source has been found
     *
     * @param cutProfile vertical profile
     * @return Will skip or not the next processing depending on this value.
     */
    PathSearchStrategy onNewCutPlane(CutProfile cutProfile);

    enum PathSearchStrategy {
        /**
         * Continue looking for vertical cut planes
         */
        CONTINUE,
        /**
         * Skip remaining potential vertical planes for this source point
         */
        SKIP_SOURCE,
        /**
         * Ignore other sources and process to the next receiver
         */
        SKIP_RECEIVER
    }

    /**
     * No more propagation paths will be pushed for this receiver identifier
     * @param receiverId Id of the receiver in the subdomain
     */
    void finalizeReceiver(int receiverId);

    /**
     * If the implementation does not support thread concurrency, this method is called to return an instance
     * @return
     */
    IComputePathsOut subProcess();
}
