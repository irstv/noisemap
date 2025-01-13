package org.noise_planet.noisemodelling.pathfinder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJTSUtility {

    /**
     *  Error for coordinates. Its high because cnossos is rounding all its coordinates to 0.01
     */
    private static final double DELTA_COORDS = 0.1;

    @Test
    public void testGetNewCoordinateSystem() {

        List<Coordinate> expectedZProfile = new ArrayList<>();
        expectedZProfile.add(new Coordinate(0.0, 0.0));
        expectedZProfile.add(new Coordinate(14.46, 0.0));
        expectedZProfile.add(new Coordinate(23.03, 5.0));
        expectedZProfile.add(new Coordinate(24.39, 5.0));
        expectedZProfile.add(new Coordinate(32.85, 0.0));
        expectedZProfile.add(new Coordinate(45.10, 0.0));
        expectedZProfile.add(new Coordinate(45.10, 6.0));
        expectedZProfile.add(new Coordinate(60.58, 6.0));
        expectedZProfile.add(new Coordinate(60.58, 0.0));
        expectedZProfile.add(new Coordinate(68.15, 0.0));

        List<Coordinate> profile3D = new ArrayList<>();

        profile3D.add(new Coordinate(38.0, 14.0, 0.0));
        profile3D.add(new Coordinate(52.42955839014942, 14.954897246406947, 0.0));
        profile3D.add(new Coordinate(61.05310530816698, 15.525573145393402, 5.0));
        profile3D.add(new Coordinate(62.24216524375934, 15.60426093524878, 5.0));
        profile3D.add(new Coordinate(70.77572077133856, 16.1689815216327, 0.0));
        profile3D.add(new Coordinate(82.999, 16.977941176470587, 0.0));
        profile3D.add(new Coordinate(83.0, 16.977941176470587, 6.0));
        profile3D.add(new Coordinate(98.44444444444444, 18.0, 6.0));
        profile3D.add(new Coordinate(98.44444444444444, 18.001, 0.0));
        profile3D.add(new Coordinate(106.0, 18.5, 0.0));

        List<Coordinate> actualZProfile = JTSUtility.getNewCoordinateSystem(profile3D, ProfileBuilder.MILLIMETER);

        assertZProfil(expectedZProfile, actualZProfile);
    }


    private static void assertZProfil(List<Coordinate> expectedZ_profile, List<Coordinate> actualZ_profile) {
        if (expectedZ_profile.size() != actualZ_profile.size()){
            assertEquals(expectedZ_profile.size(), actualZ_profile.size(), "Expected zprofil count is different than actual zprofil count.");
        }
        for (int i = 0; i < actualZ_profile.size(); i++) {
            assertEquals(expectedZ_profile.get(i).x, actualZ_profile.get(i).x, DELTA_COORDS, String.format(Locale.ROOT, "Coord X point %d", i));
            assertEquals(expectedZ_profile.get(i).y, actualZ_profile.get(i).y, DELTA_COORDS, String.format(Locale.ROOT, "Coord Y point %d", i));
        }
    }
}
