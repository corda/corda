/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * <p>Tests {@link AegisAgent} command line use (both static and dynamic attach).
 *
 * <p><b>NOTE:</b> Code covered by these tests will not be included in the code coverage stats, because it runs
 * in the manually-forked VMs, which JaCoCo does not know about. Because of this (and because forking extra VMs
 * is extra slow), this class should only be used to test the agent entry points; most functional testing belongs
 * in {@link AegisAgentTest} instead.
 */
public class AegisAgentCommandLineTest {

    @Test
    public void testStaticAttach() throws Exception {
        String jar = TestUtils.createAgentJar();
        //                    CONFIG                   EXPECTED ERROR
        testStaticAttach(jar, "block=jndi",            "");
        testStaticAttach(jar, "unblock=serialization", "");
        testStaticAttach(jar, "block=serialization",   "Java serialization blocked by aegis4j");
    }

    @Test
    public void testDynamicAttach() throws Exception {
        String jar = TestUtils.createAgentJar();
        //                     PID    CONFIG                   EXTRA   EXPECTED APP ERROR                       EXPECTED AGENT ERROR
        testDynamicAttach(jar, true,  "block=jndi",            false,  "",                                      "");
        testDynamicAttach(jar, true,  "unblock=serialization", false,  "",                                      "");
        testDynamicAttach(jar, true,  "block=serialization",   false,  "Java serialization blocked by aegis4j", "");
        testDynamicAttach(jar, true,  "",                      false,  "Java serialization blocked by aegis4j", "");
        testDynamicAttach(jar, false, "block=serialization",   false,  "",                                      "Invalid process identifier");
        testDynamicAttach(jar, false, "",                      false,  "",                                      "ERROR: Missing required argument: pid");
        testDynamicAttach(jar, true,  "block=serialization",   true,   "",                                      "ERROR: Too many arguments provided");
    }

    private static void testStaticAttach(String jar, String config, String expectedErr) throws Exception {

        String main = Main.class.getName();
        String cp = System.getProperty("java.class.path");
        Process process = new ProcessBuilder("java", "-javaagent:" + jar + "=" + config, "-cp", cp, main).start();
        process.waitFor(5, TimeUnit.SECONDS);
        assertFalse(process.isAlive());

        String out = new String(process.getInputStream().readAllBytes(), UTF_8);
        String err = new String(process.getErrorStream().readAllBytes(), UTF_8);
        String summary = "OUT: " + out + "\nERR: " + err;
        assertEquals(expectedErr.isEmpty(), out.endsWith("done" + System.lineSeparator()), summary);
        assertEmptyOrContains(expectedErr, err, summary);
    }

    private static void testDynamicAttach(String jar, boolean addPid, String config, boolean addThirdParam, String expectedErr, String expectedAttachErr) throws Exception {

        String main = Main.class.getName();
        String cp = System.getProperty("java.class.path");
        Process process = new ProcessBuilder("java", "-cp", cp, main).start();

        List< String > cmd2 = new ArrayList<>();
        cmd2.addAll(Arrays.asList("java", "-jar", jar));
        if (addPid) cmd2.add(String.valueOf(process.pid()));
        if (!config.isEmpty()) cmd2.add(config);
        if (addThirdParam) cmd2.add("foo");
        Process process2 = new ProcessBuilder(cmd2).start();

        process.waitFor(5, TimeUnit.SECONDS);
        process2.waitFor(5, TimeUnit.SECONDS);
        assertFalse(process.isAlive());
        assertFalse(process2.isAlive());

        String out = new String(process.getInputStream().readAllBytes(), UTF_8);
        String err = new String(process.getErrorStream().readAllBytes(), UTF_8);
        String out2 = new String(process2.getInputStream().readAllBytes(), UTF_8);
        String err2 = new String(process2.getErrorStream().readAllBytes(), UTF_8);
        String summary = "OUT 1: " + out + "\nERR 1: " + err + "\nOUT 2: " + out2 + "\nERR 2: " + err2;
        assertEquals(expectedErr.isEmpty(), out.endsWith("done" + System.lineSeparator()), summary);
        assertEmptyOrContains(expectedErr, err, summary);
        assertEmptyOrContains(expectedAttachErr, err2, summary);
    }

    private static void assertEmptyOrContains(String expected, String actual, String message) {
        if (expected.isEmpty()) {
            // actual value should be empty, as well
            assertEquals(expected, actual, message);
        } else {
            // actual value should contain the expected value
            assertTrue(actual.contains(expected), message);
        }
    }

    public static class Main {
        public static void main(String[] args) throws Exception {
            // sleep until the agent finishes attaching, or time out after 30 * 100 ms = 3 seconds
            // really only relevant for dynamic attach -- no wait will be needed with static attach
            for (int i = 0; i < 30; i++) {
                if (System.getProperty("aegis4j.blocked.features") != null) {
                    break; // agent finished attaching
                }
                Thread.sleep(100);
            }
            new ObjectOutputStream(new ByteArrayOutputStream()); // triggers serialization block (if enabled)
            System.out.println("done");
        }
    }
}
