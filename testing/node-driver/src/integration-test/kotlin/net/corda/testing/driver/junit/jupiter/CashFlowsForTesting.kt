package net.corda.testing.driver.junit.jupiter

import net.corda.core.contracts.Amount
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.`issued by`
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.expect
import net.corda.testing.core.expectEvents
import org.assertj.core.api.Assertions.assertThat
import java.util.Currency
import kotlin.test.assertEquals

class CashFlowsForTesting(cordaDriverJunitJupiter: CordaDriverJunitJupiter) {

    private val ref = OpaqueBytes.of(0x01)

    private val bankOfCordaNode = cordaDriverJunitJupiter.getCordaNodeHandlesOrThrow().first {
        it.nodeInfo.legalIdentities.first().name == BOC_NAME
    }

    private val aliceNode = cordaDriverJunitJupiter.getCordaNodeHandlesOrThrow().first {
        it.nodeInfo.legalIdentities.first().name == ALICE_NAME
    }

    private val defaultNotaryIdentity = cordaDriverJunitJupiter.getDefaultCordaNotaryHandleOrThrow().identity

    private val bankOfCorda = bankOfCordaNode.nodeInfo.legalIdentities.first()
    private val alice = aliceNode.nodeInfo.legalIdentities.first()

    fun bankOfCordaIssues(
            amountToIssue: Amount<Currency> = 2000.DOLLARS
    ) {

        bankOfCordaNode.rpc.startFlowDynamic(
                CashIssueFlow::class.java,
                amountToIssue,
                ref,
                defaultNotaryIdentity
        ).returnValue.getOrThrow()
    }

    fun paySomeCash(
            forceException: Boolean = false
    ) {

        val expectedPayment = 500.DOLLARS
        val expectedChange = 1500.DOLLARS

        // Register for vault updates
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val (_, vaultUpdatesBoc) = bankOfCordaNode.rpc.vaultTrackBy<Cash.State>(criteria)
        val (_, vaultUpdatesBankClient) = aliceNode.rpc.vaultTrackBy<Cash.State>(criteria)

        bankOfCordaNode.rpc.startFlowDynamic(
                CashPaymentFlow::class.java,
                expectedPayment,
                alice
        ).returnValue.getOrThrow()

        // Check Bank of Corda vault updates - we take in some issued cash and split it into $500 to the notary
        // and $1,500 back to us, so we expect to consume one state, produce one state for our own vault
        vaultUpdatesBoc.expectEvents {
            expect { (consumed, produced) ->
                if (forceException) {
                    // use this to check whether exceptions are correctly thrown
                    assertThat(consumed).hasSize(2)
                } else {
                    assertThat(consumed).hasSize(1)
                }
                assertThat(produced).hasSize(1)
                val changeState = produced.single().state.data
                assertEquals(expectedChange.`issued by`(bankOfCorda.ref(ref)), changeState.amount)
            }
        }

        // Check notary node vault updates
        vaultUpdatesBankClient.expectEvents {
            expect { (consumed, produced) ->
                assertThat(consumed).isEmpty()
                assertThat(produced).hasSize(1)
                val paymentState = produced.single().state.data
                assertEquals(expectedPayment.`issued by`(bankOfCorda.ref(ref)), paymentState.amount)
            }
        }
    }
}
