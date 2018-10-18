package net.corda.common.configuration.parsing.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue
import net.corda.common.configuration.parsing.internal.versioned.VersionExtractor
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.valid
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

            fun parse(configuration: Config, options: Configuration.Validation.Options): Validated<VALUE, Validation.Error>
        }
    }

    object Property {

        interface Metadata {

            val key: String
            val typeName: String
            val mandatory: Boolean
            val sensitive: Boolean

            val schema: Schema?
        }

        interface Definition<TYPE> : Configuration.Property.Metadata, Configuration.Validator, Configuration.Value.Extractor<TYPE>, Configuration.Describer, Configuration.Value.Parser<TYPE> {

            override fun isSpecifiedBy(configuration: Config): Boolean = configuration.hasPath(key)

            interface Required<TYPE> : Definition<TYPE> {

                fun optional(defaultValue: TYPE? = null): Definition<TYPE?>
            }

            interface Single<TYPE> : Definition<TYPE> {

                fun list(): Required<List<TYPE>>
            }

            interface Standard<TYPE> : Required<TYPE>, Single<TYPE> {

                fun <MAPPED : Any> map(mappedTypeName: String, convert: (String, TYPE) -> Validated<MAPPED, Validation.Error>): Standard<MAPPED>
            }

            override fun parse(configuration: Config, options: Validation.Options): Validated<TYPE, Validation.Error> {

                return validate(configuration, options).flatMap { config -> valid<TYPE, Configuration.Validation.Error>(valueIn(config)) }
            }

            companion object {

                const val SENSITIVE_DATA_PLACEHOLDER = "*****"

                internal val expectedExceptionTypes: Set<KClass<*>> = setOf(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)

                fun long(key: String, sensitive: Boolean = false): Standard<Long> = LongProperty(key, sensitive)

                fun boolean(key: String, sensitive: Boolean = false): Standard<Boolean> = StandardProperty(key, Boolean::class.javaObjectType.simpleName, Config::getBoolean, Config::getBooleanList, sensitive)

                fun double(key: String, sensitive: Boolean = false): Standard<Double> = StandardProperty(key, Double::class.javaObjectType.simpleName, Config::getDouble, Config::getDoubleList, sensitive)

                fun string(key: String, sensitive: Boolean = false): Standard<String> = StandardProperty(key, String::class.java.simpleName, Config::getString, Config::getStringList, sensitive)

                fun duration(key: String, sensitive: Boolean = false): Standard<Duration> = StandardProperty(key, Duration::class.java.simpleName, Config::getDuration, Config::getDurationList, sensitive)

                fun nestedObject(key: String, schema: Schema? = null, sensitive: Boolean = false): Standard<ConfigObject> = StandardProperty(key, ConfigObject::class.java.simpleName, Config::getObject, Config::getObjectList, sensitive, schema)

                fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, sensitive: Boolean = false): Standard<ENUM> = StandardProperty(key, enumClass.java.simpleName, { conf: Config, propertyKey: String -> conf.getEnum(enumClass.java, propertyKey) }, { conf: Config, propertyKey: String -> conf.getEnumList(enumClass.java, propertyKey) }, sensitive)
            }
        }
    }

    interface Schema : Configuration.Validator, Configuration.Describer {

        val name: String?

        fun description(): String

        val properties: Set<Property.Definition<*>>

        companion object {

            fun withProperties(name: String? = null, properties: Iterable<Property.Definition<*>>): Schema = Schema(name, properties)

            fun withProperties(vararg properties: Property.Definition<*>, name: String? = null): Schema = withProperties(name, properties.toSet())

            fun withProperties(name: String? = null, builder: Property.Definition.Companion.() -> Iterable<Property.Definition<*>>): Schema = withProperties(name, builder.invoke(Property.Definition))
        }
    }

    abstract class Specification<VALUE>(name: String?) : Configuration.Schema, Configuration.Value.Parser<VALUE> {

        private val mutableProperties = mutableSetOf<Property.Definition<*>>()

        override val properties: Set<Property.Definition<*>> = mutableProperties

        private val schema: Schema by lazy { Schema(name, properties) }

        fun long(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Long> = PropertyDelegate.long(key, sensitive) { mutableProperties.add(it) }

        fun boolean(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Boolean> = PropertyDelegate.boolean(key, sensitive) { mutableProperties.add(it) }

        fun double(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Double> = PropertyDelegate.double(key, sensitive) { mutableProperties.add(it) }

        fun string(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<String> = PropertyDelegate.string(key, sensitive) { mutableProperties.add(it) }

        fun duration(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Duration> = PropertyDelegate.duration(key, sensitive) { mutableProperties.add(it) }

        fun nestedObject(schema: Schema? = null, key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ConfigObject> = PropertyDelegate.nestedObject(schema, key, sensitive) { mutableProperties.add(it) }

        fun <ENUM : Enum<ENUM>> enum(key: String? = null, enumClass: KClass<ENUM>, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = PropertyDelegate.enum(key, enumClass, sensitive) { mutableProperties.add(it) }

        override val name: String? get() = schema.name

        override fun description() = schema.description()

        override fun validate(target: Config, options: Validation.Options?) = schema.validate(target, options)

        override fun describe(configuration: Config) = schema.describe(configuration)

        final override fun parse(configuration: Config, options: Configuration.Validation.Options): Valid<VALUE> {

            return validate(configuration, options).flatMap(::parseValid)
        }

        protected abstract fun parseValid(configuration: Config): Valid<VALUE>
    }

    object Validation {

        data class Options(val strict: Boolean)

        sealed class Error constructor(val keyName: String, open val typeName: String? = null, open val message: String, val containingPath: List<String> = emptyList()) {

            val path: List<String> = containingPath + keyName

            val containingPathAsString: String = containingPath.joinToString(".")
            val pathAsString: String = path.joinToString(".")

            abstract fun withContainingPath(vararg containingPath: String): Error

            override fun toString(): String {

                return "(keyName='$keyName', typeName='$typeName', path=$path, message='$message')"
            }

            class WrongType private constructor(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(keyName: String, message: String, typeName: String, containingPath: List<String> = emptyList()): WrongType {

                        val keyParts = keyName.split(".")
                        return if (keyParts.size > 1) {
                            val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                            val keySegment = keyParts.last()
                            return WrongType(keySegment, typeName, message, fullContainingPath)
                        } else {
                            WrongType(keyName, typeName, message, containingPath)
                        }
                    }
                }

                override fun withContainingPath(vararg containingPath: String) = WrongType(keyName, typeName, message, containingPath.toList() + this.containingPath)
            }

            class MissingValue private constructor(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(keyName: String, typeName: String, message: String, containingPath: List<String> = emptyList()): MissingValue {

                        val keyParts = keyName.split(".")
                        return if (keyParts.size > 1) {
                            val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                            val keySegment = keyParts.last()
                            return MissingValue(keySegment, typeName, message, fullContainingPath)
                        } else {
                            MissingValue(keyName, typeName, message, containingPath)
                        }
                    }
                }

                override fun withContainingPath(vararg containingPath: String) = MissingValue(keyName, typeName, message, containingPath.toList() + this.containingPath)
            }

            class BadValue private constructor(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(keyName: String, typeName: String, message: String, containingPath: List<String> = emptyList()): BadValue {

                        val keyParts = keyName.split(".")
                        return if (keyParts.size > 1) {
                            val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                            val keySegment = keyParts.last()
                            return BadValue(keySegment, typeName, message, fullContainingPath)
                        } else {
                            BadValue(keyName, typeName, message, containingPath)
                        }
                    }
                }

                override fun withContainingPath(vararg containingPath: String) = BadValue(keyName, typeName, message, containingPath.toList() + this.containingPath)
            }

            class Unknown private constructor(keyName: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, null, message(keyName), containingPath) {

                internal companion object {

                    private fun message(keyName: String) = "Unknown property \"$keyName\"."

                    internal fun of(keyName: String, containingPath: List<String> = emptyList()): Unknown {

                        val keyParts = keyName.split(".")
                        return when {
                            keyParts.size > 1 -> {
                                val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                                val keySegment = keyParts.last()
                                return Unknown(keySegment, fullContainingPath)
                            }
                            else -> Unknown(keyName, containingPath)
                        }
                    }
                }

                override val message = message(pathAsString)

                override fun withContainingPath(vararg containingPath: String) = Unknown(keyName, containingPath.toList() + this.containingPath)
            }
        }
    }

    object Version {

        interface Extractor : Configuration.Value.Parser<Int?> {

            companion object {

                const val DEFAULT_VERSION_VALUE = 1

                fun fromKey(versionKey: String, versionDefaultValue: Int? = DEFAULT_VERSION_VALUE): Configuration.Version.Extractor = VersionExtractor(versionKey, versionDefaultValue)
            }
        }
    }

    interface Validator : net.corda.common.validation.internal.Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options>
}