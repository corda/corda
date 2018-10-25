package net.corda.common.configuration.parsing.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue
import net.corda.common.configuration.parsing.internal.versioned.VersionExtractor
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import java.time.Duration
import kotlin.reflect.KClass

/**
 * Entry point for the [Config] parsing utilities.
 */
object Configuration {

    /**
     * Able to describe a part of a [Config] as a [ConfigValue].
     * Implemented by [Configuration.Specification], [Configuration.Schema] and [Configuration.Property.Definition] to output values that are masked if declared as sensitive.
     */
    interface Describer {

        /**
         * Describes a [Config] hiding sensitive data.
         */
        fun describe(configuration: Config): ConfigValue
    }

    object Value {

        /**
         * Defines functions able to extract values from a [Config] in a type-safe fashion.
         */
        interface Extractor<TYPE> {

            /**
             * Returns a value out of a [Config] if all is good. Otherwise, it throws an exception.
             *
             * @throws ConfigException.Missing if the [Config] does not specify the value.
             * @throws ConfigException.WrongType if the [Config] specifies a value of the wrong type.
             * @throws ConfigException.BadValue if the [Config] specifies a value of the correct type, but this in unacceptable according to application-level validation rules..
             */
            @Throws(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)
            fun valueIn(configuration: Config): TYPE

            /**
             * Returns whether the value is specified by the [Config].
             */
            fun isSpecifiedBy(configuration: Config): Boolean

            /**
             * Returns a value out of a [Config] if all is good, or null if no value is present. Otherwise, it throws an exception.
             *
             * @throws ConfigException.WrongType if the [Config] specifies a value of the wrong type.
             * @throws ConfigException.BadValue if the [Config] specifies a value of the correct type, but this in unacceptable according to application-level validation rules..
             */
            @Throws(ConfigException.WrongType::class, ConfigException.BadValue::class)
            fun valueInOrNull(configuration: Config): TYPE? {

                return when {
                    isSpecifiedBy(configuration) -> valueIn(configuration)
                    else -> null
                }
            }
        }

        /**
         * Able to parse a value from a [Config] and [Configuration.Validation.Options], returning a [Valid] result containing either the value itself, or some [Configuration.Validation.Error]s.
         */
        interface Parser<VALUE> {

            /**
             * Returns a [Valid] wrapper either around a valid value extracted from the [Config], or around a set of [Configuration.Validation.Error] with details about what went wrong.
             */
            fun parse(configuration: Config, options: Configuration.Validation.Options = Configuration.Validation.Options.defaults): Valid<VALUE>
        }
    }

    object Property {

        /**
         * Configuration property metadata, as in the set of qualifying traits for a [Configuration.Property.Definition].
         */
        interface Metadata {

            /**
             * Property key.
             */
            val key: String

            /**
             * Name of the type for this property..
             */
            val typeName: String

            /**
             * Whether the absence of a value for this property will raise an error.
             */
            val isMandatory: Boolean

            /**
             * Whether the value for this property will be shown by [Configuration.Property.Definition.describe].
             */
            val isSensitive: Boolean

            val schema: Schema?
        }

        /**
         * Property definition, able to validate, describe and extract values from a [Config] object.
         */
        interface Definition<TYPE> : Configuration.Property.Metadata, Configuration.Validator, Configuration.Value.Extractor<TYPE>, Configuration.Describer, Configuration.Value.Parser<TYPE> {

            override fun isSpecifiedBy(configuration: Config): Boolean = configuration.hasPath(key)

            /**
             * Defines a required property, which must provide a value or produce an error.
             */
            interface Required<TYPE> : Definition<TYPE> {

                /**
                 * Returns an optional property with given [defaultValue]. This property does not produce errors in case the value is unspecified, returning the [defaultValue] instead.
                 */
                fun optional(defaultValue: TYPE? = null): Definition<TYPE?>
            }

