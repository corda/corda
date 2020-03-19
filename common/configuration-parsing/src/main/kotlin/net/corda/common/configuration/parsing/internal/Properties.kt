package net.corda.common.configuration.parsing.internal

import com.typesafe.config.*
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid

internal class LongProperty(key: String, sensitive: Boolean = false) : StandardProperty<Long>(key, Long::class.javaObjectType.simpleName, { config, path, _ -> config.getLong(path) }, { config, path, _ -> config.getLongList(path) }, sensitive) {

    override fun validate(target: Config, options: Configuration.Options): Valid<Config> {
        val validated = super.validate(target, options)
        if (validated.isValid && target.getValue(key).unwrapped().toString().contains(".")) {
            return invalid(ConfigException.WrongType(target.origin(), key, Long::class.javaObjectType.simpleName, Double::class.javaObjectType.simpleName).toValidationError(key, typeName))
        }
        return validated
    }
}

typealias ValueSelector<T> = (Config, String, Configuration.Options) -> T

internal open class StandardProperty<TYPE : Any>(override val key: String, typeNameArg: String, private val extractSingleValue: ValueSelector<TYPE>, internal val extractListValue: ValueSelector<List<TYPE>>, override val isSensitive: Boolean = false, final override val schema: Configuration.Schema? = null) : Configuration.Property.Definition.Standard<TYPE> {

    override fun valueIn(configuration: Config, options: Configuration.Options) = extractSingleValue.invoke(configuration, key, options)

    override val typeName: String = schema?.let { "#${it.name ?: "Object@$key"}" } ?: typeNameArg

    override fun <MAPPED> mapValid(mappedTypeName: String, convert: (TYPE) -> Valid<MAPPED>): Configuration.Property.Definition.Standard<MAPPED> = FunctionalProperty(this, mappedTypeName, extractListValue, convert)

    override fun optional(): Configuration.Property.Definition.Optional<TYPE> = OptionalDelegatedProperty(this)

    override fun list(): Configuration.Property.Definition.RequiredList<TYPE> = ListProperty(this)

    override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options): ConfigValue {
        if (isSensitive) {
            return valueDescription(Configuration.Property.Definition.SENSITIVE_DATA_PLACEHOLDER, serialiseValue)
        }
        return schema?.describe(configuration.getConfig(key), serialiseValue, options) ?: valueDescription(valueIn(configuration, options), serialiseValue)
    }

    override val isMandatory = true

    override fun validate(target: Config, options: Configuration.Options): Valid<Config> {
        val errors = mutableSetOf<Configuration.Validation.Error>()
        errors += errorsWhenExtractingValue(target, options)
        if (errors.isEmpty()) {
            schema?.let { nestedSchema ->
                val nestedConfig: Config? = target.getConfig(key)
                nestedConfig?.let {
                    errors += nestedSchema.validate(nestedConfig, options).errors.map { error -> error.withContainingPathPrefix(*key.split(".").toTypedArray()) }
                }
            }
        }
        return Validated.withResult(target, errors)
    }

    override fun toString() = "\"$key\": \"$typeName\""
}

private class ListProperty<TYPE : Any>(delegate: StandardProperty<TYPE>) : RequiredDelegatedProperty<List<TYPE>, StandardProperty<TYPE>>(delegate), Configuration.Property.Definition.RequiredList<TYPE> {

    override val typeName: String = "List<${delegate.typeName}>"

    override fun valueIn(configuration: Config, options: Configuration.Options): List<TYPE> = delegate.extractListValue.invoke(configuration, key, options)

    override fun validate(target: Config, options: Configuration.Options): Valid<Config> {
        val errors = mutableSetOf<Configuration.Validation.Error>()
        errors += errorsWhenExtractingValue(target, options)
        if (errors.isEmpty()) {
            delegate.schema?.let { schema ->
                errors += valueIn(target, options).asSequence()
                        .map { element -> element as ConfigObject }
                        .map(ConfigObject::toConfig)
                        .mapIndexed { index, targetConfig -> schema.validate(targetConfig, options).errors.map { error -> error.withContainingPath(*error.containingPath(index).toTypedArray()) } }
                        .fold(emptyList<Configuration.Validation.Error>()) { one, other -> one + other }
                        .toSet()
            }
        }
        return Validated.withResult(target, errors)
    }

    override fun <MAPPED> mapValid(mappedTypeName: String, convert: (List<TYPE>) -> Validated<MAPPED, Configuration.Validation.Error>): Configuration.Property.Definition.Required<MAPPED> = ListMappingProperty(this, mappedTypeName, convert)

