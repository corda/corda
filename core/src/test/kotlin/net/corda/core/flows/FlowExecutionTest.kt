package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.vault.DUMMY_LINEAR_CONTRACT_PROGRAM_ID
import net.corda.testing.internal.vault.DummyLinearContract
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import rx.schedulers.Schedulers
import java.time.Instant
import java.util.concurrent.CountDownLatch

class FlowExecutionTest {
    private val mockNet = InternalMockNetwork(listOf(Cash::class.packageName))
    private val nodes = (0..2).map { mockNet.createPartyNode() }
    @After
    fun stopNodes() {
        mockNet.stopNodes()
    }

    companion object {
        private var latch = CountDownLatch(1)
        private var successful = false
    }

    @Test
    fun `can use operations requiring a transaction from another thread`() {
        latch = CountDownLatch(1)
        successful = false

        val initiatingFlow = TestFlow(mockNet.defaultNotaryIdentity)
        val node = nodes[0]

        node.services.validatedTransactions.updates.observeOn(Schedulers.io()).subscribe { update ->
            try {
                node.services.vaultService.queryBy<ContractState>().states
                successful = true
            } finally {
                latch.countDown()
            }
        }

        val flow = node.services.startFlow(initiatingFlow)
        mockNet.runNetwork()
        flow.resultFuture.getOrThrow()
        latch.await()
        assertThat(successful).isTrue()
    }

    class TestFlow(private val notary: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            val builder = TransactionBuilder(notary)
            val issuer = ourIdentity.ref(OpaqueBytes.of(0))
            Cash().generateIssue(builder, 10.DOLLARS.issuedBy(issuer), ourIdentity, notary)
            val stx = serviceHub.signInitialTransaction(builder)
            return subFlow(FinalityFlow(stx))
        }

        private fun createTransaction(): TransactionBuilder {
            return TransactionBuilder(notary = notary).apply {
                addOutputState(DummyLinearContract.State(
                        linearId = UniqueIdentifier(null),
                        participants = listOf(serviceHub.myInfo.singleIdentity()),
                        linearString = "",
                        linearNumber = 0L,
                        linearBoolean = false,
                        linearTimestamp = Instant.now()), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                addCommand(dummyCommand())
            }
        }
    }
}