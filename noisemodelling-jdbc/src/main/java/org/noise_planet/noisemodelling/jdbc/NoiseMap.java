/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.noise_planet.noisemodelling.pathfinder.IComputePathsOut;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;
import org.noise_planet.noisemodelling.propagation.Attenuation;

public class NoiseMap extends Attenuation {
    AttenuatedPaths attenuatedPaths;
    NoiseEmissionMaker noiseEmissionMaker;
    public AttenuationCnossosParameters dayPathData;
    public AttenuationCnossosParameters eveningPathData;
    public AttenuationCnossosParameters nightPathData;
    public NoiseMapParameters noiseMapParameters;

    /**
     * Create NoiseMap constructor
     * @param dayPathData
     * @param eveningPathData
     * @param nightPathData
     * @param inputData
     * @param attenuatedPaths
     * @param noiseMapParameters
     */
    public NoiseMap(AttenuationCnossosParameters dayPathData, AttenuationCnossosParameters eveningPathData,
                    AttenuationCnossosParameters nightPathData, NoiseEmissionMaker inputData,
                    AttenuatedPaths attenuatedPaths, NoiseMapParameters noiseMapParameters) {
        super(inputData.noiseMapParameters.exportRaysMethod != NoiseMapParameters.ExportRaysMethods.NONE, null, inputData);
        this.exportAttenuationMatrix = inputData.noiseMapParameters.exportAttenuationMatrix;
        this.attenuatedPaths = attenuatedPaths;
        this.noiseEmissionMaker = inputData;
        this.dayPathData = dayPathData;
        this.eveningPathData = eveningPathData;
        this.nightPathData = nightPathData;
        this.noiseMapParameters = noiseMapParameters;
    }

    /**
     *
     * @return an instance of the interface IComputePathsOut
     */
    @Override
    public IComputePathsOut subProcess() {
        return new NoiseMapInStack(this);
    }





}
