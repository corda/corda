package net.corda.common.configuration.parsing.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import net.corda.common.validation.internal.Validated

internal class Schema(override val name: String?, unorderedProperties: Iterable<Configuration.Property.Definition<*>>) : Configuration.Schema {

    override val properties = unorderedProperties.sortedBy(Configuration.Property.Definition<*>::key).toSet()

    init {
        val invalid = properties.groupBy(Configuration.Property.Definition<*>::key).mapValues { entry -> entry.value.size }.filterValues { propertiesForKey -> propertiesForKey > 1 }
        if (invalid.isNotEmpty()) {
            throw IllegalArgumentException("More than one property was found for keys ${invalid.keys.joinToString(", ", "[", "]")}.")
        }
    }

    override fun validate(target: Config, options: Configuration.Options): Valid<Config> {

        val propertyErrors = properties.flatMap { property ->
            property.validate(target, options).errors
        }.toMutableSet()
        if (options.strict) {
            val unknownKeys = target.root().keys - properties.map(Configuration.Property.Definition<*>::key)
            propertyErrors += unknownKeys.map { Configuration.Validation.Error.Unknown.of(it) }
        }
        return Validated.withResult(target, propertyErrors)
    }

    override fun description(): String {

        val description = StringBuilder()
        val root = properties.asSequence().map { it.key to ConfigValueFactory.fromAnyRef(it.typeName) }.fold(configObject()) { config, (key, value) -> config.withValue(key, value) }

        description.append(root.toConfig().serialize())

        val nestedProperties = (properties + properties.flatMap { it.schema?.properties ?: emptySet() }).asSequence().distinctBy(Configuration.Property.Definition<*>::schema)
        nestedProperties.forEach { property ->
            property.schema?.let {
                description.append(System.lineSeparator())
                description.append("${property.typeName}: ")
                description.append(it.description())
                description.append(System.lineSeparator())
            }
        }
        return description.toString()
    }

    override fun describe(configuration: Config, serialiseValue: (Any?) -> ConfigValue, options: Configuration.Options): ConfigValue {

        return properties.asSequence().map { it.key to it.describe(configuration, serialiseValue, options) }.filter { it.second != null }.fold(configObject()) { config, (key, value) -> config.withValue(key, value) }
    }

    override fun equals(other: Any?): Boolean {

        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as Schema

        if (properties != other.properties) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {

        return properties.hashCode()
    }
}