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
import org.locationtech.jts.index.strtree.STRtree;

import java.util.Iterator;

/**
 * Connector for RTree.
 * @author Nicolas Fortin
 */
public class QueryRTree implements QueryGeometryStructure {
    private STRtree rTree;
    public QueryRTree() {
        rTree = new STRtree();
    }

    /**
     * Add a given geometry and its Id into the tree
     * @param newGeom
     * @param externalId
     */
    @Override
    public void appendGeometry(Geometry newGeom, Integer externalId) {
        rTree.insert(newGeom.getEnvelopeInternal(), externalId);
    }

    /**
     *
     * @param queryEnv
     * @return
     */
    @Override
    public Iterator<Integer> query(Envelope queryEnv) {
        return rTree.query(queryEnv).iterator();
    }
    
}
