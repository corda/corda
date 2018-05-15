/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.generator

import com.r3.corda.networkmanage.common.configuration.ConfigFilePathArgsParser
import com.typesafe.config.ConfigException
import joptsimple.OptionException
import net.corda.nodeapi.internal.crypto.CertificateType
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GeneratorParametersTest {
    private val validConfigPath = File("generator.conf").absolutePath
    private val invalidConfigPath = File(javaClass.getResource("/generator_fail.conf").toURI()).absolutePath
    private val validArgs = arrayOf("--config-file", validConfigPath)

    @Test
    fun `should fail when config file is missing`() {
        val message = assertFailsWith<OptionException> {
            ConfigFilePathArgsParser().parseOrExit("--config-file", "not-existing-file", printHelpOn = null)
        }.message
        Assertions.assertThat(message).contains("not-existing-file")
    }

    @Test
    fun `should fail when config is invalid`() {
        assertFailsWith<ConfigException.Missing> {
            parseParameters(ConfigFilePathArgsParser().parseOrExit("--config-file", invalidConfigPath))
        }
    }

    @Test
    fun `should parse generator config correctly`() {
        val parameters = parseCommandLineAndGetParameters()
        assertEquals("127.0.0.1", parameters.hsmHost)
        assertEquals(3001, parameters.hsmPort)
        val certConfig = parameters.certConfig
        assertEquals(1, certConfig.keySpecifier)
        assertEquals("trustpass", parameters.trustStorePassword)
        assertEquals(Paths.get("."), parameters.trustStoreDirectory)
        assertFalse(certConfig.storeKeysExternal)
        assertFalse(parameters.userConfigs.isEmpty())
        val userConfig = parameters.userConfigs.first()
        assertEquals("INTEGRATION_TEST", userConfig.username)
        assertEquals(AuthMode.PASSWORD, userConfig.authMode)
        assertEquals("INTEGRATION_TEST", userConfig.authToken)
        assertEquals(3650, certConfig.validDays)
        assertEquals(CertificateType.ROOT_CA, certConfig.certificateType)
        assertEquals("NIST-P256", certConfig.keyCurve)
    }

    private fun parseCommandLineAndGetParameters(): GeneratorParameters {
        return parseParameters(ConfigFilePathArgsParser().parseOrExit(*validArgs))
    }
}