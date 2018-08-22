package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class ConfigDefinitionTest {

    @Test
    fun proxy() {

        val prop1 = "prop1"
        val prop1Value = "value1"

        val prop2 = "prop2"
        val prop2Value = 3

        val prop3 = "prop3"
        val prop4 = "prop4"
        val prop4Value = true
        val prop5 = "prop5"
        val prop5Value = -17.3
        val prop3Value = configOf(prop4 to prop4Value, prop5 to prop5Value)

        val configuration = configOf(prop1 to prop1Value, prop2 to prop2Value, prop3 to prop3Value).toConfig()

        val fooConfigSchema = setOf(ConfigProperty.boolean("prop4"), ConfigProperty.double("prop5"))
        val blahConfigSchema = setOf(ConfigProperty.string(prop1), ConfigProperty.int(prop2), ConfigProperty.value("prop3").map("FooConfig") { value -> proxyConfig<FooConfig>(value.toConfig(), fooConfigSchema) })

        val blahConfig: BlahConfig = proxyConfig(configuration, blahConfigSchema)

        assertThat(blahConfig.prop1).isEqualTo(prop1Value)
        assertThat(blahConfig.prop2).isEqualTo(prop2Value)

        val fooConfig = blahConfig.prop3
        assertThat(fooConfig.prop4).isEqualTo(prop4Value)
        assertThat(fooConfig.prop5).isEqualTo(prop5Value)
    }
}

private interface BlahConfig {

    val prop1: String
    val prop2: Int
    val prop3: FooConfig
}

private interface FooConfig {

    val prop4: Boolean
    val prop5: Double
}

//interface Validatable {
//
//    fun validate():
//}


// TODO sollecitom introduce a ConfigSchema type able to output the structure of an expected config, to validate against a `Config` type, and to proxy it. Try to make this composite an ObjectConfigProperty. Also, it'd be great to have it as a DelegatedProperty as well.

// TODO sollecitom add validation and eager loading.
private class PropertiesInvocationHandler(private val configuration: Config, properties: Set<ConfigProperty<*>>) : InvocationHandler {

    private val propertyByGetterName = properties.associateBy(::getterName)

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {

        if (args?.isNotEmpty() == true) {
            throw IllegalStateException("PropertiesInvocationHandler can only cover fields, not functions.")
        }
        if (!propertyByGetterName.containsKey(method.name)) {
            throw IllegalStateException("Unmapped key ${method.name}. Known keys are: ${propertyByGetterName.values.map(ConfigProperty<*>::key)}.")
        }
        val property = propertyByGetterName[method.name]!!
        // TODO sollecitom turn this into proper validation, rejecting unknown keys when specified.
        if (property.mandatory && !property.isSpecifiedBy(configuration)) {
            throw IllegalStateException("Unspecified value for mandatory property key ${property.key}.")
        }
        return property.valueIn(configuration)
    }

    private fun getterName(property: ConfigProperty<*>): String = "get${property.key.capitalize()}"
}

private inline fun <reified TYPE> proxyConfig(configuration: Config, properties: Set<ConfigProperty<*>>): TYPE {

    // TODO sollecitom fix the classloader here.
    return Proxy.newProxyInstance(Thread.currentThread().contextClassLoader, arrayOf(TYPE::class.java), PropertiesInvocationHandler(configuration, properties)) as TYPE
}