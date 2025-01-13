/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import org.h2gis.api.ProgressVisitor;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ReceiverStatsMetric;

import java.util.concurrent.Callable;

import static org.noise_planet.noisemodelling.pathfinder.PathFinder.LOGGER;

/**
 * A Thread class to evaluate all receivers cut planes.
 * Return true if the computation is done without issues
 */
public final class ThreadPathFinder implements Callable<Boolean> {
    int startReceiver; // Included
    int endReceiver; // Excluded
    PathFinder propagationProcess;
    ProgressVisitor visitor;
    IComputePathsOut dataOut;
    Scene data;


    /**
     * Create the ThreadPathFinder constructor
     * @param startReceiver
     * @param endReceiver
     * @param propagationProcess
     * @param visitor
     * @param dataOut
     * @param data
     */
    public ThreadPathFinder(int startReceiver, int endReceiver, PathFinder propagationProcess,
                            ProgressVisitor visitor, IComputePathsOut dataOut,
                            Scene data) {
        this.startReceiver = startReceiver;
        this.endReceiver = endReceiver;
        this.propagationProcess = propagationProcess;
        this.visitor = visitor;
        this.dataOut = dataOut.subProcess();
        this.data = data;
    }

    /**
     * Executes the computation of ray paths for each receiver in the specified range.
     */
    @Override
    public Boolean call() throws Exception {
        try {
            for (int idReceiver = startReceiver; idReceiver < endReceiver; idReceiver++) {
                if (visitor != null) {
                    if (visitor.isCanceled()) {
                        break;
                    }
                }
                PathFinder.ReceiverPointInfo rcv = new PathFinder.ReceiverPointInfo(idReceiver, data.receivers.get(idReceiver));

                long start = 0;
                if(propagationProcess.getProfilerThread() != null) {
                    start = propagationProcess.getProfilerThread().timeTracker.get();
                }

                propagationProcess.computeRaysAtPosition(rcv, dataOut, visitor);

                // Save computation time for this receiver
                if(propagationProcess.getProfilerThread() != null &&
                        propagationProcess.getProfilerThread().getMetric(ReceiverStatsMetric.class) != null) {
                    propagationProcess.getProfilerThread().getMetric(ReceiverStatsMetric.class).onEndComputation(idReceiver,
                            (int) (propagationProcess.getProfilerThread().timeTracker.get() - start));
                }

                if (visitor != null) {
                    visitor.endStep();
                }
            }
        } catch (Exception ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
            if (visitor != null) {
                visitor.cancel();
            }
            throw ex;
        }
        return true;
    }
}