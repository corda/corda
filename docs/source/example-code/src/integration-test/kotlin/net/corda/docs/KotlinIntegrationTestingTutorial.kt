package net.corda.docs

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.withoutIssuer
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.Vault
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
import rx.Observable
import java.util.*
import kotlin.test.assertEquals

class KotlinIntegrationTestingTutorial {
    @Test
    fun `alice bob cash exchange example`() {
        // START 1
        driver(DriverParameters(
                startNodesInProcess = true,
                extraCordappPackagesToScan = listOf("net.corda.finance.contracts.asset", "net.corda.finance.schemas")
        )) {
            val aliceUser = User("aliceUser", "testPassword1", permissions = setOf(
                    startFlow<CashIssueAndPaymentFlow>(),
                    invokeRpc("vaultTrackBy")
            ))

            val bobUser = User("bobUser", "testPassword2", permissions = setOf(
                    startFlow<CashPaymentFlow>(),
                    invokeRpc("vaultTrackBy")
            ))

            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(bobUser))
            ).map { it.getOrThrow() }
            // END 1

            // START 2
            val aliceClient = CordaRPCClient(alice.rpcAddress)
            val aliceProxy: CordaRPCOps = aliceClient.start("aliceUser", "testPassword1").proxy

            val bobClient = CordaRPCClient(bob.rpcAddress)
            val bobProxy: CordaRPCOps = bobClient.start("bobUser", "testPassword2").proxy
            // END 2

            // START 3
            val bobVaultUpdates: Observable<Vault.Update<Cash.State>> = bobProxy.vaultTrackBy<Cash.State>().updates
            val aliceVaultUpdates: Observable<Vault.Update<Cash.State>> = aliceProxy.vaultTrackBy<Cash.State>().updates
            // END 3

            // START 4
            val issueRef = OpaqueBytes.of(0)
            aliceProxy.startFlow(::CashIssueAndPaymentFlow,
                    1000.DOLLARS,
                    issueRef,
                    bob.nodeInfo.singleIdentity(),
                    true,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()

            bobVaultUpdates.expectEvents {
                expect { update ->
                    println("Bob got vault update of $update")
                    val amount: Amount<Issued<Currency>> = update.produced.first().state.data.amount
                    assertEquals(1000.DOLLARS, amount.withoutIssuer())
                }
            }
            // END 4

            // START 5
            bobProxy.startFlow(::CashPaymentFlow, 1000.DOLLARS, alice.nodeInfo.singleIdentity()).returnValue.getOrThrow()

            aliceVaultUpdates.expectEvents {
                expect { update ->
                    println("Alice got vault update of $update")
                    val amount: Amount<Issued<Currency>> = update.produced.first().state.data.amount
                    assertEquals(1000.DOLLARS, amount.withoutIssuer())
                }
            }
            // END 5
        }
    }
}
