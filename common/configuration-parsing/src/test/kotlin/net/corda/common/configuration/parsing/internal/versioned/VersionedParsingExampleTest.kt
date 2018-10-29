package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.*
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class VersionedParsingExampleTest {

    @Test
    fun correct_parsing_function_is_used_for_present_version() {

        val versionParser = Configuration.Version.Extractor.fromKey("configuration.metadata.version", null)
        val extractVersion: (Config) -> Valid<Int> = { config -> versionParser.parseRequired(config) }
        val parseConfiguration = VersionedSpecificationRegistry.mapping(extractVersion, 1 to RpcSettingsSpec.V1, 2 to RpcSettingsSpec.V2)

        val principalAddressValue = Address("localhost", 8080)
        val adminAddressValue = Address("127.0.0.1", 8081)

        val configurationV1 = configObject("configuration.metadata.version" to 1, "principalHost" to principalAddressValue.host, "principalPort" to principalAddressValue.port, "adminHost" to adminAddressValue.host, "adminPort" to adminAddressValue.port).toConfig().also { println(it.serialize()) }
        val rpcSettingsFromVersion1Conf = parseConfiguration.invoke(configurationV1).mapValid { it.parse(configurationV1) }

        assertResult(rpcSettingsFromVersion1Conf, principalAddressValue, adminAddressValue)

        val addressesValue = configObject("principal" to "${principalAddressValue.host}:${principalAddressValue.port}", "admin" to "${adminAddressValue.host}:${adminAddressValue.port}")
        val configurationV2 = configObject("configuration.metadata.version" to 2, "configuration.value.addresses" to addressesValue).toConfig().also { println(it.serialize()) }
        val rpcSettingsFromVersion2Conf = parseConfiguration.invoke(configurationV2).mapValid { it.parse(configurationV2) }

        assertResult(rpcSettingsFromVersion2Conf, principalAddressValue, adminAddressValue)
    }

    @Test
    fun default_value_is_used_for_absent_version() {

        val defaultVersion = 2
        val versionParser = Configuration.Version.Extractor.fromKey("configuration.metadata.version", defaultVersion)
        val extractVersion: (Config) -> Valid<Int> = { config -> versionParser.parseRequired(config) }
        val parseConfiguration = VersionedSpecificationRegistry.mapping(extractVersion, 1 to RpcSettingsSpec.V1, 2 to RpcSettingsSpec.V2)

        val principalAddressValue = Address("localhost", 8080)
        val adminAddressValue = Address("127.0.0.1", 8081)

        val addressesValue = configObject("principal" to "${principalAddressValue.host}:${principalAddressValue.port}", "admin" to "${adminAddressValue.host}:${adminAddressValue.port}")
        val configurationV2 = configObject("configuration.value.addresses" to addressesValue).toConfig().also { println(it.serialize()) }
        val rpcSettingsFromVersion2Conf = parseConfiguration.invoke(configurationV2).mapValid { it.parse(configurationV2) }

        assertResult(rpcSettingsFromVersion2Conf, principalAddressValue, adminAddressValue)
    }

    private fun assertResult(result: Valid<RpcSettings>, principalAddressValue: Address, adminAddressValue: Address) {

        assertThat(result.isValid).isTrue()
        assertThat(result.valueOrThrow()).satisfies { value ->

            assertThat(value.principal).isEqualTo(principalAddressValue)
            assertThat(value.admin).isEqualTo(adminAddressValue)
        }
    }

    private data class RpcSettings(val principal: Address, val admin: Address)

    private object RpcSettingsSpec {

        private fun addressFor(host: String, port: Int): Valid<Address> {

            return try {
                require(host.isNotBlank())
                require(port > 0)
                Validated.valid(Address(host, port))
            } catch (e: Exception) {
                return Validated.invalid(Configuration.Validation.Error.BadValue.of(host, Address::class.java.simpleName, "Value must be of format \"host(String):port(Int > 0)\" e.g., \"127.0.0.1:8080\""))
            }
        }

        object V1 : Configuration.Specification<RpcSettings>("RpcSettings") {

            private val principalHost by string()
            private val principalPort by int()

            private val adminHost by string()
            private val adminPort by int()

            override fun parseValid(configuration: Config): Valid<RpcSettings> {

                val principalHost = configuration[principalHost]
                val principalPort = configuration[principalPort]

                val adminHost = configuration[adminHost]
                val adminPort = configuration[adminPort]

                val principalAddress = addressFor(principalHost, principalPort)
                val adminAddress = addressFor(adminHost, adminPort)

                return if (principalAddress.isValid && adminAddress.isValid) {
                    return valid(RpcSettings(principalAddress.value, adminAddress.value))
                } else {
                    invalid(principalAddress.errors + adminAddress.errors)
                }
            }
        }

        object V2 : Configuration.Specification<RpcSettings>("RpcSettings", prefix = "configuration.value") {

            private object AddressesSpec : Configuration.Specification<Addresses>("Addresses") {

                val principal by string().mapValid(::parseAddress)

                val admin by string().mapValid(::parseAddress)

                override fun parseValid(configuration: Config) = valid(Addresses(configuration[principal],configuration[admin]))

                private fun parseAddress(rawValue: String): Valid<Address> {

                    return Address.validFromRawValue(rawValue) { error -> Configuration.Validation.Error.BadValue.of(error) }
                }
            }

            private val addresses by nested(AddressesSpec)

            override fun parseValid(configuration: Config): Valid<RpcSettings> {

                val addresses = configuration[addresses]
                return valid(RpcSettings(addresses.principal, addresses.admin))
            }
        }
    }
}

private fun Configuration.Version.Extractor.parseRequired(config: Config, options: Configuration.Validation.Options = Configuration.Validation.Options.defaults) = parse(config, options).map { it ?: throw IllegalStateException("Absent version value.") }