/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.h2.server.web.WebServlet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests H2 Web Console blocking.
 */
public class CVE_2018_10054 {
    @AfterAll
    public static void uninstallAgent() throws Exception {
        System.clearProperty("aegis4j.additional.args");
    }

    @Test
    public void test() throws Exception {
        TestUtils.installAgent("path=" + System.getProperty("aegis4j.projectDir") + "/src/test/resources/h2-mods.properties");
        try {
            new WebServlet();
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertEquals("H2 Console blocked by aegis4j", e.getMessage());
        }
    }
}
