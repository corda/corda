package net.corda.node.services.config.parsing

import com.typesafe.config.*
import net.corda.node.services.config.parsing.Validated.Companion.invalid
import net.corda.node.services.config.parsing.Validated.Companion.valid
import java.time.Duration
import kotlin.reflect.KClass

// TODO sollecitom introduce either a PasswordProperty type or a "sensitive" boolean resulting in "*****" being printed instead of the value when the configuration is serialized.
interface ConfigProperty<TYPE> : Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> {

    val key: String
    val typeName: String
    val mandatory: Boolean

    @Throws(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)
    fun valueIn(configuration: Config): TYPE

    fun valueDescriptionIn(configuration: Config): ConfigValue {

        return ConfigValueFactory.fromAnyRef(valueIn(configuration))
    }

    @Throws(ConfigException.WrongType::class, ConfigException.BadValue::class)
    fun valueInOrNull(configuration: Config): TYPE? {

        return when {
            isSpecifiedBy(configuration) -> valueIn(configuration)
            else -> null
        }
    }

    interface Required<TYPE> : ConfigProperty<TYPE> {

        fun optional(defaultValue: TYPE? = null): ConfigProperty<TYPE?>

        override val mandatory: Boolean get() = true
    }

    interface Single<TYPE> : ConfigProperty<TYPE> {

        fun list(): ConfigProperty.Required<List<TYPE>>
    }

    interface Standard<TYPE> : ConfigProperty.Required<TYPE>, ConfigProperty.Single<TYPE> {

        fun <MAPPED : Any> map(mappedTypeName: String, convert: (String, TYPE) -> Validated<MAPPED, ConfigValidationError>): ConfigProperty.Standard<MAPPED>
    }

    fun isSpecifiedBy(configuration: Config): Boolean = configuration.hasPath(key)

    override fun validate(target: Config, options: ConfigProperty.ValidationOptions?): Validated<Config, ConfigValidationError> {

        try {
            valueIn(target)
            return valid(target)
        } catch (exception: ConfigException) {
            if (expectedExceptionTypes.any { expected -> expected.isInstance(exception) }) {
                return invalid(exception.toValidationError(key, typeName))
            }
            throw exception
        }
    }

    companion object {

        internal val expectedExceptionTypes: Set<KClass<*>> = setOf(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)

        fun long(key: String): ConfigProperty.Standard<Long> = LongConfigProperty(key)

        fun boolean(key: String): ConfigProperty.Standard<Boolean> = StandardConfigProperty(key, Boolean::class.javaObjectType.simpleName, Config::getBoolean, Config::getBooleanList)

        fun double(key: String): ConfigProperty.Standard<Double> = StandardConfigProperty(key, Double::class.javaObjectType.simpleName, Config::getDouble, Config::getDoubleList)

        fun string(key: String): ConfigProperty.Standard<String> = StandardConfigProperty(key, String::class.java.simpleName, Config::getString, Config::getStringList)

        fun duration(key: String): ConfigProperty.Standard<Duration> = StandardConfigProperty(key, Duration::class.java.simpleName, Config::getDuration, Config::getDurationList)

        fun nestedObject(key: String, schema: ConfigSchema? = null): ConfigProperty.Standard<ConfigObject> = StandardConfigProperty(key, ConfigObject::class.java.simpleName, Config::getObject, Config::getObjectList, schema)

        fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>): ConfigProperty.Standard<ENUM> = StandardConfigProperty(key, enumClass.java.simpleName, { conf: Config, propertyKey: String -> conf.getEnum(enumClass.java, propertyKey) }, { conf: Config, propertyKey: String -> conf.getEnumList(enumClass.java, propertyKey) })
    }

    data class ValidationOptions(val strict: Boolean)
}

private class LongConfigProperty(key: String) : StandardConfigProperty<Long>(key, Long::class.javaObjectType.simpleName, Config::getLong, Config::getLongList) {

    override fun validate(target: Config, options: ConfigProperty.ValidationOptions?): Validated<Config, ConfigValidationError> {

        val validated = super.validate(target, options)
        if (validated.isValid && target.getValue(key).unwrapped().toString().contains(".")) {
            return invalid(ConfigException.WrongType(target.origin(), key, Long::class.javaObjectType.simpleName, Double::class.javaObjectType.simpleName).toValidationError(key, typeName))
        }
        return validated
    }
}

