/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests Guava blocking.
 */
public class CVE_2020_8908 {
    @AfterAll
    public static void uninstallAgent() throws Exception {
        System.clearProperty("aegis4j.additional.args");
    }

    @Test
    public void test() throws Exception {
        TestUtils.installAgent("path=" + System.getProperty("aegis4j.projectDir") + "/src/test/resources/guava-mods.properties");
        try {
            com.google.common.io.Files.createTempDir();
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertEquals("Guava createTempDir blocked by aegis4j", e.getMessage());
        }
    }
}
