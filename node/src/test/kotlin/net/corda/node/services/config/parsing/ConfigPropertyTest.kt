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
        val configuration = configOf(key to value).toConfig()

        val property = ConfigProperty.int(key)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration)).isEqualTo(value)
    }

    @Test
    fun present_value_with_wrong_type() {

        val key = "a.b.c"
        val value = 1
        val configuration = configOf(key to value).toConfig()

        val property = ConfigProperty.boolean(key)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.WrongType::class.java)
    }

    @Test
    fun absent_value() {

        val key = "a.b.c"
        val configuration = configOf(key to null).toConfig()

        val property = ConfigProperty.int(key)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.Missing::class.java)
    }

    @Test
    fun map_with_present_value_with_correct_value() {

        val key = "a.b.c"
        val value = 1
        val configuration = configOf(key to value).toConfig()

        val property = ConfigProperty.int(key).map { integer -> integer == value }
        val expectedValue = true

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration)).isEqualTo(expectedValue)
    }

    @Test
    fun map_with_present_value_with_wrong_value() {

        val key = "a.b.c"
        val value = 1
        val configuration = configOf(key to value).toConfig()

        val property = ConfigProperty.boolean(key).map(Boolean::hashCode)

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.WrongType::class.java)
    }

    @Test
    fun map_with_absent_value() {

        val key = "a.b.c"
        val value = 1
        val configuration = configOf(key to null).toConfig()

        val property = ConfigProperty.int(key).map { integer -> integer == value }

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.Missing::class.java)
    }

    @Test
    fun optional_present_value_with_correct_type() {

        val key = "a.b.c"
        val value = 1
        val configuration = configOf(key to value).toConfig()

        val property = ConfigProperty.int(key).optional()

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration)).isEqualTo(value)
    }

    @Test
    fun optional_present_value_with_wrong_type() {

        val key = "a.b.c"
        val value = 1
        val configuration = configOf(key to value).toConfig()

        val property = ConfigProperty.boolean(key).optional()

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.WrongType::class.java)
    }

    @Test
    fun optional_absent_value() {

        val key = "a.b.c"
        val configuration = configOf(key to null).toConfig()

        val property = ConfigProperty.int(key).optional()

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration)).isNull()
    }

    @Test
    fun optional_map_with_present_value_with_correct_value() {

        val key = "a.b.c"
        val value = 1
        val configuration = configOf(key to value).toConfig()

        val property = ConfigProperty.int(key).optional().map { integer -> integer == value }
        val expectedValue = true

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThat(property.valueIn(configuration)).isEqualTo(expectedValue)
    }

    @Test
    fun optional_map_with_present_value_with_wrong_value() {

        val key = "a.b.c"
        val value = 1
        val configuration = configOf(key to value).toConfig()

        val property = ConfigProperty.boolean(key).optional().map { boolean -> boolean?.hashCode() }

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isTrue()
        assertThatThrownBy { property.valueIn(configuration) }.isInstanceOf(ConfigException.WrongType::class.java)
    }

    @Test
    fun optional_map_with_absent_value() {

        val key = "a.b.c"
        val value: Int? = null
        val configuration = configOf(key to null).toConfig()

        val property = ConfigProperty.int(key).optional().map { integer -> integer == value }

        assertThat(property.key).isEqualTo(key)
        assertThat(property.isSpecifiedBy(configuration)).isFalse()
        assertThat(property.valueIn(configuration)).isTrue()
    }
}