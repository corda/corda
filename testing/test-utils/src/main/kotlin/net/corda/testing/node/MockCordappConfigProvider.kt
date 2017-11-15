package net.corda.testing.node

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.cordapp.CordappConfigProvider

class MockCordappConfigProvider : CordappConfigProvider {
    private val cordappConfigs = mutableMapOf<String, Config> ()

    override fun getConfigByName(name: String): Config {
        return if(cordappConfigs.containsKey(name)) {
             cordappConfigs[name]!!
        } else {
            ConfigFactory.empty()
        }
    }
}