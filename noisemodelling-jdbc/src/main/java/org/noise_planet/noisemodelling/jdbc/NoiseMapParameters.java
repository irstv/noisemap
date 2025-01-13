/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;


import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;
import java.io.File;

/**
 * Configuration of NoiseModelling computation based on database data using standard Lden outputs
 */
public class NoiseMapParameters {
    boolean exportAttenuationMatrix;

    public NoiseMapParameters(INPUT_MODE inputMode) {
        input_mode = inputMode;
    }

    public enum TIME_PERIOD {DAY, EVENING, NIGHT}
    public enum INPUT_MODE { INPUT_MODE_TRAFFIC_FLOW, INPUT_MODE_LW_DEN, INPUT_MODE_PROBA}
    final INPUT_MODE input_mode;
    boolean exportProfileInRays = false;
    boolean keepAbsorption = false; // in rays, keep store detailed absorption data
    int maximumRaysOutputCount = 0; // if export rays, do not keep more than this number of rays (0 infinite)
    // This field is initialised when {@link PointNoiseMap#initialize} is called
    AttenuationCnossosParameters attenuationCnossosParametersDay = null;
    AttenuationCnossosParameters attenuationCnossosParametersEvening = null;
    AttenuationCnossosParameters attenuationCnossosParametersNight = null;
    public enum ExportRaysMethods {TO_RAYS_TABLE, TO_MEMORY, NONE}
    ExportRaysMethods exportRaysMethod = ExportRaysMethods.NONE;
    // Cnossos revisions have multiple coefficients for road emission formulae
    // this parameter will be removed when the final version of Cnossos will be published
    int coefficientVersion = 2;

    // Process status
    boolean exitWhenDone = false;
    boolean aborted = false;

    // Output config
    boolean computeLDay = true;
    boolean computeLEvening = true;
    boolean computeLNight = true;
    boolean computeLDEN = true;
    public int geojsonColumnSizeLimit = 1000000; // sql column size limitation for geojson

    public int getMaximumRaysOutputCount() {
        return maximumRaysOutputCount;
    }
    int outputMaximumQueue = 50000;

    public boolean mergeSources = true;

    String lDayTable = "LDAY_RESULT";
    String lEveningTable = "LEVENING_RESULT";
    String lNightTable = "LNIGHT_RESULT";
    String lDenTable = "LDEN_RESULT";
    String raysTable = "RAYS";

    String lwFrequencyPrepend = "LW";

    File sqlOutputFile;
    Boolean sqlOutputFileCompression = true;
    Boolean dropResultsTable = true;
    public boolean computeLAEQOnly = false;

    /**
     * If true the position of the receiver (with the altitude if available) will be exported into the results tables
     */
    boolean exportReceiverPosition = false;

    /**
     * @return If true the position of the receiver (with the altitude if available) will be exported into the results
     * tables
     */
    public boolean isExportReceiverPosition() {
        return exportReceiverPosition;
    }

    /**
     * @param exportReceiverPosition If true the position of the receiver (with the altitude if available) will be
     *                               exported into the results tables
     */
    public void setExportReceiverPosition(boolean exportReceiverPosition) {
        this.exportReceiverPosition = exportReceiverPosition;
    }

    /**
     * @param maximumRaysOutputCount if export rays, do not keep more than this number of rays per computation area (0 infinite)
     */
    public void setMaximumRaysOutputCount(int maximumRaysOutputCount) {
        this.maximumRaysOutputCount = maximumRaysOutputCount;
    }

    public boolean isComputeLAEQOnly() {
        return computeLAEQOnly;
    }

    public void setComputeLAEQOnly(boolean computeLAEQOnly) {
        this.computeLAEQOnly = computeLAEQOnly;
    }

    /**
     * Retrieves the propagation process path data for the specified time period.
     * @param time_period the time period for which to retrieve the propagation process path data.
     * @return the attenuation Cnossos parameters for the specified time period.
     */
    public AttenuationCnossosParameters getPropagationProcessPathData(TIME_PERIOD time_period) {
        switch (time_period) {
            case DAY:
                return attenuationCnossosParametersDay;
            case EVENING:
                return attenuationCnossosParametersEvening;
            default:
                return attenuationCnossosParametersNight;
        }
    }

    /**
     * Sets the propagation process path data for the specified time period.
     * @param time_period the time period for which to set the propagation process path data.
     * @param attenuationCnossosParameters the attenuation Cnossos parameters to set.
     */
    public void setPropagationProcessPathData(TIME_PERIOD time_period, AttenuationCnossosParameters attenuationCnossosParameters) {
        switch (time_period) {
            case DAY:
                attenuationCnossosParametersDay = attenuationCnossosParameters;
            case EVENING:
                attenuationCnossosParametersEvening = attenuationCnossosParameters;
            default:
                attenuationCnossosParametersNight = attenuationCnossosParameters;
        }
    }

