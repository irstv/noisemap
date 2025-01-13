import jnius_config
import os
import time

deb = time.time()
jnius_config.add_options('-Xmx4096m')
# Please run the following command in order to get the libraries (linux)
# ./gradlew build -x test && cd build/distributions && unzip -o scriptrunner.zip
jnius_config.set_classpath('build/distributions/scriptrunner/lib/*')

from jnius import autoclass

# Imports
RoadSourceParametersCnossos = autoclass('org.noise_planet.noisemodelling.emission.RoadSourceParametersCnossos')
EvaluateRoadSourceCnossos = autoclass('org.noise_planet.noisemodelling.emission.EvaluateRoadSourceCnossos')
PathParameters = autoclass('org.noise_planet.noisemodelling.pathfinder.path.PathParameters')
RunnerMain = autoclass('org.noisemodelling.runner.Main')
LoggerFactory = autoclass('org.slf4j.LoggerFactory')
PropagationDataBuilder = autoclass('org.noise_planet.noisemodelling.pathfinder.PropagationDataBuilder')
CnossosPaths = autoclass('org.noise_planet.noisemodelling.pathfinder.cnossos.CnossosPaths')
IComputePathsOut = autoclass('org.noise_planet.noisemodelling.pathfinder.IComputePathsOut')
ProfileBuilder = autoclass('org.noise_planet.noisemodelling.pathfinder.ProfileBuilder')
ProfilerThread = autoclass('org.noise_planet.noisemodelling.pathfinder.utils.ProfilerThread')
ComputeRaysOutAttenuation = autoclass('org.noise_planet.noisemodelling.propagation.ComputeRaysOutAttenuation')
PropagationProcessPathData = autoclass('org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters')
Coordinate = autoclass('org.locationtech.jts.geom.Coordinate')
Array = autoclass('java.lang.reflect.Array')

def T02():
    lv_speed = 70
    lv_per_hour = 1000
    mv_speed = 70
    mv_per_hour = 1000
    hgv_speed = 70
    hgv_per_hour = 1000
    wav_speed = 70
    wav_per_hour = 1000
    wbv_speed = 70
    wbv_per_hour = 1000
    FreqParam = 8000
    Temperature = 15
    RoadSurface = "NL01"
    Pm_stud = 0.5
    Ts_stud = 4
    Junc_dist = 200
    Junc_type = 1
    rsParameters = RoadSourceParametersCnossos(lv_speed, mv_speed, hgv_speed, wav_speed, wbv_speed, lv_per_hour,
                                               mv_per_hour, hgv_per_hour, wav_per_hour, wbv_per_hour, FreqParam,
                                               Temperature, RoadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type)
    rsParameters.setSlopePercentage_without_limit(10)
    rsParameters.setCoeffVer(1)
    return EvaluateRoadSourceCnossos.evaluate(rsParameters)


def to_coordinate_array(coordinate_list):
    ar = Array.newInstance(Coordinate, len(coordinate_list))
    for i, v in enumerate(coordinate_list):
        ar[i] = Coordinate(v[0], v[1], v[2])
    return ar


def format_db_list(values):
    return ["%.1f" % v for v in values]

