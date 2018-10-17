package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigValue

object Configuration {

    interface Describer {

        fun describe(configuration: Config): ConfigValue
    }

    interface ValueExtractor {

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