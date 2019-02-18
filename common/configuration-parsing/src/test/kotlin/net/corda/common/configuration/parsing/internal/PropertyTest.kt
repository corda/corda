package net.corda.common.configuration.parsing.internal

import com.typesafe.config.ConfigException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class PropertyTest {

    @Test
    fun present_value_with_correct_type() {

        val key = "a.b.c"
        val value = 1L
        val configuration = configObject(key to value).toConfig()
        val options = Configuration.Options.defaults
        val config = configuration.withOptions(options)

        val property = Configuration.Property.Definition.long(key)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration, options)).isEqualTo(value)
        assertThat(config[property]).isEqualTo(value)
    }

    @Test
    fun present_value_with_wrong_type() {

        val key = "a.b.c"
        val value = 1
        val configuration = configObject(key to value).toConfig()

        val property = Configuration.Property.Definition.boolean(key)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThatThrownBy { property.valueIn(configuration, Configuration.Options.defaults) }.isInstanceOf(ConfigException.WrongType::class.java)
    }

    @Test
    fun present_value_of_list_type() {

        val key = "a.b.c"
        val value = listOf(1L, 2L, 3L)
        val configuration = configObject(key to value).toConfig()

        val property = Configuration.Property.Definition.long(key).list()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isEqualTo(value)
    }

    @Test
    fun present_value_of_list_type_with_whole_list_mapping() {

        val key = "a.b.c"
        val value = listOf(1L, 3L, 2L)
        val configuration = configObject(key to value).toConfig()

        val property = Configuration.Property.Definition.long(key).list().map { list -> list.max() }
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isEqualTo(value.max())
    }

    @Test
    fun absent_value_of_list_type_with_whole_list_mapping() {

        val key = "a.b.c"
        val configuration = configObject().toConfig()

        val property = Configuration.Property.Definition.long(key).list().map { list -> list.max() }.optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isEqualTo(null)
    }

    @Test
    fun present_value_of_list_type_with_single_element_and_whole_list_mapping() {

        val key = "a.b.c"
        val value = listOf(1L, 3L, 2L)
        val configuration = configObject(key to value).toConfig()

        val property = Configuration.Property.Definition.long(key).map(::AtomicLong).list().map { list -> list.map(AtomicLong::get).max() }
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isEqualTo(value.max())
    }

    @Test
    fun absent_value_of_list_type_with_single_element_and_whole_list_mapping() {

        val key = "a.b.c"
        val configuration = configObject().toConfig()

        val property = Configuration.Property.Definition.long(key).map(::AtomicLong).list().map { list -> list.map(AtomicLong::get).max() }.optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isEqualTo(null)
    }

    @Test
    fun optional_present_value_of_list_type() {

        val key = "a.b.c"
        val value = listOf(1L, 2L, 3L)
        val configuration = configObject(key to value).toConfig()

        val property = Configuration.Property.Definition.long(key).list().optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isEqualTo(value)
    }

    @Test
    fun optional_absent_value_of_list_type() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property = Configuration.Property.Definition.long(key).list().optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isNull()

    }

    @Test
    fun optional_absent_value_of_list_type_with_default_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val defaultValue = listOf(1L, 2L, 3L)
        val property = Configuration.Property.Definition.long(key).list().optional().withDefaultValue(defaultValue)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isEqualTo(defaultValue)
    }

    @Test
    fun absent_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property = Configuration.Property.Definition.long(key)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThatThrownBy { property.valueIn(configuration, Configuration.Options.defaults) }.isInstanceOf(ConfigException.Missing::class.java)
    }

    @Test
    fun optional_present_value_with_correct_type() {

        val key = "a.b.c"
        val value = 1L
        val configuration = configObject(key to value).toConfig()

        val property = Configuration.Property.Definition.long(key).optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isEqualTo(value)
    }

    @Test
    fun optional_present_value_with_wrong_type() {

        val key = "a.b.c"
        val value = 1
        val configuration = configObject(key to value).toConfig()

        val property = Configuration.Property.Definition.boolean(key).optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThatThrownBy { property.valueIn(configuration, Configuration.Options.defaults) }.isInstanceOf(ConfigException.WrongType::class.java)
    }

    @Test
    fun optional_absent_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property = Configuration.Property.Definition.long(key).optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isNull()
    }

    @Test
    fun optional_absent_with_default_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val defaultValue = 23L
        val property = Configuration.Property.Definition.long(key).optional().withDefaultValue(defaultValue)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isMandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration, Configuration.Options.defaults)).isEqualTo(defaultValue)
    }
}