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

title = 'Enrich DEM with lines'
description = '&#10145;&#65039; Insert altimetric points coming from linestring input layers into the input DEM. </br>' +
              '<hr>' +
              'This script works with two input layers:</br>' +
              ' <ul>' +
                '<li>Digital Elevation Model (DEM) to be enriched</li>' +
                '<li>A linestring layer (e.g: hydrographic network, ...) in which coordinates have a Z dimension</li>' +
              '</ul>' +
              'And three optionnal parameters:</br>' +
              ' <ul>' +
                '<li>Input SRID (inputSRID): SRID of the input tables</li>' +
                '<li>Source (source): Text indicating the source of the linestring layer. Can be useful to distinguish the points in the resulting DEM . If not specified, "LINESTRING" is applied</li>' +
                '<li>Output suffixe (outputSuffixe): Suffixe applied at the end of the resuling table name. If not specified, "ENRICHED" is applied</li>' +
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
        inputLine : [
                name       : 'Input Linestring',
                title      : 'Input Linestring table',
                description: 'Name of the input Linestring table',
                type       : String.class
        ],
        source : [
                name       : 'Source',
                title      : 'Source',
                description: 'Text indicating the source of the linestring layer (Optionnal) </br> </br>'+
                             '&#128736; If not specified, "LINESTRING" is applied',
                min        : 0, max: 1,
                type       : String.class
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
    
    String inputDEM = input["inputDEM"] as String
    String inputLine = input["inputLine"] as String

    // If no SRID provided, the one from DEM layer is applied
    Integer srid = 0
    if ('inputSRID' in input) {
        srid = input["inputSRID"] as Integer
    }
    else {
        srid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(inputDEM))
    }

    // If no source provided, LINESTRING is applied
    String source = 'LINESTRING'
    if ('source' in input) {
        source = input["source"] as String
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
    logger.info('# Linestring table: ' + inputLine)
    logger.info('# Source: ' + source)
    logger.info('# Output suffixe: ' + outputSuffixe)
    logger.info('--------------------------------------------')

    logger.info('Start enrich the DEM')

    def sql = new Sql(connection)


    def import_dem_without_source = """
    ------------
    -- Import DEM

    DROP TABLE IF EXISTS dem_to_enrich;
    CREATE TABLE dem_to_enrich (THE_GEOM geometry, SOURCE varchar) AS SELECT THE_GEOM, 'DEM' as SOURCE FROM $inputDEM;
        
    -- DEM: layer $inputDEM imported
    """

    def import_dem_with_source = """
    ------------
    -- Import DEM

    DROP TABLE IF EXISTS dem_to_enrich;
    CREATE TABLE dem_to_enrich AS SELECT THE_GEOM, SOURCE FROM $inputDEM;
        
    -- DEM: layer $inputDEM imported
    """

    def import_line = """
    ------------
    -- Import linestrings

    DROP TABLE IF EXISTS dem_linestring;
    CREATE TABLE dem_linestring AS SELECT THE_GEOM FROM $inputLine;
    CREATE SPATIAL INDEX ON dem_linestring(THE_GEOM);
    ALTER TABLE dem_linestring ADD PK_LINE INT AUTO_INCREMENT NOT NULL;
    ALTER TABLE dem_linestring add primary key(PK_LINE);
    
    -- Linestrings: layer $inputLine imported
    """

    def enrich_line = """    
    ----------------------------------
    -- Start enrich the DEM in a new layer called $enrichedDEM

    ------------
    -- Insert Linestrings into $enrichedDEM

    DROP TABLE IF EXISTS LINESTRING_DENSIFY;
    CREATE TABLE LINESTRING_DENSIFY AS SELECT ST_ToMultiPoint(ST_Densify(st_force2D(THE_GEOM), 5)) THE_GEOM, PK_LINE FROM dem_linestring WHERE st_length(st_simplify(THE_GEOM, 2)) > 0 ;
    INSERT INTO dem_to_enrich(THE_GEOM, SOURCE) SELECT st_setsrid(ST_MakePoint(ST_X(P.THE_GEOM), ST_Y(P.THE_GEOM), ST_Z(ST_ProjectPoint(P.THE_GEOM,L.THE_GEOM))), $srid) THE_GEOM, '$source' 
        FROM ST_EXPLODE('LINESTRING_DENSIFY') P, dem_linestring L WHERE P.PK_LINE = L.PK_LINE;

    DROP TABLE IF EXISTS LINESTRING_DENSIFY;
    
    -- $enrichedDEM enriched with Linestrings
    """
    
    def enrich_final = """
    
    DROP TABLE IF EXISTS $enrichedDEM;
    ALTER TABLE dem_to_enrich RENAME TO $enrichedDEM;
    -- Create a spatial index on $enrichedDEM
    CREATE SPATIAL INDEX ON $enrichedDEM (THE_GEOM);

    ----------------------------------
    -- Remove non needed tables
    
    DROP TABLE IF EXISTS dem_linestring, dem_to_enrich;

    -- DEM successfully enriched in the table $enrichedDEM
    """

    StringBuilder stringBuilder = new StringBuilder()
    // print to command window
    def engine = new SimpleTemplateEngine()

    if (JDBCUtilities.hasField(connection, TableLocation.parse(inputDEM), 'SOURCE'))
        {
        stringBuilder.append(import_dem_with_source) 
    }
    else{
        stringBuilder.append(import_dem_without_source) 
    }

    stringBuilder.append(import_line)
    stringBuilder.append(enrich_line)
    stringBuilder.append(enrich_final)

    def binding = ["inputDEM": inputDEM, "inputLine":inputLine, "source": source, "outputSuffixe": outputSuffixe, "srid": srid]
    def template = engine.createTemplate(stringBuilder.toString()).make(binding)
    parseScript(template.toString(), sql, progress, logger)

    return "DEM successfully enriched in the table " + enrichedDEM
    }
