/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link AegisAgent} monitoring via system properties.
 */
public class AegisAgentMonitoringTest {
    @AfterAll
    public static void uninstallAgent() throws Exception {
        TestUtils.installAgent("unblock=serialization");
    }

    @Test
    public void testSystemProperty() throws Exception {
        assertNull(System.getProperty("aegis4j.blocked.features"));
        TestUtils.installAgent("unblock=jndi,rmi,scripting");
        assertEquals("serialization,process,httpserver", System.getProperty("aegis4j.blocked.features"));
    }
}
