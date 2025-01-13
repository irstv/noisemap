/**
 * NoiseModelling is an open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by the DECIDE team FROM the Lab-STICC (CNRS) and by the Mixt Research Unit in Environmental Acoustics (Université Gustave Eiffel).
 * <http://noise-planet.org/noisemodelling.html>
 *
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 *
 * Contact: contact@noise-planet.org
 *
 */

/**
 * @Author Pierre Aumond, Université Gustave Eiffel
 * @Author Nicolas Fortin, Université Gustave Eiffel
 * @Author Gwendall Petit, Lab-STICC CNRS UMR 6285 
 */


import geoserver.GeoServer
import geoserver.catalog.Store
import org.geotools.jdbc.JDBCDataStore

import groovy.sql.Sql
import groovy.text.SimpleTemplateEngine
import groovy.transform.CompileStatic
import org.h2.util.ScriptReader
import org.h2gis.api.ProgressVisitor
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement

title = 'Enrich DEM with roads'
description = '&#10145;&#65039; Insert altimetric points coming from roads into the input DEM.</br>' +
              '<hr>' +
              'This script works with two input layers:</br>' +
              ' <ul>' +
                '<li>Digital Elevation Model (DEM) to be enriched</li>' +
                '<li>Roads</li>' +
              '</ul>' +
              'And four parameters:</br>' +
              ' <ul>' +
                '<li>Roads right-of-way (roadWidth): Name of column where the road right-of-way is stored (Mandatory)</li>' +
                '<li>Road platform height (hRoad): Roads platform height (Optionnal). Default value = 0.0m</li>' +
                '<li>Input SRID (inputSRID): SRID of the input tables (Optionnal)</li>' +
                '<li>Output suffixe (outputSuffixe): Suffixe applied at the end of the resuling table name (Optionnal). If not specified, "ENRICHED" is applied</li>' +
              '</ul>'


inputs = [
        inputSRID : [
                name       : 'Input SRID',
                title      : 'Input SRID',
                description: '&#127757; SRID of the input tables. </br> </br>'+
                             '&#128736; If not specified, the SRID from DEM layer is applied. If DEM has no SRID, 0 is applied',
                min        : 0, max: 1,
                type       : Integer.class
        ],
        inputDEM : [
                name       : 'Input DEM',
                title      : 'Input DEM table',
                description: 'Name of the input DEM table to be enriched',
                type       : String.class
        ],
        inputRoad : [
                name       : 'Input Roads',
                title      : 'Input roads table',
                description: 'Name of the input roads table',
                type       : String.class
        ],
        roadWidth : [
                name       : 'Road width',
                title      : 'Road width',
                description: 'Name of column where the road width is stored',
                type       : String.class
        ],
        hRoad : [
                name       : 'Roads platform height',
                title      : 'Roads platform height',
                description: 'Roads platform height (in meters) (Optionnal)  </br> </br>'+
                             '&#128736; Default value = <b>0</b>',
                min        : 0, max: 1,
                type       : double.class
        ],
        outputSuffixe : [
                name       : 'Output suffixe',
                title      : 'Output suffixe',
                description: 'Suffixe applied at the end of the resuling table name </br> </br>'+
                             '&#128736; If not specified, "ENRICHED" is applied',
                min        : 0, max: 1,
                type       : String.class
        ]
]

outputs = [
        result: [
                name       : 'Result output string',
                title      : 'Result output string',
                description: 'This type of result does not allow the blocks to be linked together.',
                type       : String.class
        ]
]

@CompileStatic
static def parseScript(String sqlInstructions, Sql sql, ProgressVisitor progressVisitor, Logger logger) {
    Reader reader = null
    ByteArrayInputStream s = new ByteArrayInputStream(sqlInstructions.getBytes())
    InputStream is = s
    List<String> statementList = new LinkedList<>()
    try {
        reader  = new InputStreamReader(is)
        ScriptReader scriptReader = new ScriptReader(reader)
        scriptReader.setSkipRemarks(false)
        String statement = scriptReader.readStatement()
        while (statement != null && !statement.trim().isEmpty()) {
            statementList.add(statement)
            statement = scriptReader.readStatement()
        }
    } finally {
        reader.close()
    }
    int idStatement = 0
    final int nbStatements = statementList.size()
    ProgressVisitor evalProgress = progressVisitor.subProcess(nbStatements)
    for(String statement : statementList) {
        for(String subStatement : statement.split("\n")) {
            if(subStatement.trim().startsWith("--")){
                logger.info(String.format(Locale.ROOT, "%d/%d %s", idStatement + 1, nbStatements, subStatement.replace("--", "")))            
            }
        } 
        idStatement++
        sql.execute(statement)
        evalProgress.endStep()
        if(evalProgress.isCanceled()) {
            throw new SQLException("Canceled by user")
        }
    }
}


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



