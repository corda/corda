package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.typesafe.config.ConfigException
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DoormanParametersTest {
    private val validOverrideNetworkConfigPath = File("network-parameters.conf").absolutePath
    private val validConfigPath = File("doorman.conf").absolutePath
    private val invalidConfigPath = File(javaClass.getResource("/doorman_fail.conf").toURI()).absolutePath
    private val validArgs = arrayOf("--config-file", validConfigPath, "--update-network-parameters", validOverrideNetworkConfigPath)

    @Test
    fun `should fail when initial network parameters file is missing`() {
        val message = assertFailsWith<InvocationTargetException> {
            parseParameters("--config-file", validConfigPath, "--update-network-parameters", "not-here")
        }.targetException.message
        assertThat(message).contains("Update network parameters file ")
    }

    @Test
    fun `should fail when config file is missing`() {
        val message = assertFailsWith<IllegalStateException> {
            parseParameters("--config-file", "not-existing-file")
        }.message
        assertThat(message).contains("Config file ")
    }

    @Test
    fun `should throw ShowHelpException when help option is passed on the command line`() {
        assertFailsWith<ShowHelpException> {
            parseParameters("-?")
        }
    }

    @Test
    fun `should fail when config missing`() {
        assertFailsWith<ConfigException.Missing> {
            parseParameters("--config-file", invalidConfigPath)
        }
    }

    @Test
    fun `should parse jira config correctly`() {
        val parameter = parseParameters(*validArgs).doormanConfig!!
        assertEquals("https://doorman-jira-host.com/", parameter.jiraConfig?.address)
        assertEquals("TD", parameter.jiraConfig?.projectCode)
        assertEquals("username", parameter.jiraConfig?.username)
        assertEquals("password", parameter.jiraConfig?.password)
        assertEquals(41, parameter.jiraConfig?.doneTransitionCode)
    }
}
