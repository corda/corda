package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import org.assertj.core.api.Assertions.*
import org.junit.Test

class ConfigPropertyValidationTest {

    @Test
    fun absent_value() {

        val key = "a.b.c"
        val configuration = configObject().toConfig()

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.long(key)

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun missing_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.long(key)

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun absent_list_value() {

        val key = "a.b.c"
        val configuration = configObject().toConfig()

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.long(key).list()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun missing_list_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.long(key).list()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun wrong_type() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.long(key)

        val configuration = configObject(key to false).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun wrong_floating_numeric_type_when_integer_expected() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.long(key)

        val configuration = configObject(key to 1.2).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun integer_numeric_type_when_floating_expected_works() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.double(key)

        val configuration = configObject(key to 1).toConfig()

        assertThat(property.isValid(configuration)).isTrue()

        assertThat(property.validate(configuration)).isEmpty()

        val exception = IllegalArgumentException()
        assertThatCode { property.rejectIfInvalid(configuration) { exception } }.doesNotThrowAnyException()
    }

    @Test
    fun wrong_element_type_for_list() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.long(key).list()

        val configuration = configObject(key to listOf(false, true)).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun list_type_when_declared_single() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.long(key)

        val configuration = configObject(key to listOf(1, 2, 3)).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun single_type_when_declared_list() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError> = ConfigProperty.long(key).list()

        val configuration = configObject(key to 1).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }
}