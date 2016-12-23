package net.corda.bank

import com.google.common.util.concurrent.Futures
import net.corda.core.contracts.DOLLARS
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.BOC_PARTY_REF
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.node.utilities.getHostAndPort
import net.corda.testing.sequence
import org.junit.Test
import kotlin.test.assertTrue

class BankOfCordaRPCClientTest {
    @Test
    fun `issuer flow via RPC`() {
        driver(dsl = {
            val user = User("user1", "test", permissions = setOf(startFlowPermission<IssuanceRequester>()))
            val (nodeBankOfCorda, nodeBigCorporation) = Futures.allAsList(
                startNode("BankOfCorda", setOf(ServiceInfo(SimpleNotaryService.type)), listOf(user)),
                startNode("BigCorporation", rpcUsers = listOf(user))
            ).getOrThrow()

            // Bank of Corda RPC Client
            val bocClient = nodeBankOfCorda.rpcClientToNode()
            bocClient.start("user1", "test")
            val bocProxy = bocClient.proxy()

            // Big Corporation RPC Client
            val bigCorpClient = nodeBankOfCorda.rpcClientToNode()  // TODO This test is broken as this should be nodeBigCorporation
            bigCorpClient.start("user1", "test")
            val bigCorpProxy = bigCorpClient.proxy()

            // Register for Bank of Corda Vault updates
            val vaultUpdatesBoc = bocProxy.vaultAndUpdates().second

            // Register for Big Corporation Vault updates
            val vaultUpdatesBigCorp = bigCorpProxy.vaultAndUpdates().second

            // Kick-off actual Issuer Flow
            val result = bocProxy.startFlow(
                    ::IssuanceRequester,
                    1000.DOLLARS,
                    nodeBigCorporation.nodeInfo.legalIdentity,
                    BOC_PARTY_REF,
                    nodeBankOfCorda.nodeInfo.legalIdentity).returnValue.toBlocking().first()
            assertTrue { result is SignedTransaction }

            // Check Bank of Corda Vault Updates
            vaultUpdatesBoc.expectEvents {
                sequence(
                        // ISSUE
                        expect { update ->
                            require(update.consumed.isEmpty()) { update.consumed.size }
                            require(update.produced.size == 1) { update.produced.size }
                        },
                        // MOVE
                        expect { update ->
                            require(update.consumed.size == 1) { update.consumed.size }
                            require(update.produced.isEmpty()) { update.produced.size }
                        }
                )
            }

            // Check Big Corporation Vault Updates
            vaultUpdatesBigCorp.expectEvents {
                sequence(
                        // ISSUE
                        expect { update ->
                            require(update.consumed.isEmpty()) { update.consumed.size }
                            require(update.produced.size == 1) { update.produced.size }
                        },
                        // MOVE
                        expect { update ->
                            require(update.consumed.size == 1) { update.consumed.size }
                            require(update.produced.isEmpty()) { update.produced.size }
                        }
                )
            }
        }, isDebug = true)
    }
}
