/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class Building {
    /** Building footprint. */
    Polygon poly;
    /** Height of the building. */
    final double height;
    /**
     * Minimum Z ground under building contour
     */
    double minimumZDEM = Double.NaN;

    /** Absorption coefficients. */
    final List<Double> alphas;

    /** if true take into account z value on Buildings Polygons */
    final boolean zBuildings;

    /** Primary key of the building in the database. */
    long primaryKey = -1;
    List<Wall> walls = new ArrayList<>();

    /**
     *
     */
    public void poly2D_3D(){

        GeometryFactory f = new GeometryFactory();

        LinearRing shell2D = poly.getExteriorRing();
        Coordinate[] newCoordinate = new Coordinate[shell2D.getNumPoints()];
        for (int idCoordinate=0;idCoordinate<newCoordinate.length;idCoordinate++) {
            newCoordinate[idCoordinate] = new Coordinate(shell2D.getCoordinateN(idCoordinate).getX(),shell2D.getCoordinateN(idCoordinate).getY(),0.0);
        }

        LinearRing shell3D = f.createLinearRing(newCoordinate);

        LinearRing[] holes = new LinearRing[poly.getNumInteriorRing()];
        for (int idHole=0;idHole<holes.length;idHole++){
            LinearRing lr2D = poly.getInteriorRingN(idHole);
            newCoordinate = new Coordinate[lr2D.getNumPoints()];
            for (int idCoordinate=0;idCoordinate<newCoordinate.length;idCoordinate++) {
                newCoordinate[idCoordinate] = new Coordinate(lr2D.getCoordinateN(idCoordinate).getX(),
                        lr2D.getCoordinateN(idCoordinate).getY(),
                        0.0);
            }

            holes[idHole]=f.createLinearRing(newCoordinate);
        }


        Polygon newPoly = f.createPolygon(shell3D, holes);
        this.poly = newPoly;
    }


    /**
     * Main constructor.
     * @param poly   {@link Geometry} footprint of the building.
     * @param height Height of the building.
     * @param alphas Absorption coefficients.
     * @param key Primary key of the building in the database.
     */
    public Building(Polygon poly, double height, List<Double> alphas, long key, boolean zBuildings) {
        this.poly = poly;
        // Fix clock wise orientation of the polygon and inner holes
        this.poly.normalize();
        this.height = height;
        this.alphas = new ArrayList<>();
        this.alphas.addAll(alphas);
        this.primaryKey = key;
        this.zBuildings = zBuildings;
    }

    /**
     * get Height from Building
     * @return height
     */
    public double getHeight() { return height; }


    /**
     * Retrieve the building footprint.
     * @return The building footprint.
     */
    public Polygon getGeometry() {
        return poly;
    }

    /**
     * Retrieve the absorption coefficients.
     * @return The absorption coefficients.
     */
    public List<Double> getAlphas() {
        return alphas;
    }

    /**
     * Retrieve the primary key of the building in the database. If there is no primary key, returns -1.
     * @return The primary key of the building in the database or -1.
     */
    public long getPrimaryKey() {
        return primaryKey;
    }

    /**
     * Compute minimum Z ground under the building contour
     * @param profileBuilder
     * @return
     */
    public double updateZTopo(ProfileBuilder profileBuilder) {
        Coordinate[] coordinates = poly.getBoundary().getCoordinates();
        double minZ = Double.MAX_VALUE;
        AtomicInteger triangleHint = new AtomicInteger(-1);
        for (int i = 0; i < coordinates.length-1; i++) {
            minZ = Math.min(minZ, profileBuilder.getZGround(coordinates[i], triangleHint));
        }
        minimumZDEM = minZ;
        return minimumZDEM;
    }

    public double getZ() {
        if(Double.isNaN(minimumZDEM) || Double.isNaN(height)) {
            return poly.getCoordinate().z;
        } else {
            return minimumZDEM + height;
        }
    }

    /**
     *
     * @param walls
     */
    public void setWalls(List<Wall> walls) {
        this.walls = walls;
    }

    public Collection<? extends Wall> getWalls() {
        return walls;
    }
}