package net.corda.demobench.config;

import java.io.*;
import java.nio.file.*;
import java.util.logging.*;

/**
 * Configuration class for JUL / TornadoFX.
 * Requires <code>-Djava.util.logging.config.class=net.corda.demobench.config.LoggingConfig</code>
 * to be added to the JVM's command line.
 */
public class LoggingConfig {
    private static final String LOGGING_CONFIG = "logging.properties";

    public LoggingConfig() throws IOException {
        try (InputStream input = getLoggingProperties()) {
            LogManager manager = LogManager.getLogManager();
            manager.readConfiguration(input);
        }
    }

    private static InputStream getLoggingProperties() throws IOException {
        ClassLoader classLoader = LoggingConfig.class.getClassLoader();
        InputStream input = classLoader.getResourceAsStream(LOGGING_CONFIG);
        if (input == null) {
            // Use the default JUL logging configuration properties instead.
            Path logging = Paths.get(System.getProperty("java.home"), "lib", LOGGING_CONFIG);
            input = Files.newInputStream(logging, StandardOpenOption.READ);
        }
        return input;
    }

}
