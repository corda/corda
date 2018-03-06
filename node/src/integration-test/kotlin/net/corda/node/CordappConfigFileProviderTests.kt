/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class CordappConfigFileProviderTests {
    private companion object {
        val cordappConfDir = File("build/tmp/cordapps/config")
        val cordappName = "test"
        val cordappConfFile = File(cordappConfDir, cordappName + ".conf").toPath()

        val validConfig = ConfigFactory.parseString("key=value")
        val alternateValidConfig = ConfigFactory.parseString("key=alternateValue")
        val invalidConfig = "Invalid"
    }

    val provider = CordappConfigFileProvider(cordappConfDir)

    @Test
    fun `test that config can be loaded`() {
        writeConfig(validConfig, cordappConfFile)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(validConfig)
    }

    @Test
    fun `config is idempotent if the underlying file is not changed`() {
        writeConfig(validConfig, cordappConfFile)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(validConfig)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(validConfig)
    }

    @Test
    fun `config is not idempotent if the underlying file is changed`() {
        writeConfig(validConfig, cordappConfFile)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(validConfig)

        writeConfig(alternateValidConfig, cordappConfFile)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(alternateValidConfig)
    }

    @Test(expected = ConfigException.Parse::class)
    fun `an invalid config throws an exception`() {
        Files.write(cordappConfFile, invalidConfig.toByteArray())

        provider.getConfigByName(cordappName)
    }

    /**
     * Writes the config to the path provided - will (and must) overwrite any existing config
     */
    private fun writeConfig(config: Config, to: Path) = Files.write(cordappConfFile, config.root().render(ConfigRenderOptions.concise()).toByteArray())
}