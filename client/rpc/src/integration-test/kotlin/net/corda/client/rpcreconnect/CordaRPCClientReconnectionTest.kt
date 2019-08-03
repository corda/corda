package net.corda.client.rpcreconnect

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.CordaRPCClientTest
import net.corda.client.rpc.internal.ReconnectingCordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordaRPCClientReconnectionTest {

    private val portAllocator = incrementalPortAllocation()

    companion object {
        val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
    }

    @Test
    fun `rpc client calls and returned observables continue working when the server crashes and restarts`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS, startNodesInProcess = false, inMemoryDB = false)) {
            val latch = CountDownLatch(2)
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())

            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, CordaRPCClientConfiguration.DEFAULT.copy(
                    maxReconnectAttempts = 5
            ))

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = true).proxy as ReconnectingCordaRPCOps).use {
                val rpcOps = it
                val networkParameters = rpcOps.networkParameters
                val cashStatesFeed = rpcOps.vaultTrack(Cash.State::class.java)
                cashStatesFeed.updates.subscribe { latch.countDown() }
                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                node.stop()
                startNode()

                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                val networkParametersAfterCrash = rpcOps.networkParameters
                assertThat(networkParameters).isEqualTo(networkParametersAfterCrash)
                assertTrue {
                    latch.await(2, TimeUnit.SECONDS)
                }
            }
        }
    }

    @Test
    fun `a client can successfully unsubscribe a reconnecting observable`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS, startNodesInProcess = false, inMemoryDB = false)) {
            val latch = CountDownLatch(2)
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())

            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, CordaRPCClientConfiguration.DEFAULT.copy(
                    maxReconnectAttempts = 5
            ))

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = true).proxy as ReconnectingCordaRPCOps).use {
                val rpcOps = it
                val cashStatesFeed = rpcOps.vaultTrack(Cash.State::class.java)
                val subscription = cashStatesFeed.updates.subscribe { latch.countDown() }
                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                node.stop()
                startNode()

                subscription.unsubscribe()

                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                assertFalse {
                    latch.await(4, TimeUnit.SECONDS)
                }
            }
        }
    }

    @Test
    fun `rpc client calls and returned observables continue working when there is failover between servers`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS, startNodesInProcess = false, inMemoryDB = false)) {
            val latch = CountDownLatch(2)

            fun startNode(address: NetworkHostAndPort): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val addresses = listOf(NetworkHostAndPort("localhost", portAllocator.nextPort()), NetworkHostAndPort("localhost", portAllocator.nextPort()))

            val node = startNode(addresses[0])
            val client = CordaRPCClient(addresses, CordaRPCClientConfiguration.DEFAULT.copy(
                    maxReconnectAttempts = 5
            ))

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = true).proxy as ReconnectingCordaRPCOps).use {
                val rpcOps = it
                val networkParameters = rpcOps.networkParameters
                val cashStatesFeed = rpcOps.vaultTrack(Cash.State::class.java)
                cashStatesFeed.updates.subscribe { latch.countDown() }
                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                node.stop()
                startNode(addresses[1])

                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                val networkParametersAfterCrash = rpcOps.networkParameters
                assertThat(networkParameters).isEqualTo(networkParametersAfterCrash)
                assertTrue {
                    latch.await(2, TimeUnit.SECONDS)
                }
            }
        }
    }

}