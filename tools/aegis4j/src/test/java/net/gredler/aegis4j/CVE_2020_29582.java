/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests Kotlin blocking.
 */
public class CVE_2020_29582 {
    @AfterAll
    public static void uninstallAgent() throws Exception {
        System.clearProperty("aegis4j.additional.args");
    }

    @Test
    public void testDir() throws Exception {
        TestUtils.installAgent("path=" + System.getProperty("aegis4j.projectDir") + "/src/test/resources/kotlin-mods.properties");
        try {
            new KotlinStdLibUtils().kotlinCreateTempDir();
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertEquals("Kotlin createTempDir blocked by aegis4j", e.getMessage());
        }
    }

    @Test
    public void testFile() throws Exception {
        TestUtils.installAgent("path=" + System.getProperty("aegis4j.projectDir") + "/src/test/resources/kotlin-mods.properties");
        try {
            new KotlinStdLibUtils().kotlinCreateTempFile();
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertEquals("Kotlin createTempFile blocked by aegis4j", e.getMessage());
        }
    }
}
