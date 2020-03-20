package net.corda.common.configuration.parsing.internal

import com.typesafe.config.*
import net.corda.common.validation.internal.Validated

inline fun <reified ENUM : Enum<ENUM>, VALUE : Any> Configuration.Specification<VALUE>.enum(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = enum(key, ENUM::class, sensitive)

inline fun <TYPE, reified MAPPED> PropertyDelegate.Standard<TYPE>.mapValid(noinline convert: (TYPE) -> Valid<MAPPED>): PropertyDelegate.Standard<MAPPED> = mapValid(MAPPED::class.java.simpleName, convert)

inline fun <TYPE, reified MAPPED> PropertyDelegate.Standard<TYPE>.map(noinline convert: (TYPE) -> MAPPED): PropertyDelegate.Standard<MAPPED> = map(MAPPED::class.java.simpleName, convert)

inline fun <TYPE, reified MAPPED> PropertyDelegate.RequiredList<TYPE>.mapValid(noinline convert: (List<TYPE>) -> Valid<MAPPED>): PropertyDelegate.Required<MAPPED> = mapValid(MAPPED::class.java.simpleName, convert)

inline fun <TYPE, reified MAPPED> PropertyDelegate.RequiredList<TYPE>.map(noinline convert: (List<TYPE>) -> MAPPED): PropertyDelegate.Required<MAPPED> = map(MAPPED::class.java.simpleName, convert)

inline fun <TYPE, reified MAPPED> Configuration.Property.Definition.Standard<TYPE>.mapValid(noinline convert: (TYPE) -> Valid<MAPPED>): Configuration.Property.Definition.Standard<MAPPED> = mapValid(MAPPED::class.java.simpleName, convert)

inline fun <TYPE, reified MAPPED> Configuration.Property.Definition.Standard<TYPE>.map(noinline convert: (TYPE) -> MAPPED): Configuration.Property.Definition.Standard<MAPPED> = map(MAPPED::class.java.simpleName, convert)

inline fun <TYPE, reified MAPPED> Configuration.Property.Definition.RequiredList<TYPE>.mapValid(noinline convert: (List<TYPE>) -> Valid<MAPPED>): Configuration.Property.Definition.Required<MAPPED> = mapValid(MAPPED::class.java.simpleName, convert)

inline fun <TYPE, reified MAPPED> Configuration.Property.Definition.RequiredList<TYPE>.map(noinline convert: (List<TYPE>) -> MAPPED): Configuration.Property.Definition.Required<MAPPED> = map(MAPPED::class.java.simpleName, convert)

fun Config.withOptions(options: Configuration.Options) = ConfigurationWithOptions(this, options)

data class ConfigurationWithOptions(private val config: Config, private val options: Configuration.Options) {
    operator fun <TYPE> get(property: Configuration.Value.Extractor<TYPE>): TYPE = property.valueIn(config, options)
}

inline fun <reified NESTED : Any> Configuration.Specification<*>.nested(specification: Configuration.Specification<NESTED>, key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<NESTED> = nestedObject(schema = specification, key = key, sensitive = sensitive).map(ConfigObject::toConfig).mapValid { value -> specification.parse(value) }

fun <TYPE> Configuration.Property.Definition.Single<TYPE>.listOrEmpty(): Configuration.Property.Definition<List<TYPE>> = list().optional().withDefaultValue(emptyList())

fun <TYPE> PropertyDelegate.Single<TYPE>.listOrEmpty(): PropertyDelegate<List<TYPE>> = list().optional().withDefaultValue(emptyList())

@Suppress("UNCHECKED_CAST")
internal fun configObject(vararg entries: Pair<String, Any?>): ConfigObject {

    var configuration = ConfigFactory.empty()
    entries.forEach { entry ->
        val value = entry.second
        configuration += if (value is Pair<*, *> && value.first is String) {
            (entry.first to (ConfigFactory.empty() + value as Pair<String, Any?>).root())
        } else {
            entry
        }
    }
    return configuration.root()
}

internal operator fun Config.plus(entry: Pair<String, Any?>): Config {

    var value = entry.second ?: return this - entry.first
    if (value is Config) {
        value = value.root()
    }
    return withValue(entry.first, ConfigValueFactory.fromAnyRef(value))
}

internal operator fun Config.minus(key: String): Config {

    return withoutPath(key)
}

internal fun Config.serialize(options: ConfigRenderOptions = ConfigRenderOptions.concise().setFormatted(true).setJson(true)): String = root().serialize(options)

internal fun ConfigValue.serialize(options: ConfigRenderOptions = ConfigRenderOptions.concise().setFormatted(true).setJson(true)): String = render(options)

internal typealias Valid<TARGET> = Validated<TARGET, Configuration.Validation.Error>

internal fun <TYPE> valid(target: TYPE) = Validated.valid<TYPE, Configuration.Validation.Error>(target)

/**
 * Helper interface to mark objects that will have [ConfigurationWithOptions] in them.
 */
interface ConfigurationWithOptionsContainer {
    val configurationWithOptions : ConfigurationWithOptions
}