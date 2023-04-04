/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link AegisAgent} monitoring via system properties.
 */
public class AegisAgentMonitoringTest {

    @Test
    public void testSystemProperty() throws Exception {
        assertNull(System.getProperty("aegis4j.blocked.features"));
        TestUtils.installAgent("unblock=jndi,rmi,unsafe,scripting");
        assertEquals("serialization,jshell,process,httpserver", System.getProperty("aegis4j.blocked.features"));
    }

}
