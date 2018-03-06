/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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

    public LoggingConfig() throws IOException {
        try (InputStream input = getLoggingProperties()) {
            LogManager manager = LogManager.getLogManager();
            manager.readConfiguration(input);
        }
    }

    private static InputStream getLoggingProperties() throws IOException {
        ClassLoader classLoader = LoggingConfig.class.getClassLoader();
        InputStream input = classLoader.getResourceAsStream("logging.properties");
        if (input == null) {
            Path javaHome = Paths.get(System.getProperty("java.home"));
            input = Files.newInputStream(javaHome.resolve("lib").resolve("logging.properties"));
        }
        return input;
    }

}