internal open class StandardConfigProperty<TYPE>(override val key: String, typeNameArg: String, private val extractSingleValue: (Config, String) -> TYPE, private val extractListValue: (Config, String) -> List<TYPE>, internal val schema: ConfigSchema? = null) : ConfigProperty.Standard<TYPE> {

    override fun valueIn(configuration: Config) = extractSingleValue.invoke(configuration, key)

    override val typeName: String = schema?.let { "#${it.name ?: "Object@$key"}" } ?: typeNameArg

    override fun <MAPPED : Any> map(mappedTypeName: String, convert: (String, TYPE) -> Validated<MAPPED, ConfigValidationError>): ConfigProperty.Standard<MAPPED> = FunctionalConfigProperty(this, mappedTypeName, extractListValue, convert)

    override fun optional(defaultValue: TYPE?): ConfigProperty<TYPE?> = OptionalConfigProperty(this, defaultValue)

    override fun list(): ConfigProperty.Required<List<TYPE>> = ListConfigProperty(key, typeName, extractListValue, schema)

    override fun toString() = "\"$key\": \"$typeName\""

    override fun valueDescriptionIn(configuration: Config): ConfigValue {

        return schema?.let { configObject(key to it.describe(configuration.getConfig(key))) } ?: super.valueDescriptionIn(configuration)
    }

    override fun validate(target: Config, options: ConfigProperty.ValidationOptions?): Validated<Config, ConfigValidationError> {

        val errors = mutableSetOf<ConfigValidationError>()
        errors += super.validate(target, options).errors
        schema?.let { nestedSchema ->
            val nestedConfig: Config? = target.getConfig(key)
            nestedConfig?.let {
                errors += nestedSchema.validate(nestedConfig, options).errors.map { error -> error.withContainingPath(*key.split(".").toTypedArray()) }
            }
        }
        return if (errors.isEmpty()) valid(target) else invalid(errors)
    }
}

// TODO sollecitom make it take a delegate property instead
private class ListConfigProperty<TYPE>(override val key: String, elementTypeName: String, private val extractListValue: (Config, String) -> List<TYPE>, private val elementSchema: ConfigSchema?) : ConfigProperty.Required<List<TYPE>> {

    override val typeName: String = elementSchema?.let { "List<#${it.name ?: "Object@$key"}>" } ?: "List<${elementTypeName.capitalize()}>"

    override fun valueIn(configuration: Config): List<TYPE> = extractListValue.invoke(configuration, key)

    override fun optional(defaultValue: List<TYPE>?): ConfigProperty<List<TYPE>?> = OptionalConfigProperty(this, defaultValue)

    override fun validate(target: Config, options: ConfigProperty.ValidationOptions?): Validated<Config, ConfigValidationError> {

        val errors = mutableSetOf<ConfigValidationError>()
        errors += super.validate(target, options).errors
        elementSchema?.let { schema ->
            errors += valueIn(target).asSequence().map { element -> element as ConfigObject }.map(ConfigObject::toConfig).mapIndexed { index, targetConfig -> schema.validate(targetConfig, options).errors.map { error -> error.withContainingPath(key, "[$index]") } }.reduce { one, other -> one + other }
        }
        return if (errors.isEmpty()) valid(target) else invalid(errors)
    }

    override fun valueDescriptionIn(configuration: Config): ConfigValue {

        return elementSchema?.let { schema -> configObject(key to ConfigValueFactory.fromAnyRef(valueIn(configuration).asSequence().map { element -> element as ConfigObject }.map(ConfigObject::toConfig).map { schema.describe(it) })) } ?: super.valueDescriptionIn(configuration)
    }

    override fun toString() = "\"$key\": \"$typeName\""
}

private class OptionalConfigProperty<TYPE>(private val delegate: ConfigProperty.Required<TYPE>, private val defaultValue: TYPE?) : ConfigProperty<TYPE?> {

    override val key = delegate.key

    override val mandatory: Boolean = false

    override val typeName: String = "${delegate.typeName}?"

    override fun valueIn(configuration: Config): TYPE? {

        return when {
            isSpecifiedBy(configuration) -> delegate.valueIn(configuration)
            else -> defaultValue ?: throw ConfigException.Missing(key)
        }
    }

    override fun validate(target: Config, options: ConfigProperty.ValidationOptions?): Validated<Config, ConfigValidationError> {

        return when {
            isSpecifiedBy(target) -> delegate.validate(target, options)
            defaultValue != null -> valid(target)
            else -> invalid(ConfigException.Missing(key).toValidationError(key, typeName))
        }
    }

