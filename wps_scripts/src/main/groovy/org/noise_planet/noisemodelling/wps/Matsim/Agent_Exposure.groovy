/**
 * NoiseModelling is an open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by Université Gustave Eiffel and CNRS
 * <http://noise-planet.org/noisemodelling.html>
 *
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 *
 * Contact: contact@noise-planet.org
 *
 */
/**
 * @Author Valentin Le Bescond, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.wps.Matsim

import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderHeaderAware
import com.opencsv.CSVReaderHeaderAwareBuilder
import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.matsim.api.core.v01.Id
import org.matsim.api.core.v01.Scenario
import org.matsim.api.core.v01.population.*
import org.matsim.core.config.ConfigUtils
import org.matsim.core.population.io.PopulationReader
import org.matsim.core.scenario.ScenarioUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.*
import java.util.zip.GZIPInputStream

title = 'Calculate Mastim agents exposure'
description = "Loads a Matsim plans.xml file and calculate agents noise exposure, based on previously claculated timesliced noisemap at receiver positions, linked with matsim activities (facilities)"

inputs = [
        plansFile: [
                name: 'Path of the Matsim output_plans file',
                title: 'Path of the Matsim output_plans file',
                description: 'Path of the Matsim output_plans file </br> For example : /home/mastim/simulation_output/output_plans.xml.gz',
                type: String.class
        ],
        experiencedPlansFile: [
                name: 'Path of the Matsim output_experienced_plans file',
                title: 'Path of the Matsim output_experienced_plans file',
                description: 'Path of the Matsim output_plans file </br> For example : /home/mastim/simulation_output/output_experienced_plans.xml.gz',
                type: String.class
        ],
        personsCsvFile: [
                name: 'personsCsvFile',
                title: 'personsCsvFile',
                description: 'Path of the Matsim output_persons csv file </br> For example : /home/mastim/simulation_output/output_persons.csv.gz',
                min: 0,
                max: 1,
                type: String.class
        ],
        receiversTable: [
                name: 'Table containing the receivers position',
                title: 'Table containing the receivers position',
                description: 'Table containing the receivers position' +
                        '<br/>The table must contain the following fields :' +
                        '<br/>PK, FACILITY, ORIGIN_GEOM, THE_GEOM, TYPES',
                type: String.class
        ],
        dataTable: [
                name: 'Table containing the noise data',
                title: 'Table containing the noise data',
                description: 'Table containing the noise data' +
                        '<br/>The table must contain the following fields :' +
                        '<br/>PK, IDRECEIVER, THE_GEOM, HZ63, HZ125, HZ250, HZ500, HZ1000, HZ2000, HZ000, HZ8000, TIME',
                type: String.class
        ],
        timeBinSize: [
                name: 'The size of time bins in seconds.',
                title: 'The size of time bins in seconds.',
                description: 'This parameter dictates the time resolution of the resulting data ' +
                        '<br/>The time information stored will be the starting time of the time bins ' +
                        '<br/>For exemple with a timeBinSize of 3600, the data will be analysed using the following timeBins: ' +
                        '<br/>0, 3600, 7200, ..., 79200, 82800',
                type: Integer.class
        ],
        SRID : [
                name: 'Projection identifier',
                title: 'Projection identifier',
                description: 'Original projection identifier (also called SRID) of your tables.' +
                        'It should be an EPSG code, a integer with 4 or 5 digits (ex: 3857 is Web Mercator projection).' +
                        '</br><b> Default value : 4326 </b> ',
                min: 0,
                max: 1,
                type: Integer.class
        ],
        outTableName: [
                name: 'Output table name',
                title: 'Name of created table',
                description: 'Name of the table you want to create from the file.' +
                        '<br/>The table will contain the following fields :' +
                        '<br/>PK, PERSON_ID, HOME_FACILITY, HOME_GEOM, WORK_FACILITY, WORK_GEOM, LAEQ, HOME_LAEQ, DIFF_LAEQ',
                type: String.class
        ]
]

outputs = [
        result: [
                name: 'Result output string',
                title: 'Result output string',
                description: 'This type of result does not allow the blocks to be linked together.',
                type: String.class
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

    connection = new ConnectionWrapper(connection)
    Sql sql = new Sql(connection)

    String resultString

    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")
    logger.info('Start : Agent_Exposure')
    logger.info("inputs {}", input)

    String plansFile = input["plansFile"];
    String experiencedPlansFile = input["experiencedPlansFile"];
    String receiversTable = input["receiversTable"];
    String dataTable = input["dataTable"];
    String outTableName = input['outTableName'];

    int timeBinSize = 3600;
    if (input["timeBinSize"]) {
        timeBinSize = input["timeBinSize"] as int;
    }
    String SRID = "4326"
    if (input['SRID']) {
        SRID = input['SRID'];
    }

    String personsCsvFile = "";
    if (input["personsCsvFile"]) {
        personsCsvFile = input["personsCsvFile"];
    }

    File file = new File(plansFile);
    if(!file.exists() || file.isDirectory()) {
        throw new FileNotFoundException(file.getName(), file.getName() + " not found");
    }
    file = new File(experiencedPlansFile);
    if(!file.exists() || file.isDirectory()) {
        throw new FileNotFoundException(file.getName(), file.getName() + " not found");
    }
    if (personsCsvFile != "") {
        def f = new File(personsCsvFile);
        if(!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException(f.getName(), f.getName() + " not found");
        }
    }

    DatabaseMetaData dbMeta = connection.getMetaData();

    logger.info("searching index on data table... ")
    ResultSet rs = dbMeta.getIndexInfo(null, null, dataTable, false, false);
    boolean indexIDRECEIVER = false;
    boolean indexTIMESTRING = false;
    while (rs.next()) {
        String column = rs.getString("COLUMN_NAME");
        if (column == "IDRECEIVER") {
            indexIDRECEIVER = true;
            logger.info("index on data table IDSOURCE found")
        }
        else if (column == "TIME") {
            indexTIMESTRING = true;
            logger.info("index on data table TIME found")
        }
    }

    if (!indexIDRECEIVER) {
        logger.info("index on data table IDRECEIVER, NOT found, creating one...")
        sql.execute("CREATE INDEX ON " + dataTable + " (IDRECEIVER)");
    }
    if (!indexTIMESTRING) {
        logger.info("index on data table TIME, NOT found, creating one...")
        sql.execute("CREATE INDEX ON " + dataTable + " (TIME)");
    }

    logger.info("searching index on receivers table... ")
    rs = dbMeta.getIndexInfo(null, null, receiversTable, false, false);
    boolean indexFACILITY = false;
    while (rs.next()) {
        String column = rs.getString("COLUMN_NAME");
        if (column == "FACILITY") {
            indexFACILITY = true;
            logger.info("index on receivers table FACILITY found")
        }
    }

    if (!indexFACILITY) {
        logger.info("index on receivers table FACILITY, NOT found, creating one...")
        sql.execute("CREATE INDEX ON " + receiversTable + " (FACILITY)");
    }

    Map<String, Map<String, String>> personsCsvData = new HashMap<String, Map<String, String>>();
    if (!personsCsvFile.isEmpty()) {
        logger.info("Start Reading personsCsv file ...");
        CSVReaderHeaderAware csvReader;
        CSVParser parser = new CSVParserBuilder().withSeparator(';' as char).build()
        if (personsCsvFile.endsWith(".gz")) {
            InputStream is = Files.newInputStream(Paths.get(personsCsvFile));
            GZIPInputStream gis = new GZIPInputStream(is);
            InputStreamReader isReader = new InputStreamReader(gis);
            BufferedReader br = new BufferedReader(isReader);
            csvReader = new CSVReaderHeaderAwareBuilder(br).withCSVParser(parser).build()
        } else {
            csvReader = new CSVReaderHeaderAwareBuilder(new FileReader(personsCsvFile)).withCSVParser(parser).build()
        }
        Map<String, String> values;
        while ((values = csvReader.readMap()) != null) {
            personsCsvData.put(values["person"], values)
        }
        logger.info("Done Reading personsCsv file");
    }

    Scenario scenario = ScenarioUtils.loadScenario(ConfigUtils.createConfig());
    PopulationReader populationReader = new PopulationReader(scenario);
    populationReader.readFile(plansFile);
    Population population = scenario.getPopulation();

    Scenario experiencedScenario = ScenarioUtils.loadScenario(ConfigUtils.createConfig());
    PopulationReader experiencedPopulationReader = new PopulationReader(experiencedScenario);
    experiencedPopulationReader.readFile(experiencedPlansFile);
    Population experiencedPlans = experiencedScenario.getPopulation();


    Map<Id<Person>, Person> persons = (Map<Id<Person>, Person>) population.getPersons();

    sql.execute("DROP TABLE IF EXISTS " + outTableName)
    sql.execute("DROP TABLE IF EXISTS " + outTableName + "_SEQUENCE")
    sql.execute("CREATE TABLE " + outTableName + ''' (
        PK integer PRIMARY KEY AUTO_INCREMENT,
        PERSON_ID varchar(255),
        AGE int,
        SEX varchar,
        INCOME double,
        EMPLOYED double,
        HOME_FACILITY varchar(255),
        HOME_GEOM geometry,
        WORK_FACILITY varchar(255),
        WORK_GEOM geometry,
        LAEQ real
    );''')
    sql.execute("CREATE TABLE " + outTableName + '''_SEQUENCE (
        PK integer PRIMARY KEY AUTO_INCREMENT,
        PERSON_ID varchar(255),
        TIME int,
        LEVEL double,
        START_ACTIVITY_ID varchar,
        START_ACTIVITY_TYPE varchar,
        START_ACTIVITY_GEOM geometry,
        MAIN_ACTIVITY_ID varchar,
        MAIN_ACTIVITY_TYPE varchar,
        MAIN_ACTIVITY_GEOM geometry,
        END_ACTIVITY_ID varchar,
        END_ACTIVITY_TYPE varchar,
        END_ACTIVITY_GEOM geometry
    );''')
    Statement stmt = connection.createStatement();

    int doprint = 1
    int counter = 0
    long start = System.currentTimeMillis()
    int nb_persons = persons.size()
    for (Map.Entry<Id<Person>, Person> entry : persons.entrySet()) {
        String personId = entry.getKey().toString();
        Person person = entry.getValue();

        def attributes = person.getAttributes();
        Integer age = attributes.getAttribute("age") as Integer
        if (age == null && personsCsvData[personId] != null && personsCsvData[personId].containsKey("age")) {
            age = personsCsvData[personId]["age"] as Integer
        }
        String sex = attributes.getAttribute("sex")
        if (sex == null && personsCsvData[personId] != null && personsCsvData[personId].containsKey("sex")) {
            sex = personsCsvData[personId]["sex"]
        }
        Double income = attributes.getAttribute("householdIncome") as Double
        if (income == null && personsCsvData[personId] != null && personsCsvData[personId].containsKey("householdIncome")) {
            income = personsCsvData[personId]["householdIncome"] as Double
        }
        Boolean employed = attributes.getAttribute("employed")
        if (employed == null && personsCsvData[personId] != null && personsCsvData[personId].containsKey("employed")) {
            employed = personsCsvData[personId]["employed"] as Boolean
        }
        Plan plan = experiencedPlans.getPersons()[person.getId()].getSelectedPlan()

        Map<String, Map<Integer, Double>> activitiesTimeSeries = new HashMap<String, Map<Integer, Double>>()

        if (plan.getPlanElements().size() == 0) { // stays at home all day
            plan = person.getSelectedPlan() // back to the not *experienced* version
        }
        String homeId = "";
        String homeGeom = "POINT EMPTY";
        String workId = "";
        String workGeom = "POINT EMPTY";
        for (PlanElement element : plan.getPlanElements()) {
            if (!(element instanceof Activity)) {
                continue;
            }
            Activity activity = (Activity) element;
            String activityId = activity.getFacilityId().toString();
            if (activity.getType().contains("home")) {
                homeId = activityId;
                if (activity.getCoord() != null) {
                    homeGeom = String.format("POINT(%s %s)", Double.toString(activity.getCoord().getX()), Double.toString(activity.getCoord().getY()))
                }
            }
            if (activity.getType().contains("work")) {
                workId = activityId;
                if (activity.getCoord() != null) {
                    workGeom = String.format("POINT(%s %s)", Double.toString(activity.getCoord().getX()), Double.toString(activity.getCoord().getY()))
                }
            }

            String query = '''
                        SELECT D.LEQA, D.TIME
                        FROM ''' + dataTable + ''' D
                        INNER JOIN ''' + receiversTable + ''' R
                        ON D.IDRECEIVER = R.PK
                        WHERE R.FACILITY = \'''' + activityId + '''\'
                    '''
            ResultSet result = stmt.executeQuery(query)
            Map<Integer, Double> timeSeries = new HashMap<Integer, Double>()
            while(result.next()) {
                int timeBin = result.getInt("TIME")
                Double value = result.getDouble("LEQA")
                timeSeries.put(timeBin, value)
            }
            activitiesTimeSeries.put(activityId, timeSeries)
        }

        double LAeq = -99.0;

        Map<Integer, SequenceElement> sequence = new HashMap<Integer, SequenceElement>()

        int nbTimeBins = (int) (86400 / timeBinSize)
        for (int timeBin = 0; timeBin < 86400; timeBin += timeBinSize) {

            if (!sequence.containsKey(timeBin)) {
                sequence.put(timeBin, new SequenceElement())
            }

            double timeSliceStart = timeBin;
            double timeSliceEnd = timeBin + timeBinSize;

            if (timeSliceStart < 4 * 3600) {
                timeSliceStart += 86400
            }
            if (timeSliceEnd <= 4 * 3600) {
                timeSliceEnd += 86400
            }

            boolean hasActivity = false;
            boolean isOutside = false;
            boolean hasLevel = false; // in cas there is no propagation path arriving to this facility's receiver.
            boolean hasHomeLevel = false; // idem for home
            for (PlanElement element : plan.getPlanElements()) {
                if (!(element instanceof Activity)) {
                    continue;
                }
                Activity activity = (Activity) element;
                String activityId = activity.getFacilityId().toString();
                if (activityId == "null") { // pt interaction ?
                    continue;
                }
                if (activity.type == "outside") {
                    isOutside = true
                    continue;
                }
                double activityStart = 0;
                if (activity.getStartTime() > 0) {
                    activityStart = activity.getStartTime();
                }
                double activityEnd = 86400 + 4 * 3600; // 28h
                if (activity.getEndTime() > 0) {
                    activityEnd = activity.getEndTime();
                }
                double timeWeight = 0.0;

                if (activityStart >= activityEnd) {
                    continue;
                }
                if (activityStart >= timeSliceEnd || activityEnd < timeSliceStart) {
                    continue;
                }

                hasActivity = true;
                String activity_geom = "POINT EMPTY"
                if (activity.getCoord() == null) {
                    if (activity.type == "home" && homeGeom != "") {
                        activity_geom = homeGeom
                    }
                }
                else {
                    activity_geom = String.format("POINT(%s %s)", Double.toString(activity.getCoord().getX()), Double.toString(activity.getCoord().getY()))
                }

                // exemples with timeslice : 1h to 2h (timeBin = 3600, timeBinSize = 3600)
                if (activityStart <= timeSliceStart) { // activity starts before the current timeslice  (ie. 00:05:07)
                    sequence[timeBin].start_activity_id = activity.facilityId.toString()
                    sequence[timeBin].start_activity_type = activity.type
                    sequence[timeBin].start_activity_geom = activity_geom
                    if (activityEnd > timeSliceEnd) { // activity ends after the current timeslice (ie. 02:30:00)
                        timeWeight = 1 / nbTimeBins;
                        sequence[timeBin].end_activity_id = activity.facilityId.toString()
                        sequence[timeBin].end_activity_type = activity.type
                        sequence[timeBin].end_activity_geom = activity_geom
                    }
                    if (activityEnd < timeSliceEnd) { // activity ends in current timeslice (ie. 01:38:00)
                        timeWeight = ((activityEnd - timeSliceStart) / timeBinSize) / nbTimeBins;
                    }
                }
                if (activityStart > timeSliceStart && activityStart < timeSliceEnd) { // activity start is in the current timeslice  (ie. 01:05:07)
                    if (activityEnd > timeSliceEnd) { // activity ends after the current timeslice (ie. 02:30:00)
                        timeWeight = ((timeSliceEnd - activityStart) / timeBinSize) / nbTimeBins;
                        sequence[timeBin].end_activity_id = activity.facilityId.toString()
                        sequence[timeBin].end_activity_type = activity.type
                        sequence[timeBin].end_activity_geom = activity_geom
                    }
                    if (activityEnd < timeSliceEnd) { // activity ends in current timeslice (ie. 01:38:00)
                        timeWeight = ((activityEnd - activityStart) / timeBinSize) / nbTimeBins;
                    }
                }
                if (timeWeight > sequence[timeBin].weight) {
                    sequence[timeBin].main_activity_id = activity.facilityId.toString()
                    sequence[timeBin].main_activity_type = activity.type
                    sequence[timeBin].main_activity_geom = activity_geom
                }

                Double value = activitiesTimeSeries[activityId][timeBin]
                if (value != null) {
                    LAeq = 10 * Math.log10(Math.pow(10, LAeq / 10) + timeWeight * Math.pow(10, value / 10));
                    sequence[timeBin].noise_laeq = value
                    hasLevel = true;
                }
            }

            if (!hasLevel) {
                sequence[timeBin].noise_laeq = -99.0
            }
            if (!hasActivity) {
                if (isOutside) {
                    sequence[timeBin].start_activity_id = "outside"
                    sequence[timeBin].start_activity_type = "outside"
                    sequence[timeBin].main_activity_id = "outside"
                    sequence[timeBin].main_activity_type = "outside"
                    sequence[timeBin].end_activity_id = "outside"
                    sequence[timeBin].end_activity_type = "outside"
                }
                else {
                    // keep default 'travelling' activity
                }
            }
        }

        /*
        PK integer PRIMARY KEY AUTO_INCREMENT,
        PERSON_ID varchar(255),
        AGE int,
        SEX varchar,
        INCOME double,
        EMPLOYED double,
        HOME_FACILITY varchar(255),
        HOME_GEOM geometry,
        WORK_FACILITY varchar(255),
        WORK_GEOM geometry,
        LAEQ real
         */
        PreparedStatement insert_stmt = connection.prepareStatement("INSERT INTO " + outTableName + " VALUES(" +
                "DEFAULT, ?, ?, ?, ?, ?, " +
                "?, ST_GeomFromText(?, "+SRID+"), ?, ST_GeomFromText(?, "+SRID+"), ?)")
        insert_stmt.setString(1, personId)
        insert_stmt.setInt(2, age)
        insert_stmt.setString(3, sex)
        insert_stmt.setDouble(4, income)
        insert_stmt.setBoolean(5, employed)
        insert_stmt.setString(6, homeId)
        insert_stmt.setString(7, homeGeom)
        insert_stmt.setString(8, workId)
        insert_stmt.setString(9, workGeom)
        insert_stmt.setDouble(10, LAeq)
        insert_stmt.execute()
        /*
        PK integer PRIMARY KEY AUTO_INCREMENT,
        PERSON_ID varchar(255),
        TIME int,
        LEVEL double,
        START_ACTIVITY_ID varchar,
        START_ACTIVITY_TYPE varchar,
        START_ACTIVITY_GEOM geometry,
        MAIN_ACTIVITY_ID varchar,
        MAIN_ACTIVITY_TYPE varchar,
        MAIN_ACTIVITY_GEOM geometry,
        END_ACTIVITY_ID varchar,
        END_ACTIVITY_TYPE varchar,
        END_ACTIVITY_GEOM geometry
         */
        PreparedStatement insert_stmt_sequence = connection.prepareStatement(
            "INSERT INTO " + outTableName + "_SEQUENCE VALUES(DEFAULT, '" + personId + "', ?, ?, ?, ?, ST_GeomFromText(?, "+SRID+"), ?, ?, ST_GeomFromText(?, "+SRID+"), ?, ?, ST_GeomFromText(?, "+SRID+"))"
        )
        for (int timeBin = 0; timeBin < 86400; timeBin += timeBinSize) {
            insert_stmt_sequence.setInt(1, timeBin)
            insert_stmt_sequence.setDouble(2, sequence[timeBin].noise_laeq)
            insert_stmt_sequence.setString(3, sequence[timeBin].start_activity_id)
            insert_stmt_sequence.setString(4, sequence[timeBin].start_activity_type)
            insert_stmt_sequence.setString(5, sequence[timeBin].start_activity_geom)
            insert_stmt_sequence.setString(6, sequence[timeBin].main_activity_id)
            insert_stmt_sequence.setString(7, sequence[timeBin].main_activity_type)
            insert_stmt_sequence.setString(8, sequence[timeBin].main_activity_geom)
            insert_stmt_sequence.setString(9, sequence[timeBin].end_activity_id)
            insert_stmt_sequence.setString(10, sequence[timeBin].end_activity_type)
            insert_stmt_sequence.setString(11, sequence[timeBin].end_activity_geom)
            insert_stmt_sequence.addBatch()
        }
        insert_stmt_sequence.executeBatch()

        if (counter >= doprint) {
            doprint *= 2
            double elapsed = (System.currentTimeMillis() - start + 1) / 1000
            logger.info(String.format("Processing Person %d (max:%d) - elapsed : %ss (%.1fit/s)",
                    counter, nb_persons, elapsed, counter/elapsed))
        }
        counter++;
    }

    logger.info('End : Agent_Exposure')
    resultString = "Process done. Table " + outTableName + " created !"
    logger.info('Result : ' + resultString)
    return resultString
}

class SequenceElement {
    double weight = -1 // used only to define 'main' activity

    String start_activity_id = "travelling"
    String start_activity_type = "travelling"
    String start_activity_geom = "POINT EMPTY"
    String main_activity_id = "travelling"
    String main_activity_type = "travelling"
    String main_activity_geom = "POINT EMPTY"
    String end_activity_id = "travelling"
    String end_activity_type = "travelling"
    String end_activity_geom = "POINT EMPTY"

    double noise_laeq
}