/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CutProfile {
    /** List of cut points.
     * First point is source, last point is receiver */
    public ArrayList<CutPoint> cutPoints = new ArrayList<>();

    /** True if Source-Receiver linestring is below building intersection */
    public boolean hasBuildingIntersection = false;
    /** True if Source-Receiver linestring is below topography cutting point. */
    public boolean hasTopographyIntersection = false;

    /**
     * Empty constructor for deserialization
     */
    public CutProfile() {
    }

    public CutProfile(CutPointSource source, CutPointReceiver receiver) {
        cutPoints.add(source);
        cutPoints.add(receiver);
    }

    /**
     * Insert and sort cut points,
     * @param sortBySourcePosition After inserting points, sort the by the distance from the source
     * @param cutPointsToInsert
     */
    public void insertCutPoint(boolean sortBySourcePosition, CutPoint... cutPointsToInsert) {
        CutPointSource sourcePoint = getSource();
        CutPointReceiver receiverPoint = getReceiver();
        cutPoints.addAll(1, Arrays.asList(cutPointsToInsert));
        if(sortBySourcePosition) {
            sort(sourcePoint.coordinate);
            // move source as the first point
            int sourceIndex = cutPoints.indexOf(sourcePoint);
            if (sourceIndex != 0) {
                cutPoints.remove(sourceIndex);
                cutPoints.add(0, sourcePoint);
            }
            // move receiver as the last point
            int receiverIndex = cutPoints.indexOf(receiverPoint);
            if (receiverIndex != cutPoints.size() - 1) {
                cutPoints.remove(receiverIndex);
                cutPoints.add(cutPoints.size(), receiverPoint);
            }
        }
    }

    /**
     * Sort the CutPoints by distance with c0
     */
    public void sort(Coordinate c0) {
        cutPoints.sort(new CutPointDistanceComparator(c0));
    }

    /**
     * compute the path between two points
     * @param p0
     * @param p1
     * @return the absorption coefficient of this path
     */
    @JsonIgnore
    public double getGPath(CutPoint p0, CutPoint p1, double buildingRoofG) {
        double totalLength = 0;
        double rsLength = 0.0;

        // Extract part of the path from the specified argument
        int i0 = cutPoints.indexOf(p0);
        int i1 = cutPoints.indexOf(p1);
        if(i0 == -1 || i1 == -1 || i1 < i0) {
            return 0.0;
        }

        boolean aboveRoof = false;
        for(int index = 0; index < i1; index++) {
            CutPoint current = cutPoints.get(index);
            if(current instanceof CutPointWall) {
                CutPointWall currentWall = (CutPointWall) current;
                if(!aboveRoof && currentWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.BUILDING_ENTER)) {
                    aboveRoof = true;
                } else if(aboveRoof && currentWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.BUILDING_EXIT)) {
                    aboveRoof = false;
                }
            }
            if(index >= i0) {
                double segmentLength = current.getCoordinate().distance(cutPoints.get(index + 1).getCoordinate());
                rsLength += segmentLength * (aboveRoof ? buildingRoofG : current.getGroundCoefficient());
                totalLength += segmentLength;
            }
        }
        return rsLength / totalLength;
    }

    @JsonIgnore
    public double getGPath() {
        if(!cutPoints.isEmpty()) {
            return getGPath(cutPoints.get(0), cutPoints.get(cutPoints.size() - 1), Scene.DEFAULT_G_BUILDING);
        } else {
            return 0;
        }
    }

    /**
     *
     * @return
     */
    @JsonIgnore
    public boolean isFreeField() {
        return !hasBuildingIntersection && !hasTopographyIntersection;
    }


    @Override
    public String toString() {
        return "CutProfile{" +
                "pts=" + cutPoints +
                ", hasBuildingIntersection=" + hasBuildingIntersection +
                ", hasTopographyIntersection=" + hasTopographyIntersection +
                '}';
    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings) then re-project it into
     * a 2d coordinate system. The first point is always x=0.
     * @return the computed 2D coordinate list of DEM
     */
    public List<Coordinate> computePts2DGround() {
        return computePts2DGround(0, null);
    }


    /**
     *
     * @param pts points
     * @return @return the computed coordinate list
     */
    public List<Coordinate> computePts2D() {
        List<Coordinate> pts2D = cutPoints.stream()
                .map(CutPoint::getCoordinate)
                .collect(Collectors.toList());
        pts2D = JTSUtility.getNewCoordinateSystem(pts2D);
        return pts2D;
    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings) then re-project it into
     * a 2d coordinate system. The first point is always x=0.
     * @param index Corresponding index from parameter to return list items
     * @return the computed 2D coordinate list of DEM
     */
    public List<Coordinate> computePts2DGround(List<Integer> index) {
        return computePts2DGround(0, index);
    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings)
     * @param pts Cut points
     * @param index Corresponding index from parameter to return list items
     * @return the computed coordinate list of the vertical cut
     */
    public static List<Coordinate> computePtsGround(List<CutPoint> pts, List<Integer> index) {

        List<Coordinate> pts2D = new ArrayList<>(pts.size());
        if(pts.isEmpty()) {
            return pts2D;
        }
        // keep track of the obstacle under our current position.
        boolean overArea = false;
        for (CutPoint cut : pts) {
            if (cut instanceof CutPointWall) {
                CutPointWall cutPointWall = (CutPointWall) cut;
                if (cutPointWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.BUILDING_EXIT)) {
                    overArea = true;
                } else {
                    break;
                }
            }
        }
        for (CutPoint cut : pts) {
            if (cut instanceof CutPointGroundEffect) {
                if (index != null) {
                    index.add(pts2D.size() - 1);
                }
                continue;
            }
            if (cut instanceof CutPointWall) {
                // Z ground profile must add intermediate ground points before adding the top level of building/wall
                CutPointWall cutPointWall = (CutPointWall) cut;
                if (cutPointWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.BUILDING_ENTER) ||
                        cutPointWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT)) {
                    pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
                    overArea = true;
                }
                pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getCoordinate().z));
                if (cutPointWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.BUILDING_EXIT) ||
                        cutPointWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT)) {
                    pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
                    overArea = false;
                }
            } else if (cut instanceof CutPointReflection) {
                // Z ground profile is duplicated for reflection point before and after
                pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
                pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
                pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
            } else {
                // we will ignore topographic point if we are over a building
                if (!(overArea && cut instanceof CutPointTopography)) {
                    pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
                }
            }
            if (index != null) {
                index.add(pts2D.size() - 1);
            }
        }
        return pts2D;
    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings) then re-project it into
     * a 2d coordinate system. The first point is always x=0.
     * @param pts Cut points
     * @param tolerance Simplify the point list by not adding points where the distance from the line segments
     *                 formed from the previous and the next point is inferior to this tolerance (remove intermediate collinear points)
     * @param index Corresponding index from parameter to return list items
     * @return the computed 2D coordinate list of DEM
     */
    public static List<Coordinate> computePts2DGround(List<CutPoint> pts, double tolerance, List<Integer> index) {
        return JTSUtility.getNewCoordinateSystem(computePtsGround(pts, index), tolerance);
    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings) then re-project it into
     * a 2d coordinate system. The first point is always x=0.
     * @param tolerance Simplify the point list by not adding points where the distance from the line segments
     *                 formed from the previous and the next point is inferior to this tolerance (remove intermediate collinear points)
     * @param index Corresponding index from parameter to return list items
     * @return the computed 2D coordinate list of DEM
     */
    public List<Coordinate> computePts2DGround(double tolerance, List<Integer> index) {
        return computePts2DGround(this.cutPoints, tolerance, index);
    }

    @JsonIgnore
    public CutPointSource getSource() {
        return !cutPoints.isEmpty() && cutPoints.get(0) instanceof CutPointSource ?
                (CutPointSource) cutPoints.get(0) : null;
    }

    @JsonIgnore
    public CutPointReceiver getReceiver() {
        return !cutPoints.isEmpty() && cutPoints.get(cutPoints.size() - 1) instanceof CutPointReceiver ?
                (CutPointReceiver) cutPoints.get(cutPoints.size() - 1) : null;
    }
}
