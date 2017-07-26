package net.corda.docs

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.DOLLARS
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.testing.driver.driver
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.parallel
import net.corda.testing.sequence
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
            val (alice, bob, notary) = Futures.allAsList(
                    startNode(ALICE.name, rpcUsers = listOf(aliceUser)),
                    startNode(BOB.name, rpcUsers = listOf(bobUser)),
                    startNode(DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))
            ).getOrThrow()
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
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val (_, bobVaultUpdates) = bobProxy.vaultTrackByCriteria<Cash.State>(Cash.State::class.java, criteria)
            val (_, aliceVaultUpdates) = aliceProxy.vaultTrackByCriteria<Cash.State>(Cash.State::class.java, criteria)
            // END 3

            // START 4
            val issueRef = OpaqueBytes.of(0)
            val futures = Stack<ListenableFuture<*>>()
            (1..10).map { i ->
                thread {
                    futures.push(aliceProxy.startFlow(::CashIssueFlow,
                            i.DOLLARS,
                            issueRef,
                            bob.nodeInfo.legalIdentity,
                            notary.nodeInfo.notaryIdentity
                    ).returnValue)
                }
            }.forEach(Thread::join) // Ensure the stack of futures is populated.
            futures.forEach { it.getOrThrow() }

            bobVaultUpdates.expectEvents {
                parallel(
                        (1..10).map { i ->
                            expect(
                                    match = { update: Vault.Update<Cash.State> ->
                                        update.produced.first().state.data.amount.quantity == i * 100L
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
                bobProxy.startFlow(::CashPaymentFlow, i.DOLLARS, alice.nodeInfo.legalIdentity).returnValue.getOrThrow()
            }

            aliceVaultUpdates.expectEvents {
                sequence(
                        (1..10).map { i ->
                            expect { update: Vault.Update<Cash.State> ->
                                println("Alice got vault update of $update")
                                assertEquals(update.produced.first().state.data.amount.quantity, i * 100L)
                            }
                        }
                )
            }
        }
    }
}
// END 5
