package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

internal class PropertiesInvocationHandler(private val configuration: Config, properties: Set<ConfigProperty<*>>) : InvocationHandler {

    private val propertyByGetterName = properties.associateBy(::getterName)

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {

        if (args?.isNotEmpty() == true) {
            throw IllegalStateException("PropertiesInvocationHandler can only cover fields, not functions.")
        }
        if (!propertyByGetterName.containsKey(method.name)) {
            throw IllegalStateException("Unmapped key ${method.name}. Known keys are: ${propertyByGetterName.values.map(ConfigProperty<*>::key)}.")
        }
        val property = propertyByGetterName[method.name]!!
        if (property.mandatory && !property.isSpecifiedBy(configuration)) {
            throw IllegalStateException("Unspecified value for mandatory property key ${property.key}.")
        }
        return property.valueIn(configuration)
    }

    private fun getterName(property: ConfigProperty<*>): String = "get${property.key.capitalize()}"
}