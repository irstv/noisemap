/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.utils.geometry;

/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.GeometryTransformer;

/**
 * Densifies a {@link Geometry} by inserting extra vertices along the line segments
 * contained in the geometry.
 * All segments in the created densified geometry will be no longer than
 * than the given distance tolerance.
 * Densified polygonal geometries are guaranteed to be topologically correct.
 * The coordinates created during densification respect the input geometry's
 * {@link PrecisionModel}.
 * <p>
 * <b>Note:</b> At some future point this class will
 * offer a variety of densification strategies.
 *
 * @author Martin Davis
 */
public class Densifier3D {
    /**
     * Densifies a geometry using a given distance tolerance,
     * and respecting the input geometry's {@link PrecisionModel}.
     *
     * @param geom the geometry to densify
     * @param distanceTolerance the distance tolerance to densify
     * @return the densified geometry
     */
    public static Geometry densify(Geometry geom, double distanceTolerance) {
        Densifier3D densifier = new Densifier3D(geom);
        densifier.setDistanceTolerance(distanceTolerance);
        return densifier.getResultGeometry();
    }

    /**
     * Densifies a coordinate sequence.
     *
     * @param pts
     * @param distanceTolerance
     * @return the densified coordinate sequence
     */
    private static Coordinate[] densifyPoints(Coordinate[] pts,
                                              double distanceTolerance, PrecisionModel precModel) {
        LineSegment seg = new LineSegment();
        CoordinateList coordList = new CoordinateList();
        for (int i = 0; i < pts.length - 1; i++) {
            seg.p0 = pts[i];
            seg.p1 = pts[i + 1];
            coordList.add(seg.p0, false);
            double len = seg.getLength();
            int densifiedSegCount = (int) (len / distanceTolerance) + 1;
            if (densifiedSegCount > 1) {
                double densifiedSegLen = len / densifiedSegCount;
                for (int j = 1; j < densifiedSegCount; j++) {
                    double segFract = (j * densifiedSegLen) / len;
                    Coordinate p = seg.pointAlong(segFract);
                    if(!Double.isNaN(seg.p0.z) && !Double.isNaN(seg.p1.z)) {
                        p.z = (seg.p1.z - seg.p0.z) * segFract + seg.p0.z;
                    }
                    precModel.makePrecise(p);
                    coordList.add(p, false);
                }
            }
        }
        coordList.add(pts[pts.length - 1], false);
        return coordList.toCoordinateArray();
    }

    private Geometry inputGeom;

    private double distanceTolerance;

    /**
     * Creates a new densifier instance.
     *
     * @param inputGeom
     */
    public Densifier3D(Geometry inputGeom) {
        this.inputGeom = inputGeom;
    }

    /**
     * Sets the distance tolerance for the densification. All line segments
     * in the densified geometry will be no longer than the distance tolerance.
     * simplified geometry will be within this distance of the original geometry.
     * The distance tolerance must be positive.
     *
     * @param distanceTolerance
     *          the densification tolerance to use
     */
    public void setDistanceTolerance(double distanceTolerance) {
        if (distanceTolerance <= 0.0)
            throw new IllegalArgumentException("Tolerance must be positive");
        this.distanceTolerance = distanceTolerance;
    }

    /**
     * Gets the densified geometry.
     *
     * @return the densified geometry
     */
    public Geometry getResultGeometry() {
        return (new Densifier3D.DensifyTransformer(distanceTolerance)).transform(inputGeom);
    }

    static class DensifyTransformer extends GeometryTransformer {
        double distanceTolerance;

        DensifyTransformer(double distanceTolerance) {
            this.distanceTolerance = distanceTolerance;
        }

        protected CoordinateSequence transformCoordinates(
                CoordinateSequence coords, Geometry parent) {
            Coordinate[] inputPts = coords.toCoordinateArray();
            Coordinate[] newPts = Densifier3D
                    .densifyPoints(inputPts, distanceTolerance, parent.getPrecisionModel());
            // prevent creation of invalid linestrings
            if (parent instanceof LineString && newPts.length == 1) {
                newPts = new Coordinate[0];
            }
            return factory.getCoordinateSequenceFactory().create(newPts);
        }

        protected Geometry transformPolygon(Polygon geom, Geometry parent) {
            Geometry roughGeom = super.transformPolygon(geom, parent);
            // don't try and correct if the parent is going to do this
            if (parent instanceof MultiPolygon) {
                return roughGeom;
            }
            return createValidArea(roughGeom);
        }

        protected Geometry transformMultiPolygon(MultiPolygon geom, Geometry parent) {
            Geometry roughGeom = super.transformMultiPolygon(geom, parent);
            return createValidArea(roughGeom);
        }

        /**
         * Creates a valid area geometry from one that possibly has bad topology
         * (i.e. self-intersections). Since buffer can handle invalid topology, but
         * always returns valid geometry, constructing a 0-width buffer "corrects"
         * the topology. Note this only works for area geometries, since buffer
         * always returns areas. This also may return empty geometries, if the input
         * has no actual area.
         *
         * @param roughAreaGeom
         *          an area geometry possibly containing self-intersections
         * @return a valid area geometry
         */
        private Geometry createValidArea(Geometry roughAreaGeom) {
            return roughAreaGeom.buffer(0.0);
        }
    }

}