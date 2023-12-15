/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.constructor.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests SnakeYAML blocking.
 */
public class CVE_2022_1471 {
    @AfterAll
    public static void uninstallAgent() throws Exception {
        System.clearProperty("aegis4j.additional.args");
    }

    @Test
    public void test() throws Exception {
        TestUtils.installAgent("path=" + System.getProperty("aegis4j.projectDir") + "/src/test/resources/snakeyaml-mods.properties");
        try {
            Constructor banned = new Constructor(this.getClass());
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertEquals("SnakeYAML Constructor blocked by aegis4j", e.getMessage());
        }
    }
}
