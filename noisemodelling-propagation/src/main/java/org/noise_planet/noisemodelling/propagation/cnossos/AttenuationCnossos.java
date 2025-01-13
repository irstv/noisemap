/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.*;
import static org.noise_planet.noisemodelling.propagation.cnossos.PointPath.POINT_TYPE.*;


/**
 * Return the dB value corresponding to the parameters
 * Following Directive 2015/996/EN
 * https://circabc.europa.eu/sd/a/9566c5b9-8607-4118-8427-906dab7632e2/Directive_2015_996_EN.pdf
 * @author Pierre Aumond
 */

public class AttenuationCnossos {
    private static double[] freq_lambda;
    private static double[] aGlobal;

    public static double[] getaGlobal() {
        return aGlobal;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AttenuationCnossos.class);

    /**
     * Eq 2.5.21: calculate the value of DeltaDif
     * @param srpath
     * @param data
     * @return double list with the value of DeltaDif
     */
    public static double[] getDeltaDif(SegmentPath srpath, AttenuationCnossosParameters data) {
        double[] DeltaDif = new double[data.freq_lvl.size()];
        double cprime;

        for (int idfreq = 0; idfreq < data.freq_lvl.size(); idfreq++) {
            double Ch = 1; // Eq 2.5.21
            if (srpath.eLength > 0.3) {
                double gammaPart = pow((5 * freq_lambda[idfreq]) / srpath.eLength, 2);
                cprime = (1. + gammaPart) / (1./3. + gammaPart); // Eq. 2.5.23
            } else {
                cprime = 1.;
            }

            //(7.11) NMP2008 P.32
            double testForm = 40 / freq_lambda[idfreq]* cprime * srpath.getDelta();

            double deltaDif = 0.;
            if (testForm >= -2.) {
                deltaDif = 10 * Ch * log10(Math.max(0, 3 + testForm));
            }
            DeltaDif[idfreq] = Math.max(0, deltaDif);
        }
        return DeltaDif;
    }

    /**
     * Compute attenuation of sound energy by distance. Minimum distance is one
     * meter.
     * Eq. 2.5.12
     * @param distance Distance in meter
     * @return Attenuated sound level. Take only account of geometric dispersion
     * of sound wave.
     */
    private static double getADiv(double distance) {
        return 20*log10(distance)+11;
    }

    /**
     * Compute the attenuation of atmospheric absorption
     * @param dist       Propagation distance
     * @param alpha_atmo Atmospheric alpha (dB/km)
     * @return
     */
    private static double getAAtm(double dist, double alpha_atmo) {
        return alpha_atmo * dist / 1000.;
    }


    /**
     * Eq. 2.5.15:
     * Compute Aground
     * @param pathParameters
     * @param segmentPath
     * @param data
     * @return list double with the values of AGround
     */
    public static double[] getAGroundCore(CnossosPath pathParameters, SegmentPath segmentPath, AttenuationCnossosParameters data) {

        double[] aGround = new double[data.freq_lvl.size()];
        double aGroundMin;
        double AGround;

        for(int idfreq = 0; idfreq < data.freq_lvl.size(); idfreq++) {
            int fm = data.freq_lvl.get(idfreq);
            double gw = segmentPath.gw;
            double dp = segmentPath.dp;

            //NF S 31-133 page 41 c
            double k = 2 * Math.PI * fm / data.getCelerity();
            //NF S 31-113 page 41 w
            //eq 2.5.17
            double w = 0.0185 * pow(fm, 2.5) * pow(gw, 2.6) /
                    (pow(fm, 1.5) * pow(gw, 2.6) + 1.3e3 * pow(fm, 0.75) * pow(gw, 1.3) + 1.16e6);
            //NF S 31-113 page 41 Cf
            //eq 2.5.16
            double cf = dp * (1 + 3 * w * dp * pow(Math.E, -sqrt(w * dp))) / (1 + w * dp);
            //NF S 31-113 page 41 A sol

            if (pathParameters.isFavorable()) {
                if (data.isPrime2520()) {
                    if (segmentPath.testFormF <= 1) {
                        aGroundMin = -3 * (1 - segmentPath.gm);
                    } else {
                        aGroundMin = -3 * (1 - segmentPath.gm) * (1 + 2 * (1 - (1 / segmentPath.testFormF)));
                    }
                } else {
                    if (segmentPath.testFormH <= 1) {
                        aGroundMin = -3 * (1 - segmentPath.gm);
                    } else {
                        aGroundMin = -3 * (1 - segmentPath.gm) * (1 + 2 * (1 - (1 / segmentPath.testFormH)));
                    }
                }
                /* eq. 2.5.20 */
                AGround = -10 * log10(4 * pow(k, 2) / pow(segmentPath.dp, 2) *
                        (pow(segmentPath.zsF, 2) - sqrt(2 * cf / k) * segmentPath.zsF + cf / k) *
                        (pow(segmentPath.zrF, 2) - sqrt(2 * cf / k) * segmentPath.zrF + cf / k));
            } else {
                /* eq. 2.5.15 */
                AGround = -10 * log10(4 * pow(k, 2)/ pow(segmentPath.dp, 2) *
                        (pow(segmentPath.zsH, 2) - sqrt(2 * cf / k) * segmentPath.zsH + cf / k) *
                        (pow(segmentPath.zrH, 2) - sqrt(2 * cf / k) * segmentPath.zrH + cf / k));
                /* eq. 2.5.18 */
                aGroundMin = -3 * (1 - segmentPath.gm);
            }
            aGround[idfreq] = Math.max(AGround, aGroundMin);

            //For testing purpose
            if(pathParameters.keepAbsorption) {
                if(pathParameters.isFavorable()) {
                    pathParameters.groundAttenuation.wF[idfreq] += w;
                    pathParameters.groundAttenuation.cfF[idfreq] = cf;
                    pathParameters.groundAttenuation.aGroundF[idfreq] = aGround[idfreq];
                }
                else{
                    pathParameters.groundAttenuation.wH[idfreq] += w;
                    pathParameters.groundAttenuation.cfH[idfreq] = cf;
                    pathParameters.groundAttenuation.aGroundH[idfreq] = aGround[idfreq];
                }
            }

        }
        return aGround;
    }

