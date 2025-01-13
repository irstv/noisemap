/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.checkerframework.checker.units.qual.C;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.IComputePathsOut;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.propagation.Attenuation;
import org.noise_planet.noisemodelling.propagation.AttenuationVisitor;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPathBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.*;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.wToDba;


public class NoiseMapInStack implements IComputePathsOut {
    NoiseMap noiseMapComputeRaysOut;
    NoiseMapParameters noiseMapParameters;
    AttenuationVisitor[] lDENAttenuationVisitor = new AttenuationVisitor[3];
    public List<CnossosPath> pathParameters = new ArrayList<CnossosPath>();

    /**
     * Constructs a NoiseMapInStack object with a multi-threaded parent NoiseMap instance.
     * @param multiThreadParent
     */
    public NoiseMapInStack(NoiseMap multiThreadParent) {
        this.noiseMapComputeRaysOut = multiThreadParent;
        this.noiseMapParameters = multiThreadParent.noiseEmissionMaker.noiseMapParameters;
        lDENAttenuationVisitor[0] = new AttenuationVisitor(multiThreadParent, multiThreadParent.dayPathData);
        lDENAttenuationVisitor[1] = new AttenuationVisitor(multiThreadParent, multiThreadParent.eveningPathData);
        lDENAttenuationVisitor[2] = new AttenuationVisitor(multiThreadParent, multiThreadParent.nightPathData);
        for (AttenuationVisitor attenuationVisitor : lDENAttenuationVisitor) {
            attenuationVisitor.keepRays = false;
        }

    }

    /**
     * Energetic sum of VerticeSL attenuation with WJ sources
     * @param wjSources
     * @param receiverAttenuationLevels
     * @return
     */
    double[] sumLevels(List<double[]> wjSources, List<Attenuation.SourceReceiverAttenuation> receiverAttenuationLevels) {
        double[] levels = new double[noiseMapComputeRaysOut.dayPathData.freq_lvl.size()];
        for (Attenuation.SourceReceiverAttenuation lvl : receiverAttenuationLevels) {
            if(wjSources.size() > lvl.sourceIndex && lvl.sourceIndex >= 0) {
                levels = sumArray(levels,
                        dbaToW(sumArray(wToDba(wjSources.get(lvl.sourceIndex)), lvl.value)));
            }
        }
        return levels;
    }


    /**
     * Processes the attenuation levels for a receiver and pushes the result into a concurrent linked deque.
     * @param receiverIndex              the index of the receiver in memory list
     * @param receiverPK                 the primary key of the receiver.
     * @param wjSources                  the list of source attenuation levels.
     * @param receiverAttenuationLevels  the list of attenuation levels from receiver to sources.
     * @param result                     the concurrent linked deque to push the result into.
     * @param feedStack                  {@code true} if the result should be pushed into the result stack, {@code false} otherwise.
     * @return the computed attenuation levels for the receiver.
     */
    double[] processAndPushResult(int receiverIndex, long receiverPK, List<double[]> wjSources, List<Attenuation.SourceReceiverAttenuation> receiverAttenuationLevels, ConcurrentLinkedDeque<Attenuation.SourceReceiverAttenuation> result, boolean feedStack) {
        double[] levels = sumLevels(wjSources, receiverAttenuationLevels);
        if(feedStack) {
            pushInStack(result, new Attenuation.SourceReceiverAttenuation(receiverPK,
                    receiverIndex,
                    -1,
                    -1,
                    wToDba(levels),
                    noiseMapComputeRaysOut.inputData.receivers.get(receiverIndex)
            ));
        }
        return levels;
    }

    @Override
    public PathSearchStrategy onNewCutPlane(CutProfile cutProfile) {
        final Scene scene = noiseMapComputeRaysOut.inputData;
        CnossosPath cnossosPath = CnossosPathBuilder.computeAttenuationFromCutProfile(cutProfile, scene.isBodyBarrier(),
                scene.freq_lvl, scene.gS);
        if(cnossosPath != null) {
            addPropagationPaths(cutProfile.getSource(), cutProfile.getReceiver(),
                    Collections.singletonList(cnossosPath));
        }
        return PathSearchStrategy.CONTINUE;
    }

