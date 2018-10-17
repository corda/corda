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

        interface Parser<VALUE> {

            fun parse(configuration: Config, strict: Boolean): Validated<VALUE, Configuration.Validation.Error>
        }
    }

    object Property {

        interface Metadata {

            val key: String
            val typeName: String
            val mandatory: Boolean
            val sensitive: Boolean

            val schema: ConfigSchema?
        }

        interface Definition : Metadata {

        }
    }

    interface Schema {

    }

    abstract class Specification {

    }

    interface Validator : net.corda.node.services.config.parsing.Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options>

    object Validation {

        data class Options(val strict: Boolean)

        sealed class Error constructor(val keyName: String, open val typeName: String? = null, open val message: String, val containingPath: List<String> = emptyList()) {

            val path: List<String> = containingPath + keyName

            val containingPathAsString: String = containingPath.joinToString(".")
            val pathAsString: String = path.joinToString(".")

            abstract fun withContainingPath(vararg containingPath: String): Configuration.Validation.Error

            override fun toString(): String {

                return "(keyName='$keyName', typeName='$typeName', path=$path, message='$message')"
            }

            class WrongType private constructor(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(keyName: String, message: String, typeName: String, containingPath: List<String> = emptyList()): Configuration.Validation.Error.WrongType {

                        val keyParts = keyName.split(".")
                        return if (keyParts.size > 1) {
                            val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                            val keySegment = keyParts.last()
                            return Configuration.Validation.Error.WrongType(keySegment, typeName, message, fullContainingPath)
                        } else {
                            Configuration.Validation.Error.WrongType(keyName, typeName, message, containingPath)
                        }
                    }
                }

                override fun withContainingPath(vararg containingPath: String) = Configuration.Validation.Error.WrongType(keyName, typeName, message, containingPath.toList() + this.containingPath)
            }

            class MissingValue private constructor(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(keyName: String, typeName: String, message: String, containingPath: List<String> = emptyList()): Configuration.Validation.Error.MissingValue {

                        val keyParts = keyName.split(".")
                        return if (keyParts.size > 1) {
                            val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                            val keySegment = keyParts.last()
                            return Configuration.Validation.Error.MissingValue(keySegment, typeName, message, fullContainingPath)
                        } else {
                            Configuration.Validation.Error.MissingValue(keyName, typeName, message, containingPath)
                        }
                    }
                }

                override fun withContainingPath(vararg containingPath: String) = Configuration.Validation.Error.MissingValue(keyName, typeName, message, containingPath.toList() + this.containingPath)
            }

            class BadValue private constructor(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(keyName: String, typeName: String, message: String, containingPath: List<String> = emptyList()): Configuration.Validation.Error.BadValue {

                        val keyParts = keyName.split(".")
                        return if (keyParts.size > 1) {
                            val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                            val keySegment = keyParts.last()
                            return Configuration.Validation.Error.BadValue(keySegment, typeName, message, fullContainingPath)
                        } else {
                            Configuration.Validation.Error.BadValue(keyName, typeName, message, containingPath)
                        }
                    }
                }

                override fun withContainingPath(vararg containingPath: String) = Configuration.Validation.Error.BadValue(keyName, typeName, message, containingPath.toList() + this.containingPath)
            }

            class Unknown private constructor(keyName: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, null, message(keyName), containingPath) {

                internal companion object {

                    private fun message(keyName: String) = "Unknown property \"$keyName\"."

                    internal fun of(keyName: String, containingPath: List<String> = emptyList()): Configuration.Validation.Error.Unknown {

                        val keyParts = keyName.split(".")
                        return when {
                            keyParts.size > 1 -> {
                                val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                                val keySegment = keyParts.last()
                                return Configuration.Validation.Error.Unknown(keySegment, fullContainingPath)
                            }
                            else -> Configuration.Validation.Error.Unknown(keyName, containingPath)
                        }
                    }
                }

                override val message = message(pathAsString)

                override fun withContainingPath(vararg containingPath: String) = Configuration.Validation.Error.Unknown(keyName, containingPath.toList() + this.containingPath)
            }
        }
    }
}