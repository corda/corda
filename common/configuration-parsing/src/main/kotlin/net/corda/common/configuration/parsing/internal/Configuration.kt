package net.corda.common.configuration.parsing.internal

import com.typesafe.config.*
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
        fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue = { value -> ConfigValueFactory.fromAnyRef(value.toString()) }, options: Options): ConfigValue?
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
             * @throws ConfigException.BadValue if the [Config] specifies a value of the correct type, but this in unacceptable according to
             * application-level validation rules.
             */
            @Throws(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)
            fun valueIn(configuration: Config, options: Options): TYPE

            /**
             * Returns whether the value is specified by the [Config].
             */
            fun isSpecifiedBy(configuration: Config): Boolean

            /**
             * Returns a value out of a [Config] if all is good, or null if no value is present. Otherwise, it throws an exception.
             *
             * @throws ConfigException.WrongType if the [Config] specifies a value of the wrong type.
             * @throws ConfigException.BadValue if the [Config] specifies a value of the correct type, but this in unacceptable according to
             * application-level validation rules.
             */
            @Throws(ConfigException.WrongType::class, ConfigException.BadValue::class)
            fun valueInOrNull(configuration: Config, options: Options): TYPE? {

                return when {
                    isSpecifiedBy(configuration) -> valueIn(configuration, options)
                    else -> null
                }
            }
        }

        /**
         * Able to parse a value from a [Config] and [Configuration.Options], returning a [Valid] result containing either the value itself, or some [Configuration.Validation.Error]s.
         */
        interface Parser<VALUE> {

            /**
             * Returns a [Valid] wrapper either around a valid value extracted from the [Config], or around a set of [Configuration.Validation.Error] with details about what went wrong.
             */
            fun parse(configuration: Config, options: Options = Options.defaults): Valid<VALUE>
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
                 * Returns an optional property. This property does not produce errors in case the value is unspecified.
                 */
                fun optional(): Optional<TYPE>
            }

            /**
             * Defines a required property with a collection of values.
             */
            interface RequiredList<TYPE> : Required<List<TYPE>> {

                /**
                 * Passes the value to a validating mapping function, provided this is valid in the first place.
                 */
                fun <MAPPED> mapValid(mappedTypeName: String, convert: (List<TYPE>) -> Validated<MAPPED, Validation.Error>): Required<MAPPED>

                /**
                 * Passes the value to a non-validating mapping function, provided this is valid in the first place.
                 */
                fun <MAPPED> map(mappedTypeName: String, convert: (List<TYPE>) -> MAPPED): Required<MAPPED> = mapValid(mappedTypeName) { value -> valid(convert.invoke(value)) }
            }

            /**
             * Defines a property that must provide a single value or produce an error in case multiple values are specified for the relevant key.
             */
            interface Single<TYPE> : Definition<TYPE> {

                /**
                 * Returns a required property expecting multiple values for the relevant key.
                 */
                fun list(): RequiredList<TYPE>
            }

            /**
             * Defines a property that might be missing, resulting in a null value.
             */
            interface Optional<TYPE> : Definition<TYPE?> {

                /**
                 * Allows to specify a [defaultValue], returning a required [Configuration.Property.Definition].
                 */
                fun withDefaultValue(defaultValue: TYPE): Definition<TYPE>
            }

            /**
             * Default property definition, required and single-value.
             */
            interface Standard<TYPE> : Required<TYPE>, Single<TYPE> {

                /**
                 * Passes the value to a validating mapping function, provided this is valid in the first place.
                 */
                fun <MAPPED> mapValid(mappedTypeName: String, convert: (TYPE) -> Validated<MAPPED, Validation.Error>): Standard<MAPPED>

                /**
                 * Passes the value to a non-validating mapping function, provided this is valid in the first place.
                 */
                fun <MAPPED> map(mappedTypeName: String, convert: (TYPE) -> MAPPED): Standard<MAPPED> = mapValid(mappedTypeName) { value -> valid(convert.invoke(value)) }
            }

            override fun parse(configuration: Config, options: Configuration.Options): Validated<TYPE, Validation.Error> {
                return validate(configuration, options).mapValid { config -> valid(valueIn(config, options)) }
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
                fun boolean(key: String, sensitive: Boolean = false): Standard<Boolean> = StandardProperty(key, Boolean::class.javaObjectType.simpleName, { config, path, _ -> config.getBoolean(path) }, { config, path, _ -> config.getBooleanList(path) }, sensitive)

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [Double].
                 */
                fun double(key: String, sensitive: Boolean = false): Standard<Double> = StandardProperty(key, Double::class.javaObjectType.simpleName, { config, path, _ -> config.getDouble(path) }, { config, path, _  -> config.getDoubleList(path) }, sensitive)

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
                fun string(key: String, sensitive: Boolean = false): Standard<String> = StandardProperty(
                        key,
                        String::class.java.simpleName,
                        { config, path, _ -> config.getString(path) },
                        { config, path, _ -> config.getStringList(path) },
                        sensitive
                )

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [Duration].
                 */
                fun duration(key: String, sensitive: Boolean = false): Standard<Duration> = StandardProperty(key, Duration::class.java.simpleName, { config, path, _ -> config.getDuration(path) }, { config, path, _ -> config.getDurationList(path) }, sensitive)

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [ConfigObject].
                 * It supports an optional [Configuration.Schema], which is used for validation and more when provided.
                 */
                fun nestedObject(key: String, schema: Schema? = null, sensitive: Boolean = false): Standard<ConfigObject> = StandardProperty(
                        key,
                        ConfigObject::class.java.simpleName,
                        { config, path, _ -> config.getObject(path) },
                        { config, path, _ -> config.getObjectList(path) },
                        sensitive,
                        schema
                )

                /**
                 * Returns a [Configuration.Property.Definition.Standard] with value of type [ENUM].
                 * This property expects a value in the configuration matching one of the cases of [ENUM], as text, in uppercase.
                 */
                fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, sensitive: Boolean = false): Standard<ENUM> = StandardProperty(
                        key,
                        enumClass.java.simpleName,
                        { conf: Config, propertyKey: String, _ -> conf.getEnum(enumClass.java, propertyKey) },
                        { conf: Config, propertyKey: String, _ -> conf.getEnumList(enumClass.java, propertyKey) },
                        sensitive
                )
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

        override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options): ConfigValue

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
    abstract class Specification<VALUE>(override val name: String, private val prefix: String? = null) : Configuration.Schema, Configuration.Value.Parser<VALUE> {

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
         * This property expects a value in the configuration matching one of the cases of [ENUM], as text, in uppercase.
         */
        fun <ENUM : Enum<ENUM>> enum(key: String? = null, enumClass: KClass<ENUM>, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = PropertyDelegate.enum(key, prefix, enumClass, sensitive) { mutableProperties.add(it) }

        /**
         * @see enum
         */
        fun <ENUM : Enum<ENUM>> enum(enumClass: KClass<ENUM>, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = enum(key = null, enumClass = enumClass, sensitive = sensitive)

        override fun description() = schema.description()

        override fun validate(target: Config, options: Options) = schema.validate(target, options)

        override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options) = schema.describe(configuration, serialiseValue, options)

        final override fun parse(configuration: Config, options: Options): Valid<VALUE> = validate(configuration, options).mapValid { parseValid(it, options) }

        /**
         * Implement to define further mapping and validation logic, assuming the underlying raw [Config] is correct in terms of this [Configuration.Specification].
         */
        protected abstract fun parseValid(configuration: Config, options: Options): Valid<VALUE>
    }

    /**
     * Validation and processing options.
     * @property strict whether to raise unknown property keys as errors.
     */
    class Options(val strict: Boolean = false) {

        companion object {

            /**
             * Default [Config] options, without [strict] parsing enabled.
             */
            val defaults: Configuration.Options = Options()
        }
    }

    object Validation {

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
             * [path] joined by "." characters.
             */
            val pathAsString: String get() = path.joinToString(".")

            internal fun withContainingPathPrefix(vararg containingPath: String): Error = withContainingPath(*(containingPath.toList() + this.containingPath).toTypedArray())

            internal abstract fun withContainingPath(vararg containingPath: String): Error

            internal abstract fun with(keyName: String = this.keyName ?: UNKNOWN, typeName: String = this.typeName ?: UNKNOWN): Configuration.Validation.Error

            override fun toString(): String {

                return "$message: (keyName='$keyName', typeName='$typeName', path=$path)"
            }

            /**
             * Raised when a value was found for the relevant [keyName], but the value did not match the declared one for the property.
             */
            class WrongType private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                companion object {

                    fun of(message: String, keyName: String? = null, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): WrongType = contextualize(keyName ?: UNKNOWN, containingPath).let { (key, path) -> WrongType(key, typeName, message, path) }

                    fun forKey(keyName: String, expectedTypeName: String, actualTypeName: String): WrongType = of("$keyName has type ${actualTypeName.toUpperCase()} rather than ${expectedTypeName.toUpperCase()}")
                }

                override fun withContainingPath(vararg containingPath: String) = WrongType(keyName, typeName, message, containingPath.toList())

                override fun with(keyName: String, typeName: String): WrongType = WrongType.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when no value was found for the relevant [keyName], and the property is [Configuration.Property.Definition.Required].
             */
            class MissingValue private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                companion object {

                    fun of(message: String, keyName: String? = null, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): MissingValue = contextualize(keyName ?: UNKNOWN, containingPath).let { (key, path) -> MissingValue(key, typeName, message, path) }

                    fun forKey(keyName: String): MissingValue = of("No configuration setting found for key '$keyName'", keyName)
                }

                override fun withContainingPath(vararg containingPath: String) = MissingValue(keyName, typeName, message, containingPath.toList())

                override fun with(keyName: String, typeName: String): MissingValue = MissingValue.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when a value was found for the relevant [keyName], it matched the declared raw type for the property, but its value is unacceptable due to application-level validation rules.
             */
            class BadValue private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                companion object {

                    fun of(message: String, keyName: String? = null, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): BadValue = contextualize(keyName ?: UNKNOWN, containingPath).let { (key, path) -> BadValue(key, typeName, message, path) }
                }

                override fun withContainingPath(vararg containingPath: String) = BadValue(keyName, typeName, message, containingPath.toList())

                override fun with(keyName: String, typeName: String): BadValue = BadValue.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when the [Config] contains a malformed path.
             */
            class BadPath private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                companion object {

                    fun of(message: String, keyName: String? = null, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): BadPath = contextualize(keyName ?: UNKNOWN, containingPath).let { (key, path) -> BadPath(key, typeName, message, path) }
                }

                override fun withContainingPath(vararg containingPath: String) = BadPath(keyName, typeName, message, containingPath.toList())

                override fun with(keyName: String, typeName: String): BadPath = BadPath.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when the [Config] is malformed and cannot be parsed.
             */
            class MalformedStructure private constructor(override val keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, typeName, message, containingPath) {

                companion object {

                    fun of(message: String, keyName: String? = null, typeName: String = UNKNOWN, containingPath: List<String> = emptyList()): MalformedStructure = contextualize(keyName ?: UNKNOWN, containingPath).let { (key, path) -> MalformedStructure(key, typeName, message, path) }
                }

                override fun withContainingPath(vararg containingPath: String) = MalformedStructure(keyName, typeName, message, containingPath.toList())

                override fun with(keyName: String, typeName: String): MalformedStructure = MalformedStructure.of(message, keyName, typeName, containingPath)
            }

            /**
             * Raised when a key-value pair appeared in the [Config] object without a matching property in the [Configuration.Schema], and [Configuration.Options.strict] was enabled.
             */
            class Unknown private constructor(override val keyName: String, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(keyName, null, message(keyName), containingPath) {

                companion object {

                    private fun message(keyName: String) = "Unknown property \'$keyName\'"

                    fun of(keyName: String = UNKNOWN, containingPath: List<String> = emptyList()): Unknown = contextualize(keyName, containingPath).let { (key, path) -> Unknown(key, path) }
                }

                override fun withContainingPath(vararg containingPath: String) = Unknown(keyName, containingPath.toList())

                override fun with(keyName: String, typeName: String): Unknown = Unknown.of(keyName, containingPath)
            }

            /**
             * Raised when the specification version found in the [Config] object did not match any known [Configuration.Specification].
             */
            class UnsupportedVersion private constructor(val version: Int, containingPath: List<String> = emptyList()) : Configuration.Validation.Error(null, null, "Unknown configuration version $version.", containingPath) {

                companion object {

                    fun of(version: Int): UnsupportedVersion = UnsupportedVersion(version)
                }

                override fun withContainingPath(vararg containingPath: String) = UnsupportedVersion(version, containingPath.toList())

                override fun with(keyName: String, typeName: String): UnsupportedVersion = this
            }
        }
    }

    object Version {

        /**
         * Defines the contract from extracting a specification version from a [Config] object.
         */
        interface Extractor : Configuration.Value.Parser<Int> {

            companion object {

                const val DEFAULT_VERSION_VALUE = 1

                /**
                 * Returns a [Configuration.Version.Extractor] that reads the value from given [versionPath], defaulting to [versionDefaultValue] when [versionPath] is unspecified.
                 */
                fun fromPath(versionPath: String, versionDefaultValue: Int = DEFAULT_VERSION_VALUE): Configuration.Version.Extractor = VersionExtractor(versionPath, versionDefaultValue)
            }
        }
    }

    /**
     * Defines the ability to validate a [Config] object, producing a valid [Config] or a set of [Configuration.Validation.Error].
     */
    interface Validator : net.corda.common.validation.internal.Validator<Config, Configuration.Validation.Error, Configuration.Options>
}