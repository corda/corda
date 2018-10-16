package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValueFactory

interface ConfigSchema : Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> {

    val name: String?

    fun description(): String

    fun describe(configuration: Config): ConfigObject

    val properties: Set<ConfigProperty<*>>

    companion object {

        fun withProperties(name: String? = null, properties: Iterable<ConfigProperty<*>>): ConfigSchema = ConfigPropertySchema(name, properties)

        fun withProperties(vararg properties: ConfigProperty<*>, name: String? = null): ConfigSchema = withProperties(name, properties.toSet())

        fun withProperties(name: String? = null, builder: ConfigProperty.Companion.() -> Iterable<ConfigProperty<*>>): ConfigSchema = withProperties(name, builder.invoke(ConfigProperty.Companion))
    }
}

private class ConfigPropertySchema(override val name: String?, unorderedProperties: Iterable<ConfigProperty<*>>) : ConfigSchema {

    override val properties = unorderedProperties.sortedBy(ConfigProperty<*>::key).toSet()

    init {
        val invalid = properties.groupBy(ConfigProperty<*>::key).mapValues { entry -> entry.value.size }.filterValues { propertiesForKey -> propertiesForKey > 1 }
        if (invalid.isNotEmpty()) {
            throw IllegalArgumentException("More than one property was found for keys ${invalid.keys}.")
        }
    }

    override fun validate(target: Config, options: ConfigProperty.ValidationOptions?): Validated<Config, ConfigValidationError> {

        var propertyErrors = properties.flatMap { property -> property.validate(target, options).errors }.toSet()
        if (options?.strict == true) {
            val unknownKeys = target.root().keys - properties.map(ConfigProperty<*>::key)
            propertyErrors += unknownKeys.map(::unknownPropertyError)
        }
        return Validated.withResult(target, propertyErrors)
    }

    private fun unknownPropertyError(key: String) = ConfigValidationError.Unknown.of(key)

    override fun description(): String {

        val description = StringBuilder()
        val root = properties.asSequence().map { it.key to ConfigValueFactory.fromAnyRef(it.typeName) }.fold(configObject()) { config, (key, value) -> config.withValue(key, value) }

        description.append(root.toConfig().serialize())

        val nestedProperties = (properties + properties.flatMap(ConfigProperty<*>::nestedProperties)).asSequence().distinctBy(ConfigProperty<*>::schema)
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

    override fun describe(configuration: Config): ConfigObject {

        var rootDescription = configObject()
        properties.forEach { property ->
            rootDescription = rootDescription.withValue(property.key, property.valueDescriptionIn(configuration))
        }
        return rootDescription
    }

    override fun equals(other: Any?): Boolean {

        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as ConfigPropertySchema

        if (properties != other.properties) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {

        return properties.hashCode()
    }
}