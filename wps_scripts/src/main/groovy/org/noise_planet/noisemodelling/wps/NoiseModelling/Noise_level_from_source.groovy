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
 * @Author Hesry Quentin, Université Gustave Eiffel
 * @Author Nicolas Fortin, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.wps.NoiseModelling

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.cts.crs.CRSException
import org.cts.op.CoordinateOperationException
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.api.EmptyProgressVisitor
import org.h2gis.api.ProgressVisitor
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory

import org.noise_planet.noisemodelling.emission.*
import org.noise_planet.noisemodelling.pathfinder.*
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder
import org.noise_planet.noisemodelling.pathfinder.utils.documents.KMLDocument
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.JVMMemoryMetric
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProfilerThread
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProgressMetric
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ReceiverStatsMetric
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor

import org.noise_planet.noisemodelling.propagation.*
import org.noise_planet.noisemodelling.jdbc.*
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.xml.stream.XMLStreamException
import java.nio.file.Paths
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDateTime

title = 'Calculation of the Lden,LDay,LEvening,LNight map from the noise emission table'
description = '&#10145;&#65039; Computes the Lden, LDay, LEvening, LNight maps from the noise emission table.' +
        '<hr>' +
        '&#127757; Tables must be projected in a metric coordinate system (SRID). Use "Change_SRID" WPS Block if needed. </br></br>' +
        '&#x2705; The output table are called: <b> LDEN_GEOM, LDAY_GEOM, LEVENING_GEOM, LNIGHT_GEOM </b> </br></br>' +
        'These tables contain: </br> <ul>' +
        '<li><b> IDRECEIVER</b>: an identifier (INTEGER, PRIMARY KEY)</li>' +
        '<li><b> THE_GEOM </b>: the 3D geometry of the receivers (POINT)</li>' +
        '<li><b> Hz63, Hz125, Hz250, Hz500, Hz1000,Hz2000, Hz4000, Hz8000 </b>: 8 columns giving the day (evening, night or den) emission sound level for each octave band (FLOAT)</li></ul>'