    override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options): ConfigValue {
        if (isSensitive) {
            return valueDescription(Configuration.Property.Definition.SENSITIVE_DATA_PLACEHOLDER, serialiseValue)
        }
        return when {
            delegate.schema != null -> {
                val elementsDescription = valueIn(configuration, options).asSequence().map { it as ConfigObject }.map(ConfigObject::toConfig).map { delegate.schema.describe(it, serialiseValue, options) }.toList()
                ConfigValueFactory.fromIterable(elementsDescription)
            }
            else -> valueDescription(valueIn(configuration, options), serialiseValue)
        }
    }

    private fun Configuration.Validation.Error.containingPath(index: Int): List<String> {
        val newContainingPath = listOf(key, "[$index]")
        return when {
            containingPath.size > 1 -> newContainingPath + containingPath.subList(1, containingPath.size)
            else -> newContainingPath
        }
    }
}

private class OptionalPropertyWithDefault<TYPE>(delegate: Configuration.Property.Definition.Optional<TYPE>, private val defaultValue: TYPE) : DelegatedProperty<TYPE, Configuration.Property.Definition.Optional<TYPE>>(delegate) {

    override val isMandatory: Boolean = false

    override val typeName: String = delegate.typeName.removeSuffix("?")

    override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options): ConfigValue? = delegate.describe(configuration, serialiseValue, options) ?: valueDescription(if (isSensitive) Configuration.Property.Definition.SENSITIVE_DATA_PLACEHOLDER else defaultValue, serialiseValue)

    override fun valueIn(configuration: Config, options: Configuration.Options): TYPE = delegate.valueIn(configuration, options) ?: defaultValue

    override fun validate(target: Config, options: Configuration.Options): Valid<Config> = delegate.validate(target, options)
}

private class FunctionalProperty<TYPE, MAPPED>(delegate: Configuration.Property.Definition.Standard<TYPE>, private val mappedTypeName: String, internal val extractListValue: ValueSelector<List<TYPE>>, private val convert: (TYPE) -> Valid<MAPPED>)
    : RequiredDelegatedProperty<MAPPED, Configuration.Property.Definition.Standard<TYPE>>(delegate), Configuration.Property.Definition.Standard<MAPPED> {

    override fun valueIn(configuration: Config, options: Configuration.Options) = convert.invoke(delegate.valueIn(configuration, options)).value()

    override val typeName: String = if (super.typeName == "#$mappedTypeName") super.typeName else "$mappedTypeName(${super.typeName})"

    override fun <M> mapValid(mappedTypeName: String, convert: (MAPPED) -> Valid<M>): Configuration.Property.Definition.Standard<M> = FunctionalProperty(delegate, mappedTypeName, extractListValue, { target: TYPE -> this.convert.invoke(target).mapValid(convert) })

    override fun list(): Configuration.Property.Definition.RequiredList<MAPPED> = FunctionalListProperty(this)

    override fun validate(target: Config, options: Configuration.Options): Valid<Config> {
        val errors = mutableSetOf<Configuration.Validation.Error>()
        errors += delegate.validate(target, options).errors
        if (errors.isEmpty()) {
            errors += convert.invoke(delegate.valueIn(target, options)).mapErrors { error -> error.with(delegate.key, mappedTypeName) }.errors
        }
        return Validated.withResult(target, errors)
    }

    override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options) = delegate.describe(configuration, serialiseValue, options)
}

private class FunctionalListProperty<RAW, TYPE>(delegate: FunctionalProperty<RAW, TYPE>) : RequiredDelegatedProperty<List<TYPE>, FunctionalProperty<RAW, TYPE>>(delegate), Configuration.Property.Definition.RequiredList<TYPE> {

    override val typeName: String = "List<${super.typeName}>"

    override fun valueIn(configuration: Config, options: Configuration.Options): List<TYPE> = delegate.extractListValue.invoke(configuration, key, options).asSequence()
            .map { configObject(key to ConfigValueFactory.fromAnyRef(it)) }
            .map(ConfigObject::toConfig)
            .map { delegate.valueIn(it, options) }
            .toList()

    override fun validate(target: Config, options: Configuration.Options): Valid<Config> {
        val list = try {
            delegate.extractListValue.invoke(target, key, options)
        } catch (e: ConfigException) {
            if (isErrorExpected(e)) {
                return invalid(e.toValidationError(key, typeName))
            } else {
                throw e
            }
        }
        val errors = list.asSequence()
                .map { configObject(key to ConfigValueFactory.fromAnyRef(it)) }
                .mapIndexed { index, value -> delegate.validate(value.toConfig(), options).errors.map { error -> error.withContainingPath(*error.containingPath(index).toTypedArray()) } }
                .fold(emptyList<Configuration.Validation.Error>()) { one, other -> one + other }
                .toSet()
        return Validated.withResult(target, errors)
    }

    private fun Configuration.Validation.Error.containingPath(index: Int): List<String> {
        val newContainingPath = listOf(key, "[$index]")
        return when {
            containingPath.size > 1 -> newContainingPath + containingPath.subList(1, containingPath.size)
            else -> newContainingPath
        }
    }

