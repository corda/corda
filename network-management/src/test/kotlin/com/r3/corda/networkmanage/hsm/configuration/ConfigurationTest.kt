package com.r3.corda.networkmanage.hsm.configuration

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import com.typesafe.config.ConfigException
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigurationTest : TestBase() {
    private val validConfigPath = File(javaClass.getResource("/hsm.conf").toURI()).absolutePath
    private val invalidConfigPath = File(javaClass.getResource("/hsm_fail.conf").toURI()).absolutePath

    @Test
    fun `config file is parsed correctly`() {
        val paramsWithPassword = parseParameters("--config-file", validConfigPath)
        assertEquals(AuthMode.PASSWORD, paramsWithPassword.authMode)
        assertEquals("3001@192.168.0.1", paramsWithPassword.device)
    }

    @Test
    fun `should fail when config missing database source properties`() {
        // dataSourceProperties is missing from node_fail.conf and it should fail during parsing, and shouldn't use default from reference.conf.
        assertFailsWith<ConfigException.Missing> {
            parseParameters("--config-file", invalidConfigPath)
        }
    }

    @Test
    fun `should fail when config file is missing`() {
        val message = assertFailsWith<IllegalArgumentException> {
            com.r3.corda.networkmanage.doorman.parseParameters("--config-file", "not-existing-file")
        }.message
        Assertions.assertThat(message).contains("Config file ")
    }
}