    /**
     * Get propagation path result
     * @param source Source identifier
     * @param sourceLi Source power per meter coefficient
     * @param pathsParameter Propagation path result
     */
    public double[] addPropagationPaths(CutPointSource source, CutPointReceiver receiver, List<CnossosPath> pathsParameter) {

        long receiverId = receiver.receiverPk == -1 ? receiver.id : receiver.receiverPk;
        long sourceId = source.sourcePk == -1 ? source.id : source.sourcePk;

        noiseMapComputeRaysOut.rayCount.addAndGet(pathsParameter.size());
        if(noiseMapComputeRaysOut.exportPaths && !noiseMapComputeRaysOut.exportAttenuationMatrix) {
            for(CnossosPath cnossosPath : pathsParameter) {
                // Use only one ray as the ray is the same if we not keep absorption values
                // Copy path content in order to keep original ids for other method calls
                cnossosPath.setIdReceiver(receiverId);
                cnossosPath.setIdSource(sourceId);
                this.pathParameters.add(cnossosPath);
            }
        }

        double[] globalLevel = null;
        for(NoiseMapParameters.TIME_PERIOD timePeriod : NoiseMapParameters.TIME_PERIOD.values()) {
            for(CnossosPath pathParameters : pathsParameter) {
                if (globalLevel == null) {
                    globalLevel = lDENAttenuationVisitor[timePeriod.ordinal()].addPropagationPaths(source,
                            receiver, Collections.singletonList(pathParameters));
                } else {
                    globalLevel = AcousticIndicatorsFunctions.sumDbArray(globalLevel,
                            lDENAttenuationVisitor[timePeriod.ordinal()].addPropagationPaths(source,
                            receiver, Collections.singletonList(pathParameters)));
                }
                pathParameters.setTimePeriod(timePeriod.name());
                if(noiseMapComputeRaysOut.exportPaths && noiseMapComputeRaysOut.exportAttenuationMatrix) {
                    // copy ray for each time period because absorption is different for each period
                    if (noiseMapComputeRaysOut.inputData != null && sourceId < noiseMapComputeRaysOut.inputData.sourcesPk.size() && receiverId < noiseMapComputeRaysOut.inputData.receiversPk.size()) {
                        // Copy path content in order to keep original ids for other method calls
                        CnossosPath pathParametersPk = new CnossosPath(pathParameters);
                        pathParametersPk.setIdReceiver(receiver.receiverPk == -1 ? receiver.id : receiver.receiverPk);
                        pathParametersPk.setIdSource(source.sourcePk == -1 ? source.id : source.sourcePk);
                        this.pathParameters.add(pathParametersPk);
                    } else {
                        this.pathParameters.add(pathParameters);
                    }
                }
            }
        }
        return globalLevel;
    }

