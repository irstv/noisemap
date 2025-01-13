/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;


/**
 * Noise function used in order to generate realistic height maps
 * https://github.com/KdotJPG/OpenSimplex2
 * Creative Commons Zero v1.0 Universal
 * K.jpg's OpenSimplex 2, smooth variant ("SuperSimplex")
 */

public class OpenSimplex2S {

    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Y = 0x598CD327003817B5L;
    private static final long HASH_MULTIPLIER = 0x53A3F72DEEC546F5L;
    private static final double SKEW_2D = 0.366025403784439;
    private static final double UNSKEW_2D = -0.21132486540518713;

    private static final int N_GRADS_2D_EXPONENT = 7;
    private static final int N_GRADS_2D = 1 << N_GRADS_2D_EXPONENT;

    private static final double NORMALIZER_2D = 0.05481866495625118;

    private static final float RSQUARED_2D = 2.0f / 3.0f;

    /*
     * Noise Evaluators
     */

    /**
     * 2D OpenSimplex2S/SuperSimplex noise, standard lattice orientation.
     */
    public static float noise2(long seed, double x, double y) {

        // Get points for A2* lattice
        double s = SKEW_2D * (x + y);
        double xs = x + s, ys = y + s;

        return noise2_UnskewedBase(seed, xs, ys);
    }

