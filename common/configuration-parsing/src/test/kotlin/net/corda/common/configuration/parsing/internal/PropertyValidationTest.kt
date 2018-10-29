package net.corda.common.configuration.parsing.internal

import com.typesafe.config.Config
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.common.validation.internal.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PropertyValidationTest {

    @Test
    fun absent_value() {

        val key = "a.b.c"
        val configuration = configObject().toConfig()

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.long(key)

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    @Test
    fun missing_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.long(key)

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    @Test
    fun absent_list_value() {

        val key = "a.b.c"
        val configuration = configObject().toConfig()

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.long(key).list()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    @Test
    fun missing_list_value() {

        val key = "a.b.c"
        val configuration = configObject(key to null).toConfig()

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.long(key).list()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    @Test
    fun wrong_type() {

        val key = "a.b.c"

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.long(key)

        val configuration = configObject(key to false).toConfig()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    @Test
    fun wrong_floating_numeric_type_when_integer_expected() {

        val key = "a.b.c"

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.long(key)

        val configuration = configObject(key to 1.2).toConfig()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    @Test
    fun integer_numeric_type_when_floating_expected_works() {

        val key = "a.b.c"

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.double(key)

        val configuration = configObject(key to 1).toConfig()

        assertThat(property.validate(configuration).isValid).isTrue()
    }

    @Test
    fun wrong_element_type_for_list() {

        val key = "a.b.c"

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.long(key).list()

        val configuration = configObject(key to listOf(false, true)).toConfig()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    @Test
    fun list_type_when_declared_single() {

        val key = "a.b.c"

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.long(key)

        val configuration = configObject(key to listOf(1, 2, 3)).toConfig()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    @Test
    fun single_type_when_declared_list() {

        val key = "a.b.c"

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.long(key).list()

        val configuration = configObject(key to 1).toConfig()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    @Test
    fun wrong_type_in_nested_property() {

        val key = "a.b.c"

        val nestedKey = "d"
        val nestedPropertySchema = Configuration.Schema.withProperties(Configuration.Property.Definition.long(nestedKey))

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.nestedObject(key, nestedPropertySchema)

        val configuration = configObject(key to configObject(nestedKey to false)).toConfig()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.WrongType::class.java) { error ->

                assertThat(error.keyName).isEqualTo(nestedKey)
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray(), nestedKey)
            }
        }
    }

    @Test
    fun absent_value_in_nested_property() {

        val key = "a.b.c"

        val nestedKey = "d"
        val nestedPropertySchema = Configuration.Schema.withProperties(Configuration.Property.Definition.long(nestedKey))

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.nestedObject(key, nestedPropertySchema)

        val configuration = configObject(key to configObject()).toConfig()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(nestedKey)
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray(), nestedKey)
            }
        }
    }

    @Test
    fun missing_value_in_nested_property() {

        val key = "a.b.c"

        val nestedKey = "d"
        val nestedPropertySchema = Configuration.Schema.withProperties(Configuration.Property.Definition.long(nestedKey))

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.nestedObject(key, nestedPropertySchema)

        val configuration = configObject(key to configObject(nestedKey to null)).toConfig()

        assertThat(property.validate(configuration).errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.MissingValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(nestedKey)
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray(), nestedKey)
            }
        }
    }

    @Test
    fun nested_property_without_schema_does_not_validate() {

        val key = "a.b.c"

        val nestedKey = "d"

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.nestedObject(key)

        val configuration = configObject(key to configObject(nestedKey to false)).toConfig()

        assertThat(property.validate(configuration).isValid).isTrue()
    }

    @Test
    fun valid_mapped_property() {

        val key = "a"

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.string(key).mapValid(::parseAddress)

        val host = "localhost"
        val port = 8080
        val value = "$host:$port"

        val configuration = configObject(key to value).toConfig()

        assertThat(property.validate(configuration).isValid).isTrue()
    }

    @Test
    fun invalid_mapped_property() {

        val key = "a.b.c"

        val property: Validator<Config, Configuration.Validation.Error, Configuration.Validation.Options> = Configuration.Property.Definition.string(key).mapValid(::parseAddress)

        val host = "localhost"
        val port = 8080
        // No ":" separating the 2 parts.
        val value = "$host$port"

        val configuration = configObject(key to value).toConfig()

        val result = property.validate(configuration)

        assertThat(result.errors).satisfies { errors ->

            assertThat(errors).hasSize(1)
            assertThat(errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.BadValue::class.java) { error ->

                assertThat(error.keyName).isEqualTo(key.split(".").last())
                assertThat(error.path).containsExactly(*key.split(".").toTypedArray())
            }
        }
    }

    private fun parseAddress(value: String): Valid<Address> {

        return try {
            val parts = value.split(":")
            val host = parts[0].also { require(it.isNotBlank()) }
            val port = parts[1].toInt().also { require(it > 0) }
            valid(Address(host, port))
        } catch (e: Exception) {
            return invalid(Configuration.Validation.Error.BadValue.of("Value must be of format \"host(String):port(Int > 0)\" e.g., \"127.0.0.1:8080\""))
        }
    }
}