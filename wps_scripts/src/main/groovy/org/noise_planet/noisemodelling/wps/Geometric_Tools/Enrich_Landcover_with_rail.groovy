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

title = 'Enrich Landcover with railways'
description = '&#10145;&#65039; Insert rail ground surfaces into the input LANDCOVER.</br>' +
              '<hr>' +
              'This script works with two input layers:</br>' +
              ' <ul>' +
                '<li>Landcover to be enriched</li>' +
                '<li>Railways</li>' +
              '</ul>' +
              'And four parameters:</br>' +
              ' <ul>' +
                '<li>Railroads right-of-way (railWidth): Name of column where the railroad right-of-way is stored (Mandatory)</li>' +
                '<li>Rail platform height (hRail): Railways platform height (Optionnal). Default value = 0.5m</li>' +
                '<li>Input SRID (inputSRID): SRID of the input tables (Optionnal)</li>' +
                '<li>Output suffixe (outputSuffixe): Suffixe applied at the end of the resuling table name (Optionnal). If not specified, "ENRICHED" is applied</li>' +
              '</ul>' +
              '<hr>' +
              'In the schema below, orange points will be inserted into the DEM. d2, d3 and d4 are deduced from the information provided in the parameter <b>railWidth</b>, using the following formula:' +
              '<ul>' +
                '<li>d2 = (railWidth - 5.5)/2</li>' +
                '<li>d3 = (railWidth - 4)/2</li>' +
                '<li>d4 = (railWidth)/2</li>' +
              '</ul>' +
              '<img src="wps_images/railway_plateform.png" alt="Railways platform" width="95%" align="center">'

