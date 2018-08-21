package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject

interface ConfigVersionExtractor : (Config) -> Int? {

    fun invoke(configuration: ConfigObject): Int? = invoke(configuration.toConfig())
}

class KeyedConfigVersionExtractor(private val versionKey: String) : ConfigVersionExtractor {

    override fun invoke(configuration: Config): Int? {

        return when {
            configuration.hasPath(versionKey) -> configuration.getInt(versionKey)
            else -> null
        }
    }
}