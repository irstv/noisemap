/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.utils.profiler;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Generate stats about receiver computation time
 */
public class ReceiverStatsMetric implements ProfilerThread.Metric {
    private ConcurrentLinkedDeque<ReceiverComputationTime> receiverComputationTimes = new ConcurrentLinkedDeque<>();
    private ConcurrentLinkedDeque<ReceiverRays> receiverRaysDeque = new ConcurrentLinkedDeque<>();
    private DescriptiveStatistics computationTime = new DescriptiveStatistics();
    private DescriptiveStatistics computationRays = new DescriptiveStatistics();

    public ReceiverStatsMetric() {
    }

    @Override
    public void tick(long currentMillis) {
        while (!receiverComputationTimes.isEmpty()) {
            ReceiverComputationTime receiverProfile = receiverComputationTimes.pop();
            computationTime.addValue(receiverProfile.computationTime);
        }
        while (!receiverRaysDeque.isEmpty()) {
            ReceiverRays receiverProfile = receiverRaysDeque.pop();
            computationRays.addValue(receiverProfile.numberOfRays);
        }
    }

    @Override
    public String[] getColumnNames() {
        return new String[] {"receiver_min","receiver_median","receiver_mean","receiver_max", "receiver_median_rays", "receiver_max_rays"};
    }

    public void onEndComputation(int receiverId, int computationTime) {
        receiverComputationTimes.add(new ReceiverComputationTime(receiverId, computationTime));
    }

    public void onReceiverRays(int receiverId, int receiverRays) {
        receiverRaysDeque.add(new ReceiverRays(receiverId, receiverRays));
    }

    @Override
    public String[] getCurrentValues() {
        String[] res = new String[] {
                Integer.toString((int) computationTime.getMin()),
                Integer.toString((int) computationTime.getPercentile(50)),
                Integer.toString((int) computationTime.getMean()),
                Integer.toString((int) computationTime.getMax()),
                Integer.toString((int) computationRays.getPercentile(50)),
                Integer.toString((int) computationRays.getMax())
        };
        computationTime.clear();
        computationRays.clear();
        return res;
    }

    private static class ReceiverComputationTime {
        public int receiverId;
        public int computationTime;

        /**
         * Create the ReceiverComputationTime constructor
         * @param receiverId
         * @param computationTime
         */
        public ReceiverComputationTime(int receiverId, int computationTime) {
            this.receiverId = receiverId;
            this.computationTime = computationTime;
        }
    }

    private static class ReceiverRays {
        public int receiverId;
        public int numberOfRays;

        /**
         * Create the ReceiverRays constructor
         * @param receiverId
         * @param numberOfRays
         */
        public ReceiverRays(int receiverId, int numberOfRays) {
            this.receiverId = receiverId;
            this.numberOfRays = numberOfRays;
        }
    }
}