package net.corda.node

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.internal.errors.AddressBindingException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

class AddressBindingFailureTests: IntegrationTest() {

    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName(), DUMMY_BANK_A_NAME.toDatabaseSchemaName())

        private val portAllocation = PortAllocation.Incremental(20_000)
    }

    @Test
    fun `p2p address`() = assertBindExceptionForOverrides { address -> mapOf("p2pAddress" to address.toString()) }

    @Test
    fun `rpc address`() = assertBindExceptionForOverrides { address -> mapOf("rpcSettings" to mapOf("address" to address.toString())) }

    @Test
    fun `rpc admin address`() = assertBindExceptionForOverrides { address -> mapOf("rpcSettings" to mapOf("adminAddress" to address.toString())) }

    @Test
    fun `H2 address`() {
        assumeTrue(!IntegrationTest.isRemoteDatabaseMode()) // Enterprise only - disable test where running against remote database
        assertBindExceptionForOverrides { address -> mapOf("h2Settings" to mapOf("address" to address.toString()), "dataSourceProperties.dataSource.password" to "password") }
    }

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