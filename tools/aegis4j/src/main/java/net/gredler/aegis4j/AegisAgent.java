/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

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

    private static Instrumentation instrumentation;

    /**
     * Supports static attach (via -javaagent parameter at JVM startup).
     *
     * @param args  agent arguments
     * @param instr instrumentation services
     */
    public static void premain(String args, Instrumentation instr) {
        instrumentation = instr;
        if(args.trim().equalsIgnoreCase("dynamic")) return;
        Path path = null;
        boolean started = false;
        for(String arg: args.split(";")) {
            if (started) throw new IllegalArgumentException("Aegis4j ERROR: parameter ordering means patching already started");
            String normalisedaArg = arg.trim().toLowerCase();
            if(normalisedaArg.isEmpty() || normalisedaArg.startsWith("block=") || normalisedaArg.startsWith("unblock=")) {
                try {
                    Patcher.start(instr, toBlockList(normalisedaArg, path), getModificationsInputStream(path));
                    started = true;
                } catch (IOException e) {
                    throw new IllegalArgumentException("Aegis4j ERROR: Unable to process mods file", e);
                }
            } else if (normalisedaArg.startsWith("path=")) {
                path = Paths.get(arg.trim().substring(5));
                System.out.println("Aegis4j patching from " + path + " mods file");
            } else {
                throw new IllegalArgumentException("Aegis4j ERROR: unrecognised parameters " + arg);
            }
        }
        if (!started) {
            try {
                Patcher.start(instr, toBlockList("", path), getModificationsInputStream(path));
            } catch (IOException e) {
                throw new IllegalArgumentException("Aegis4j ERROR: Unable to process mods file", e);
            }
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
        agentmain(args, instrumentation);
    }

    /**
     * Parses the agent argument string (e.g. {@code "block=jndi,rmi,serialization"} or {@code "unblock=process"})
     * into a feature block list.
     *
     * @param args agent arguments
     * @return the block list derived from the agent arguments
     */
    protected static Set<String> toBlockList(String args, Path path) {
        Set<String> all = loadFeaturesFromModifications(path);
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

    private static Set<String> loadFeaturesFromModifications(Path path) {
        Properties props = new Properties();
        try {
            props.load(getModificationsInputStream(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
        Set<String> features = new HashSet<String>();
        for (String key : props.stringPropertyNames()) {
            int first = key.indexOf('.');
            String feature = key.substring(0, first).toLowerCase();
            features.add(feature);
        }
        return Collections.unmodifiableSet(features);
    }

    public static InputStream getModificationsInputStream(Path path) throws IOException {
        if(path != null) return path.toUri().toURL().openStream();
        return AegisAgent.class.getResourceAsStream("mods.properties");
    }
}
