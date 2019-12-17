package net.corda.coretests.flows

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import net.corda.core.contracts.Amount
import net.corda.core.contracts.FungibleAsset
import net.corda.core.flows.FinalityFlow
import net.corda.core.identity.Party
import net.corda.core.internal.cordapp.CordappResolver
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.coretests.flows.WithFinality.FinalityInvoker
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.flows.CashPaymentReceiverFlow
import net.corda.finance.issuedBy
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.testing.core.*
import net.corda.testing.internal.matchers.flow.willReturn
import net.corda.testing.internal.matchers.flow.willThrow
import net.corda.testing.node.internal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FinalityFlowTests : WithFinality {
    companion object {
        private val CHARLIE = TestIdentity(CHARLIE_NAME, 90).party
    }

    override val mockNet = InternalMockNetwork(cordappsForAllNodes = listOf(FINANCE_CONTRACTS_CORDAPP, enclosedCordapp()))

    private val aliceNode = makeNode(ALICE_NAME)

    private val notary = mockNet.defaultNotaryIdentity

    @After
    fun tearDown() = mockNet.stopNodes()

    @Test
    fun `finalise a simple transaction`() {
        val bob = createBob()
        val stx = aliceNode.issuesCashTo(bob)

        assertThat(
                aliceNode.finalise(stx, bob.info.singleIdentity()),
                willReturn(
                        requiredSignatures(1)
                                and visibleTo(bob)))
    }

    @Test
    fun `reject a transaction with unknown parties`() {
        // Charlie isn't part of this network, so node A won't recognise them
        val stx = aliceNode.issuesCashTo(CHARLIE)

        assertThat(
                aliceNode.finalise(stx),
                willThrow<IllegalArgumentException>())
    }

    @Test
    fun `allow use of the old API if the CorDapp target version is 3`() {
        val oldBob = createBob(cordapps = listOf(tokenOldCordapp()))
        val stx = aliceNode.issuesCashTo(oldBob)
        val resultFuture = CordappResolver.withTestCordapp(targetPlatformVersion = 3) {
            @Suppress("DEPRECATION")
            aliceNode.startFlowAndRunNetwork(FinalityFlow(stx)).resultFuture
        }
        resultFuture.getOrThrow()
        assertThat(oldBob.services.validatedTransactions.getTransaction(stx.id)).isNotNull()
    }

    @Test
    fun `broadcasting to both new and old participants`() {
        val newCharlie = mockNet.createNode(InternalMockNodeParameters(legalName = CHARLIE_NAME))
        val oldBob = createBob(cordapps = listOf(tokenOldCordapp()))
        val stx = aliceNode.issuesCashTo(oldBob)
        val resultFuture = aliceNode.startFlowAndRunNetwork(FinalityInvoker(
                stx,
                newRecipients = setOf(newCharlie.info.singleIdentity()),
                oldRecipients = setOf(oldBob.info.singleIdentity())
        )).resultFuture
        resultFuture.getOrThrow()
        assertThat(newCharlie.services.validatedTransactions.getTransaction(stx.id)).isNotNull()
        assertThat(oldBob.services.validatedTransactions.getTransaction(stx.id)).isNotNull()
    }

    @Test
    fun `not notarised transaction will not get hospitalised when fail to record locally`() {
        var observationCounter = 0
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }
        aliceNode.services.vaultService.rawUpdates.subscribe { throw Exception("Error in Observer#onNext") }

        val stx = aliceNode.selfIssuesCash(1000.POUNDS)
        assertThat(
            aliceNode.finalise(stx), // FinalityFlow will try to record this transaction locally without notarising it.
            willThrow<Exception>()
        )
        assertEquals(0, observationCounter)
    }

    @Test
    fun `notarised transaction will get hospitalised when fail to record locally - fails with OnErrorNotImplementedException`() {
        val bobNode = createBob()
        bobNode.registerInitiatedFlow(CashPaymentFlow::class.java, CashPaymentReceiverFlow::class.java)

        var observationCounter = 0
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }

        val stx = aliceNode.selfIssuesCash(1000.POUNDS)
        aliceNode.finalise(stx) // there is no erroneous observer subscribed yet => this will succeed.
        aliceNode.services.vaultService.rawUpdates.subscribe { throw Exception("Error in Observer#onNext") }

        val future = aliceNode.services.startFlow(CashPaymentFlow(1000.POUNDS, bobNode.info.singleIdentity())).resultFuture
        mockNet.runNetwork()

        assertFailsWith<TimeoutException> { future.getOrThrow(5.seconds) }
        assertEquals(1, observationCounter)
    }

    @Test
    fun `notarised transaction will get hospitalised when fail to record locally - fails with OnErrorFailedException`() {
        val bobNode = createBob()
        bobNode.registerInitiatedFlow(CashPaymentFlow::class.java, CashPaymentReceiverFlow::class.java)

        var observationCounter: Int = 0
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }

        val stx = aliceNode.selfIssuesCash(1000.POUNDS)
        aliceNode.finalise(stx) // there is no erroneous observer subscribed yet => this will succeed.
        aliceNode.services.vaultService.rawUpdates.subscribe(
                { throw Exception("Error in Observer#onNext") },
                { throw Exception("Error in Observer#onError") })

        val future = aliceNode.services.startFlow(CashPaymentFlow(1000.POUNDS, bobNode.info.singleIdentity())).resultFuture
        mockNet.runNetwork()

        assertFailsWith<TimeoutException> { future.getOrThrow(5.seconds) }
        assertEquals(1, observationCounter)
    }

    @Test
    fun `notarised but failed to record locally transaction, gets hospitalised and will be retried after observer fix`() {
        val bobNode = createBob()
        bobNode.registerInitiatedFlow(CashPaymentFlow::class.java, CashPaymentReceiverFlow::class.java)

        var observationCounter = 0
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }

        val stx = aliceNode.selfIssuesCash(1000.POUNDS)
        aliceNode.finalise(stx) // there is no erroneous observer subscribed yet => this will succeed.

        aliceNode.services.vaultService.rawUpdates.subscribe(
                { throw Exception("Error in Observer#onNext") },
                { throw Exception("Error in Observer#onError") })

        val future = aliceNode.services.startFlow(CashPaymentFlow(1000.POUNDS, bobNode.info.singleIdentity())).resultFuture
        mockNet.runNetwork()
        assertFailsWith<TimeoutException> { future.getOrThrow(5.seconds) }
        assertEquals(1, observationCounter)

        observationCounter = 0
        mockNet.restartNode(aliceNode, parameters = InternalMockNodeParameters(additionalCordapps = listOf(FINANCE_CONTRACTS_CORDAPP)))
        mockNet.runNetwork()
        assertEquals(0, observationCounter)
        assertThat(aliceNode.services.vaultService.queryBy<FungibleAsset<*>>().states).isEmpty()
        assertThat(bobNode.services.vaultService.queryBy<Cash.State>().states.single().state.data.amount.quantity == 1000.toLong())
    }

    private fun createBob(cordapps: List<TestCordappInternal> = emptyList()): TestStartedNode {
        return mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME, additionalCordapps = cordapps))
    }

    private fun TestStartedNode.issuesCashTo(recipient: TestStartedNode): SignedTransaction {
        return issuesCashTo(recipient.info.singleIdentity())
    }

    private fun TestStartedNode.issuesCashTo(other: Party, _amount: Amount<Currency> = 1000.POUNDS): SignedTransaction {
        val amount = _amount.issuedBy(info.singleIdentity().ref(0))
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, other, notary)
        return services.signInitialTransaction(builder)
    }

    private fun TestStartedNode.selfIssuesCash(amount: Amount<Currency>): SignedTransaction {
        return issuesCashTo(this.info.singleIdentity(), amount)
    }

    /** "Old" CorDapp which will force its node to keep its FinalityHandler enabled */
    private fun tokenOldCordapp() = cordappWithPackages("com.template").copy(targetPlatformVersion = 3)
}
