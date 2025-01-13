/**
 * NoiseModelling is an open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by the DECIDE team from the Lab-STICC (CNRS) and by the Mixt Research Unit in Environmental Acoustics (Université Gustave Eiffel).
 * <http://noise-planet.org/noisemodelling.html>
 *
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 *
 * Contact: contact@noise-planet.org
 *
 */

/**
 * @Author Pierre Aumond, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.wps.NoiseModelling

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import groovy.time.TimeCategory
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.api.EmptyProgressVisitor
import org.h2gis.api.ProgressVisitor
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.SpatialResultSet
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Geometry
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossos
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossosParameters
import org.noise_planet.noisemodelling.emission.road.cnossosvar.RoadVehicleCnossosvar
import org.noise_planet.noisemodelling.emission.road.cnossosvar.RoadVehicleCnossosvarParameters
import org.noise_planet.noisemodelling.pathfinder.*
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder
import org.noise_planet.noisemodelling.pathfinder.path.Scene
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor
import org.noise_planet.noisemodelling.propagation.*
import org.noise_planet.noisemodelling.jdbc.*


import java.sql.Connection
import java.sql.SQLException

title = 'Road traffic probabilistic modeling'
description = '&#10145;&#65039; Compute road traffic probabilistic modeling</br>' +
              '<hr>' +
              'This approach is described in Aumond, P., Jacquesson, L., & Can, A. (2018). <a href="https://www.sciencedirect.com/science/article/pii/S0003682X17311283" target="_blank">Probabilistic modeling framework for multisource sound mapping</a>. Applied Acoustics, 139, 34-43 .</br></br>' +
              'The user can indicate the number of iterations he wants the model to calculate.</br> </br>' +
              '&#x2705; The first output table is called: <b>L_PROBA_GEOM </b> and contain the following columns:' +
              '<ul>' +
              '<li> <b> I </b>: The i iteration (INTEGER)</li>' +
              '<li> <b> IDRECEIVER </b>: an identifier (INTEGER, PRIMARY KEY)</li>' +
              '<li> <b> THE_GEOM </b>: the 3D geometry of the receivers (POINT)</li> ' +
              '<li> <b> Hz63, Hz125, Hz250, Hz500, Hz1000,Hz2000, Hz4000, Hz8000 </b>: 8 columns giving the day emission sound level for each octave band (FLOAT)</li></ul>'

inputs = [
        tableBuilding     : [
                name       : 'Buildings table name', 
                title: 'Buildings table name',
                description: '<b>Name of the Buildings table.</b>  </br>  ' +
                                     '<br>  The table shall contain : </br>' +
                                     '- <b> THE_GEOM </b> : the 2D geometry of the building (POLYGON or MULTIPOLYGON). </br>' +
                                     '- <b> HEIGHT </b> : the height of the building (FLOAT)',
                type       : String.class],
        tableRoads    : [
                name: 'Roads table name', 
                title: 'Roads table name', 
                description: "<b>Name of the Roads table.</b> </br> <br>" +
                    "The table shall contain: </br>" +
                    "- <b> PK </b> : an identifier. It shall be a primary key (INTEGER, PRIMARY KEY)<br/>" +
                    "- <b> TV_D </b> : Hourly average light and heavy vehicle count (DOUBLE)<br/>" +
                    "- <b> HV_D </b> :  Hourly average heavy vehicle count (DOUBLE)<br/>" +
                    "- <b> LV_SPD_D </b> :  Hourly average light vehicle speed (DOUBLE)<br/>" +
                    "- <b> HV_SPD_D </b> :  Hourly average heavy vehicle speed  (DOUBLE)<br/>" +
                    "- <b> PVMT </b> :  CNOSSOS road pavement identifier (ex: NL05) (VARCHAR)" +
                    "</br> </br> <b> This table can be generated from the WPS Block 'Import_OSM'. </b>.", 
                type: String.class],
        tableReceivers    : [
                name       : 'Receivers table name', 
                title: 'Receivers table name',
                description: '<b>Name of the Receivers table.</b></br>  ' +
                                     '</br>  The table shall contain : </br> ' +
                                     '- <b> PK </b> : an identifier. It shall be a primary key (INTEGER, PRIMARY KEY). </br> ' +
                                     '- <b> THE_GEOM </b> : the 3D geometry of the sources (POINT, MULTIPOINT).</br> ' +
                                     '</br> </br> <b> This table can be generated from the WPS Blocks in the "Receivers" folder. </b>',
                type       : String.class],
        tableDEM          : [
                name       : 'DEM table name', 
                title: 'DEM table name',
                description: '<b>Name of the Digital Elevation Model table.</b></br>' +
                                     'The table shall contain : </br> ' +
                                     '- <b> THE_GEOM </b> : the 3D geometry of the sources (POINT, MULTIPOINT).</br> </br> ' +
                                     '<b> This table can be generated from the WPS Block "Import_Asc_File". </b>',
                min        : 0, max: 1, 
                type: String.class],
        tableGroundAbs    : [
                name       : 'Ground absorption table name', 
                title: 'Ground absorption table name',
                description: '<b>Name of the surface/ground acoustic absorption table.</b></br>  ' +
                                     '</br>The table shall contain : </br> ' +
                                     '- <b> THE_GEOM </b> : the 2D geometry of the sources (POLYGON or MULTIPOLYGON).</br> ' +
                                     '- <b> G </b> : the acoustic absorption of a ground (FLOAT between 0 : very hard and 1 : very soft).</br> ',
                min        : 0, max: 1, 
                type: String.class],
        paramWallAlpha    : [
                name       : 'wallAlpha', 
                title: 'Wall absorption coefficient',
                description: 'Wall absorption coefficient (FLOAT between 0 : fully absorbent and strictly less than 1 : fully reflective)</br> </br>' +
                             '&#128736; Default value : <b>0.1 </b> ',
                min        : 0, max: 1, 
                type: String.class],
        confReflOrder     : [
                name       : 'Order of reflexion', 
                title: 'Order of reflexion',
                description: 'Maximum number of reflections to be taken into account (INTEGER) </br> </br>' +
                             '&#128736; Default value: <b>1 </b>',
                min        : 0, max: 1, 
                type: String.class],
        confMaxSrcDist    : [
                name       : 'Maximum source-receiver distance', 
                title: 'Maximum source-receiver distance',
                description: 'Maximum distance between source and receiver (in meters) (FLOAT)</br> </br>' +
                             '&#128736; Default value: <b>150 </b>',
                min        : 0, max: 1, 
                type: String.class],
        confMaxReflDist   : [
                name       : 'Maximum source-reflexion distance', 
                title:       'Maximum source-reflexion distance',
                description: 'Maximum reflection distance from the source (in meters) (FLOAT)</br> </br>' +
                                     '&#128736; Default value: <b>50 </b>',
                min        : 0, max: 1, 
                type: String.class],
        confThreadNumber  : [
                name       : 'Thread number', 
                title: 'Thread number',
                description: 'Number of thread to use on the computer (INTEGER).' +
                                     '</br> To set this value, look at the number of cores you have.' +
                                     '</br> If it is set to 0, use the maximum number of cores available.</br> </br>' +
                                     '&#128736; Default value: <b>1 </b>',
                min        : 0, max: 1, 
                type: String.class],
        confDiffVertical  : [
                name       : 'Diffraction on vertical edges', 
                title: 'Diffraction on vertical edges',
                description: 'Compute or not the diffraction on vertical edges.</br> </br>' +
                              '&#128736; Default value: <b>false </b>',
                min        : 0, max: 1, 
                type: Boolean.class],
        confDiffHorizontal: [
                name       : 'Diffraction on horizontal edges', 
                title: 'Diffraction on horizontal edges',
                description: 'Compute or not the diffraction on horizontal edges.</br> </br>' +
                              '&#128736; Default value: <b>false </b>',
                min        : 0, max: 1, 
                type: Boolean.class],
        nIterations       : [
                name       : 'Iteration number', 
                title: 'Iteration number',
                description: 'Number of the iterations to compute (INTEGER).</br> </br>' +
                             '&#128736; Default value : <b>100 </b>',
                min        : 0, max: 1, 
                type: Integer.class],
]

outputs = [
        result: [
                name       : 'Result output string',
                title      : 'Result output string',
                description: 'This type of result does not allow the blocks to be linked together.',
                type       : String.class
        ]
]

// Open Connection to Geoserver
static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

// run the script
def run(input) {

    // Get name of the database
    // by default an embedded h2gis database is created
    // Advanced user can replace this database for a postGis or h2Gis server database.
    String dbName = "h2gisdb"

    // Open connection
    openGeoserverDataStoreConnection(dbName).withCloseable {
        Connection connection ->
            return [result: exec(connection, input)]
    }
}


// main function of the script
def exec(Connection connection, input) {

    //Need to change the ConnectionWrapper to WpsConnectionWrapper to work under postGIS database
    connection = new ConnectionWrapper(connection)

    // Open sql connection to communicate with the database
    Sql sql = new Sql(connection)

    // output string, the information given back to the user
    String resultString = null

    // print to command window
    System.out.println('Start : Traffic Probabilistic Modelling')
    def start = new Date()

    // -------------------
    // Get every inputs
    // -------------------

    int nIterations = 300
    if (input['nIterations']) {
        nIterations = Integer.valueOf(input['nIterations'] as String)
    }

    String sources_table_name = input['tableRoads']
    // do it case-insensitive
    sources_table_name = sources_table_name.toUpperCase()
    // Check if srid are in metric projection.
    int sridSources = GeometryTableUtilities.getSRID(connection, TableLocation.parse(sources_table_name))
    if (sridSources == 3785 || sridSources == 4326) throw new IllegalArgumentException("Error : Please use a metric projection for "+sources_table_name+".")
    if (sridSources == 0) throw new IllegalArgumentException("Error : The table "+sources_table_name+" does not have an associated SRID.")


    String receivers_table_name = input['tableReceivers']
    // do it case-insensitive
    receivers_table_name = receivers_table_name.toUpperCase()
    //Get the geometry field of the receiver table
    TableLocation receiverTableIdentifier = TableLocation.parse(receivers_table_name)
    List<String> geomFieldsRcv = GeometryTableUtilities.getGeometryColumnNames(connection, receiverTableIdentifier)
    if (geomFieldsRcv.isEmpty()) {
        throw new SQLException(String.format("The table %s does not exists or does not contain a geometry field", receiverTableIdentifier))
    }
    // Check if srid are in metric projection and are all the same.
    int sridReceivers = GeometryTableUtilities.getSRID(connection, TableLocation.parse(receivers_table_name))
    if (sridReceivers == 3785 || sridReceivers == 4326) throw new IllegalArgumentException("Error : Please use a metric projection for "+receivers_table_name+".")
    if (sridReceivers == 0) throw new IllegalArgumentException("Error : The table "+receivers_table_name+" does not have an associated SRID.")
    if (sridReceivers != sridSources) throw new IllegalArgumentException("Error : The SRID of table "+sources_table_name+" and "+receivers_table_name+" are not the same.")


    String building_table_name = input['tableBuilding']
    // do it case-insensitive
    building_table_name = building_table_name.toUpperCase()
    // Check if srid are in metric projection and are all the same.
    int sridBuildings = GeometryTableUtilities.getSRID(connection, TableLocation.parse(building_table_name))
    if (sridBuildings == 3785 || sridReceivers == 4326) throw new IllegalArgumentException("Error : Please use a metric projection for "+building_table_name+".")
    if (sridBuildings == 0) throw new IllegalArgumentException("Error : The table "+building_table_name+" does not have an associated SRID.")
    if (sridReceivers != sridBuildings) throw new IllegalArgumentException("Error : The SRID of table "+building_table_name+" and "+receivers_table_name+" are not the same.")

    String dem_table_name = ""
    if (input['tableDEM']) {
        dem_table_name = input['tableDEM']
        // do it case-insensitive
        dem_table_name = dem_table_name.toUpperCase()
        // Check if srid are in metric projection and are all the same.
        int sridDEM = GeometryTableUtilities.getSRID(connection, TableLocation.parse(dem_table_name))
        if (sridDEM == 3785 || sridReceivers == 4326) throw new IllegalArgumentException("Error : Please use a metric projection for "+dem_table_name+".")
        if (sridDEM == 0) throw new IllegalArgumentException("Error : The table "+dem_table_name+" does not have an associated SRID.")
        if (sridDEM != sridSources) throw new IllegalArgumentException("Error : The SRID of table "+sources_table_name+" and "+dem_table_name+" are not the same.")
    }


    String ground_table_name = ""
    if (input['tableGroundAbs']) {
        ground_table_name = input['tableGroundAbs']
        // do it case-insensitive
        ground_table_name = ground_table_name.toUpperCase()
        // Check if srid are in metric projection and are all the same.
        int sridGROUND = GeometryTableUtilities.getSRID(connection, TableLocation.parse(ground_table_name))
        if (sridGROUND == 3785 || sridReceivers == 4326) throw new IllegalArgumentException("Error : Please use a metric projection for "+ground_table_name+".")
        if (sridGROUND == 0) throw new IllegalArgumentException("Error : The table "+ground_table_name+" does not have an associated SRID.")
        if (sridGROUND != sridSources) throw new IllegalArgumentException("Error : The SRID of table "+ground_table_name+" and "+sources_table_name+" are not the same.")
    }

    int reflexion_order = 0
    if (input['confReflOrder']) {
        reflexion_order = Integer.valueOf(input['confReflOrder'])
    }

    double max_src_dist = 150
    if (input['confMaxSrcDist']) {
        max_src_dist = Double.valueOf(input['confMaxSrcDist'])
    }

    double max_ref_dist = 50
    if (input['confMaxReflDist']) {
        max_ref_dist = Double.valueOf(input['confMaxReflDist'])
    }

    double wall_alpha = 0.1
    if (input['paramWallAlpha']) {
        wall_alpha = Double.valueOf(input['paramWallAlpha'])
    }

    int n_thread = 1
    if (input['confThreadNumber']) {
        n_thread = Integer.valueOf(input['confThreadNumber'])
    }

    boolean compute_vertical_diffraction = false
    if (input['confDiffVertical']) {
        compute_vertical_diffraction = input['confDiffVertical']
    }

    boolean compute_horizontal_diffraction = false
    if (input['confDiffHorizontal']) {
        compute_horizontal_diffraction = input['confDiffHorizontal']
    }


    // -------------------------
    // Initialize some variables
    // -------------------------

    // Attenuation matrix table
    List<Attenuation.SourceReceiverAttenuation> allLevels = new ArrayList<>()
    // Set of already processed receivers
    Set<Long> receivers = new HashSet<>()


    // --------------------------------------------
    // Initialize NoiseModelling propagation part
    // --------------------------------------------

    NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker(building_table_name, sources_table_name, receivers_table_name)
    noiseMapByReceiverMaker.setComputeHorizontalDiffraction(compute_horizontal_diffraction)
    noiseMapByReceiverMaker.setComputeVerticalDiffraction(compute_vertical_diffraction)
    noiseMapByReceiverMaker.setSoundReflectionOrder(reflexion_order)
    // Building height field name
    noiseMapByReceiverMaker.setHeightField("HEIGHT")
    // Import table with Snow, Forest, Grass, Pasture field polygons. Attribute G is associated with each polygon
    if (ground_table_name != "") {
        noiseMapByReceiverMaker.setSoilTableName(ground_table_name)
    }
    // Point cloud height above sea level POINT(X Y Z)
    if (dem_table_name != "") {
        noiseMapByReceiverMaker.setDemTable(dem_table_name)
    }

    noiseMapByReceiverMaker.setMaximumPropagationDistance(max_src_dist)
    noiseMapByReceiverMaker.setMaximumReflectionDistance(max_ref_dist)
    noiseMapByReceiverMaker.setWallAbsorption(wall_alpha)
    noiseMapByReceiverMaker.setThreadCount(n_thread)

    // Do not propagate for low emission or far away sources
    // Maximum error in dB
    noiseMapByReceiverMaker.setMaximumError(0.0d)
    // Init Map
    noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor())


    // --------------------------------------------
    // Initialize NoiseModelling emission part
    // --------------------------------------------

    WpsPropagationProcessDataProbaFactory wpsPropagationProcessDataProbaFactory =  new WpsPropagationProcessDataProbaFactory()
    noiseMapByReceiverMaker.setPropagationProcessDataFactory(wpsPropagationProcessDataProbaFactory)

    // --------------------------------------------
    // Run Calculations
    // --------------------------------------------

    // Init ProgressLogger (loading bar)
    RootProgressVisitor progressLogger = new RootProgressVisitor(1, true, 1)
    ProgressVisitor progressVisitor = progressLogger.subProcess(noiseMapByReceiverMaker.getGridDim() * noiseMapByReceiverMaker.getGridDim())
    int fullGridSize = noiseMapByReceiverMaker.getGridDim() * noiseMapByReceiverMaker.getGridDim()

    System.println("Start calculation... ")
    // Iterate over computation areas
    int k=0
    for (int i = 0; i < noiseMapByReceiverMaker.getGridDim(); i++) {
        for (int j = 0; j < noiseMapByReceiverMaker.getGridDim(); j++) {
            System.println("Compute... " + 100*k++/fullGridSize + " % ")

            IComputePathsOut out = noiseMapByReceiverMaker.evaluateCell(connection, i, j, progressVisitor, receivers)

            if (out instanceof NoiseMap) {
                allLevels.addAll(((NoiseMap) out).getVerticesSoundLevel())
            }
        }
    }

    System.out.println('Intermediate  time : ' + TimeCategory.minus(new Date(), start))

    System.println("Create the random road traffic table over the number of iterations... ")

    sql.execute("set @grid_density=10;\n" +
            "create table TRAFIC_DENSITY (the_geom geometry, LV int, HV INT, SPEED double, DENSITY_LV double, DENSITY_HV double, DENSITY_TV double) as \n" +
            "    select THE_GEOM, TV_D - HV_D, HV_D , \n" +
            "        case when LV_SPD_D  < 20 then 20 \n" +
            "            else LV_SPD_D  end, \n" +
            "        case when LV_SPD_D  < 20 then 0.001*(TV_D-HV_D)/20 \n" +
            "            else 0.001*(TV_D-HV_D)/LV_SPD_D end, \n" +
            "        case when HV_SPD_D < 20 then 0.001*HV_D/20 \n" +
            "            else 0.001*HV_D/HV_SPD_D  end, \n" +
            "        case when LV_SPD_D< 20 then 0.001*(TV_D)/20 \n" +
            "            else 0.001*(TV_D)/LV_SPD_D end \n" +
            "        from " + sources_table_name + " WHERE (TV_D IS NOT NULL);" +
            "drop table if exists traf_explode;\n" +
            "create table traf_explode as SELECT * FROM ST_Explode('TRAFIC_DENSITY');\n" +
            "alter table traf_explode add length double as select ST_LENGTH(the_geom) ;\n" +
            "drop table grid_traf2 if exists;\n" +
            "create table grid_traf2 (the_geom geometry, SPEED int, DENSITY_LV double, DENSITY_HV double, DENSITY_TV double) as SELECT ST_Tomultipoint(ST_Densify(the_geom, 10)), SPEED, DENSITY_LV, DENSITY_HV, DENSITY_TV from traf_explode;\n" +
            "drop table grid_traf_tot if exists;\n" +
            "create table grid_traf_tot as SELECT * from  ST_explode('grid_traf2');\n" +
            "alter table grid_traf_tot ADD number_veh double as select DENSITY_TV;\n" +
            "drop table grid_traf2 if exists;\n" +
            "drop table CARS2D if exists;\n" +
            "create table CARS2D as SELECT ST_FORCE2D(the_geom) the_geom, speed, density_LV, density_HV, density_TV, explod_id exp_id, number_veh from grid_traf_tot WHERE DENSITY_TV > 0 and DENSITY_TV IS NOT NULL;\n" +
            "alter table CARS2D add column PK serial ;\n" +
            "drop table ROADS_PROBA if exists;\n" +
            "create table ROADS_PROBA as SELECT ST_UPDATEZ(ST_FORCE3D(the_geom),0.05,1) the_geom,ST_Z(ST_AddZ(ST_FORCE3D(the_geom),0.05)) z, speed, density_LV LV, density_HV HV, DENSITY_TV TV, exp_id, number_veh from ST_Explode('CARS2D');\n" +
            "alter table ROADS_PROBA add column PK serial ;\n" +
            "drop table grid_traf_tot if exists;" +
            "drop table CARS2D if exists;" +
            "drop table grid_traf2 if exists;" +
            "drop table if exists traf_explode;" +
            "drop table TRAFIC_DENSITY if exists;")


    ProbabilisticProcessData probabilisticProcessData = new ProbabilisticProcessData();
    probabilisticProcessData.setProbaTable("ROADS_PROBA", sql)

    sql.execute("drop table ROADS_PROBA if exists;")

    System.out.println('Intermediate  time : ' + TimeCategory.minus(new Date(), start))
    System.out.println("Export data to table")

    sql.execute("drop table if exists L_PROBA;")
    sql.execute("create table L_PROBA(IT integer, IDRECEIVER integer, Hz63 double precision, Hz125 double precision, Hz250 double precision, Hz500 double precision, Hz1000 double precision, Hz2000 double precision, Hz4000 double precision, Hz8000 double precision);")
    def qry = 'INSERT INTO L_PROBA(IT , IDRECEIVER,Hz63, Hz125, Hz250, Hz500, Hz1000,Hz2000, Hz4000, Hz8000) VALUES (?,?,?,?,?,?,?,?,?,?);'

    k = 0
    for (int it = 1; it < nIterations; it++) {
        // Iterate over attenuation matrix
        Map<Integer, double[]> soundLevels = new HashMap<>()
        Map<Integer, double[]> sourceLev = new HashMap<>()

        for (int i = 0; i < allLevels.size(); i++) {
            k++

            // get attenuation matrix value
            double[] soundLevel = allLevels.get(i).value

            //get id from receiver and sound sources
            int idReceiver = (Integer) allLevels.get(i).receiverId
            int idSource = (Integer) allLevels.get(i).sourceId

            // get source SoundLevel
            if (!sourceLev.containsKey(idSource)) {
                double[] carsLevel = probabilisticProcessData.getCarsLevel(idSource)
                sourceLev.put(idSource, carsLevel)
            }

            if (sourceLev.get(idSource)[0] > 0) {
                // if any attenuation matrix value is set to NaN
                if (!Double.isNaN(soundLevel[0]) && !Double.isNaN(soundLevel[1])
                        && !Double.isNaN(soundLevel[2]) && !Double.isNaN(soundLevel[3])
                        && !Double.isNaN(soundLevel[4]) && !Double.isNaN(soundLevel[5])
                        && !Double.isNaN(soundLevel[6]) && !Double.isNaN(soundLevel[7])) {

                    if (soundLevels.containsKey(idReceiver)) {
                        // add Leq value to the pre-existing sound level on this receiver
                        double[] sumArray = tools.invokeMethod("sumArraySR", [soundLevel, sourceLev.get(idSource)])
                        soundLevel = ComputeRays.sumDbArray(sumArray, soundLevels.get(idReceiver))
                        soundLevels.replace(idReceiver, soundLevel)
                    } else {
                        // apply A ponderation
                        //soundLevel = DBToDBA(soundLevel)
                        // add a new Leq value on this receiver
                        double[] sumArray =  tools.invokeMethod("sumArraySR", [soundLevel, sourceLev.get(idSource)])
                        soundLevels.put(idReceiver, sumArray)
                    }
                }
            }
        }

        sql.withBatch(100, qry) { ps ->
            for (Map.Entry<Integer, double[]> s : soundLevels.entrySet()) {

                ps.addBatch(it as Integer, s.key as Integer,
                        s.value[0] as Double, s.value[1] as Double, s.value[2] as Double,
                        s.value[3] as Double, s.value[4] as Double, s.value[5] as Double,
                        s.value[6] as Double, s.value[7] as Double)

            }
        }
    }


    // Drop table LDEN_GEOM if exists
    sql.execute("drop table if exists L_PROBA_GEOM;")
    // Associate Geometry column to the table LDEN
    sql.execute("CREATE INDEX ON L_PROBA(IDRECEIVER);")
    sql.execute("CREATE INDEX ON RECEIVERS(PK);")
    sql.execute("create table L_PROBA_GEOM  as select a.IT,a.IDRECEIVER, b.THE_GEOM, a.Hz63, a.Hz125, a.Hz250, a.Hz500, a.Hz1000, a.Hz2000, a.Hz4000, a.Hz8000  FROM L_PROBA a LEFT JOIN  RECEIVERS b  ON a.IDRECEIVER = b.PK;")


    resultString = "Calculation Done ! The table L_PROBA_GEOM has been created."

    // print to command window
    System.out.println('Result : ' + resultString)
    System.out.println('End : Traffic Probabilistic Modelling')
    System.out.println('Duration : ' + TimeCategory.minus(new Date(), start))

    // print to WPS Builder
    return resultString
}






/**
 * Read source database and compute the sound emission spectrum of roads sources
 * */
