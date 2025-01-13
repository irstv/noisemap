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
 * @Author Nicolas Fortin, Université Gustave Eiffel
 * @Author Gwendall Petit, Cerema
 * @Author Pierre Aumond, Université Gustave Eiffel
 * @Author buildingParams.json is from https://github.com/orbisgis/geoclimate/
 */

package org.noise_planet.noisemodelling.wps.Import_and_Export

import crosby.binary.osmosis.OsmosisReader
import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.json.JsonSlurper
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer
import org.openstreetmap.osmosis.core.domain.v0_6.*
import org.openstreetmap.osmosis.core.task.v0_6.Sink
import org.openstreetmap.osmosis.xml.common.CompressionMethod
import org.openstreetmap.osmosis.xml.v0_6.XmlReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

title = 'Import BUILDINGS, GROUND and ROADS tables from OSM'

description = '&#10145;&#65039; Convert <b>.osm</b>, <b>.osm.gz</b> or <b>.osm.pbf</b> file into NoiseModelling input tables. We recommend using OSMBBBike : https://extract.bbbike.org/ </br>' +
              '<hr>' +
              'The following output tables will be created: <br>' +
              '- <b> BUILDINGS </b>: a table containing the buildings<br>' +
              '- <b> GROUND </b>: a table containing ground acoustic absorption, based on OSM landcover surfaces<br>' +
              '- <b> ROADS </b>: a table containing the roads. As OSM does not include data on road traffic flows, default values are assigned according to the -Good Practice Guide for Strategic Noise Mapping and the Production of Associated Data on Noise Exposure - Version 2<br><br>' +
              '&#128161; The user can choose to avoid creating some of these tables by checking the dedicated boxes </br> </br>' +
              '<img src="/wps_images/import_osm_file.png" alt="Import OSM file" width="95%" align="center">'

inputs = [
        pathFile : [
                name       : 'Path of the OSM file',
                title      : 'Path of the OSM file',
                description: '&#128194; Path of the OSM file, including its extension (.osm, .osm.gz or .osm.pbf).<br>' +
                             'For example: c:/home/area.osm.pbf',
                type       : String.class
        ],
        targetSRID : [
                name       : 'Target projection identifier',
                title      : 'Target projection identifier',
                description: '&#127757; Target projection identifier (also called SRID) of your table.<br>' +
                             'It should be an <a href="https://epsg.io/" target="_blank">EPSG</a> code, an integer with 4 or 5 digits (ex: <a href="https://epsg.io/3857" target="_blank">3857</a> is Web Mercator projection).<br><br>' +
                             '&#x1F6A8; The target SRID must be in <b>metric</b> coordinates.',
                type       : Integer.class
        ],
        ignoreBuilding : [
                name       : 'Do not import Buildings',
                title      : 'Do not import Buildings',
                description: '&#9989; If the box is checked</i> &#8594; the table BUILDINGS will <b>NOT</b> be created.<br><br>' +
                             '&#129001; If the box is <b>NOT</b> checked &#8594; the table BUILDINGS will be created and will contain:<br>' +
                             '- <b> PK </b> : An identifier. It shall be a primary key (INTEGER, PRIMARY KEY)<br>' +
                             '- <b> THE_GEOM </b> : The 2D geometry of the building (POLYGON or MULTIPOLYGON). <br>' +
                             '- <b> HEIGHT </b> : The height of the building (FLOAT). ' +
                             'If this information is not available then it is deduced from the number of floors (if available) with the addition of a small random variation from one building to another. ' +
                             'Finally, if no information is available, a height of 5m is set by default.',
                min        : 0, 
                max        : 1,
                type       : Boolean.class
        ],
        ignoreGround : [
                name       : 'Do not import Surface acoustic absorption',
                title      : 'Do not import Surface acoustic absorption',
                description: '&#9989; If the box is checked &#8594; the table GROUND will <b>NOT</b> be created.<br><br>' +
                             '&#129001; If the box is <b>NOT</b> checked &#8594; the table GROUND will be created and will contain: <br>' +
                             '- <b> PK </b> : An identifier. It shall be a primary key (INTEGER, PRIMARY KEY)<br>' + 
                             '- <b> ID_WAY </b> : OSM identifier (INTEGER)<br>' + 
                             '- <b> THE_GEOM </b> : The 2D geometry of the sources (POLYGON or MULTIPOLYGON)<br>' +
                             '- <b> PRIORITY </b> : Since NoiseModelling does not allowed overlapping geometries, if this is the case, this column is used to prioritize the geometry that will win over the other one when cutting. The order is given according to the type of land use<br>' +
                             '- <b> G </b> : The acoustic absorption of a ground (FLOAT) (between 0 : very hard and 1 : very soft)',
                min        : 0, 
                max        : 1,
                type       : Boolean.class
        ],
        ignoreRoads : [
                name       : 'Do not import Roads',
                title      : 'Do not import Roads',
                description: '&#9989; If the box is checked &#8594; the table ROADS will <b>NOT</b> be created.<br><br>' +
                             '&#129001; If the box is <b>NOT</b> checked &#8594; the table ROADS will be created and will contain:<br>' +
                             '- <b> PK </b> : An identifier. It shall be a primary key (INTEGER, PRIMARY KEY)<br>' +
                             '- <b> ID_WAY </b> : OSM identifier (INTEGER)<br>' +
                             '- <b> THE_GEOM </b> : The 2D geometry of the sources (LINESTRING or MULTILINESTRING)<br>' +                        
                             '- <b> LV_D </b> : Hourly average light and heavy vehicle count (6-18h) (DOUBLE)<br>' +
                             '- <b> LV_E </b> : Hourly average light and heavy vehicle count (18-22h) (DOUBLE)<br>' +
                             '- <b> LV_N </b> : Hourly average light and heavy vehicle count (22-6h) (DOUBLE)<br>' +
                             '- <b> HGV_D </b> : Hourly average heavy vehicle count (6-18h) (DOUBLE)<br>' +
                             '- <b> HGV_E </b> : Hourly average heavy vehicle count (18-22h) (DOUBLE)<br>' +
                             '- <b> HGV_N </b> : Hourly average heavy vehicle count (22-6h) (DOUBLE)<br>' +
                             '- <b> LV_SPD_D </b> : Hourly average light vehicle speed (6-18h) (DOUBLE)<br>' +
                             '- <b> LV_SPD_E </b> : Hourly average light vehicle speed (18-22h) (DOUBLE)<br>' +
                             '- <b> LV_SPD_N </b> : Hourly average light vehicle speed (22-6h) (DOUBLE)<br>' +
                             '- <b> HGV_SPD_D </b> : Hourly average heavy vehicle speed (6-18h) (DOUBLE)<br>' +
                             '- <b> HGV_SPD_E </b> : Hourly average heavy vehicle speed (18-22h) (DOUBLE)<br>' +
                             '- <b> HGV_SPD_N </b> : Hourly average heavy vehicle speed (22-6h) (DOUBLE)<br>' +
                             '- <b> PVMT </b> : CNOSSOS road pavement identifier (ex: NL05) (VARCHAR)<br> <br>' +
                             '&#128161; <b>These information are deduced from the roads importance in OSM.</b>.',
                min        : 0, 
                max        : 1,
                type       : Boolean.class
        ],
        removeTunnels : [
                name       : 'Remove tunnels from OSM data',
                title      : 'Remove tunnels from OSM data',
                description: '&#9989; If checked, remove roads from OSM data that contain OSM tag <b>tunnel=yes</b>.',
                min        : 0, 
                max        : 1,
                type       : Boolean.class
        ],
        eliminateNoTrafficRoads : [
                name       : 'Eliminate no traffic roads',
                title      : 'Eliminate no traffic roads',
                description: '<b>If checked</b>, only roads with these "TYPE" values will remain:<br>' +
                             '- bus_guideway: Dedicated lanes or tracks for buses<br>' +
                             '- busway: Bus-only lanes<br>' +
                             '- living_street: Residential streets with pedestrian priority<br>' +
                             '- motorway: High-speed, restricted-access highways<br>' +
                             '- motorway_link: Connector ramps for motorways<br>' +
                             '- primary: Major roads linking large cities<br>' +
                             '- primary_link: Connector ramps for primary roads<br>' +
                             '- raceway: Racing tracks<br>' +
                             '- residential: Roads in residential areas<br>' +
                             '- road: Generic roads<br>' +
                             '- secondary: Roads connecting smaller towns<br>' +
                             '- secondary_link: Connector ramps for secondary roads<br>' +
                             '- service: Service lanes (access to parking lots, etc.)<br>' +
                             '- tertiary: Roads connecting villages and hamlets<br>' +
                             '- tertiary_link: Connector ramps for tertiary roads<br>' +
                             '- trunk: Important roads that are not motorways<br>' +
                             '- trunk_link: Connector ramps for trunk roads<br>' +
                             '- unclassified: Minor roads not fitting higher classifications<br>' +
                             '- rest_area: Areas for rest along roads<br>' +
                             '- traffic_calming: Traffic calming features (speed bumps, etc.)<br>' +
                             '- traffic_island: Traffic islands<br><br>' +
                             '<b>If not checked</b>, all roads are processed as before.',
                min        : 0, 
                max        : 1,
                type       : Boolean.class
        ]
]

