package net.corda.common.configuration.parsing.internal

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class UtilsTest {

    @Test(timeout=300_000)
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
}