package net.corda.node.services.config.parsing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class ConfigSchemaTest {

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
        val prop3Value = configObject(prop4 to prop4Value, prop5 to prop5Value)

        val configuration = configObject(prop1 to prop1Value, prop2 to prop2Value, prop3 to prop3Value).toConfig()
        println(configuration.serialize())

        val fooConfigSchema = ConfigSchema.withProperties { setOf(boolean("prop4"), double("prop5")) }
        val barConfigSchema = ConfigSchema.withProperties { setOf(string(prop1), int(prop2), nested<FooConfig>("prop3", fooConfigSchema)) }

        val barConfig: BarConfig = barConfigSchema.proxy(configuration)

        assertThat(barConfig.prop1).isEqualTo(prop1Value)
        assertThat(barConfig.prop2).isEqualTo(prop2Value)

        assertThat(barConfig.prop3.prop4).isEqualTo(prop4Value)
        assertThat(barConfig.prop3.prop5).isEqualTo(prop5Value)
    }

    @Test
    fun validation_with_nested_properties() {

        val prop1 = "prop1"
        val prop1Value = "value1"

        val prop2 = "prop2"
        val prop2Value = 3

        val prop3 = "prop3"
        val prop4 = "prop4"
        val prop4Value = true
        val prop5 = "prop5"
        val prop5Value = -17.3
        val prop3Value = configObject(prop4 to prop4Value, prop5 to prop5Value)

        val configuration = configObject(prop1 to prop1Value, prop2 to prop2Value, prop3 to prop3Value).toConfig()
        println(configuration.serialize())

        val fooConfigSchema = ConfigSchema.withProperties { setOf(boolean("prop4"), double("prop5")) }
        val barConfigSchema = ConfigSchema.withProperties { setOf(string(prop1), int(prop2), nested<FooConfig>("prop3", fooConfigSchema)) }

        val errors = barConfigSchema.validate(configuration)

        assertThat(errors).isEmpty()
        assertThat(barConfigSchema.isValid(configuration)).isTrue()
        assertThatCode { barConfigSchema.rejectIfInvalid(configuration) { _ -> IllegalArgumentException() } }.doesNotThrowAnyException()
    }

    @Test
    fun validation_with_wrong_nested_properties() {

        val prop1 = "prop1"
        val prop1Value = "value1"

        val prop2 = "prop2"
        // This value is wrong, should be an Int.
        val prop2Value = false

        val prop3 = "prop3"
        val prop4 = "prop4"
        // This value is wrong, should be a Boolean.
        val prop4Value = 44444
        val prop5 = "prop5"
        val prop5Value = -17.3
        val prop3Value = configObject(prop4 to prop4Value, prop5 to prop5Value)

        val configuration = configObject(prop1 to prop1Value, prop2 to prop2Value, prop3 to prop3Value).toConfig()
        println(configuration.serialize())

        val fooConfigSchema = ConfigSchema.withProperties { setOf(boolean("prop4"), double("prop5")) }
        val barConfigSchema = ConfigSchema.withProperties { setOf(string(prop1), int(prop2), nested<FooConfig>("prop3", fooConfigSchema)) }

        val errors = barConfigSchema.validate(configuration)
        errors.forEach(::println)

        assertThat(errors).hasSize(2)
        assertThat(barConfigSchema.isValid(configuration)).isFalse()
        assertThatThrownBy { barConfigSchema.rejectIfInvalid(configuration) { _ -> IllegalArgumentException() } }.isInstanceOf(IllegalArgumentException::class.java)
    }

    // TODO sollecitom write tests for `description()`

    private interface BarConfig {

        val prop1: String
        val prop2: Int
        val prop3: FooConfig
    }

    private interface FooConfig {

        val prop4: Boolean
        val prop5: Double
    }
}