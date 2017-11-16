package net.corda.core.internal.cordapp

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappConfigException

class TypesafeCordappConfig(private val cordappConfig: Config) : CordappConfig {
    override fun <T> get(path: String): T? {
        try {
            val value = cordappConfig.getAnyRef(path)
            return if (value != null) {
                value as T
            } else {
                null
            }
        } catch (e: ConfigException) {
            throw CordappConfigException("Cordapp configuration is incorrect due to exception", e)
        }
    }
}