/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.noise_planet.noisemodelling.jdbc.utils.MakeParallelLines;

import java.util.ArrayList;
import java.util.List;

import static org.noise_planet.noisemodelling.jdbc.utils.MakeParallelLines.MakeParallelLine;

public class MakeParallelLinesTest {

    @Test
    public void makeParallelLine() throws ParseException {
        WKTReader wktReader  = new WKTReader();
        LineString line = (LineString)wktReader.read("LINESTRING(0 0,5 0,10 0)");

        LineString line2 = MakeParallelLines.MakeParallelLine(line, 0.1);

        LineString line3 = MakeParallelLines.MakeParallelLine(line, -0.1);

        System.out.println(new GeometryFactory().createMultiLineString(new LineString[]{line, line2, line3}));
    }




    @Test
    public void makeParallelLine2() throws ParseException {
        WKTReader wktReader  = new WKTReader();
        LineString line = (LineString)wktReader.read("LINESTRING(4 5,5 8,6 6,6 8,9 8,10 5,12 6)");

        LineString line2 = MakeParallelLines.MakeParallelLine(line, 0.5);

        System.out.println(new GeometryFactory().createMultiLineString(new LineString[]{line, line2}));
    }

    @Test
    public void makeParallelLine3() throws ParseException {
        List<LineString> geometries = new ArrayList<>();
        List<LineString> lines = new ArrayList<>();
        WKTReader wktReader  = new WKTReader();
        LineString line = (LineString) wktReader.read("LINESTRING(0 0,10 0)");
        lines.add(line);
        int nbTrack = 6;
        double distance = 1;


        boolean even = false;
        if (nbTrack % 2 == 0) even = true;

        if (nbTrack == 1) {
            geometries.add(line);
        } else {

            if (even) {
                for (int j = 0; j < nbTrack / 2; j++) {
                    for (LineString subGeom : lines) {
                        geometries.add(MakeParallelLine(subGeom, (distance / 2) + distance * j));
                        geometries.add(MakeParallelLine(subGeom, -((distance / 2) + distance * j)));
                    }
                }
            } else {
                for (int j = 1; j <= ((nbTrack - 1) / 2); j++) {
                    for (LineString subGeom : lines) {
                        geometries.add(MakeParallelLine(subGeom, distance * j));
                        geometries.add(MakeParallelLine(subGeom, -(distance * j)));
                    }
                }
                LineMerger centerLine = new LineMerger();
                centerLine.add(lines);
                geometries.addAll(centerLine.getMergedLineStrings());
            }

        }
        System.out.println(geometries);
    }
}

