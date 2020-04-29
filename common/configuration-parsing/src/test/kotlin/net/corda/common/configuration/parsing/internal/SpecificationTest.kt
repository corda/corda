package net.corda.common.configuration.parsing.internal

import com.typesafe.config.Config
import net.corda.common.validation.internal.Validated.Companion.invalid
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class SpecificationTest {

    private object RpcSettingsSpec : Configuration.Specification<RpcSettings>("RpcSettings") {

        private object AddressesSpec : Configuration.Specification<Addresses>("Addresses") {

            val principal by string().mapValid(::parseAddress)
            val admin by string().mapValid(::parseAddress)

            override fun parseValid(configuration: Config, options: Configuration.Options) = configuration.withOptions(options).let { valid(Addresses(it[principal], it[admin])) }

            private fun parseAddress(rawValue: String): Valid<Address> {

                return Address.validFromRawValue(rawValue) { error -> Configuration.Validation.Error.BadValue.of(error) }
            }
        }

        val useSsl by boolean()
        val addresses by nested(AddressesSpec)

        override fun parseValid(configuration: Config, options: Configuration.Options) = configuration.withOptions(options).let { valid<RpcSettings>(RpcSettingsImpl(it[addresses], it[useSsl])) }
    }

    @Test(timeout=300_000)
	fun parse() {

        val useSslValue = true
        val principalAddressValue = Address("localhost", 8080)
        val adminAddressValue = Address("127.0.0.1", 8081)
        val addressesValue = configObject("principal" to "${principalAddressValue.host}:${principalAddressValue.port}", "admin" to "${adminAddressValue.host}:${adminAddressValue.port}")
        val configuration = configObject("useSsl" to useSslValue, "addresses" to addressesValue).toConfig()

        val rpcSettings = RpcSettingsSpec.parse(configuration)

        assertThat(rpcSettings.isValid).isTrue()
        assertThat(rpcSettings.value()).satisfies { value ->

            assertThat(value.useSsl).isEqualTo(useSslValue)
            assertThat(value.addresses).satisfies { addresses ->

                assertThat(addresses.principal).isEqualTo(principalAddressValue)
                assertThat(addresses.admin).isEqualTo(adminAddressValue)
            }
        }
    }

    @Test(timeout=300_000)
	fun parse_list_aggregation() {

        val spec = object : Configuration.Specification<AtomicLong>("AtomicLong") {

            private val maxElement by long("elements").list().map { elements -> elements.max() }

            override fun parseValid(configuration: Config, options: Configuration.Options): Valid<AtomicLong> {
                val config = configuration.withOptions(options)
                return valid(AtomicLong(config[maxElement]!!))
            }
        }

        val elements = listOf(1L, 10L, 2L)
        val configuration = configObject("elements" to elements).toConfig()

        val result = spec.parse(configuration)

        assertThat(result.isValid).isTrue()
        assertThat(result.value().get()).isEqualTo(elements.max())
    }

    @Test(timeout=300_000)
	fun validate() {

        val principalAddressValue = Address("localhost", 8080)
        val adminAddressValue = Address("127.0.0.1", 8081)
        val addressesValue = configObject("principal" to "${principalAddressValue.host}:${principalAddressValue.port}", "admin" to "${adminAddressValue.host}:${adminAddressValue.port}")
        // Here "useSsl" shouldn't be `null`, hence causing the validation to fail.
        val configuration = configObject("useSsl" to null, "addresses" to addressesValue).toConfig()

        val rpcSettings = RpcSettingsSpec.parse(configuration)

        assertThat(rpcSettings.errors).hasSize(1)
        assertThat(rpcSettings.errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.MissingValue::class.java) { error ->

            assertThat(error.path).containsExactly("useSsl")
        }
    }

    @Test(timeout=300_000)
	fun validate_list_aggregation() {

        fun parseMax(elements: List<Long>): Valid<Long> {

            if (elements.isEmpty()) {
                return invalid(Configuration.Validation.Error.BadValue.of("element list cannot be empty"))
            }
            if (elements.any { element -> element <= 1  }) {
                return invalid(Configuration.Validation.Error.BadValue.of("elements cannot be less than or equal to 1"))
            }
            return valid(elements.max()!!)
        }

        val spec = object : Configuration.Specification<AtomicLong>("AtomicLong") {

            private val maxElement by long("elements").list().mapValid(::parseMax)

            override fun parseValid(configuration: Config, options: Configuration.Options): Valid<AtomicLong> {
                val config = configuration.withOptions(options)
                return valid(AtomicLong(config[maxElement]))
            }
        }

        val elements = listOf(1L, 10L, 2L)
        val configuration = configObject("elements" to elements).toConfig()

        val result = spec.parse(configuration)

        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.BadValue::class.java) { error ->

            assertThat(error.path).containsExactly("elements")
            assertThat(error.message).contains("elements cannot be less than or equal to 1")
        }
    }

    @Test(timeout=300_000)
	fun validate_with_domain_specific_errors() {

        val useSslValue = true
        val principalAddressValue = Address("localhost", 8080)
        val adminAddressValue = Address("127.0.0.1", 8081)
        // Here, for the "principal" property, the value is incorrect, as the port value is unacceptable.
        val addressesValue = configObject("principal" to "${principalAddressValue.host}:-10", "admin" to "${adminAddressValue.host}:${adminAddressValue.port}")
        val configuration = configObject("useSsl" to useSslValue, "addresses" to addressesValue).toConfig()

        val rpcSettings = RpcSettingsSpec.parse(configuration)

        assertThat(rpcSettings.errors).hasSize(1)
        assertThat(rpcSettings.errors.first()).isInstanceOfSatisfying(Configuration.Validation.Error.BadValue::class.java) { error ->

            assertThat(error.path).containsExactly("addresses", "principal")
            assertThat(error.keyName).isEqualTo("principal")
            assertThat(error.typeName).isEqualTo(Address::class.java.simpleName)
        }
    }

    @Test(timeout=300_000)
	fun chained_delegated_properties_are_not_added_multiple_times() {

        val spec = object : Configuration.Specification<List<String>?>("Test") {

            @Suppress("unused")
            val myProp by string().list().optional()

            override fun parseValid(configuration: Config, options: Configuration.Options) = configuration.withOptions(options).let { valid(it[myProp]) }
        }

        assertThat(spec.properties).hasSize(1)
    }

    private interface RpcSettings {

        val addresses: Addresses
        val useSsl: Boolean
    }

    private data class RpcSettingsImpl(override val addresses: Addresses, override val useSsl: Boolean) : RpcSettings
}