inputs = [
        tableBuilding           : [
                name       : 'Buildings table name',
                title      : 'Buildings table name',
                description: '&#127968; Name of the Buildings table</br> </br>' +
                        'The table must contain: </br><ul>' +
                        '<li><b> THE_GEOM </b>: the 2D geometry of the building (POLYGON or MULTIPOLYGON)</li>' +
                        '<li><b> HEIGHT </b>: the height of the building (FLOAT)</li></ul>',
                type       : String.class
        ],
        tableSources            : [
                name       : 'Sources table name',
                title      : 'Sources table name',
                description: 'Name of the Sources table </br> </br>' +
                        'The table must contain: </br> <ul>' +
                        '<li> <b> PK </b> : an identifier. It shall be a primary key (INTEGER, PRIMARY KEY) </li> ' +
                        '<li> <b> THE_GEOM </b> : the 3D geometry of the sources (POINT, MULTIPOINT, LINESTRING, MULTILINESTRING). According to CNOSSOS-EU, you need to set a height of 0.05 m for a road traffic emission </li> ' +
                        '<li> <b> LWD63, LWD125, LWD250, LWD500, LWD1000, LWD2000, LWD4000, LWD8000 </b> : 8 columns giving the day emission sound level for each octave band (FLOAT) </li> ' +
                        '<li> <b> LWE* </b> : 8 columns giving the evening emission sound level for each octave band (FLOAT) </li> ' +
                        '<li> <b> LWN* </b> : 8 columns giving the night emission sound level for each octave band (FLOAT) </li> ' +
                        '<li> <b> YAW </b> : Source horizontal orientation in degrees. For points 0&#176; North, 90&#176; East. For lines 0&#176; line direction, 90&#176; right of the line direction.  (FLOAT) </li> ' +
                        '<li> <b> PITCH </b> : Source vertical orientation in degrees. 0&#176; front, 90&#176; top, -90&#176; bottom. (FLOAT) </li> ' +
                        '<li> <b> ROLL </b> : Source roll in degrees (FLOAT) </li> ' +
                        '<li> <b> DIR_ID </b> : identifier of the directivity sphere from tableSourceDirectivity parameter or train directivity if not provided -> OMNIDIRECTIONAL(0), ROLLING(1), TRACTIONA(2), TRACTIONB(3), AERODYNAMICA(4), AERODYNAMICB(5), BRIDGE(6) (INTEGER) </li> </ul> ' +
                        '&#128161; This table can be generated from the WPS Block "Road_Emission_from_Traffic"',
                type       : String.class
        ],
        tableReceivers          : [
                name       : 'Receivers table name',
                title      : 'Receivers table name',
                description: 'Name of the Receivers table </br> </br>' +
                        'The table must contain: </br> <ul>' +
                        '<li> <b> PK </b> : an identifier. It shall be a primary key (INTEGER, PRIMARY KEY) </li> ' +
                        '<li> <b> THE_GEOM </b> : the 3D geometry of the sources (POINT, MULTIPOINT) </li> </ul>' +
                        '&#128161; This table can be generated from the WPS Blocks in the "Receivers" folder',
                type       : String.class
        ],
        tableDEM                : [
                name       : 'DEM table name',
                title      : 'DEM table name',
                description: 'Name of the Digital Elevation Model (DEM) table </br> </br>' +
                        'The table must contain: </br> <ul>' +
                        '<li> <b> THE_GEOM </b> : the 3D geometry of the sources (POINT, MULTIPOINT) </li> </ul>' +
                        '&#128161; This table can be generated from the WPS Block "Import_Asc_File"',
                min        : 0, max: 1, type: String.class
        ],
        tableGroundAbs          : [
                name       : 'Ground absorption table name',
                title      : 'Ground absorption table name',
                description: 'Name of the surface/ground acoustic absorption table </br> </br>' +
                        'The table must contain: </br> <ul>' +
                        '<li> <b> THE_GEOM </b>: the 2D geometry of the sources (POLYGON or MULTIPOLYGON) </li>' +
                        '<li> <b> G </b>: the acoustic absorption of a ground (FLOAT between 0 : very hard and 1 : very soft) </li> </ul> ',
                min        : 0, max: 1, type: String.class
        ],
        tableSourceDirectivity          : [
                name       : 'Source directivity table name',
                title      : 'Source directivity table name',
                description: 'Name of the emission directivity table </br> </br>' +
                        'If not specified the default is train directivity of CNOSSOS-EU</b> </br> </br>' +
                        'The table must contain the following columns: </br> <ul>' +
                        '<li> <b> DIR_ID </b>: identifier of the directivity sphere (INTEGER) </li> ' +
                        '<li> <b> THETA </b>: [-90;90] Vertical angle in degree. 0&#176; front 90&#176; top -90&#176; bottom (FLOAT) </li> ' +
                        '<li> <b> PHI </b>: [0;360] Horizontal angle in degree. 0&#176; front 90&#176; right (FLOAT) </li> ' +
                        '<li> <b> LW63, LW125, LW250, LW500, LW1000, LW2000, LW4000, LW8000 </b>: attenuation levels in dB for each octave or third octave (FLOAT) </li> </ul> ' ,
                min        : 0, max: 1, type: String.class
        ],
        paramWallAlpha          : [
                name       : 'wallAlpha',
                title      : 'Wall absorption coefficient',
                description: 'Wall absorption coefficient (FLOAT) </br> </br>' +
                        'This coefficient is going <br> <ul>' +
                        '<li> from 0 : fully absorbent </li>' +
                        '<li> to strictly less than 1 : fully reflective. </li> </ul>' +
                        '&#128736; Default value: <b>0.1 </b> ',
                min        : 0, max: 1, type: String.class
        ],
        confReflOrder           : [
                name       : 'Order of reflexion',
                title      : 'Order of reflexion',
                description: 'Maximum number of reflections to be taken into account (INTEGER). </br> </br>' +
                        '&#x1F6A8; Adding 1 order of reflexion can significantly increase the processing time. </br> </br>' +
                        '&#128736; Default value: <b>1 </b>',
                min        : 0, max: 1, type: String.class
        ],
        confMaxSrcDist          : [
                name       : 'Maximum source-receiver distance',
                title      : 'Maximum source-receiver distance',
                description: 'Maximum distance between source and receiver (FLOAT, in meters). </br> </br>' +
                        '&#128736; Default value: <b>150 </b>',
                min        : 0, max: 1, type: String.class
        ],
        confMaxReflDist         : [
                name       : 'Maximum source-reflexion distance',
                title      : 'Maximum source-reflexion distance',
                description: 'Maximum reflection distance from the source (FLOAT, in meters). </br> </br>' +
                        '&#128736; Default value: <b>50 </b>',
                min        : 0, max: 1, type: String.class
        ],
        confThreadNumber        : [
                name       : 'Thread number',
                title      : 'Thread number',
                description: 'Number of thread to use on the computer (INTEGER). </br> </br>' +
                        'To set this value, look at the number of cores you have. </br>' +
                        'If it is set to 0, use the maximum number of cores available.</br> </br>' +
                        '&#128736; Default value: <b>0 </b>',
                min        : 0, max: 1, type: String.class
        ],
        confDiffVertical        : [
                name       : 'Diffraction on vertical edges',
                title      : 'Diffraction on vertical edges',
                description: 'Compute or not the diffraction on vertical edges. Following Directive 2015/996, enable this option for rail and industrial sources only. </br> </br>' +
                        '&#128736; Default value: <b>false </b>',
                min        : 0, max: 1, type: Boolean.class
        ],
        confDiffHorizontal      : [
                name       : 'Diffraction on horizontal edges',
                title      : 'Diffraction on horizontal edges',
                description: 'Compute or not the diffraction on horizontal edges. </br> </br>' +
                        '&#128736; Default value: <b>false </b>',
                min        : 0, max: 1, type: Boolean.class
        ],
        confSkipLday            : [
                name       : 'Skip LDAY_GEOM table',
                title      : 'Do not compute LDAY_GEOM table',
                description: 'Skip the creation of this table. </br> </br>' +
                        '&#128736; Default value: <b>false </b>',
                min        : 0, max: 1, type: Boolean.class
        ],
        confSkipLevening        : [
                name       : 'Skip LEVENING_GEOM table',
                title      : 'Do not compute LEVENING_GEOM table',
                description: 'Skip the creation of this table. </br> </br> ' +
                        '&#128736; Default value: <b>false </b>',
                min        : 0, max: 1, type: Boolean.class
        ],
        confSkipLnight          : [
                name       : 'Skip LNIGHT_GEOM table',
                title      : 'Do not compute LNIGHT_GEOM table',
                description: 'Skip the creation of this table. </br> </br>' +
                        '&#128736; Default value: <b>false </b>',
                min        : 0, max: 1, type: Boolean.class
        ],
        confSkipLden            : [
                name       : 'Skip LDEN_GEOM table',
                title      : 'Do not compute LDEN_GEOM table',
                description: 'Skip the creation of this table. </br> </br>' +
                        '&#128736; Default value: <b>false </b>',
                min        : 0, max: 1, type: Boolean.class
        ],
        confExportSourceId      : [
                name       : 'Keep source id',
                title      : 'Separate receiver level by source identifier',
                description: 'Keep source identifier in output in order to get noise contribution of each noise source. </br> </br>' +
                        '&#128736; Default value: <b>false </b>',
                min        : 0, max: 1,
                type       : Boolean.class
        ],
        confHumidity            : [
                name       : 'Relative humidity',
                title      : 'Relative humidity',
                description: '&#127783; Humidity for noise propagation. </br> </br>' +
                        '&#128736; Default value: <b>70</b>',
                min        : 0, max: 1,
                type       : Double.class
        ],
        confTemperature         : [
                name       : 'Temperature',
                title      : 'Air temperature',
                description: '&#127777; Air temperature in degree celsius </br> </br>' +
                        '&#128736; Default value: <b> 15</b>',
                min        : 0, max: 1,
                type       : Double.class
        ],
        confFavorableOccurrencesDay: [
                name       : 'Probability of occurrences (Day)',
                title      : 'Probability of occurrences (Day)',
                description: 'Comma-delimited string containing the probability of occurrences of favourable propagation conditions. </br> </br>' +
                        'The north slice is the last array index not the first one <br/>' +
                        'Slice width are 22.5&#176;: (16 slices)</br> <ul>' +
                        '<li>The first column 22.5&#176; contain occurrences between 11.25 to 33.75 &#176; </li>' +
                        '<li>The last column 360&#176; contains occurrences between 348.75&#176; to 360&#176; and 0 to 11.25&#176; </li> </ul>' +
                        '&#128736; Default value: <b>0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5</b>',
                min        : 0, max: 1,
                type       : String.class
        ],
        confFavorableOccurrencesEvening: [
                name       : 'Probability of occurrences (Evening)',
                title      : 'Probability of occurrences (Evening)',
                description: 'Comma-delimited string containing the probability of occurrences of favourable propagation conditions. </br> </br>' +
                        'The north slice is the last array index not the first one <br/>' +
                        'Slice width are 22.5&#176;: (16 slices)</br> <ul>' +
                        '<li>The first column 22.5&#176; contain occurrences between 11.25 to 33.75 &#176; </li>' +
                        '<li>The last column 360&#176; contains occurrences between 348.75&#176; to 360&#176; and 0 to 11.25&#176; </li> </ul>' +
                        '&#128736; Default value: <b>0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5</b>',
                min        : 0, max: 1,
                type       : String.class
        ],
        confFavorableOccurrencesNight: [
                name       : 'Probability of occurrences (Night)',
                title      : 'Probability of occurrences (Night)',
                description: 'Comma-delimited string containing the probability of occurrences of favourable propagation conditions. </br> </br>' +
                        'The north slice is the last array index not the first one <br/>' +
                        'Slice width are 22.5&#176;: (16 slices)</br> <ul>' +
                        '<li>The first column 22.5&#176; contain occurrences between 11.25 to 33.75 &#176; </li>' +
                        '<li>The last column 360&#176; contains occurrences between 348.75&#176; to 360&#176; and 0 to 11.25&#176; </li> </ul>' +
                        '&#128736; Default value: <b>0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5</b>',
                min        : 0, max: 1,
                type       : String.class
        ],
        maxError            : [
                name       : 'maxError',
                title      : 'maxError',
                description: 'maxError',
                min        : 0, max: 1, type: Double.class
        ],
        confRaysName            : [
                name       : '',
                title      : 'Export scene',
                description: 'Save each mnt, buildings and propagation rays into the specified table (ex:RAYS) or file URL (ex: file:///Z:/dir/map.kml) </br> </br>' +
                        'You can set a table name here in order to save all the rays computed by NoiseModelling. </br> </br>' +
                        'The number of rays has been limited in this script in order to avoid memory exception. </br> </br>' +
                        '&#128736; Default value: <b>empty (do not keep rays)</b>',
                min        : 0, max: 1, type: String.class
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
// Open Connection to Geoserver
static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

def forgeCreateTable(Sql sql, String tableName, NoiseMapParameters ldenConfig, String geomField, String tableReceiver, String tableResult) {
    // Create a logger to display messages in the geoserver logs and in the command prompt.
    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")

    StringBuilder sb = new StringBuilder("create table ");
    sb.append(tableName);
    if (!ldenConfig.mergeSources) {
        sb.append(" (IDRECEIVER bigint NOT NULL");
        sb.append(", IDSOURCE bigint NOT NULL");
    } else {
        sb.append(" (IDRECEIVER bigint NOT NULL");
    }
    sb.append(", THE_GEOM geometry")
    List<Integer> freqLvl = ldenConfig.getPropagationProcessPathData(NoiseMapParameters.TIME_PERIOD.DAY).freq_lvl;
    for (int idfreq = 0; idfreq < freqLvl.size(); idfreq++) {
        sb.append(", HZ");
        sb.append(freqLvl.get(idfreq));
        sb.append(" REAL");
    }
    sb.append(", LAEQ REAL, LEQ REAL ) AS SELECT PK");
    if (!ldenConfig.mergeSources) {
        sb.append(", IDSOURCE");
    }
    sb.append(", ")
    sb.append(geomField)
    for (int idfreq = 0; idfreq < freqLvl.size(); idfreq++) {
        sb.append(", HZ");
        sb.append(freqLvl.get(idfreq));
    }
    sb.append(", LAEQ, LEQ FROM ")
    sb.append(tableReceiver)
    if (!ldenConfig.mergeSources) {
        // idsource can't be null so we can't left join
        sb.append(" a, ")
        sb.append(tableResult)
        sb.append(" b WHERE a.PK = b.IDRECEIVER")
    } else {
        sb.append(" a LEFT JOIN ")
        sb.append(tableResult)
        sb.append(" b ON a.PK = b.IDRECEIVER")
    }
    sql.execute(sb.toString())
    // apply pk
    logger.info("Add primary key on " + tableName)
    if (!ldenConfig.mergeSources) {
        sql.execute("ALTER TABLE " + tableName + " ADD PRIMARY KEY(IDRECEIVER, IDSOURCE)")
    } else {
        sql.execute("ALTER TABLE " + tableName + " ADD PRIMARY KEY(IDRECEIVER)")
    }
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

static void exportScene(String name, ProfileBuilder builder, NoiseMap result, int crs) throws IOException {
    try {
        FileOutputStream outData = new FileOutputStream(name);
        KMLDocument kmlDocument = new KMLDocument(outData);
        kmlDocument.setInputCRS("EPSG:" + crs);
        kmlDocument.writeHeader();
        if(builder != null) {
            kmlDocument.writeTopographic(builder.getTriangles(), builder.getVertices());
        }
        if(result != null) {
            kmlDocument.writeRays(result.getPropagationPaths());
        }
        if(builder != null) {
            kmlDocument.writeBuildings(builder);
        }
        kmlDocument.writeFooter();
    } catch (XMLStreamException | CoordinateOperationException | CRSException ex) {
        throw new IOException(ex);
    }
}

// main function of the script
def exec(Connection connection, input) {

    //Need to change the ConnectionWrapper to WpsConnectionWrapper to work under postGIS database
    connection = new ConnectionWrapper(connection)

    // output string, the information given back to the user
    String resultString = null

    // Create a logger to display messages in the geoserver logs and in the command prompt.
    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")

    // print to command window
    logger.info('Start : LDEN from Emission')
    logger.info("inputs {}", input) // log inputs of the run


    // -------------------
    // Get every inputs
    // -------------------

    String sources_table_name = input['tableSources']
    // do it case-insensitive
    sources_table_name = sources_table_name.toUpperCase()
    // Check if srid are in metric projection.
    int sridSources = GeometryTableUtilities.getSRID(connection, TableLocation.parse(sources_table_name))
    if (sridSources == 3785 || sridSources == 4326) {
        throw new IllegalArgumentException("Error : Please use a metric projection for " + sources_table_name + ".")
    }
    if (sridSources == 0) {
        throw new IllegalArgumentException("Error : The table " + sources_table_name + " does not have an associated SRID.")
    }


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
    if (sridReceivers == 3785 || sridReceivers == 4326) {
        throw new IllegalArgumentException("Error : Please use a metric projection for " + receivers_table_name + ".")
    }
    if (sridReceivers == 0) {
        throw new IllegalArgumentException("Error : The table " + receivers_table_name + " does not have an associated SRID.")
    }
    if (sridReceivers != sridSources) {
        throw new IllegalArgumentException("Error : The SRID of table " + sources_table_name + " and " + receivers_table_name + " are not the same.")
    }


    String building_table_name = input['tableBuilding']
    // do it case-insensitive
    building_table_name = building_table_name.toUpperCase()
    // Check if srid are in metric projection and are all the same.
    int sridBuildings = GeometryTableUtilities.getSRID(connection, TableLocation.parse(building_table_name))
    if (sridBuildings == 3785 || sridReceivers == 4326) {
        throw new IllegalArgumentException("Error : Please use a metric projection for " + building_table_name + ".")
    }
    if (sridBuildings == 0) {
        throw new IllegalArgumentException("Error : The table " + building_table_name + " does not have an associated SRID.")
    }
    if (sridReceivers != sridBuildings) {
        throw new IllegalArgumentException("Error : The SRID of table " + building_table_name + " and " + receivers_table_name + " are not the same.")
    }

    String dem_table_name = ""
    if (input['tableDEM']) {
        dem_table_name = input['tableDEM']
        // do it case-insensitive
        dem_table_name = dem_table_name.toUpperCase()
        // Check if srid are in metric projection and are all the same.
        int sridDEM = GeometryTableUtilities.getSRID(connection, TableLocation.parse(dem_table_name))
        if (sridDEM == 3785 || sridReceivers == 4326) {
            throw new IllegalArgumentException("Error : Please use a metric projection for " + dem_table_name + ".")
        }
        if (sridDEM == 0) {
            throw new IllegalArgumentException("Error : The table " + dem_table_name + " does not have an associated SRID.")
        }
        if (sridDEM != sridSources) {
            throw new IllegalArgumentException("Error : The SRID of table " + sources_table_name + " and " + dem_table_name + " are not the same.")
        }
    }


    String ground_table_name = ""
    if (input['tableGroundAbs']) {
        ground_table_name = input['tableGroundAbs']
        // do it case-insensitive
        ground_table_name = ground_table_name.toUpperCase()
        // Check if srid are in metric projection and are all the same.
        int sridGROUND = GeometryTableUtilities.getSRID(connection, TableLocation.parse(ground_table_name))
        if (sridGROUND == 3785 || sridReceivers == 4326) {
            throw new IllegalArgumentException("Error : Please use a metric projection for " + ground_table_name + ".")
        }
        if (sridGROUND == 0) {
            throw new IllegalArgumentException("Error : The table " + ground_table_name + " does not have an associated SRID.")
        }
        if (sridGROUND != sridSources) {
            throw new IllegalArgumentException("Error : The SRID of table " + ground_table_name + " and " + sources_table_name + " are not the same.")
        }
    }

    String tableSourceDirectivity = ""
    if (input['tableSourceDirectivity']) {
        tableSourceDirectivity = input['tableSourceDirectivity']
        // do it case-insensitive
        tableSourceDirectivity = tableSourceDirectivity.toUpperCase()
    }


    int reflexion_order = 0
    if (input['confReflOrder']) {
        reflexion_order = Integer.valueOf(input['confReflOrder'])
    }

    double maxError = 0.1
    if (input['maxError']) {
        maxError = Double.valueOf(input['maxError'])
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

    int n_thread = 0
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

    boolean confSkipLday = false;
    if (input['confSkipLday']) {
        confSkipLday = input['confSkipLday']
    }

    boolean confSkipLevening = false;
    if (input['confSkipLevening']) {
        confSkipLevening = input['confSkipLevening']
    }

    boolean confSkipLnight = false;
    if (input['confSkipLnight']) {
        confSkipLnight = input['confSkipLnight']
    }

    boolean confSkipLden = false;
    if (input['confSkipLden']) {
        confSkipLden = input['confSkipLden']
    }

    boolean confSkipAll = false;
    if (confSkipLday && confSkipLevening && confSkipLnight && confSkipLden) {
        confSkipAll = true;
    }

    boolean confExportSourceId = false;
    if (input['confExportSourceId']) {
        confExportSourceId = input['confExportSourceId']
    }

    // -------------------------
    // Initialize some variables
    // -------------------------

    // Set of already processed receivers
    Set<Long> receivers = new HashSet<>()

    // --------------------------------------------
    // Initialize NoiseModelling propagation part
    // --------------------------------------------

    NoiseMapByReceiverMaker pointNoiseMap = new NoiseMapByReceiverMaker(building_table_name, sources_table_name, receivers_table_name)

    NoiseMapParameters ldenConfig = new NoiseMapParameters(NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_DEN)
    if (confSkipAll) {
        ldenConfig = new NoiseMapParameters(NoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_HZ)
        confSkipLday = false
    }

    ldenConfig.setComputeLDay(!confSkipLday)
    ldenConfig.setComputeLEvening(!confSkipLevening)
    ldenConfig.setComputeLNight(!confSkipLnight)
    ldenConfig.setComputeLDEN(!confSkipLden)



    ldenConfig.setMergeSources(!confExportSourceId)
    //ldenConfig.setExportReceiverPosition(true)
    ldenConfig.setlDayTable("LDAY_GEOM")
    ldenConfig.setlEveningTable("LEVENING_GEOM")
    ldenConfig.setlNightTable("LNIGHT_GEOM")
    ldenConfig.setlDenTable("LDEN_GEOM")


    Sql sql = new Sql(connection)
    if(!confSkipLday) {
        sql.execute("drop table if exists " + TableLocation.parse(ldenConfig.getlDayTable()))
    }
    if(!confSkipLevening) {
        sql.execute("drop table if exists " + TableLocation.parse(ldenConfig.getlEveningTable()))
    }
    if(!confSkipLnight) {
        sql.execute("drop table if exists " + TableLocation.parse(ldenConfig.getlNightTable()))
    }
    if(!confSkipLden) {
        sql.execute("drop table if exists " + TableLocation.parse(ldenConfig.getlDenTable()))
    }

    int maximumRaysToExport = 5000

    File folderExportKML = null
    String kmlFileNamePrepend = ""
    if (input['confRaysName'] && !((input['confRaysName'] as String).isEmpty())) {
        String confRaysName = input['confRaysName'] as String
        if(confRaysName.toLowerCase().startsWith("file:")) {
            ldenConfig.setExportRaysMethod(NoiseMapParameters.ExportRaysMethods.TO_MEMORY)
            URL url = new URL(confRaysName)
            File urlFile = new File(url.toURI())
            if(urlFile.isDirectory()) {
                folderExportKML = urlFile
            } else {
                folderExportKML = urlFile.getParentFile()
                String fileName = urlFile.getName()
                int positionExt = fileName.lastIndexOf(".")
                kmlFileNamePrepend = fileName.substring(0, positionExt > 0 ? positionExt : fileName.length())
            }
        } else {
            ldenConfig.setExportRaysMethod(NoiseMapParameters.ExportRaysMethods.TO_RAYS_TABLE)
            ldenConfig.setRaysTable(input['confRaysName'] as String)
        }
        //ldenConfig.setKeepAbsorption(true);
        ldenConfig.setMaximumRaysOutputCount(maximumRaysToExport);
    }

    NoiseMapMaker ldenProcessing = new NoiseMapMaker(connection, ldenConfig)

    // add optional discrete directivity table name
    if(tableSourceDirectivity.isEmpty()) {
        // Add train directivity
        ldenProcessing.insertTrainDirectivity()
    } else {
        // Load table into specialized class
        ldenProcessing.directionAttributes = NoiseMapLoader.fetchDirectivity(connection, tableSourceDirectivity, 1)
        logger.info(String.format(Locale.ROOT, "Loaded %d directivity from %s table", ldenProcessing.directionAttributes.size(), tableSourceDirectivity))
    }
    pointNoiseMap.setComputeHorizontalDiffraction(compute_vertical_diffraction)
    pointNoiseMap.setComputeVerticalDiffraction(compute_horizontal_diffraction)
    pointNoiseMap.setSoundReflectionOrder(reflexion_order)

    // Set environmental parameters
    AttenuationCnossosParameters environmentalDataDay = new AttenuationCnossosParameters()

    if (input.containsKey('confHumidity')) {
        environmentalDataDay.setHumidity(input['confHumidity'] as Double)
    }
    if (input.containsKey('confTemperature')) {
        environmentalDataDay.setTemperature(input['confTemperature'] as Double)
    }

    AttenuationCnossosParameters environmentalDataEvening = new AttenuationCnossosParameters(environmentalDataDay)
    AttenuationCnossosParameters environmentalDataNight = new AttenuationCnossosParameters(environmentalDataDay)
    if (input.containsKey('confFavorableOccurrencesDay')) {
        StringTokenizer tk = new StringTokenizer(input['confFavorableOccurrencesDay'] as String, ',')
        double[] favOccurrences = new double[PropagationProcessPathData.DEFAULT_WIND_ROSE.length]
        for (int i = 0; i < favOccurrences.length; i++) {
            favOccurrences[i] = Math.max(0, Math.min(1, Double.valueOf(tk.nextToken().trim())))
        }
        environmentalDataDay.setWindRose(favOccurrences)
    }
    if (input.containsKey('confFavorableOccurrencesEvening')) {
        StringTokenizer tk = new StringTokenizer(input['confFavorableOccurrencesEvening'] as String, ',')
        double[] favOccurrences = new double[PropagationProcessPathData.DEFAULT_WIND_ROSE.length]
        for (int i = 0; i < favOccurrences.length; i++) {
            favOccurrences[i] = Math.max(0, Math.min(1, Double.valueOf(tk.nextToken().trim())))
        }
        environmentalDataEvening.setWindRose(favOccurrences)
    }
    if (input.containsKey('confFavorableOccurrencesNight')) {
        StringTokenizer tk = new StringTokenizer(input['confFavorableOccurrencesNight'] as String, ',')
        double[] favOccurrences = new double[PropagationProcessPathData.DEFAULT_WIND_ROSE.length]
        for (int i = 0; i < favOccurrences.length; i++) {
            favOccurrences[i] = Math.max(0, Math.min(1, Double.valueOf(tk.nextToken().trim())))
        }
        environmentalDataNight.setWindRose(favOccurrences)
    }

    pointNoiseMap.setPropagationProcessPathData(NoiseMapParameters.TIME_PERIOD.DAY, environmentalDataDay)
    pointNoiseMap.setPropagationProcessPathData(NoiseMapParameters.TIME_PERIOD.EVENING, environmentalDataEvening)
    pointNoiseMap.setPropagationProcessPathData(NoiseMapParameters.TIME_PERIOD.NIGHT, environmentalDataNight)

    // Building height field name
    pointNoiseMap.setHeightField("HEIGHT")
    // Import table with Snow, Forest, Grass, Pasture field polygons. Attribute G is associated with each polygon
    if (ground_table_name != "") {
        pointNoiseMap.setSoilTableName(ground_table_name)
    }
    // Point cloud height above sea level POINT(X Y Z)
    if (dem_table_name != "") {
        pointNoiseMap.setDemTable(dem_table_name)
    }

    pointNoiseMap.setMaximumPropagationDistance(max_src_dist)
    pointNoiseMap.setMaximumReflectionDistance(max_ref_dist)
    pointNoiseMap.setWallAbsorption(wall_alpha)
    pointNoiseMap.setThreadCount(n_thread)


    // --------------------------------------------
    // Initialize NoiseModelling emission part
    // --------------------------------------------

    pointNoiseMap.setComputeRaysOutFactory(ldenProcessing)
    pointNoiseMap.setPropagationProcessDataFactory(ldenProcessing)


    // Do not propagate for low emission or far away sources
    // Maximum error in dB
    pointNoiseMap.setMaximumError(maxError)
    // Init Map
    pointNoiseMap.initialize(connection, new EmptyProgressVisitor())

    // --------------------------------------------
    // Run Calculations
    // --------------------------------------------

    // Init ProgressLogger (loading bar)
    RootProgressVisitor progressLogger = new RootProgressVisitor(1, true, 1)

    logger.info("Start calculation... ")
    LocalDateTime now = LocalDateTime.now();
    ProfilerThread profilerThread = new ProfilerThread(new File(String.format("profile_%d_%d_%d_%dh%d.csv",
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute())));
    profilerThread.addMetric(ldenProcessing);
    profilerThread.addMetric(new ProgressMetric(progressLogger));
    profilerThread.addMetric(new JVMMemoryMetric());
    profilerThread.addMetric(new ReceiverStatsMetric());
    profilerThread.setWriteInterval(300);
    profilerThread.setFlushInterval(300);
    pointNoiseMap.setProfilerThread(profilerThread);
    try {
        ldenProcessing.start()
        new Thread(profilerThread).start();
        // Iterate over computation areas
        int k = 0
        Map cells = pointNoiseMap.searchPopulatedCells(connection);
        ProgressVisitor progressVisitor = progressLogger.subProcess(cells.size());
        new TreeSet<>(cells.keySet()).each { cellIndex ->
            Envelope cellEnvelope = pointNoiseMap.getCellEnv(pointNoiseMap.getMainEnvelope(),
                    cellIndex.getLatitudeIndex(), cellIndex.getLongitudeIndex(), pointNoiseMap.getCellWidth(),
                    pointNoiseMap.getCellHeight());
            logger.info("Compute domain is " + new GeometryFactory().toGeometry(cellEnvelope))
            logger.info(String.format("Compute... %.3f %% (%d receivers in this cell)", 100 * k++ / cells.size(), cells.get(cellIndex)))
            // Run ray propagation
            IComputePathsOut out = pointNoiseMap.evaluateCell(connection, cellIndex.getLatitudeIndex(), cellIndex.getLongitudeIndex(), progressVisitor, receivers)
            // Export as a Google Earth 3d scene
            if (out instanceof NoiseMap && folderExportKML != null) {
                NoiseMap cellStorage = (NoiseMap) out;
                exportScene(new File(folderExportKML.getPath(),
                        String.format(Locale.ROOT, kmlFileNamePrepend + "_%d_%d.kml", cellIndex.getLatitudeIndex(),
                                cellIndex.getLongitudeIndex())).getPath(),
                        cellStorage.inputData.profileBuilder, cellStorage, sridSources)
            }

        }
    } finally {
        profilerThread.stop();
        ldenProcessing.stop()
    }

    StringBuilder createdTables = new StringBuilder()


    if (ldenConfig.computeLDay) {
        createdTables.append(" LDAY_GEOM")
    }
    if (ldenConfig.computeLEvening) {
        createdTables.append(" LEVENING_GEOM")
    }
    if (ldenConfig.computeLNight) {
        createdTables.append(" LNIGHT_GEOM")
    }
    if (ldenConfig.computeLDEN) {
        createdTables.append(" LDEN_GEOM")
    }

    resultString = "Calculation Done ! " + createdTables.toString() + " table(s) have been created."


    // print to command window
    logger.info('Result : ' + resultString)
    logger.info('End : LDEN from Emission')

    // print to WPS Builder
    return resultString

}