    /**
     * Formulae Eq. 2.5.31 - Eq. 2.5.32
     * @param aGround        Asol(O,R) or Asol(S,O) (sol mean ground)
     * @param deltaDifPrim Δdif(S,R') if Asol(S,O) is given or Δdif(S', R) if Asol(O,R)
     * @param deltaDif     Δdif(S, R)
     * @return Δsol(S, O) if Asol(S,O) is given or Δsol(O,R) if Asol(O,R) is given
     */
    private static double getDeltaGround(double aGround, double deltaDifPrim, double deltaDif) {
        double attArg = 1 + (pow(10, -aGround / 20) - 1) * pow(10, -(deltaDifPrim - deltaDif) / 20);
        if(Double.isNaN(attArg)){
            attArg = Double.MAX_VALUE;
        }
        else if (attArg < 0) {
            attArg = 0;
        }
        return -20 * log10(attArg);
    }

    /**
     * Compute ARef
     * @param pathParameters
     * @param data
     * @return list double with the values of ARef
     */
    private static double[] getARef(CnossosPath pathParameters, AttenuationCnossosParameters data) {
        double[] aRef = new double[data.freq_lvl.size()];
        Arrays.fill(aRef, 0.0);
        for (PointPath pointPath : pathParameters.getPointList()) {
            if(pointPath.type.equals(REFL)) {
                for (int idf = 0; idf < data.freq_lvl.size(); idf++) {
                    List<Double> alpha = pointPath.alphaWall;
                    if (alpha != null && !alpha.isEmpty()) {
                        aRef[idf] += 10 * log10(1 - alpha.get(idf));
                    }
                }
            }
        }
        return aRef;
    }

    /**
     * Compute AGround
     * @param segmentPath
     * @param pathParameters
     * @param data
     * @return list double with the values of AGround
     */
    private static double[] aGround(SegmentPath segmentPath, CnossosPath pathParameters, AttenuationCnossosParameters data) {
        // Here there is a debate if use the condition isgDisc or not
        // In Directive 2015-2019, isgDisc == true because the term – 3(1 – Gm) takes into account the fact that when the source and the receiver are far apart, the first reflection source side is no longer on the platform but on natural land.
        if (!(segmentPath.gPath == 0 && data.isgDisc())) {
            return getAGroundCore(pathParameters, segmentPath, data);
        } else {
            double aGroundMin;
            //For testing purpose
            if(pathParameters.keepAbsorption) {
                //Used to calculate value ignored like Cf
                getAGroundCore(pathParameters, segmentPath, data);
            }

            if (pathParameters.isFavorable()) {
                // The lower bound of Aground,F (calculated with unmodified heights) depends on the geometry of the path
                if (segmentPath.testFormF <= 1) {
                    aGroundMin = -3 * (1 - segmentPath.gm);
                } else {
                    aGroundMin = -3 * (1 - segmentPath.gm) * (1 + 2 * (1 - (1 / segmentPath.testFormF)));
                }
            } else {
                aGroundMin = -3;
            }

            double[] aGround = new double[data.freq_lvl.size()];
            Arrays.fill(aGround, aGroundMin);

            //For testing purpose
            if(pathParameters.keepAbsorption) {
                if(pathParameters.isFavorable()) {
                    pathParameters.groundAttenuation.aGroundF = aGround;
                }
                else{
                    pathParameters.groundAttenuation.aGroundH = aGround;
                }
            }
            return aGround;
        }
    }

