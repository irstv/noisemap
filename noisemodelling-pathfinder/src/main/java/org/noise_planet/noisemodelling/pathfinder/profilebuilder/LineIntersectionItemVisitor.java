package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.index.ItemVisitor;

public interface LineIntersectionItemVisitor extends ItemVisitor {
    void setIntersectionLine(LineSegment segment);
}