    /**
     * 2D  OpenSimplex2S/SuperSimplex noise base.
     */
    private static float noise2_UnskewedBase(long seed, double xs, double ys) {

        // Get base points and offsets.
        int xsb = fastFloor(xs), ysb = fastFloor(ys);
        float xi = (float)(xs - xsb), yi = (float)(ys - ysb);

        // Prime pre-multiplication for hash.
        long xsbp = xsb * PRIME_X, ysbp = ysb * PRIME_Y;

        // Unskew.
        float t = (xi + yi) * (float)UNSKEW_2D;
        float dx0 = xi + t, dy0 = yi + t;

        // First vertex.
        float a0 = RSQUARED_2D - dx0 * dx0 - dy0 * dy0;
        float value = (a0 * a0) * (a0 * a0) * grad(seed, xsbp, ysbp, dx0, dy0);

        // Second vertex.
        float a1 = (float)(2 * (1 + 2 * UNSKEW_2D) * (1 / UNSKEW_2D + 2)) * t + ((float)(-2 * (1 + 2 * UNSKEW_2D) * (1 + 2 * UNSKEW_2D)) + a0);
        float dx1 = dx0 - (float)(1 + 2 * UNSKEW_2D);
        float dy1 = dy0 - (float)(1 + 2 * UNSKEW_2D);
        value += (a1 * a1) * (a1 * a1) * grad(seed, xsbp + PRIME_X, ysbp + PRIME_Y, dx1, dy1);

        // Third and fourth vertices.
        // Nested conditionals were faster than compact bit logic/arithmetic.
        float xmyi = xi - yi;
        if (t < UNSKEW_2D) {
            if (xi + xmyi > 1) {
                float dx2 = dx0 - (float)(3 * UNSKEW_2D + 2);
                float dy2 = dy0 - (float)(3 * UNSKEW_2D + 1);
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp + (PRIME_X << 1), ysbp + PRIME_Y, dx2, dy2);
                }
            }
            else
            {
                float dx2 = dx0 - (float)UNSKEW_2D;
                float dy2 = dy0 - (float)(UNSKEW_2D + 1);
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp + PRIME_Y, dx2, dy2);
                }
            }

            if (yi - xmyi > 1) {
                float dx3 = dx0 - (float)(3 * UNSKEW_2D + 1);
                float dy3 = dy0 - (float)(3 * UNSKEW_2D + 2);
                float a3 = RSQUARED_2D - dx3 * dx3 - dy3 * dy3;
                if (a3 > 0) {
                    value += (a3 * a3) * (a3 * a3) * grad(seed, xsbp + PRIME_X, ysbp + (PRIME_Y << 1), dx3, dy3);
                }
            }
            else
            {
                float dx3 = dx0 - (float)(UNSKEW_2D + 1);
                float dy3 = dy0 - (float)UNSKEW_2D;
                float a3 = RSQUARED_2D - dx3 * dx3 - dy3 * dy3;
                if (a3 > 0) {
                    value += (a3 * a3) * (a3 * a3) * grad(seed, xsbp + PRIME_X, ysbp, dx3, dy3);
                }
            }
        }
        else
        {
            if (xi + xmyi < 0) {
                float dx2 = dx0 + (float)(1 + UNSKEW_2D);
                float dy2 = dy0 + (float)UNSKEW_2D;
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp - PRIME_X, ysbp, dx2, dy2);
                }
            }
            else
            {
                float dx2 = dx0 - (float)(UNSKEW_2D + 1);
                float dy2 = dy0 - (float)UNSKEW_2D;
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp + PRIME_X, ysbp, dx2, dy2);
                }
            }

            if (yi < xmyi) {
                float dx2 = dx0 + (float)UNSKEW_2D;
                float dy2 = dy0 + (float)(UNSKEW_2D + 1);
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp - PRIME_Y, dx2, dy2);
                }
            }
            else
            {
                float dx2 = dx0 - (float)UNSKEW_2D;
                float dy2 = dy0 - (float)(UNSKEW_2D + 1);
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp + PRIME_Y, dx2, dy2);
                }
            }
        }

        return value;
    }

    /*
     * Utility
     */

    private static float grad(long seed, long xsvp, long ysvp, float dx, float dy) {
        long hash = seed ^ xsvp ^ ysvp;
        hash *= HASH_MULTIPLIER;
        hash ^= hash >> (64 - N_GRADS_2D_EXPONENT + 1);
        int gi = (int)hash & ((N_GRADS_2D - 1) << 1);
        return GRADIENTS_2D[gi | 0] * dx + GRADIENTS_2D[gi | 1] * dy;
    }

    private static int fastFloor(double x) {
        int xi = (int)x;
        return x < xi ? xi - 1 : xi;
    }

    /*
     * Lookup Tables & Gradients
     */

    private static float[] GRADIENTS_2D;

    static {

        GRADIENTS_2D = new float[N_GRADS_2D * 2];
        float[] grad2 = {0.38268343236509f, 0.923879532511287f, 0.923879532511287f, 0.38268343236509f, 0.923879532511287f, -0.38268343236509f, 0.38268343236509f, -0.923879532511287f, -0.38268343236509f, -0.923879532511287f, -0.923879532511287f, -0.38268343236509f, -0.923879532511287f, 0.38268343236509f, -0.38268343236509f, 0.923879532511287f,
                //-------------------------------------//
                0.130526192220052f, 0.99144486137381f, 0.608761429008721f, 0.793353340291235f, 0.793353340291235f, 0.608761429008721f, 0.99144486137381f, 0.130526192220051f, 0.99144486137381f, -0.130526192220051f, 0.793353340291235f, -0.60876142900872f, 0.608761429008721f, -0.793353340291235f, 0.130526192220052f, -0.99144486137381f, -0.130526192220052f, -0.99144486137381f, -0.608761429008721f, -0.793353340291235f, -0.793353340291235f, -0.608761429008721f, -0.99144486137381f, -0.130526192220052f, -0.99144486137381f, 0.130526192220051f, -0.793353340291235f, 0.608761429008721f, -0.608761429008721f, 0.793353340291235f, -0.130526192220052f, 0.99144486137381f,};
        for (int i = 0; i < grad2.length; i++) {
            grad2[i] = (float) (grad2[i] / NORMALIZER_2D);
        }
        for (int i = 0, j = 0; i < GRADIENTS_2D.length; i++, j++) {
            if (j == grad2.length) j = 0;
            GRADIENTS_2D[i] = grad2[j];
        }
    }
}
