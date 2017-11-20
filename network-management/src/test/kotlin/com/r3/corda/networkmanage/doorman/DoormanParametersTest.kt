package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.typesafe.config.ConfigException
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DoormanParametersTest {
    private val validInitialNetworkConfigPath = File(javaClass.getResource("/initial-network-parameters.conf").toURI()).absolutePath
    private val validConfigPath = File(javaClass.getResource("/doorman.conf").toURI()).absolutePath
    private val invalidConfigPath = File(javaClass.getResource("/doorman_fail.conf").toURI()).absolutePath
    private val validArgs = arrayOf("--config-file", validConfigPath, "--initial-network-parameters", validInitialNetworkConfigPath)

    @Test
    fun `should fail when initial network parameters file is missing`() {
        val message = assertFailsWith<IllegalStateException> {
            parseCommandLine("--config-file", validConfigPath, "--initial-network-parameters", "not-here")
        }.message
        assertThat(message).contains("Initial network parameters file ")
    }

    @Test
    fun `should fail when config file is missing`() {
        val message = assertFailsWith<IllegalStateException> {
            parseCommandLine("--config-file", "not-existing-file", "--initial-network-parameters", validInitialNetworkConfigPath)
        }.message
        assertThat(message).contains("Config file ")
    }

    @Test
    fun `should throw ShowHelpException when help option is passed on the command line`() {
        assertFailsWith<ShowHelpException> {
            parseCommandLine("-?")
        }
    }

    @Test
    fun `should fail when config missing`() {
        assertFailsWith<ConfigException.Missing> {
            parseParameters(parseCommandLine("--config-file", invalidConfigPath, "--initial-network-parameters", validInitialNetworkConfigPath).configFile)
        }
    }

    @Test
    fun `should parse jira config correctly`() {
        val parameter =  parseCommandLineAndGetParameters()
        assertEquals("https://doorman-jira-host.com/", parameter.jiraConfig?.address)
        assertEquals("TD", parameter.jiraConfig?.projectCode)
        assertEquals("username", parameter.jiraConfig?.username)
        assertEquals("password", parameter.jiraConfig?.password)
        assertEquals(41, parameter.jiraConfig?.doneTransitionCode)
    }

    private fun parseCommandLineAndGetParameters(): DoormanParameters {
        return parseParameters(parseCommandLine(*validArgs).configFile)
    }
}
