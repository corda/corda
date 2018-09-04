package net.corda.node

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.internal.errors.AddressBindingException
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

class AddressBindingFailureTests {

    companion object {
        private val portAllocation = PortAllocation.Incremental(20_000)
    }

    @Test
    fun `p2p address`() = assertBindExceptionForOverrides { address -> mapOf("p2pAddress" to address.toString()) }

    @Test
    fun `rpc address`() = assertBindExceptionForOverrides { address -> mapOf("rpcSettings" to mapOf("address" to address.toString())) }

    @Test
    fun `rpc admin address`() = assertBindExceptionForOverrides { address -> mapOf("rpcSettings" to mapOf("adminAddress" to address.toString())) }

    @Test
    fun `H2 address`() = assertBindExceptionForOverrides { address -> mapOf("h2Settings" to mapOf("address" to address.toString()), "dataSourceProperties.dataSource.password" to "password") }

    private fun assertBindExceptionForOverrides(overrides: (NetworkHostAndPort) -> Map<String, Any?>) {

        ServerSocket(0).use { socket ->

            val address = InetSocketAddress(socket.localPort).toNetworkHostAndPort()
            driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), inMemoryDB = false, portAllocation = portAllocation)) {

                assertThatThrownBy { startNode(customOverrides = overrides(address)).getOrThrow() }.isInstanceOfSatisfying(AddressBindingException::class.java) { exception ->
                    assertThat(exception.addresses).contains(address).withFailMessage("Expected addresses to contain $address but was ${exception.addresses}.")
                }
            }
        }
    }

    private fun InetSocketAddress.toNetworkHostAndPort() = NetworkHostAndPort(hostName, port)
}