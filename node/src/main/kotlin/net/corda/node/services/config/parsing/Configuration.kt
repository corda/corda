package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue
import net.corda.node.services.config.parsing.common.validation.Validated
import java.time.Duration
import kotlin.reflect.KClass

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

            fun parse(configuration: Config, strict: Boolean): Validated<VALUE, Validation.Error>
        }
    }

    object Property {

        interface Metadata {

            val key: String
            val typeName: String
            val mandatory: Boolean
            val sensitive: Boolean

            val schema: Configuration.Schema?
        }

        interface Definition<TYPE> : Configuration.Property.Metadata, Configuration.Validator, Configuration.Value.Extractor<TYPE>, Configuration.Describer {

            override fun isSpecifiedBy(configuration: Config): Boolean = configuration.hasPath(key)

            interface Required<TYPE> : Configuration.Property.Definition<TYPE> {

                fun optional(defaultValue: TYPE? = null): Configuration.Property.Definition<TYPE?>
            }

            interface Single<TYPE> : Configuration.Property.Definition<TYPE> {

                fun list(): Configuration.Property.Definition.Required<List<TYPE>>
            }

            interface Standard<TYPE> : Configuration.Property.Definition.Required<TYPE>, Configuration.Property.Definition.Single<TYPE> {

                fun <MAPPED : Any> map(mappedTypeName: String, convert: (String, TYPE) -> Validated<MAPPED, Validation.Error>): Configuration.Property.Definition.Standard<MAPPED>
            }

            companion object {

                const val SENSITIVE_DATA_PLACEHOLDER = "*****"

                internal val expectedExceptionTypes: Set<KClass<*>> = setOf(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)

                fun long(key: String, sensitive: Boolean = false): Configuration.Property.Definition.Standard<Long> = LongProperty(key, sensitive)

                fun boolean(key: String, sensitive: Boolean = false): Configuration.Property.Definition.Standard<Boolean> = StandardProperty(key, Boolean::class.javaObjectType.simpleName, Config::getBoolean, Config::getBooleanList, sensitive)

                fun double(key: String, sensitive: Boolean = false): Configuration.Property.Definition.Standard<Double> = StandardProperty(key, Double::class.javaObjectType.simpleName, Config::getDouble, Config::getDoubleList, sensitive)

                fun string(key: String, sensitive: Boolean = false): Configuration.Property.Definition.Standard<String> = StandardProperty(key, String::class.java.simpleName, Config::getString, Config::getStringList, sensitive)

                fun duration(key: String, sensitive: Boolean = false): Configuration.Property.Definition.Standard<Duration> = StandardProperty(key, Duration::class.java.simpleName, Config::getDuration, Config::getDurationList, sensitive)

                fun nestedObject(key: String, schema: Configuration.Schema? = null, sensitive: Boolean = false): Configuration.Property.Definition.Standard<ConfigObject> = StandardProperty(key, ConfigObject::class.java.simpleName, Config::getObject, Config::getObjectList, sensitive, schema)

                fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, sensitive: Boolean = false): Configuration.Property.Definition.Standard<ENUM> = StandardProperty(key, enumClass.java.simpleName, { conf: Config, propertyKey: String -> conf.getEnum(enumClass.java, propertyKey) }, { conf: Config, propertyKey: String -> conf.getEnumList(enumClass.java, propertyKey) }, sensitive)
            }
        }
    }

    interface Schema : Configuration.Validator, Configuration.Describer {

        val name: String?

        fun description(): String

        val properties: Set<Configuration.Property.Definition<*>>

        companion object {

            fun withProperties(name: String? = null, properties: Iterable<Configuration.Property.Definition<*>>): Configuration.Schema = Schema(name, properties)

            fun withProperties(vararg properties: Configuration.Property.Definition<*>, name: String? = null): Configuration.Schema = withProperties(name, properties.toSet())

            fun withProperties(name: String? = null, builder: Configuration.Property.Definition.Companion.() -> Iterable<Configuration.Property.Definition<*>>): Configuration.Schema = withProperties(name, builder.invoke(Configuration.Property.Definition.Companion))
        }
    }

    abstract class Specification<VALUE>(name: String?) : Configuration.Schema, Configuration.Value.Parser<VALUE> {

        private val mutableProperties = mutableSetOf<Configuration.Property.Definition<*>>()

        override val properties: Set<Configuration.Property.Definition<*>> = mutableProperties

        private val schema: Configuration.Schema by lazy { Schema(name, properties) }

        fun long(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Long> = PropertyDelegate.long(key, sensitive) { mutableProperties.add(it) }

        fun boolean(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Boolean> = PropertyDelegate.boolean(key, sensitive) { mutableProperties.add(it) }

        fun double(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Double> = PropertyDelegate.double(key, sensitive) { mutableProperties.add(it) }

        fun string(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<String> = PropertyDelegate.string(key, sensitive) { mutableProperties.add(it) }

        fun duration(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Duration> = PropertyDelegate.duration(key, sensitive) { mutableProperties.add(it) }

        fun nestedObject(schema: Configuration.Schema? = null, key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ConfigObject> = PropertyDelegate.nestedObject(schema, key, sensitive) { mutableProperties.add(it) }

        fun <ENUM : Enum<ENUM>> enum(key: String? = null, enumClass: KClass<ENUM>, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = PropertyDelegate.enum(key, enumClass, sensitive) { mutableProperties.add(it) }

        override val name: String? get() = schema.name

        override fun description() = schema.description()

        override fun validate(target: Config, options: Configuration.Validation.Options?) = schema.validate(target, options)

        override fun describe(configuration: Config) = schema.describe(configuration)
    }

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

    interface Validator : net.corda.node.services.config.parsing.common.validation.Validator<Config, Validation.Error, Validation.Options>
}