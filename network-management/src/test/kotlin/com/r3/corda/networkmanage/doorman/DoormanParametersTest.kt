package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.typesafe.config.ConfigException
import joptsimple.OptionException
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val message = assertFailsWith<IllegalArgumentException> {
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
    fun `should parse database config correctly`() {
        val parameter = parseParameters(*validArgs).database
        assertTrue(parameter.runMigration)
    }

    @Test
    fun `should parse trust store password correctly`() {
        val parameter = parseParameters("--config-file", validConfigPath, "--mode", "ROOT_KEYGEN", "--trust-store-password", "testPassword")
        assertEquals("testPassword", parameter.trustStorePassword)

        assertFailsWith<OptionException> {
            parseParameters("--trust-store-password")
        }

        // Should fail if password is provided in mode other then root keygen.
        assertFailsWith<IllegalArgumentException> {
            parseParameters("--config-file", validConfigPath, "--trust-store-password", "testPassword")
        }

        // trust store password is optional.
        assertNull(parseParameters("--config-file", validConfigPath).trustStorePassword)
    }
}