inputs = [
        inputSRID : [
                name       : 'Input SRID',
                title      : 'Input SRID',
                description: '&#127757; SRID of the input tables. </br> </br>'+
                             '&#128736; If not specified, the SRID from DEM layer is applied. If DEM has no SRID, 0 is applied',
                min        : 0, max: 1,
                type       : Integer.class
        ],
        inputLandcover : [
                name       : 'Input landcover',
                title      : 'Input landcover table',
                description: 'Name of the input landcover table',
                type       : String.class
        ],
        gColumn : [
                name       : 'G column',
                title      : 'G column',
                description: 'Ground absorption coeffecient (G) column name',
                type       : String.class
        ],
        inputRail : [
                name       : 'Input Railways',
                title      : 'Input railways table',
                description: 'Name of the input railways table',
                type       : String.class
        ],
        railWidth : [
                name       : 'Railways width',
                title      : 'Railways width',
                description: 'Name of column where the railways width is stored',
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
    
    String inputLandcover = input["inputLandcover"]
    String gColumn = input["gColumn"]
    String inputRail = input["inputRail"]
    String railWidth = input["railWidth"]
    

    // If no SRID provided, the one from DEM layer is applied
    Integer srid = 0
    if ('inputSRID' in input) {
        srid = input["inputSRID"] as Integer
    }
    else {
        srid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(inputLandcover))
    }

    // If no output table name (outputSuffixe) provided, ENRICHED is applied
    String outputSuffixe = 'ENRICHED'
    if ('outputSuffixe' in input) {
        outputSuffixe = input["outputSuffixe"] as String
    }
    String enrichedLandcover = input["inputLandcover"] + "_" + input["outputSuffixe"] as String

    // print to command window
    logger.info('List of the input parameters:')
    logger.info('--------------------------------------------')
    logger.info('# SRID: ' + srid)
    logger.info('# Landcover table: ' + inputLandcover)
    logger.info('# Landcover G column: ' + gColumn)
    logger.info('# Railways network table: ' + inputRail)
    logger.info('# Railways width column: ' + railWidth)
    logger.info('# Output suffixe: ' + outputSuffixe)
    logger.info('--------------------------------------------')

    logger.info('Start enrich the Landcover')

    def sql = new Sql(connection)


    def initPlatform = """
    -- Initialize the rail platform table

    DROP TABLE IF EXISTS PLATEFORM;
    CREATE TABLE PLATEFORM (idPlatform varchar Primary Key, d1 float, g1 float, g2 float, g3 float, h1 float, h2 float);

    INSERT INTO PLATEFORM VALUES ('SNCF', 1.435, 0, 1, 1, 0.5, 0.18);

    -- Rail platform: layer PLATEFORM imported
    """


    def import_landcover = """
    ------------
    -- Import Landcover
    -- Only geometries where $gColumn is higher than 0 are kept

    DROP TABLE IF EXISTS landcover_to_enrich;
    CREATE TABLE landcover_to_enrich AS SELECT THE_GEOM, $gColumn FROM $inputLandcover WHERE $gColumn>0;
    CREATE SPATIAL INDEX ON landcover_to_enrich(the_geom);

    -- Landcover: layer $inputLandcover imported
    """

    def import_rail = """
    ------------
    -- Import railways (that are on the floor --> POS_SOL=0)

    DROP TABLE IF EXISTS landcover_rail;    
    CREATE TABLE landcover_rail AS SELECT a.THE_GEOM, a.$railWidth - 5.5 as d2, a.$railWidth - 4 as d3, a.$railWidth as d4, 
        p.idplatform, p.d1, p.g1, p.g2, p.g3
        FROM $inputRail a, PLATEFORM p 
        WHERE st_zmin(a.THE_GEOM) > 0 AND p.idplatform ='SNCF';

    CREATE SPATIAL INDEX ON landcover_rail(THE_GEOM);
    ALTER TABLE landcover_rail ADD PK_LINE INT AUTO_INCREMENT NOT NULL;
    ALTER TABLE landcover_rail add primary key(PK_LINE);
    
    -- Railways: layer $inputRail imported
    """

    def queries_landcover_rail = """
    -- Integrates RAIL_SECTIONS into the Landcover
    ------------------------------------------------------------------
    DROP TABLE IF EXISTS rail_buff_d1, rail_buff_d3, rail_buff_d4;
    CREATE TABLE rail_buff_d1 AS SELECT ST_UNION(ST_ACCUM(ST_BUFFER(the_geom, d1/2))) as the_geom FROM landcover_rail;
    CREATE TABLE rail_buff_d3 AS SELECT ST_UNION(ST_ACCUM(ST_BUFFER(the_geom, d3/2))) as the_geom FROM landcover_rail;
    CREATE TABLE rail_buff_d4 AS SELECT ST_UNION(ST_ACCUM(ST_BUFFER(the_geom, d4/2))) as the_geom FROM landcover_rail;

    DROP TABLE IF EXISTS rail_diff_d3_d1, rail_diff_d4_d3;
    CREATE TABLE rail_diff_d3_d1 as select ST_SymDifference(a.the_geom, b.the_geom) as the_geom from rail_buff_d3 a, rail_buff_d1 b where a.the_geom && b.the_geom and st_intersects(a.the_geom, b.the_geom);
    CREATE TABLE rail_diff_d4_d3 as select ST_SymDifference(a.the_geom, b.the_geom) as the_geom from rail_buff_d4 a, rail_buff_d3 b where a.the_geom && b.the_geom and st_intersects(a.the_geom, b.the_geom);
    
    DROP TABLE IF EXISTS rail_buff_d1_expl, rail_buff_d3_expl, rail_buff_d4_expl;
    CREATE TABLE rail_buff_d1_expl AS SELECT a.the_geom, b.g3 as g FROM ST_Explode('RAIL_BUFF_D1') a, PLATEFORM  b WHERE b.IDPLATFORM ='SNCF';
    CREATE TABLE rail_buff_d3_expl AS SELECT a.the_geom, b.g2 as g FROM ST_Explode('RAIL_DIFF_D3_D1 ') a, PLATEFORM  b WHERE b.IDPLATFORM ='SNCF';
    CREATE TABLE rail_buff_d4_expl AS SELECT a.the_geom, b.g1 as g FROM ST_Explode('RAIL_DIFF_D4_D3 ') a, PLATEFORM  b WHERE b.IDPLATFORM ='SNCF';

    DROP TABLE IF EXISTS LANDCOVER_G_0, LANDCOVER_G_03, LANDCOVER_G_07, LANDCOVER_G_1;
    CREATE TABLE LANDCOVER_G_0 AS SELECT ST_Union(ST_Accum(the_geom)) as the_geom FROM landcover_to_enrich WHERE g=0;
    CREATE TABLE LANDCOVER_G_03 AS SELECT ST_Union(ST_Accum(the_geom)) as the_geom FROM landcover_to_enrich WHERE g=0.3;
    CREATE TABLE LANDCOVER_G_07 AS SELECT ST_Union(ST_Accum(the_geom)) as the_geom FROM landcover_to_enrich WHERE g=0.7;
    CREATE TABLE LANDCOVER_G_1 AS SELECT ST_Union(ST_Accum(the_geom)) as the_geom FROM landcover_to_enrich WHERE g=1;

    DROP TABLE IF EXISTS LANDCOVER_0_DIFF_D4, LANDCOVER_03_DIFF_D4, LANDCOVER_07_DIFF_D4, LANDCOVER_1_DIFF_D4;
    CREATE TABLE LANDCOVER_0_DIFF_D4 AS SELECT ST_Difference(b.the_geom, a.the_geom) as the_geom from rail_buff_d4 a, LANDCOVER_G_0 b where a.the_geom && b.the_geom and st_intersects(a.the_geom, b.the_geom);
    CREATE TABLE LANDCOVER_03_DIFF_D4 AS SELECT ST_Difference(b.the_geom, a.the_geom) as the_geom from rail_buff_d4 a, LANDCOVER_G_03 b where a.the_geom && b.the_geom and st_intersects(a.the_geom, b.the_geom);
    CREATE TABLE LANDCOVER_07_DIFF_D4 AS SELECT ST_Difference(b.the_geom, a.the_geom) as the_geom from rail_buff_d4 a, LANDCOVER_G_07 b where a.the_geom && b.the_geom and st_intersects(a.the_geom, b.the_geom);
    CREATE TABLE LANDCOVER_1_DIFF_D4 AS SELECT ST_Difference(b.the_geom, a.the_geom) as the_geom from rail_buff_d4 a, LANDCOVER_G_1 b where a.the_geom && b.the_geom and st_intersects(a.the_geom, b.the_geom);

    DROP TABLE IF EXISTS LANDCOVER_0_EXPL, LANDCOVER_03_EXPL, LANDCOVER_07_EXPL, LANDCOVER_1_EXPL;
    CREATE TABLE LANDCOVER_0_EXPL AS SELECT the_geom, 0 as g FROM ST_Explode('LANDCOVER_0_DIFF_D4 ');
    CREATE TABLE LANDCOVER_03_EXPL AS SELECT the_geom, 0.3 as g FROM ST_Explode('LANDCOVER_03_DIFF_D4 ');
    CREATE TABLE LANDCOVER_07_EXPL AS SELECT the_geom, 0.7 as g FROM ST_Explode('LANDCOVER_07_DIFF_D4 ');
    CREATE TABLE LANDCOVER_1_EXPL AS SELECT the_geom, 1 as g FROM ST_Explode('LANDCOVER_1_DIFF_D4 ');

    -- Unifiy tables
    DROP TABLE IF EXISTS LANDCOVER_UNION, LANDCOVER_MERGE;
    CREATE TABLE LANDCOVER_UNION AS SELECT * FROM LANDCOVER_0_EXPL UNION SELECT * FROM LANDCOVER_03_EXPL UNION SELECT * FROM LANDCOVER_07_EXPL 
    UNION SELECT * FROM LANDCOVER_1_EXPL UNION SELECT * FROM RAIL_BUFF_D1_EXPL UNION SELECT * FROM RAIL_BUFF_D3_EXPL UNION SELECT * FROM RAIL_BUFF_D4_EXPL ; 

    -- Merge geometries that have the same G
    CREATE TABLE LANDCOVER_MERGE AS SELECT ST_UNION(ST_ACCUM(the_geom)) as the_geom, g FROM LANDCOVER_UNION GROUP BY g;
    DROP TABLE IF EXISTS $enrichedLandcover;
    CREATE TABLE $enrichedLandcover AS SELECT ST_SETSRID(the_geom,$srid) as the_geom, g FROM ST_Explode('LANDCOVER_MERGE');
    CREATE SPATIAL INDEX ON $enrichedLandcover(THE_GEOM);

    -- Remove non-needed tables
    DROP TABLE IF EXISTS rail_buff_d1, rail_buff_d3, rail_buff_d4, rail_diff_d3_d1, rail_diff_d4_d3, rail_buff_d1_expl, 
    rail_buff_d3_expl, rail_buff_d4_expl, LANDCOVER_G_0, LANDCOVER_G_03, LANDCOVER_G_07, LANDCOVER_G_1, 
    LANDCOVER_0_DIFF_D4, LANDCOVER_03_DIFF_D4, LANDCOVER_07_DIFF_D4, LANDCOVER_1_DIFF_D4, 
    LANDCOVER_0_EXPL, LANDCOVER_03_EXPL, LANDCOVER_07_EXPL, LANDCOVER_1_EXPL, 
    LANDCOVER_UNION, LANDCOVER_MERGE, landcover_rail, landcover_to_enrich;


    -- Landcover successfully enriched in the table $enrichedLandcover
    """

    StringBuilder stringBuilder = new StringBuilder()
    // print to command window
    def engine = new SimpleTemplateEngine()


    stringBuilder.append(initPlatform)
    stringBuilder.append(import_landcover)
    stringBuilder.append(import_rail)
    stringBuilder.append(queries_landcover_rail)

    def binding = ["inputLandcover": inputLandcover, "gColumn": gColumn, "inputRail": inputRail, "railWidth": railWidth, "outputSuffixe": outputSuffixe, "srid": srid]
    def template = engine.createTemplate(stringBuilder.toString()).make(binding)
    parseScript(template.toString(), sql, progress, logger)

    return "Landcover successfully enriched in the table " + enrichedLandcover

}