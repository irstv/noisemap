/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.noise_planet.noisemodelling.pathfinder.utils.ComplexNumber;

/**
 * Collection of methods related to wall absorption coefficients
 */
public class WallAbsorption {

    /**
     * Get WallAlpha
     */
    public static double getWallAlpha(double wallAlpha, double freq_lvl)
    {
        double value;
        if(wallAlpha >= 0 && wallAlpha <= 1) {
            // todo let the user choose if he wants to convert G to Sigma
            //value = GetWallImpedance(20000 * Math.pow (10., -2 * Math.pow (wallAlpha, 3./5.)),freq_lvl);
            value= wallAlpha;
        } else {
            value = GetWallImpedance(Math.min(20000, Math.max(20, wallAlpha)),freq_lvl);
        }
        return value;
    }

    public static double GetWallImpedance(double sigma, double freq_l)
    {
        double s = Math.log(freq_l / sigma);
        double x = 1. + 9.08 * Math.exp(-.75 * s);
        double y = 11.9 * Math.exp(-0.73 * s);
        ComplexNumber Z = new ComplexNumber(x, y);

        // Delany-Bazley method, not used in NoiseModelling for the moment
            /*double layer = 0.05; // Let user Choose
            if (layer > 0 && sigma < 1000)
            {
                s = 1000 * sigma / freq;
                double c = 340;
                double RealK= 2 * Math.PI * freq / c *(1 + 0.0858 * Math.pow(s, 0.70));
                double ImgK=2 * Math.PI * freq / c *(0.175 * Math.pow(s, 0.59));
                ComplexNumber k = ComplexNumber.multiply(new ComplexNumber(2 * Math.PI * freq / c,0) , new ComplexNumber(1 + 0.0858 * Math.pow(s, 0.70),0.175 * Math.pow(s, 0.59)));
                ComplexNumber j = new ComplexNumber(-0, -1);
                ComplexNumber m = ComplexNumber.multiply(j,k);
                Z[i] = ComplexNumber.divide(Z[i], (ComplexNumber.exp(m)));
            }*/

        return GetTrueWallAlpha(Z);
    }

    static double GetTrueWallAlpha(ComplexNumber impedance)         // TODO convert impedance to alpha
    {
        double alpha ;
        ComplexNumber z = ComplexNumber.divide(new ComplexNumber(1.0,0), impedance) ;
        double x = z.getRe();
        double y = z.getIm();
        double a1 = (x * x - y * y) / y ;
        double a2 = y / (x * x + y * y + x) ;
        double a3 = ((x + 1) *(x + 1) + y * y) / (x * x + y * y) ;
        alpha = 8 * x * (1 + a1 * Math.atan(a2) - x * Math.log(a3)) ;
        return alpha ;
    }
}