outputs = [
        result : [
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
    logger.info('Start : Get Buildings from OSM')
    logger.info("inputs {}", input)

    String pathFile = input["pathFile"] as String

    Boolean ignoreBuilding = false
    if ('ignoreBuilding' in input) {
        ignoreBuilding = input['ignoreBuilding'] as Boolean
    }

    Boolean ignoreGround = false
    if ('ignoreGround' in input) {
        ignoreGround = input['ignoreGround'] as Boolean
    }

    Boolean ignoreRoads = false
    if ('ignoreRoads' in input) {
        ignoreRoads = input['ignoreRoads'] as Boolean
    }

    Integer srid = 3857
    if (input['targetSRID']) {
        srid = input['targetSRID'] as Integer
    }

    Boolean removeTunnels = false
    if ('removeTunnels' in input) {
        removeTunnels = input['removeTunnels'] as Boolean
    }

    Boolean eliminateNoTrafficRoads = false
    if ('eliminateNoTrafficRoads' in input) {
        eliminateNoTrafficRoads = input['eliminateNoTrafficRoads'] as Boolean
    }

    def reader
    if (pathFile.toLowerCase(Locale.getDefault()).endsWith(".pbf")) {
        InputStream inputStream = new FileInputStream(pathFile);
        reader = new OsmosisReader(inputStream);
    } else if (pathFile.toLowerCase(Locale.getDefault()).endsWith(".osm")) {
        reader = new XmlReader(new File(pathFile), true, CompressionMethod.None);
    } else if (pathFile.toLowerCase(Locale.getDefault()).endsWith(".osm.gz")) {
        reader = new XmlReader(new File(pathFile), true, CompressionMethod.GZip);
    } else {
        throw new IllegalArgumentException("File extension not known.Should be pbf, osm or osm.gz but got " + pathFile)
    }

    OsmHandler handler = new OsmHandler(logger, ignoreBuilding, ignoreRoads, ignoreGround, removeTunnels)
    reader.setSink(handler);
    reader.run();

    logger.info('OSM Read done')

    // If eliminateNoTrafficRoads is true, filter the roads by the allowed list.
    if (eliminateNoTrafficRoads && !ignoreRoads) {
        def validRoadTypes = [
            "bus_guideway", "busway", "living_street", "motorway", "motorway_link", "primary", "primary_link",
            "raceway", "residential", "road", "secondary", "secondary_link", "service", "tertiary", "tertiary_link",
            "trunk", "trunk_link", "unclassified", "rest_area", "traffic_calming", "traffic_island"
        ]
        handler.roads = handler.roads.findAll { validRoadTypes.contains(it.type) }
    }


    if (!ignoreBuilding) {
        String tableName = "MAP_BUILDINGS_GEOM";

        sql.execute("DROP TABLE IF EXISTS " + tableName)
        sql.execute("CREATE TABLE " + tableName + '''( 
            ID_WAY integer PRIMARY KEY, 
            THE_GEOM geometry,
            HEIGHT real
        );''')

        for (Building building: handler.buildings) {
            sql.execute("INSERT INTO " + tableName + " VALUES (" + building.id + ", ST_MakeValid(ST_SIMPLIFYPRESERVETOPOLOGY(ST_Transform(ST_GeomFromText('" + building.geom + "', 4326), "+srid+"),0.1)), " + building.height + ")")
        }

        sql.execute('''
            CREATE SPATIAL INDEX IF NOT EXISTS BUILDINGS_INDEX ON ''' + tableName + '''(the_geom);
            -- List buildings that intersects with other buildings that have a greater area
            DROP TABLE IF EXISTS tmp_relation_buildings_buildings;
            CREATE TABLE tmp_relation_buildings_buildings AS SELECT s1.ID_WAY as PK_BUILDING, S2.ID_WAY as PK2_BUILDING FROM MAP_BUILDINGS_GEOM S1, MAP_BUILDINGS_GEOM S2 WHERE ST_AREA(S1.THE_GEOM) < ST_AREA(S2.THE_GEOM) AND S1.THE_GEOM && S2.THE_GEOM AND ST_DISTANCE(S1.THE_GEOM, S2.THE_GEOM) <= 0.1;
            
            -- Alter that small area buildings by removing shared area
            DROP TABLE IF EXISTS tmp_buildings_truncated;
            CREATE TABLE tmp_buildings_truncated AS SELECT PK_BUILDING, ST_DIFFERENCE(s1.the_geom, ST_BUFFER(ST_Collect(s2.the_geom), 0.1, 'join=mitre')) the_geom, s1.HEIGHT HEIGHT from tmp_relation_buildings_buildings r, MAP_BUILDINGS_GEOM s1, MAP_BUILDINGS_GEOM s2 WHERE PK_BUILDING = S1.ID_WAY AND PK2_BUILDING = S2.ID_WAY  GROUP BY PK_BUILDING;
            
            -- Merge original buildings with altered buildings 
            DROP TABLE IF EXISTS BUILDINGS;
            CREATE TABLE BUILDINGS(PK INTEGER PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT real) AS SELECT s.id_way, ST_SETSRID(s.the_geom, '''+srid+'''), s.HEIGHT from  MAP_BUILDINGS_GEOM s where id_way not in (select PK_BUILDING from tmp_buildings_truncated) UNION ALL select PK_BUILDING, ST_SETSRID(the_geom, '''+srid+'''), HEIGHT from tmp_buildings_truncated WHERE NOT st_isempty(the_geom);
    
            DROP TABLE IF EXISTS tmp_buildings_truncated;
            DROP TABLE IF EXISTS tmp_relation_buildings_buildings;
            DROP TABLE IF EXISTS MAP_BUILDINGS_GEOM;
        ''');

        sql.execute("CREATE SPATIAL INDEX IF NOT EXISTS BUILDING_GEOM_INDEX ON " + "BUILDINGS" + "(THE_GEOM)")

    }

    if (!ignoreRoads) {
        sql.execute("DROP TABLE IF EXISTS ROADS")
        sql.execute("CREATE TABLE ROADS (PK serial PRIMARY KEY, ID_WAY integer, THE_GEOM geometry, TYPE varchar, LV_D integer, LV_E integer,LV_N integer,HGV_D integer,HGV_E integer,HGV_N integer,LV_SPD_D integer,LV_SPD_E integer,LV_SPD_N integer,HGV_SPD_D integer, HGV_SPD_E integer,HGV_SPD_N integer, PVMT varchar(10));")

        for (Road road: handler.roads) {
            if (road.geom.isEmpty()) {
                continue;
            }
            String query = 'INSERT INTO ROADS(ID_WAY, ' +
                    'THE_GEOM, ' +
                    'TYPE, ' +
                    'LV_D, LV_E, LV_N, ' +
                    'HGV_D, HGV_E, HGV_N, ' +
                    'LV_SPD_D, LV_SPD_E, LV_SPD_N, ' +
                    'HGV_SPD_D, HGV_SPD_E, HGV_SPD_N, ' +
                    'PVMT) ' +
                    ' VALUES (?,' +
                    'st_setsrid(st_updatez(ST_precisionreducer(ST_SIMPLIFYPRESERVETOPOLOGY(ST_TRANSFORM(ST_GeomFromText(?, 4326), '+srid+'),0.1),1), 0.05), ' + srid + '),' +
                    '?,?,?,?,?,?,?,?,?,?,?,?,?,?);'
            sql.execute(query, [road.id, road.geom, road.type,
                                road.getNbLV("d"), road.getNbLV("e"), road.getNbLV("n"),
                                road.getNbHV("d"), road.getNbHV("e"), road.getNbHV("n"),
                                Road.speed[road.category], Road.speed[road.category], Road.speed[road.category],
                                Math.min(90, Road.speed[road.category]), Math.min(90, Road.speed[road.category]), Math.min(90, Road.speed[road.category]),
                                'NL08'])
        }
        sql.execute("CREATE SPATIAL INDEX IF NOT EXISTS ROADS_GEOM_INDEX ON " + "ROADS" + "(THE_GEOM)")
    }

    if (!ignoreGround) {
        sql.execute("DROP TABLE IF EXISTS GROUND")
        sql.execute("CREATE TABLE GROUND (PK serial PRIMARY KEY, ID_WAY int, THE_GEOM geometry, PRIORITY int, G double);")

        for (Ground ground : handler.grounds) {
            if (ground.priority == 0) {
                continue
            }
            if (ground.geom.isEmpty()) {
                continue
            }
            sql.execute("INSERT INTO GROUND (ID_WAY, THE_GEOM, PRIORITY, G) VALUES (" + ground.id + ", ST_Transform(ST_GeomFromText('" + ground.geom + "', 4326), " + srid + "), " + ground.priority + ", " + ground.coeff_G + ")")
        }
        sql.execute("CREATE SPATIAL INDEX IF NOT EXISTS GROUND_GEOM_INDEX ON " + "GROUND" + "(THE_GEOM)")
    }

    logger.info('SQL INSERT done')

    resultString = "nodes : " + handler.nb_nodes
    resultString += "<br>\n"
    resultString += "ways : " + handler.nb_ways
    resultString += "<br>\n"
    resultString += "relations : " + handler.nb_relations
    resultString += "<br>\n"
    resultString += "buildings : " + handler.nb_buildings
    resultString += "<br>\n"
    resultString += "roads : " + handler.nb_roads
    resultString += "<br>\n"
    resultString += "grounds : " + handler.nb_grounds
    resultString += "<br>\n"

    logger.info('End : Get Buildings from OSM')
    logger.info('Result : ' + resultString)
    return resultString
}

public class OsmHandler implements Sink {

    public int nb_ways = 0;
    public int nb_nodes = 0;
    public int nb_relations = 0;
    public int nb_buildings = 0;
    public int nb_roads = 0;
    public int nb_grounds = 0;

    Random rand = new Random();

    public Map<Long, Node> nodes = new HashMap<Long, Node>();
    public Map<Long, Way> ways = new HashMap<Long, Way>();
    public Map<Long, Relation> relations = new HashMap<Long, Relation>();
    public List<Building> buildings = new ArrayList<Building>();
    public List<Road> roads = new ArrayList<Road>();
    public List<Ground> grounds = new ArrayList<Ground>();

    Logger logger
    boolean ignoreBuildings
    boolean ignoreRoads
    boolean ignoreGround
    boolean removeTunnels

    OsmHandler(Logger logger, boolean ignoreBuildings, boolean ignoreRoads, boolean ignoreGround, boolean removeTunnels) {
        this.logger = logger
        this.ignoreBuildings = ignoreBuildings
        this.ignoreRoads = ignoreRoads
        this.ignoreGround = ignoreGround
        this.removeTunnels = removeTunnels
    }

    @Override
    public void initialize(Map<String, Object> arg0) {
    }

    @Override
    public void process(EntityContainer entityContainer) {



        if (entityContainer instanceof NodeContainer) {
            nb_nodes++;
            Node node = ((NodeContainer) entityContainer).getEntity();
            nodes.put(node.getId(), node);
        } else if (entityContainer instanceof WayContainer) {


            // This is a copy of the GeoClimate file : buildingsParams.json (https://github.com/orbisgis/geoclimate/tree/master/osm/src/main/resources/org/orbisgis/geoclimate/osm)
            String buildingParams = """{
                  "tags": {
                    "building": [],
                    "railway": [
                      "station",
                      "train_station"
                    ]
                  },
                  "columns": [
                    "height",
                    "roof:height",
                    "building:levels",
                    "roof:levels",
                    "building",
                    "amenity",
                    "layer",
                    "aeroway",
                    "historic",
                    "leisure",
                    "monument",
                    "place_of_worship",
                    "military",
                    "railway",
                    "public_transport",
                    "barrier",
                    "government",
                    "historic:building",
                    "grandstand",
                    "house",
                    "shop",
                    "industrial",
                    "man_made",
                    "residential",
                    "apartments",
                    "ruins",
                    "agricultural",
                    "barn",
                    "healthcare",
                    "education",
                    "restaurant",
                    "sustenance",
                    "office",
                    "tourism",
                    "roof:shape"
                  ],
                  "level": {
                    "building": 1,
                    "house": 1,
                    "detached": 1,
                    "residential": 1,
                    "apartments": 1,
                    "bungalow": 0,
                    "historic": 0,
                    "monument": 0,
                    "ruins": 0,
                    "castle": 0,
                    "agricultural": 0,
                    "farm": 0,
                    "farm_auxiliary": 0,
                    "barn": 0,
                    "greenhouse": 0,
                    "silo": 0,
                    "commercial": 2,
                    "industrial": 0,
                    "sport": 0,
                    "sports_centre": 0,
                    "grandstand": 0,
                    "transportation": 0,
                    "train_station": 0,
                    "toll_booth": 0,
                    "toll": 0,
                    "terminal": 0,
                    "healthcare": 1,
                    "education": 1,
                    "entertainment_arts_culture": 0,
                    "sustenance": 1,
                    "military": 0,
                    "religious": 0,
                    "chapel": 0,
                    "church": 0,
                    "government": 1,
                    "townhall": 1,
                    "office": 1,
                    "heavy_industry": 0,
                    "light_industry": 0,
                    "emergency": 0,
                    "hotel": 2,
                    "hospital": 2,
                    "parking": 1
                  },
                  "type": {
                    "terminal:transportation": {
                      "aeroway": [
                        "terminal",
                        "airport_terminal"
                      ],
                      "amenity": [
                        "terminal",
                        "airport_terminal"
                      ],
                      "building": [
                        "terminal",
                        "airport_terminal"
                      ]
                    },
                    "parking:transportation": {
                      "building": [
                        "parking"
                      ]
                    },
                    "monument": {
                      "building": [
                        "monument"
                      ],
                      "historic": [
                        "monument"
                      ],
                      "leisure": [
                        "monument"
                      ],
                      "monument": [
                        "yes"
                      ]
                    },
                    "chapel:religious": {
                      "building": [
                        "chapel"
                      ],
                      "amenity": [
                        "chapel"
                      ],
                      "place_of_worship": [
                        "chapel"
                      ]
                    },
                    "church:religious": {
                      "building": [
                        "church"
                      ],
                      "amenity": [
                        "church"
                      ],
                      "place_of_worship": [
                        "church"
                      ]
                    },
                    "castle:heritage": {
                      "building": [
                        "castle",
                        "fortress"
                      ]
                    },
                    "religious": {
                      "building": [
                        "religious",
                        "abbey",
                        "cathedral",
                        "mosque",
                        "musalla",
                        "temple",
                        "synagogue",
                        "shrine",
                        "place_of_worship",
                        "wayside_shrine"
                      ],
                      "amenity": [
                        "religious",
                        "abbey",
                        "cathedral",
                        "chapel",
                        "church",
                        "mosque",
                        "musalla",
                        "temple",
                        "synagogue",
                        "shrine",
                        "place_of_worship",
                        "wayside_shrine"
                      ],
                      "place_of_worship": [
                        "! no",
                        "! chapel",
                        "! church"
                      ]
                    },
                    "sport:entertainment_arts_culture": {
                      "building": [
                        "swimming_pool",
                        "fitness_centre",
                        "horse_riding",
                        "ice_rink",
                        "pitch",
                        "stadium",
                        "track"
                      ],
                      "leisure": [
                        "swimming_pool",
                        "fitness_centre",
                        "horse_riding",
                        "ice_rink",
                        "pitch",
                        "stadium",
                        "track"
                      ],
                      "amenity": [
                        "swimming_pool",
                        "fitness_centre",
                        "horse_riding",
                        "ice_rink",
                        "pitch",
                        "stadium",
                        "track"
                      ]
                    },
                    "sports_centre:entertainment_arts_culture": {
                      "building": [
                        "sports_centre",
                        "sports_hall"
                      ],
                      "leisure": [
                        "sports_centre",
                        "sports_hall"
                      ],
                      "amenity": [
                        "sports_centre",
                        "sports_hall"
                      ]
                    },
                    "military": {
                      "military": [
                        "ammunition",
                        "bunker",
                        "barracks",
                        "casemate",
                        "office",
                        "shelter"
                      ],
                      "building": [
                        "ammunition",
                        "bunker",
                        "barracks",
                        "casemate",
                        "military",
                        "shelter"
                      ],
                      "office": [
                        "military"
                      ]
                    },
                    "train_station:transportation": {
                      "building": [
                        "train_station"
                      ],
                      "railway": [
                        "station",
                        "train_station"
                      ],
                      "public_transport": [
                        "train_station"
                      ],
                      "amenity": [
                        "train_station"
                      ]
                    },
                    "townhall:government": {
                      "amenity": [
                        "townhall"
                      ],
                      "building": [
                        "townhall"
                      ]
                    },
                    "toll:transportation": {
                      "barrier": [
                        "toll_booth"
                      ],
                      "building": [
                        "toll_booth"
                      ]
                    },
                    "government": {
                      "building": [
                        "government",
                        "government_office"
                      ],
                      "government": [
                        "! no"
                      ],
                      "office": [
                        "government"
                      ]
                    },
                    "historic": {
                      "building": [
                        "historic"
                      ],
                      "historic": [
                      ],
                      "historic_building": [
                        "! no"
                      ]
                    },
                    "grandstand:entertainment_arts_culture": {
                      "building": [
                        "grandstand"
                      ],
                      "leisure": [
                        "grandstand"
                      ],
                      "amenity": [
                        "grandstand"
                      ],
                      "grandstand": [
                        "yes"
                      ]
                    },
                    "detached:residential": {
                      "building": [
                        "detached"
                      ],
                      "house": [
                        "detached"
                      ]
                    },
                    "farm_auxiliary:agricultural": {
                      "building": [
                        "farm_auxiliary",
                        "barn",
                        "stable",
                        "sty",
                        "cowshed",
                        "digester",
                        "greenhouse"
                      ]
                    },
                    "commercial": {
                      "building": [
                        "bank",
                        "bureau_de_change",
                        "boat_rental",
                        "car_rental",
                        "commercial",
                        "internet_cafe",
                        "kiosk",
                        "money_transfer",
                        "market",
                        "market_place",
                        "pharmacy",
                        "post_office",
                        "retail",
                        "shop",
                        "store",
                        "supermarket",
                        "warehouse"
                      ],
                      "amenity": [
                        "bank",
                        "bureau_de_change",
                        "boat_rental",
                        "car_rental",
                        "commercial",
                        "internet_cafe",
                        "kiosk",
                        "money_transfer",
                        "market",
                        "market_place",
                        "pharmacy",
                        "post_office",
                        "retail",
                        "shop",
                        "store",
                        "supermarket",
                        "warehouse"
                      ],
                      "shop": [
                        "!= no"
                      ]
                    },
                    "light_industry:industrial": {
                      "building": [
                        "industrial",
                        "factory",
                        "warehouse"
                      ],
                      "industrial": [
                        "factory"
                      ],
                      "amenity": [
                        "factory"
                      ]
                    },
                    "heavy_industry:industrial": {
                      "building": [
                        "digester"
                      ],
                      "industrial": [
                        "gas",
                        "heating_station",
                        "oil_mill",
                        "oil",
                        "wellsite",
                        "well_cluster"
                      ]
                    },
                    "greenhouse:agricultural": {
                      "building": [
                        "greenhouse"
                      ],
                      "amenity": [
                        "greenhouse"
                      ],
                      "industrial": [
                        "greenhouse"
                      ]
                    },
                    "silo:agricultural": {
                      "building": [
                        "silo",
                        "grain_silo"
                      ],
                      "man_made": [
                        "silo",
                        "grain_silo"
                      ]
                    },
                    "house:residential": {
                      "building": [
                        "house"
                      ],
                      "house": [
                        "! no",
                        "! detached",
                        "! residential",
                        "! villa",
                        "residential"
                      ],
                      "amenity": [
                        "house"
                      ]
                    },
                    "apartments:residential": {
                      "building": [
                        "apartments"
                      ],
                      "residential": [
                        "apartments"
                      ],
                      "amenity": [
                        "apartments"
                      ],
                      "apartments": [
                        "yes"
                      ]
                    },
                    "bungalow:residential": {
                      "building": [
                        "bungalow"
                      ],
                      "house": [
                        "bungalow"
                      ],
                      "amenity": [
                        "bungalow"
                      ]
                    },
                    "residential": {
                      "building": [
                        "residential",
                        "villa",
                        "dormitory",
                        "condominium",
                        "sheltered_housing",
                        "workers_dormitory",
                        "terrace"
                      ],
                      "residential": [
                        "university",
                        "detached",
                        "dormitory",
                        "condominium",
                        "sheltered_housing",
                        "workers_dormitory",
                        "building"
                      ],
                      "house": [
                        "residential"
                      ],
                      "amenity": [
                        "residential"
                      ]
                    },
                    "ruins:heritage": {
                      "building": [
                        "ruins"
                      ],
                      "ruins": [
                        "ruins"
                      ]
                    },
                    "agricultural": {
                      "building": [
                        "agricultural"
                      ],
                      "agricultural": [
                        "building"
                      ]
                    },
                    "farm:agricultural": {
                      "building": [
                        "farm",
                        "farmhouse"
                      ]
                    },
                    "barn:agricultural": {
                      "building": [
                        "barn"
                      ],
                      "barn": [
                        "! no"
                      ]
                    },
                    "transportation": {
                      "building": [
                        "train_station",
                        "transportation",
                        "station"
                      ],
                      "aeroway": [
                        "hangar",
                        "tower",
                        "bunker",
                        "control_tower",
                        "building"
                      ],
                      "railway": [
                        "station",
                        "train_station",
                        "building"
                      ],
                      "public_transport": [
                        "train_station",
                        "station"
                      ],
                      "amenity": [
                        "train_station",
                        "terminal"
                      ]
                    },
                    "healthcare": {
                      "amenity": [
                        "healthcare",
                        "social_facility"
                      ],
                      "building": [
                        "healthcare",
                        "hospital"
                      ],
                      "healthcare": [
                        "! no"
                      ]
                    },
                    "education": {
                      "amenity": [
                        "education",
                        "college",
                        "kindergarten",
                        "school",
                        "university",
                        "research_institute"
                      ],
                      "building": [
                        "education",
                        "college",
                        "kindergarten",
                        "school",
                        "university"
                      ],
                      "education": [
                        "college",
                        "kindergarten",
                        "school",
                        "university"
                      ]
                    },
                    "entertainment_arts_culture": {
                      "leisure": [
                        "! no"
                      ]
                    },
                    "sustenance:commercial": {
                      "amenity": [
                        "restaurant",
                        "bar",
                        "cafe",
                        "fast_food",
                        "ice_cream",
                        "pub"
                      ],
                      "building": [
                        "restaurant",
                        "bar",
                        "cafe",
                        "fast_food",
                        "ice_cream",
                        "pub"
                      ],
                      "restaurant": [
                        "! no"
                      ],
                      "shop": [
                        "restaurant",
                        "bar",
                        "cafe",
                        "fast_food",
                        "ice_cream",
                        "pub"
                      ],
                      "sustenance": [
                        "! no"
                      ]
                    },
                    "office": {
                      "building": [
                        "office"
                      ],
                      "amenity": [
                        "office"
                      ],
                      "office": [
                        "! no"
                      ]
                    },
                    "building:public": {
                      "building": [
                        "public"
                      ]
                    },
                    "emergency": {
                      "building": [
                        "fire_station"
                      ]
                    },
                    "hotel:tourism": {
                      "building": [
                        "hotel"
                      ],
                      "tourism": [
                        "hotel"
                      ]
                    },
                    "attraction:tourism": {
                      "tourism": [
                        "attraction"
                      ]
                    },
                    "building": {
                      "building": [
                        "yes"
                      ]
                    }
                  }
                }"""

            def parametersMap = new JsonSlurper().parseText(buildingParams)
            def tags = parametersMap.get("tags")
            def columnsToKeep = parametersMap.get("columns")
            def typeBuildings = parametersMap.get("type")

            nb_ways++;
            Way way = ((WayContainer) entityContainer).getEntity();
            ways.put(way.getId(), way);
            boolean isBuilding = false;
            boolean isRoad = false;
            boolean isTunnel = false;
            double height = 4.0 + rand.nextDouble() * 2.1;
            boolean trueHeightFound = false;
            boolean closedWay = way.isClosed();

            for (Tag tag : way.getTags()) {
                if (tags.containsKey(tag.getKey()) && closedWay){
                    if (tags.get(tag.getKey()).isEmpty() || tags.get(tag.getKey()).any{it == (tag.getValue())})
                    {
                        isBuilding = true;
                    }
                }

                if ( closedWay && columnsToKeep.any{ (it == tag.getKey()) }) {
                    for (typeHighLevel in typeBuildings) {
                        for (typeLowLevel in typeHighLevel.getValue()) {
                            if (typeLowLevel.getKey() == (tag.getKey())) {
                                if (typeLowLevel.getValue().any { it == (tag.getValue()) }) {
                                    isBuilding = true;
                                }
                            }

                        }
                    }
                }

                if ("tunnel".equalsIgnoreCase(tag.getKey()) && "yes".equalsIgnoreCase(tag.getValue())) {
                    isTunnel = true;
                }
                if ("highway".equalsIgnoreCase((tag.getKey()))) {
                    isRoad = true
                }
                if (isBuilding) {
                    if (!trueHeightFound && "building:levels".equalsIgnoreCase(tag.getKey())) {
                        height = height - 4 + Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", "")) * 3.0;
                    }
                    if ("height".equalsIgnoreCase(tag.getKey())) {
                        height = Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", ""));
                        trueHeightFound = true;
                    }
                }
            }
            if (!ignoreBuildings && isBuilding && closedWay) {
                buildings.add(new Building(way, height));
                nb_buildings++;
            }
            if (!ignoreRoads && isRoad) {
                if (removeTunnels && isTunnel) {
                    return
                }
                roads.add(new Road(way));
                nb_roads++;
            }
            if (!ignoreGround && !isBuilding && !isRoad && closedWay) {
                grounds.add(new Ground(way));
                nb_grounds++;
            }
        } else if (entityContainer instanceof RelationContainer) {
            nb_relations++;
            Relation rel = ((RelationContainer) entityContainer).getEntity();
            relations.put(rel.getId(), rel);
        } else {
            System.out.println("Unknown Entity!");
        }
    }

    @Override
    public void complete() {
        for(Building building: buildings) {
            building.setGeom(calculateBuildingGeometry(building.way));
        }
        for(Road road: roads) {
            road.setGeom(calculateRoadGeometry(road.way));
        }
        GeometryFactory geomFactory = new GeometryFactory();
        for(Ground ground: grounds) {
            if (ground.priority == 0) {
                ground.setGeom(geomFactory.createPolygon())
                continue
            }
            Geometry geom = calculateGroundGeometry(ground.way)
            ground.setGeom(geom)
        }
        int doPrint = 2
        for (int j = 0; j < grounds.size(); j++) {
            if (j >= doPrint) {
                logger.info("Cleaning GROUND geom : " + j + "/" + grounds.size())
                doPrint *= 2
            }
            if (grounds[j].geom.isEmpty() || !grounds[j].geom.isValid()) {
                continue
            }
            if (!["Polygon", "MultiPolygon"].contains(grounds[j].geom.geometryType)) {
                continue
            }
            for (int k = 0; k < grounds.size(); k++) {
                if (j == k) {
                    continue
                }
                if (grounds[k].geom.isEmpty() || !grounds[k].geom.isValid()) {
                    continue
                }
                if (!["Polygon", "MultiPolygon"].contains(grounds[k].geom.geometryType)) {
                    continue
                }
                if (!grounds[j].geom.intersects(grounds[k].geom)) {
                    continue
                }
                if (grounds[j].priority >= grounds[k].priority) {
                    grounds[k].geom = grounds[k].geom.difference(grounds[j].geom)
                }
                if (grounds[j].priority < grounds[k].priority) {
                    grounds[j].geom = grounds[j].geom.difference(grounds[k].geom)
                }
            }
        }
    }

    @Override
    public void close() {
    }

    public Geometry calculateBuildingGeometry(Way way) {
        GeometryFactory geomFactory = new GeometryFactory();
        if (way == null) {
            return geomFactory.createPolygon();
        }
        List<WayNode> wayNodes = way.getWayNodes();
        if (wayNodes.size() < 4) {
            return geomFactory.createPolygon();
        }
        Coordinate[] shell = new Coordinate[wayNodes.size()];
        for(int i = 0; i < wayNodes.size(); i++) {
            Node node = nodes.get(wayNodes.get(i).getNodeId());
            if (node == null) {
                return geomFactory.createPolygon();
            }
            double x = node.getLongitude();
            double y = node.getLatitude();
            shell[i] = new Coordinate(x, y, 0.0);
        }
        return geomFactory.createPolygon(shell);
    }

    public Geometry calculateRoadGeometry(Way way) {
        GeometryFactory geomFactory = new GeometryFactory();
        if (way == null) {
            return geomFactory.createLineString();
        }
        List<WayNode> wayNodes = way.getWayNodes();
        if (wayNodes.size() < 2) {
            return geomFactory.createLineString();
        }
        Coordinate[] coordinates = new Coordinate[wayNodes.size()];
        for(int i = 0; i < wayNodes.size(); i++) {
            Node node = nodes.get(wayNodes.get(i).getNodeId());
            if (node == null) { // some odd case where a node is defined here but outside of the osm file limits
                return geomFactory.createLineString();
            }
            double x = node.getLongitude();
            double y = node.getLatitude();
            coordinates[i] = new Coordinate(x, y, 0.0);
        }
        return geomFactory.createLineString(coordinates);
    }

    public Geometry calculateGroundGeometry(Way way) {
        GeometryFactory geomFactory = new GeometryFactory();
        if (way == null) {
            return geomFactory.createPolygon();
        }
        List<WayNode> wayNodes = way.getWayNodes();
        if (wayNodes.size() < 4) {
            return geomFactory.createPolygon();
        }
        Coordinate[] shell = new Coordinate[wayNodes.size()];
        for (int i = 0; i < wayNodes.size(); i++) {
            Node node = nodes.get(wayNodes.get(i).getNodeId());
            double x = node.getLongitude();
            double y = node.getLatitude();
            shell[i] = new Coordinate(x, y, 0.0);
        }
        return geomFactory.createPolygon(shell);
    }
}

public class Building {

    long id;
    Way way;
    Geometry geom;
    double height = 0.0;

    Building(Way way) {
        this.way = way;
        this.id = way.getId();
        double h = 4.0 + rand.nextDouble() * 2.1;
        boolean trueHeightFound = false;
        for (Tag tag : way.getTags()) {
            if (!trueHeightFound && "building:levels".equalsIgnoreCase(tag.getKey())) {
                h = h - 4 + Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", "")) * 3.0;
            }
            if ("height".equalsIgnoreCase(tag.getKey())) {
                h = Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", ""));
                trueHeightFound = true;
            }
        }
        this.height = h;
    }
    Building(Way way, double height) {
        this.way = way;
        this.id = way.getId();
        this.height = height;
    }

    void setGeom(Geometry geom) {
        this.geom = geom;
    }

    void setHeight(double height) {
        this.height = height;
    }
}

public class Road {

    def static aadf_d = [26103, 17936, 7124, 1400, 700, 350, 175]
    def static aadf_e = [7458, 3826, 1069, 400, 200, 100, 50]
    def static aadf_n = [3729, 2152, 712, 200, 100, 50, 25]
    def static hv_d = [0.25, 0.2, 0.2, 0.15, 0.10, 0.05, 0.02]
    def static hv_e = [0.35, 0.2, 0.15, 0.10, 0.06, 0.02, 0.01]
    def static hv_n = [0.45, 0.2, 0.1, 0.05, 0.03, 0.01, 0.0]
    def static speed = [130, 110, 80, 80, 50, 30, 30]

    def static hours_in_d = 12
    def static hours_in_e = 4
    def static hours_in_n = 8

    long id;
    Way way;
    Geometry geom;
    double maxspeed = 0.0;
    boolean oneway = false;
    String type = null;
    int category = 5;

    Road(Way way) {
        this.way = way;
        this.id = way.getId();
        for (Tag tag : way.getTags()) {
            if ("maxspeed".equalsIgnoreCase(tag.getKey())) {
                try {
                    this.maxspeed = Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", ""));
                }
                catch (NumberFormatException e) {
                    // in case maxspeed does not contain a numerical value
                }
                if (tag.getValue().contains("mph")) {
                    maxspeed = maxspeed * 1.60934
                }
            }
            if ("highway".equalsIgnoreCase(tag.getKey())) {
                this.type = tag.getValue();
            }
            if ("highway".equalsIgnoreCase(tag.getKey()) && "yes".equalsIgnoreCase(tag.getValue())) {
                oneway = true
            }
        }
        updateCategory();
    }

    double getNbLV(String period) {
        double lv
        if (period == "d") {
            lv = (1 - hv_d[category]) * aadf_d[category] / hours_in_d
        }
        else if (period == "e") {
            lv = (1 - hv_e[category]) * aadf_e[category] / hours_in_e
        }
        else { // n
            lv = (1 - hv_n[category]) * aadf_n[category] / hours_in_n
        }
        if (oneway) {
            lv /= 2
        }
        return lv
    }

    double getNbHV(String period) {
        double hv
        if (period == "d") {
            hv = hv_d[category] * aadf_d[category] / hours_in_d
        }
        else if (period == "e") {
            hv = hv_e[category] * aadf_e[category] / hours_in_e
        }
        else { // n
            hv = hv_n[category] * aadf_n[category] / hours_in_n
        }
        if (oneway) {
            hv /= 2
        }
        return hv
    }

    void updateCategory() {
        if (["motorway", "motorway_link"].contains(type)) {
            category = 0
        }
        if (["trunk", "trunk_link"].contains(type)) {
            category = 1
        }
        if (["primary", "primary_link"].contains(type)) {
            category = 2
        }
        if (["secondary", "secondary_link"].contains(type)) {
            category = 3
        }
        if (["tertiary", "tertiary_link", "unclassified"].contains(type)) {
            category = 4
        }
        if (["residential"].contains(type)) {
            category = 5
        }
        if (["service", "living_street"].contains(type)) {
            category = 6
        }
    }

    void setGeom(Geometry geom) {
        this.geom = geom;
    }

    void setHeight(double height) {
        this.height = height;
    }
}

public class Ground {

    long id;
    Way way;
    Geometry geom;

    int priority = 0;
    float coeff_G = 0.0;

    Ground(Way way) {
        this.way = way;
        this.id = way.getId();

        String primaryTagKey = "";
        String primaryTagValue = "";
        String secondaryTagKey = "";
        String secondaryTagValue = "";

        for (Tag tag : way.getTags()) {
            String key = tag.getKey()
            String value = tag.getValue()
            if (["aeroway","amenity","landcover","landuse","leisure","natural","water","waterway"].contains(key)) {
                primaryTagKey = key
                primaryTagValue = value
            }
            if (["parking","covered","surface","wetland"].contains(key)) {
                secondaryTagKey = key
                secondaryTagValue = value
            }
        }
        if (primaryTagKey == "aeroway" &&
                ["taxiway","runway","aerodrome","helipad","apron","taxilane"].contains(primaryTagValue)) {
            priority = 29
            coeff_G = 0.1
        }
        if (primaryTagKey == "amenity") {
            if (primaryTagValue == "taxi") {
                priority = 22
                coeff_G = 0.1
            }
            if (primaryTagValue == "parking" && secondaryTagKey == "parking" && !secondaryTagValue.contains("underground")) {
                priority = 22
                coeff_G = 0.1
            }
        }
        if (primaryTagKey == "landcover") {
            if (primaryTagValue == "water") {
                priority = 7
                coeff_G = 0.3
            }
            if (["bedrock","bare_ground","concrete","asphalt"].contains(primaryTagValue)) {
                priority = 8
                coeff_G = 0.1
            }
            if (primaryTagValue == "sand") {
                priority = 9
                coeff_G = 0.2
            }
            if (["scrub","gravel"].contains(primaryTagValue)) {
                priority = 10
                coeff_G = 0.7
            }
            if (["bushes","vegetation"].contains(primaryTagValue)) {
                priority = 11
                coeff_G = 0.8
            }
            if (["flowerbed","trees, grass","trees","grass","tree","grassland","wood"].contains(primaryTagValue)) {
                priority = 12
                coeff_G = 1.0
            }
        }
        if (primaryTagKey == "landuse") {
            if (primaryTagValue == "reservoir" && secondaryTagKey == "covered" && secondaryTagValue == "no") {
                priority = 7
                coeff_G = 0.3
            }
            if (["residential","industrial","retail","harbour","quarry","landfill",
                 "construction","commercial","garages","railway","basin","farmyard"].contains(primaryTagValue)) {
                priority = 23
                coeff_G = 0.1
            }
            if (primaryTagValue == "brownfield") {
                priority = 24
                coeff_G = 0.3
            }
            if (primaryTagValue == "salt_pond") {
                priority = 25
                coeff_G = 0.5
            }
            if (["farmland","allotements","logging","plant_nursery","farm"].contains(primaryTagValue)) {
                priority = 26
                coeff_G = 0.7
            }
            if (["vineyard","orchard","greenfield","village_green"].contains(primaryTagValue)) {
                priority = 27
                coeff_G = 0.8
            }
            if (["meadow","forest","grass"].contains(primaryTagValue)) {
                priority = 28
                coeff_G = 1.0
            }
        }
        if (primaryTagKey == "leisure") {
            if (primaryTagValue == "pitch" && secondaryTagKey == "surface") {
                if (["asphalt","concrete","concrete:plate"].contains(secondaryTagValue)) {
                    priority = 1
                    coeff_G = 0.1
                }
                if (["dirt","compacted","sand","wood","clay"].contains(secondaryTagValue)) {
                    priority = 2
                    coeff_G = 0.2
                }
                if (["ground","fine_gravel","earth","mud"].contains(secondaryTagValue)) {
                    priority = 4
                    coeff_G = 0.5
                }
                if (["gravel"].contains(secondaryTagValue)) {
                    priority = 5
                    coeff_G = 0.7
                }
                if (["grass"].contains(secondaryTagValue)) {
                    priority = 6
                    coeff_G = 1.0
                }
            }
            if (primaryTagValue == "marina") {
                priority = 30
                coeff_G = 0.2
            }
            if (primaryTagValue == "park") {
                priority = 31
                coeff_G = 0.7
            }
            if (["garden","nature_reserve","golf_course"].contains(primaryTagValue)) {
                priority = 32
                coeff_G = 1.0
            }
        }
        if (primaryTagKey == "natural") {
            if (primaryTagValue == "beach" && secondaryTagKey == "surface") {
                if (secondaryTagValue == "sand") {
                    priority = 2
                    coeff_G = 0.2
                }
                if (secondaryTagValue == "shingle") {
                    priority = 3
                    coeff_G = 0.3
                }
                if (secondaryTagValue == "gravel" || secondaryTagValue == "pebbles") {
                    priority = 5
                    coeff_G = 0.7
                }
            }
            if (primaryTagValue == "wetland" && secondaryTagKey == "wetland") {
                if (secondaryTagValue == "tidalflat") {
                    priority = 14
                    coeff_G = 0.2
                }
                if (secondaryTagValue == "saltern") {
                    priority = 15
                    coeff_G = 0.3
                }
                if (secondaryTagValue == "marsh") {
                    priority = 16
                    coeff_G = 0.4
                }
                if (secondaryTagValue == "reebed") {
                    priority = 18
                    coeff_G = 0.6
                }
                if (secondaryTagValue == "bog") {
                    priority = 19
                    coeff_G = 0.7
                }
                if (secondaryTagValue == "mangrove") {
                    priority = 21
                    coeff_G = 1.0
                }
                if (["swamp","saltmarsh","wet_meadow"].contains(secondaryTagValue)) {
                    priority = 20
                    coeff_G = 0.9
                }
            }
            if (primaryTagValue == "bare_rock") {
                priority = 13
                coeff_G = 0.1
            }
            if (primaryTagValue == "bay") {
                priority = 7
                coeff_G = 0.3
            }
            if (primaryTagValue == "glacier") {
                priority = 13
                coeff_G = 0.1
            }
            if (primaryTagValue == "heath" || primaryTagValue == "grassland") {
                priority = 21
                coeff_G = 1.0
            }
            if (primaryTagValue == "rock") {
                priority = 13
                coeff_G = 0.1
            }
            if (primaryTagValue == "sand") {
                priority = 14
                coeff_G = 0.2
            }
            if (primaryTagValue == "scree") {
                priority = 17
                coeff_G = 0.5
            }
            if (primaryTagValue == "scrub") {
                priority = 19
                coeff_G = 0.7
            }
            if (primaryTagValue == "shingle") {
                priority = 15
                coeff_G = 0.3
            }
            if (primaryTagValue == "water") {
                priority = 7
                coeff_G = 0.3
            }
            if (primaryTagValue == "wood") {
                priority = 21
                coeff_G = 1.0
            }
        }
        if (primaryTagKey == "water") {
            if (["pond","lake","reservoir","river","wastewater","canal","oxbow",
                 "salt","lagoon","yes","ditch","salt_pool","tidal","stream",
                 "fishpond","riverbank","pool","lock","natural","shallow",
                 "salt_pond","lake;pond","marsh","well","reflecting_pool",
                 "fountain","stream;river","not_deep"].contains(primaryTagValue)) {
                priority = 7
                coeff_G = 0.3
            }
        }
        if (primaryTagKey == "waterway") {
            if (["stream","riverbank","canal","artificial"].contains(primaryTagValue)) {
                priority = 7
                coeff_G = 0.3
            }
        }
    }

    void setGeom(Geometry geom) {
        this.geom = geom;
    }
}

