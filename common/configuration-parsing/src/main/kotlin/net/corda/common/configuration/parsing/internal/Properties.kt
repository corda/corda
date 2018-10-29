package net.corda.common.configuration.parsing.internal

import com.typesafe.config.*
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid

internal class LongProperty(key: String, sensitive: Boolean = false) : StandardProperty<Long>(key, Long::class.javaObjectType.simpleName, Config::getLong, Config::getLongList, sensitive) {

    override fun validate(target: Config, options: Configuration.Validation.Options?): Valid<Config> {

        val validated = super.validate(target, options)
        if (validated.isValid && target.getValue(key).unwrapped().toString().contains(".")) {
            return invalid(ConfigException.WrongType(target.origin(), key, Long::class.javaObjectType.simpleName, Double::class.javaObjectType.simpleName).toValidationError(key, typeName))
        }
        return validated
    }
}

internal open class StandardProperty<TYPE>(override val key: String, typeNameArg: String, private val extractSingleValue: (Config, String) -> TYPE, internal val extractListValue: (Config, String) -> List<TYPE>, override val isSensitive: Boolean = false, final override val schema: Configuration.Schema? = null) : Configuration.Property.Definition.Standard<TYPE> {

    override fun valueIn(configuration: Config) = extractSingleValue.invoke(configuration, key)

    override val typeName: String = schema?.let { "#${it.name ?: "Object@$key"}" } ?: typeNameArg

    override fun <MAPPED : Any> mapValid(mappedTypeName: String, convert: (TYPE) -> Valid<MAPPED>): Configuration.Property.Definition.Standard<MAPPED> = FunctionalProperty(this, mappedTypeName, extractListValue, convert)

    override fun optional(defaultValue: TYPE?): Configuration.Property.Definition<TYPE?> = OptionalProperty(this, defaultValue)

    override fun list(): Configuration.Property.Definition.Required<List<TYPE>> = ListProperty(this)

    override fun describe(configuration: Config): ConfigValue {

        if (isSensitive) {
            return ConfigValueFactory.fromAnyRef(Configuration.Property.Definition.SENSITIVE_DATA_PLACEHOLDER)
        }
        return schema?.describe(configuration.getConfig(key)) ?: ConfigValueFactory.fromAnyRef(valueIn(configuration))
    }

    override val isMandatory = true

