package net.corda.bank

import net.corda.core.messaging.startFlow
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.nodeapi.User
import net.corda.testing.*
import net.corda.testing.driver.driver
import org.junit.Test

class BankOfCordaRPCClientTest {
    @Test
    fun `issuer flow via RPC`() {
        driver(extraCordappPackagesToScan = listOf("net.corda.finance"), dsl = {
            val bocManager = User("bocManager", "password1", permissions = setOf(
                    startFlowPermission<CashIssueAndPaymentFlow>()))
            val bigCorpCFO = User("bigCorpCFO", "password2", permissions = emptySet())
            val nodeBankOfCordaFuture = startNotaryNode(BOC.name, rpcUsers = listOf(bocManager), validating = false)
            val nodeBigCorporationFuture = startNode(providedName = BIGCORP_LEGAL_NAME, rpcUsers = listOf(bigCorpCFO))
            val (nodeBankOfCorda, nodeBigCorporation) = listOf(nodeBankOfCordaFuture, nodeBigCorporationFuture).map { it.getOrThrow() }
            val bigCorporation = nodeBankOfCorda.rpc.wellKnownPartyFromX500Name(BIGCORP_LEGAL_NAME)!!

            // Bank of Corda RPC Client
            val bocClient = nodeBankOfCorda.rpcClientToNode()
            val bocProxy = bocClient.start("bocManager", "password1").proxy

            // Big Corporation RPC Client
            val bigCorpClient = nodeBigCorporation.rpcClientToNode()
            val bigCorpProxy = bigCorpClient.start("bigCorpCFO", "password2").proxy
            bocProxy.waitUntilNetworkReady().getOrThrow()
            bigCorpProxy.waitUntilNetworkReady().getOrThrow()

            // Register for Bank of Corda Vault updates
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val vaultUpdatesBoc = bocProxy.vaultTrackByCriteria(Cash.State::class.java, criteria).updates

            // Register for Big Corporation Vault updates
            val vaultUpdatesBigCorp = bigCorpProxy.vaultTrackByCriteria(Cash.State::class.java, criteria).updates

            // Kick-off actual Issuer Flow
            val anonymous = true
            val notary = bocProxy.notaryIdentities().first()
            bocProxy.startFlow(::CashIssueAndPaymentFlow,
                    1000.DOLLARS, BIG_CORP_PARTY_REF,
                    bigCorporation,
                    anonymous,
                    notary).returnValue.getOrThrow()

            // Check Bank of Corda Vault Updates
            vaultUpdatesBoc.expectEvents {
                sequence(
                        // ISSUE
                        expect { update ->
                            require(update.consumed.isEmpty()) { "Expected 0 consumed states, actual: $update" }
                            require(update.produced.size == 1) { "Expected 1 produced states, actual: $update" }
                        },
                        // MOVE
                        expect { update ->
                            require(update.consumed.size == 1) { "Expected 1 consumed states, actual: $update" }
                            require(update.produced.isEmpty()) { "Expected 0 produced states, actual: $update" }
                        }
                )
            }

            // Check Big Corporation Vault Updates
            vaultUpdatesBigCorp.expectEvents {
                sequence(
                        // MOVE
                        expect { (consumed, produced) ->
                            require(consumed.isEmpty()) { consumed.size }
                            require(produced.size == 1) { produced.size }
                        }
                )
            }
        }, isDebug = true)
    }
}