    /**
     * Compute ABoundary
     *
     * @param pathParameters
     * @param data
     * @return list double with the values of ABoundary
     */
    private static double[] getABoundary(CnossosPath pathParameters, AttenuationCnossosParameters data) {

        SegmentPath srPath = pathParameters.getSRSegment();
        List<SegmentPath> segments = pathParameters.getSegmentList();

        double[] aGround;
        double[] aDif = new double[data.freq_lvl.size()];

        double[] aBoundary;

        // Set Gm and Gw for AGround SR - Table 2.5.b
        if (pathParameters.isFavorable()) {
            srPath.setGw(srPath.gPath);
        } else {
            srPath.setGw(srPath.gPathPrime);
        }
        srPath.setGm(srPath.gPathPrime);

        List<Integer> difBands = new ArrayList<>();
        List<Integer> noDifBands = new ArrayList<>();
        double deltaD = srPath.d - (segments.get(0).d + segments.get(1).dp);
        double deltaDPrime = -srPath.dPrime + segments.get(0).dPrime + segments.get(1).dPrime;
        for (int freq : data.freq_lvl) {
            double lambda = 340.0 / freq;
            if (deltaD > -lambda / 20) {
                if (deltaD > (lambda / 4 - deltaDPrime)) {
                    difBands.add(data.freq_lvl.indexOf(freq));
                } else {
                    noDifBands.add(data.freq_lvl.indexOf(freq));
                }
            }
        }

        // Adif is calculated with diffraction. The ground effect is taken into account in the Adif equation itself (Aground = 0 dB). This therefore gives Aboundary = Adif
        List<SegmentPath> segmentPath = pathParameters.getSegmentList();

        double[] deltaDifSR; // is the attenuation due to the diffraction between the source S and the receiver R
        double[] DeltaDifSpR;
        double[] deltaDifSRp;
        double[] aGroundSO; // is the attenuation due to the ground effect on the source side, weighted by the diffraction on the source side; where it is understood that O = O1 in case of multiple diffractions as in Figure 2.5.f
        double[] aGroundOR; // is the attenuation due to the ground effect on the receiver side, weighted by the diffraction on the receiver side.

        deltaDifSR = getDeltaDif(srPath, data);
        DeltaDifSpR = getDeltaDif(segments.get(segments.size() - 2), data);
        deltaDifSRp = getDeltaDif(segments.get(segments.size() - 1), data);

        // Set Gm and Gw for AGround SO - Table 2.5.b
        if (pathParameters.isFavorable()) {
            segmentPath.get(0).setGw(segmentPath.get(0).gPath);
        } else {
            segmentPath.get(0).setGw(segmentPath.get(0).gPathPrime);
        }
        segmentPath.get(0).setGm(segmentPath.get(0).gPathPrime);
        aGroundSO = aGround(segmentPath.get(0), pathParameters, data);

        // Set Gm and Gw for AGround OR - Table 2.5.b
        segmentPath.get(segmentPath.size() - 1).setGw(segmentPath.get(segmentPath.size() - 1).gPath);
        segmentPath.get(segmentPath.size() - 1).setGm(segmentPath.get(segmentPath.size() - 1).gPath);
        aGroundOR = aGround(segmentPath.get(segmentPath.size() - 1), pathParameters, data);

        double[] deltaGroundSO = new double[data.freq_lvl.size()];
        double[] deltaGroundOR = new double[data.freq_lvl.size()];
        // Eq 2.5.30 - Eq. 2.5.31 - Eq. 2.5.32
        for (int idf : difBands) {
            // if Deltadif > 25: Deltadif = 25 dB for a diffraction on a horizontal edge and only on the term Deltadif which figures in the calculation of Adif. This upper bound shall not be applied in the Deltadif terms that intervene in the calculation of Deltaground, or for a diffraction on a vertical edge (lateral diffraction) in the case of industrial noise mapping
            if (segmentPath.get(segmentPath.size() - 1).zrH > 0.0000001) {// see 5.3 Equivalent heights from AFNOR document
                deltaGroundSO[idf] = getDeltaGround(aGroundSO[idf], DeltaDifSpR[idf], deltaDifSR[idf]);
                deltaGroundOR[idf] = getDeltaGround(aGroundOR[idf], deltaDifSRp[idf], deltaDifSR[idf]);
            } else {
                deltaGroundSO[idf] = getDeltaGround(aGroundSO[idf], DeltaDifSpR[idf], deltaDifSR[idf]);
                deltaGroundOR[idf] = aGroundOR[idf];
            }
            aDif[idf] = Math.min(25, deltaDifSR[idf]) + deltaGroundSO[idf] + deltaGroundOR[idf]; // Eq. 2.5.30
        }

        // Aground is calculated with no diffraction (Adif = 0 dB) and Aboundary = Aground;
        // In addition, Aatm and Aground shall be calculated from the total length of the propagation path.
        aGround = aGround(srPath, pathParameters, data);
        aBoundary = aGround;

        long difVPointCount = pathParameters.getPointList().stream().
                filter(pointPath -> pointPath.type.equals(DIFV)).count();
        if (difVPointCount > 0) {
            aDif = getDeltaDif(srPath, data);
            // Eq. 2.5.33 - Eq. 2.5.34
            for (int idf : noDifBands) {
                aBoundary[idf] = aDif[idf] + aGround[idf];
            }
        }
        return aBoundary;
    }