    override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options): ConfigValue {
        if (isSensitive) {
            return valueDescription(Configuration.Property.Definition.SENSITIVE_DATA_PLACEHOLDER, serialiseValue)
        }
        return delegate.schema?.let { schema -> valueDescription(valueIn(configuration, options).asSequence() .map { element -> valueDescription(element, serialiseValue) } .map { it as ConfigObject } .map(ConfigObject::toConfig) .map { schema.describe(it, serialiseValue, options) } .toList(), serialiseValue) } ?: valueDescription(valueIn(configuration, options), serialiseValue)
    }

    override fun <MAPPED> mapValid(mappedTypeName: String, convert: (List<TYPE>) -> Validated<MAPPED, Configuration.Validation.Error>): Configuration.Property.Definition.Required<MAPPED> = ListMappingProperty(this, mappedTypeName, convert)
}

private abstract class DelegatedProperty<TYPE, DELEGATE : Configuration.Property.Metadata>(protected val delegate: DELEGATE) : Configuration.Property.Metadata by delegate, Configuration.Property.Definition<TYPE> {

    final override fun toString() = "\"$key\": \"$typeName\""
}

private class OptionalDelegatedProperty<TYPE>(private val delegate: Configuration.Property.Definition<TYPE>) : Configuration.Property.Metadata by delegate, Configuration.Property.Definition.Optional<TYPE> {

    override val isMandatory: Boolean = false

    override val typeName: String = "${delegate.typeName}?"

    override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options) = if (isSpecifiedBy(configuration)) delegate.describe(configuration, serialiseValue, options) else null

    override fun valueIn(configuration: Config, options: Configuration.Options): TYPE? {
        return when {
            isSpecifiedBy(configuration) -> delegate.valueIn(configuration, options)
            else -> null
        }
    }

    override fun validate(target: Config, options: Configuration.Options): Valid<Config> {
        val result = delegate.validate(target, options)
        val errors = result.errors
        val missingValueError = errors.asSequence().filterIsInstance<Configuration.Validation.Error.MissingValue>().filter { it.pathAsString == key }.singleOrNull()
        return when {
            missingValueError != null -> if (errors.size > 1) result else valid(target)
            else -> result
        }
    }

    override fun withDefaultValue(defaultValue: TYPE): Configuration.Property.Definition<TYPE> = OptionalPropertyWithDefault(this, defaultValue)

    override fun toString() = "\"$key\": \"$typeName\""
}


private abstract class RequiredDelegatedProperty<TYPE, DELEGATE : Configuration.Property.Definition.Required<*>>(delegate: DELEGATE) : DelegatedProperty<TYPE, DELEGATE>(delegate), Configuration.Property.Definition.Required<TYPE> {

    final override fun optional(): Configuration.Property.Definition.Optional<TYPE> = OptionalDelegatedProperty(this)
}

private class ListMappingProperty<TYPE, MAPPED>(private val delegate: Configuration.Property.Definition.RequiredList<TYPE>, private val mappedTypeName: String, private val convert: (List<TYPE>) -> Validated<MAPPED, Configuration.Validation.Error>) : Configuration.Property.Definition.Required<MAPPED> {

    override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options): ConfigValue? = delegate.describe(configuration, serialiseValue, options)

    override fun valueIn(configuration: Config, options: Configuration.Options) = convert.invoke(delegate.valueIn(configuration, options)).value()

    override fun optional(): Configuration.Property.Definition.Optional<MAPPED> = OptionalDelegatedProperty(this)

    override fun validate(target: Config, options: Configuration.Options): Validated<Config, Configuration.Validation.Error> {
        val errors = mutableSetOf<Configuration.Validation.Error>()
        errors += delegate.validate(target, options).errors
        if (errors.isEmpty()) {
            errors += convert.invoke(delegate.valueIn(target, options)).mapErrors { error -> error.with(delegate.key, mappedTypeName) }.errors
        }
        return Validated.withResult(target, errors)
    }

    override val typeName: String = mappedTypeName

    override val key = delegate.key
    override val isMandatory = delegate.isMandatory
    override val isSensitive = delegate.isSensitive
    override val schema = delegate.schema

    override fun toString() = "\"$key\": \"$typeName\""
}

fun ConfigException.toValidationError(keyName: String? = null, typeName: String): Configuration.Validation.Error {
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

private fun Configuration.Property.Definition<*>.errorsWhenExtractingValue(target: Config, options: Configuration.Options): Set<Configuration.Validation.Error> {
    try {
        valueIn(target, options)
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

private fun valueDescription(value: Any?, serialiseValue: (Any?) -> ConfigValue) = serialiseValue.invoke(value)