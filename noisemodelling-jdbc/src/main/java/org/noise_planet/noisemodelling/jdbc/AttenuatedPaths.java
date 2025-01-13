/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.propagation.Attenuation;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public class AttenuatedPaths {
    public final AtomicLong queueSize = new AtomicLong(0);
    public final AtomicLong totalRaysInserted = new AtomicLong(0);
    public final ConcurrentLinkedDeque<Attenuation.SourceReceiverAttenuation> lDayLevels = new ConcurrentLinkedDeque<>();
    public final ConcurrentLinkedDeque<Attenuation.SourceReceiverAttenuation> lEveningLevels = new ConcurrentLinkedDeque<>();
    public final ConcurrentLinkedDeque<Attenuation.SourceReceiverAttenuation> lNightLevels = new ConcurrentLinkedDeque<>();
    public final ConcurrentLinkedDeque<Attenuation.SourceReceiverAttenuation> lDenLevels = new ConcurrentLinkedDeque<>();
    public final ConcurrentLinkedDeque<CnossosPath> rays = new ConcurrentLinkedDeque<>();
}
