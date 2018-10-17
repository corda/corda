package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigValue

object Configuration {

    interface Describer {

        fun describe(configuration: Config): ConfigValue
    }

    object Value {

        interface Extractor<TYPE> {

            @Throws(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)
            fun valueIn(configuration: Config): TYPE

            fun isSpecifiedBy(configuration: Config): Boolean

            @Throws(ConfigException.WrongType::class, ConfigException.BadValue::class)
            fun valueInOrNull(configuration: Config): TYPE? {

                return when {
                    isSpecifiedBy(configuration) -> valueIn(configuration)
                    else -> null
                }
            }
        }
    }

    object Property {

        interface Metadata {

        }

        interface Definition : Metadata {

        }
    }

    interface Schema {

    }

    abstract class Specification {

    }

    object Validation {

        data class Options(val strict: Boolean)
    }
}