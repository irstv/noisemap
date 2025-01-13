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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class RootProgressVisitor extends DefaultProgressVisitor {
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    private boolean canceled = false;
    private boolean logProgression = false;
    private Logger logger = LoggerFactory.getLogger(RootProgressVisitor.class);
    private String lastLoggedProgression = "";
    private double minimumSecondsBetweenPrint = 1.0;
    private long lastPrint = 0;

    /*public RootProgressVisitor(long subprocessSize) {
        super(subprocessSize, null);
    }*/

    /**
     * Create the RootProgressVisitor constructor
     * @param subprocessSize
     * @param logProgression
     * @param minimumSecondsBetweenPrint
     */

    public RootProgressVisitor(long subprocessSize, boolean logProgression, double minimumSecondsBetweenPrint) {
        super(subprocessSize, null);
        this.logProgression = logProgression;
        this.minimumSecondsBetweenPrint = minimumSecondsBetweenPrint;
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    @Override
    public void addPropertyChangeListener(String property, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(property, listener);
    }

    /**
     *
     * @param incProg
     */
    @Override
    protected synchronized void pushProgression(double incProg) {
        double oldProgress = getProgression();
        super.pushProgression(incProg);
        double newProgress = getProgression();
        propertyChangeSupport.firePropertyChange("PROGRESS", oldProgress, newProgress);
        if(logProgression) {
            String newLogProgress = String.format("%.2f %%", newProgress * 100);
            if(!newLogProgress.equals(lastLoggedProgression)) {
                lastLoggedProgression = newLogProgress;
                long t = System.currentTimeMillis();
                if((t - lastPrint) / 1000.0 > minimumSecondsBetweenPrint) {
                    logger.info(newLogProgress);
                    lastPrint = t;
                }
            }
        }
    }

    /**
     * check if the property is canceled
     * @return a boolen
     */
    @Override
    public boolean isCanceled() {
        return canceled;
    }

    /**
     * Allow to cancel the property of ProgressVisitor
     */
    @Override
    public void cancel() {
        canceled = true;
        propertyChangeSupport.firePropertyChange(ProgressVisitor.PROPERTY_CANCELED, false, true);
    }
}
