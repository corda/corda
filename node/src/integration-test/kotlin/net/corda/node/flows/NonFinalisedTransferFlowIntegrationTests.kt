package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DummyCommandData
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.cordappForClasses
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

/*
   In this test Alice creates a transaction then Bob sign it and Alice does not run the normal FinalityFlow.
   So Bob's knowledge about the transaction is that it got left in partially signed/unverified state.
 */

class NonFinalisedTransferFlowIntegrationTests {
    @StartableByRPC
    @InitiatingFlow
    class NonFinalisedTransferFlow(val party: Party) : FlowLogic<SecureHash>() {
        @Suspendable
        override fun call(): SecureHash {
            val session = initiateFlow(party)
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addOutputState(DummyState(participants = listOf(ourIdentity, party)))
                addCommand(DummyCommandData, ourIdentity.owningKey, party.owningKey)
            }
            val stx = serviceHub.signInitialTransaction(tx)
            val ftx = subFlow(CollectSignaturesFlow(stx, listOf(session)))
            return subFlow(OnlyNotarizingFinalityFlow(ftx)).id
        }
    }

    class OnlyNotarizingFinalityFlow constructor(val transaction: SignedTransaction) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val notarySignatures = subFlow(NotaryFlow.Client(transaction, skipVerification = true))
            val notarised = transaction + notarySignatures
            serviceHub.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(notarised))
            return notarised
        }
    }

    @InitiatedBy(NonFinalisedTransferFlow::class)
    class NonFinalisedTransferResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(object : SignTransactionFlow(otherSide) {
                override fun checkTransaction(stx: SignedTransaction) {
                }
            })
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class QueryUnverifiedTransactionIdsFlow constructor(private val youngerThanTimestamp: Instant,
                                                        private val olderThanTimestamp: Instant) : FlowLogic<List<String>>() {
        @Suspendable
        override fun call() : List<String>{
            var txIds : List<String> = listOf()
            serviceHub.withEntityManager {
                txIds = currentDBSession()
                        .createNamedQuery("DBTransaction.findUnverifiedTransactions")
                        .setParameter("youngerThanTimestamp", youngerThanTimestamp)
                        .setParameter("olderThanTimestamp", olderThanTimestamp)
                        .resultList
                        .map { it as String }
            }
            return txIds
        }
    }

    @Test(timeout = 300_000)
	fun `test that when finalisation is missing the receiving parties save the partially signed transaction as unverified`() {
        val cordappClasses = setOf(NonFinalisedTransferFlow::class.java,
                NonFinalisedTransferResponderFlow::class.java,
                OnlyNotarizingFinalityFlow::class.java,
                QueryUnverifiedTransactionIdsFlow::class.java
        )
        @Suppress("SpreadOperator")
        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, cordappForClasses(*cordappClasses.toTypedArray()))
        )) {

            fun queryUnverifiedTxIds(node: NodeHandle,
                                     youngerThanTimestamp: Instant,
                                     olderThanTimestamp: Instant): List<String> {
                return node.rpc.startFlow(::QueryUnverifiedTransactionIdsFlow, youngerThanTimestamp, olderThanTimestamp).returnValue.getOrThrow()
            }
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME).map { startNode(providedName = it) }.transpose().getOrThrow()

            val startTime = Instant.now()
            val txId = alice.rpc.startFlow(::NonFinalisedTransferFlow, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow()
            val endTime = Instant.now() + 2.seconds

            val aliceUnverifiedTxnIds = queryUnverifiedTxIds(alice, startTime, endTime)
            val bobUnverifiedTxnIds = queryUnverifiedTxIds(bob, startTime, endTime)

            assertEquals(0, aliceUnverifiedTxnIds.size)
            assertEquals(1, bobUnverifiedTxnIds.size)

            assertEquals(txId.toString(), bobUnverifiedTxnIds[0])

        }
    }
}
