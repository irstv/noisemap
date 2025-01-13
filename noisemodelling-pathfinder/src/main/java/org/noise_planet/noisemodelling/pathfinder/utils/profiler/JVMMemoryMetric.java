/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.utils.profiler;

public class JVMMemoryMetric implements ProfilerThread.Metric {
    @Override
    public String[] getColumnNames() {
        return new String[] {"jvm_used_heap_mb", "jvm_max_heap_mb"};
    }

    /**
     * Allow to get the current value
     * @return a list of values in String
     */
    @Override
    public String[] getCurrentValues() {
        Runtime r = Runtime.getRuntime();
        return new String[] {Long.toString((r.totalMemory() - r.freeMemory()) / 1048576L),
                Long.toString(r.totalMemory() / 1048576L)};
    }

    @Override
    public void tick(long currentMillis) {

    }
}
