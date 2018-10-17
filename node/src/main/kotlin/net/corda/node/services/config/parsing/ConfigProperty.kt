package net.corda.node.services.config.parsing

import com.typesafe.config.*
import net.corda.node.services.config.parsing.Validated.Companion.invalid
import net.corda.node.services.config.parsing.Validated.Companion.valid
import java.time.Duration
import kotlin.reflect.KClass

interface ConfigPropertyMetadata {

    val key: String
    val typeName: String
    val mandatory: Boolean
    val sensitive: Boolean

    val schema: ConfigSchema?
}

interface ConfigProperty<TYPE> : Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options>, ConfigPropertyMetadata, Configuration.Describer, Configuration.Value.Extractor<TYPE> {

    override fun isSpecifiedBy(configuration: Config): Boolean = configuration.hasPath(key)

    interface Required<TYPE> : ConfigProperty<TYPE> {

        fun optional(defaultValue: TYPE? = null): ConfigProperty<TYPE?>
    }

    interface Single<TYPE> : ConfigProperty<TYPE> {

        fun list(): ConfigProperty.Required<List<TYPE>>
    }

    interface Standard<TYPE> : ConfigProperty.Required<TYPE>, ConfigProperty.Single<TYPE> {

        fun <MAPPED : Any> map(mappedTypeName: String, convert: (String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): ConfigProperty.Standard<MAPPED>
    }

    companion object {

        const val SENSITIVE_DATA_PLACEHOLDER = "*****"

        internal val expectedExceptionTypes: Set<KClass<*>> = setOf(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)

        fun long(key: String, sensitive: Boolean = false): ConfigProperty.Standard<Long> = LongConfigProperty(key, sensitive)

        fun boolean(key: String, sensitive: Boolean = false): ConfigProperty.Standard<Boolean> = StandardConfigProperty(key, Boolean::class.javaObjectType.simpleName, Config::getBoolean, Config::getBooleanList, sensitive)

        fun double(key: String, sensitive: Boolean = false): ConfigProperty.Standard<Double> = StandardConfigProperty(key, Double::class.javaObjectType.simpleName, Config::getDouble, Config::getDoubleList, sensitive)

        fun string(key: String, sensitive: Boolean = false): ConfigProperty.Standard<String> = StandardConfigProperty(key, String::class.java.simpleName, Config::getString, Config::getStringList, sensitive)

        fun duration(key: String, sensitive: Boolean = false): ConfigProperty.Standard<Duration> = StandardConfigProperty(key, Duration::class.java.simpleName, Config::getDuration, Config::getDurationList, sensitive)

        fun nestedObject(key: String, schema: ConfigSchema? = null, sensitive: Boolean = false): ConfigProperty.Standard<ConfigObject> = StandardConfigProperty(key, ConfigObject::class.java.simpleName, Config::getObject, Config::getObjectList, sensitive, schema)

        fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, sensitive: Boolean = false): ConfigProperty.Standard<ENUM> = StandardConfigProperty(key, enumClass.java.simpleName, { conf: Config, propertyKey: String -> conf.getEnum(enumClass.java, propertyKey) }, { conf: Config, propertyKey: String -> conf.getEnumList(enumClass.java, propertyKey) }, sensitive)
    }
}

private class LongConfigProperty(key: String, sensitive: Boolean = false) : StandardConfigProperty<Long>(key, Long::class.javaObjectType.simpleName, Config::getLong, Config::getLongList, sensitive) {

    override fun validate(target: Config, options: Configuration.Validation.Options?): Validated<Config, Configuration.Validation.Error> {

        val validated = super.validate(target, options)
        if (validated.isValid && target.getValue(key).unwrapped().toString().contains(".")) {
            return invalid(ConfigException.WrongType(target.origin(), key, Long::class.javaObjectType.simpleName, Double::class.javaObjectType.simpleName).toValidationError(key, typeName))
        }
        return validated
    }
}

internal open class StandardConfigProperty<TYPE>(override val key: String, typeNameArg: String, private val extractSingleValue: (Config, String) -> TYPE, internal val extractListValue: (Config, String) -> List<TYPE>, override val sensitive: Boolean = false, final override val schema: ConfigSchema? = null) : ConfigProperty.Standard<TYPE> {

    override fun valueIn(configuration: Config) = extractSingleValue.invoke(configuration, key)

    override val typeName: String = schema?.let { "#${it.name ?: "Object@$key"}" } ?: typeNameArg

