package net.corda.node.services.config.parsing

import com.typesafe.config.ConfigFactory
import org.junit.Test

class ConfigurationParsingTest {

    @Test
    fun showcase() {

        var rawConfiguration = ConfigFactory.empty()

        rawConfiguration += "key" to "value"
        rawConfiguration += "key1.key2" to configOf("key3" to "value2", "key4" to configOf("key5" to "value5", "key6" to "value6"))

        println(rawConfiguration.serialized())
    }
}