/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.configuration

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.utils.parseConfig
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals

class SigningServiceArgsParserTest : TestBase() {
    private val doormanConfigPath = File("./hsm-for-doorman.conf").absolutePath
    private val networkMapConfigPath = File("./hsm-for-networkmap.conf").absolutePath
    private val argsParser = SigningServiceArgsParser()

    @Test
    fun `doorman-based config file is parsed correctly`() {
        val cmdLineOptions = argsParser.parseOrExit("--config-file", doormanConfigPath)
        val config = parseConfig<SigningServiceConfig>(cmdLineOptions.configFile)
        assertEquals("3001@192.168.0.1", config.device)
        val doormanCertParameters = config.doorman!!
        assertEquals(AuthMode.PASSWORD, doormanCertParameters.authParameters.mode)
        assertEquals(2, doormanCertParameters.authParameters.threshold)
        assertEquals(3650, doormanCertParameters.validDays)
    }

    @Test
    fun `networkmap-based config file is parsed correctly`() {
        val cmdLineOptions = argsParser.parseOrExit("--config-file", networkMapConfigPath)
        val config = parseConfig<SigningServiceConfig>(cmdLineOptions.configFile)
        assertEquals("3001@192.168.0.1", config.device)
        val networkMapConfig = config.networkMap!!
        assertEquals(AuthMode.KEY_FILE, networkMapConfig.authParameters.mode)
        assertEquals(Paths.get("./Administrator.KEY"), networkMapConfig.authParameters.keyFilePath)
        assertEquals(2, networkMapConfig.authParameters.threshold)
        assertEquals("PASSWORD", networkMapConfig.authParameters.password)
        assertEquals("TEST_USERNAME", networkMapConfig.username)
    }

    @Test
    fun `should fail when config file is missing`() {
        assertThatThrownBy {
            argsParser.parseOrExit("--config-file", "not-existing-file", printHelpOn = null)
        }.hasMessageContaining("not-existing-file")
    }
}
