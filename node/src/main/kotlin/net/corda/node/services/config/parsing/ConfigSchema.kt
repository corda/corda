package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory

interface ConfigSchema : Validator<Config, ConfigValidationError> {

    val name: String?

    fun description(): String

    companion object {

        fun withProperties(properties: Iterable<ConfigProperty<*>>, name: String? = null, strict: Boolean = false): ConfigSchema = ConfigPropertySchema(name, strict, properties)

        fun withProperties(vararg properties: ConfigProperty<*>, strict: Boolean = false, name: String? = null): ConfigSchema = withProperties(properties.toSet(), name, strict)

        fun withProperties(strict: Boolean = false, name: String? = null, builder: ConfigProperty.Companion.() -> Iterable<ConfigProperty<*>>): ConfigSchema = withProperties(builder.invoke(ConfigProperty.Companion), name, strict)
    }
}

// TODO sollecitom: move `strict` from field to `validate()` argument
private class ConfigPropertySchema(override val name: String?, private val strict: Boolean, unorderedProperties: Iterable<ConfigProperty<*>>) : ConfigSchema {

    private val properties = unorderedProperties.sortedBy(ConfigProperty<*>::key).toSet()

    init {
        val invalid = properties.groupBy(ConfigProperty<*>::key).mapValues { entry -> entry.value.size }.filterValues { propertiesForKey -> propertiesForKey > 1 }
        if (invalid.isNotEmpty()) {
            throw IllegalArgumentException("More than one property was found for keys ${invalid.keys}.")
        }
    }

    override fun validate(target: Config): Set<ConfigValidationError> {

        val propertyErrors = properties.flatMap { property -> property.validate(target) }.toSet()
        if (strict) {
            val unknownKeys = target.root().keys - properties.map(ConfigProperty<*>::key)
            return propertyErrors + unknownKeys.map(::unknownPropertyError)
        }
        return propertyErrors
    }

    private fun unknownPropertyError(key: String) = ConfigValidationError.Unknown.of(key)

    // TODO sollecitom refactor
    override fun description(): String {

        val description = StringBuilder()
        var rootDescription = configObject()
        properties.forEach { property ->
            rootDescription = rootDescription.withValue(property.key, ConfigValueFactory.fromAnyRef(property.typeName))
        }
        description.append(rootDescription.toConfig().serialize())

        val nestedProperties = (properties + properties.flatMap(::nestedProperties)).asSequence().filterIsInstance<StandardConfigProperty<*>>().filter { it.schema != null }.distinctBy(StandardConfigProperty<*>::schema).toList()
        nestedProperties.forEach { property ->
            description.append(System.lineSeparator())
            description.append("${property.typeName}: ")
            description.append(property.schema!!.description())
            description.append(System.lineSeparator())
        }
        return description.toString()
    }

    // TODO sollecitom refactor
    private fun nestedProperties(property: ConfigProperty<*>): Set<ConfigProperty<*>> {

        return when (property) {
            is StandardConfigProperty<*> -> (property.schema as? ConfigPropertySchema)?.properties ?: emptySet()
            else -> emptySet()
        }
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