package com.r3.corda.networkmanage.hsm.configuration

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import com.typesafe.config.ConfigException
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigurationTest : TestBase() {
    private val validConfigPath = File(javaClass.getResource("/hsm.conf").toURI()).absolutePath
    private val invalidConfigPath = File(javaClass.getResource("/hsm_fail.conf").toURI()).absolutePath

    @Test
    fun `authMode is parsed correctly`() {
        val paramsWithPassword = parseParameters("--configFile", validConfigPath, "--authMode", AuthMode.CARD_READER.name)
        assertEquals(AuthMode.CARD_READER, paramsWithPassword.authMode)
        val paramsWithCardReader = parseParameters("--configFile", validConfigPath, "--authMode", AuthMode.PASSWORD.name)
        assertEquals(AuthMode.PASSWORD, paramsWithCardReader.authMode)
    }

    @Test
    fun `validDays duration is parsed correctly`() {
        val expectedDuration = 360
        val paramsWithPassword = parseParameters("--configFile", validConfigPath, "--validDays", expectedDuration.toString())
        assertEquals(expectedDuration, paramsWithPassword.validDays)
    }

    @Test
    fun `should fail when config missing database source properties`() {
        // dataSourceProperties is missing from node_fail.conf and it should fail during parsing, and shouldn't use default from reference.conf.
        assertFailsWith<ConfigException.Missing> {
            parseParameters("--configFile", invalidConfigPath)
        }
    }
}