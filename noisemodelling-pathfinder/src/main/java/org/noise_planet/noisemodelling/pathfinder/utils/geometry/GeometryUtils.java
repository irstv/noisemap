/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.utils.geometry;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.math.Vector3D;

public class GeometryUtils {
    /**
     *
     * @param P
     * @param vector
     * @param pInit
     * @return
     */
    public static Coordinate projectPointOnSegment(Coordinate P, Vector3D vector, Coordinate pInit) {
        Coordinate A = new Coordinate(pInit.x, pInit.y,pInit.z);
        Coordinate B = new Coordinate(vector.getX()+pInit.x, vector.getY()+pInit.y,vector.getZ()+pInit.z);

        return new Coordinate(A.x+(Vector3D.dot(A,P,A,B) / Vector3D.dot(A,B,A,B))*vector.getX(),
                A.y+(Vector3D.dot(A,P,A,B) / Vector3D.dot(A,B,A,B))*vector.getY(),
                A.z+(Vector3D.dot(A,P,A,B) / Vector3D.dot(A,B,A,B))*vector.getZ());
    }

    /**
     *
     * @param P
     * @param vector
     * @param pInit
     * @return
     */
    public static Coordinate projectPointOnVector(Coordinate P, Vector3D vector,Coordinate pInit) {
        Coordinate A = new Coordinate(pInit.x, pInit.y,pInit.z);
        Coordinate B = new Coordinate(vector.getX()+pInit.x, vector.getY()+pInit.y,vector.getZ()+pInit.z);
        return new Coordinate(A.x+(Vector3D.dot(A,P,A,B) / Vector3D.dot(A,B,A,B))*vector.getX(),
                A.y+(Vector3D.dot(A,P,A,B) / Vector3D.dot(A,B,A,B))*vector.getY(),
                A.z+(Vector3D.dot(A,P,A,B) / Vector3D.dot(A,B,A,B))*vector.getZ());
    }

    /**
     * projette un point sur une ligne donnée.
     * @param c représente les coordonnées du point à projeter.
     * @param a la pente (x) de la ligne.
     * @param b l'ordonnée (y) à l'origine de la ligne.
     * @return les coordonnées du point projeté
     */
    public static Coordinate projectPointOnLine(Coordinate c, double a, double b) {
        double x = (c.x-a*b+a*c.y)/(1+a*a);
        double y = b+a*(c.x-a*b+a*c.y)/(1+a*a);
        return new Coordinate(x, y);
    }
}
