package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
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
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import rx.schedulers.Schedulers
import java.util.concurrent.CountDownLatch

class ServiceHubConcurrentUsageTest {

    private val mockNet = InternalMockNetwork(cordappsForAllNodes = cordappsForPackages("net.corda.finance.schemas", "net.corda.node.services.vault.VaultQueryExceptionsTests", Cash::class.packageName))

    @After
    fun stopNodes() {
        mockNet.stopNodes()
    }

    @Test
    fun `operations requiring a transaction work from another thread`() {

        val latch = CountDownLatch(1)
        var successful = false
        val initiatingFlow = TestFlow(mockNet.defaultNotaryIdentity)
        val node = mockNet.createPartyNode()

        node.services.validatedTransactions.updates.observeOn(Schedulers.io()).subscribe { _ ->
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
    }
}