package net.corda.node

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.errors.AddressBindingException
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

class NodeStartupTests {

    @Test
    fun `node throws detailed exception if it cannot bind to p2p address`() {

        ServerSocket(0).use { socket ->

            val address = InetSocketAddress(socket.localPort).toNetworkHostAndPort()
            val overrides = mapOf("p2pAddress" to address.toString())
            driver(DriverParameters(startNodesInProcess = true)) {

                assertThatThrownBy { startNode(customOverrides = overrides).getOrThrow() }.isInstanceOfSatisfying(AddressBindingException::class.java) { exception ->
                    assertThat(exception.addresses).containsExactly(address).withFailMessage("Expected addresses to contain exactly $address but was ${exception.addresses}.")
                }
            }
        }
    }

    @Test
    fun `node throws detailed exception if it cannot bind to any rpc address`() {

        ServerSocket(0).use { socket ->

            val address = InetSocketAddress(socket.localPort).toNetworkHostAndPort()

            val overrides = listOf<(NetworkHostAndPort) -> Map<String, Any?>>(
                    { mapOf("rpcSettings" to mapOf("address" to it.toString())) },
                    { mapOf("rpcSettings" to mapOf("adminAddress" to it.toString())) }
            )

            driver(DriverParameters(startNodesInProcess = true)) {

                overrides.forEach { override ->
                    assertThatThrownBy { startNode(customOverrides = override(address)).getOrThrow() }.isInstanceOfSatisfying(AddressBindingException::class.java) { exception ->
                        assertThat(exception.addresses).contains(address).withFailMessage("Expected addresses to contain $address but was ${exception.addresses}.")
                    }
                }
            }
        }
    }

    private fun InetSocketAddress.toNetworkHostAndPort() = NetworkHostAndPort(hostName, port)
}