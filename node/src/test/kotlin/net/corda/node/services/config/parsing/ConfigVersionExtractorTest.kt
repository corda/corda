package net.corda.node.services.config.parsing

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ConfigVersionExtractorTest {

    private val extractVersion: ConfigVersionExtractor = KeyedConfigVersionExtractor("configuration.metadata.version")

    @Test
    fun serialize_deserialize_configuration() {

        var rawConfiguration = ConfigFactory.empty()

        rawConfiguration += "key" to "value"
        rawConfiguration += "key1.key2" to configObject("key3" to "value2", "key4" to configObject("key5" to -2.0, "key6" to false))
        rawConfiguration += "key7" to listOf("Hey!", true, 17, 0.0, configObject("key8" to listOf(-12.0, "HH", false), "key9" to "blah"))

        val serialized = rawConfiguration.serialize()
        println(serialized)

        val deserialized = ConfigFactory.parseString(serialized)

        assertThat(deserialized).isEqualTo(rawConfiguration)
    }

    @Test
    fun version_header_extraction_present() {

        val rawConfiguration = configObject("configuration" to configObject("metadata" to configObject("version" to 2), "node" to configObject("p2pAddress" to "localhost:8080")))

        val version = extractVersion.invoke(rawConfiguration)
        assertThat(version).isEqualTo(2)
    }

    @Test
    fun version_header_extraction_no_metadata() {

        val rawConfiguration = configObject("configuration" to configObject("node" to configObject("p2pAddress" to "localhost:8080")))

        val version = extractVersion.invoke(rawConfiguration)
        assertThat(version).isNull()
    }

    @Test
    fun version_header_extraction_no_key() {

        val rawConfiguration = configObject("configuration" to configObject("metadata" to configObject(), "node" to configObject("p2pAddress" to "localhost:8080")))

        val version = extractVersion.invoke(rawConfiguration)

        assertThat(version).isNull()
    }

    @Test
    fun version_header_extraction_no_value() {

        val rawConfiguration = configObject("configuration" to configObject("metadata" to configObject("version" to null), "node" to configObject("p2pAddress" to "localhost:8080")))

        val version = extractVersion.invoke(rawConfiguration)

        assertThat(version).isNull()
    }

    @Test
    fun version_header_extraction_no_configuration() {

        val rawConfiguration = configObject()

        val version = extractVersion.invoke(rawConfiguration)

        assertThat(version).isNull()
    }
}