package net.corda.common.configuration.parsing.internal

import com.typesafe.config.*
import net.corda.common.validation.internal.Validated

// TODO sollecitom try to remove the key from these mapping functions, by perhaps making keyName in Configuration.Validation.Error optional, and contextualizing the produced errors with the key we know here.
inline fun <TYPE, reified MAPPED : Any> Configuration.Property.Definition.Standard<TYPE>.mapValid(noinline convert: (String, TYPE) -> Valid<MAPPED>): Configuration.Property.Definition.Standard<MAPPED> = mapValid(MAPPED::class.java.simpleName, convert)

inline fun <reified ENUM : Enum<ENUM>, VALUE : Any> Configuration.Specification<VALUE>.enum(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = enum(key, ENUM::class, sensitive)

inline fun <TYPE, reified MAPPED : Any> PropertyDelegate.Standard<TYPE>.mapValid(noinline convert: (key: String, typeName: String, TYPE) -> Valid<MAPPED>): PropertyDelegate.Standard<MAPPED> = mapValid(MAPPED::class.java.simpleName, convert)

inline fun <TYPE, reified MAPPED : Any> PropertyDelegate.Standard<TYPE>.map(noinline convert: (key: String, typeName: String, TYPE) -> MAPPED): PropertyDelegate.Standard<MAPPED> = map(MAPPED::class.java.simpleName, convert)

operator fun <TYPE> Config.get(property: Configuration.Property.Definition<TYPE>): TYPE = property.valueIn(this)

fun Configuration.Version.Extractor.parseRequired(config: Config, options: Configuration.Validation.Options = Configuration.Validation.Options.defaults) = parse(config, options).map { it ?: throw IllegalStateException("Absent version value.") }

inline fun <reified NESTED : Any> Configuration.Specification<*>.nested(specification: Configuration.Specification<NESTED>, key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<NESTED> = nestedObject(schema = specification as Configuration.Schema, key = key, sensitive = sensitive).mapValid { _, _, value -> specification.parse(value.toConfig()) }

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