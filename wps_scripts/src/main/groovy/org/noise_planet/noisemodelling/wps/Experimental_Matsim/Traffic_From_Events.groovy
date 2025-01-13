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

package org.noise_planet.noisemodelling.wps.Experimental_Matsim

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.io.WKTWriter
import org.matsim.api.core.v01.Coord
import org.matsim.api.core.v01.Id
import org.matsim.api.core.v01.events.*
import org.matsim.api.core.v01.events.handler.*
import org.matsim.api.core.v01.network.Link
import org.matsim.api.core.v01.network.Network
import org.matsim.api.core.v01.population.Person
import org.matsim.core.api.experimental.events.EventsManager
import org.matsim.core.config.ConfigUtils
import org.matsim.core.events.EventsUtils
import org.matsim.core.events.MatsimEventsReader
import org.matsim.core.network.io.MatsimNetworkReader
import org.matsim.core.scenario.ScenarioUtils
import org.matsim.vehicles.Vehicle
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossos
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossosParameters
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.PreparedStatement

title = 'Import traffic data from Mastim simultaion output folder'
description = 'Read Mastim events output file in order to get traffic NoiseModelling input'

inputs = [
    folder: [
        name: 'Path of the Matsim output folder',
        title: 'Path of the Matsim output folder',
        description: 'Path of the Matsim output folder </br> For example : /home/mastim/simulation_output' +
                '<br/>The folder must contain at least the following files: ' +
                '<br/><br/> - output_network.xml.gz' +
                '<br/><br/> - output_events.xml.gz',
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
    populationFactor: [
            name: 'Population Factor',
            title: 'Population Factor',
            description: 'Set the population factor of the MATSim simulation' +
                    '<br/>Must be a decimal number between 0 and 1' +
                    '<br/>Default: 1.0',
            min: 0,
            max: 1,
            type: String.class
    ],
    link2GeometryFile: [
        name: 'Network CSV file',
        title: 'Network CSV file',
        description: 'The path of the pt2matsim CSV file generated when importing OSM network. Ignored if not set.' +
                '<br/>The file must contain at least two columns : ' +
                '<br/><br/> - The link ID' +
                '<br/><br/> - The WKT geometry',
        min: 0,
        max: 1,
        type: String.class
    ],
    SRID : [
            name: 'Projection identifier',
            title: 'Projection identifier',
            description: 'Projection identifier (also called SRID) of the geometric data.' +
                    'It should be an EPSG code, a integer with 4 or 5 digits (ex: 3857 is Web Mercator projection).' +
                    '</br><b> Default value : 4326 </b> ',
            min: 0,
            max: 1,
            type: Integer.class
    ],
    exportTraffic: [
            name: 'Export additionnal traffic data ?',
            title: 'Export additionnal traffic data ?',
            description: 'Define if you want to output average speed and flow per vehicle category in an additional table' +
                    '<br/>Default: False',
            min: 0,
            max: 1,
            type: Boolean.class
    ],
    skipUnused: [
        name: 'Skip unused links ?',
        title: 'Skip unused links ?',
        description: 'Define if links with unused traffic should be omitted in the output table.' +
                '<br/>Default: True',
        min: 0,
        max: 1,
        type: Boolean.class
    ],
    outTableName: [
        name: 'Output table name',
        title: 'Output table name',
        description: 'Name of the table you want to create.' +
                '<br/>A table with this name will be created plus another with a "_LW" suffix' +
                '<br/>For exemple if set to "MATSIM_ROADS (default value)":' +
                '<br/><br/> - the table MATSIM_ROADS, with the link ID and the geometry field' +
                '<br/><br/> - the table MATSIM_ROADS_LW, with the link ID and the traffic data',
        min: 0,
        max: 1,
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

    String resultString = null

    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")
    logger.info('Start : Traffic_From_Events')
    logger.info("inputs {}", input)

    String folder = input["folder"]

    String outTableName = "MATSIM_ROADS"
    if (input["outTableName"]) {
        outTableName = input["outTableName"]
    }
    String lwTableName = outTableName + "_LW"
    String trafficTableName = outTableName + "_TRAFFIC"
    String contribTableName = outTableName + "_CONTRIB"

    String link2GeometryFile = ""
    if (input["link2GeometryFile"]) {
        link2GeometryFile = input["link2GeometryFile"]
    }

    int timeBinSize = 3600;
    if (input["timeBinSize"]) {
        timeBinSize = input["timeBinSize"] as int
    }
    int timeBinMin = 0;
    if (input["timeBinMin"]) {
        timeBinMin = input["timeBinMin"] as int
    }
    int timeBinMax = 86400;
    if (input["timeBinMax"]) {
        timeBinMax = input["timeBinMax"] as int
    }

    String SRID = "4326"
    if (input['SRID']) {
        SRID = input['SRID']
    }

    boolean skipUnused = false
    if (input["skipUnused"]) {
        skipUnused = input["skipUnused"] as boolean
    }

    boolean keepVehicleContrib = false
    if (input["keepVehicleContrib"]) {
        keepVehicleContrib = input["keepVehicleContrib"] as boolean
    }

    boolean exportTraffic = false
    if (input["exportTraffic"]) {
        exportTraffic = input["exportTraffic"] as boolean
    }

    double populationFactor = 1.0
    if (input["populationFactor"]) {
        populationFactor = input["populationFactor"] as double
    }

    File f
    String eventFile = folder + "/output_events.xml.gz"
    f = new File(eventFile)
    if(!f.exists() || f.isDirectory()) {
        throw new FileNotFoundException(eventFile, "output_events.xml.gz not found in MATSim folder")
    }
    String networkFile = folder + "/output_network.xml.gz"
    f = new File(networkFile)
    if(!f.exists() || f.isDirectory()) {
        throw new FileNotFoundException(networkFile, "output_network.xml.gz not found in MATSim folder")
    }
    if (link2GeometryFile != "") {
        f = new File(link2GeometryFile)
        if(!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException(link2GeometryFile, link2GeometryFile + " not found")
        }
    }

    logger.info("Create SQL tables : " + outTableName + " & " + lwTableName)
    // Open connection
    sql.execute("DROP TABLE IF EXISTS " + outTableName)
    sql.execute("CREATE TABLE " + outTableName + '''( 
        PK integer PRIMARY KEY AUTO_INCREMENT, 
        LINK_ID varchar(255),
        OSM_ID varchar(255),
        THE_GEOM geometry
    );''')

    sql.execute("DROP TABLE IF EXISTS " + lwTableName)
    sql.execute("CREATE TABLE " + lwTableName + '''( 
        PK integer PRIMARY KEY AUTO_INCREMENT, 
        LINK_ID varchar(255),
        LW63 double precision, LW125 double precision, LW250 double precision, LW500 double precision, LW1000 double precision, LW2000 double precision, LW4000 double precision, LW8000 double precision,
        TIME int
    );''')

    if (exportTraffic) {
        sql.execute("DROP TABLE IF EXISTS " + trafficTableName)
        sql.execute("CREATE TABLE " + trafficTableName + '''( 
            PK integer PRIMARY KEY AUTO_INCREMENT, 
            LINK_ID varchar(255),
            LV_D double precision,
            LV_SPD_D double precision,
            MV_D double precision,
            MV_SPD_D double precision,
            HGV_D double precision,
            HGV_SPD_D double precision,
            TIME int
        );''')
    }
    if (keepVehicleContrib) {
        sql.execute("DROP TABLE IF EXISTS " + contribTableName)
        sql.execute("CREATE TABLE " + contribTableName + '''( 
            PK integer PRIMARY KEY AUTO_INCREMENT, 
            LINK_ID varchar(255),
            PERSON_ID varchar(255),
            VEHICLE_ID varchar(255),
            LW63 double precision, LW125 double precision, LW250 double precision, LW500 double precision, LW1000 double precision, LW2000 double precision, LW4000 double precision, LW8000 double precision,
            TIME int
        );''')
    }

    PreparedStatement roadStatement = connection.prepareStatement("INSERT INTO " + outTableName + " (LINK_ID, OSM_ID, THE_GEOM) VALUES (?, ?, ST_UpdateZ(ST_GeomFromText(?, " + SRID + "),0.05))")
    PreparedStatement lwStatement = connection.prepareStatement("INSERT INTO " + lwTableName + " (LINK_ID, LW63, LW125, LW250, LW500, LW1000, LW2000, LW4000, LW8000, TIME) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    PreparedStatement trafficStatement;
    if (exportTraffic) {
        trafficStatement = connection.prepareStatement("INSERT INTO " + trafficTableName + " (LINK_ID, LV_D, LV_SPD_D, MV_D, MV_SPD_D, HGV_D, HGV_SPD_D, TIME) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
    }
    PreparedStatement contribStatement;
    if (keepVehicleContrib) {
        contribStatement = connection.prepareStatement("INSERT INTO " + contribTableName + " (LINK_ID, PERSON_ID, VEHICLE_ID, LW63, LW125, LW250, LW500, LW1000, LW2000, LW4000, LW8000, TIME) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    }
    logger.info("Done Creating SQL tables")

    Network network = ScenarioUtils.loadScenario(ConfigUtils.createConfig()).getNetwork()
    MatsimNetworkReader networkReader = new MatsimNetworkReader(network)
    logger.info("Start reading network file ... ")
    networkReader.readFile(networkFile)
    logger.info("Done reading network file ")

    Map<Id<Link>, Link> links = (Map<Id<Link>, Link>) network.getLinks()

    EventsManager evMgr = EventsUtils.createEventsManager()
    ProcessOutputEventHandler evHandler = new ProcessOutputEventHandler()

    evHandler.setTimeBinSize(timeBinSize)
    evHandler.setTimeBinMin(timeBinMin)
    evHandler.setTimeBinMax(timeBinMax)
    evHandler.setSRID(SRID)
    evHandler.setPopulationFactor(populationFactor)
    evHandler.initLinks((Map<Id<Link>, Link>) links)

    evMgr.addHandler(evHandler)

    MatsimEventsReader eventsReader = new MatsimEventsReader(evMgr)

    logger.info("Start reading event file ... ")
    eventsReader.readFile(eventFile)
    logger.info("Done reading event file ")

    Map<String, String> link2geomData = new HashMap<>()
    if (!link2GeometryFile.isEmpty()) {
        logger.info("Start Reading link2geom file ...")
        BufferedReader br = new BufferedReader(new FileReader(link2GeometryFile))
        String line = null;
        while ((line = br.readLine()) != null) {
            String[] str = line.split(",", 2)
            if (str.size() > 1) {
                link2geomData.put(str[0], str[1].trim().replace("\"", ""))
            }
        }
        logger.info("Done Reading link2geom file")
    }

    logger.info("Start Inserting Into SQL tables...")
    int counter = 0
    int doprint = 1
    long start = System.currentTimeMillis();
    for (Map.Entry<Id<Link>, LinkStatStruct> entry : evHandler.links.entrySet()) {
        String linkId = entry.getKey().toString()
        LinkStatStruct linkStatStruct = entry.getValue()
        if (counter >= doprint) {
            double elapsed = (System.currentTimeMillis() - start + 1) / 1000
            logger.info(String.format("Processing Link %d (max:%d) - elapsed : %ss (%.1fit/s) - eta : %ss",
                    counter, evHandler.links.size(), elapsed, counter/elapsed, (evHandler.links.size() - counter) / (counter / elapsed)))
            doprint *= 2
        }
        counter ++

        if (skipUnused && !linkStatStruct.isUsed) {
            continue
        }
        linkStatStruct.calculate()

        String geomString = ""
        if (!link2GeometryFile.isEmpty()) {
            geomString = link2geomData.get(linkId)
        }
        if (geomString == '' || geomString == null || geomString.matches("LINESTRING\\(\\d+\\.\\d+ \\d+\\.\\d+\\)")) {
            geomString = linkStatStruct.getGeometryString()
        }
        roadStatement.setString(1, linkId)
        roadStatement.setString(2, linkStatStruct.getOsmId())
        roadStatement.setString(3, geomString)
        roadStatement.execute()
        for (int timeBin = timeBinMin ; timeBin < timeBinMax; timeBin += timeBinSize) {
            int index = 1
            lwStatement.setString(index, linkId)
            List<Double> levels = linkStatStruct.getSourceLevels(timeBin)
            for (Double level: levels) {
                index ++
                lwStatement.setDouble(index, level)
            }
            index ++
            lwStatement.setInt(index, timeBin)
            lwStatement.addBatch()
        }
        lwStatement.executeBatch()
        if (exportTraffic) {
            for (int timeBin = timeBinMin ; timeBin < timeBinMax; timeBin += timeBinSize) {
                int index = 1
                trafficStatement.setString(index, linkId)
                Trip.Type[] types = [Trip.Type.LV, Trip.Type.MV, Trip.Type.HV]
                for (Trip.Type type in types) {
                    int count = linkStatStruct.getVehicleCount(type, timeBin)
                    double speed = 0.0
                    if (count > 0) {
                        speed = Math.round(3.6 * linkStatStruct.link.getLength() / linkStatStruct.getMeanTravelTime(type, timeBin))
                    }
                    index ++
                    trafficStatement.setInt(index, count)
                    index ++
                    trafficStatement.setDouble(index, speed)
                }
                index ++
                trafficStatement.setInt(index, timeBin)
                trafficStatement.addBatch()
            }
            trafficStatement.executeBatch()
        }
        if (keepVehicleContrib) {
//            sql.execute(linkStatStruct.toSqlInsertContrib(contribTableName));
            for (int timeBin = 0 ; timeBin < 86400; timeBin += timeBinSize) {
                for (PersonContribFreq person_contrib : linkStatStruct.contributions.get(timeBin)) {
                    String personId = person_contrib.personId.toString()
                    String vehicleId = person_contrib.vehicleId.toString()
                    List<Double> contributions = person_contrib.contributions
                    int index = 1
                    contribStatement.setString(index, linkId)
                    index ++
                    contribStatement.setString(index, personId)
                    index ++
                    contribStatement.setString(index, vehicleId)
                    for (Double contrib: contributions) {
                        index ++
                        contribStatement.setDouble(index, contrib)
                    }
                    index ++
                    contribStatement.setInt(index, timeBin)
                    contribStatement.addBatch()
                }
            }
            contribStatement.executeBatch()
        }
    }
    logger.info("DONE Inserting Into SQL tables...")

    logger.info("Start Creating indexes on tables ...")
    logger.info("CREATE INDEX " + outTableName + "_LINK_ID_IDX ON " + outTableName + " (LINK_ID);")
    sql.execute("CREATE INDEX " + outTableName + "_LINK_ID_IDX ON " + outTableName + " (LINK_ID);")
    logger.info("CREATE INDEX " + lwTableName + "_LINK_ID_IDX ON " + lwTableName + " (LINK_ID);")
    sql.execute("CREATE INDEX " + lwTableName + "_LINK_ID_IDX ON " + lwTableName + " (LINK_ID);")
    logger.info("CREATE INDEX " + lwTableName + "_TIME_IDX ON " + lwTableName + " (TIME);")
    sql.execute("CREATE INDEX " + lwTableName + "_TIME_IDX ON " + lwTableName + " (TIME);")
    if (keepVehicleContrib) {
        logger.info("CREATE INDEX " + contribTableName + "_LINK_ID_IDX ON " + contribTableName + " (LINK_ID);")
        sql.execute("CREATE INDEX " + contribTableName + "_LINK_ID_IDX ON " + contribTableName + " (LINK_ID);")
        logger.info("CREATE INDEX " + contribTableName + "_TIME_IDX ON " + contribTableName + " (TIME);")
        sql.execute("CREATE INDEX " + contribTableName + "_TIME_IDX ON " + contribTableName + " (TIME);")
        logger.info("CREATE INDEX " + contribTableName + "_PERSON_ID_IDX ON " + contribTableName + " (PERSON_ID);")
        sql.execute("CREATE INDEX " + contribTableName + "_PERSON_ID_IDX ON " + contribTableName + " (PERSON_ID);")
    }

    logger.info("Done Creating indexes on tables.")
    resultString = "Roads stats imported from matsim traffic output"
    logger.info('Result : ' + resultString)
    return resultString
}

class ProcessOutputEventHandler implements
        LinkEnterEventHandler, LinkLeaveEventHandler,
        VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler,
        PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler {

    Map<Id<Link>, LinkStatStruct> links = new HashMap<Id<Link>, LinkStatStruct>()
    Map<Id<Vehicle>, List<Id<Person>>> personsInVehicle = new HashMap<Id<Vehicle>, List<Id<Person>>>()
    int timeBinSize = 3600;
    int timeBinMin = 0;
    int timeBinMax = 86400;
    String SRID = 4326
    double populationFactor = 1.0

    void setSRID(String srid) {
        this.SRID = srid
    }

    void setTimeBinSize(int timeBinSize) {
        this.timeBinSize = timeBinSize
    }
    void setTimeBinMin(int timeBinMin) {
        this.timeBinMin = timeBinMin
    }
    void setTimeBinMax(int timeBinMax) {
        this.timeBinMax = timeBinMax
    }

    void setPopulationFactor(double populationFactor) {
        this.populationFactor = populationFactor
    }

    @Override
    void handleEvent(VehicleEntersTrafficEvent event) {
        if (!personsInVehicle.containsKey(event.getVehicleId())) {
            List<Id<Person>> personList = new ArrayList<Id<Person>>()
            personList.add(event.getPersonId())
            personsInVehicle.put(event.getVehicleId(), personList)
        }
    }

    @Override
    void handleEvent(PersonEntersVehicleEvent event) {
        if (!personsInVehicle.containsKey(event.getVehicleId())) {
            List<Id<Person>> personList = new ArrayList<Id<Person>>()
            personList.add(event.getPersonId())
            personsInVehicle.put(event.getVehicleId(), personList)
        } else {
            personsInVehicle.get(event.getVehicleId()).add(event.getPersonId())
        }
    }

    @Override
    void handleEvent(PersonLeavesVehicleEvent event) {
        if (personsInVehicle.containsKey(event.getVehicleId())) {
            personsInVehicle.get(event.getVehicleId()).remove(event.getPersonId())
            if (personsInVehicle.get(event.getVehicleId()).size() == 0) {
                personsInVehicle.remove(event.getVehicleId())
            }
        }
    }

    @Override
    void handleEvent(VehicleLeavesTrafficEvent event) {
        if (personsInVehicle.containsKey(event.getVehicleId())) {
            personsInVehicle.remove(event.getVehicleId())
        }
    }

    @Override
    void handleEvent(LinkEnterEvent event) {

        Id<Link> linkId = event.getLinkId()
        Id<Vehicle> vehicleId = event.getVehicleId()

        double time = event.getTime()

        if (!links.containsKey(linkId)) {
            LinkStatStruct stats = new LinkStatStruct(timeBinSize, populationFactor, SRID)
            links.put(linkId, stats)
        }

        LinkStatStruct stats = links.get(linkId)

        stats.vehicleEnterAt(vehicleId, time)

        links.put(linkId, stats)
    }

    @Override
    void handleEvent(LinkLeaveEvent event) {

        Id<Link> linkId = event.getLinkId()
        Id<Vehicle> vehicleId = event.getVehicleId()
        double time = event.getTime()

        if (!links.containsKey(linkId)) {
            LinkStatStruct stats = new LinkStatStruct(timeBinSize, populationFactor, SRID)
            links.put(linkId, stats)
        }

        LinkStatStruct stats = links.get(linkId)
        stats.vehicleLeaveAt(vehicleId, time, personsInVehicle.get(vehicleId))
        links.put(linkId, stats)
    }

    void initLinks(Map<Id<Link>, Link> netLinks) {
        for (Map.Entry<Id<Link>, Link> entry: netLinks.entrySet()) {
            Id<Link> linkId = entry.getKey()
            Link link = entry.getValue()

            if (!links.containsKey(linkId)) {
                LinkStatStruct stats = new LinkStatStruct(timeBinSize, populationFactor, SRID)
                stats.setLink(link)
                links.put(linkId, stats)
            }
        }
    }

}

class Trip {
    enum Type {
        LV, MV, HV
    };

    public int timeBin
    public Id<Vehicle> vehicleId
    public Type type
    public double travelTime
    public List<Id<Person>> persons

    Trip(int timeBin, Id<Vehicle> vehicleId, Type type, double travelTime, List<Id<Person>> persons) {
        this.timeBin = timeBin
        this.vehicleId = vehicleId
        this.type = type
        this.travelTime = travelTime
        this.persons = persons
    }
}

class PersonContrib {
    public Id<Person> personId
    public Id<Vehicle> vehicleId
    public double contrib

    PersonContrib(Id<Person> personId, Id<Vehicle> vehicleId, double contrib) {
        this.personId = personId
        this.vehicleId = vehicleId
        this.contrib = contrib
    }
}

class PersonContribFreq {
    public Id<Person> personId
    public Id<Vehicle> vehicleId
    public List<Double> contributions

    PersonContribFreq(Id<Person> personId, Id<Vehicle> vehicleId, List<Double> contributions) {
        this.personId = personId
        this.vehicleId = vehicleId
        this.contributions = contributions
    }
}

class LinkStatStruct {

    public Map<Integer, ArrayList<Trip>> trips = new HashMap<Integer, ArrayList<Trip> >()
    public Map<Id<Vehicle>, Double> enterTimes = new HashMap<Id<Vehicle>, Double>()

    public Link link
    public Map<Integer, List<Double>> levels = new HashMap<Integer, List<Double>>()
    public Map<Integer, List<PersonContribFreq>> contributions = new HashMap<Integer, List<PersonContribFreq>>()
//    public Map<String, List<PersonContribFreq>> contributions = new HashMap<String, List<PersonContribFreq>>()

    public String SRID = 4326
    public double populationFactor = 1.0

    public boolean isUsed = false

    int timeBinSize = 3600
    int timeBinMin = 0
    int timeBinMax = 86400

    LinkStatStruct(int timeBinSize, double populationFactor) {
        this.timeBinSize = timeBinSize
        this.populationFactor = populationFactor
    }

    LinkStatStruct(int timeBinSize, double populationFactor, SRID) {
        this(timeBinSize, populationFactor)
        this.SRID = SRID
    }

    void vehicleEnterAt(Id<Vehicle> vehicleId, double time) {
        isUsed = true
        if (!enterTimes.containsKey(vehicleId)) {
            enterTimes.put(vehicleId, time)
        }
    }

    void vehicleLeaveAt(Id<Vehicle> vehicleId, double time) {
        vehicleLeaveAt(vehicleId, time, new ArrayList<Id<Person>>())
    }

    void vehicleLeaveAt(Id<Vehicle> vehicleId, double time, List<Id<Person>> persons) {
        int timeBin = getTimeBin(time)
        if (!trips.containsKey(timeBin)) {
            trips.put(timeBin, new ArrayList<Trip>())
        }
        if (enterTimes.containsKey(vehicleId)) {
            double enterTime = enterTimes.get(vehicleId)
            double travelTime = time - enterTime
            Trip.Type type = Trip.Type.LV
            if (vehicleId.toString().contains("bus")) {
                type = Trip.Type.MV
            }
            if (vehicleId.toString().contains("tram")) {
                return
            }
            if (vehicleId.toString().contains("rail")) {
                return
            }
            Trip trip = new Trip(timeBin, vehicleId, type, travelTime, new ArrayList<Id<Person>>(persons))
            trips.get(timeBin).add(trip)
            enterTimes.remove(vehicleId)
        }
    }

    int getVehicleCount(Trip.Type type, int timeBin) {
        if (!trips.containsKey(timeBin)) {
            return 0
        }
        int count = 0
        for (Trip trip in trips.get(timeBin)) {
            if (trip.type == type) {
                count++
            }
        }
        return count
    }

    double getMeanTravelTime(Trip.Type type, int timeBin) {
        int vehicleCount = getVehicleCount(type, timeBin)
        if (vehicleCount == 0) {
            return 0.0
        }
        double sum = 0.0
        for (Trip trip in trips.get(timeBin)) {
            if (trip.type == type) {
                sum += trip.travelTime
            }
        }
        return (sum / vehicleCount)
    }

    double getMaxTravelTime(Trip.Type type, int timeBin) {
        int vehicleCount = getVehicleCount(type, timeBin)
        if (vehicleCount == 0) {
            return 0.0
        }
        double max = 0.0
        for (Trip trip in trips.get(timeBin)) {
            if (trip.type == type) {
                if (trip.travelTime > max) {
                    max = trip.travelTime
                }
            }
        }
        return max
    }

    double getMinTravelTime(Trip.Type type, int timeBin) {
        int vehicleCount = getVehicleCount(type, timeBin)
        if (vehicleCount == 0) {
            return 0.0
        }
        double min = -1
        for (Trip trip in trips.get(timeBin)) {
            if (trip.type == type) {
                if (min <= 0 || trip.travelTime < min) {
                    min = trip.travelTime
                }
            }
        }
        return min
    }
    private int getTimeBin(double time) {
        return (time - time % timeBinSize) % 86400;
    }

    void setLink(Link link) {
        this.link = link
    }

    List<Double> getSourceLevels(int timeBin) {
        return levels.get(timeBin)
    }

    static double[] calculateSourceLevels(double LVCount, double LVAvgSpeed, double MVCount, double MVAvgSpeed, double HVCount, double HVAvgSpeed) {
        int[] freqs = [63, 125, 250, 500, 1000, 2000, 4000, 8000]
        double[] result = new double[freqs.length]
        if (LVCount == 0 && MVCount == 0 && HVCount == 0) {
            for (int i = 0; i < freqs.length; i++) {
                result[i] = -99.0
            }
            return result
        }
        for (int i = 0; i < freqs.length; i++) {
            RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(
                    LVAvgSpeed,MVAvgSpeed,HVAvgSpeed,0.0,0.0,
                    LVCount,MVCount,HVCount,0.0,0.0,
                    freqs[i],20.0,"NL08",0.0,0.0,
                    100,2)

            result[i] = RoadCnossos.evaluate(rsParametersCnossos)
        }
        return result
    }

    static double calculateSourceLeq(double LVCount, double LVAvgSpeed, double MVCount, double MVAvgSpeed, double HVCount, double HVAvgSpeed) {
        double[] levels = calculateSourceLevels(LVCount, LVAvgSpeed, MVCount, MVAvgSpeed, HVCount, HVAvgSpeed)
        double leq = -99.0
        for (double level : levels) {
            leq = Math.log10(Math.pow(10, leq / 10.0) + Math.pow(10, level / 10.0))
        }
        return leq
    }

    static double calculateSourceLAeq(double LVCount, double LVAvgSpeed, double MVCount, double MVAvgSpeed, double HVCount, double HVAvgSpeed) {
        double[] levels = calculateSourceLevels(LVCount, LVAvgSpeed, MVCount, MVAvgSpeed, HVCount, HVAvgSpeed)
        double[] aWeights = [-26.2, -16.1, -8.6, -3.2, 0, 1.2, 1.0, -1.1]
        double leq = -99.0
        for (int i = 0; i < levels.length; i++) {
            leq = Math.log10(Math.pow(10, leq / 10.0) + Math.pow(10, (levels[i] + aWeights[i]) / 10.0))
        }
        return leq
    }

    Coordinate[] getGeometry() {
        if (link.getAttributes().getAsMap().containsKey("geometry")) {
            Coord[] coords = ((Coord[]) link.getAttributes().getAttribute("geometry"))
            Coordinate[] result = new Coordinate[coords.length]
            for (int i = 0; i < coords.length; i++) {
                result[i] = new Coordinate(coords[i].getX(), coords[i].getY(), 0.05)
            }
            return result
        } else {
            Coordinate[] result = new Coordinate[2]
            result[0] = new Coordinate(
                    link.getFromNode().getCoord().getX(),
                    link.getFromNode().getCoord().getY(),
                    0.05
            )
            result[1] = new Coordinate(
                    link.getToNode().getCoord().getX(),
                    link.getToNode().getCoord().getY(),
                    0.05
            )
            return result
        }
    }
    String getGeometryString() {
        Coordinate[] points = getGeometry()
        return WKTWriter.toLineString(points);
    }

    String getOsmId() {
        if (link.getAttributes().getAsMap().containsKey("origid")) {
            return link.getAttributes().getAttribute("origid").toString()
        } else if (link.getId().toString().contains("_")) {
            return link.getId().toString().split("_")[0]
        } else {
            return String.valueOf(Long.parseLong(link.getId().toString()))
        }
    }

    void calculate() {
        double vehicleCount = 1
        vehicleCount = vehicleCount * 3600 / timeBinSize // rescale the vehicle count to match an hourly flow
        vehicleCount = vehicleCount / populationFactor // rescale the vehicle count to match the population factor
        List<Double> empty_levels = [-99.0, -99.0, -99.0, -99.0, -99.0, -99.0, -99.0, -99.0]

        for (int timeBin = timeBinMin; timeBin < timeBinMax; timeBin += timeBinSize) {
            if (!levels.containsKey(timeBin)) {
                levels.put(timeBin, new ArrayList<Double>(empty_levels))
            }
            if (!contributions.containsKey(timeBin)) {
                contributions.put(timeBin, new ArrayList<PersonContribFreq>())
            }
            for (Trip trip in trips.get(timeBin)) {
                double speed = Math.round(3.6 * link.getLength() / trip.travelTime)
                List<Double> trip_levels = new ArrayList<Double>(empty_levels)
                if (trip.type == Trip.Type.LV) {
                    trip_levels = calculateSourceLevels(vehicleCount, speed, 0, 0, 0, 0)
                } else if (trip.type == Trip.Type.MV) {
                    trip_levels = calculateSourceLevels(0, 0, 0, 0, vehicleCount, speed)
                } else if (trip.type == Trip.Type.HV) {
                    trip_levels = calculateSourceLevels(0, 0, vehicleCount, speed, 0, 0)
                }
                List<Double> contribution_levels = new ArrayList<Double>(empty_levels)
                for (int freq = 0; freq < empty_levels.size(); freq++) {
                    levels[timeBin][freq] = 10 * Math.log10(Math.pow(10, levels[timeBin][freq] / 10) + Math.pow(10, trip_levels[freq] / 10))
                    contribution_levels[freq] = trip_levels[freq] - 10 * Math.log10(trip.persons.size())
                }
                for (Id<Person> person_id: trip.persons) {
                    contributions.get(timeBin).add(new PersonContribFreq(person_id, trip.vehicleId, contribution_levels))
                }
            }
        }
    }
}