    override fun validate(target: Config, options: Configuration.Validation.Options?): Valid<Config> {

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

private class ListProperty<TYPE>(delegate: StandardProperty<TYPE>) : RequiredDelegatedProperty<List<TYPE>, StandardProperty<TYPE>>(delegate) {

    override val typeName: String = "List<${delegate.typeName}>"

    override fun valueIn(configuration: Config): List<TYPE> = delegate.extractListValue.invoke(configuration, key)

    override fun validate(target: Config, options: Configuration.Validation.Options?): Valid<Config> {

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

        if (isSensitive) {
            return ConfigValueFactory.fromAnyRef(Configuration.Property.Definition.SENSITIVE_DATA_PLACEHOLDER)
        }
        return delegate.schema?.let { schema -> ConfigValueFactory.fromAnyRef(valueIn(configuration).asSequence().map { element -> element as ConfigObject }.map(ConfigObject::toConfig).map { schema.describe(it) }.toList()) } ?: ConfigValueFactory.fromAnyRef(valueIn(configuration))
    }
}

private class OptionalProperty<TYPE>(delegate: Configuration.Property.Definition.Required<TYPE>, private val defaultValue: TYPE?) : DelegatedProperty<TYPE?, Configuration.Property.Definition.Required<TYPE>>(delegate) {

    override val isMandatory: Boolean = false

    override val typeName: String = "${super.typeName}?"

    override fun describe(configuration: Config) = delegate.describe(configuration)

    override fun valueIn(configuration: Config): TYPE? {

        return when {
            isSpecifiedBy(configuration) -> delegate.valueIn(configuration)
            else -> defaultValue
        }
    }

    override fun validate(target: Config, options: Configuration.Validation.Options?): Valid<Config> {

        val result = delegate.validate(target, options)
        val error = result.errors.asSequence().filterIsInstance<Configuration.Validation.Error.MissingValue>().singleOrNull()
        return when {
            error != null -> if (result.errors.size > 1) result else valid(target)
            else -> result
        }
    }
}

private class FunctionalProperty<TYPE, MAPPED : Any>(delegate: Configuration.Property.Definition.Standard<TYPE>, private val mappedTypeName: String, internal val extractListValue: (Config, String) -> List<TYPE>, private val convert: (TYPE) -> Valid<MAPPED>) : RequiredDelegatedProperty<MAPPED, Configuration.Property.Definition.Standard<TYPE>>(delegate), Configuration.Property.Definition.Standard<MAPPED> {

    override fun valueIn(configuration: Config) = convert.invoke(delegate.valueIn(configuration)).valueOrThrow()

    override val typeName: String = if (super.typeName == "#$mappedTypeName") super.typeName else "$mappedTypeName(${super.typeName})"

    override fun <M : Any> mapValid(mappedTypeName: String, convert: (MAPPED) -> Valid<M>): Configuration.Property.Definition.Standard<M> = FunctionalProperty(delegate, mappedTypeName, extractListValue, { target: TYPE -> this.convert.invoke(target).mapValid(convert) })

    override fun list(): Configuration.Property.Definition.Required<List<MAPPED>> = FunctionalListProperty(this)

    override fun validate(target: Config, options: Configuration.Validation.Options?): Valid<Config> {

        val errors = mutableSetOf<Configuration.Validation.Error>()
        errors += delegate.validate(target, options).errors
        if (errors.isEmpty()) {
            errors += convert.invoke(delegate.valueIn(target)).mapErrors { error -> error.with(delegate.key, mappedTypeName) }.errors
        }
        return Validated.withResult(target, errors)
    }

    override fun describe(configuration: Config) = delegate.describe(configuration)
}

private class FunctionalListProperty<RAW, TYPE : Any>(delegate: FunctionalProperty<RAW, TYPE>) : RequiredDelegatedProperty<List<TYPE>, FunctionalProperty<RAW, TYPE>>(delegate) {

    override val typeName: String = "List<${super.typeName}>"

    override fun valueIn(configuration: Config): List<TYPE> = delegate.extractListValue.invoke(configuration, key).asSequence().map { configObject(key to ConfigValueFactory.fromAnyRef(it)) }.map(ConfigObject::toConfig).map(delegate::valueIn).toList()

    override fun validate(target: Config, options: Configuration.Validation.Options?): Valid<Config> {

        val list = try {
            delegate.extractListValue.invoke(target, key)
        } catch (e: ConfigException) {
            if (isErrorExpected(e)) {
                return invalid(e.toValidationError(key, typeName))
            } else {
                throw e
            }
        }
        val errors = list.asSequence().map { configObject(key to ConfigValueFactory.fromAnyRef(it)) }.mapIndexed { index, value -> delegate.validate(value.toConfig(), options).errors.map { error -> error.withContainingPath(key, "[$index]") } }.reduce { one, other -> one + other }.toSet()
        return Validated.withResult(target, errors)
    }

    override fun describe(configuration: Config): ConfigValue {

        if (isSensitive) {
            return ConfigValueFactory.fromAnyRef(Configuration.Property.Definition.SENSITIVE_DATA_PLACEHOLDER)
        }
        return delegate.schema?.let { schema -> ConfigValueFactory.fromAnyRef(valueIn(configuration).asSequence().map { element -> element as ConfigObject }.map(ConfigObject::toConfig).map { schema.describe(it) }.toList()) } ?: ConfigValueFactory.fromAnyRef(valueIn(configuration))
    }
}

private abstract class DelegatedProperty<TYPE, DELEGATE : Configuration.Property.Metadata>(protected val delegate: DELEGATE) : Configuration.Property.Metadata by delegate, Configuration.Property.Definition<TYPE> {

    final override fun toString() = "\"$key\": \"$typeName\""
}

private abstract class RequiredDelegatedProperty<TYPE, DELEGATE : Configuration.Property.Definition.Required<*>>(delegate: DELEGATE) : DelegatedProperty<TYPE, DELEGATE>(delegate), Configuration.Property.Definition.Required<TYPE> {

    final override fun optional(defaultValue: TYPE?): Configuration.Property.Definition<TYPE?> = OptionalProperty(this, defaultValue)
}

private fun ConfigException.toValidationError(keyName: String, typeName: String): Configuration.Validation.Error {

    val toError = when (this) {
        is ConfigException.Missing -> Configuration.Validation.Error.MissingValue.Companion::of
        is ConfigException.WrongType -> Configuration.Validation.Error.WrongType.Companion::of
        is ConfigException.BadValue -> Configuration.Validation.Error.BadValue.Companion::of
        is ConfigException.BadPath -> Configuration.Validation.Error.BadPath.Companion::of
        is ConfigException.Parse -> Configuration.Validation.Error.MalformedStructure.Companion::of
        else -> throw IllegalStateException("Unsupported ConfigException of type ${this::class.java.name}", this)
    }
    return toError.invoke(message!!, keyName, typeName, emptyList())
}

private fun Configuration.Property.Definition<*>.errorsWhenExtractingValue(target: Config): Set<Configuration.Validation.Error> {

    try {
        valueIn(target)
        return emptySet()
    } catch (exception: ConfigException) {
        if (isErrorExpected(exception)) {
            return setOf(exception.toValidationError(key, typeName))
        }
        throw exception
    }
}

private val expectedExceptionTypes = setOf(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class, ConfigException.BadPath::class, ConfigException.Parse::class)

private fun isErrorExpected(error: ConfigException) = expectedExceptionTypes.any { expected -> expected.isInstance(error) }