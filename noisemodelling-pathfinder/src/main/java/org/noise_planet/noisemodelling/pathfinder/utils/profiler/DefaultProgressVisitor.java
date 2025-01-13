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

import java.beans.PropertyChangeListener;

public class DefaultProgressVisitor implements ProgressVisitor {
    protected long subprocessSize;
    protected double subprocessDone = 0;
    DefaultProgressVisitor parentProcess;

    public DefaultProgressVisitor(long subprocessSize, DefaultProgressVisitor parentProcess) {
        this.subprocessSize = subprocessSize;
        this.parentProcess = parentProcess;
    }

    /**
     *
     * @return an instance of the interface ProgressVisitor
     */
    @Override
    public ProgressVisitor subProcess(int i) {
        return new DefaultProgressVisitor(i, this);
    }

    @Override
    public void endStep() {
        pushProgression(1.0);
    }

    protected synchronized void pushProgression(double incProg) {
        if (subprocessDone + incProg <= subprocessSize) {
            subprocessDone += incProg;
            if (parentProcess != null) {
                parentProcess.pushProgression((incProg / subprocessSize));
            }
        }
    }

    @Override
    public void setStep(int i) {
    }

    @Override
    public int getStepCount() {
        return (int)subprocessSize;
    }

    @Override
    public void endOfProgress() {
    }

    @Override
    public double getProgression() {
        if(parentProcess != null) {
            return parentProcess.getProgression();
        } else {
            return subprocessDone / subprocessSize;
        }
    }

    /**
     * check if the process is cancel or not
     * @return a boolean
     */
    @Override
    public boolean isCanceled() {
        return parentProcess != null && parentProcess.isCanceled();
    }

    /**
     * allow to cancel a process
     */
    @Override
    public void cancel() {
        if(parentProcess != null) {
            parentProcess.cancel();
        }
    }

    @Override
    public void addPropertyChangeListener(String s, PropertyChangeListener propertyChangeListener) {
        if(parentProcess != null) {
            parentProcess.addPropertyChangeListener(s, propertyChangeListener);
        }
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        if(parentProcess != null) {
            parentProcess.removePropertyChangeListener(propertyChangeListener);
        }
    }
}