    override fun toString() = "\"$key\": \"$typeName\""
}

private class FunctionalConfigProperty<TYPE, MAPPED : Any>(private val delegate: ConfigProperty.Standard<TYPE>, mappedTypeName: String, val extractListValue: (Config, String) -> List<TYPE>, private val convert: (String, TYPE) -> Validated<MAPPED, ConfigValidationError>) : ConfigProperty.Standard<MAPPED> {

    override val key = delegate.key

    override fun valueIn(configuration: Config) = convert.invoke(key, delegate.valueIn(configuration)).orElseThrow()

    override val typeName: String = if (delegate.typeName == "#$mappedTypeName") delegate.typeName else "$mappedTypeName(${delegate.typeName})"

    override fun <M : Any> map(mappedTypeName: String, convert: (String, MAPPED) -> Validated<M, ConfigValidationError>): ConfigProperty.Standard<M> = FunctionalConfigProperty(delegate, mappedTypeName, extractListValue, { key: String, target: TYPE -> this.convert.invoke(key, target).flatMap { convert(key, it) } })

    override fun optional(defaultValue: MAPPED?): ConfigProperty<MAPPED?> = OptionalConfigProperty(this, defaultValue)

    override fun list(): ConfigProperty.Required<List<MAPPED>> = FunctionalListConfigProperty(this, extractListValue)

    override fun validate(target: Config, options: ConfigProperty.ValidationOptions?): Validated<Config, ConfigValidationError> {

        val errors = mutableSetOf<ConfigValidationError>()
        errors += delegate.validate(target, options).errors
        if (errors.isEmpty()) {
            errors += convert.invoke(key, delegate.valueIn(target)).errors
        }
        return if (errors.isEmpty()) valid(target) else invalid(errors)
    }

    override fun toString() = "\"$key\": \"$typeName\""
}

private class FunctionalListConfigProperty<RAW, TYPE : Any>(private val delegate: ConfigProperty.Single<TYPE>, private val extractListValue: (Config, String) -> List<RAW>) : ConfigProperty.Required<List<TYPE>> {

    override val key = delegate.key

    override val typeName: String = "List<${delegate.typeName}>"

    override fun valueIn(configuration: Config): List<TYPE> = extractListValue.invoke(configuration, key).asSequence().map { configObject(key to ConfigValueFactory.fromAnyRef(it)) }.map(ConfigObject::toConfig).map(delegate::valueIn).toList()

    override fun optional(defaultValue: List<TYPE>?): ConfigProperty<List<TYPE>?> = OptionalConfigProperty(this, defaultValue)

    override fun validate(target: Config, options: ConfigProperty.ValidationOptions?): Validated<Config, ConfigValidationError> {

        val list = try {
            extractListValue.invoke(target, key)
        } catch (e: Exception) {
            if (e is ConfigException && ConfigProperty.expectedExceptionTypes.any { expected -> expected.isInstance(e) }) {
                return invalid(e.toValidationError(key, typeName))
            } else {
                throw e
            }
        }
        val errors = list.asSequence().map { configObject(key to ConfigValueFactory.fromAnyRef(it)) }.mapIndexed { index, value -> delegate.validate(value.toConfig(), options).errors.map { error -> error.withContainingPath(key, "[$index]") } }.reduce { one, other -> one + other }.toSet()

        return if (errors.isEmpty()) valid(target) else invalid(errors)
    }

    override fun toString() = "\"$key\": \"$typeName\""
}

private fun ConfigException.toValidationError(keyName: String, typeName: String): ConfigValidationError {

    return when (this) {
        is ConfigException.Missing -> ConfigValidationError.MissingValue.of(keyName, typeName, message!!)
        is ConfigException.WrongType -> ConfigValidationError.WrongType.of(keyName, typeName, message!!)
        is ConfigException.BadValue -> ConfigValidationError.MissingValue.of(keyName, typeName, message!!)
        else -> throw IllegalStateException("Unsupported ConfigException of type ${this::class.java.name}")
    }
}

inline fun <TYPE, reified MAPPED : Any> ConfigProperty.Standard<TYPE>.map(noinline convert: (String, TYPE) -> Validated<MAPPED, ConfigValidationError>): ConfigProperty.Standard<MAPPED> = this.map(MAPPED::class.java.simpleName, convert)