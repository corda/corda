package net.corda.node.services.config.parsing

import com.typesafe.config.ConfigException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class ConfigPropertyTest {

    @Test
    fun present_value_with_correct_type() {

        val key = "a.b.c"
        val value = 1
        val configuration = configObject(key to value).toConfig()

        val property = ConfigProperty.int(key)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration)).isEqualTo(value)
    }

    @Test
    fun present_value_with_wrong_type() {

        val key = "a.b.c"
        val value = 1
        val configuration = configObject(key to value).toConfig()

        val property = ConfigProperty.boolean(key)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.WrongType::class.java)
    }

    @Test
    fun present_value_of_list_type() {

        val key = "a.b.c"
        val value = listOf(1, 2, 3)
        val configuration = configObject(key to value).toConfig()

        val property = ConfigProperty.int(key).list()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration)).isEqualTo(value)
    }

    @Test
    fun optional_present_value_of_list_type() {

        val key = "a.b.c"
        val value = listOf(1, 2, 3)
        val configuration = configObject(key to value).toConfig()

        val property = ConfigProperty.int(key).list().optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration)).isEqualTo(value)
    }

    @Test
    fun optional_absent_value_of_list_type() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property = ConfigProperty.int(key).list().optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.Missing::class.java)

    }

    @Test
    fun optional_absent_value_of_list_type_with_default_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val defaultValue = listOf(1, 2, 3)
        val property = ConfigProperty.int(key).list().optional(defaultValue)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration)).isEqualTo(defaultValue)
    }

    @Test
    fun absent_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property = ConfigProperty.int(key)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isTrue()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.Missing::class.java)
    }

    @Test
    fun optional_present_value_with_correct_type() {

        val key = "a.b.c"
        val value = 1
        val configuration = configObject(key to value).toConfig()

        val property = ConfigProperty.int(key).optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration)).isEqualTo(value)
    }

    @Test
    fun optional_present_value_with_wrong_type() {

        val key = "a.b.c"
        val value = 1
        val configuration = configObject(key to value).toConfig()

        val property = ConfigProperty.boolean(key).optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.WrongType::class.java)
    }

    @Test
    fun optional_absent_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property = ConfigProperty.int(key).optional()
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.Missing::class.java)
    }

    @Test
    fun optional_absent_with_default_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val defaultValue = 23
        val property = ConfigProperty.int(key).optional(defaultValue)
        println(property)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.mandatory).isFalse()
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration)).isEqualTo(defaultValue)
    }
}