    public void setComputeLDay(boolean computeLDay) {
        this.computeLDay = computeLDay;
    }

    public void setComputeLEvening(boolean computeLEvening) {
        this.computeLEvening = computeLEvening;
    }

    public void setComputeLNight(boolean computeLNight) {
        this.computeLNight = computeLNight;
    }

    public ExportRaysMethods getExportRaysMethod() {
        return exportRaysMethod;
    }

    /**
     * Export rays in table (beware this could take a lot of storage space) or keep on memory or do not keep
     * @param exportRaysMethod
     */
    public void setExportRaysMethod(ExportRaysMethods exportRaysMethod) {
        this.exportRaysMethod = exportRaysMethod;
    }


    public void setExportProfileInRays(boolean exportProfileInRays) {
        this.exportProfileInRays = exportProfileInRays;
    }

    public boolean isKeepAbsorption() {
        return keepAbsorption;
    }

    /**
     * @param exportAttenuationMatrix If true store absorption values in propagation path objects
     * @see #setExportAttenuationMatrix(boolean)
     */
    public void setExportAttenuationMatrix(boolean exportAttenuationMatrix) {
        this.exportAttenuationMatrix = exportAttenuationMatrix;
    }

    /**
     * @param coefficientVersion Cnossos revisions have multiple coefficients for road emission formulae this parameter
     *                          will be removed when the final version of Cnossos will be published
     */
    public void setCoefficientVersion(int coefficientVersion) {
        this.coefficientVersion = coefficientVersion;
    }

    public int getCoefficientVersion() {
        return coefficientVersion;
    }

    /**
     * Maximum result stack to be inserted in database
     * if the stack is full, the computation core is waiting
     * @param outputMaximumQueue Maximum number of elements in stack
    */
    public void setOutputMaximumQueue(int outputMaximumQueue) {
        this.outputMaximumQueue = outputMaximumQueue;
    }

    /**
     * @param computeLDEN IF true create LDEN_GEOM table
     */
    public void setComputeLDEN(boolean computeLDEN) {
        this.computeLDEN = computeLDEN;
    }

    public void setMergeSources(boolean mergeSources) {
        this.mergeSources = mergeSources;
    }

    public void setlDayTable(String lDayTable) {
        this.lDayTable = lDayTable;
    }

    public void setlEveningTable(String lEveningTable) {
        this.lEveningTable = lEveningTable;
    }

    /**
     */
    public void setlNightTable(String lNightTable) {
        this.lNightTable = lNightTable;
    }

    public void setlDenTable(String lDenTable) {
        this.lDenTable = lDenTable;
    }

    public String getlDayTable() {
        return lDayTable;
    }

    public String getlEveningTable() {
        return lEveningTable;
    }

    public String getlNightTable() {
        return lNightTable;
    }

    public String getlDenTable() {
        return lDenTable;
    }

    /**
     * @return Table name that contains rays dump (profile)
     */
    public String getRaysTable() {
        return raysTable;
    }

    /**
     */
    public void setRaysTable(String raysTable) {
        this.raysTable = raysTable;
    }

    public boolean isComputeLDay() {
        return computeLDay;
    }

    public boolean isComputeLEvening() {
        return computeLEvening;
    }

    public boolean isComputeLNight() {
        return computeLNight;
    }

    public boolean isComputeLDEN() {
        return computeLDEN;
    }

    public boolean isMergeSources() {
        return mergeSources;
    }

    /**
     * representing the noise levels for different time periods.
     */
    public static class TimePeriodParameters {
        public double [] dayLevels = null;
        public double [] eveningLevels = null;
        public double [] nightLevels = null;

        /**
         * Gets the noise levels for the specified time period.
         * @param timePeriod The time period for which to retrieve the noise levels.
         * @return The noise levels for the specified time period.
         */
        public double[] getTimePeriodLevel(TIME_PERIOD timePeriod) {
            switch (timePeriod) {
                case DAY:
                    return dayLevels;
                case EVENING:
                    return eveningLevels;
                default:
                    return nightLevels;
            }
        }
        /**
         * Sets the noise levels for the specified time period.
         * @param timePeriod The time period for which to set the noise levels.
         * @param levels The noise levels to set.
         */
        public void setTimePeriodLevel(TIME_PERIOD timePeriod, double [] levels) {
            switch (timePeriod) {
                case DAY:
                    dayLevels = levels;
                case EVENING:
                    eveningLevels = levels;
                default:
                    nightLevels = levels;
            }
        }
    }
}