    /**
     * Initialize the instance of AttenuationCnossos
     * @param data
     */
    public static void init(AttenuationCnossosParameters data) {
        // init
        aGlobal = new double[data.freq_lvl.size()];

        // Init wave length for each frequency
        freq_lambda = new double[data.freq_lvl.size()];
        for (int idf = 0; idf < data.freq_lvl.size(); idf++) {
            if (data.freq_lvl.get(idf) > 0) {
                freq_lambda[idf] = data.getCelerity() / data.freq_lvl.get(idf);
            } else {
                freq_lambda[idf] = 1;
            }
        }
    }

    /**
     * Compute ADiv the attenuation
     * @param pathParameters
     * @param data
     * @return list double with the values of ADiv
     */
    public static double[] aDiv(CnossosPath pathParameters, AttenuationCnossosParameters data) {
        double[] aDiv = new double[data.freq_lvl.size()];
        long difVPointCount = pathParameters.getPointList().stream().
                filter(pointPath -> pointPath.type.equals(DIFV)).count();
        Arrays.fill(aDiv, getADiv(difVPointCount == 0 ? pathParameters.getSRSegment().d : pathParameters.getSRSegment().dc));
        return aDiv;
    }

    /**
     * Compute AAtm
     * @param data
     * @param distance
     * @return list double with the values of AAtm
     */
    public static double[] aAtm(AttenuationCnossosParameters data, double distance) {
        // init
        double[] aAtm = new double[data.freq_lvl.size()];
        // init atmosphere
        double[] alpha_atmo = data.getAlpha_atmo();
        for (int idfreq = 0; idfreq < data.freq_lvl.size(); idfreq++) {
            aAtm[idfreq] = getAAtm(distance, alpha_atmo[idfreq]);
        }
        return aAtm;
    }

    /**
     *
     * @param pathParameters
     * @param data
     * @return
     */
    public static double[] evaluateAref(CnossosPath pathParameters, AttenuationCnossosParameters data) {
        return getARef(pathParameters, data);
    }

    /**
     * Only for propagation Path Cnossos
     * // todo erase evaluate
     * @param pathParameters
     * @param data
     * @return
     */
    public static double[] evaluate(CnossosPath pathParameters, AttenuationCnossosParameters data) {
        // init
        aGlobal = new double[data.freq_lvl.size()];
        double[] aBoundary;
        double[] aRef;

        // Init wave length for each frequency
        freq_lambda = new double[data.freq_lvl.size()];
        for (int idf = 0; idf < data.freq_lvl.size(); idf++) {
            if (data.freq_lvl.get(idf) > 0) {
                freq_lambda[idf] = data.getCelerity() / data.freq_lvl.get(idf);
            } else {
                freq_lambda[idf] = 1;
            }
        }

        // init atmosphere
        double[] alpha_atmo = data.getAlpha_atmo();

        double aDiv;
        // divergence
        long refPointCount = pathParameters.getPointList().stream().
                filter(pointPath -> pointPath.type.equals(REFL)).count();
        if (refPointCount > 0) {
            aDiv = getADiv(pathParameters.getSRSegment().dPath);
        } else {
            aDiv = getADiv(pathParameters.getSRSegment().d);
        }


        // boundary (ground + diffration)
        aBoundary = getABoundary(pathParameters, data);

        // reflections
        aRef = getARef(pathParameters, data);

        for (int idfreq = 0; idfreq < data.freq_lvl.size(); idfreq++) {
            // atm
            double aAtm;

            long verticalPivotPointCount = pathParameters.getPointList().stream().
                    filter(pointPath -> pointPath.type.equals(REFL) || pointPath.type.equals(DIFV)).count();
            if (verticalPivotPointCount > 0) {
                aAtm = getAAtm(pathParameters.getSRSegment().dPath, alpha_atmo[idfreq]);
            } else {
                aAtm = getAAtm(pathParameters.getSRSegment().d, alpha_atmo[idfreq]);
            }

            aGlobal[idfreq] = -(aDiv + aAtm + aBoundary[idfreq] + aRef[idfreq]);

        }
        return aGlobal;
    }

