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
import org.locationtech.jts.math.Vector3D;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SegmentPath {
    // debug/unit test purpose data
    /**
     * Ground points used to compute mean ground plane
     */
    private transient Coordinate[] points2DGround = new Coordinate[0];

    //  given by user
    public double gPath;          // G coefficient for the considered path segment
    public Vector3D meanGdPlane;     // mean ground plane for the considered path segment
    public Coordinate pInit;     // Init point of the mean ground plane
    public Coordinate s;
    public Coordinate r;
    public double a;
    public double b;

    // computed in AugmentedSegments
    public int idPtStart;               // start point indice for the considered path segment
    public int idPtFinal;               // final point indice for the considered path segment

    public Double gPathPrime = null;    //Gpath prime , calculated from Gpath and geometry
    public Double gw = null;
    public Double gm = null;
    public Double zsH = null; // The equivalent source height
    public Double zrH = null; //the equivalent receiver height
    public Double testFormH = null;

    public Coordinate sMeanPlane = null; // projection of source  points on ground for each segment
    public Coordinate rMeanPlane = null; // projection of receiver points on ground for each segment
    public Coordinate sPrime = null;
    public Coordinate rPrime = null;

    public Double zsF = null;
    public Double zrF = null;
    public Double testFormF = null;

    public Double dPath; // direct ray between source and receiver passing by diffraction and reflection points
    public Double d ; // direct ray between source and receiver
    public Double dc; // direct ray sensible to meteorological conditions (can be curve) between source and receiver
    public Double dp; // The distance between the source and receiver in projection over the mean ground plane
    public Double eLength = 0.0; // distance between first and last diffraction point
    public Double delta = 1.0;
    public double dPrime;
    public double deltaPrime;

    /**`
     * @return Ground points used to compute mean ground plane
     */
    public Coordinate[] getPoints2DGround() {
        return points2DGround;
    }

    /**
     * @param points2DGround Ground points used to compute mean ground plane
     */
    public void setPoints2DGround(Coordinate[] points2DGround) {
        this.points2DGround = points2DGround;
    }

    public void setDelta(Double delta) {
        this.delta = delta;
    }

    public Double getDelta() {
        return delta;
    }

    /**
     * @param gPath
     */
    public SegmentPath(double gPath, Vector3D meanGdPlane, Coordinate pInit) {
        this.gPath = gPath;
        this.meanGdPlane = meanGdPlane;
        this.pInit = pInit;
    }

    public SegmentPath() {
    }

    /**
     * Writes the content of this object into <code>out</code>.
     * @param out the stream to write into
     * @throws java.io.IOException if an I/O-error occurs
     */
    public void writeStream( DataOutputStream out ) throws IOException {
        out.writeDouble(gPath);
        Path.writeVector(out, meanGdPlane);
        Path.writeCoordinate(out, pInit);
    }

    /**
     * Reads the content of this object from <code>out</code>. All
     * properties should be set to their default value or to the value read
     * from the stream.
     * @param in the stream to read
     * @throws IOException if an I/O-error occurs
     */
    public void readStream( DataInputStream in ) throws IOException {
        gPath = in.readDouble();
        meanGdPlane = Path.readVector(in);
        pInit = Path.readCoordinate(in);
    }


    public void setGw(double g) {
        this.gw = g;
    }

    public void setGm(double g) {
        this.gm = g;
    }

    public void setGpath(double gPath, double gS) {
        this.gPath = gPath;
        this.gPathPrime = this.testFormH <= 1 ? this.gPath*(this.testFormH) + gS*(1-this.testFormH) : this.gPath;
    }

    public Double getGw() {
        return gw;
    }

    public Double getGm() {
        return gm;
    }

    public Double getgPathPrime() {
        return gPathPrime;
    }



    public Double getZs() {
        return zsH;
    }

    public Double getZr() {
        return zrH;
    }

    public Double getZsPrime() {
        return zsF;
    }

    public Double getZrPrime() {
        return zrF;
    }
}
