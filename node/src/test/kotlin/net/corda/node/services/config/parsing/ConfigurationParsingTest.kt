package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ConfigurationParsingTest {

    @Test
    fun serialize_deserialize_configuration() {

        var rawConfiguration = ConfigFactory.empty()

        rawConfiguration += "key" to "value"
        rawConfiguration += "key1.key2" to configOf("key3" to "value2", "key4" to configOf("key5" to -2.0, "key6" to false))
        rawConfiguration += "key7" to listOf("Hey!", true, 17, 0.0, configOf("key8" to listOf(-12.0, "HH", false), "key9" to "blah"))

        val serialized = rawConfiguration.serialize()
        println(serialized)

        val deserialized = ConfigFactory.parseString(serialized)

        assertThat(deserialized).isEqualTo(rawConfiguration)
    }

    @Test
    fun version_header_extraction_present() {

        val body = configOf("metadata" to configOf("version" to 2), "node" to configOf("p2pAddress" to "localhost:8080"))

        val rawConfiguration = configOf("configuration" to body)

        val version = readVersionHeader(rawConfiguration)
        assertThat(version).isEqualTo(2)
    }

    @Test
    fun version_header_extraction_no_metadata() {

        val body = configOf("node" to configOf("p2pAddress" to "localhost:8080"))

        val rawConfiguration = configOf("configuration" to body)

        val version = readVersionHeader(rawConfiguration)
        assertThat(version).isNull()
    }

    @Test
    fun version_header_extraction_no_key() {

        val body = configOf("metadata" to configOf(), "node" to configOf("p2pAddress" to "localhost:8080"))

        val rawConfiguration = configOf("configuration" to body)

        val version = readVersionHeader(rawConfiguration)

        assertThat(version).isNull()
    }

    @Test
    fun version_header_extraction_no_value() {

        val body = configOf("metadata" to configOf("version" to null), "node" to configOf("p2pAddress" to "localhost:8080"))

        val rawConfiguration = configOf("configuration" to body)

        val version = readVersionHeader(rawConfiguration)

        assertThat(version).isNull()
    }

    @Test
    fun version_header_extraction_no_configuration() {

        val rawConfiguration = configOf()

        val version = readVersionHeader(rawConfiguration)

        assertThat(version).isNull()
    }

    private fun readVersionHeader(configuration: Config): Int? {

        return when {
            configuration.hasPath("configuration.metadata.version") -> configuration.getInt("configuration.metadata.version")
            else -> null
        }
    }

    private fun readVersionHeader(configuration: ConfigObject): Int? = readVersionHeader(configuration.toConfig())
}