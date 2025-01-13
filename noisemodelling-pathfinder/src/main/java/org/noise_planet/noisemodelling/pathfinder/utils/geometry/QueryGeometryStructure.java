/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.utils.geometry;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import java.util.Iterator;

/**
 * QueryGeometryStructure aims to speed up the query of a geometry collection
 * inside a region envelope.
 * 
 * @author Nicolas Fortin
 */

public interface QueryGeometryStructure {

	void appendGeometry(final Geometry newGeom, Integer externalId);

	Iterator<Integer> query(Envelope queryEnv);

}