            /**
             * Defines a property that must provide a single value or produce an error in case multiple values are specified for the relevant key.
             */
            interface Single<TYPE> : Definition<TYPE> {

                /**
                 * Returns a required property expecting multiple values for the relevant key.
                 */
                fun list(): Required<List<TYPE>>
            }

            /**
             * Default property definition, required and single-value.
             */
            interface Standard<TYPE> : Required<TYPE>, Single<TYPE> {

                /**
                 * Passes the value to a validating mapping function, provided this is valid in the first place.
                 */
                fun <MAPPED : Any> mapValid(mappedTypeName: String, convert: (TYPE) -> Validated<MAPPED, Validation.Error>): Standard<MAPPED>

                /**
                 * Passes the value to a non-validating mapping function, provided this is valid in the first place.
                 */
                fun <MAPPED : Any> map(mappedTypeName: String, convert: (TYPE) -> MAPPED): Standard<MAPPED> = mapValid(mappedTypeName) { value -> valid(convert.invoke(value)) }
            }

            override fun parse(configuration: Config, options: Configuration.Validation.Options): Validated<TYPE, Validation.Error> {

                return validate(configuration, options).mapValid { config -> valid(valueIn(config)) }
            }

            companion object {

                const val SENSITIVE_DATA_PLACEHOLDER = "*****"

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [Long].
                 */
                fun long(key: String, sensitive: Boolean = false): Standard<Long> = LongProperty(key, sensitive)

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [Int].
                 */
                fun int(key: String, sensitive: Boolean = false): Standard<Int> = long(key, sensitive).mapValid { value ->

                    try {
                        valid(Math.toIntExact(value))
                    } catch (e: ArithmeticException) {
                        invalid<Int, Configuration.Validation.Error>(Configuration.Validation.Error.BadValue.of("Provided value exceeds Integer range [${Int.MIN_VALUE}, ${Int.MAX_VALUE}].", key, Int::class.javaObjectType.simpleName))
                    }
                }

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [Boolean].
                 */
                fun boolean(key: String, sensitive: Boolean = false): Standard<Boolean> = StandardProperty(key, Boolean::class.javaObjectType.simpleName, Config::getBoolean, Config::getBooleanList, sensitive)

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [Double].
                 */
                fun double(key: String, sensitive: Boolean = false): Standard<Double> = StandardProperty(key, Double::class.javaObjectType.simpleName, Config::getDouble, Config::getDoubleList, sensitive)

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [Float].
                 */
                fun float(key: String, sensitive: Boolean = false): Standard<Float> = double(key, sensitive).mapValid { value ->

                    val floatValue = value.toFloat()
                    if (floatValue.isInfinite() || floatValue.isNaN()) {
                        invalid<Float, Configuration.Validation.Error>(Configuration.Validation.Error.BadValue.of(key, Float::class.javaObjectType.simpleName, "Provided value exceeds Float range."))
                    } else {
                        valid(value.toFloat())
                    }
                }

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [String].
                 */
                fun string(key: String, sensitive: Boolean = false): Standard<String> = StandardProperty(key, String::class.java.simpleName, Config::getString, Config::getStringList, sensitive)

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [Duration].
                 */
                fun duration(key: String, sensitive: Boolean = false): Standard<Duration> = StandardProperty(key, Duration::class.java.simpleName, Config::getDuration, Config::getDurationList, sensitive)

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [ConfigObject].
                 * It supports an optional [Configuration.Schema], which is used for validation and more when provided.
                 */
                fun nestedObject(key: String, schema: Schema? = null, sensitive: Boolean = false): Standard<ConfigObject> = StandardProperty(key, ConfigObject::class.java.simpleName, Config::getObject, Config::getObjectList, sensitive, schema)

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [ENUM].
                 * This property expects the exact [ENUM] value specified as text for the relevant key.
                 */
                fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, sensitive: Boolean = false): Standard<ENUM> = StandardProperty(key, enumClass.java.simpleName, { conf: Config, propertyKey: String -> conf.getEnum(enumClass.java, propertyKey) }, { conf: Config, propertyKey: String -> conf.getEnumList(enumClass.java, propertyKey) }, sensitive)
            }
        }
    }

    /**
     * A definition of the expected structure of a [Config] object, able to validate it and describe it while preventing sensitive values from being revealed.
     */
    interface Schema : Configuration.Validator, Configuration.Describer {

        /**
         * Name of the schema.
         */
        val name: String?

        /**
         * A description of the schema definition, with references to nested types.
         */
        fun description(): String

        /**
         * All properties defining this schema.
         */
        val properties: Set<Property.Definition<*>>

        companion object {

            /**
             * Constructs a schema with given name and properties.
             */
            fun withProperties(name: String? = null, properties: Iterable<Property.Definition<*>>): Schema = Schema(name, properties)

            /**
             * @see [withProperties].
             */
            fun withProperties(vararg properties: Property.Definition<*>, name: String? = null): Schema = withProperties(name, properties.toSet())

            /**
             * Convenient way of creating an [Iterable] of [Property.Definition]s without having to reference the [Property.Definition.Companion] each time.
             * @see [withProperties].
             */
            fun withProperties(name: String? = null, builder: Property.Definition.Companion.() -> Iterable<Property.Definition<*>>): Schema = withProperties(name, builder.invoke(Property.Definition))
        }
    }

    /**
     * A [Configuration.Schema] that is also able to parse a raw [Config] object into a [VALUE].
     * It is an abstract class to allow extension with delegated properties e.g., object Settings: Specification() { val address by string().optional("localhost:8080") }.
     */
    abstract class Specification<VALUE>(name: String?, private val prefix: String? = null) : Configuration.Schema, Configuration.Value.Parser<VALUE> {

        private val mutableProperties = mutableSetOf<Property.Definition<*>>()

        override val properties: Set<Property.Definition<*>> = mutableProperties

        private val schema: Schema by lazy { Schema(name, properties) }

        /**
         * Returns a delegate for a [Configuration.Property.Definition.Standard] of type [Long].
         */
        fun long(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Long> = PropertyDelegate.long(key, prefix, sensitive) { mutableProperties.add(it) }

        /**
         * Returns a delegate for a [Configuration.Property.Definition.Standard] of type [Int].
         */
        fun int(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Int> = PropertyDelegate.int(key, prefix, sensitive) { mutableProperties.add(it) }

        /**
         * Returns a delegate for a [Configuration.Property.Definition.Standard] of type [Boolean].
         */
        fun boolean(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Boolean> = PropertyDelegate.boolean(key, prefix, sensitive) { mutableProperties.add(it) }

        /**
         * Returns a delegate for a [Configuration.Property.Definition.Standard] of type [Double].
         */
        fun double(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Double> = PropertyDelegate.double(key, prefix, sensitive) { mutableProperties.add(it) }

        /**
         * Returns a delegate for a [Configuration.Property.Definition.Standard] of type [Float].
         */
        fun float(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Float> = PropertyDelegate.float(key, prefix, sensitive) { mutableProperties.add(it) }

        /**
         * Returns a delegate for a [Configuration.Property.Definition.Standard] of type [String].
         */
        fun string(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<String> = PropertyDelegate.string(key, prefix, sensitive) { mutableProperties.add(it) }

        /**
         * Returns a delegate for a [Configuration.Property.Definition.Standard] of type [Duration].
         */
        fun duration(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Duration> = PropertyDelegate.duration(key, prefix, sensitive) { mutableProperties.add(it) }

        /**
         * Returns a delegate for a [Configuration.Property.Definition.Standard] of type [ConfigObject].
         * It supports an optional [Configuration.Schema], which is used for validation and more when provided.
         */
        fun nestedObject(schema: Schema? = null, key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ConfigObject> = PropertyDelegate.nestedObject(schema, key, prefix, sensitive) { mutableProperties.add(it) }

        /**
         * Returns a delegate for a [Configuration.Property.Definition.Standard] of type [ENUM].
         * This property expects the exact [ENUM] value specified as text for the relevant key.
         */
        fun <ENUM : Enum<ENUM>> enum(key: String? = null, enumClass: KClass<ENUM>, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = PropertyDelegate.enum(key, prefix, enumClass, sensitive) { mutableProperties.add(it) }

        override val name: String? get() = schema.name

        override fun description() = schema.description()

        override fun validate(target: Config, options: Validation.Options?) = schema.validate(target, options)

        override fun describe(configuration: Config) = schema.describe(configuration)

        final override fun parse(configuration: Config, options: Configuration.Validation.Options): Valid<VALUE> = validate(configuration, options).mapValid(::parseValid)

        /**
         * Implement to define further mapping and validation logic, assuming the underlying raw [Config] is correct in terms of this [Configuration.Specification].
         */
        protected abstract fun parseValid(configuration: Config): Valid<VALUE>
    }

    object Validation {

        /**
         * [Config] validation options.
         * @property strict whether to raise unknown property keys as errors.
         */
        data class Options(val strict: Boolean) {

            companion object {

                /**
                 * Default [Config] validation options, without [strict] parsing enabled.
                 */
                val defaults: Configuration.Validation.Options = Options(strict = false)
            }
        }

        /**
         * Super-type for the errors raised by the parsing and validation of a [Config] object.
         *
         * @property keyName name of the property key this error refers to, if any.
         * @property typeName name of the type of the property this error refers to, if any.
         * @property message details about what went wrong during the processing.
         * @property containingPath containing path of the error, excluding the [keyName].
         */
        sealed class Error constructor(open val keyName: String?, open val typeName: String?, open val message: String, val containingPath: List<String> = emptyList()) {

            internal companion object {

                private const val UNKNOWN = "<unknown>"

                private fun contextualize(keyName: String, containingPath: List<String>): Pair<String, List<String>> {

                    val keyParts = keyName.split(".")
                    return when {
                        keyParts.size > 1 -> {
                            val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                            val keySegment = keyParts.last()
                            keySegment to fullContainingPath
                        }
                        else -> keyName to containingPath
                    }
                }
            }

            /**
             * Full path for nested property keys, including the [keyName].
             */
            val path: List<String> get() = keyName?.let { containingPath + it } ?: containingPath

            /**
             * [containingPath] joined by "." characters.
             */
            val containingPathAsString: String = containingPath.joinToString(".")

            /**
             * [pathstr] joined by "." characters.
             */
            val pathAsString: String = path.joinToString(".")

            internal abstract fun withContainingPath(vararg containingPath: String): Error

            internal abstract fun with(keyName: String = this.keyName ?: UNKNOWN, typeName: String = this.typeName ?: UNKNOWN): Configuration.Validation.Error

            override fun toString(): String {

                return "(keyName='$keyName', typeName='$typeName', path=$path, message='$message')"
            }

            /**
             * Raised when a value was found for the relevant [keyName], but the value did not match the declared one for the property.
             */
            class WrongType private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(message: String, keyName: String = UNKNOWN, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): WrongType = contextualize(keyName, containingPath).let { (key, path) -> WrongType(key, typeName, message, path) }
                }

                override fun withContainingPath(vararg containingPath: String) = WrongType(keyName, typeName, message, containingPath.toList() + this.containingPath)

                override fun with(keyName: String, typeName: String): WrongType = WrongType.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when no value was found for the relevant [keyName], and the property is [Configuration.Property.Definition.Required].
             */
            class MissingValue private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(message: String, keyName: String = UNKNOWN, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): MissingValue = contextualize(keyName, containingPath).let { (key, path) -> MissingValue(key, typeName, message, path) }
                }

                override fun withContainingPath(vararg containingPath: String) = MissingValue(keyName, typeName, message, containingPath.toList() + this.containingPath)

                override fun with(keyName: String, typeName: String): MissingValue = MissingValue.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when a value was found for the relevant [keyName], it matched the declared raw type for the property, but its value is unacceptable due to application-level validation rules.
             */
            class BadValue private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(message: String, keyName: String = UNKNOWN, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): BadValue = contextualize(keyName, containingPath).let { (key, path) -> BadValue(key, typeName, message, path) }
                }

                override fun withContainingPath(vararg containingPath: String) = BadValue(keyName, typeName, message, containingPath.toList() + this.containingPath)

                override fun with(keyName: String, typeName: String): BadValue = BadValue.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when the [Config] contains a malformed path.
             */
            class BadPath private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(message: String, keyName: String = UNKNOWN, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): BadPath = contextualize(keyName, containingPath).let { (key, path) -> BadPath(key, typeName, message, path) }
                }

                override fun withContainingPath(vararg containingPath: String) = BadPath(keyName, typeName, message, containingPath.toList() + this.containingPath)

                override fun with(keyName: String, typeName: String): BadPath = BadPath.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when the [Config] is malformed and cannot be parsed.
             */
            class MalformedStructure private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                internal companion object {

                    internal fun of(message: String, keyName: String = UNKNOWN, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): MalformedStructure = contextualize(keyName, containingPath).let { (key, path) -> MalformedStructure(key, typeName, message, path) }
                }

                override fun withContainingPath(vararg containingPath: String) = MalformedStructure(keyName, typeName, message, containingPath.toList() + this.containingPath)

                override fun with(keyName: String, typeName: String): MalformedStructure = MalformedStructure.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when a key-value pair appeared in the [Config] object without a matching property in the [Configuration.Schema], and [Configuration.Validation.Options.strict] was enabled.
             */
            class Unknown private constructor(override val keyName: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, null, message(keyName), containingPath) {

                internal companion object {

                    private fun message(keyName: String) = "Unknown property \"$keyName\"."

                    internal fun of(keyName: String = UNKNOWN, containingPath: List<String> = emptyList()): Unknown = contextualize(keyName, containingPath).let { (key, path) -> Unknown(key, path) }
                }

                override val message = message(pathAsString)

                override fun withContainingPath(vararg containingPath: String) = Unknown(keyName, containingPath.toList() + this.containingPath)

                override fun with(keyName: String, typeName: String): Unknown = Unknown.of(keyName, containingPath)
            }

            /**
             * Raised when the specification version found in the [Config] object did not match any known [Configuration.Specification].
             */
            class UnsupportedVersion private constructor(val version: Int, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(null, null, "Unknown configuration version $version.", containingPath) {

                internal companion object {

                    internal fun of(version: Int): UnsupportedVersion = UnsupportedVersion(version)
                }

                override fun withContainingPath(vararg containingPath: String) = UnsupportedVersion(version, containingPath.toList() + this.containingPath)

                override fun with(keyName: String, typeName: String): UnsupportedVersion = this
            }
        }
    }

    object Version {

        /**
         * Defines the contract from extracting a specification version from a [Config] object.
         */
        interface Extractor : Configuration.Value.Parser<Int?> {

            companion object {

                const val DEFAULT_VERSION_VALUE = 1

                /**
                 * Returns a [Configuration.Version.Extractor] that reads the value from given [versionKey], defaulting to [versionDefaultValue] when [versionKey] is unspecified.
                 */
                fun fromKey(versionKey: String, versionDefaultValue: Int? = DEFAULT_VERSION_VALUE): Configuration.Version.Extractor = VersionExtractor(versionKey, versionDefaultValue)
            }
        }
    }

    /**
     * Defines the ability to validate a [Config] object, producing a valid [Config] or a set of [Configuration.Validation.Error].
     */
    interface Validator : net.corda.common.validation.internal.Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options>
}