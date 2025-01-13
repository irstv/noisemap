/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.utils.profiler;

import org.h2gis.api.ProgressVisitor;

import java.util.Locale;

/**
 * Metric that write progression value in percentage
 */
public class ProgressMetric implements ProfilerThread.Metric {
    private ProgressVisitor progressVisitor;

    public ProgressMetric(ProgressVisitor progressVisitor) {
        this.progressVisitor = progressVisitor;
    }

    @Override
    public String[] getColumnNames() {
        return new String[] {"progression"};
    }

    @Override
    public String[] getCurrentValues() {
        return new String[] {String.format(Locale.ROOT, "%.2f", progressVisitor.getProgression() *  100.0)};
    }

    @Override
    public void tick(long currentMillis) {

    }
}
