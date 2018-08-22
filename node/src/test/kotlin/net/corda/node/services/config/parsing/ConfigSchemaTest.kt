package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.lang.reflect.Proxy

class ConfigDefinitionTest {

    @Test
    fun proxy_with_nested_properties() {

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
        println(configuration.serialize())

        val fooConfigSchema = ConfigSchema.withProperties(ConfigProperty.boolean("prop4"), ConfigProperty.double("prop5"))
        val blahConfigSchema = ConfigSchema.withProperties(ConfigProperty.string(prop1), ConfigProperty.int(prop2), ConfigProperty.nested<FooConfig>("prop3", fooConfigSchema))

        val blahConfig: BlahConfig = blahConfigSchema.proxy(configuration)

        assertThat(blahConfig.prop1).isEqualTo(prop1Value)
        assertThat(blahConfig.prop2).isEqualTo(prop2Value)

        val fooConfig = blahConfig.prop3
        assertThat(fooConfig.prop4).isEqualTo(prop4Value)
        assertThat(fooConfig.prop5).isEqualTo(prop5Value)
    }

    // TODO sollecitom write a test with nested properties
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

// TODO sollecitom introduce a ConfigSchema type able to output the structure of an expected config, to validate against a `Config` type, and to proxy it. Try to make this composite an ObjectConfigProperty. Also, it'd be great to have it as a DelegatedProperty as well.

private inline fun <reified TYPE> proxyConfig(configuration: Config, properties: Set<ConfigProperty<*>>): TYPE {

    // TODO sollecitom fix the classloader here.
    return Proxy.newProxyInstance(Thread.currentThread().contextClassLoader, arrayOf(TYPE::class.java), PropertiesInvocationHandler(configuration, properties)) as TYPE
}