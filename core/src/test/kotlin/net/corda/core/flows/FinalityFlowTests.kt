package net.corda.core.flows

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assert
import net.corda.testing.internal.matchers.flow.willReturn
import net.corda.testing.internal.matchers.flow.willThrow
import net.corda.core.flows.mixins.WithFinality
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.testing.core.*
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.cordappsForPackages
import org.junit.AfterClass
import org.junit.Test

class FinalityFlowTests : WithFinality {
    companion object {
        private val CHARLIE = TestIdentity(CHARLIE_NAME, 90).party
        private val classMockNet = InternalMockNetwork(cordappsForAllNodes = cordappsForPackages("net.corda.finance.contracts.asset"))

        @JvmStatic
        @AfterClass
        fun tearDown() = classMockNet.stopNodes()
    }

    override val mockNet = classMockNet

    private val aliceNode = makeNode(ALICE_NAME)
    private val bobNode = makeNode(BOB_NAME)

    private val alice = aliceNode.info.singleIdentity()
    private val bob = bobNode.info.singleIdentity()
    private val notary = mockNet.defaultNotaryIdentity

    @Test
    fun `finalise a simple transaction`() {
        val stx = aliceNode.signCashTransactionWith(bob)

        assert.that(
            aliceNode.finalise(stx),
                willReturn(
                        requiredSignatures(1)
                                and visibleTo(bobNode)))
    }

    @Test
    fun `reject a transaction with unknown parties`() {
        // Charlie isn't part of this network, so node A won't recognise them
        val stx = aliceNode.signCashTransactionWith(CHARLIE)

        assert.that(
            aliceNode.finalise(stx),
                willThrow<IllegalArgumentException>())
    }

    private fun TestStartedNode.signCashTransactionWith(other: Party): SignedTransaction {
        val amount = 1000.POUNDS.issuedBy(alice.ref(0))
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, other, notary)

        return services.signInitialTransaction(builder)
    }

}