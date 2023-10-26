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
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotarySigCheck
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.flows.ReceiverDistributionRecord
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.flows.SenderDistributionRecord
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.TransactionStatus
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.CordaX500Name
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
import net.corda.coretests.flows.WithFinality.OldFinalityInvoker
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.issuedBy
import net.corda.finance.test.flows.CashIssueWithObserversFlow
import net.corda.finance.test.flows.CashPaymentWithObserversFlow
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.DBTransactionStorageLedgerRecovery.DBReceiverDistributionRecord
import net.corda.node.services.persistence.DBTransactionStorageLedgerRecovery.DBSenderDistributionRecord
import net.corda.node.services.persistence.HashedDistributionList
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
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
import net.corda.testing.node.internal.findCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert.assertNotNull
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
                                                       findCordapp("net.corda.finance.test.flows")))
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
        aliceNode.startFlowAndRunNetwork(OldFinalityInvoker(stx)).resultFuture.getOrThrow()
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

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionWithStatus(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionWithStatus(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBob)

        try {
            aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            val stxId = (e.error as NotaryError.Conflict).txId
            assertNull(aliceNode.services.validatedTransactions.getTransactionWithStatus(stxId))
            // Note: double spend error not propagated to peers by default (corDapp PV = 3)
            // Un-notarised txn clean-up occurs in ReceiveFinalityFlow upon receipt of UnexpectedFlowEndException
            assertNull(aliceNode.services.validatedTransactions.getTransactionWithStatus(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
        }
    }

    @Test(timeout=300_000)
    fun `two phase finality flow double spend transaction with double spend handling`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        val ref = aliceNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        val stx = aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionWithStatus(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionWithStatus(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBob)

        try {
            aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity(), handlePropagatedNotaryError = true)).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            val stxId = (e.error as NotaryError.Conflict).txId
            assertNull(aliceNode.services.validatedTransactions.getTransactionWithStatus(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
            assertNull(bobNode.services.validatedTransactions.getTransactionWithStatus(stxId))
            assertTxnRemovedFromDatabase(bobNode, stxId)
        }

        try {
            aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity(), handlePropagatedNotaryError = false)).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            val stxId = (e.error as NotaryError.Conflict).txId
            assertNull(aliceNode.services.validatedTransactions.getTransactionWithStatus(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
            val (_, txnStatus) = bobNode.services.validatedTransactions.getTransactionWithStatus(stxId) ?: fail()
            assertEquals(TransactionStatus.IN_FLIGHT, txnStatus)
        }
    }

    private fun assertTxnRemovedFromDatabase(node: TestStartedNode, stxId: SecureHash) {
        val fromDb = node.database.transaction {
            session.createQuery(
                    "from ${DBTransactionStorage.DBTransaction::class.java.name} where tx_id = :transactionId",
                    DBTransactionStorage.DBTransaction::class.java
            ).setParameter("transactionId", stxId.toString()).resultList
        }
        assertEquals(0, fromDb.size)
    }

    @Test(timeout=300_000)
    fun `two phase finality flow double spend transaction from pre-2PF initiator`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY - 1)

        val ref = bobNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        val stx = bobNode.startFlowAndRunNetwork(SpendFlow(ref, aliceNode.info.singleIdentity())).resultFuture.getOrThrow()

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionWithStatus(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionWithStatus(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBob)

        try {
            bobNode.startFlowAndRunNetwork(SpendFlow(ref, aliceNode.info.singleIdentity())).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            val stxId = (e.error as NotaryError.Conflict).txId
            assertNull(bobNode.services.validatedTransactions.getTransactionWithStatus(stxId))
            assertTxnRemovedFromDatabase(bobNode, stxId)
            assertNull(aliceNode.services.validatedTransactions.getTransactionWithStatus(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
        }
    }

    @Test(timeout=300_000)
    fun `two phase finality flow double spend transaction to pre-2PF peer`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY - 1)

        val ref = aliceNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        val stx = aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionWithStatus(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionWithStatus(stx.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBob)

        try {
            aliceNode.startFlowAndRunNetwork(SpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()
        }
        catch (e: NotaryException) {
            val stxId = (e.error as NotaryError.Conflict).txId
            assertNull(aliceNode.services.validatedTransactions.getTransactionWithStatus(stxId))
            assertTxnRemovedFromDatabase(aliceNode, stxId)
            assertNull(bobNode.services.validatedTransactions.getTransactionWithStatus(stxId))
            assertTxnRemovedFromDatabase(bobNode, stxId)
        }
    }

    @Test(timeout=300_000)
    fun `two phase finality flow speedy spender`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        val ref = aliceNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        val notarisedStxn1 = aliceNode.startFlowAndRunNetwork(SpeedySpendFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        val (_, txnStatusAlice) = aliceNode.services.validatedTransactions.getTransactionWithStatus(notarisedStxn1.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice)
        val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionWithStatus(notarisedStxn1.id) ?: fail()
        assertEquals(TransactionStatus.IN_FLIGHT, txnStatusBob)

        // now lets attempt a new spend with the new output of the previous transaction
        val newStateRef = notarisedStxn1.coreTransaction.outRef<DummyContract.SingleOwnerState>(1)
        val notarisedStxn2 = aliceNode.startFlowAndRunNetwork(SpeedySpendFlow(newStateRef, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        // the original transaction is now finalised at Bob (despite the original flow not completing) because Bob resolved the
        // original transaction from Alice in the second transaction (and Alice had already notarised and finalised the original transaction)
        val (_, txnStatusBobAgain) = bobNode.services.validatedTransactions.getTransactionWithStatus(notarisedStxn1.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBobAgain)

        val (_, txnStatusAlice2) = aliceNode.services.validatedTransactions.getTransactionWithStatus(notarisedStxn2.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusAlice2)
        val (_, txnStatusBob2) = bobNode.services.validatedTransactions.getTransactionWithStatus(notarisedStxn2.id) ?: fail()
        assertEquals(TransactionStatus.IN_FLIGHT, txnStatusBob2)

        // Validate attempt at flow finalisation by Bob has no effect on outcome.
        val finaliseStxn1 =  bobNode.startFlowAndRunNetwork(FinaliseSpeedySpendFlow(notarisedStxn1.id, notarisedStxn1.sigs)).resultFuture.getOrThrow()
        val (_, txnStatusBobYetAgain) = bobNode.services.validatedTransactions.getTransactionWithStatus(finaliseStxn1.id) ?: fail()
        assertEquals(TransactionStatus.VERIFIED, txnStatusBobYetAgain)
    }

    @Test(timeout=300_000)
    fun `two phase finality flow keeps un-notarised transaction where initiator fails to send notary signature`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        val ref = aliceNode.startFlowAndRunNetwork(IssueFlow(notary)).resultFuture.getOrThrow()
        try {
            aliceNode.startFlowAndRunNetwork(MimicFinalityFailureFlow(ref, bobNode.info.singleIdentity())).resultFuture.getOrThrow()
        }
        catch (e: UnexpectedFlowEndException) {
            val stxId = SecureHash.parse(e.message)
            val (_, txnStatusBob) = bobNode.services.validatedTransactions.getTransactionWithStatus(stxId) ?: fail()
            assertEquals(TransactionStatus.IN_FLIGHT, txnStatusBob)
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

        val sdrs = getSenderRecoveryData(stx.id, aliceNode.database).apply {
            assertEquals(1, this.size)
            assertEquals(StatesToRecord.ONLY_RELEVANT, this[0].senderStatesToRecord)
            assertEquals(StatesToRecord.ALL_VISIBLE, this[0].receiverStatesToRecord)
            assertEquals(SecureHash.sha256(BOB_NAME.toString()), this[0].peerPartyId)
        }
        val rdr = getReceiverRecoveryData(stx.id, bobNode).apply {
            assertNotNull(this)
            val hashedDL = HashedDistributionList.decrypt(this!!.encryptedDistributionList.bytes, aliceNode.internals.encryptionService)
            assertEquals(StatesToRecord.ONLY_RELEVANT, hashedDL.senderStatesToRecord)
            assertEquals(SecureHash.sha256(aliceNode.info.singleIdentity().name.toString()), this.peerPartyId)
            assertEquals(mapOf<SecureHash, StatesToRecord>(SecureHash.sha256(BOB_NAME.toString()) to StatesToRecord.ALL_VISIBLE), hashedDL.peerHashToStatesToRecord)
        }
        validateSenderAndReceiverTimestamps(sdrs, rdr!!)
    }

    @Test(timeout=300_000)
    fun `two phase finality flow payment transaction with observers`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)
        val charlieNode = createNode(CHARLIE_NAME, platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        // issue some cash
        aliceNode.startFlow(CashIssueFlow(Amount(1000L, GBP), OpaqueBytes.of(1), notary)).resultFuture.getOrThrow().stx

        // standard issuance with observers passed in as FinalityFlow sessions
        val stx = aliceNode.startFlowAndRunNetwork(CashPaymentWithObserversFlow(
                amount = Amount(100L, GBP),
                recipient = bobNode.info.singleIdentity(),
                observers = setOf(charlieNode.info.singleIdentity()))).resultFuture.getOrThrow()

        assertThat(aliceNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
        assertThat(bobNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
        assertThat(charlieNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull

        val sdrs = getSenderRecoveryData(stx.id, aliceNode.database).apply {
            assertEquals(2, this.size)
            assertEquals(StatesToRecord.ONLY_RELEVANT, this[0].senderStatesToRecord)
            assertEquals(SecureHash.sha256(BOB_NAME.toString()), this[0].peerPartyId)
            assertEquals(StatesToRecord.ALL_VISIBLE, this[1].receiverStatesToRecord)
            assertEquals(SecureHash.sha256(CHARLIE_NAME.toString()), this[1].peerPartyId)
        }
        val rdr = getReceiverRecoveryData(stx.id, bobNode).apply {
            assertNotNull(this)
            val hashedDL = HashedDistributionList.decrypt(this!!.encryptedDistributionList.bytes, aliceNode.internals.encryptionService)
            assertEquals(StatesToRecord.ONLY_RELEVANT, hashedDL.senderStatesToRecord)
            assertEquals(SecureHash.sha256(aliceNode.info.singleIdentity().name.toString()), this.peerPartyId)
            // note: Charlie assertion here is using the hinted StatesToRecord value passed to it from Alice
            assertEquals(mapOf<SecureHash, StatesToRecord>(
                    SecureHash.sha256(BOB_NAME.toString()) to StatesToRecord.ONLY_RELEVANT,
                    SecureHash.sha256(CHARLIE_NAME.toString()) to StatesToRecord.ALL_VISIBLE
            ), hashedDL.peerHashToStatesToRecord)
        }
        validateSenderAndReceiverTimestamps(sdrs, rdr!!)

        // exercise the new FinalityFlow observerSessions constructor parameter
        val stx3 = aliceNode.startFlowAndRunNetwork(CashPaymentWithObserversFlow(
                amount = Amount(100L, GBP),
                recipient = bobNode.info.singleIdentity(),
                observers = setOf(charlieNode.info.singleIdentity()),
                useObserverSessions = true)).resultFuture.getOrThrow()

        assertThat(aliceNode.services.validatedTransactions.getTransaction(stx3.id)).isNotNull
        assertThat(bobNode.services.validatedTransactions.getTransaction(stx3.id)).isNotNull
        assertThat(charlieNode.services.validatedTransactions.getTransaction(stx3.id)).isNotNull

        val senderDistributionRecords = getSenderRecoveryData(stx3.id, aliceNode.database).apply {
            assertEquals(2, this.size)
            assertEquals(this[0].timestamp, this[1].timestamp)
        }
        getReceiverRecoveryData(stx3.id, bobNode).apply {
            assertThat(this).isNotNull
            assertEquals(senderDistributionRecords[0].timestamp, this!!.timestamp)
        }
        getReceiverRecoveryData(stx3.id, charlieNode).apply {
            assertThat(this).isNotNull
            assertEquals(senderDistributionRecords[0].timestamp, this!!.timestamp)
        }
    }

    private fun validateSenderAndReceiverTimestamps(sdrs: List<SenderDistributionRecord>, rdr: ReceiverDistributionRecord) {
        sdrs.map {
            assertEquals(it.timestamp, rdr.timestamp)
        }
    }

    @Test(timeout=300_000)
    fun `two phase finality flow payment transaction using confidential identities`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        aliceNode.startFlow(CashIssueFlow(Amount(1000L, GBP), OpaqueBytes.of(1), notary)).resultFuture.getOrThrow().stx
        val stx = aliceNode.startFlowAndRunNetwork(CashPaymentFlow(
                amount = Amount(100L, GBP),
                recipient = bobNode.info.singleIdentity(),
                anonymous = true)).resultFuture.getOrThrow().stx

        assertThat(aliceNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull
        assertThat(bobNode.services.validatedTransactions.getTransaction(stx.id)).isNotNull

        val sdr = getSenderRecoveryData(stx.id, aliceNode.database).apply {
            assertEquals(1, this.size)
            assertEquals(StatesToRecord.ONLY_RELEVANT, this[0].senderStatesToRecord)
            assertEquals(SecureHash.sha256(BOB_NAME.toString()), this[0].peerPartyId)
        }
        val rdr = getReceiverRecoveryData(stx.id, bobNode).apply {
            assertNotNull(this)
            val hashedDL = HashedDistributionList.decrypt(this!!.encryptedDistributionList.bytes, aliceNode.internals.encryptionService)
            assertEquals(StatesToRecord.ONLY_RELEVANT, hashedDL.senderStatesToRecord)
            assertEquals(SecureHash.sha256(aliceNode.info.singleIdentity().name.toString()), this.peerPartyId)
            assertEquals(mapOf<SecureHash, StatesToRecord>(SecureHash.sha256(BOB_NAME.toString()) to StatesToRecord.ONLY_RELEVANT), hashedDL.peerHashToStatesToRecord)
        }
        validateSenderAndReceiverTimestamps(sdr, rdr!!)
    }

    private fun getSenderRecoveryData(id: SecureHash, database: CordaPersistence): List<SenderDistributionRecord> {
        val fromDb = database.transaction {
            session.createQuery(
                    "from ${DBSenderDistributionRecord::class.java.name} where transaction_id = :transactionId",
                    DBSenderDistributionRecord::class.java
            ).setParameter("transactionId", id.toString()).resultList
        }
        return fromDb.map { it.toSenderDistributionRecord() }
    }

    private fun getReceiverRecoveryData(txId: SecureHash, receiver: TestStartedNode): ReceiverDistributionRecord? {
        return receiver.database.transaction {
            session.createQuery(
                    "from ${DBReceiverDistributionRecord::class.java.name} where transaction_id = :transactionId",
                    DBReceiverDistributionRecord::class.java
            ).setParameter("transactionId", txId.toString()).resultList
        }.singleOrNull()?.toReceiverDistributionRecord()
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
    class SpendFlow(private val stateAndRef: StateAndRef<DummyContract.SingleOwnerState>, private val newOwner: Party,
                    private val handlePropagatedNotaryError: Boolean = false) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val txBuilder = DummyContract.move(stateAndRef, newOwner)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val sessionWithCounterParty = initiateFlow(newOwner)
            sessionWithCounterParty.send(handlePropagatedNotaryError)
            return subFlow(FinalityFlow(signedTransaction, setOf(sessionWithCounterParty)))
        }
    }

    @Suppress("unused")
    @InitiatedBy(SpendFlow::class)
    class AcceptSpendFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val handleNotaryError = otherSide.receive<Boolean>().unwrap { it }
            subFlow(ReceiveFinalityFlow(otherSide, handlePropagatedNotaryError = handleNotaryError))
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

    @Suppress("unused")
    @InitiatedBy(SpeedySpendFlow::class)
    class AcceptSpeedySpendFlow(private val otherSideSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            // Mimic ReceiveFinalityFlow but fail to finalise
            try {
                val stx = subFlow(ReceiveTransactionFlow(otherSideSession, false, StatesToRecord.ONLY_RELEVANT, true))
                require(NotarySigCheck.needsNotarySignature(stx))
                logger.info("Peer recording transaction without notary signature.")
                (serviceHub as ServiceHubCoreInternal).recordUnnotarisedTransaction(stx)
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

    class FinaliseSpeedySpendFlow(val id: SecureHash, private val sigs: List<TransactionSignature>) : FlowLogic<SignedTransaction>() {

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
            subFlow(object : SendTransactionFlow(stxn, setOf(sessionWithCounterParty), emptySet(), StatesToRecord.ONLY_RELEVANT, true) {
                override fun isFinality(): Boolean = true
            })
            throw UnexpectedFlowEndException("${stxn.id}")
        }
    }

    @Suppress("unused")
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

    private fun createNode(legalName: CordaX500Name, cordapps: List<TestCordappInternal> = emptyList(), platformVersion: Int = PLATFORM_VERSION): TestStartedNode {
        return mockNet.createNode(InternalMockNodeParameters(legalName = legalName, additionalCordapps = cordapps,
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
