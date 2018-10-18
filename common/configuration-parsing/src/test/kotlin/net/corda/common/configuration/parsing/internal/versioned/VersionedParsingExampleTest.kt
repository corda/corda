package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import net.corda.common.configuration.parsing.internal.*
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class VersionedParsingExampleTest {

    @Test
    fun correct_parsing_function_is_used_for_present_version() {

        val versionParser = Configuration.Version.Extractor.fromKey("configuration.metadata.version", null)
        val defaultVersion = null
        val parser = VersionedConfigurationParser.mapping(versionParser, defaultVersion, 1 to RpcSettingsSpec.V1, 2 to RpcSettingsSpec.V2)

        val principalAddressValue = Address("localhost", 8080)
        val adminAddressValue = Address("127.0.0.1", 8081)

        val configurationV1 = configObject("configuration.metadata.version" to 1, "principalHost" to principalAddressValue.host, "principalPort" to principalAddressValue.port, "adminHost" to adminAddressValue.host, "adminPort" to adminAddressValue.port).toConfig()
        val rpcSettingsFromVersion1Conf = parser.parse(configurationV1, Configuration.Validation.Options(strict = false))

        assertResult(rpcSettingsFromVersion1Conf, principalAddressValue, adminAddressValue)

        val addressesValue = configObject("principal" to "${principalAddressValue.host}:${principalAddressValue.port}", "admin" to "${adminAddressValue.host}:${adminAddressValue.port}")
        val configurationV2 = configObject("configuration.metadata.version" to 2, "configuration.value.addresses" to addressesValue).toConfig()
        val rpcSettingsFromVersion2Conf = parser.parse(configurationV2, Configuration.Validation.Options(strict = false))

        assertResult(rpcSettingsFromVersion2Conf, principalAddressValue, adminAddressValue)
    }

    @Test
    fun default_version_parsing_function_is_used_for_absent_version() {

        val versionParser = Configuration.Version.Extractor.fromKey("configuration.metadata.version", null)
        val defaultVersion = 1
        val parser = VersionedConfigurationParser.mapping(versionParser, defaultVersion, defaultVersion to RpcSettingsSpec.V1)

        val principalAddressValue = Address("localhost", 8080)
        val adminAddressValue = Address("127.0.0.1", 8081)

        val configurationWithoutVersion = configObject("principalHost" to principalAddressValue.host, "principalPort" to principalAddressValue.port, "adminHost" to adminAddressValue.host, "adminPort" to adminAddressValue.port).toConfig()
        val rpcSettings = parser.parse(configurationWithoutVersion, Configuration.Validation.Options(strict = false))

        assertResult(rpcSettings, principalAddressValue, adminAddressValue)
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
            private val principalPort by long().mapRaw { _, _, value -> value.toInt() }

            private val adminHost by string()
            private val adminPort by long().mapRaw { _, _, value -> value.toInt() }

            override fun parseValid(configuration: Config): Valid<RpcSettings> {

                val principalHost = principalHost.valueIn(configuration)
                val principalPort = principalPort.valueIn(configuration)

                val adminHost = adminHost.valueIn(configuration)
                val adminPort = adminPort.valueIn(configuration)

                val principalAddress = addressFor(principalHost, principalPort)
                val adminAddress = addressFor(adminHost, adminPort)

                return if (principalAddress.isValid && adminAddress.isValid) {
                    return valid(RpcSettings(principalAddress.valueIfValid!!, adminAddress.valueIfValid!!))
                } else {
                    invalid(principalAddress.errors + adminAddress.errors)
                }
            }
        }

        object V2 : Configuration.Specification<RpcSettings>("RpcSettings") {

            private object AddressesSpec : Configuration.Specification<Addresses>("Addresses") {

                val principal by string().map { key, typeName, rawValue -> Address.validFromRawValue(rawValue) { error -> Configuration.Validation.Error.BadValue.of(key, typeName, error) as Configuration.Validation.Error } }

                val admin by string().map { key, typeName, rawValue -> Address.validFromRawValue(rawValue) { error -> Configuration.Validation.Error.BadValue.of(key, typeName, error) as Configuration.Validation.Error } }

                override fun parseValid(configuration: Config) = valid(Addresses(principal.valueIn(configuration), admin.valueIn(configuration)))

                @Suppress("UNUSED_PARAMETER")
                fun parse(key: String, typeName: String, rawValue: ConfigObject): Valid<Addresses> = parse(rawValue.toConfig(), Configuration.Validation.Options(strict = false))
            }

            private val addresses by nestedObject(AddressesSpec, "configuration.value.addresses").map(AddressesSpec::parse)

            override fun parseValid(configuration: Config): Valid<RpcSettings> {

                val addresses = addresses.valueIn(configuration)
                return valid(RpcSettings(addresses.principal, addresses.admin))
            }
        }
    }
}