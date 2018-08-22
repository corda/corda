package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory

@Suppress("UNCHECKED_CAST")
internal fun configObject(vararg entries: Pair<String, Any?>): ConfigObject {

    var configuration = ConfigFactory.empty()
    entries.forEach { entry ->
        val value = entry.second
        configuration += if (value is Pair<*, *> && value.first is String) {
            (entry.first to (ConfigFactory.empty() + value as Pair<String, Any?>).root())
        } else {
            entry
        }
    }
    return configuration.root()
}

internal operator fun Config.plus(entry: Pair<String, Any?>): Config {

    var value = entry.second ?: return this - entry.first
    if (value is Config) {
        value = value.root()
    }
    return withValue(entry.first, ConfigValueFactory.fromAnyRef(value))
}

internal operator fun Config.minus(key: String): Config {

    return withoutPath(key)
}

internal fun Config.serialize(options: ConfigRenderOptions = ConfigRenderOptions.concise().setFormatted(true).setJson(true)): String = root().render(options)