    /**
     *
     * @param pp
     * @param freq
     * @param favorable
     * @return
     */
    private static boolean isValidRcrit(CnossosPath pp, int freq, boolean favorable) {
        double lambda = 340.0/freq;
        return favorable ?
                pp.deltaF > -lambda / 20 && pp.deltaF > lambda / 4 - pp.deltaPrimeF || pp.deltaF > 0 :
                pp.deltaH > -lambda / 20 && pp.deltaH > lambda / 4 - pp.deltaPrimeH || pp.deltaH > 0 ;
    }

    /**
     * Compute ABoundary
     * @param path
     * @param data
     * @return
     */
    public static double[] aBoundary(CnossosPath path, AttenuationCnossosParameters data) {
        double[] aGround = new double[data.freq_lvl.size()];
        double[] aDif = new double[data.freq_lvl.size()];
        List<PointPath> diffPts = path.getPointList().stream().
                filter(pointPath -> pointPath.type.equals(DIFH_RCRIT) || pointPath.type.equals(DIFH)
                        || pointPath.type.equals(DIFV)).collect(Collectors.toList());
        path.aBoundaryH.init(data.freq_lvl.size());
        path.aBoundaryF.init(data.freq_lvl.size());
        // Without diff
        for(int i=0; i<data.freq_lvl.size(); i++) {
            int finalI = i;
            boolean isValidRCriterion = isValidRcrit(path, data.freq_lvl.get(finalI), path.isFavorable());
            PointPath first = diffPts.stream()
                    .filter(pp -> pp.type.equals(PointPath.POINT_TYPE.DIFH) || pp.type.equals(DIFV) ||
                            (pp.type.equals(DIFH_RCRIT) && isValidRCriterion ))
                    .findFirst()
                    .orElse(null);
            aGround[i] = path.isFavorable() ?
                    aGroundF(path, path.getSRSegment(), data, i) :
                    aGroundH(path, path.getSRSegment(), data, i);
            if(path.groundAttenuation != null && path.groundAttenuation.aGroundF != null) {
                if (path.isFavorable()) {
                    path.groundAttenuation.aGroundF[i] = aGround[i];
                } else {
                    path.groundAttenuation.aGroundH[i] = aGround[i];
                }
            }
            if (first != null) {
                aDif[i] = aDif(path, data, i, first.type);
                if(!first.type.equals(DIFV) && isValidRCriterion) {
                    aGround[i] = 0.;
                }
            } else {
                aDif[i] = 0.;
            }

        }
        if(path.keepAbsorption) {
            if (path.isFavorable()) {
                path.aDifF = aDif;
            } else {
                path.aDifH = aDif;
            }
        }
        double[] aBoundary = new double[data.freq_lvl.size()];
        for(int i=0; i<data.freq_lvl.size(); i++) {
            aBoundary[i] = aGround[i] + aDif[i];
        }
        return aBoundary;
    }


