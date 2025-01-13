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


package org.noise_planet.noisemodelling.wps.NoiseModelling

import geoserver.GeoServer
import geoserver.catalog.Store
import org.geotools.jdbc.JDBCDataStore
import org.noise_planet.noisemodelling.emission.directivity.DiscreteDirectivitySphere
import org.noise_planet.noisemodelling.emission.directivity.OmnidirectionalDirection
import org.noise_planet.noisemodelling.emission.directivity.PolarGraphDirectivity
import org.noise_planet.noisemodelling.emission.railway.nmpb.RailWayNMPBParameters
import org.noise_planet.noisemodelling.emission.railway.nmpb.TrainAttenuation
import org.noise_planet.noisemodelling.jdbc.NoiseMapLoader

//import org.noise_planet.noisemodelling.emission.directivity.DirectivitySphere
//import org.noise_planet.noisemodelling.emission.directivity.DiscreteDirectivitySphere

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

title = 'Plot the directivity graph of the specified DIR_ID'
description = '&#10145;&#65039; Plot the directivity graph of the specified "DIR_ID"'

inputs = [
        tableSourceDirectivity          : [
                name       : 'Source directivity table name',
                title      : 'Source directivity table name',
                description: 'Name of the emission directivity table.</br></br>' +
                        '&#128736;  If not specified the default is train directivity of CNOSSOS-EU </br> </br>' +
                        'The table must contain the following columns: <ul> ' +
                        '<li> <b> DIR_ID </b> : identifier of the directivity sphere (INTEGER)</li> ' +
                        '<li> <b> THETA </b> : [-90;90] Vertical angle in degree. 0&#176; front 90&#176; top -90&#176; bottom (FLOAT)</li> ' +
                        '<li> <b> PHI </b> : [0;360] Horizontal angle in degree. 0&#176; front 90&#176; right (FLOAT)</li> ' +
                        '<li> <b> LW63, LW125, LW250, LW500, LW1000, LW2000, LW4000, LW8000 </b> : attenuation levels in dB for each octave or third octave (FLOAT). </li></ul> ' ,
                min        : 0, max: 1, type: String.class
        ],
        confDirId            : [
                name       : 'Directivity Index',
                title      : 'Directivity Index',
                description: 'Identifier of the directivity sphere from "tableSourceDirectivity" parameter or train directivity if "tableSourceDirectivity" parameter is not filled (INTEGER)</br> </br>' +
                        'In case of train, you can use these values: <ul>'+
                        '<li>0 = OMNIDIRECTIONAL</li>' +
                        '<li>1 = ROLLING</li>' +
                        '<li>2 = TRACTIONA</li>' +
                        '<li>3 = TRACTIONB</li>' +
                        '<li>4 = AERODYNAMICA</li>' +
                        '<li>5 = AERODYNAMICB</li>' +
                        '<li>6 = BRIDGE</li></ul>',
                type       : Integer.class
        ],
        confFrequency            : [
                name       : 'Frequency',
                title      : 'Frequency',
                description: 'Frequency to plot (INTEGER). 63, 125, 250, 500, 1000, 2000, 4000, 8000 (should match with the column of tableSourceDirectivity</br>',
                type       : Integer.class
        ],
        confScaleMinimum            : [
                name       : 'Minimum scale attenuation (dB)',
                title      : 'Minimum scale attenuation (dB)',
                description: 'Minimum scale attenuation (in dB) </br> </br>'+
                        '&#128736; Default value: <b>-35 dB</b>',
                min        : 0, max: 1,
                type       : Double.class
        ],
        confScaleMaximum            : [
                name       : 'Maximum scale attenuation (dB)',
                title      : 'Maximum scale attenuation (dB)',
                description: 'Maximum scale attenuation (in dB) </br> </br>'+
                        '&#128736; Default value: <b>0 dB</b>',
                min        : 0, max: 1,
                type       : Double.class
        ]
]

outputs = [
        result: [
                name       : 'Result output string',
                title      : 'Result output string',
                description: 'Svg/Html of the directivity chart',
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

    // Create a logger to display messages in the geoserver logs and in the command prompt.
    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")


    String tableSourceDirectivity = ""
    Map<Integer,DiscreteDirectivitySphere> directivityData;
    if (input['tableSourceDirectivity']) {
        tableSourceDirectivity = input['tableSourceDirectivity']
        // do it case-insensitive
        tableSourceDirectivity = tableSourceDirectivity.toUpperCase()
        directivityData = NoiseMapLoader.fetchDirectivity(connection, tableSourceDirectivity, 1)
    } else {
        directivityData = new HashMap<>();
        directivityData.put(0, new OmnidirectionalDirection());
        for(RailWayNMPBParameters.TrainNoiseSource noiseSource : RailWayLW.TrainNoiseSource.values()) {
            directivityData.put(noiseSource.ordinal() + 1, new TrainAttenuation(noiseSource));
        }
    }

    int directivityIndex
    if (input['confDirId']) {
        directivityIndex = input['confDirId'] as Integer
        if(!directivityData.containsKey(directivityIndex)) {
            return "The specified directivity index does not exist (not in " + Arrays.toString(directivityData.keySet()) +" )"
        }
    } else {
        return "Please provide confDirId parameter"
    }

    int frequency = 1000
    if (input['confDirId']) {
        confFrequency = input['confDirId'] as Integer
    } else {
        return "Please provide frequency parameter"
    }


    double scaleMinimum = -35
    if (input['confScaleMinimum']) {
        scaleMinimum = input['confScaleMinimum'] as Double
    }
    double scaleMaximum = 0
    if (input['confScaleMaximum']) {
        scaleMaximum = input['confScaleMaximum'] as Double
    }
    directionAttributes = directivityData.get(directivityIndex)
    PolarGraphDirectivity polarGraphDirectivity = new PolarGraphDirectivity()
    StringBuilder sb = new StringBuilder()
    sb.append("<h2>Top</h2>")
    sb.append(polarGraphDirectivity.generatePolarGraph(directionAttributes, frequency, scaleMinimum, scaleMaximum, PolarGraphDirectivity.ORIENTATION.TOP))
    sb.append("<h2>Side</h2>")
    sb.append(polarGraphDirectivity.generatePolarGraph(directionAttributes, frequency, scaleMinimum, scaleMaximum, PolarGraphDirectivity.ORIENTATION.SIDE))
    sb.append("<h2>Front</h2>")
    sb.append(polarGraphDirectivity.generatePolarGraph(directionAttributes, frequency, scaleMinimum, scaleMaximum, PolarGraphDirectivity.ORIENTATION.FRONT))
    return sb.toString()
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
