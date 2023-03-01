package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import net.corda.core.contracts.Amount
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.TransactionStatus
import net.corda.core.identity.Party
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.coretesting.internal.matchers.flow.willReturn
import net.corda.coretesting.internal.matchers.flow.willThrow
import net.corda.coretests.flows.WithFinality.FinalityInvoker
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.issuedBy
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.MOCK_VERSION_INFO
import net.corda.testing.node.internal.TestCordappInternal
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class FinalityFlowTests : WithFinality {
    companion object {
        private val CHARLIE = TestIdentity(CHARLIE_NAME, 90).party
    }

    override val mockNet = InternalMockNetwork(cordappsForAllNodes = setOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP, DUMMY_CONTRACTS_CORDAPP, enclosedCordapp(),
                                                       CustomCordapp(targetPlatformVersion = 3, classes = setOf(FinalityFlow::class.java))))

    private val aliceNode = makeNode(ALICE_NAME)

    private val notary = mockNet.defaultNotaryIdentity

    @After
    fun tearDown() = mockNet.stopNodes()

    @Test(timeout=300_000)
	fun `finalise a simple transaction`() {
        val bob = createBob()
        val stx = aliceNode.issuesCashTo(bob)

        assertThat(
                aliceNode.finalise(stx, bob.info.singleIdentity()),
                willReturn(
                        requiredSignatures(1)
                                and visibleTo(bob)))
    }

    @Test(timeout=300_000)
	fun `reject a transaction with unknown parties`() {
        // Charlie isn't part of this network, so node A won't recognise them
        val stx = aliceNode.issuesCashTo(CHARLIE)

        assertThat(
                aliceNode.finalise(stx),
                willThrow<IllegalArgumentException>())
    }

    @Test(timeout=300_000)
	fun `allow use of the old API if the CorDapp target version is 3`() {
        val oldBob = createBob(cordapps = listOf(tokenOldCordapp()))
        val stx = aliceNode.issuesCashTo(oldBob)
        @Suppress("DEPRECATION")
        aliceNode.startFlowAndRunNetwork(FinalityFlow(stx)).resultFuture.getOrThrow()
        assertThat(oldBob.services.validatedTransactions.getTransaction(stx.id)).isNotNull
    }

    @Test(timeout=300_000)
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
        assertThat(newCharlie.services.validatedTransactions.getTransaction(stx.id)).isNotNull
        assertThat(oldBob.services.validatedTransactions.getTransaction(stx.id)).isNotNull
    }

    @Test(timeout=300_000)
    fun `two phase finality flow transaction`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        val stx = aliceNode.startFlow(CashIssueFlow(Amount(1000L, GBP), OpaqueBytes.of(1), notary)).resultFuture.getOrThrow().stx
        aliceNode.startFlowAndRunNetwork(CashPaymentFlow(Amount(100, GBP), bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        assertThat(aliceNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
        assertThat(bobNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
    }

    @Test(timeout=300_000)
    fun `two phase finality flow initiator to pre-2PF peer`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY - 1)

        val stx = aliceNode.startFlow(CashIssueFlow(Amount(1000L, GBP), OpaqueBytes.of(1), notary)).resultFuture.getOrThrow().stx
        aliceNode.startFlowAndRunNetwork(CashPaymentFlow(Amount(100, GBP), bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        assertThat(aliceNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
        assertThat(bobNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
    }

    @Test(timeout=300_000)
    fun `pre-2PF initiator to two phase finality flow peer`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY - 1)

        val stx = bobNode.startFlow(CashIssueFlow(Amount(1000L, GBP), OpaqueBytes.of(1), notary)).resultFuture.getOrThrow().stx
        bobNode.startFlowAndRunNetwork(CashPaymentFlow(Amount(100, GBP), aliceNode.info.singleIdentity())).resultFuture.getOrThrow()

        assertThat(aliceNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
        assertThat(bobNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
    }

    @Test(timeout=300_000)
    fun `two phase finality flow double spend transaction`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        val ref = aliceNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        val stx = aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionInternal(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionInternal(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBob)

        try {
            aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            val stxId = (e.error as NotaryError.Conflict).txId
            val (_, txnDsStatusAlice) = aliceNode.services.validatedTransactions.getTransactionInternal(stxId) ?: fail()
            assertEquals(TransactionStatus.MISSING_NOTARY_SIG, txnDsStatusAlice)
            val (_, txnDsStatusBob) = bobNode.services.validatedTransactions.getTransactionInternal(stxId) ?: fail()
            assertEquals(TransactionStatus.MISSING_NOTARY_SIG, txnDsStatusBob)
        }
    }

    @Test(timeout=300_000)
    fun `two phase finality flow double spend transaction from pre-2PF initiator`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY - 1)

        val ref = bobNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        val stx = bobNode.startFlowAndRunNetwork(SpendFlow(ref, aliceNode.info.singleIdentity())).resultFuture.getOrThrow()

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionInternal(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionInternal(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBob)

        try {
            bobNode.startFlowAndRunNetwork(SpendFlow(ref, aliceNode.info.singleIdentity())).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            val stxId = (e.error as NotaryError.Conflict).txId
            assertNull(bobNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertNull(aliceNode.services.validatedTransactions.getTransactionInternal(stxId))
        }
    }

    @Test(timeout=300_000)
    fun `two phase finality flow double spend transaction to pre-2PF peer`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY - 1)

        val ref = aliceNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        val stx = aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionInternal(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionInternal(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBob)

        try {
            aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            val stxId = (e.error as NotaryError.Conflict).txId
            val (_, txnDsStatusAlice) = aliceNode.services.validatedTransactions.getTransactionInternal(stxId) ?: fail()
            assertEquals(TransactionStatus.MISSING_NOTARY_SIG, txnDsStatusAlice)
            assertNull(bobNode.services.validatedTransactions.getTransactionInternal(stxId))
        }
    }

    @StartableByRPC
    class IssueFlow(val notary: Party) : FlowLogic<StateAndRef<DummyContract.SingleOwnerState>>() {

        @Suspendable
        override fun call(): StateAndRef<DummyContract.SingleOwnerState> {
            val partyAndReference = PartyAndReference(ourIdentity, OpaqueBytes.of(1))
            val txBuilder = DummyContract.generateInitial(Random().nextInt(), notary, partyAndReference)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val notarised = subFlow(FinalityFlow(signedTransaction, emptySet<FlowSession>()))
            return notarised.coreTransaction.outRef(0)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class SpendFlow(private val stateAndRef: StateAndRef<DummyContract.SingleOwnerState>, private val newOwner: Party) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val txBuilder = DummyContract.move(stateAndRef, newOwner)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val sessionWithCounterParty = initiateFlow(newOwner)
            sessionWithCounterParty.sendAndReceive<String>("initial-message")
            return subFlow(FinalityFlow(signedTransaction, setOf(sessionWithCounterParty)))
        }
    }

    @InitiatedBy(SpendFlow::class)
    class AcceptSpendFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            otherSide.receive<String>()
            otherSide.send("initial-response")

            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }

    private fun createBob(cordapps: List<TestCordappInternal> = emptyList(), platformVersion: Int = PLATFORM_VERSION): TestStartedNode {
        return mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME, additionalCordapps = cordapps,
            version = MOCK_VERSION_INFO.copy(platformVersion = platformVersion)))
    }

    private fun TestStartedNode.issuesCashTo(recipient: TestStartedNode): SignedTransaction {
        return issuesCashTo(recipient.info.singleIdentity())
    }

    private fun TestStartedNode.issuesCashTo(other: Party): SignedTransaction {
        val amount = 1000.POUNDS.issuedBy(info.singleIdentity().ref(0))
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, other, notary)
        return services.signInitialTransaction(builder)
    }

    /** "Old" CorDapp which will force its node to keep its FinalityHandler enabled */
    private fun tokenOldCordapp() = cordappWithPackages().copy(targetPlatformVersion = 3)
}
