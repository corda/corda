package net.corda.common.configuration.parsing.internal

import com.typesafe.config.*
import net.corda.common.validation.internal.Validated

inline fun <TYPE, reified MAPPED : Any> Configuration.Property.Definition.Standard<TYPE>.flatMap(noinline convert: (String, TYPE) -> Valid<MAPPED>): Configuration.Property.Definition.Standard<MAPPED> = flatMap(MAPPED::class.java.simpleName, convert)

inline fun <reified ENUM : Enum<ENUM>, VALUE : Any> Configuration.Specification<VALUE>.enum(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = enum(key, ENUM::class, sensitive)

inline fun <TYPE, reified MAPPED : Any> PropertyDelegate.Standard<TYPE>.flatMap(noinline convert: (key: String, typeName: String, TYPE) -> Valid<MAPPED>): PropertyDelegate.Standard<MAPPED> = flatMap(MAPPED::class.java.simpleName, convert)

inline fun <TYPE, reified MAPPED : Any> PropertyDelegate.Standard<TYPE>.map(noinline convert: (key: String, typeName: String, TYPE) -> MAPPED): PropertyDelegate.Standard<MAPPED> = map(MAPPED::class.java.simpleName, convert)

operator fun <TYPE> Config.get(property: Configuration.Property.Definition<TYPE>): TYPE = property.valueIn(this)

fun Configuration.Version.Extractor.parseRequired(config: Config, options: Configuration.Validation.Options = Configuration.Validation.Options.defaults) = parse(config, options).map { it ?: throw IllegalStateException("Absent version value.") }

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