class WpsPropagationProcessDataProba extends Scene {
    static List<Integer> freq_lvl = Arrays.asList(asOctaveBands(DEFAULT_FREQUENCIES_THIRD_OCTAVE))

    // Lden values
    public List<double[]> wjSourcesD = new ArrayList<>()
    public List<double[]> wjSourcesE = new ArrayList<>()
    public List<double[]> wjSourcesN = new ArrayList<>()
    public List<double[]> wjSourcesDEN = new ArrayList<>()

    public Map<Long, Integer> SourcesPk = new HashMap<>()

    public String inputFormat = "Proba"
    int idSource = 0

    WpsPropagationProcessDataProba(ProfileBuilder profileBuilder) {
        super(profileBuilder)
    }

    @Override
    void addSource(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException, IOException {
        super.addSource(pk, geom, rs)
        SourcesPk.put(pk, idSource++)

        def res = computeLw(inputFormat, rs)
        wjSourcesD.add(res[0])
        wjSourcesE.add(res[1])
        wjSourcesN.add(res[2])
        wjSourcesDEN.add(res[3])
    }

    double[][] computeLw(String Format, SpatialResultSet rs) throws SQLException {

        // Compute day average level
        double[] ld = new double[freq_lvl.size()]
        double[] le = new double[freq_lvl.size()]
        double[] ln = new double[freq_lvl.size()]
        double[] lden = new double[freq_lvl.size()]

        if (Format == 'Proba') {
            double val = ComputeRays.dbaToW((BigDecimal) 90.0)
            ld = [val,val,val,val,val,val,val,val]
            le = [val,val,val,val,val,val,val,val]
            ln = [val,val,val,val,val,val,val,val]
        }

        if (Format == 'EmissionDEN') {
            // Read average 24h traffic
            ld = [ComputeRays.dbaToW(rs.getDouble('LWD63')),
                  ComputeRays.dbaToW(rs.getDouble('LWD125')),
                  ComputeRays.dbaToW(rs.getDouble('LWD250')),
                  ComputeRays.dbaToW(rs.getDouble('LWD500')),
                  ComputeRays.dbaToW(rs.getDouble('LWD1000')),
                  ComputeRays.dbaToW(rs.getDouble('LWD2000')),
                  ComputeRays.dbaToW(rs.getDouble('LWD4000')),
                  ComputeRays.dbaToW(rs.getDouble('LWD8000'))]

            le = [ComputeRays.dbaToW(rs.getDouble('LWE63')),
                  ComputeRays.dbaToW(rs.getDouble('LWE125')),
                  ComputeRays.dbaToW(rs.getDouble('LWE250')),
                  ComputeRays.dbaToW(rs.getDouble('LWE500')),
                  ComputeRays.dbaToW(rs.getDouble('LWE1000')),
                  ComputeRays.dbaToW(rs.getDouble('LWE2000')),
                  ComputeRays.dbaToW(rs.getDouble('LWE4000')),
                  ComputeRays.dbaToW(rs.getDouble('LWE8000'))]

            ln = [ComputeRays.dbaToW(rs.getDouble('LWN63')),
                  ComputeRays.dbaToW(rs.getDouble('LWN125')),
                  ComputeRays.dbaToW(rs.getDouble('LWN250')),
                  ComputeRays.dbaToW(rs.getDouble('LWN500')),
                  ComputeRays.dbaToW(rs.getDouble('LWN1000')),
                  ComputeRays.dbaToW(rs.getDouble('LWN2000')),
                  ComputeRays.dbaToW(rs.getDouble('LWN4000')),
                  ComputeRays.dbaToW(rs.getDouble('LWN8000'))]
        }
        if (Format == 'Classic') {
            // Get input traffic data
            double tvD = rs.getDouble("TV_D")
            double tvE = rs.getDouble("TV_E")
            double tvN = rs.getDouble("TV_N")

            double hvD = rs.getDouble("HV_D")
            double hvE = rs.getDouble("HV_E")
            double hvN = rs.getDouble("HV_N")

            double lvSpeedD = rs.getDouble("LV_SPD_D")
            double lvSpeedE = rs.getDouble("LV_SPD_E")
            double lvSpeedN = rs.getDouble("LV_SPD_N")

            double hvSpeedD = rs.getDouble("HV_SPD_D")
            double hvSpeedE = rs.getDouble("HV_SPD_E")
            double hvSpeedN = rs.getDouble("HV_SPD_N")

            String pavement = rs.getString("PVMT")

            // this options can be activated if needed
            double Temperature = 20.0d
            double Ts_stud = 0
            double Pm_stud = 0
            double Junc_dist = 300
            int Junc_type = 0

            // Day
            int idFreq = 0
            for (int freq : freq_lvl) {
                RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(lvSpeedD, hvSpeedD, hvSpeedD, lvSpeedD,
                        lvSpeedD, Math.max(0, tvD - hvD), 0, hvD, 0, 0, freq, Temperature,
                        pavement, Ts_stud, Pm_stud, Junc_dist, Junc_type)
                ld[idFreq++] += RoadCnossos.evaluate(rsParametersCnossos)
            }

            // Evening
            idFreq = 0
            for (int freq : freq_lvl) {
                RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(lvSpeedE, hvSpeedE, hvSpeedE, lvSpeedE,
                        lvSpeedE, Math.max(0, tvE - hvE), 0, hvE, 0, 0, freq, Temperature,
                        pavement, Ts_stud, Pm_stud, Junc_dist, Junc_type)
                le[idFreq++] += RoadCnossos.evaluate(rsParametersCnossos)
            }

            // Night
            idFreq = 0
            for (int freq : freq_lvl) {
                RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(lvSpeedN, hvSpeedN, hvSpeedN, lvSpeedN,
                        lvSpeedN, Math.max(0, tvN - hvN), 0, hvN, 0, 0, freq, Temperature,
                        pavement, Ts_stud, Pm_stud, Junc_dist, Junc_type)
                ln[idFreq++] += RoadCnossos.evaluate(rsParametersCnossos)
            }


        }

        if (Format == "AADF") {
            String AAFD_FIELD_NAME = "AADF"

            // Annual Average Daily Flow (AADF) estimates
            String ROAD_CATEGORY_FIELD_NAME = "CLAS_ADM"
            def lv_hourly_distribution = [0.56, 0.3, 0.21, 0.26, 0.69, 1.8, 4.29, 7.56, 7.09, 5.5, 4.96, 5.04,
                                          5.8, 6.08, 6.23, 6.67, 7.84, 8.01, 7.12, 5.44, 3.45, 2.26, 1.72, 1.12];
            def hv_hourly_distribution = [1.01, 0.97, 1.06, 1.39, 2.05, 3.18, 4.77, 6.33, 6.72, 7.32, 7.37, 7.4,
                                          6.16, 6.22, 6.84, 6.74, 6.23, 4.88, 3.79, 3.05, 2.36, 1.76, 1.34, 1.07];

            int LDAY_START_HOUR = 6
            int LDAY_STOP_HOUR = 18
            int LEVENING_STOP_HOUR = 22
            int[] nightHours = [22, 23, 0, 1, 2, 3, 4, 5]
            double HV_PERCENTAGE = 0.1

            int idSource = 0

            idSource = idSource + 1
            // Read average 24h traffic
            double tmja = rs.getDouble(AAFD_FIELD_NAME)

            //130 km/h 1:Autoroute
            //80 km/h  2:Nationale
            //50 km/h  3:Départementale
            //50 km/h  4:Voirie CUN
            //50 km/h  5:Inconnu
            //50 km/h  6:Privée
            //50 km/h  7:Communale
            int road_cat = rs.getInt(ROAD_CATEGORY_FIELD_NAME)

            int roadType;
            if (road_cat == 1) {
                roadType = 10;
            } else {
                if (road_cat == 2) {
                    roadType = 42;
                } else {
                    roadType = 62;
                }
            }
            double speed_lv = 50;
            if (road_cat == 1) {
                speed_lv = 120;
            } else {
                if (road_cat == 2) {
                    speed_lv = 80;
                }
            }

            /**
             * Vehicles category Table 3 P.31 CNOSSOS_EU_JRC_REFERENCE_REPORT
             * lv : Passenger cars, delivery vans ≤ 3.5 tons, SUVs , MPVs including trailers and caravans
             * mv: Medium heavy vehicles, delivery vans > 3.5 tons,  buses, touring cars, etc. with two axles and twin tyre mounting on rear axle
             * hgv: Heavy duty vehicles, touring cars, buses, with three or more axles
             * wav:  mopeds, tricycles or quads ≤ 50 cc
             * wbv:  motorcycles, tricycles or quads > 50 cc
             * @param lv_speed Average light vehicle speed
             * @param mv_speed Average medium vehicle speed
             * @param hgv_speed Average heavy goods vehicle speed
             * @param wav_speed Average light 2 wheels vehicle speed
             * @param wbv_speed Average heavy 2 wheels vehicle speed
             * @param lvPerHour Average light vehicle per hour
             * @param mvPerHour Average heavy vehicle per hour
             * @param hgvPerHour Average heavy vehicle per hour
             * @param wavPerHour Average heavy vehicle per hour
             * @param wbvPerHour Average heavy vehicle per hour
             * @param FreqParam Studied Frequency
             * @param Temperature Temperature (Celsius)
             * @param roadSurface roadSurface empty default, NL01 FR01 ..
             * @param Ts_stud A limited period Ts (in months) over the year where a average proportion pm of light vehicles are equipped with studded tyres and during .
             * @param Pm_stud Average proportion of vehicles equipped with studded tyres
             * @param Junc_dist Distance to junction
             * @param Junc_type Type of junction ((k = 1 for a crossing with traffic lights ; k = 2 for a roundabout)
             */

            double lvPerHour = 0;
            double mvPerHour = 0;
            double hgvPerHour = 0;
            double wavPerHour = 0;
            double wbvPerHour = 0;
            double Temperature = 20.0d;
            String roadSurface = "FR_R2";
            double Ts_stud = 0.5;
            double Pm_stud = 4;
            double Junc_dist = 0;
            int Junc_type = 0;
            double slopePercentage = 0;
            double speedLv = speed_lv;
            double speedMv = speed_lv;
            double speedHgv = speed_lv;
            double speedWav = speed_lv;
            double speedWbv = speed_lv;

            for (int h = LDAY_START_HOUR; h < LDAY_STOP_HOUR; h++) {
                lvPerHour = tmja * (1 - HV_PERCENTAGE) * (lv_hourly_distribution[h] / 100.0);
                hgvPerHour = tmja * HV_PERCENTAGE * (hv_hourly_distribution[h] / 100.0);
                int idFreq = 0;
                for (int freq : freq_lvl) {
                    RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(speedLv, speedMv, speedHgv, speedWav,
                            speedWbv, lvPerHour, mvPerHour, hgvPerHour, wavPerHour, wbvPerHour, freq, Temperature,
                            roadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
                    rsParametersCnossos.setSpeedFromRoadCaracteristics(speed_lv, speed_lv, false, speed_lv, roadType);
                    ld[idFreq++] += RoadCnossos.evaluate(rsParametersCnossos)
                }
            }
            // Average
            for (int i = 0; i < ld.length; i++) {
                ld[i] = ld[i] / (LDAY_STOP_HOUR - LDAY_START_HOUR);
            }

            // Evening
            for (int h = LDAY_STOP_HOUR; h < LEVENING_STOP_HOUR; h++) {
                lvPerHour = tmja * (1 - HV_PERCENTAGE) * (lv_hourly_distribution[h] / 100.0)
                mvPerHour = tmja * HV_PERCENTAGE * (hv_hourly_distribution[h] / 100.0)
                int idFreq = 0
                for (int freq : freq_lvl) {
                    RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(speedLv, speedMv, speedHgv, speedWav,
                            speedWbv, lvPerHour, mvPerHour, hgvPerHour, wavPerHour, wbvPerHour, freq, Temperature,
                            roadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
                    rsParametersCnossos.setSpeedFromRoadCaracteristics(speed_lv, speed_lv, false, speed_lv, roadType)
                    le[idFreq++] += RoadCnossos.evaluate(rsParametersCnossos)
                }
            }

            for (int i = 0; i < le.size(); i++) {
                le[i] = (le[i] / (LEVENING_STOP_HOUR - LDAY_STOP_HOUR))
            }

            // Night
            for (int h : nightHours) {
                lvPerHour = tmja * (1 - HV_PERCENTAGE) * (lv_hourly_distribution[h] / 100.0)
                mvPerHour = tmja * HV_PERCENTAGE * (hv_hourly_distribution[h] / 100.0)
                int idFreq = 0
                for (int freq : freq_lvl) {
                    RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(speedLv, speedMv, speedHgv, speedWav,
                            speedWbv, lvPerHour, mvPerHour, hgvPerHour, wavPerHour, wbvPerHour, freq, Temperature,
                            roadSurface, Ts_stud, Pm_stud, Junc_dist, Junc_type);
                    rsParametersCnossos.setSpeedFromRoadCaracteristics(speed_lv, speed_lv, false, speed_lv, roadType)
                    ln[idFreq++] += RoadCnossos.evaluate(rsParametersCnossos)
                }
            }
            for (int i = 0; i < ln.size(); i++) {
                ln[i] = (ln[i] / nightHours.length)
            }
        }

        int idFreq = 0
        // Combine day evening night sound levels
        for (int freq : freq_lvl) {
            lden[idFreq++] = (12 * ld[idFreq] + 4 * ComputeRays.dbaToW(ComputeRays.wToDba(le[idFreq]) + 5) + 8 * ComputeRays.dbaToW(ComputeRays.wToDba(ln[idFreq]) + 10)) / 24.0
        }

        return [ld, le, ln, lden]
    }

}

class WpsPropagationProcessDataProbaFactory implements NoiseMapByReceiverMaker.PropagationProcessDataFactory {

    @Override
    Scene create(ProfileBuilder builder) {
        return new WpsPropagationProcessDataProba(builder)
    }

    @Override
    void initialize(Connection connection, NoiseMapByReceiverMaker noiseMapByReceiverMaker) throws SQLException {

    }
}


/**
 *
 */
class ProbabilisticProcessData {

    Map<Integer, Double> SPEED_LV = new HashMap<>()
    Map<Integer, Double> SPEED_HV = new HashMap<>()
    Map<Integer, Double> LV = new HashMap<>()
    Map<Integer, Double> HV = new HashMap<>()

    double[] getCarsLevel(int idSource) throws SQLException {
        double[] res_d = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
        double[] res_LV = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
        double[] res_HV = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
        def list = [63, 125, 250, 500, 1000, 2000, 4000, 8000]
        // memes valeurs d e et n


        def random = Math.random()
        if (random < LV.get(idSource)) {
            int kk = 0
            for (f in list) {

                double speed = SPEED_LV.get(idSource)
                int acc = 0
                int FreqParam = f
                double Temperature = 20
                String RoadSurface = "DEF"
                boolean Stud = true
                double Junc_dist = 200
                int Junc_type = 1
                String veh_type = "1"
                int acc_type = 1
                double LwStd = 1
                int VehId = 10

                RoadVehicleCnossosvarParameters rsParameters = new RoadVehicleCnossosvarParameters(speed, acc, veh_type, acc_type, FreqParam, Temperature, RoadSurface, Stud, Junc_dist, Junc_type, LwStd, VehId)
                rsParameters.setSlopePercentage(0)

                res_LV[kk] = RoadVehicleCnossosvar.evaluate(rsParameters)
                kk++
            }

        }
        if (random < HV.get(idSource)) {
            int kk = 0
            for (f in list) {
                double speed = SPEED_HV.get(idSource)
                int acc = 0
                int FreqParam = f
                double Temperature = 20
                String RoadSurface = "DEF"
                boolean Stud = true
                double Junc_dist = 200
                int Junc_type = 1
                String veh_type = "3"
                int acc_type = 1
                double LwStd = 1
                int VehId = 10

                RoadVehicleCnossosvarParameters rsParameters = new RoadVehicleCnossosvarParameters(speed, acc, veh_type, acc_type, FreqParam, Temperature, RoadSurface, Stud, Junc_dist, Junc_type, LwStd, VehId)
                rsParameters.setSlopePercentage(0)

                res_HV[kk] = RoadVehicleCnossosvar.evaluate(rsParameters)
                kk++
            }
        }
        int kk = 0
        for (f in list) {
            res_d[kk] = 10 * Math.log10(
                    (1.0 / 2.0) *
                            (Math.pow(10, (10 * Math.log10(Math.pow(10, res_LV[kk] / 10))) / 10)
                                    + Math.pow(10, (10 * Math.log10(Math.pow(10, res_HV[kk] / 10))) / 10)
                            )
            )
            kk++
        }


        return res_d
    }

    void setProbaTable(String tablename, Sql sql) {
        //////////////////////
        // Import file text
        //////////////////////

        // Remplissage des variables avec le contenu du fichier plan d'exp
        sql.eachRow('SELECT PK,  SPEED, HV,LV FROM ' + tablename + ';') { row ->
            int pk = (int) row[0]

            SPEED_HV.put(pk, (double) row[1])
            SPEED_LV.put(pk, (double) row[1])
            HV.put(pk, (double) row[2])
            LV.put(pk, (double) row[3])

        }


    }

}