package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ConfigSpecificationTest {

    private object RpcSettingsSpec : ConfigSpecification("RpcSettings") {

        object AddressesSpec : ConfigSpecification("Addresses") {

            val principal by string().map("NetworkHostAndPort") { key, rawValue ->

                val parts = rawValue.split(":")
                val host = parts[0]
                val port = parts[1].toInt()
                Validated.valid<NetworkHostAndPort, ConfigValidationError>(NetworkHostAndPort(host, port))
            }

            val admin by string().map("NetworkHostAndPort") { key, rawValue ->

                val parts = rawValue.split(":")
                val host = parts[0]
                val port = parts[1].toInt()
                Validated.valid<NetworkHostAndPort, ConfigValidationError>(NetworkHostAndPort(host, port))
            }

            // TODO sollecitom pull in interface
            fun parse(configuration: Config, strict: Boolean): Validated<RpcSettings.Addresses, ConfigValidationError> {

                return schema.validate(configuration, ConfigProperty.ValidationOptions(strict)).map {

                    val principal = principal.valueIn(it)
                    val admin = admin.valueIn(it)
                    RpcSettings.Addresses(principal, admin)
                }
            }
        }

        val useSsl by boolean()

        val addresses by nestedObject(AddressesSpec.schema).map("Addresses") { _, rawValue -> AddressesSpec.parse(rawValue.toConfig(), false) }

        // TODO sollecitom pull in interface
        fun parse(configuration: Config, strict: Boolean): Validated<RpcSettings, ConfigValidationError> {

            return schema.validate(configuration, ConfigProperty.ValidationOptions(strict)).map {

                val useSsl = useSsl.valueIn(it)
                val addresses = addresses.valueIn(it)
                RpcSettingsImpl(addresses, useSsl)
            }
        }
    }

    @Test
    fun parse() {

        val useSslValue = true
        val principalAddressValue = NetworkHostAndPort("localhost", 8080)
        val adminAddressValue = NetworkHostAndPort("127.0.0.1", 8081)
        val addressesValue = configObject("principal" to "${principalAddressValue.host}:${principalAddressValue.port}", "admin" to "${adminAddressValue.host}:${adminAddressValue.port}")
        val configuration = configObject("useSsl" to useSslValue, "addresses" to addressesValue).toConfig()

        val rpcSettings = RpcSettingsSpec.parse(configuration, strict = false)

        assertThat(rpcSettings.isValid).isTrue()
        assertThat(rpcSettings.valueOrThrow()).satisfies { value ->

            assertThat(value.useSsl).isEqualTo(useSslValue)
            assertThat(value.addresses).satisfies { addresses ->

                assertThat(addresses.principal).isEqualTo(principalAddressValue)
                assertThat(addresses.admin).isEqualTo(adminAddressValue)
            }
        }
    }

    private interface RpcSettings {

        val addresses: RpcSettings.Addresses
        val useSsl: Boolean

        data class Addresses(val principal: NetworkHostAndPort, val admin: NetworkHostAndPort)
    }

    private data class RpcSettingsImpl(override val addresses: RpcSettings.Addresses, override val useSsl: Boolean) : RpcSettings

    private data class NetworkHostAndPort(val host: String, val port: Int) {

        init {
            require(host.isNotBlank())
            require(port > 0)
        }
    }
}