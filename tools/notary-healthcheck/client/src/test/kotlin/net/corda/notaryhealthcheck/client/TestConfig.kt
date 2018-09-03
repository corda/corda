package net.corda.notaryhealthcheck.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.PrintWriter

class TestConfig {
    @Test
    fun testLoadDefault() {
        val conf = loadConfig(File("non-existent").toPath())
        assertEquals(120, conf.waitPeriodSeconds)
        assertEquals(300, conf.waitForOutstandingFlowsSeconds)
        assertNull(conf.user)
        assertNull(conf.password)
        assertNull(conf.host)
        assertNull(conf.port)
    }

    @Test
    fun testLoadFile() {
        val tmpDir = createTempDir()
        try {
            val configFile = tmpDir.resolve("healthcheck.conf")
            val writer = PrintWriter(configFile)
            writer.use {
                it.println("user = foo")
                it.println("password = bar")
                it.println("waitPeriodSeconds = 100")
                it.println("port = 12345")
                it.println("host = localhost")
            }
            val conf = loadConfig(tmpDir.toPath())
            assertEquals(100, conf.waitPeriodSeconds)
            assertEquals(300, conf.waitForOutstandingFlowsSeconds)
            assertEquals("foo", conf.user)
            assertEquals("bar", conf.password)
            assertEquals("localhost", conf.host)
            assertEquals(12345, conf.port)
        } finally {
            tmpDir.delete()
        }
    }
}