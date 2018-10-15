package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import org.assertj.core.api.Assertions.*
import org.junit.Test

class ConfigPropertyValidationTest {

    @Test
    fun absent_value() {

        val key = "a.b.c"
        val configuration = configObject().toConfig()

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.long(key)

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
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

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.long(key)

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
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

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.long(key).list()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
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

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.long(key).list()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun wrong_type() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.long(key)

        val configuration = configObject(key to false).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun wrong_floating_numeric_type_when_integer_expected() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.long(key)

        val configuration = configObject(key to 1.2).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun integer_numeric_type_when_floating_expected_works() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.double(key)

        val configuration = configObject(key to 1).toConfig()

        assertThat(property.isValid(configuration)).isTrue()

        assertThat(property.validate(configuration)).isEmpty()

        val exception = IllegalArgumentException()
        assertThatCode { property.rejectIfInvalid(configuration) { exception } }.doesNotThrowAnyException()
    }

    @Test
    fun wrong_element_type_for_list() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.long(key).list()

        val configuration = configObject(key to listOf(false, true)).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun list_type_when_declared_single() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.long(key)

        val configuration = configObject(key to listOf(1, 2, 3)).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun single_type_when_declared_list() {

        val key = "a.b.c"

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.long(key).list()

        val configuration = configObject(key to 1).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun wrong_type_in_nested_property() {

        val key = "a.b.c"

        val nestedKey = "d"
        val nestedPropertySchema = ConfigSchema.withProperties(ConfigProperty.long(nestedKey))

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.value(key, nestedPropertySchema)

        val configuration = configObject(key to configObject(nestedKey to false)).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(nestedKey)
                assertThat(error.path).containsExactly(key, nestedKey)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun absent_value_in_nested_property() {

        val key = "a.b.c"

        val nestedKey = "d"
        val nestedPropertySchema = ConfigSchema.withProperties(ConfigProperty.long(nestedKey))

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.value(key, nestedPropertySchema)

        val configuration = configObject(key to configObject()).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(nestedKey)
                assertThat(error.path).containsExactly(key, nestedKey)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun missing_value_in_nested_property() {

        val key = "a.b.c"

        val nestedKey = "d"
        val nestedPropertySchema = ConfigSchema.withProperties(ConfigProperty.long(nestedKey))

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.value(key, nestedPropertySchema)

        val configuration = configObject(key to configObject(nestedKey to null)).toConfig()

        assertThat(property.isValid(configuration)).isFalse()

        fun assertErrors(errors: Iterable<ConfigValidationError>) {

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(ConfigValidationError.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(nestedKey)
                // TODO sollecitom here
                assertThat(error.path).containsExactly(key, nestedKey)
            }
        }

        assertThat(property.validate(configuration)).satisfies(::assertErrors)

        val exception = IllegalArgumentException()
        assertThatThrownBy { property.rejectIfInvalid(configuration) { errors -> exception.also { assertErrors(errors) } } }.isSameAs(exception)
    }

    @Test
    fun nested_property_without_schema_does_not_validate() {

        val key = "a.b.c"

        val nestedKey = "d"

        val property: Validator<Config, ConfigValidationError, ConfigProperty.ValidationOptions> = ConfigProperty.value(key)

        val configuration = configObject(key to configObject(nestedKey to false)).toConfig()

        assertThat(property.isValid(configuration)).isTrue()

        assertThat(property.validate(configuration)).isEmpty()

        val exception = IllegalArgumentException()
        assertThatCode { property.rejectIfInvalid(configuration) { exception } }.doesNotThrowAnyException()
    }
}