    /**
     * Pushes attenuation data into a concurrent linked deque.
     * @param stack Stack to feed
     * @param data receiver noise level in dB
     */
    public void pushInStack(ConcurrentLinkedDeque<Attenuation.SourceReceiverAttenuation> stack, Attenuation.SourceReceiverAttenuation data) {
        while(noiseMapComputeRaysOut.attenuatedPaths.queueSize.get() > noiseMapParameters.outputMaximumQueue) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                noiseMapParameters.aborted = true;
                break;
            }
            if(noiseMapParameters.aborted) {
                if(noiseMapComputeRaysOut != null && this.noiseMapComputeRaysOut.inputData != null &&
                        this.noiseMapComputeRaysOut.inputData.cellProg != null) {
                    this.noiseMapComputeRaysOut.inputData.cellProg.cancel();
                }
                return;
            }
        }
        stack.add(data);
        noiseMapComputeRaysOut.attenuatedPaths.queueSize.incrementAndGet();
    }

    /**
     *
     * @return an instance of the interface IComputePathsOut
     */
    @Override
    public IComputePathsOut subProcess() {
        return null;
    }

    /**
     * Adds Cnossos paths to a concurrent stack while maintaining the maximum stack size.
     * @param stack Stack to feed
     * @param data rays
     */
    public void pushInStack(ConcurrentLinkedDeque<CnossosPath> stack, Collection<CnossosPath> data) {
        while(noiseMapComputeRaysOut.attenuatedPaths.queueSize.get() > noiseMapParameters.outputMaximumQueue) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                noiseMapParameters.aborted = true;
                break;
            }
            if(noiseMapParameters.aborted) {
                if(noiseMapComputeRaysOut != null && this.noiseMapComputeRaysOut.inputData != null &&
                        this.noiseMapComputeRaysOut.inputData.cellProg != null) {
                    this.noiseMapComputeRaysOut.inputData.cellProg.cancel();
                }
                return;
            }
        }
        if(noiseMapParameters.getMaximumRaysOutputCount() == 0 || noiseMapComputeRaysOut.attenuatedPaths.totalRaysInserted.get() < noiseMapParameters.getMaximumRaysOutputCount()) {
            long newTotalRays = noiseMapComputeRaysOut.attenuatedPaths.totalRaysInserted.addAndGet(data.size());
            if(noiseMapParameters.getMaximumRaysOutputCount() > 0 && newTotalRays > noiseMapParameters.getMaximumRaysOutputCount()) {
                // too many rays, remove unwanted rays
                int newListSize = data.size() - (int)(newTotalRays - noiseMapParameters.getMaximumRaysOutputCount());
                List<CnossosPath> subList = new ArrayList<CnossosPath>(newListSize);
                for(CnossosPath pathParameters : data) {
                    subList.add(pathParameters);
                    if(subList.size() >= newListSize) {
                        break;
                    }
                }
                data = subList;
            }
            stack.addAll(data);
            noiseMapComputeRaysOut.attenuatedPaths.queueSize.addAndGet(data.size());
        }
    }

    /**
     * No more propagation paths will be pushed for this receiver identifier
     * @param receiverId
     */
    @Override
    public void finalizeReceiver(int receiverId) {
        Coordinate receiverPosition = receiverId >= 0 && receiverId < noiseMapComputeRaysOut.inputData.receivers.size() ?
                noiseMapComputeRaysOut.inputData.receivers.get(receiverId) : new Coordinate();
        if(!this.pathParameters.isEmpty()) {
            if(noiseMapParameters.getExportRaysMethod() == org.noise_planet.noisemodelling.jdbc.NoiseMapParameters.ExportRaysMethods.TO_RAYS_TABLE) {
                // Push propagation rays
                pushInStack(noiseMapComputeRaysOut.attenuatedPaths.rays, this.pathParameters);
            } else if(noiseMapParameters.getExportRaysMethod() == org.noise_planet.noisemodelling.jdbc.NoiseMapParameters.ExportRaysMethods.TO_MEMORY
                    && (noiseMapParameters.getMaximumRaysOutputCount() == 0 ||
                    noiseMapComputeRaysOut.propagationPathsSize.get() < noiseMapParameters.getMaximumRaysOutputCount())){
                int newRaysSize = noiseMapComputeRaysOut.propagationPathsSize.addAndGet(this.pathParameters.size());
                if(noiseMapParameters.getMaximumRaysOutputCount() > 0 && newRaysSize > noiseMapParameters.getMaximumRaysOutputCount()) {
                    // remove exceeded elements of the array
                    this.pathParameters = this.pathParameters.subList(0,
                            this.pathParameters.size() - Math.min( this.pathParameters.size(),
                                    newRaysSize - noiseMapParameters.getMaximumRaysOutputCount()));
                }
                noiseMapComputeRaysOut.pathParameters.addAll(this.pathParameters);
            }
            this.pathParameters.clear();
        }
        long receiverPK = receiverId;
        if(noiseMapComputeRaysOut.inputData != null) {
            if(receiverId >= 0 && receiverId < noiseMapComputeRaysOut.inputData.receiversPk.size()) {
                receiverPK = noiseMapComputeRaysOut.inputData.receiversPk.get((int)receiverId);
            }
        }
        double[] dayLevels = new double[0], eveningLevels = new double[0], nightLevels = new double[0];
        if (!noiseMapParameters.mergeSources) {
            // Aggregate by source id
            Map<Integer, NoiseMapParameters.TimePeriodParameters> levelsPerSourceLines = new HashMap<>();
            for (NoiseMapParameters.TIME_PERIOD timePeriod : org.noise_planet.noisemodelling.jdbc.NoiseMapParameters.TIME_PERIOD.values()) {
                AttenuationVisitor attenuationVisitor = lDENAttenuationVisitor[timePeriod.ordinal()];
                for (Attenuation.SourceReceiverAttenuation lvl : attenuationVisitor.receiverAttenuationLevels) {
                    NoiseMapParameters.TimePeriodParameters timePeriodParameters;
                    if (!levelsPerSourceLines.containsKey(lvl.sourceIndex)) {
                        timePeriodParameters = new NoiseMapParameters.TimePeriodParameters();
                        levelsPerSourceLines.put(lvl.sourceIndex, timePeriodParameters);
                    } else {
                        timePeriodParameters = levelsPerSourceLines.get(lvl.sourceIndex);
                    }
                    if (timePeriodParameters.getTimePeriodLevel(timePeriod) == null) {
                        timePeriodParameters.setTimePeriodLevel(timePeriod, lvl.value);
                    } else {
                        // same receiver, same source already exists, merge attenuation
                        timePeriodParameters.setTimePeriodLevel(timePeriod, sumDbArray(
                                timePeriodParameters.getTimePeriodLevel(timePeriod), lvl.value));
                    }
                }
            }
            long sourcePK;
            for (Map.Entry<Integer, NoiseMapParameters.TimePeriodParameters> entry : levelsPerSourceLines.entrySet()) {
                final int sourceId = entry.getKey();
                sourcePK = sourceId;
                if (noiseMapComputeRaysOut.inputData != null) {
                    // Retrieve original source identifier
                    if (entry.getKey() < noiseMapComputeRaysOut.inputData.sourcesPk.size()) {
                        sourcePK = noiseMapComputeRaysOut.inputData.sourcesPk.get((int) sourceId);
                    }
                }
                if (noiseMapParameters.computeLDay || noiseMapParameters.computeLDEN) {
                    dayLevels = sumArray(wToDba(noiseMapComputeRaysOut.noiseEmissionMaker.wjSourcesD.get((int) sourceId)), entry.getValue().dayLevels);
                    if(noiseMapParameters.computeLDay) {
                        pushInStack(noiseMapComputeRaysOut.attenuatedPaths.lDayLevels, new Attenuation.SourceReceiverAttenuation(receiverPK,receiverId, sourcePK, sourceId, dayLevels, receiverPosition));
                    }
                }
                if (noiseMapParameters.computeLEvening || noiseMapParameters.computeLDEN) {
                    eveningLevels = sumArray(wToDba(noiseMapComputeRaysOut.noiseEmissionMaker.wjSourcesE.get((int) sourceId)), entry.getValue().eveningLevels);
                    if(noiseMapParameters.computeLEvening) {
                        pushInStack(noiseMapComputeRaysOut.attenuatedPaths.lEveningLevels, new Attenuation.SourceReceiverAttenuation(receiverPK,receiverId, sourcePK, sourceId, eveningLevels, receiverPosition));
                    }
                }
                if (noiseMapParameters.computeLNight || noiseMapParameters.computeLDEN) {
                    nightLevels = sumArray(wToDba(noiseMapComputeRaysOut.noiseEmissionMaker.wjSourcesN.get((int) sourceId)), entry.getValue().nightLevels);
                    if(noiseMapParameters.computeLNight) {
                        pushInStack(noiseMapComputeRaysOut.attenuatedPaths.lNightLevels, new Attenuation.SourceReceiverAttenuation(receiverPK,receiverId, sourcePK, sourceId, nightLevels, receiverPosition));
                    }
                }
                if (noiseMapParameters.computeLDEN) {
                    double[] levels = new double[dayLevels.length];
                    for(int idFrequency = 0; idFrequency < levels.length; idFrequency++) {
                        levels[idFrequency] = (12 * dayLevels[idFrequency] +
                                4 * dbaToW(wToDba(eveningLevels[idFrequency]) + 5) +
                                8 * dbaToW(wToDba(nightLevels[idFrequency]) + 10)) / 24.0;
                    }
                    pushInStack(noiseMapComputeRaysOut.attenuatedPaths.lDenLevels, new Attenuation.SourceReceiverAttenuation(receiverPK,receiverId, sourcePK, sourceId, levels, receiverPosition));
                }
            }
        } else {
            // Merge all results
            if (noiseMapParameters.computeLDay || noiseMapParameters.computeLDEN) {
                dayLevels = processAndPushResult(receiverId ,receiverPK,
                        noiseMapComputeRaysOut.noiseEmissionMaker.wjSourcesD,
                        lDENAttenuationVisitor[0].receiverAttenuationLevels, noiseMapComputeRaysOut.attenuatedPaths.lDayLevels,
                        noiseMapParameters.computeLDay);
            }
            if (noiseMapParameters.computeLEvening || noiseMapParameters.computeLDEN) {
                eveningLevels = processAndPushResult(receiverId ,receiverPK,
                        noiseMapComputeRaysOut.noiseEmissionMaker.wjSourcesE,
                        lDENAttenuationVisitor[1].receiverAttenuationLevels, noiseMapComputeRaysOut.attenuatedPaths.lEveningLevels,
                        noiseMapParameters.computeLEvening);
            }
            if (noiseMapParameters.computeLNight || noiseMapParameters.computeLDEN) {
                nightLevels = processAndPushResult(receiverId ,receiverPK,
                        noiseMapComputeRaysOut.noiseEmissionMaker.wjSourcesN,
                        lDENAttenuationVisitor[2].receiverAttenuationLevels, noiseMapComputeRaysOut.attenuatedPaths.lNightLevels,
                        noiseMapParameters.computeLNight);
            }
            if (noiseMapParameters.computeLDEN) {
                double[] levels = new double[dayLevels.length];
                for(int idFrequency = 0; idFrequency < levels.length; idFrequency++) {
                    levels[idFrequency] = (12 * dayLevels[idFrequency] +
                            4 * dbaToW(wToDba(eveningLevels[idFrequency]) + 5) +
                            8 * dbaToW(wToDba(nightLevels[idFrequency]) + 10)) / 24.0;
                }
                pushInStack(noiseMapComputeRaysOut.attenuatedPaths.lDenLevels,
                        new Attenuation.SourceReceiverAttenuation(receiverPK, receiverId,-1, -1, wToDba(levels), receiverPosition));
            }
        }
        for (AttenuationVisitor attenuationVisitor : lDENAttenuationVisitor) {
            attenuationVisitor.receiverAttenuationLevels.clear();
        }
    }
}