    override fun <MAPPED : Any> map(mappedTypeName: String, convert: (String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): ConfigProperty.Standard<MAPPED> = FunctionalConfigProperty(this, mappedTypeName, extractListValue, convert)

    override fun optional(defaultValue: TYPE?): ConfigProperty<TYPE?> = OptionalConfigProperty(this, defaultValue)

    override fun list(): ConfigProperty.Required<List<TYPE>> = ListConfigProperty(this)

    override fun describe(configuration: Config): ConfigValue {

        if (sensitive) {
            return ConfigValueFactory.fromAnyRef(ConfigProperty.SENSITIVE_DATA_PLACEHOLDER)
        }
        return schema?.describe(configuration.getConfig(key)) ?: ConfigValueFactory.fromAnyRef(valueIn(configuration))
    }

    override val mandatory = true

    override fun validate(target: Config, options: Configuration.Validation.Options?): Validated<Config, Configuration.Validation.Error> {

        val errors = mutableSetOf<Configuration.Validation.Error>()
        errors += errorsWhenExtractingValue(target)
        if (errors.isEmpty()) {
            schema?.let { nestedSchema ->
                val nestedConfig: Config? = target.getConfig(key)
                nestedConfig?.let {
                    errors += nestedSchema.validate(nestedConfig, options).errors.map { error -> error.withContainingPath(*key.split(".").toTypedArray()) }
                }
            }
        }
        return Validated.withResult(target, errors)
    }

    override fun toString() = "\"$key\": \"$typeName\""
}

private abstract class DelegatedProperty<TYPE, DELEGATE : ConfigPropertyMetadata>(protected val delegate: DELEGATE) : ConfigPropertyMetadata by delegate, ConfigProperty<TYPE> {

    final override fun toString() = "\"$key\": \"$typeName\""
}

private abstract class RequiredDelegatedProperty<TYPE, DELEGATE : ConfigProperty.Required<*>>(delegate: DELEGATE) : DelegatedProperty<TYPE, DELEGATE>(delegate), ConfigProperty.Required<TYPE> {

    final override fun optional(defaultValue: TYPE?): ConfigProperty<TYPE?> = OptionalConfigProperty(this, defaultValue)
}

private class ListConfigProperty<TYPE>(delegate: StandardConfigProperty<TYPE>) : RequiredDelegatedProperty<List<TYPE>, StandardConfigProperty<TYPE>>(delegate) {

    override val typeName: String = "List<${delegate.typeName}>"

    override fun valueIn(configuration: Config): List<TYPE> = delegate.extractListValue.invoke(configuration, key)

    override fun validate(target: Config, options: Configuration.Validation.Options?): Validated<Config, Configuration.Validation.Error> {

        val errors = mutableSetOf<Configuration.Validation.Error>()
        errors += errorsWhenExtractingValue(target)
        if (errors.isEmpty()) {
            delegate.schema?.let { schema ->
                errors += valueIn(target).asSequence().map { element -> element as ConfigObject }.map(ConfigObject::toConfig).mapIndexed { index, targetConfig -> schema.validate(targetConfig, options).errors.map { error -> error.withContainingPath(key, "[$index]") } }.reduce { one, other -> one + other }
            }
        }
        return Validated.withResult(target, errors)
    }

    override fun describe(configuration: Config): ConfigValue {

        if (sensitive) {
            return ConfigValueFactory.fromAnyRef(ConfigProperty.SENSITIVE_DATA_PLACEHOLDER)
        }
        return delegate.schema?.let { schema -> ConfigValueFactory.fromAnyRef(valueIn(configuration).asSequence().map { element -> element as ConfigObject }.map(ConfigObject::toConfig).map { schema.describe(it) }.toList()) } ?: ConfigValueFactory.fromAnyRef(valueIn(configuration))
    }
}

private fun ConfigProperty<*>.errorsWhenExtractingValue(target: Config): Set<Configuration.Validation.Error> {

    try {
        valueIn(target)
        return emptySet()
    } catch (exception: ConfigException) {
        if (ConfigProperty.expectedExceptionTypes.any { expected -> expected.isInstance(exception) }) {
            return setOf(exception.toValidationError(key, typeName))
        }
        throw exception
    }
}

private class OptionalConfigProperty<TYPE>(delegate: ConfigProperty.Required<TYPE>, private val defaultValue: TYPE?) : DelegatedProperty<TYPE?, ConfigProperty.Required<TYPE>>(delegate) {

    override val mandatory: Boolean = false

    override val typeName: String = "${super.typeName}?"

    override fun describe(configuration: Config) = delegate.describe(configuration)

    override fun valueIn(configuration: Config): TYPE? {

        return when {
            isSpecifiedBy(configuration) -> delegate.valueIn(configuration)
            else -> defaultValue ?: throw ConfigException.Missing(key)
        }
    }

