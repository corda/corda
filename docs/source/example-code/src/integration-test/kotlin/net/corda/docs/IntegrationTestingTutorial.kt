package net.corda.docs

import net.corda.contracts.asset.Cash
import net.corda.core.concurrent.CordaFuture
import net.corda.core.concurrent.transpose
import net.corda.core.contracts.DOLLARS
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.Vault
import net.corda.core.utilities.OpaqueBytes
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.testing.*
import net.corda.testing.driver.driver
import org.junit.Test
import java.util.*
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class IntegrationTestingTutorial {
    @Test
    fun `alice bob cash exchange example`() {
        // START 1
        driver {
            val aliceUser = User("aliceUser", "testPassword1", permissions = setOf(
                    startFlowPermission<CashIssueFlow>()
            ))
            val bobUser = User("bobUser", "testPassword2", permissions = setOf(
                    startFlowPermission<CashPaymentFlow>()
            ))
            val (alice, bob, notary) = listOf(
                    startNode(ALICE.name, rpcUsers = listOf(aliceUser)),
                    startNode(BOB.name, rpcUsers = listOf(bobUser)),
                    startNode(DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))
            ).transpose().getOrThrow()
            // END 1

            // START 2
            val aliceClient = alice.rpcClientToNode()
            val aliceProxy = aliceClient.start("aliceUser", "testPassword1").proxy

            val bobClient = bob.rpcClientToNode()
            val bobProxy = bobClient.start("bobUser", "testPassword2").proxy

            aliceProxy.waitUntilRegisteredWithNetworkMap().getOrThrow()
            bobProxy.waitUntilRegisteredWithNetworkMap().getOrThrow()
            // END 2

            // START 3
            val bobVaultUpdates = bobProxy.vaultAndUpdates().second
            val aliceVaultUpdates = aliceProxy.vaultAndUpdates().second
            // END 3

            // START 4
            val issueRef = OpaqueBytes.of(0)
            val futures = Stack<CordaFuture<*>>()
            (1..10).map { i ->
                thread {
                    futures.push(aliceProxy.startFlow(::CashIssueFlow,
                            i.DOLLARS,
                            issueRef,
                            bob.nodeInfo.legalIdentity,
                            notary.nodeInfo.notaryIdentity,
                            false // Not anonymised
                    ).returnValue)
                }
            }.forEach(Thread::join) // Ensure the stack of futures is populated.
            futures.forEach { it.getOrThrow() }

            bobVaultUpdates.expectEvents {
                parallel(
                        (1..10).map { i ->
                            expect(
                                    match = { update: Vault.Update ->
                                        (update.produced.first().state.data as Cash.State).amount.quantity == i * 100L
                                    }
                            ) { update ->
                                println("Bob vault update of $update")
                            }
                        }
                )
            }
            // END 4

            // START 5
            for (i in 1..10) {
                bobProxy.startFlow(::CashPaymentFlow, i.DOLLARS, alice.nodeInfo.legalIdentity, false).returnValue.getOrThrow()
            }

            aliceVaultUpdates.expectEvents {
                sequence(
                        (1..10).map { i ->
                            expect { update: Vault.Update ->
                                println("Alice got vault update of $update")
                                assertEquals((update.produced.first().state.data as Cash.State).amount.quantity, i * 100L)
                            }
                        }
                )
            }
        }
    }
}
// END 5
