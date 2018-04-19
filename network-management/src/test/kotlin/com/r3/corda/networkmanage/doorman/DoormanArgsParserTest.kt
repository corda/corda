/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman

import joptsimple.OptionException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DoormanArgsParserTest {
    private val validConfigPath = File("doorman.conf").absolutePath
    private val argsParser = DoormanArgsParser()

    @Test
    fun `should fail when network parameters file is missing`() {
        assertThatThrownBy {
            argsParser.parseOrExit("--config-file", validConfigPath, "--set-network-parameters", "not-here")
        }.hasMessageContaining("not-here")
    }

    @Test
    fun `should fail when config file is missing`() {
        assertThatThrownBy {
            argsParser.parseOrExit("--config-file", "not-existing-file")
        }.hasMessageContaining("not-existing-file")
    }

    @Test
    fun `should parse trust store password correctly`() {
        val parameter = argsParser.parseOrExit("--config-file", validConfigPath, "--mode", "ROOT_KEYGEN", "--trust-store-password", "testPassword")
        assertEquals("testPassword", parameter.trustStorePassword)

        assertFailsWith<OptionException> {
            argsParser.parseOrExit("--trust-store-password", printHelpOn = null)
        }

        // Should fail if password is provided in mode other then root keygen.
        assertFailsWith<IllegalArgumentException> {
            argsParser.parseOrExit("--config-file", validConfigPath, "--trust-store-password", "testPassword")
        }

        // trust store password is optional.
        assertNull(argsParser.parseOrExit("--config-file", validConfigPath).trustStorePassword)
    }
}
