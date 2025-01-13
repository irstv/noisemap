/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc;

import org.h2.util.StringUtils;
import org.h2gis.utilities.SpatialResultSet;
import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.pathfinder.*;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;
import org.noise_planet.noisemodelling.propagation.Attenuation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.*;

public class Utils {

    public static double [] diffArray(double[] array1, double[] array2) {
        double[] difference = new double[array1.length];
        if (array1.length == array2.length) {
            for (int i = 0; i < array1.length; i++) {
                difference[i] = array1[i] - array2[i];
            }
        }else {
            throw new IllegalArgumentException("Arrays with different size");
        }
        return difference;

    }
    public static double[] addArray(double[] first, double[] second) {
        int length = Math.min(first.length, second.length);
        double[] result = new double[length];

        for (int i = 0; i < length; i++) {
            result[i] = first[i] + second[i];
        }

        return result;
    }

    public static double[] getMaxValeurAbsolue(double[] listes) {
        if (listes == null || listes.length == 0) {
            throw new IllegalArgumentException("La liste ne peut pas être vide ou nulle.");
        }
        double[] result = new double[] {0.0,0};
        double maxAbsolue = Double.MIN_VALUE;
        for (int i = 0; i < listes.length; i++) {
            double valeurAbsolue = Math.abs(listes[i]);
            if (valeurAbsolue > maxAbsolue) {
                maxAbsolue = valeurAbsolue;
                result = new double[] {maxAbsolue,i};
            }
        }
        result[0] = Math.round(result[0] * 100.0) / 100.0;
        return result;
    }

    public static String getRunScriptRes(String fileName) throws URISyntaxException {
        File resourceFile = new File(NoiseMapByReceiverMakerTest.class.getResource(fileName).toURI());
        return "RUNSCRIPT FROM "+ StringUtils.quoteStringSQL(resourceFile.getPath());
    }

    public static class JDBCPropagationData implements NoiseMapByReceiverMaker.PropagationProcessDataFactory {
        @Override
        public Scene create(ProfileBuilder builder) {
            return new DirectPathsParameters(builder);
        }

        @Override
        public void initialize(Connection connection, NoiseMapByReceiverMaker noiseMapByReceiverMaker) {

        }
    }

    public static class JDBCComputeRaysOut implements NoiseMapByReceiverMaker.IComputeRaysOutFactory {
        boolean keepRays;

        public JDBCComputeRaysOut(boolean keepRays) {
            this.keepRays = keepRays;
        }

        @Override
        public IComputePathsOut create(Scene threadData, AttenuationCnossosParameters pathDataDay,
                                       AttenuationCnossosParameters pathDataEvening,
                                       AttenuationCnossosParameters pathDataNight) {
            return new RayOut(keepRays, pathDataDay, (DirectPathsParameters)threadData);
        }
    }

    private static final class RayOut extends Attenuation {
        private DirectPathsParameters processData;

        public RayOut(boolean keepRays, AttenuationCnossosParameters pathData, DirectPathsParameters processData) {
            super(keepRays, pathData, processData);
            this.processData = processData;
        }

        @Override
        public double[] computeCnossosAttenuation(AttenuationCnossosParameters data, int sourceId, double sourceLi, List<CnossosPath> pathParameters) {
            double[] attenuation = super.computeCnossosAttenuation(data, sourceId, sourceLi, pathParameters);
            return wToDba(multArray(processData.wjSources.get(sourceId), dbaToW(attenuation)));
        }
    }

    private static class DirectPathsParameters extends Scene {
        List<double[]> wjSources = new ArrayList<>();
        private final static String[] powerColumns = new String[]{"db_m63", "db_m125", "db_m250", "db_m500", "db_m1000", "db_m2000", "db_m4000", "db_m8000"};

        public DirectPathsParameters(ProfileBuilder builder) {
            super(builder);
        }


        @Override
        public void addSource(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException, IOException {
            super.addSource(pk, geom, rs);
            double sl[] = new double[powerColumns.length];
            int i = 0;
            for(String columnName : powerColumns) {
                sl[i++] = dbaToW(rs.getDouble(columnName));
            }
            wjSources.add(sl);
        }
    }


    public static double[] aWeighting(List<Double> lvls) {
        return new double[] {lvls.get(0) - 26.2, lvls.get(1) - 16.1, lvls.get(2) - 8.6, lvls.get(3) - 3.2, lvls.get(4) ,
                lvls.get(5) + 1.2, lvls.get(6) + 1, lvls.get(7)  - 1.1};
    }
}