# Demo using unit test TC21
# TC21 - Building on ground with spatially varying heights and acoustic properties
def propagation_demo():
    # use default env data 15 C 70% humidity 101325 Pa (octave)
    default_environmental_data = PropagationProcessPathData()
    # profile builder is a class that help computing intersection with 3d objects such as buildings,
    # digital terrain model, ground type areas

    builder = ProfileBuilder()
    # Add building addWall or addBuilding
    # ask to use Z of polygons as the gutter (absolute roof altitude)
    builder.setzBuildings(True)
    builder.addBuilding(to_coordinate_array([[167.2, 39.5, 11.5], [151.6, 48.5, 11.5], [141.1, 30.3, 11.5],
                        [156.7, 21.3, 11.5], [159.7, 26.5, 11.5], [151.0, 31.5, 11.5], [155.5, 39.3, 11.5],
                                             [164.2, 34.3, 11.5]]))
    builder.addGroundEffect(0.0, 50.0, -20.0, 80.0, 0.9)
    builder.addGroundEffect(50.0, 150.0, -20.0, 80.0, 0.5)
    builder.addGroundEffect(150.0, 225.0, -20.0, 80.0, 0.2)
    # Insert digital elevation model as topographic lines
    builder.addTopographicLine(0, 80, 0, 225, 80, 0) \
        .addTopographicLine(225, 80, 0, 225, -20, 0) \
        .addTopographicLine(225, -20, 0, 0, -20, 0) \
        .addTopographicLine(0, -20, 0, 0, 80, 0) \
        .addTopographicLine(120, -20, 0, 120, 80, 0) \
        .addTopographicLine(185, -5, 10, 205, -5, 10) \
        .addTopographicLine(205, -5, 10, 205, 75, 10) \
        .addTopographicLine(205, 75, 10, 185, 75, 10) \
        .addTopographicLine(185, 75, 10, 185, -5, 10)

    # construct internal structures
    builder.finishFeeding()

    thread_data = PropagationDataBuilder(builder)
    thread_data.addSource(10, 10, 1)  # x, y, z
    thread_data.addReceiver(200, 25, 14)  # x, y, z
    thread_data.hEdgeDiff(True)  # Diffraction on vertical plane
    thread_data.vEdgeDiff(True)  # Diffraction on horizontal plane
    thread_data.setGs(0.9)  # Source factor absorption (default is 0 such as +3dB)
    ray_data = thread_data.build()
    ray_data.reflexionOrder = 1
    default_environmental_data.setHumidity(70)
    default_environmental_data.setTemperature(10)
    keep_rays = True
    keep_absorption = True
    prop_data_out = ComputeRaysOutAttenuation(keep_rays, keep_absorption, default_environmental_data)
    compute_rays = CnossosPaths(ray_data)
    compute_rays.setThreadCount(1)

    # Run computation
    compute_rays.run(prop_data_out)

    # Read receiver noise level
    receiver = prop_data_out.getVerticesSoundLevel().get(0)
    source_lvl = 93  # 93 dB source level (not A weighted)
    formatting = "{0:<20} " + " ".join(["{%d:>8}" % (i + 1) for i in range(len(default_environmental_data.freq_lvl))])
    l_a_top = [prop_data_out.getPropagationPaths()[0].cnossosPathsParameters.aGlobal[i] + source_lvl +
               default_environmental_data.freq_lvl_a_weighting[i]
               for i in range(len(default_environmental_data.freq_lvl))]
    l_a_right = [prop_data_out.getPropagationPaths()[1].cnossosPathsParameters.aGlobal[i] + source_lvl +
               default_environmental_data.freq_lvl_a_weighting[i]
               for i in range(len(default_environmental_data.freq_lvl))]
    l_a_left = [prop_data_out.getPropagationPaths()[2].cnossosPathsParameters.aGlobal[i] + source_lvl +
               default_environmental_data.freq_lvl_a_weighting[i]
               for i in range(len(default_environmental_data.freq_lvl))]
    print(formatting.format(*(["f in Hz"] + list(map(str, default_environmental_data.freq_lvl)))))
    print(formatting.format(*(["A atmospheric/km"] + format_db_list(prop_data_out.genericMeteoData.getAlpha_atmo()))))
    print(formatting.format(*(["A atmospheric dB"] + format_db_list(prop_data_out.getPropagationPaths()[0]
                                                                    .cnossosPathsParameters.aAtm))))
    print(formatting.format(*(["A-weighting"] + format_db_list(default_environmental_data.freq_lvl_a_weighting))))
    print(formatting.format(*(["LA in dB over top"] + format_db_list(l_a_top))))
    print(formatting.format(*(["LA in dB right"] + format_db_list(l_a_right))))
    print(formatting.format(*(["LA in dB left"] + format_db_list(l_a_left))))
    print(formatting.format(*(["LA in dB"] + format_db_list([source_lvl + receiver.value[i] + default_environmental_data.freq_lvl_a_weighting[i]
                                                      for i in range(len(default_environmental_data.freq_lvl))]))))


def main():
    # logger = LoggerFactory.getLogger("mainlog")
    # RunnerMain.printBuildIdentifiers(logger)
    source_noise_level = T02()  # source noise level in dB(A)
    propagation_demo()


if __name__ == '__main__':
    main()
    print("\nDone in %s " % (time.strftime("%H:%M:%S", time.gmtime(time.time() - deb))))
