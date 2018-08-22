package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import java.lang.reflect.Proxy

interface ConfigSchema : Validator<Config, ConfigValidationError>, ConfigDefinition {

    fun <TYPE> proxy(configuration: Config, type: Class<TYPE>): TYPE

    companion object {

        fun withProperties(properties: Iterable<ConfigProperty<*>>): ConfigSchema = ConfigPropertySchema(properties)

        fun withProperties(vararg properties: ConfigProperty<*>): ConfigSchema = withProperties(properties.toSet())
    }
}

inline fun <reified TYPE> ConfigSchema.proxy(configuration: Config): TYPE = proxy(configuration, TYPE::class.java)

private class ConfigPropertySchema(unorderedProperties: Iterable<ConfigProperty<*>>) : ConfigSchema {

    private val properties = unorderedProperties.sortedBy(ConfigProperty<*>::key).toSet()

    init {
        val invalid = properties.groupBy(ConfigProperty<*>::key).mapValues { entry -> entry.value.size }.filterValues { propertiesForKey -> propertiesForKey > 1 }
        if (invalid.isNotEmpty()) {
            throw IllegalArgumentException("More than one property was found for keys ${invalid.keys}.")
        }
    }

    override fun <TYPE> proxy(configuration: Config, type: Class<TYPE>): TYPE = createProxy(configuration, type, properties)

    override fun validate(target: Config): Set<ConfigValidationError> {

        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun serialize(options: ConfigRenderOptions): String {

        val representation = TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("UNCHECKED_CAST")
    private fun <TYPE> createProxy(configuration: Config, type: Class<TYPE>, properties: Set<ConfigProperty<*>>): TYPE {

        // TODO sollecitom fix the classloader here.
        return Proxy.newProxyInstance(Thread.currentThread().contextClassLoader, arrayOf(type), PropertiesInvocationHandler(configuration, properties)) as TYPE
    }
}