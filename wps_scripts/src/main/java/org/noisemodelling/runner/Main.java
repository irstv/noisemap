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
package org.noisemodelling.runner;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.PropertyConfigurator;
import org.h2.util.OsgiDataSourceFactory;
import org.h2gis.functions.factory.H2GISFunctions;
import org.h2gis.utilities.wrapper.ConnectionWrapper;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.osgi.service.jdbc.DataSourceFactory;


public class Main {
    public static final int SECONDS_BETWEEN_PROGRESSION_PRINT = 5;


    public static DataSource createDataSource(String user, String password, String dbDirectory, String dbName, boolean debug) throws SQLException {
        // Create H2 memory DataSource
        org.h2.Driver driver = org.h2.Driver.load();
        OsgiDataSourceFactory dataSourceFactory = new OsgiDataSourceFactory(driver);
        Properties properties = new Properties();
        String databasePath = "jdbc:h2:" + new File(dbDirectory, dbName).getAbsolutePath();
        properties.setProperty(DataSourceFactory.JDBC_URL, databasePath);
        properties.setProperty(DataSourceFactory.JDBC_USER, user);
        properties.setProperty(DataSourceFactory.JDBC_PASSWORD, password);
        if(debug) {
            properties.setProperty("TRACE_LEVEL_FILE", "3"); // enable debug
        }
        DataSource dataSource = dataSourceFactory.createDataSource(properties);
        // Init spatial ext
        try (Connection connection = dataSource.getConnection()) {
            H2GISFunctions.load(connection);
        }
        return dataSource;

    }

