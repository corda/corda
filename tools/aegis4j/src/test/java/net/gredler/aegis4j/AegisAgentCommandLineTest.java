/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        //                    CONFIG                           EXPECTED ERROR
        testStaticAttach(jar, "block=jndi", "");
        testStaticAttach(jar, "unblock=serialization", "");
        testStaticAttach(jar, "block=serialization", "Java serialization blocked by aegis4j");
        testStaticAttach(jar, "block=serialization;", "Java serialization blocked by aegis4j");
        testStaticAttach(jar, ";block=serialization", "ERROR: parameter ordering means patching already started");
        testStaticAttach(jar, "foo", "ERROR: unrecognised parameters foo");
        testStaticAttach(jar, "dynamic", "");

        Path path = Paths.get(System.getProperty("aegis4j.projectDir") + "/src/main/resources/net/gredler/aegis4j/mods.properties");
        testStaticAttach(jar, "path=" + path, "Java serialization blocked by aegis4j");
        testStaticAttach(jar, "path=" + path + ";unblock=serialization", "");
        testStaticAttach(jar, "path=/foo/bar", "java.io.FileNotFoundException: /foo/bar");
    }

    private static void testStaticAttach(String jar, String config, String expectedErr) throws Exception {

        String main = Main.class.getName();
        String cp = System.getProperty("java.class.path");
        Process process = new ProcessBuilder("java", "-javaagent:" + jar + "=" + config, "-cp", cp, main).start();
        process.waitFor(100, TimeUnit.SECONDS);
        assertFalse(process.isAlive());

        String out = new String(TestUtils.inputStreamReadAllBytes(process.getInputStream()), UTF_8);
        String err = new String(TestUtils.inputStreamReadAllBytes(process.getErrorStream()), UTF_8);
        String summary = "OUT: " + out + "\nERR: " + err;
        assertEquals(expectedErr.isEmpty(), out.endsWith("done" + System.lineSeparator()), summary);
        assertEmptyOrContains(expectedErr, err, summary);
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
