package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import java.lang.reflect.Proxy

// Additional benefits of going this way:
// - Allows to validate a raw configuration object.
// - Allows to display the structure of the configuration.
interface ConfigSchema : Validator<Config, ConfigValidationError> {

    fun <TYPE> proxy(configuration: Config, type: Class<TYPE>): TYPE

    fun description(): String

    companion object {

        fun withProperties(strict: Boolean = false, properties: Iterable<ConfigProperty<*>>): ConfigSchema = ConfigPropertySchema(strict, properties)

        fun withProperties(strict: Boolean = false, vararg properties: ConfigProperty<*>): ConfigSchema = withProperties(strict, properties.toSet())

        fun withProperties(strict: Boolean = false, builder: ConfigProperty.Companion.() -> Iterable<ConfigProperty<*>>): ConfigSchema = withProperties(strict, builder.invoke(ConfigProperty.Companion))
    }
}

inline fun <reified TYPE> ConfigSchema.proxy(configuration: Config): TYPE = proxy(configuration, TYPE::class.java)

private class ConfigPropertySchema(private val strict: Boolean, unorderedProperties: Iterable<ConfigProperty<*>>) : ConfigSchema {

    private val properties = unorderedProperties.sortedBy(ConfigProperty<*>::key).toSet()

    init {
        val invalid = properties.groupBy(ConfigProperty<*>::key).mapValues { entry -> entry.value.size }.filterValues { propertiesForKey -> propertiesForKey > 1 }
        if (invalid.isNotEmpty()) {
            throw IllegalArgumentException("More than one property was found for keys ${invalid.keys}.")
        }
    }

    override fun <TYPE> proxy(configuration: Config, type: Class<TYPE>): TYPE = createProxy(configuration, type, properties)

    override fun validate(target: Config): Set<ConfigValidationError> {

        val propertyErrors = properties.flatMap { property -> property.validate(target).map { error -> error.withContainingPath(property.contextualize(error.containingPath)) } }.toSet()
        if (strict) {
            val unknownKeys = target.root().keys - properties.map(ConfigProperty<*>::key)
            return propertyErrors + unknownKeys.map(::unknownPropertyError)
        }
        return propertyErrors
    }

    private fun unknownPropertyError(key: String) = ConfigValidationError(key, message = "Unknown configuration key: \"$key\".")

    override fun description(): String {

        val description = StringBuilder()
        var rootDescription = configObject()
        properties.forEach { property ->
            rootDescription = rootDescription.withValue(property.key, ConfigValueFactory.fromAnyRef(typeRef(property)))
        }
        description.append(rootDescription.toConfig().serialize())

        val nestedProperties = (properties + properties.flatMap(::nestedProperties)).filterIsInstance<NestedConfigProperty<*>>().distinctBy(NestedConfigProperty<*>::schema)
        nestedProperties.forEach { property ->
            description.append(System.lineSeparator())
            description.append("${property.typeName}: ")
            description.append(property.schema.description())
            description.append(System.lineSeparator())
        }
        return description.toString()
    }

    private fun nestedProperties(property: ConfigProperty<*>): Set<ConfigProperty<*>> {

        return when (property) {
            is NestedConfigProperty<*> -> (property.schema as ConfigPropertySchema).properties
            else -> emptySet()
        }
    }

    private fun typeRef(property: ConfigProperty<*>): String {

        return if (property is NestedConfigProperty<*>) {
            "#${property.typeName}"
        } else {
            property.typeName
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <TYPE> createProxy(configuration: Config, type: Class<TYPE>, properties: Set<ConfigProperty<*>>): TYPE {

        // TODO sollecitom wrap with with a caching proxy (see how to do this with regards to dynamic values).
        return Proxy.newProxyInstance(Thread.currentThread().contextClassLoader, arrayOf(type), PropertiesInvocationHandler(configuration, properties)) as TYPE
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