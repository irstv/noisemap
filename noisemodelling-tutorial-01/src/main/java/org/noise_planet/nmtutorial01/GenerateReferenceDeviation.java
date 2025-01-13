/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.nmtutorial01;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilderDecorator;
import org.noise_planet.noisemodelling.propagation.Attenuation;
import org.noise_planet.noisemodelling.propagation.AttenuationVisitor;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.IntStream;

public class GenerateReferenceDeviation {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateReferenceDeviation.class);
    private static final List<Integer> FREQ_LVL = Arrays.asList(Scene.asOctaveBands(Scene.DEFAULT_FREQUENCIES_THIRD_OCTAVE));
    private static final double[] SOUND_POWER_LEVELS = new double[]{93, 93, 93, 93, 93, 93, 93, 93};
    private static final double[] A_WEIGHTING = new double[]{-26.2, -16.1, -8.6, -3.2, 0.0, 1.2, 1.0, -1.1};

    private static final double HUMIDITY = 70;
    private static final double TEMPERATURE = 10;
    private static final String CHECKED = "☑";
    private static final String UNCHECKED = "□";
    private static final String REPORT_HEADER = "Conformity to ISO 17534-1:2015\n" +
            "==============================\n" +
            ".. This document has been generated with noisemodelling-tutorial-01/src/main/java/org/noise_planet/nmtutorial01/GenerateReferenceDeviation.java\n" +
            "\n" +
            "Conformity table\n" +
            "^^^^^^^^^^^^^^^^\n" +
            "| Conform - Do not the deviate more than ±0,1 dB \n" +
            "| NLD Conform - Do not the deviate more than ±0,1 dB neglecting lateral diffraction\n" +
            ".. list-table::\n" +
            "   :widths: 10 20 20 25 30\n" +
            "\n" +
            "   * - Test Case\n" +
            "     - Conform ? \n" +
            "     - NLD Conform ?\n" +
            "     - Largest Deviation\n" +
            "     - Details\n";

    private static CutProfile loadCutProfile(String utName) throws IOException {
        String testCaseFileName = utName + ".json";
        try(InputStream inputStream = PathFinder.class.getResourceAsStream("test_cases/"+testCaseFileName)) {
            if(inputStream == null) {
                throw new IOException("Document " + testCaseFileName + " not found");
            }
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(inputStream, CutProfile.class);
        }
    }

    public static double[] addArray(double[] first, double[] second) {
        int length = Math.min(first.length, second.length);
        double[] result = new double[length];

        for (int i = 0; i < length; i++) {
            result[i] = first[i] + second[i];
        }

        return result;
    }

    private static Attenuation computeCnossosPath(String... utNames)
            throws IOException {
        //Create profile builder
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .finishFeeding();

        //Propagation data building
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .build();

        //Propagation process path data building
        AttenuationCnossosParameters attData = new AttenuationCnossosParameters();
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);

        //Out and computation settings
        Attenuation propDataOut = new Attenuation(true, true, attData, rayData);

        AttenuationVisitor attenuationVisitor = new AttenuationVisitor(propDataOut, propDataOut.genericMeteoData);
        for (String utName : utNames) {
            CutProfile cutProfile = loadCutProfile(utName);
            attenuationVisitor.onNewCutPlane(cutProfile);
        }
        // merge attenuation per receiver
        attenuationVisitor.finalizeReceiver(0);

        return propDataOut;
    }

    private static double[] asArray(JsonNode arrayNode) {
        double[] doubleArray = new double[arrayNode.size()];
        for (int i = 0; i < arrayNode.size(); i++) {
            doubleArray[i] = arrayNode.get(i).asDouble();
        }
        return doubleArray;
    }

    private static DeviationResult computeDeviation(double[] expected, double[] actual) {
        assert expected.length == actual.length;
        assert expected.length == FREQ_LVL.size();
        int largestDiffIndex = IntStream.range(0, expected.length)
                .boxed()
                .max(Comparator.comparingDouble(i -> Math.abs(expected[i] - actual[i])))
                .orElse(-1);
        if(largestDiffIndex >= 0) {
            return new DeviationResult(Math.abs(expected[largestDiffIndex] - actual[largestDiffIndex]),
                    FREQ_LVL.get(largestDiffIndex));
        } else {
            return new DeviationResult(0, 0);
        }
    }

    private static void addUTDeviation(String utName, StringBuilder sb, JsonNode expectedValues, Attenuation actual, Attenuation actualWithoutLateral, double[] powerLevel) {
        double[] expectedLA = asArray(expectedValues.get("LA"));
        double[] expectedLAWithoutLateral = asArray(expectedValues.get("LA_WL"));
        double[] actualLA = addArray(powerLevel, addArray(actual.receiversAttenuationLevels.getFirst().value,
                A_WEIGHTING));
        double[] actualLAWithoutLateral = addArray(powerLevel,
                addArray(actualWithoutLateral.receiversAttenuationLevels.getFirst().value,
                A_WEIGHTING));
        DeviationResult lADeviation = computeDeviation(expectedLA, actualLA);
        DeviationResult lADeviationWithoutLateral = computeDeviation(expectedLAWithoutLateral, actualLAWithoutLateral);
        sb.append(String.format(Locale.ROOT, "   * - %s\n" +
                "     - %s\n" +
                "     - %s\n" +
                "     - %s\n" +
                "     - `%s`_\n",
                utName,
                lADeviation.deviation <= 0.1 ? CHECKED : UNCHECKED,
                lADeviationWithoutLateral.deviation <= 0.1 ? CHECKED : UNCHECKED,
                lADeviation.deviation > lADeviationWithoutLateral.deviation ?
                        String.format(Locale.ROOT, "%.2f dB @ %d Hz",
                                lADeviation.deviation,
                                lADeviation.frequency)
                        :
                        String.format(Locale.ROOT, "%.2f dB @ %d Hz",
                                lADeviationWithoutLateral.deviation,
                                lADeviationWithoutLateral.frequency),
                utName));
    }

    private static void addUTDeviationDetails(String utName, StringBuilder sb, JsonNode expectedValues, CnossosPath actual, double[] powerLevel) {
        double[] actualLH = addArray(actual.aGlobalH, powerLevel);
        double[] expectedLH = asArray(expectedValues.get("LH"));
        DeviationResult lhDeviation = computeDeviation(expectedLH, actualLH);
        sb.append(String.format(Locale.ROOT, "\n\n%s \n" +
                "\n" +
                "================\n" +
                "\n" +
                ".. list-table::\n" +
                "   :widths: 25 25 25\n" +
                "\n" +
                "   * - Parameters\n" +
                "     - Maximum Difference\n" +
                "     - Frequency\n" +
                "   * - Lʜ\n" +
                "     - %.2f dB\n" +
                "     - %d\n", utName.replace("_", " "), lhDeviation.deviation, lhDeviation.frequency));

        if(expectedValues.has("LF")) {
            double[] actualLF = addArray(actual.aGlobalF, powerLevel);
            double[] expectedLF = asArray(expectedValues.get("LF"));
            DeviationResult lfDeviation = computeDeviation(expectedLF, actualLF);
            sb.append(String.format(Locale.ROOT,"   * - Lꜰ\n" +
                    "     - %.2f dB\n" +
                    "     - %d\n", lfDeviation.deviation, lfDeviation.frequency));
        }
    }

    /**
     * For each cnossos test case, compute the attenuation and compare with the expected value, generate the result
     * report in rst format.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // Read working directory argument
        String workingDir = "Docs";
        if (args.length > 0) {
            workingDir = args[0];
        }
        File workingDirPath = new File(workingDir).getAbsoluteFile();
        if(!workingDirPath.exists()) {
            LOGGER.error("Working directory {} does not exists", workingDir);
            return;
        }
        try(FileWriter fileWriter = new FileWriter(new File(workingDirPath, "Cnossos_Report.rst"))) {
            fileWriter.write(REPORT_HEADER);
            try (InputStream referenceStream = GenerateReferenceDeviation.class.getResourceAsStream("reference_cnossos.json")) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.getFactory().createParser(referenceStream).readValueAsTree();
                StringBuilder stringBuilderDetail = new StringBuilder();
                for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> elt = it.next();
                    StringBuilder stringBuilder = new StringBuilder();
                    String utName = elt.getKey();
                    JsonNode pathsExpected = elt.getValue();
                    double[] powerLevel = SOUND_POWER_LEVELS;
                    if(pathsExpected.has("PL")) {
                        powerLevel = asArray(pathsExpected.get("PL"));
                    }
                    List<String> verticalCutFileNames = new ArrayList<>();
                    List<String> verticalCutFileNamesWithoutLateral = new ArrayList<>();
                    verticalCutFileNames.add(utName+"_Direct");
                    verticalCutFileNamesWithoutLateral.add(utName+"_Direct");
                    if(pathsExpected.has("Right")) {
                        verticalCutFileNames.add(utName+"_Right");
                    }
                    if(pathsExpected.has("Left")) {
                        verticalCutFileNames.add(utName+"_Left");
                    }
                    if(pathsExpected.has("Reflection")) {
                        verticalCutFileNames.add(utName+"_Reflection");
                        verticalCutFileNamesWithoutLateral.add(utName+"_Reflection");
                    }
                    Attenuation attenuation = computeCnossosPath(verticalCutFileNames.toArray(new String[]{}));
                    Attenuation attenuationWithoutLateral = computeCnossosPath(verticalCutFileNamesWithoutLateral.toArray(new String[]{}));
                    addUTDeviation(utName, stringBuilder, pathsExpected, attenuation, attenuationWithoutLateral, powerLevel);
                    fileWriter.write(stringBuilder.toString());
                    // Write details
                    stringBuilderDetail.append("\n").append(utName).append("\n^^^^\n");
                    addUTDeviationDetails("Vertical Plane", stringBuilderDetail, pathsExpected.get("Direct"), attenuation.getPropagationPaths().get(0), powerLevel);
                    int index = 1;
                    if(pathsExpected.has("Right")) {
                        addUTDeviationDetails("Right Lateral", stringBuilderDetail, pathsExpected.get("Right"), attenuation.getPropagationPaths().get(index++), powerLevel);
                    }
                    if(pathsExpected.has("Left")) {
                        addUTDeviationDetails("Left Lateral", stringBuilderDetail, pathsExpected.get("Left"), attenuation.getPropagationPaths().get(index++), powerLevel);
                    }
                    if(pathsExpected.has("Reflection")) {
                        addUTDeviationDetails("Reflection", stringBuilderDetail, pathsExpected.get("Reflection"), attenuation.getPropagationPaths().get(index), powerLevel);
                    }
                }
                fileWriter.write(stringBuilderDetail.toString());
            }
        }
    }

    static class DeviationResult {
        public DeviationResult(double deviation, int frequency) {
            this.deviation = deviation;
            this.frequency = frequency;
        }

        double deviation;
        int frequency;
    }
}
