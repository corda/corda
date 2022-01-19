package net.corda.common.configuration.parsing.internal

import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValueFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SchemaTest {

    @Test(timeout=300_000)
	fun validation_with_nested_properties() {

        val prop1 = "prop1"
        val prop1Value = "value1"

        val prop2 = "prop2"
        val prop2Value = 3L

        val prop3 = "prop3"
        val prop4 = "prop4"
        val prop4Value = true
        val prop5 = "prop5"
        val prop5Value = -17.3
        val prop3Value = configObject(prop4 to prop4Value, prop5 to prop5Value)

        val configuration = configObject(prop1 to prop1Value, prop2 to prop2Value, prop3 to prop3Value).toConfig()
        println(configuration.serialize())

        val fooConfigSchema = Configuration.Schema.withProperties(name = "Foo") { setOf(boolean("prop4"), double("prop5")) }
        val barConfigSchema = Configuration.Schema.withProperties(name = "Bar") { setOf(string(prop1), long(prop2), nestedObject("prop3", fooConfigSchema)) }

        val result = barConfigSchema.validate(configuration, Configuration.Options.defaults)
        println(barConfigSchema.description())

        assertThat(result.isValid).isTrue()
    }

    @Test(timeout=300_000)
	fun validation_with_unknown_properties() {

        val prop1 = "prop1"
        val prop1Value = "value1"

        val prop2 = "prop2"
        val prop2Value = 3L

        val prop3 = "prop3"
        val prop4 = "prop4"
        val prop4Value = true
        val prop5 = "prop5"
        val prop5Value = -17.3
        // Here "prop6" is not known to the schema.
        val prop3Value = configObject(prop4 to prop4Value, "prop6" to "value6", prop5 to prop5Value)

        // Here "prop4" is not known to the schema.
        val configuration = configObject(prop1 to prop1Value, prop2 to prop2Value, prop3 to prop3Value, "prop4" to "value4").toConfig()
        println(configuration.serialize())

        val fooConfigSchema = Configuration.Schema.withProperties { setOf(boolean("prop4"), double("prop5")) }
        val barConfigSchema = Configuration.Schema.withProperties { setOf(string(prop1), long(prop2), nestedObject("prop3", fooConfigSchema)) }

        val strictErrors = barConfigSchema.validate(configuration, Configuration.Options(strict = true)).errors

        assertThat(strictErrors).hasSize(2)
        assertThat(strictErrors.filter { error -> error.keyName == "prop4" }).hasSize(1)
        assertThat(strictErrors.filter { error -> error.keyName == "prop6" }).hasSize(1)

        val errors = barConfigSchema.validate(configuration, Configuration.Options(strict = false)).errors

        assertThat(errors).isEmpty()

        val errorsWithDefaultOptions = barConfigSchema.validate(configuration, Configuration.Options.defaults).errors

        assertThat(errorsWithDefaultOptions).isEmpty()
    }

    @Test(timeout=300_000)
	fun validation_with_unknown_properties_non_strict() {

        val prop1 = "prop1"
        val prop1Value = "value1"

        val prop2 = "prop2"
        val prop2Value = 3L

        val prop3 = "prop3"
        val prop4 = "prop4"
        val prop4Value = true
        val prop5 = "prop5"
        val prop5Value = -17.3
        // Here "prop6" is not known to the schema, but it is not in strict mode.
        val prop3Value = configObject(prop4 to prop4Value, "prop6" to "value6", prop5 to prop5Value)

        // Here "prop4" is not known to the schema, but it is not in strict mode.
        val configuration = configObject(prop1 to prop1Value, prop2 to prop2Value, prop3 to prop3Value, "prop4" to "value4").toConfig()
        println(configuration.serialize())

        val fooConfigSchema = Configuration.Schema.withProperties { setOf(boolean("prop4"), double("prop5")) }
        val barConfigSchema = Configuration.Schema.withProperties { setOf(string(prop1), long(prop2), nestedObject("prop3", fooConfigSchema)) }

        val result = barConfigSchema.validate(configuration, Configuration.Options.defaults)

        assertThat(result.isValid).isTrue()
    }

    @Test(timeout=300_000)
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

        val fooConfigSchema = Configuration.Schema.withProperties { setOf(boolean("prop4"), double("prop5")) }
        val barConfigSchema = Configuration.Schema.withProperties { setOf(string(prop1), long(prop2), nestedObject("prop3", fooConfigSchema)) }

        val errors = barConfigSchema.validate(configuration, Configuration.Options.defaults).errors
        errors.forEach(::println)

        assertThat(errors).hasSize(2)
    }

    @Test(timeout=300_000)
	fun describe_with_nested_properties_does_not_show_sensitive_values() {

        val prop1 = "prop1"
        val prop1Value = "value1"

        val prop2 = "prop2"
        val prop2Value = 3L

        val prop3 = "prop3"
        val prop4 = "prop4"
        val prop4Value = true
        val prop5 = "prop5"
        val prop5Value = "sensitive!"
        val prop3Value = configObject(prop4 to prop4Value, prop5 to prop5Value)

        val configuration = configObject(prop1 to prop1Value, prop2 to prop2Value, prop3 to prop3Value).toConfig()

        val fooConfigSchema = Configuration.Schema.withProperties(name = "Foo") { setOf(boolean("prop4"), string("prop5", sensitive = true)) }
        val barConfigSchema = Configuration.Schema.withProperties(name = "Bar") { setOf(string(prop1), long(prop2), nestedObject("prop3", fooConfigSchema)) }

        val printedConfiguration = barConfigSchema.describe(configuration, options = Configuration.Options.defaults)

        val description = printedConfiguration.serialize().also { println(it) }

        val descriptionObj = (printedConfiguration as ConfigObject).toConfig()

        assertThat(descriptionObj.getAnyRef("prop3.prop5")).isEqualTo(Configuration.Property.Definition.SENSITIVE_DATA_PLACEHOLDER)
        assertThat(description).doesNotContain(prop5Value)
    }

    @Test(timeout=300_000)
	fun describe_with_nested_properties_list_does_not_show_sensitive_values() {

        val prop1 = "prop1"
        val prop1Value = "value1"

        val prop2 = "prop2"
        val prop2Value = 3L

        val prop3 = "prop3"
        val prop4 = "prop4"
        val prop4Value = true
        val prop5 = "prop5"
        val prop5Value = "sensitive!"
        val prop3Value = ConfigValueFactory.fromIterable(listOf(configObject(prop4 to prop4Value, prop5 to prop5Value), configObject(prop4 to prop4Value, prop5 to prop5Value)))

        val configuration = configObject(prop1 to prop1Value, prop2 to prop2Value, prop3 to prop3Value).toConfig()

        val fooConfigSchema = Configuration.Schema.withProperties(name = "Foo") { setOf(boolean("prop4"), string("prop5", sensitive = true)) }
        val barConfigSchema = Configuration.Schema.withProperties(name = "Bar") { setOf(string(prop1), long(prop2), nestedObject("prop3", fooConfigSchema).list()) }

        val printedConfiguration = barConfigSchema.describe(configuration, options = Configuration.Options.defaults)

        val description = printedConfiguration.serialize().also { println(it) }

        val descriptionObj = (printedConfiguration as ConfigObject).toConfig()

        assertThat(descriptionObj.getObjectList("prop3")).satisfies { objects ->

            objects.forEach { obj ->

                assertThat(obj.toConfig().getString("prop5")).isEqualTo(Configuration.Property.Definition.SENSITIVE_DATA_PLACEHOLDER)
            }
        }
        assertThat(description).doesNotContain(prop5Value)
    }
}