package com.r3.corda.doorman

import com.typesafe.config.ConfigException
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DoormanParametersTest {
    private val testDummyPath = ".${File.separator}testDummyPath.jks"
    private val validConfigPath = javaClass.getResource("/node.conf").path
    private val invalidConfigPath = javaClass.getResource("/node_fail.conf").path

    @Test
    fun `parse mode flag arg correctly`() {
        assertEquals(DoormanParameters.Mode.CA_KEYGEN, parseParameters("--mode", "CA_KEYGEN", "--configFile", validConfigPath).mode)
        assertEquals(DoormanParameters.Mode.ROOT_KEYGEN, parseParameters("--mode", "ROOT_KEYGEN", "--configFile", validConfigPath).mode)
        assertEquals(DoormanParameters.Mode.DOORMAN, parseParameters("--mode", "DOORMAN", "--configFile", validConfigPath).mode)
    }

    @Test
    fun `command line arg should override config file`() {
        val params = parseParameters("--keystorePath", testDummyPath, "--port", "1000", "--configFile", validConfigPath)
        assertEquals(testDummyPath, params.keystorePath.toString())
        assertEquals(1000, params.port)

        val params2 = parseParameters("--configFile", validConfigPath)
        assertEquals(Paths.get("/opt/doorman/certificates/caKeystore.jks"), params2.keystorePath)
        assertEquals(8080, params2.port)
    }

    @Test
    fun `should fail when config missing`() {
        // dataSourceProperties is missing from node_fail.conf and it should fail during parsing, and shouldn't use default from reference.conf.
        assertFailsWith<ConfigException.Missing> {
            parseParameters("--configFile", invalidConfigPath)
        }
    }

    @Test
    fun `should parse jira config correctly`() {
        val parameter = parseParameters("--configFile", validConfigPath)
        assertEquals("https://doorman-jira-host.com/", parameter.jiraConfig?.address)
        assertEquals("TD", parameter.jiraConfig?.projectCode)
        assertEquals("username", parameter.jiraConfig?.username)
        assertEquals("password", parameter.jiraConfig?.password)
        assertEquals(41, parameter.jiraConfig?.doneTransitionCode)
    }
}