    public static void printBuildIdentifiers(Logger logger) {
        try {
            String columnFormat = "%-35.35s %-35.35s %-20.20s %-30.30s";
            String[] columns = new String[] {"name", "last-modified", "version", "commit"};
            Enumeration<URL> resources = Main.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append( "Loaded libraries:\n");
            stringBuilder.append(String.format(Locale.ROOT, columnFormat,
                    (Object[]) columns));
            stringBuilder.append( "\n");
            Map<String, ArrayList<String>> rows = new HashMap<>();
            for (String column : columns) {
                rows.put(column, new ArrayList<>());
            }
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                    "EEE, d MMM yyyy HH:mm:ss Z", Locale.getDefault());
            int nbRows = 0;
            while (resources.hasMoreElements()) {
                try {
                    Manifest manifest = new Manifest(resources.nextElement().openStream());
                    Attributes attributes = manifest.getMainAttributes();
                    String bundleName = attributes.getValue("Bundle-Name");
                    String bundleVersion = attributes.getValue("Bundle-Version");
                    String gitCommitId = attributes.getValue("Implementation-Build");
                    String lastModifier = attributes.getValue("Bnd-LastModified");
                    if(bundleName != null) {
                        nbRows++;
                        rows.get(columns[0]).add(bundleName);
                        if(lastModifier != null) {
                            long lastModifiedLong = Long.parseLong(lastModifier);
                            rows.get(columns[1]).add(simpleDateFormat.format(new Date(lastModifiedLong)));
                        } else {
                            rows.get(columns[1]).add(" - ");
                        }
                        rows.get(columns[2]).add(bundleVersion != null ? bundleVersion : " - ");
                        rows.get(columns[3]).add(gitCommitId != null ? gitCommitId : " - ");
                    }
                } catch (IOException ex) {
                    logger.error(ex.getLocalizedMessage(), ex);
                }
            }
            for(int idRow = 0; idRow < nbRows; idRow++) {
                String[] rowValues = new String[columns.length];
                for (int idColumn = 0; idColumn < columns.length; idColumn++) {
                    String column = columns[idColumn];
                    rowValues[idColumn] = rows.get(column).get(idRow);
                }
                stringBuilder.append(String.format(Locale.ROOT, columnFormat,
                        (Object[]) rowValues));
                stringBuilder.append("\n");
            }
            logger.info(stringBuilder.toString());
        } catch (IOException ex) {
            logger.error("Error while accessing resources", ex);
        }
    }


    public static void main(String... args) throws Exception {
        PropertyConfigurator.configure(Main.class.getResource("log4j.properties"));
        // Arguments parser
        Options options = new Options();
        Option workingDirOption = new Option("w", "working-dir", true, "Path where the database will be located");
        workingDirOption.setRequired(true);
        workingDirOption.setArgName("folder path");
        options.addOption(workingDirOption);
        Option scriptPathOption = new Option("s", "script", true, "Path and file name of the script");
        scriptPathOption.setRequired(true);
        scriptPathOption.setArgName("script path");
        options.addOption(scriptPathOption);
        Option databaseNameOption = new Option("d", "database-name", true, "Database name (default to h2gisdb)");
        options.addOption(databaseNameOption);
        Option printVersionOption = new Option("v", false,"Print version of all libraries");
        options.addOption(printVersionOption);
        Logger logger = LoggerFactory.getLogger("org.noise_planet");
        try {
            // Read parameters
            String workingDir = "";
            String scriptPath = "";
            String databaseName = "h2gisdb";
            Map<String, String> customParameters = new HashMap<>();
            boolean printVersion = false;

            CommandLineParser commandLineParser = new DefaultParser();
            HelpFormatter helpFormatter = new HelpFormatter();
            CommandLine commandLine;
            try {
                commandLine = commandLineParser.parse(options, args, true);
            } catch (ParseException ex) {
                logger.info(ex.getMessage());
                helpFormatter.printHelp("NoiseModelling Script Runner", options);
                System.exit(1);
                return;
            }
            workingDir = commandLine.getOptionValue(workingDirOption.getOpt());
            scriptPath = commandLine.getOptionValue(scriptPathOption.getOpt());
            printVersion = commandLine.hasOption(printVersionOption.getOpt());

            if(printVersion) {
                printBuildIdentifiers(logger);
            }

            // Open database
            DataSource ds = createDataSource("", "", new File(workingDir).getAbsolutePath(), databaseName, false);

            RootProgressVisitor progressVisitor = new RootProgressVisitor(1, true,
                    SECONDS_BETWEEN_PROGRESSION_PRINT);

            try (Connection connection = new ConnectionWrapper(ds.getConnection())) {
                GroovyShell shell = new GroovyShell();
                Script script= shell.parse(new File(scriptPath));
                script.run();
                if(shell.getVariable("inputs") == null) {
                    throw new IllegalArgumentException("Script does not contains inputs variable");
                }
                ((Map) shell.getVariable("inputs")).forEach((key, value) -> {
                    Map<String, Object> optionAttributes = ((Map)value);
                    Option customOption = new Option(key.toString(),
                            optionAttributes.get("type") != Boolean.class,
                            optionAttributes.getOrDefault("description", "").
                                    toString().replaceAll("<[^>]*>", ""));
                    customOption.setType((Class)optionAttributes.get("type"));
                    customOption.setArgs(1);
                    customOption.setArgName(optionAttributes.get("name").toString());
                    customOption.setRequired(!optionAttributes.containsKey("min") || (Integer)optionAttributes.get("min") == 1);
                    options.addOption(customOption);
                });
                try {
                    commandLine = commandLineParser.parse(options, args);
                    for (Iterator<Option> it = commandLine.iterator(); it.hasNext(); ) {
                        Option option = it.next();
                        customParameters.put(option.getOpt(), option.getValue());
                    }
                } catch (ParseException ex) {
                    logger.info(ex.getMessage());
                    helpFormatter.printHelp("NoiseModelling Script Runner", options);
                    System.exit(1);
                    return;
                }
                Map<String, Object> inputs = new HashMap<>(customParameters);
                inputs.put("progressVisitor", progressVisitor);
                Object result = script.invokeMethod("exec", new Object[] {connection, inputs});
                if(result != null) {
                    logger.info(result.toString());
                }
            } catch (SQLException ex) {
                while (ex != null) {
                    logger.error(ex.getLocalizedMessage(), ex);
                    ex = ex.getNextException();
                }
                System.exit(1);
            }
        } catch (Throwable ex) {
            logger.error(ex.getLocalizedMessage(), ex);
            System.exit(1);
        }
    }
}