    /**
     * Compute deltaRetrodif
     * Figure 2.5.36
     * @param reflect
     * @param data
     * @return list double with the values of deltaRetrodif
     */
    public static double[] deltaRetrodif(CnossosPath reflect, AttenuationCnossosParameters data) {
        double[] retroDiff = new double[data.freq_lvl.size()];
        Arrays.fill(retroDiff, 0.);
        final Coordinate originalS = reflect.getSRSegment().s;
        final Coordinate originalR = reflect.getSRSegment().r;
        final double SR = originalS.distance(originalR);
        Coordinate s = originalS;
        Coordinate r = originalR;
        List<PointPath> pointList = reflect.getPointList();
        for (int idPoint = 0; idPoint < pointList.size(); idPoint++) {
            PointPath pointPath = pointList.get(idPoint);
            if (pointPath.type.equals(DIFH)) {
                // update the diffraction edge on the source side
                s = pointPath.coordinate;
            } else if (pointPath.type.equals(REFL)) {
                // Look for the next DIFH of the receiver
                for(int idPointNext=0; idPointNext<pointList.size(); idPointNext++) {
                    PointPath pointPathNext = pointList.get(idPoint);
                    if (pointPathNext.type.equals(DIFH)) {
                        r = pointPathNext.coordinate;
                        break;
                    }
                }
                //Get the point on the top of the obstacle
                Coordinate p = new Coordinate(pointPath.coordinate.x, pointPath.obstacleZ);
                double ch = 1.;
                if (reflect.isFavorable()) {
                    double SP = s.distance(p);
                    double PR = p.distance(r);
                    double gamma = 2 * max(1000, 8 * SR);
                    double e = reflect.e;
                    double SpO = gamma * asin(SP / gamma);
                    double OpR = gamma * asin(PR / gamma);
                    double SpR = gamma * asin(s.distance(r) / gamma);
                    double deltaPrime = -(SpO + OpR - SpR);
                    if (e < 0.3) {
                        for (int i = 0; i < data.freq_lvl.size(); i++) {
                            double lambda = 340.0 / data.freq_lvl.get(i);
                            double testForm = 40.0 / lambda * deltaPrime;
                            double dLRetro = testForm >= -2 ? 10 * ch * log10(3 + testForm) : 0; // 2.5.37
                            retroDiff[i] = dLRetro;
                        }
                    } else {
                        for (int i = 0; i < data.freq_lvl.size(); i++) {
                            double lambda = 340.0 / data.freq_lvl.get(i);
                            double Csecond = 1 + (5 * lambda / e * 5 * lambda / e) / 1 / 3 + (5 * lambda / e * 5 * lambda / e);
                            double testForm = 40.0 / lambda * Csecond * deltaPrime;
                            double dLRetro = testForm >= -2 ? 10 * ch * log10(3 + testForm) : 0; // 2.5.37
                            retroDiff[i] = dLRetro;
                        }

                    }
                } else {
                    //2.5.36 altered with ISO/TR 17534-4:2020-11 Chapter  5.15
                    double deltaPrime = s.distance(r) - s.distance(p) - p.distance(r);
                    for (int i = 0; i < data.freq_lvl.size(); i++) {
                        double lambda = 340.0 / data.freq_lvl.get(i);
                        double testForm = 40.0 / lambda * deltaPrime;
                        double dLRetro = testForm >= -2 ? 10 * ch * log10(3 + testForm) : 0; // 2.5.37
                        retroDiff[i] = dLRetro;
                    }
                }
            }
        }
        return retroDiff;
    }

