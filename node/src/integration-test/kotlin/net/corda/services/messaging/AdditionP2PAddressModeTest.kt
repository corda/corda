package net.corda.services.messaging

import com.typesafe.config.ConfigValueFactory
import junit.framework.TestCase.assertEquals
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.withoutIssuer
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.expect
import net.corda.testing.core.expectEvents
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
import java.util.*

class AdditionP2PAddressModeTest {
    private val portAllocation = PortAllocation.Incremental(27182)
    @Test
    fun `runs nodes with one configured to use additionalP2PAddresses`() {
        val testUser = User("test", "test", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true, inMemoryDB = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val mainAddress = portAllocation.nextHostAndPort().toString()
            val altAddress = portAllocation.nextHostAndPort().toString()
            val haConfig = mutableMapOf<String, Any?>()
            haConfig["detectPublicIp"] = false
            haConfig["p2pAddress"] = mainAddress //advertise this as primary
            haConfig["messagingServerAddress"] = altAddress // but actually host on the alternate address
            haConfig["messagingServerExternal"] = false
            haConfig["additionalP2PAddresses"] = ConfigValueFactory.fromIterable(listOf(altAddress)) // advertise this secondary address

            val (nodeA, nodeB) = listOf(
                    startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = listOf(testUser), customOverrides = haConfig),
                    startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(testUser), customOverrides = mapOf("p2pAddress" to portAllocation.nextHostAndPort().toString()))
            ).map { it.getOrThrow() }
            val (nodeARpc, nodeBRpc) = listOf(nodeA, nodeB).map {
                val client = CordaRPCClient(it.rpcAddress)
                client.start(testUser.username, testUser.password).proxy
            }

            val nodeBVaultUpdates = nodeBRpc.vaultTrack(Cash.State::class.java).updates

            val issueRef = OpaqueBytes.of(0.toByte())
            nodeARpc.startFlowDynamic(
                    CashIssueAndPaymentFlow::class.java,
                    DOLLARS(1234),
                    issueRef,
                    nodeB.nodeInfo.legalIdentities.get(0),
                    true,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()
            nodeBVaultUpdates.expectEvents {
                expect { update ->
                    println("Bob got vault update of $update")
                    val amount: Amount<Issued<Currency>> = update.produced.first().state.data.amount
                    assertEquals(1234.DOLLARS, amount.withoutIssuer())
                }
            }
        }
    }
}
