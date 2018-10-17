package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.configObject
import net.corda.common.validation.internal.Validated
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class VersionExtractorTest {

    private val versionExtractor = VersionExtractor("configuration.metadata.version")
    private val extractVersion: (Config) -> Validated<Int, Configuration.Validation.Error> = { config -> versionExtractor.parse(config, Configuration.Validation.Options(strict = false)) }

    @Test
    fun version_header_extraction_present() {

        val versionValue = versionExtractor.versionDefaultValue + 1
        val rawConfiguration = configObject("configuration" to configObject("metadata" to configObject("version" to versionValue), "node" to configObject("p2pAddress" to "localhost:8080"))).toConfig()

        val version = extractVersion.invoke(rawConfiguration).valueOrThrow()
        assertThat(version).isEqualTo(versionValue)
    }

    @Test
    fun version_header_extraction_no_metadata() {

        val rawConfiguration = configObject("configuration" to configObject("node" to configObject("p2pAddress" to "localhost:8080"))).toConfig()

        val version = extractVersion.invoke(rawConfiguration).valueOrThrow()
        assertThat(version).isEqualTo(versionExtractor.versionDefaultValue)
    }

    @Test
    fun version_header_extraction_no_key() {

        val rawConfiguration = configObject("configuration" to configObject("metadata" to configObject(), "node" to configObject("p2pAddress" to "localhost:8080"))).toConfig()

        val version = extractVersion.invoke(rawConfiguration).valueOrThrow()

        assertThat(version).isEqualTo(versionExtractor.versionDefaultValue)
    }

    @Test
    fun version_header_extraction_no_value() {

        val rawConfiguration = configObject("configuration" to configObject("metadata" to configObject("version" to null), "node" to configObject("p2pAddress" to "localhost:8080"))).toConfig()

        val version = extractVersion.invoke(rawConfiguration).valueOrThrow()

        assertThat(version).isEqualTo(versionExtractor.versionDefaultValue)
    }

    @Test
    fun version_header_extraction_no_configuration() {

        val rawConfiguration = configObject().toConfig()

        val version = extractVersion.invoke(rawConfiguration).valueOrThrow()

        assertThat(version).isEqualTo(versionExtractor.versionDefaultValue)
    }
}