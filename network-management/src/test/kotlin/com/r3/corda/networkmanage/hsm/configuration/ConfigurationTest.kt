package com.r3.corda.networkmanage.hsm.configuration

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import com.typesafe.config.ConfigException
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigurationTest : TestBase() {
    private val validConfigPath = File("./hsm.conf").absolutePath
    private val invalidConfigPath = File(javaClass.getResource("/hsm_fail.conf").toURI()).absolutePath

    @Test
    fun `config file is parsed correctly`() {
        val parameters = parseParameters("--config-file", validConfigPath)
        assertEquals("3001@192.168.0.1", parameters.device)
        val doormanCertParameters = parameters.doorman!!
        assertEquals(AuthMode.PASSWORD, doormanCertParameters.authParameters.mode)
        assertEquals(2, doormanCertParameters.authParameters.threshold)
        assertEquals(3650, doormanCertParameters.validDays)
        val nmParams = parameters.networkMap!!
        assertEquals(AuthMode.KEY_FILE, nmParams.authParameters.mode)
        assertEquals(Paths.get("./Administrator.KEY"), nmParams.authParameters.keyFilePath)
        assertEquals(2, nmParams.authParameters.threshold)
        assertEquals("PASSWORD", nmParams.authParameters.password)
        assertEquals("TEST_USERNAME", nmParams.username)
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