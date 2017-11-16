package net.corda.core.internal.cordapp

import com.typesafe.config.Config
import net.corda.core.cordapp.CordappConfig

class TypesafeCordappConfig(private val cordappConfig: Config) : CordappConfig {
    override fun <T> get(path: String): T? {
        val value = cordappConfig.getAnyRef(path)
        return if (value != null) {
            value as T
        } else {
            null
        }
    }
}