    override fun validate(target: Config, options: Configuration.Validation.Options?): Validated<Config, Configuration.Validation.Error> {

        return when {
            isSpecifiedBy(target) -> delegate.validate(target, options)
            defaultValue != null -> valid(target)
            else -> invalid(ConfigException.Missing(key).toValidationError(key, typeName))
        }
    }
}

private class FunctionalConfigProperty<TYPE, MAPPED : Any>(delegate: ConfigProperty.Standard<TYPE>, private val mappedTypeName: String, internal val extractListValue: (Config, String) -> List<TYPE>, private val convert: (key: String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>) : RequiredDelegatedProperty<MAPPED, ConfigProperty.Standard<TYPE>>(delegate), ConfigProperty.Standard<MAPPED> {

    override fun valueIn(configuration: Config) = convert.invoke(key, delegate.valueIn(configuration)).valueOrThrow()

    override val typeName: String = if (super.typeName == "#$mappedTypeName") super.typeName else "$mappedTypeName(${super.typeName})"

    override fun <M : Any> map(mappedTypeName: String, convert: (key: String, MAPPED) -> Validated<M, Configuration.Validation.Error>): ConfigProperty.Standard<M> = FunctionalConfigProperty(delegate, mappedTypeName, extractListValue, { key: String, target: TYPE -> this.convert.invoke(key, target).flatMap { convert(key, it) } })

    override fun list(): ConfigProperty.Required<List<MAPPED>> = FunctionalListConfigProperty(this)

    override fun validate(target: Config, options: Configuration.Validation.Options?): Validated<Config, Configuration.Validation.Error> {

        val errors = mutableSetOf<Configuration.Validation.Error>()
        errors += delegate.validate(target, options).errors
        if (errors.isEmpty()) {
            errors += convert.invoke(key, delegate.valueIn(target)).errors
        }
        return Validated.withResult(target, errors)
    }

    override fun describe(configuration: Config) = delegate.describe(configuration)
}

private class FunctionalListConfigProperty<RAW, TYPE : Any>(delegate: FunctionalConfigProperty<RAW, TYPE>) : RequiredDelegatedProperty<List<TYPE>, FunctionalConfigProperty<RAW, TYPE>>(delegate) {

    override val typeName: String = "List<${super.typeName}>"

    override fun valueIn(configuration: Config): List<TYPE> = delegate.extractListValue.invoke(configuration, key).asSequence().map { configObject(key to ConfigValueFactory.fromAnyRef(it)) }.map(ConfigObject::toConfig).map(delegate::valueIn).toList()

    override fun validate(target: Config, options: Configuration.Validation.Options?): Validated<Config, Configuration.Validation.Error> {

        val list = try {
            delegate.extractListValue.invoke(target, key)
        } catch (e: Exception) {
            if (e is ConfigException && ConfigProperty.expectedExceptionTypes.any { expected -> expected.isInstance(e) }) {
                return invalid(e.toValidationError(key, typeName))
            } else {
                throw e
            }
        }
        val errors = list.asSequence().map { configObject(key to ConfigValueFactory.fromAnyRef(it)) }.mapIndexed { index, value -> delegate.validate(value.toConfig(), options).errors.map { error -> error.withContainingPath(key, "[$index]") } }.reduce { one, other -> one + other }.toSet()
        return Validated.withResult(target, errors)
    }

    override fun describe(configuration: Config): ConfigValue {

        if (sensitive) {
            return ConfigValueFactory.fromAnyRef(ConfigProperty.SENSITIVE_DATA_PLACEHOLDER)
        }
        return delegate.schema?.let { schema -> ConfigValueFactory.fromAnyRef(valueIn(configuration).asSequence().map { element -> element as ConfigObject }.map(ConfigObject::toConfig).map { schema.describe(it) }.toList()) } ?: ConfigValueFactory.fromAnyRef(valueIn(configuration))
    }
}

private fun ConfigException.toValidationError(keyName: String, typeName: String): Configuration.Validation.Error {

    return when (this) {
        is ConfigException.Missing -> Configuration.Validation.Error.MissingValue.of(keyName, typeName, message!!)
        is ConfigException.WrongType -> Configuration.Validation.Error.WrongType.of(keyName, typeName, message!!)
        is ConfigException.BadValue -> Configuration.Validation.Error.MissingValue.of(keyName, typeName, message!!)
        else -> throw IllegalStateException("Unsupported ConfigException of type ${this::class.java.name}")
    }
}

inline fun <TYPE, reified MAPPED : Any> ConfigProperty.Standard<TYPE>.map(noinline convert: (String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): ConfigProperty.Standard<MAPPED> = this.map(MAPPED::class.java.simpleName, convert)