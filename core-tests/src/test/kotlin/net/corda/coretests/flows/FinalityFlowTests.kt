package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import net.corda.core.contracts.Amount
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.FlowTransactionMetadata
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotarySigCheck
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.TransactionStatus
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.internal.ServiceHubCoreInternal
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.coretesting.internal.matchers.flow.willReturn
import net.corda.coretesting.internal.matchers.flow.willThrow
import net.corda.coretests.flows.WithFinality.FinalityInvoker
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashIssueWithObserversFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.issuedBy
import net.corda.node.services.persistence.DBTransactionStorage
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
import java.sql.SQLException
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
            assertNull(aliceNode.services.validatedTransactions.getTransactionInternal(stxId))
            // Note: double spend error not propagated to peers by default (corDapp PV = 3)
            // Un-notarised txn clean-up occurs in ReceiveFinalityFlow upon receipt of UnexpectedFlowEndException
            assertNull(aliceNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
        }
    }

    @Test(timeout=300_000)
    fun `two phase finality flow double spend transaction with double spend handling`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        val ref = aliceNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        val stx = aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionInternal(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionInternal(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBob)

        try {
            aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity(), propagateDoubleSpendErrorToPeers = true)).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            // note: ReceiveFinalityFlow un-notarised transaction clean-up takes place upon catching NotaryError.Conflict
            val stxId = (e.error as NotaryError.Conflict).txId
            assertNull(aliceNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
            assertNull(bobNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(bobNode, stxId)
        }

        try {
            aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity(), propagateDoubleSpendErrorToPeers = false)).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            // note: ReceiveFinalityFlow un-notarised transaction clean-up takes place upon catching UnexpectedFlowEndException
            val stxId = (e.error as NotaryError.Conflict).txId
            assertNull(aliceNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
            assertNull(bobNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(bobNode, stxId)
        }
    }

    private fun assertTxnRemovedFromDatabase(node: TestStartedNode, stxId: SecureHash) {
        val fromDb = node.database.transaction {
            session.createQuery(
                    "from ${DBTransactionStorage.DBTransaction::class.java.name} where tx_id = :transactionId",
                    DBTransactionStorage.DBTransaction::class.java
            ).setParameter("transactionId", stxId.toString()).resultList.map { it }
        }
        assertEquals(0, fromDb.size)
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
            assertTxnRemovedFromDatabase(bobNode, stxId)
            assertNull(aliceNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
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
            assertNull(aliceNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
            assertNull(bobNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(bobNode, stxId)
        }
    }

    @Test(timeout=300_000)
    fun `two phase finality flow speedy spender`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        val ref = aliceNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        val notarisedStxn1 = aliceNode.startFlowAndRunNetwork(SpeedySpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionInternal(notarisedStxn1.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionInternal(notarisedStxn1.id) ?: fail()
        assertEquals(TransactionStatus.IN_FLIGHT, txnStatusBob)

        // now lets attempt a new spend with the new output of the previous transaction
        val newStateRef = notarisedStxn1.coreTransaction.outRef<DummyContract.SingleOwnerState>(1)
        val notarisedStxn2 = aliceNode.startFlowAndRunNetwork(SpeedySpendFlow(newStateRef, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        // the original transaction is now finalised at Bob (despite the original flow not completing) because Bob resolved the
        // original transaction from Alice in the second transaction (and Alice had already notarised and finalised the original transaction)
        val (_, txnStatusBobAgain) = bobNode.services.validatedTransactions.getTransactionInternal(notarisedStxn1.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBobAgain)

        val (_, txnStatusAlice2) = aliceNode.services.validatedTransactions.getTransactionInternal(notarisedStxn2.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice2)
        val (_, txnStatusBob2) = bobNode.services.validatedTransactions.getTransactionInternal(notarisedStxn2.id) ?: fail()
        assertEquals(TransactionStatus.IN_FLIGHT, txnStatusBob2)

        // Validate attempt at flow finalisation by Bob has no effect on outcome.
        val finaliseStxn1 =  bobNode.startFlowAndRunNetwork(FinaliseSpeedySpendFlow(notarisedStxn1.id, notarisedStxn1.sigs)).resultFuture.getOrThrow()
        val (_, txnStatusBobYetAgain) = bobNode.services.validatedTransactions.getTransactionInternal(finaliseStxn1.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBobYetAgain)
    }

    @Test(timeout=300_000)
    fun `two phase finality flow successfully removes un-notarised transaction where initiator fails to send notary signature`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        val ref = aliceNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        try {
            aliceNode.startFlowAndRunNetwork(MimicFinalityFailureFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()
        }
        catch (e: UnexpectedFlowEndException) {
            val stxId = SecureHash.parse(e.message)
            assertNull(aliceNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
            assertNull(bobNode.services.validatedTransactions.getTransactionInternal(stxId))
            assertTxnRemovedFromDatabase(bobNode, stxId)
        }
    }

    @Test(timeout=300_000)
    fun `two phase finality flow issuance transaction with observers`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        val stx = aliceNode.startFlowAndRunNetwork(CashIssueWithObserversFlow(
                Amount(1000L, GBP), OpaqueBytes.of(1), notary,
                observers = setOf(bobNode.info.singleIdentity()))).resultFuture.getOrThrow().stx

        assertThat(aliceNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
        assertThat(bobNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
    }

    @StartableByRPC
    class IssueFlow(val notary: Party, val observers: Set<Party> = emptySet()) : FlowLogic<StateAndRef<DummyContract.SingleOwnerState>>() {

        @Suspendable
        override fun call(): StateAndRef<DummyContract.SingleOwnerState> {
            val partyAndReference = PartyAndReference(ourIdentity, OpaqueBytes.of(1))
            val txBuilder = DummyContract.generateInitial(Random().nextInt(), notary, partyAndReference)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val observerSessions = observers.map { initiateFlow(it) }
            val notarised = subFlow(FinalityFlow(signedTransaction, observerSessions))
            return notarised.coreTransaction.outRef(0)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class SpendFlow(private val stateAndRef: StateAndRef<DummyContract.SingleOwnerState>, private val newOwner: Party,
                    private val propagateDoubleSpendErrorToPeers: Boolean? = null) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val txBuilder = DummyContract.move(stateAndRef, newOwner)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val sessionWithCounterParty = initiateFlow(newOwner)
            sessionWithCounterParty.sendAndReceive<String>("initial-message")
            return subFlow(FinalityFlow(signedTransaction, setOf(sessionWithCounterParty), propagateDoubleSpendErrorToPeers = propagateDoubleSpendErrorToPeers))
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

    /**
     * This flow allows an Initiator to race ahead of a Receiver when using Two Phase Finality.
     * The initiator transaction will be finalised, so output states can be used in a follow-up transaction.
     * The receiver transaction will not be finalised, causing ledger inconsistency.
     */
    @StartableByRPC
    @InitiatingFlow
    class SpeedySpendFlow(private val stateAndRef: StateAndRef<DummyContract.SingleOwnerState>, private val newOwner: Party) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val newState = StateAndContract(DummyContract.SingleOwnerState(99999, ourIdentity), DummyContract.PROGRAM_ID)
            val txBuilder = DummyContract.move(stateAndRef, newOwner).withItems(newState)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val sessionWithCounterParty = initiateFlow(newOwner)
            try {
                subFlow(FinalityFlow(signedTransaction, setOf(sessionWithCounterParty)))
            }
            catch (e: FinalisationFailedException) {
                // expected (transaction has been notarised by Initiator)
                return e.notarisedTxn
            }
            return signedTransaction
        }
    }

    @InitiatedBy(SpeedySpendFlow::class)
    class AcceptSpeedySpendFlow(private val otherSideSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            // Mimic ReceiveFinalityFlow but fail to finalise
            try {
                val stx = subFlow(ReceiveTransactionFlow(otherSideSession,
                        checkSufficientSignatures = false, statesToRecord = StatesToRecord.ONLY_RELEVANT, deferredAck = true))
                require(NotarySigCheck.needsNotarySignature(stx))
                logger.info("Peer recording transaction without notary signature.")
                (serviceHub as ServiceHubCoreInternal).recordUnnotarisedTransaction(stx,
                        FlowTransactionMetadata(otherSideSession.counterparty.name, StatesToRecord.ONLY_RELEVANT))
                otherSideSession.send(FetchDataFlow.Request.End) // Finish fetching data (overrideAutoAck)
                logger.info("Peer recorded transaction without notary signature.")

                val notarySignatures = otherSideSession.receive<List<TransactionSignature>>()
                        .unwrap { it }
                logger.info("Peer received notarised signature.")
                (serviceHub as ServiceHubCoreInternal).finalizeTransactionWithExtraSignatures(stx + notarySignatures, notarySignatures, StatesToRecord.ONLY_RELEVANT)
                throw FinalisationFailedException(stx + notarySignatures)
            }
            catch (e: SQLException) {
                logger.error("Peer failure upon recording or finalising transaction: $e")
                otherSideSession.send(FetchDataFlow.Request.End) // Finish fetching data (overrideAutoAck)
                throw UnexpectedFlowEndException("Peer failure upon recording or finalising transaction.", e.cause)
            }
            catch (uae: TransactionVerificationException.UntrustedAttachmentsException) {
                logger.error("Peer failure upon receiving transaction: $uae")
                otherSideSession.send(FetchDataFlow.Request.End) // Finish fetching data (overrideAutoAck)
                throw uae
            }
        }
    }

    class FinaliseSpeedySpendFlow(val id: SecureHash, val sigs: List<TransactionSignature>) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            // Mimic ReceiveFinalityFlow finalisation
            val stx = serviceHub.validatedTransactions.getTransaction(id) ?: throw FlowException("Missing transaction: $id")
            (serviceHub as ServiceHubCoreInternal).finalizeTransactionWithExtraSignatures(stx + sigs, sigs, StatesToRecord.ONLY_RELEVANT)
            logger.info("Peer finalised transaction with notary signature.")

            return stx + sigs
        }
    }

    @InitiatingFlow
    class MimicFinalityFailureFlow(private val stateAndRef: StateAndRef<DummyContract.SingleOwnerState>, private val newOwner: Party) : FlowLogic<SignedTransaction>() {
        // Mimic FinalityFlow but trigger UnexpectedFlowEndException in ReceiveFinality whilst awaiting receipt of notary signature
        @Suspendable
        override fun call(): SignedTransaction {
            val txBuilder = DummyContract.move(stateAndRef, newOwner)
            val stxn = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val sessionWithCounterParty = initiateFlow(newOwner)
            subFlow(SendTransactionFlow(sessionWithCounterParty, stxn))
            throw UnexpectedFlowEndException("${stxn.id}")
        }
    }

    @InitiatedBy(MimicFinalityFailureFlow::class)
    class TriggerReceiveFinalityFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }

    class FinalisationFailedException(val notarisedTxn: SignedTransaction) : FlowException("Failed to finalise transaction with notary signature.")

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
