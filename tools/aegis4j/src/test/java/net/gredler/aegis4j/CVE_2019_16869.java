/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests netty-codec-http blocking.
 */
public class CVE_2019_16869 {
    @AfterAll
    public static void uninstallAgent() throws Exception {
        System.clearProperty("aegis4j.additional.args");
    }

    @Test
    public void test() throws Exception {
        TestUtils.installAgent("path=" + System.getProperty("aegis4j.projectDir") + "/src/test/resources/netty-mods.properties");
        try {
            new HttpRequestDecoder();
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertEquals("netty-codec-http HttpMessageDecoder blocked by aegis4j", e.getMessage());
        }
    }
}
