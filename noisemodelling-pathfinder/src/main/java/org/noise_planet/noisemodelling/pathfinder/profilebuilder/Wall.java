package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;



public class Wall {
    /** Type of the wall */
    public final ProfileBuilder.IntersectionType type;
    /** Id or index of the source building or topographic triangle. */
    public final int originId;
    public long primaryKey = -1;
    /** Wall alpha value. */
    public List<Double> alphas;
    /** Wall height, if -1, use z coordinate. */
    public double height;
    public Coordinate p0;
    public Coordinate p1;
    public LineSegment ls;
    public int processedWallIndex;

    /**
     * Constructor using segment and id.
     * @param line     Segment of the wall.
     * @param originId Id or index of the source building or topographic triangle.
     */
    public Wall(LineSegment line, int originId, ProfileBuilder.IntersectionType type) {
        this.p0 = line.p0;
        this.p1 = line.p1;
        this.ls = line;
        this.originId = originId;
        this.type = type;
        this.alphas = new ArrayList<>();
    }

    /**
     * Constructor using start/end point and id.
     * @param p0       Start point of the segment.
     * @param p1       End point of the segment.
     * @param originId Id or index of the source building or topographic triangle.
     */
    public Wall(Coordinate p0, Coordinate p1, int originId, ProfileBuilder.IntersectionType type) {
        this.p0 = p0;
        this.p1 = p1;
        this.ls = new LineSegment(p0, p1);
        this.originId = originId;
        this.type = type;
        this.alphas = new ArrayList<>();
    }

    /**
     * Database primary key of this wall or the building
     * @param primaryKey primary key value
     * @return this
     */
    public Wall setPrimaryKey(long primaryKey) {
        this.primaryKey = primaryKey;
        return this;
    }

    /**
     * @return Index of this wall in the ProfileBuild list
     */
    public int getProcessedWallIndex() {
        return processedWallIndex;
    }

    /**
     * @param processedWallIndex Index of this wall in the ProfileBuild list
     */
    public Wall setProcessedWallIndex(int processedWallIndex) {
        this.processedWallIndex = processedWallIndex;
        return this;
    }

    /**
     * Sets the wall alphas.
     * @param alphas Wall alphas.
     */
    public void setAlpha(List<Double> alphas) {
        this.alphas = alphas;
    }

    /**
     * Sets the wall height.
     * @param height Wall height.
     */
    public void setHeight(double height) {
        this.height = height;
    }

    public LineSegment getLineSegment() {
        return ls;
    }

    /**
     * Retrieve the id or index of the source building or topographic triangle.
     * @return Id or index of the source building or topographic triangle.
     */
    public int getOriginId() {
        return originId;
    }

    /**
     * Retrieve the alphas of the wall.
     * @return Alphas of the wall.
     */
    public List<Double> getAlphas() {
        return alphas;
    }

    /**
     * Retrieve the height of the wall.
     * @return Height of the wall.
     */
    public double getHeight() {
        return height;
    }

    public ProfileBuilder.IntersectionType getType() {
        return type;
    }
}