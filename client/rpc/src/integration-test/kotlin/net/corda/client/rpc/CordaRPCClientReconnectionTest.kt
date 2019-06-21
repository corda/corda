package net.corda.client.rpc

import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class CordaRPCClientReconnectionTest {

    companion object {
        val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
    }

    @Test
    fun `the rpc client reconnects successfully when the server crashes and restarts`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS, startNodesInProcess = false, inMemoryDB = false)) {
            fun startNode() = startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(CordaRPCClientTest.rpcUser), customOverrides = mapOf("rpcSettings.address" to "localhost:10005")).getOrThrow()

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, CordaRPCClientConfiguration.DEFAULT.copy(
                    maxReconnectAttempts = 5
            ))

            val rpcOps = client.start(rpcUser.username, rpcUser.password, true).proxy
            val networkParameters = rpcOps.networkParameters

            node.stop()
            startNode()

            val networkParametersAfterCrash = rpcOps.networkParameters
            assertThat(networkParameters).isEqualTo(networkParametersAfterCrash)
        }
    }

    @Test
    fun `observables returned by the rpc client continue working when the server crashes and restarts`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS, startNodesInProcess = false, inMemoryDB = false)) {
            val latch = CountDownLatch(2)
            fun startNode() = startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(CordaRPCClientTest.rpcUser), customOverrides = mapOf("rpcSettings.address" to "localhost:10005")).getOrThrow()

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, CordaRPCClientConfiguration.DEFAULT.copy(
                    maxReconnectAttempts = 5
            ))

            val rpcOps = client.start(rpcUser.username, rpcUser.password).proxy
            val cashStatesFeed = rpcOps.vaultTrack(Cash.State::class.java)
            cashStatesFeed.updates.subscribe { latch.countDown() }
            rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

            node.stop()
            startNode()

            rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

            assertTrue {
                latch.await(2, TimeUnit.SECONDS)
            }
        }
    }

}