package net.corda.node.internal.cordapp

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.writeText

class CordappConfigFileProviderTests {
    private companion object {
        val cordappDir = Paths.get("build") / "tmp" / "cordapps"
        const val cordappName = "test"
        val cordappConfFile = cordappDir / "config" / "$cordappName.conf"

        val validConfig: Config = ConfigFactory.parseString("key=value")
        val alternateValidConfig: Config = ConfigFactory.parseString("key=alternateValue")
        const val invalidConfig = "Invalid"
    }

    private val provider = CordappConfigFileProvider(listOf(cordappDir))

    @Test(timeout=300_000)
	fun `test that config can be loaded`() {
        writeConfig(validConfig)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(validConfig)
    }

    @Test(timeout=300_000)
	fun `config is idempotent if the underlying file is not changed`() {
        writeConfig(validConfig)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(validConfig)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(validConfig)
    }

    @Test(timeout=300_000)
	fun `config is not idempotent if the underlying file is changed`() {
        writeConfig(validConfig)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(validConfig)

        writeConfig(alternateValidConfig)
        assertThat(provider.getConfigByName(cordappName)).isEqualTo(alternateValidConfig)
    }

    @Test(timeout=300_000)
    fun `an invalid config throws an exception`() {
        cordappConfFile.writeText(invalidConfig)
        assertThatExceptionOfType(ConfigException::class.java).isThrownBy {
            provider.getConfigByName(cordappName)
        }
    }

    /**
     * Writes the config to the path provided - will (and must) overwrite any existing config
     */
    private fun writeConfig(config: Config) = cordappConfFile.writeText(config.root().render(ConfigRenderOptions.concise()))
}