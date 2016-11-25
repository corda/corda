package net.corda.docs

import net.corda.client.CordaRPCClient
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.issuedBy
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.Vault
import net.corda.core.serialization.OpaqueBytes
import net.corda.flows.CashCommand
import net.corda.flows.CashFlow
import net.corda.flows.CashFlowResult
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.config.configureTestSSL
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.messaging.startFlow
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.parallel
import net.corda.testing.sequence
import org.junit.Test
import kotlin.concurrent.thread

class IntegrationTestingTutorial {

    @Test
    fun aliceBobCashExchangeExample() {
        // START 1
        driver {
            val testUser = User("testUser", "testPassword", permissions = setOf(startFlowPermission<CashFlow>()))
            val aliceFuture = startNode("Alice", rpcUsers = listOf(testUser))
            val bobFuture = startNode("Bob", rpcUsers = listOf(testUser))
            val notaryFuture = startNode("Notary", advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.Companion.type)))
            val alice = aliceFuture.get()
            val bob = bobFuture.get()
            val notary = notaryFuture.get()
            // END 1

            // START 2
            val aliceClient = CordaRPCClient(
                    host = ArtemisMessagingComponent.Companion.toHostAndPort(alice.nodeInfo.address),
                    config = configureTestSSL()
            )
            aliceClient.start("testUser", "testPassword")
            val aliceProxy = aliceClient.proxy()
            val bobClient = CordaRPCClient(
                    host = ArtemisMessagingComponent.Companion.toHostAndPort(bob.nodeInfo.address),
                    config = configureTestSSL()
            )
            bobClient.start("testUser", "testPassword")
            val bobProxy = bobClient.proxy()
            // END 2

            // START 3
            val bobVaultUpdates = bobProxy.vaultAndUpdates().second
            val aliceVaultUpdates = aliceProxy.vaultAndUpdates().second
            // END 3

            // START 4
            val issueRef = OpaqueBytes.Companion.of(0)
            for (i in 1 .. 10) {
                thread {
                    aliceProxy.startFlow(::CashFlow, CashCommand.IssueCash(
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
                val flowHandle = bobProxy.startFlow(::CashFlow, CashCommand.PayCash(
                        amount = i.DOLLARS.issuedBy(alice.nodeInfo.legalIdentity.ref(issueRef)),
                        recipient = alice.nodeInfo.legalIdentity
                ))
                require(flowHandle.returnValue.toBlocking().first() is CashFlowResult.Success)
            }

            aliceVaultUpdates.expectEvents {
                sequence(
                        (1 .. 10).map { i ->
                            expect { update: Vault.Update ->
                                println("Alice got vault update of $update")
                                require((update.produced.first().state.data as Cash.State).amount.quantity == i * 100L)
                            }
                        }
                )
            }
        }
    }
}
// END 5