/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.sun.tools.attach.VirtualMachine;

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

    /**
     * Supports easy dynamic attach via the command line.
     *
     * @param args the command line parameters (should contain the PID of the application to patch)
     * @throws Exception if any error occurs during the attach process
     */
    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new IllegalArgumentException("ERROR: Missing required argument: pid");
        }

        if (args.length > 2) {
            throw new IllegalArgumentException("ERROR: Too many arguments provided");
        }

        String pid = args[0];
        String options = args.length > 1 ? args[1] : "";

        File jar = new File(AegisAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        VirtualMachine jvm = VirtualMachine.attach(pid);
        jvm.loadAgent(jar.getAbsolutePath(), options);
        jvm.detach();
    }

    /**
     * Supports static attach (via -javaagent parameter at JVM startup).
     *
     * @param args agent arguments
     * @param instr instrumentation services
     */
    public static void premain(String args, Instrumentation instr) {
        Patcher.start(instr, toBlockList(args));
    }

    /**
     * Supports dynamic attach (via the com.sun.tools.attach.* API).
     *
     * @param args agent arguments
     * @param instr instrumentation services
     */
    public static void agentmain(String args, Instrumentation instr) {
        Patcher.start(instr, toBlockList(args));
    }

    /**
     * Parses the agent argument string (e.g. {@code "block=jndi,rmi,serialization"} or {@code "unblock=process"})
     * into a feature block list.
     *
     * @param args agent arguments
     * @return the block list derived from the agent arguments
     */
    protected static Set< String > toBlockList(String args) {

        Set< String > all = Set.of("jndi", "rmi", "process", "httpserver", "serialization", "unsafe", "scripting", "jshell");
        if (args == null || args.isBlank()) {
            // no arguments provided by user
            return all;
        }

        args = args.trim().toLowerCase();
        int eq = args.indexOf('=');
        if (eq == -1) {
            // incorrect argument format, we expect a single "name=value" parameter
            throw new IllegalArgumentException("ERROR: Invalid agent configuration string");
        }

        String name = args.substring(0, eq).trim();
        String value = args.substring(eq + 1).trim();

        if ("block".equals(name)) {
            // user is providing their own block list
            return split(value, all);
        } else if ("unblock".equals(name)) {
            // user is modifying the default block list
            Set< String > block = new HashSet<>(all);
            Set< String > unblock = split(value, all);
            block.removeAll(unblock);
            return Collections.unmodifiableSet(block);
        } else {
            // no idea what the user is doing...
            throw new IllegalArgumentException("ERROR: Unrecognized parameter name (should be one of 'block' or 'unblock'): " + name);
        }
    }

    /**
     * Splits the specified comma-delimited feature list, validating that all specified features are valid.
     *
     * @param values the comma-delimited feature list
     * @param all the list of valid features to validate against
     * @return the feature list, split into individual (validated) feature names
     * @throws IllegalArgumentException if any unrecognized feature names are included in the comma-delimited feature list
     */
    private static Set< String > split(String values, Set< String > all) {

        Set< String > features = Arrays.asList(values.split(","))
                                       .stream()
                                       .map(String::trim)
                                       .collect(Collectors.toUnmodifiableSet());

        for (String feature : features) {
            if (!all.contains(feature)) {
                throw new IllegalArgumentException("ERROR: Unrecognized feature name: " + feature);
            }
        }

        return features;
    }
}
