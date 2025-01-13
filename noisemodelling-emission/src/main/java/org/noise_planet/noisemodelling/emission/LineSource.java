/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission;

/**
 * Line Source Class
 * A point source is define by its spectrum, height, directivity and type (optional)
 *
 * @author Pierre Aumond, Université Gustave Eiffel
 */


public class LineSource {

    private double[] lW ; // spectrum of the line source in dB/m
    public String typeSource = ""; // Optional, this type can help to select a directivity pattern
    public double sourceHeight ; // height of the source in meters
    public String directivity = ""; // directivity pattern


    public String getTypeSource() {
        return typeSource;
    }

    public void setTypeSource(String typeSource) {
        this.typeSource = typeSource;
    }

    public double getSourceHeight() {
        return sourceHeight;
    }

    public void setSourceHeight(double sourceHeight) {
        this.sourceHeight = sourceHeight;
    }

    public String getDirectivity() {
        return directivity;
    }

    public void setDirectivity(String directivity) {
        this.directivity = directivity;
    }

    public double[] getlW() {
        return lW;
    }

    public void setlW(double[] lW) {
        this.lW = lW;
    }


    private void setLW(double[] lW) {
    }

    public LineSource(double[] lW, double sourceHeight, String typeSource) {
        this.lW = lW;
        this.sourceHeight = sourceHeight;
        this.typeSource = typeSource;
    }

    public LineSource(double sourceHeight, String typeSource) {
        this.sourceHeight = sourceHeight;
        this.typeSource = typeSource;
    }

    public LineSource(String typeSource) {
        this.typeSource = typeSource;
    }

}
