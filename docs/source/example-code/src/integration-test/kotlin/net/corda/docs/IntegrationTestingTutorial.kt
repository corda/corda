package net.corda.docs

import com.google.common.util.concurrent.Futures
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.issuedBy
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.Vault
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.toFuture
import net.corda.flows.CashFlow
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.parallel
import net.corda.testing.sequence
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class IntegrationTestingTutorial {
    @Test
    fun `alice bob cash exchange example`() {
        // START 1
        driver {
            val testUser = User("testUser", "testPassword", permissions = setOf(startFlowPermission<CashFlow>()))
            val (alice, bob, notary) = Futures.allAsList(
                    startNode("Alice", rpcUsers = listOf(testUser)),
                    startNode("Bob", rpcUsers = listOf(testUser)),
                    startNode("Notary", advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))
            ).getOrThrow()
            // END 1

            // START 2
            val aliceClient = alice.rpcClientToNode()

            aliceClient.start("testUser", "testPassword")
            val aliceProxy = aliceClient.proxy()
            val bobClient = bob.rpcClientToNode()

            bobClient.start("testUser", "testPassword")
            val bobProxy = bobClient.proxy()
            // END 2

            // START 3
            val bobVaultUpdates = bobProxy.vaultAndUpdates().second
            val aliceVaultUpdates = aliceProxy.vaultAndUpdates().second
            // END 3

            // START 4
            val issueRef = OpaqueBytes.of(0)
            for (i in 1 .. 10) {
                thread {
                    aliceProxy.startFlow(::CashFlow, CashFlow.Command.IssueCash(
                            amount = i.DOLLARS,
                            issueRef = issueRef,
                            recipient = bob.nodeInfo.legalIdentity,
                            notary = notary.nodeInfo.notaryIdentity
                    ))
                }
            }

            bobVaultUpdates.expectEvents {
                parallel(
                        (1 .. 10).map { i ->
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
            for (i in 1 .. 10) {
                val flowHandle = bobProxy.startFlow(::CashFlow, CashFlow.Command.PayCash(
                        amount = i.DOLLARS.issuedBy(alice.nodeInfo.legalIdentity.ref(issueRef)),
                        recipient = alice.nodeInfo.legalIdentity
                ))
                flowHandle.returnValue.getOrThrow()
            }

            aliceVaultUpdates.expectEvents {
                sequence(
                        (1 .. 10).map { i ->
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
