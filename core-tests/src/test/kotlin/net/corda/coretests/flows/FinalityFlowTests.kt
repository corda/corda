package net.corda.coretests.flows

import co.paralleluniverse.strands.concurrent.Semaphore
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import net.corda.core.contracts.Amount
import net.corda.core.contracts.FungibleAsset
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.cordapp.CordappResolver
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
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
import kotlin.test.assertEquals

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
    fun `fail to record locally a not notarised transaction will get the flow hospitalised`() {
        val expectedHospitalizedFlows = mutableSetOf<StateMachineRunId>()
        assertHospitalizedFlows { waitUntilHospitalised ->
            aliceNode.services.vaultService.rawUpdates.subscribe { throw Exception("Error in Observer#onNext") }
            val stx = aliceNode.selfIssuesCash(1000.POUNDS)
            aliceNode.finalise(stx)
                .also { expectedHospitalizedFlows.add(it.id) }
            waitUntilHospitalised.acquire() // wait here until flow gets hospitalised
            expectedHospitalizedFlows
        }
    }

    @Test
    fun `fail to record locally a notarised transaction will get the flow hospitalised - fails with OnErrorNotImplementedException`() {
        val bobNode = createBob(listOf(financeCordapp()))
        val expectedHospitalizedFlows = mutableSetOf<StateMachineRunId>()
        assertHospitalizedFlows { waitUntilHospitalised ->
            val stx = aliceNode.selfIssuesCash(1000.POUNDS)
            aliceNode.finalise(stx)
            aliceNode.services.vaultService.rawUpdates.subscribe { throw Exception("Error in Observer#onNext") }
            val fiber = aliceNode.services.startFlow(CashPaymentFlow(1000.POUNDS, bobNode.info.singleIdentity()))
            // FinalityFlow called from CashPaymentFlow successfully notarised the transaction
            // however, observer code threw an exception while trying to record its states locally. Flow will get hospitalised.
            expectedHospitalizedFlows.add(fiber.id)
            mockNet.runNetwork()
            waitUntilHospitalised.acquire()
            expectedHospitalizedFlows
        }
    }

    @Test
    fun `fail to record locally a notarised transaction will get the flow hospitalised - fails with OnErrorFailedException`() {
        val bobNode = createBob(listOf(financeCordapp()))
        val expectedHospitalizedFlows = mutableSetOf<StateMachineRunId>()
        assertHospitalizedFlows { waitUntilHospitalised ->
            val stx = aliceNode.selfIssuesCash(1000.POUNDS)
            aliceNode.finalise(stx)
            aliceNode.services.vaultService.rawUpdates.subscribe(
                { throw Exception("Error in Observer#onNext") },
                { throw Exception("Error in Observer#onError") })

            val fiber = aliceNode.services.startFlow(CashPaymentFlow(1000.POUNDS, bobNode.info.singleIdentity()))
            expectedHospitalizedFlows.add(fiber.id)
            mockNet.runNetwork()
            waitUntilHospitalised.acquire()
            expectedHospitalizedFlows
        }
    }

    @Test
    fun `transaction gets notarised but fails to record locally then gets hospitalised and retried on node restart without observer`() {
        val bobNode = createBob(listOf(financeCordapp()))
        val expectedHospitalizedFlows = mutableSetOf<StateMachineRunId>()
        assertHospitalizedFlows { waitUntilHospitalised ->
            val stx = aliceNode.selfIssuesCash(1000.POUNDS)
            aliceNode.finalise(stx)
            aliceNode.services.vaultService.rawUpdates.subscribe(
                { throw Exception("Error in Observer#onNext") },
                { throw Exception("Error in Observer#onError") })

            val fiber = aliceNode.services.startFlow(CashPaymentFlow(1000.POUNDS, bobNode.info.singleIdentity()))
            expectedHospitalizedFlows.add(fiber.id)
            mockNet.runNetwork()
            waitUntilHospitalised.acquire()
            // restart aliceNode and the throwing observer does not get subscribed this time
            // CashPaymentFlow will be retried from previous checkpoint, will record states locally and broadcast the transaction to bob
            mockNet.restartNode(aliceNode, parameters = InternalMockNodeParameters(additionalCordapps = listOf(FINANCE_CONTRACTS_CORDAPP)))
            mockNet.runNetwork()
            assertThat(aliceNode.services.vaultService.queryBy<FungibleAsset<*>>().states).isEmpty()
            assertThat(bobNode.services.vaultService.queryBy<Cash.State>().states.single().state.data.amount.quantity == 1000.toLong())
            expectedHospitalizedFlows
        }
    }

    private fun assertHospitalizedFlows(script: (Semaphore) -> Collection<StateMachineRunId>) {
        val waitUntilHospitalised = Semaphore(0)
        val actualHospitalisedFlows = mutableSetOf<StateMachineRunId>()
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { id, _ ->
            actualHospitalisedFlows.add(id)
            waitUntilHospitalised.release() // once hospitalised we can continue
        }
        assertEquals(script(waitUntilHospitalised), actualHospitalisedFlows)
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

    private fun financeCordapp() = cordappForClasses(CashPaymentReceiverFlow::class.java)
}