    /**
     * Compute ADif
     * @param proPathParameters
     * @param data
     * @param i
     * @param type
     * @return the value of ADiv
     */
    private static double aDif(CnossosPath proPathParameters, AttenuationCnossosParameters data, int i, PointPath.POINT_TYPE type) {
        SegmentPath first = proPathParameters.getSegmentList().get(0);
        SegmentPath last = proPathParameters.getSegmentList().get(proPathParameters.getSegmentList().size()-1);

        double ch = 1.;
        double lambda = 340.0 / data.freq_lvl.get(i);
        long difHCount = proPathParameters.getPointList().stream().filter(pointPath -> pointPath.type.equals(DIFH)).count();
        long difVCount = proPathParameters.getPointList().stream().filter(pointPath -> pointPath.type.equals(DIFV)).count();
        double cSecond = (type.equals(PointPath.POINT_TYPE.DIFH) && difHCount <= 1) || (type.equals(DIFV) && difVCount <= 1) || proPathParameters.e <= 0.3 ? 1. :
                (1+pow(5*lambda/ proPathParameters.e, 2))/(1./3+pow(5*lambda/ proPathParameters.e, 2));

        double _delta = proPathParameters.isFavorable() && (type.equals(PointPath.POINT_TYPE.DIFH) || type.equals(DIFH_RCRIT)) ? proPathParameters.deltaF : proPathParameters.deltaH;
        double deltaDStar = (proPathParameters.getSegmentList().get(0).dPrime+ proPathParameters.getSegmentList().get(proPathParameters.getSegmentList().size()-1).dPrime- proPathParameters.getSRSegment().dPrime);
        double deltaDiffSR = 0;
        double testForm = 40/lambda*cSecond*_delta;

        if(_delta >= 0 || (_delta > -lambda/20 && _delta > lambda/4 - deltaDStar)) {
            deltaDiffSR = testForm>=-2 ? 10*ch*log10(3+testForm) : 0;
        } else if(type.equals(DIFH)) {
            return 0;
        }

        if(type.equals(DIFV)) {
            if(proPathParameters.keepAbsorption) {
                if(proPathParameters.isFavorable()) {
                    proPathParameters.aBoundaryF.deltaDiffSR[i] = deltaDiffSR;
                }
                else {
                    proPathParameters.aBoundaryH.deltaDiffSR[i] = deltaDiffSR;
                }
            }
            return deltaDiffSR;
        }

        _delta = proPathParameters.isFavorable() ? proPathParameters.deltaSPrimeRF : proPathParameters.deltaSPrimeRH;
        testForm = 40/lambda*cSecond*_delta;
        double deltaDiffSPrimeR = testForm>=-2 ? 10*ch*log10(3+testForm) : 0;

        _delta = proPathParameters.isFavorable() ? proPathParameters.deltaSRPrimeF : proPathParameters.deltaSRPrimeH;
        testForm = 40/lambda*cSecond*_delta;
        double deltaDiffSRPrime = testForm>=-2 ? 10*ch*log10(3+testForm) : 0;

        double aGroundSO = proPathParameters.isFavorable() ? aGroundF(proPathParameters, first, data, i) : aGroundH(proPathParameters, first, data, i);
        double aGroundOR = proPathParameters.isFavorable() ? aGroundF(proPathParameters, last, data, i, true) : aGroundH(proPathParameters, last, data, i, true);

        //If the source or the receiver are under the mean plane, change the computation of deltaDffSR and deltaGround
        double deltaGroundSO = -20*log10(1+(pow(10, -aGroundSO/20)-1)*pow(10, -(deltaDiffSPrimeR-deltaDiffSR)/20));
        double deltaGroundOR = -20*log10(1+(pow(10, -aGroundOR/20)-1)*pow(10, -(deltaDiffSRPrime-deltaDiffSR)/20));

        //Double check NaN values
        if(Double.isNaN(deltaGroundSO)){
            deltaGroundSO = aGroundSO;
            deltaDiffSR = deltaDiffSPrimeR;
        }
        if(Double.isNaN(deltaGroundOR)){
            deltaGroundOR = aGroundOR;
            deltaDiffSR = deltaDiffSPrimeR;
        }

        double aDiff = min(25, max(0, deltaDiffSR)) + deltaGroundSO + deltaGroundOR;
        if(proPathParameters.keepAbsorption) {
            if(proPathParameters.isFavorable()) {
                proPathParameters.aBoundaryF.deltaDiffSR[i] = deltaDiffSR;
                proPathParameters.aBoundaryF.aGroundSO[i] = aGroundSO;
                proPathParameters.aBoundaryF.aGroundOR[i] = aGroundOR;
                proPathParameters.aBoundaryF.deltaDiffSPrimeR[i] = deltaDiffSPrimeR;
                proPathParameters.aBoundaryF.deltaDiffSRPrime[i] = deltaDiffSRPrime;
                proPathParameters.aBoundaryF.deltaGroundSO[i] = deltaGroundSO;
                proPathParameters.aBoundaryF.deltaGroundOR[i] = deltaGroundOR;
                proPathParameters.aBoundaryF.aDiff[i] = aDiff;
            }
            else {
                proPathParameters.aBoundaryH.deltaDiffSR[i] = deltaDiffSR;
                proPathParameters.aBoundaryH.aGroundSO[i] = aGroundSO;
                proPathParameters.aBoundaryH.aGroundOR[i] = aGroundOR;
                proPathParameters.aBoundaryH.deltaDiffSPrimeR[i] = deltaDiffSPrimeR;
                proPathParameters.aBoundaryH.deltaDiffSRPrime[i] = deltaDiffSRPrime;
                proPathParameters.aBoundaryH.deltaGroundSO[i] = deltaGroundSO;
                proPathParameters.aBoundaryH.deltaGroundOR[i] = deltaGroundOR;
                proPathParameters.aBoundaryH.aDiff[i] = aDiff;
            }
        }

        return aDiff;
    }

    /**
     * Calculate the value of CfK
     * @param proPathParameters
     * @param path
     * @param data
     * @param idFreq
     * @return a double list of the value of CfK
     */
    private static double[] computeCfKValues(CnossosPath proPathParameters, SegmentPath path, AttenuationCnossosParameters data, int idFreq) {
        return computeCfKValues(proPathParameters, path, data, idFreq, false);
    }

