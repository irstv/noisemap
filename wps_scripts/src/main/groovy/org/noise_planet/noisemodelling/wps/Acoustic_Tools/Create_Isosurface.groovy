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
 * @Author Nicolas Fortin, Université Gustave Eiffel
 */


package org.noise_planet.noisemodelling.wps.Acoustic_Tools

import geoserver.GeoServer
import geoserver.catalog.Store
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.noise_planet.noisemodelling.jdbc.utils.IsoSurface
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

title = 'Create isosurfaces from a NoiseModelling resulting table and its associated TRIANGLES table.'

description = '&#10145;&#65039; Create isosurfaces from a NoiseModelling resulting table and its associated TRIANGLES table.'+
              '<hr>' +
              '&#x1F6A8; The triangle table must have been created using the WPS block "<b>Receivers/Delaunay_Grid</b>". </br> </br> ' +
              '&#x2705; The output table is called <b>CONTOURING_NOISE_MAP</b> </br> </br> ' +
              '<img src="/wps_images/create_isosurface.png" alt="Create isosurfaces" width="95%" align="center">'

inputs = [
        resultTable      : [
                name       : 'Sound levels table',
                title      : 'Sound levels table',
                description: 'Name of the sound levels table, generated from "Noise_level_from_source". (STRING)</br> </br>' +
                             'Example : LDEN_GEOM.',
                type       : String.class
        ],
        isoClass         : [
                name       : 'Iso levels in dB',
                title      : 'Iso levels in dB',
                description: 'Separation of sound levels for isosurfaces. </br> </br>' +
                             'Read <a href="https://noisemodelling.readthedocs.io/en/latest/Noise_Map_Color_Scheme.html#creation-of-the-isosurfaces" target=_blank>this documentation</a> for more information about sound levels classes. </br> </br>' +
                             '&#128736; Default value: <b>35.0,40.0,45.0,50.0,55.0,60.0,65.0,70.0,75.0,80.0,200.0 </b>',
                min        : 0, max: 1,
                type       : String.class
        ],
        keepTriangles: [
                name       : 'Keep triangles',
                title      : 'Keep triangles',
                description: 'Point inside areas with the same iso levels are kept so elevation variation into ' +
                        'same iso level areas will be preserved but the output data size will be higher.',
                min        : 0, max: 1,
                type       : Boolean.class
        ],
        smoothCoefficient: [
                name       : 'Polygon smoothing coefficient',
                title      : 'Polygon smoothing coefficient',
                description: 'This coefficient (<a href="https://en.wikipedia.org/wiki/B%C3%A9zier_curve" target="_blank">Bezier curve</a> coefficient) will smooth the generated isosurfaces. </br> </br>'+
                             'If equal to 0, it disables the smoothing step and will keep the altitude of receivers (3D geojson can be viewed on https://kepler.gl).</br> </br>' +
                             '&#128736; Default value: <b>0.5 </b>',
                min        : 0, max: 1,
                type       : Double.class
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


static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

def exec(Connection connection, input) {

    //Need to change the ConnectionWrapper to WpsConnectionWrapper to work under postGIS database
    connection = new ConnectionWrapper(connection)

    // output string, the information given back to the user
    String resultString = null

    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")

    // print to command window
    logger.info('Start : Compute Isosurfaces')
    logger.info("inputs {}", input) // log inputs of the run


    List<Double> isoLevels = IsoSurface.NF31_133_ISO // default values

    if (input.containsKey("isoClass")) {
        isoLevels = new ArrayList<>()
        StringTokenizer st = new StringTokenizer(input['isoClass'] as String, ",")
        while (st.hasMoreTokens()) {
            isoLevels.add(Double.parseDouble(st.nextToken()))
        }
    }

    String levelTable = input['resultTable'] as String

    int srid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(levelTable))

    IsoSurface isoSurface = new IsoSurface(isoLevels, srid)

    isoSurface.setPointTable(levelTable)

    if (input.containsKey("smoothCoefficient")) {
        double coefficient = input['smoothCoefficient'] as Double
        if (coefficient < 0.01) {
            isoSurface.setSmooth(false)
        } else {
            isoSurface.setSmooth(true)
            isoSurface.setSmoothCoefficient(coefficient)
        }
    }
    else {
        isoSurface.setSmooth(true)
        isoSurface.setSmoothCoefficient(0.5)
    }

    isoSurface.createTable(connection)

    resultString = "Table " + isoSurface.getOutputTable() + " created"

    logger.info('End : Compute Isosurfaces')
    logger.info(resultString)

    // print to WPS Builder
    return resultString
}


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