def exec(Connection connection, input) {


    //------------------------------------------------------
    // Clean the database before starting the importation

    List<String> ignorelst = ["SPATIAL_REF_SYS", "GEOMETRY_COLUMNS"]

    // Build the result string with every tables
    StringBuilder sb = new StringBuilder()

    // Get every table names
    List<String> tables = JDBCUtilities.getTableNames(connection, null, "PUBLIC", "%", null)

    // Loop over the tables
    tables.each { t ->
        TableLocation tab = TableLocation.parse(t)
        if (!ignorelst.contains(tab.getTable())) {
            // Add the name of the table in the string builder
            if (sb.size() > 0) {
                sb.append(" || ")
            }
            sb.append(tab.getTable())
            // Create a connection statement to interact with the database in SQL
            Statement stmt = connection.createStatement()
            // Drop the table
            //stmt.execute("drop table if exists " + tab)
        }
    }

    //------------------------------------------------------


    // output string, the information given back to the user
    String resultString = null

    // Create a logger to display messages in the geoserver logs and in the command prompt.
    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")

    ProgressVisitor progressVisitor

    if("progressVisitor" in input) {
        progressVisitor = input["progressVisitor"] as ProgressVisitor
    } else {
        progressVisitor = new RootProgressVisitor(1, true, 1);
    }

    ProgressVisitor progress = progressVisitor.subProcess(2)

    // Get provided parameters
    
    String inputDEM = input["inputDEM"]
    //String enrichedDEM = input["inputDEM"] += "_ENRICHED"
    String inputRoad = input["inputRoad"]
    String roadWidth = input["roadWidth"]

    // Initialize road platform height. Default value is 0m
    double hRoad = 0
    if ('hRoad' in input) {
        hRoad = input["hRoad"] as double
    }

    // If no SRID provided, the one from DEM layer is applied
    Integer srid = 0
    if ('inputSRID' in input) {
        srid = input["inputSRID"] as Integer
    }
    else {
        srid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(inputDEM))
    }

    // If no output table name (outputSuffixe) provided, ENRICHED is applied
    String outputSuffixe = 'ENRICHED'
    if ('outputSuffixe' in input) {
        outputSuffixe = input["outputSuffixe"] as String
    }
    String enrichedDEM = input["inputDEM"] + "_" + input["outputSuffixe"] as String

    // print to command window
    logger.info('List of the input parameters:')
    logger.info('--------------------------------------------')
    logger.info('# SRID: ' + srid)
    logger.info('# DEM table: ' + inputDEM)
    logger.info('# Roads network table: ' + inputRoad)
    logger.info('# Roads width column: ' + roadWidth)
    logger.info('# Roads platform height: ' + hRoad)
    logger.info('# Output suffixe: ' + outputSuffixe)
    logger.info('--------------------------------------------')

    logger.info('Start enrich the DEM')

    def sql = new Sql(connection)

    def import_dem = """
    ------------
    -- Import DEM

    DROP TABLE IF EXISTS dem_to_enrich;
    CREATE TABLE dem_to_enrich AS SELECT THE_GEOM, 'DEM' as SOURCE FROM $inputDEM;
    
    -- DEM: layer $inputDEM imported
    """

    def import_roads = """
    ------------
    -- Import roads (that are on the floor --> POS_SOL=0)
    -- Road width is precalculated into WIDTH column. When largeur < 3, then 3m

    DROP TABLE IF EXISTS dem_roads;
    CREATE TABLE dem_roads AS SELECT THE_GEOM, 'ROAD' as SOURCE, (CASE WHEN $roadWidth>3 THEN $roadWidth/2 ELSE 1.5 END) as WIDTH 
        FROM $inputRoad WHERE POS_SOL = '0' AND st_zmin(THE_GEOM) > 0;
    CREATE SPATIAL INDEX ON dem_roads(THE_GEOM);
    ALTER TABLE dem_roads ADD PK_LINE INT AUTO_INCREMENT NOT NULL;
    ALTER TABLE dem_roads add primary key(PK_LINE);
    
    -- Roads: layer $inputRoad imported
    """

    def enrich_roads = """
    ------------
    -- Insert roads platform into $enrichedDEM

    DROP TABLE DEM_WITHOUT_PTLINE IF EXISTS;
    CREATE TABLE DEM_WITHOUT_PTLINE(THE_GEOM geometry(POINTZ, $srid), source varchar) AS SELECT st_setsrid(THE_GEOM, $srid), SOURCE FROM dem_to_enrich;
    -- Remove DEM points that are less than $roadWidth far FROM roads
    DELETE FROM DEM_WITHOUT_PTLINE WHERE EXISTS (SELECT 1 FROM dem_roads b 
        WHERE ST_EXPAND(DEM_WITHOUT_PTLINE.THE_GEOM, 20) && b.THE_GEOM 
        AND ST_DISTANCE(DEM_WITHOUT_PTLINE.THE_GEOM, b.THE_GEOM)< b.WIDTH+5 LIMIT 1) ;
    
    -- Create buffer points from roads and copy the elevation from the roads to the point
    DROP TABLE IF EXISTS BUFFERED_PTLINE;
    -- The buffer size correspond to the greatest value between $roadWidth and 3m. If $roadWidth is null or lower than 3m, then 3m is returned
    CREATE TABLE BUFFERED_PTLINE AS SELECT ST_ToMultiPoint(ST_Densify(ST_Buffer(ST_Simplify(st_force2D(THE_GEOM), 2), WIDTH, 'endcap=flat join=mitre'), 5)) THE_GEOM, PK_LINE 
        FROM dem_roads WHERE st_length(st_simplify(THE_GEOM, 2)) > 0;
    INSERT INTO DEM_WITHOUT_PTLINE(THE_GEOM, SOURCE) SELECT st_setsrid(ST_MakePoint(ST_X(P.THE_GEOM), ST_Y(P.THE_GEOM), ST_Z(ST_ProjectPoint(P.THE_GEOM,L.THE_GEOM))), $srid) THE_GEOM, 'ROA' 
        FROM ST_EXPLODE('BUFFERED_PTLINE') P, dem_roads L WHERE P.PK_LINE = L.PK_LINE;
    
    -- $enrichedDEM enriched with roads
    """
    
    def enrich_final = """
    
    DROP TABLE IF EXISTS $enrichedDEM;
    ALTER TABLE DEM_WITHOUT_PTLINE RENAME TO $enrichedDEM;
    -- Create a spatial index on $enrichedDEM
    CREATE SPATIAL INDEX ON $enrichedDEM (THE_GEOM);

    ----------------------------------
    -- Remove non needed tables
    
    DROP TABLE IF EXISTS DEM_ORO, DEM_HYDRO, DEM_RAIL, DEM_ROADS, BUFFERED_D2, BUFFERED_D3, BUFFERED_D4, BUFFERED_PTLINE, dem_to_enrich;

    -- DEM successfully enriched in the table $enrichedDEM
    """

    StringBuilder stringBuilder = new StringBuilder()
    // print to command window
    def engine = new SimpleTemplateEngine()

    stringBuilder.append(import_dem)
    stringBuilder.append(import_roads)
    stringBuilder.append(enrich_roads)
    stringBuilder.append(enrich_final)

    def binding = ["inputDEM": inputDEM, "inputRoad": inputRoad, "roadWidth": roadWidth, "outputSuffixe": outputSuffixe, "srid": srid, "hRoad": hRoad]
    def template = engine.createTemplate(stringBuilder.toString()).make(binding)
    parseScript(template.toString(), sql, progress, logger)

    return "DEM successfully enriched in the table " + enrichedDEM
}