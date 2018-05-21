package net.corda.testing.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.node.internal.cordapp.CordappConfigProvider

class MockCordappConfigProvider : CordappConfigProvider {
    val cordappConfigs = mutableMapOf<String, Config>()

    override fun getConfigByName(name: String): Config {
        return if (cordappConfigs.containsKey(name)) {
            cordappConfigs[name]!!
        } else {
            ConfigFactory.empty()
        }
    }
}