    /**
     * Calculate the value of Cfk with checking if the absorption coefficient is
     * @param proPathParameters
     * @param path
     * @param data
     * @param idFreq
     * @param forceGPath
     * @return
     */
    private static double[] computeCfKValues(CnossosPath proPathParameters, SegmentPath path, AttenuationCnossosParameters data, int idFreq, boolean forceGPath) {
        int fm = data.freq_lvl.get(idFreq);
        double c = data.getCelerity();
        double dp = path.dp;
        double k = 2*PI*fm/c;
        double gw = forceGPath ? path.gPath : proPathParameters.isFavorable() ? path.gPath : path.gPathPrime;
        double w = 0.0185 * pow(fm, 2.5) * pow(gw, 2.6) /
                (pow(fm, 1.5) * pow(gw, 2.6) + 1.3e3 * pow(fm, 0.75) * pow(gw, 1.3) + 1.16e6);
        double cf = dp * (1 + 3 * w * dp * exp(-sqrt(w * dp))) / (1 + w * dp);
        return new double[]{cf, k, w};
    }


    /**
     * Compute AGroundH
     * @param proPathParameters
     * @param path
     * @param data
     * @param idFreq
     * @return homogeneous ground Atktenuation in db
     */
    public static double aGroundH(CnossosPath proPathParameters, SegmentPath path, AttenuationCnossosParameters data, int idFreq) {
        return aGroundH(proPathParameters, path, data, idFreq, false);
    }

    /**
     * Compute  AGroundH
     * @param proPathParameters
     * @param path
     * @param data
     * @param idFreq
     * @param forceGPath
     * @return homogeneous ground Attenuation in db
     */
    public static double aGroundH(CnossosPath proPathParameters, SegmentPath path, AttenuationCnossosParameters data, int idFreq, boolean forceGPath) {
        double[] values = computeCfKValues(proPathParameters, path, data, idFreq, forceGPath);
        double cf = values[0];
        double k = values[1];
        double w = values[2];
        if(proPathParameters.keepAbsorption && path == proPathParameters.getSRSegment()) {
            proPathParameters.groundAttenuation.wH[idFreq] = w;
            proPathParameters.groundAttenuation.cfH[idFreq] = cf;
        }
        if(path.gPath == 0) {
            return -3;
        }
        double dp = path.dp;
        double gm = forceGPath ? path.gPath : path.gPathPrime;
        double aGroundHMin = -3*(1-gm);
        double zs = path.zsH;
        double zr = path.zrH;
        double aGroundHComputed = -10 * log10(4 * (k*k) / (dp*dp) *
                (zs*zs - sqrt(2 * cf / k) * zs + cf / k) *
                (zr*zr - sqrt(2 * cf / k) * zr + cf / k));
        return max(aGroundHComputed, aGroundHMin);
    }

    //Todo check if the favorable testform should be use instead
    public static double aGroundF(CnossosPath proPathParameters, SegmentPath path, AttenuationCnossosParameters data, int idFreq) {
        return aGroundF(proPathParameters, path, data, idFreq, false);
    }

    /**
     * Compute AGroundF
     * @param proPathParameters
     * @param path
     * @param data
     * @param idFreq
     * @param forceGPath
     * @return favorable ground Attenuation in db
     */
    public static double aGroundF(CnossosPath proPathParameters, SegmentPath path, AttenuationCnossosParameters data, int idFreq, boolean forceGPath) {
        double[] values = computeCfKValues(proPathParameters, path, data, idFreq);
        double cf = values[0];
        double k = values[1];
        double w = values[2];
        if(proPathParameters.keepAbsorption && path == proPathParameters.getSRSegment()) {
            proPathParameters.groundAttenuation.wF[idFreq] = w;
            proPathParameters.groundAttenuation.cfF[idFreq] = cf;
        }
        double gm = forceGPath ? path.gPath : path.gPathPrime;
        double aGroundFMin = path.testFormH <= 1 ? -3 * (1 - gm) : -3 * (1 - gm) * (1 + 2 * (1 - (1 / path.testFormH)));
        if(path.gPath == 0) {
            return aGroundFMin;
        }
        else {
            double dp = path.dp;
            double zs = path.zsF;
            double zr = path.zrF;
            double aGroundFComputed = -10 * log10(4 * (k*k) / (dp*dp) *
                    (zs*zs - sqrt(2 * cf / k) * zs + cf / k) *
                    (zr*zr - sqrt(2 * cf / k) * zr + cf / k));
            return max(aGroundFComputed, aGroundFMin);
        }
    }
}
