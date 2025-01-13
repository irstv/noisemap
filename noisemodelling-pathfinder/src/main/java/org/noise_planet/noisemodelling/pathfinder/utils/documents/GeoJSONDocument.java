/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.utils.documents;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.cts.CRSFactory;
import org.cts.IllegalCoordinateException;
import org.cts.crs.CRSException;
import org.cts.crs.CoordinateReferenceSystem;
import org.cts.crs.GeodeticCRS;
import org.cts.op.CoordinateOperation;
import org.cts.op.CoordinateOperationException;
import org.cts.op.CoordinateOperationFactory;
import org.cts.registry.EPSGRegistry;
import org.cts.registry.RegistryManager;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.delaunay.Triangle;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Export rays for validation
 */
public class GeoJSONDocument {
    public JsonGenerator jsonGenerator;
    public String crs = "EPSG:4326";
    public int rounding = -1;
    public CoordinateOperation transform = null;

    public GeoJSONDocument(OutputStream outputStream) throws IOException {
        JsonFactory jsonFactory = new JsonFactory();
        jsonGenerator = jsonFactory.createGenerator(outputStream, JsonEncoding.UTF8);
        jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter("\n"));
    }

    public void doTransform(Geometry geometry) {
        if(transform != null && geometry != null) {
            geometry.apply(new KMLDocument.CRSTransformFilter(transform));
            // Recompute envelope
            geometry.setSRID(4326);
        }
    }

    public void setInputCRS(String crs) throws CRSException, CoordinateOperationException {
        // Create a new CRSFactory, a necessary element to create a CRS without defining one by one all its components
        CRSFactory cRSFactory = new CRSFactory();

        // Add the appropriate registry to the CRSFactory's registry manager. Here the EPSG registry is used.
        RegistryManager registryManager = cRSFactory.getRegistryManager();
        registryManager.addRegistry(new EPSGRegistry());

        // CTS will read the EPSG registry seeking the 4326 code, when it finds it,
        // it will create a CoordinateReferenceSystem using the parameters found in the registry.
        CoordinateReferenceSystem crsKML = cRSFactory.getCRS("EPSG:4326");
        CoordinateReferenceSystem crsSource = cRSFactory.getCRS(crs);
        if(crsKML instanceof GeodeticCRS && crsSource instanceof GeodeticCRS) {
            transform = CoordinateOperationFactory.createCoordinateOperations((GeodeticCRS) crsSource, (GeodeticCRS) crsKML).iterator().next();
        }
    }

    public void setRounding(int rounding) {
        this.rounding = rounding;
    }

    /**
     *
     * @throws IOException
     */
    public void writeFooter() throws IOException {
        jsonGenerator.writeEndArray(); // features
        jsonGenerator.writeObjectFieldStart("crs");
        jsonGenerator.writeStringField("type", "name");
        jsonGenerator.writeObjectFieldStart("properties");
        jsonGenerator.writeStringField("name", crs);
        jsonGenerator.writeEndObject(); // properties
        jsonGenerator.writeEndObject(); // crs
        jsonGenerator.writeEndObject(); // {
        jsonGenerator.flush();
        jsonGenerator.close();
    }

    /**
     *
     * @throws IOException
     */
    public void writeHeader() throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("type", "FeatureCollection");
        jsonGenerator.writeArrayFieldStart("features");
    }


    /**
     * 
     * @param profile
     * @throws IOException
     */
    public void writeProfile(CutProfile profile) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("type", "Feature");
        jsonGenerator.writeObjectFieldStart("geometry");
        jsonGenerator.writeStringField("type", "LineString");
        jsonGenerator.writeFieldName("coordinates");
        jsonGenerator.writeStartArray();

        for(CutPoint cutPoint : profile.cutPoints) {
            writeCoordinate(new Coordinate(cutPoint.getCoordinate()));
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject(); // geometry
        // Write properties
        jsonGenerator.writeObjectFieldStart("properties");
        jsonGenerator.writeNumberField("receiver", profile.getReceiver().receiverPk);
        jsonGenerator.writeNumberField("source", profile.getSource().sourcePk);
        jsonGenerator.writeEndObject(); // properties
        jsonGenerator.writeEndObject();
    }

    /**
     *
     * @param cutPoint
     * @throws IOException
     */
    public void writeCutPoint(CutPoint cutPoint) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("type", "Feature");
        jsonGenerator.writeObjectFieldStart("geometry");
        jsonGenerator.writeStringField("type", "Point");
        jsonGenerator.writeFieldName("coordinates");
        writeCoordinate(new Coordinate(cutPoint.getCoordinate()));
        jsonGenerator.writeEndObject(); // geometry
        // Write properties
        jsonGenerator.writeObjectFieldStart("properties");
        jsonGenerator.writeObject(cutPoint);
        jsonGenerator.writeEndObject(); // properties
        jsonGenerator.writeEndObject();
        jsonGenerator.flush();
    }

    /**
     * Write topography triangles
     * @param triVertices
     * @param vertices
     * @throws IOException
     */
    public void writeTopographic(List<Triangle> triVertices, List<Coordinate> vertices) throws IOException {
        for(Triangle triangle : triVertices) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("type", "Feature");
            jsonGenerator.writeObjectFieldStart("geometry");
            jsonGenerator.writeStringField("type", "Polygon");
            jsonGenerator.writeFieldName("coordinates");
            jsonGenerator.writeStartArray();
            jsonGenerator.writeStartArray(); // Outer line
            writeCoordinate(new Coordinate(vertices.get(triangle.getA())));
            writeCoordinate(new Coordinate(vertices.get(triangle.getB())));
            writeCoordinate(new Coordinate(vertices.get(triangle.getC())));
            writeCoordinate(new Coordinate(vertices.get(triangle.getA())));
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndObject(); // geometry
            // Write properties
            jsonGenerator.writeObjectFieldStart("properties");
            jsonGenerator.writeNumberField("b", triangle.getAttribute());
            jsonGenerator.writeEndObject(); // properties
            jsonGenerator.writeEndObject();
        }
    }

    /**
     * Write coordinate positions.
     *
     * @param coordinate
     * @throws IOException
     */
    public void writeCoordinate(Coordinate coordinate) throws IOException {
        jsonGenerator.writeStartArray();
        if(transform != null) {
            try {
                double[] coords = transform.transform(new double[]{coordinate.x, coordinate.y});
                coordinate = new Coordinate(coords[0], coords[1], coordinate.z);
            } catch (IllegalCoordinateException | CoordinateOperationException ex) {
                throw new IOException("Error while doing transform", ex);
            }
        }
        writeNumber(coordinate.x);
        writeNumber(coordinate.y);
        if (!Double.isNaN(coordinate.z)) {
            writeNumber(coordinate.z);
        }
        jsonGenerator.writeEndArray();
    }

    public void writeNumber(double number) throws IOException {
        if(rounding >= 0) {
            jsonGenerator.writeNumber(String.format(Locale.ROOT,"%."+rounding+"f", number));
        } else {
            jsonGenerator.writeNumber(number);
        }
    }
}
