package com.r3.corda.networkmanage.doorman

import com.typesafe.config.ConfigException
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DoormanParametersTest {
    private val testDummyPath = ".${File.separator}testDummyPath.jks"
    private val validInitialNetworkConfigPath = File(javaClass.getResource("/initial-network-parameters.conf").toURI()).absolutePath
    private val validConfigPath = File(javaClass.getResource("/doorman.conf").toURI()).absolutePath
    private val invalidConfigPath = File(javaClass.getResource("/doorman_fail.conf").toURI()).absolutePath

    private val requiredArgs = arrayOf("--configFile", validConfigPath, "--initialNetworkParameters", validInitialNetworkConfigPath)

    @Test
    fun `parse mode flag arg correctly`() {
        assertEquals(DoormanParameters.Mode.CA_KEYGEN, callParseParametersWithRequiredArgs("--mode", "CA_KEYGEN").mode)
        assertEquals(DoormanParameters.Mode.ROOT_KEYGEN, callParseParametersWithRequiredArgs("--mode", "ROOT_KEYGEN").mode)
        assertEquals(DoormanParameters.Mode.DOORMAN, callParseParametersWithRequiredArgs("--mode", "DOORMAN").mode)
    }

    @Test
    fun `command line arg should override config file`() {
        val params = callParseParametersWithRequiredArgs("--keystorePath", testDummyPath, "--port", "1000")
        assertEquals(testDummyPath, params.keystorePath.toString())
        assertEquals(1000, params.port)

        val params2 = callParseParametersWithRequiredArgs()
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
        val parameter = callParseParametersWithRequiredArgs()
        assertEquals("https://doorman-jira-host.com/", parameter.jiraConfig?.address)
        assertEquals("TD", parameter.jiraConfig?.projectCode)
        assertEquals("username", parameter.jiraConfig?.username)
        assertEquals("password", parameter.jiraConfig?.password)
        assertEquals(41, parameter.jiraConfig?.doneTransitionCode)
    }

    private fun callParseParametersWithRequiredArgs(vararg additionalArgs: String): DoormanParameters {
        return parseParameters(*(requiredArgs + additionalArgs))
    }
}
