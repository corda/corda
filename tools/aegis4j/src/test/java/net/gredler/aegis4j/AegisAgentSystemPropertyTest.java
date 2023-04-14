/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link AegisAgent} extra args via system properties.
 */
public class AegisAgentSystemPropertyTest {
    @AfterAll
    public static void uninstallAgent() throws Exception {
        System.clearProperty("aegis4j.additional.args");
        TestUtils.installAgent("unblock=serialization");
    }

    @Test
    public void testSystemPropertyWithNullExistingArgs() throws Exception {
        System.setProperty("aegis4j.additional.args", "unblock=jndi,rmi,scripting");
        TestUtils.installAgent(null);
        assertEquals("serialization,process,httpserver", System.getProperty("aegis4j.blocked.features"));
    }

    @Test
    public void testSystemPropertyWithEmptyExistingArgs() throws Exception {
        System.setProperty("aegis4j.additional.args", "unblock=jndi,rmi,scripting");
        TestUtils.installAgent("");
        assertEquals("serialization,process,httpserver", System.getProperty("aegis4j.blocked.features"));
    }

    @Test
    public void testSystemPropertyWithNonEmptyExistingArgs() throws Exception {
        System.setProperty("aegis4j.additional.args", "unblock=jndi,rmi,scripting");
        TestUtils.installAgent("path=" + System.getProperty("aegis4j.projectDir") + "/src/main/resources/net/gredler/aegis4j/mods.properties");
        assertEquals("serialization,process,httpserver", System.getProperty("aegis4j.blocked.features"));
    }
}
