/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The main agent class. This class fails fast on any configuration errors, so that e.g. a typo
 * which prevents a feature from blocking correctly does not create a false sense of security.
 * Once the hand-off to {@link Patcher} has occurred and the class patching has begun, errors are
 * handled more leniently.
 *
 * @see <a href="https://www.baeldung.com/java-instrumentation">Java instrumentation primer</a>
 * @see <a href="https://github.com/nccgroup/log4j-jndi-be-gone">Alternate Java agent project</a>
 */
public final class AegisAgent {

    public static boolean log = Boolean.getBoolean("aegis4j.log");

    public static void logPrint(String line) {
        if (log) System.out.print(line);
    }

    public static void logPrintln(String line) {
        if (log) System.out.println(line);
    }

    private static Instrumentation instrumentation;

    /**
     * Supports static attach (via -javaagent parameter at JVM startup).
     *
     * @param args  agent arguments
     * @param instr instrumentation services
     */
    public static void premain(String args, Instrumentation instr) {
        instrumentation = instr;
        String argsProperty = System.getProperty("aegis4j.additional.args");
        if (argsProperty != null) {
            if (args == null || args.trim().isEmpty()) {
                args = argsProperty;
            } else {
                args += ";" + argsProperty;
            }
        }
        try {
            boolean started = false;
            Properties props = null;
            if (args != null) {
                if (args.trim().equalsIgnoreCase("dynamic")) return;
                for (String arg : args.split(";")) {
                    if (started) throw new IllegalArgumentException("Aegis4j ERROR: parameter ordering means patching already started");
                    String normalisedaArg = arg.trim().toLowerCase();
                    if (normalisedaArg.isEmpty() || normalisedaArg.startsWith("block=") || normalisedaArg.startsWith("unblock=")) {
                        Patcher.start(instr, toBlockList(normalisedaArg, props), getModificationsProperties(props));
                        started = true;
                    } else if (normalisedaArg.startsWith("path=")) {
                        String pathString = arg.trim().substring(5);
                        Path path;
                        if (pathString.startsWith(File.pathSeparator)) {
                            path = Paths.get(pathString);
                        } else {
                            Path agentJar = Paths.get(AegisAgent.class.getProtectionDomain().getCodeSource().getLocation().getPath());
                            path = agentJar.resolveSibling(pathString);
                        }
                        InputStream in = path.toUri().toURL().openStream();
                        props = readPropertiesFromStream(in);
                        logPrintln("Aegis4j patching from " + path + " mods file");
                    } else if (normalisedaArg.startsWith("resource=")) {
                        String pathString = arg.trim().substring(9);
                        InputStream in = ClassLoader.getSystemResourceAsStream(pathString);
                        if (in == null) throw new IOException("Unable to load mods resource " + pathString);
                        props = readPropertiesFromStream(in);
                        logPrintln("Aegis4j patching from " + pathString + " mods resource");
                    } else {
                        throw new IllegalArgumentException("Aegis4j ERROR: unrecognised parameters " + arg);
                    }
                }
            }
            if (!started) {
                Patcher.start(instr, toBlockList("", props), getModificationsProperties(props));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Aegis4j ERROR: Unable to process mods file", e);
        }
    }

    /**
     * Supports dynamic attach (via the com.sun.tools.attach.* API).
     *
     * @param args  agent arguments
     * @param instr instrumentation services
     */
    public static void agentmain(String args, Instrumentation instr) {
        premain(args, instr);
    }

    static void dynamicLoad(String args) {
        if (instrumentation == null) throw new IllegalStateException("Cannot dynamically load agent if pre-initialised.");
        agentmain(args, instrumentation);
    }

    /**
     * Parses the agent argument string (e.g. {@code "block=jndi,rmi,serialization"} or {@code "unblock=process"})
     * into a feature block list.
     *
     * @param args agent arguments
     * @return the block list derived from the agent arguments
     */
    protected static Set<String> toBlockList(String args, Properties override) throws IOException {
        Set<String> all = loadFeaturesFromModifications(override);
        if (args == null || args.trim().isEmpty()) {
            // no arguments provided by user
            return all;
        }

        args = args.trim().toLowerCase();
        int eq = args.indexOf('=');
        if (eq == -1) {
            // incorrect argument format, we expect a single "name=value" parameter
            throw new IllegalArgumentException("Aegis4j ERROR: Invalid agent configuration string");
        }

        String name = args.substring(0, eq).trim();
        String value = args.substring(eq + 1).trim();

        if ("block".equals(name)) {
            // user is providing their own block list
            return split(value, all);
        } else if ("unblock".equals(name)) {
            // user is modifying the default block list
            Set<String> block = new HashSet<>(all);
            Set<String> unblock = split(value, all);
            block.removeAll(unblock);
            return Collections.unmodifiableSet(block);
        } else {
            // no idea what the user is doing...
            throw new IllegalArgumentException("Aegis4j ERROR: Unrecognized parameter name (should be one of 'block' or 'unblock'): " + name);
        }
    }

    /**
     * Splits the specified comma-delimited feature list, validating that all specified features are valid.
     *
     * @param values the comma-delimited feature list
     * @param all    the list of valid features to validate against
     * @return the feature list, split into individual (validated) feature names
     * @throws IllegalArgumentException if any unrecognized feature names are included in the comma-delimited feature list
     */
    private static Set<String> split(String values, Set<String> all) {
        Set<String> features = Arrays.asList(values.split(","))
                .stream()
                .map(String::trim)
                .collect(Collectors.toSet());

        for (String feature : features) {
            if (!all.contains(feature)) {
                throw new IllegalArgumentException("Aegis4j ERROR: Unrecognized feature name: " + feature);
            }
        }

        return Collections.unmodifiableSet(features);
    }

    private static Set<String> loadFeaturesFromModifications(Properties override) throws IOException {
        Properties props = getModificationsProperties(override);
        Set<String> features = new HashSet<String>();
        for (String key : props.stringPropertyNames()) {
            int first = key.indexOf('.');
            String feature = key.substring(0, first).toLowerCase();
            features.add(feature);
        }
        return Collections.unmodifiableSet(features);
    }

    public static Properties getModificationsProperties(Properties props) throws IOException {
        if (props != null) return props;
        return readPropertiesFromStream(Patcher.class.getResourceAsStream("mods.properties"));
    }

    public static Properties readPropertiesFromStream(InputStream stream) throws IOException {
        if (stream == null) return null;
        try {
            Properties props = new Properties();
            props.load(stream);
            return props;
        } finally {
            stream.close();
        }
    }
}
