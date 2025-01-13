/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.propagation;

import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.IComputePathsOut;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPathBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Receive vertical cut plane, compute the attenuation corresponding to this plane
 */
public class AttenuationVisitor implements IComputePathsOut {
    public Attenuation multiThreadParent;
    public List<Attenuation.SourceReceiverAttenuation> receiverAttenuationLevels = new ArrayList<>();
    public List<CnossosPath> pathParameters = new ArrayList<CnossosPath>();
    public AttenuationCnossosParameters attenuationCnossosParameters;
    public boolean keepRays = false;

    public AttenuationVisitor(Attenuation multiThreadParent, AttenuationCnossosParameters attenuationCnossosParameters) {
        this.multiThreadParent = multiThreadParent;
        this.keepRays = multiThreadParent.exportPaths;
        this.attenuationCnossosParameters = attenuationCnossosParameters;
    }

    @Override
    public PathSearchStrategy onNewCutPlane(CutProfile cutProfile) {
        final Scene scene = multiThreadParent.inputData;
        CnossosPath cnossosPath = CnossosPathBuilder.computeAttenuationFromCutProfile(cutProfile, scene.isBodyBarrier(),
                scene.freq_lvl, scene.gS);
        if(cnossosPath != null) {
            addPropagationPaths(cutProfile.getSource(), cutProfile.getReceiver(), Collections.singletonList(cnossosPath));
        }
        return PathSearchStrategy.CONTINUE;
    }

    /**
     * Get propagation path result
     * @param source Source identifier
     * @param receiver Receiver identifier
     * @param path Propagation path result
     */
    public double[] addPropagationPaths(CutPointSource source, CutPointReceiver receiver, List<CnossosPath> path) {
        double[] aGlobalMeteo = multiThreadParent.computeCnossosAttenuation(attenuationCnossosParameters, source.id, source.li, path);
        multiThreadParent.rayCount.addAndGet(path.size());
        if(keepRays) {
            pathParameters.addAll(path);
        }
        if (aGlobalMeteo != null) {
            receiverAttenuationLevels.add(new Attenuation.SourceReceiverAttenuation(receiver.receiverPk, receiver.id,
                    source.sourcePk, source.id, aGlobalMeteo, receiver.coordinate));
            return aGlobalMeteo;
        } else {
            return new double[0];
        }
    }

    /**
     * No more propagation paths will be pushed for this receiver identifier
     * @param receiverId
     */
    @Override
    public void finalizeReceiver(int receiverId) {
        if(keepRays && !pathParameters.isEmpty()) {
            multiThreadParent.pathParameters.addAll(this.pathParameters);
            multiThreadParent.propagationPathsSize.addAndGet(pathParameters.size());
            this.pathParameters.clear();
        }
        long receiverPK = receiverId;
        if(multiThreadParent.inputData != null) {
            if(receiverId < multiThreadParent.inputData.receiversPk.size()) {
                receiverPK = multiThreadParent.inputData.receiversPk.get((int)receiverId);
            }
        }
        multiThreadParent.finalizeReceiver(receiverId);
        if(multiThreadParent.receiversAttenuationLevels != null) {
            // Push merged sources into multi-thread parent
            // Merge levels for each receiver for lines sources
            Map<Integer, double[]> levelsPerSourceLines = new HashMap<>();
            AtomicReference<Coordinate> receiverPosition = new AtomicReference<>(new Coordinate());
            for (Attenuation.SourceReceiverAttenuation lvl : receiverAttenuationLevels) {
                if(lvl.receiverPosition != null) {
                    receiverPosition.set(lvl.receiverPosition);
                }
                if (!levelsPerSourceLines.containsKey(lvl.sourceIndex)) {
                    levelsPerSourceLines.put(lvl.sourceIndex, lvl.value);
                } else {
                    // merge
                    levelsPerSourceLines.put(lvl.sourceIndex,
                            AcousticIndicatorsFunctions.sumDbArray(levelsPerSourceLines.get(lvl.sourceIndex),
                            lvl.value));
                }
            }
            for (Map.Entry<Integer, double[]> entry : levelsPerSourceLines.entrySet()) {
                long sourcePk = -1;
                if(entry.getKey() >= 0 && entry.getKey() < multiThreadParent.inputData.sourcesPk.size()) {
                    sourcePk = multiThreadParent.inputData.sourcesPk.get(entry.getKey());
                }
                multiThreadParent.receiversAttenuationLevels.add(
                        new Attenuation.SourceReceiverAttenuation(receiverPK, receiverId,  sourcePk,
                                entry.getKey(), entry.getValue(), receiverPosition.get()));
            }
        }
        receiverAttenuationLevels.clear();
    }

    /**
     *
     * @return an instance of the interface IComputePathsOut
     */
    @Override
    public IComputePathsOut subProcess() {
        return multiThreadParent.subProcess();
    }
}