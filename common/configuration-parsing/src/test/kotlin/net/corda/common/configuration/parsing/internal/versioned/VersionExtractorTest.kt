package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.Valid
import net.corda.common.configuration.parsing.internal.configObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class VersionExtractorTest {

    private val versionExtractor = Configuration.Version.Extractor.fromPath("configuration.metadata.version")
    private val extractVersion: (Config) -> Valid<Int> = { config -> versionExtractor.parse(config) }

    @Test(timeout=300_000)
	fun version_header_extraction_present() {

        val versionValue = Configuration.Version.Extractor.DEFAULT_VERSION_VALUE + 1
        val rawConfiguration = configObject("configuration" to configObject("metadata" to configObject("version" to versionValue), "node" to configObject("p2pAddress" to "localhost:8080"))).toConfig()

        val version = extractVersion.invoke(rawConfiguration).value()
        assertThat(version).isEqualTo(versionValue)
    }

    @Test(timeout=300_000)
	fun version_header_extraction_no_metadata() {

        val rawConfiguration = configObject("configuration" to configObject("node" to configObject("p2pAddress" to "localhost:8080"))).toConfig()

        val version = extractVersion.invoke(rawConfiguration).value()
        assertThat(version).isEqualTo(Configuration.Version.Extractor.DEFAULT_VERSION_VALUE)
    }

    @Test(timeout=300_000)
	fun version_header_extraction_no_key() {

        val rawConfiguration = configObject("configuration" to configObject("metadata" to configObject(), "node" to configObject("p2pAddress" to "localhost:8080"))).toConfig()

        val version = extractVersion.invoke(rawConfiguration).value()

        assertThat(version).isEqualTo(Configuration.Version.Extractor.DEFAULT_VERSION_VALUE)
    }

    @Test(timeout=300_000)
	fun version_header_extraction_no_value() {

        val rawConfiguration = configObject("configuration" to configObject("metadata" to configObject("version" to null), "node" to configObject("p2pAddress" to "localhost:8080"))).toConfig()

        val version = extractVersion.invoke(rawConfiguration).value()

        assertThat(version).isEqualTo(Configuration.Version.Extractor.DEFAULT_VERSION_VALUE)
    }

    @Test(timeout=300_000)
	fun version_header_extraction_no_configuration() {

        val rawConfiguration = configObject().toConfig()

        val version = extractVersion.invoke(rawConfiguration).value()

        assertThat(version).isEqualTo(Configuration.Version.Extractor.DEFAULT_VERSION_VALUE)
    }
}