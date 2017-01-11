package net.corda.node.messaging

import net.corda.contracts.CommercialPaper
import net.corda.contracts.asset.*
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.composite
import net.corda.core.days
import net.corda.core.flows.FlowStateMachine
import net.corda.core.flows.StateMachineRunId
import net.corda.core.getOrThrow
import net.corda.core.map
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.*
import net.corda.core.rootCause
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.LogHelper
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.flows.TwoPartyTradeFlow.Buyer
import net.corda.flows.TwoPartyTradeFlow.Seller
import net.corda.node.internal.AbstractNode
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.persistence.StorageServiceImpl
import net.corda.node.services.persistence.checkpoints
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.*
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.util.*
import java.util.concurrent.Future
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * In this example, Alice wishes to sell her commercial paper to Bob in return for $1,000,000 and they wish to do
 * it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Alice and Bob already found each other via some market, and have agreed the details already.
 */
class TwoPartyTradeFlowTests {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var aliceNode: MockNetwork.MockNode
    lateinit var bobNode: MockNetwork.MockNode

    @Before
    fun before() {
        net = MockNetwork(false)
        net.identities += MOCK_IDENTITY_SERVICE.identities
        LogHelper.setLevel("platform.trade", "core.contract.TransactionGroup", "recordingmap")
    }

    @After
    fun after() {
        LogHelper.reset("platform.trade", "core.contract.TransactionGroup", "recordingmap")
    }

    @Test
    fun `trade cash for commercial paper`() {
        // We run this in parallel threads to help catch any race conditions that may exist. The other tests
        // we run in the unit test thread exclusively to speed things up, ensure deterministic results and
        // allow interruption half way through.
        net = MockNetwork(false, true)

        ledger {
            notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
            aliceNode = net.createPartyNode(notaryNode.info.address, ALICE.name, ALICE_KEY)
            bobNode = net.createPartyNode(notaryNode.info.address, BOB.name, BOB_KEY)
            val aliceKey = aliceNode.services.legalIdentityKey
            val notaryKey = notaryNode.services.notaryIdentityKey

            aliceNode.disableDBCloseOnStop()
            bobNode.disableDBCloseOnStop()

            databaseTransaction(bobNode.database) {
                bobNode.services.fillWithSomeTestCash(2000.DOLLARS, outputNotary = notaryNode.info.notaryIdentity)
            }
            val alicesFakePaper = fillUpForSeller(false, aliceNode.info.legalIdentity.owningKey,
                    1200.DOLLARS `issued by` DUMMY_CASH_ISSUER, null, notaryNode.info.notaryIdentity).second

            insertFakeTransactions(alicesFakePaper, aliceNode, aliceKey, notaryKey)

            val (bobStateMachine, aliceResult) = runBuyerAndSeller("alice's paper".outputStateAndRef())

            // TODO: Verify that the result was inserted into the transaction database.
            // assertEquals(bobResult.get(), aliceNode.storage.validatedTransactions[aliceResult.get().id])
            assertEquals(aliceResult.getOrThrow(), bobStateMachine.getOrThrow().resultFuture.getOrThrow())

            aliceNode.stop()
            bobNode.stop()

            databaseTransaction(aliceNode.database) {
                assertThat(aliceNode.checkpointStorage.checkpoints()).isEmpty()
            }
            aliceNode.manuallyCloseDB()
            databaseTransaction(bobNode.database) {
                assertThat(bobNode.checkpointStorage.checkpoints()).isEmpty()
            }
            bobNode.manuallyCloseDB()
        }
    }

    @Test
    fun `shutdown and restore`() {
        ledger {
            notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
            aliceNode = net.createPartyNode(notaryNode.info.address, ALICE.name, ALICE_KEY)
            bobNode = net.createPartyNode(notaryNode.info.address, BOB.name, BOB_KEY)
            aliceNode.disableDBCloseOnStop()
            bobNode.disableDBCloseOnStop()
            val aliceKey = aliceNode.services.legalIdentityKey
            val notaryKey = notaryNode.services.notaryIdentityKey

            val bobAddr = bobNode.net.myAddress as InMemoryMessagingNetwork.PeerHandle
            val networkMapAddr = notaryNode.info.address

            net.runNetwork() // Clear network map registration messages

            databaseTransaction(bobNode.database) {
                bobNode.services.fillWithSomeTestCash(2000.DOLLARS, outputNotary = notaryNode.info.notaryIdentity)
            }
            val alicesFakePaper = fillUpForSeller(false, aliceNode.info.legalIdentity.owningKey,
                    1200.DOLLARS `issued by` DUMMY_CASH_ISSUER, null, notaryNode.info.notaryIdentity).second
            insertFakeTransactions(alicesFakePaper, aliceNode, aliceKey, notaryKey)
            val aliceFuture = runBuyerAndSeller("alice's paper".outputStateAndRef()).sellerResult

            // Everything is on this thread so we can now step through the flow one step at a time.
            // Seller Alice already sent a message to Buyer Bob. Pump once:
            bobNode.pumpReceive()

            // Bob sends a couple of queries for the dependencies back to Alice. Alice reponds.
            aliceNode.pumpReceive()
            bobNode.pumpReceive()
            aliceNode.pumpReceive()
            bobNode.pumpReceive()
            aliceNode.pumpReceive()
            bobNode.pumpReceive()

            // OK, now Bob has sent the partial transaction back to Alice and is waiting for Alice's signature.
            databaseTransaction(bobNode.database) {
                assertThat(bobNode.checkpointStorage.checkpoints()).hasSize(1)
            }

            val storage = bobNode.storage.validatedTransactions
            val bobTransactionsBeforeCrash = databaseTransaction(bobNode.database) {
                (storage as DBTransactionStorage).transactions
            }
            assertThat(bobTransactionsBeforeCrash).isNotEmpty()

            // .. and let's imagine that Bob's computer has a power cut. He now has nothing now beyond what was on disk.
            bobNode.stop()

            // Alice doesn't know that and carries on: she wants to know about the cash transactions he's trying to use.
            // She will wait around until Bob comes back.
            assertThat(aliceNode.pumpReceive()).isNotNull()

            // ... bring the node back up ... the act of constructing the SMM will re-register the message handlers
            // that Bob was waiting on before the reboot occurred.
            bobNode = net.createNode(networkMapAddr, bobAddr.id, object : MockNetwork.Factory {
                override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                                    advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
                    return MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, bobAddr.id, BOB_KEY)
                }
            }, true, BOB.name, BOB_KEY)

            // Find the future representing the result of this state machine again.
            val bobFuture = bobNode.smm.findStateMachines(Buyer::class.java).single().second

            // And off we go again.
            net.runNetwork()

            // Bob is now finished and has the same transaction as Alice.
            assertThat(bobFuture.getOrThrow()).isEqualTo(aliceFuture.getOrThrow())

            assertThat(bobNode.smm.findStateMachines(Buyer::class.java)).isEmpty()
            databaseTransaction(bobNode.database) {
                assertThat(bobNode.checkpointStorage.checkpoints()).isEmpty()
            }
            databaseTransaction(aliceNode.database) {
                assertThat(aliceNode.checkpointStorage.checkpoints()).isEmpty()
            }

            databaseTransaction(bobNode.database) {
                val restoredBobTransactions = bobTransactionsBeforeCrash.filter { bobNode.storage.validatedTransactions.getTransaction(it.id) != null }
                assertThat(restoredBobTransactions).containsAll(bobTransactionsBeforeCrash)
            }

            aliceNode.manuallyCloseDB()
            bobNode.manuallyCloseDB()
        }
    }

    // Creates a mock node with an overridden storage service that uses a RecordingMap, that lets us test the order
    // of gets and puts.
    private fun makeNodeWithTracking(networkMapAddr: SingleMessageRecipient?, name: String, keyPair: KeyPair): MockNetwork.MockNode {
        // Create a node in the mock network ...
        return net.createNode(networkMapAddr, -1, object : MockNetwork.Factory {
            override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                                advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
                return object : MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, id, keyPair) {
                    // That constructs the storage service object in a customised way ...
                    override fun constructStorageService(
                            attachments: NodeAttachmentService,
                            transactionStorage: TransactionStorage,
                            stateMachineRecordedTransactionMappingStorage: StateMachineRecordedTransactionMappingStorage
                    ): StorageServiceImpl {
                        return StorageServiceImpl(attachments, RecordingTransactionStorage(database, transactionStorage), stateMachineRecordedTransactionMappingStorage)
                    }
                }
            }
        }, true, name, keyPair)
    }

    @Test
    fun `check dependencies of sale asset are resolved`() {
        notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
        aliceNode = makeNodeWithTracking(notaryNode.info.address, ALICE.name, ALICE_KEY)
        bobNode = makeNodeWithTracking(notaryNode.info.address, BOB.name, BOB_KEY)
        val aliceKey = aliceNode.services.legalIdentityKey

        ledger(aliceNode.services) {

            // Insert a prospectus type attachment into the commercial paper transaction.
            val stream = ByteArrayOutputStream()
            JarOutputStream(stream).use {
                it.putNextEntry(ZipEntry("Prospectus.txt"))
                it.write("Our commercial paper is top notch stuff".toByteArray())
                it.closeEntry()
            }
            val attachmentID = attachment(ByteArrayInputStream(stream.toByteArray()))

            val bobsFakeCash = fillUpForBuyer(false, bobNode.keyManagement.freshKey().public.composite, notaryNode.info.notaryIdentity).second
            val bobsSignedTxns = insertFakeTransactions(bobsFakeCash, bobNode)
            val alicesFakePaper = fillUpForSeller(false, aliceNode.info.legalIdentity.owningKey,
                    1200.DOLLARS `issued by` DUMMY_CASH_ISSUER, attachmentID, notaryNode.info.notaryIdentity).second
            val alicesSignedTxns = insertFakeTransactions(alicesFakePaper, aliceNode, aliceKey)

            net.runNetwork() // Clear network map registration messages

            runBuyerAndSeller("alice's paper".outputStateAndRef())

            net.runNetwork()

            run {
                val records = (bobNode.storage.validatedTransactions as RecordingTransactionStorage).records
                // Check Bobs's database accesses as Bob's cash transactions are downloaded by Alice.
                records.expectEvents(isStrict = false) {
                    sequence(
                            // Buyer Bob is told about Alice's commercial paper, but doesn't know it ..
                            expect(TxRecord.Get(alicesFakePaper[0].id)),
                            // He asks and gets the tx, validates it, sees it's a self issue with no dependencies, stores.
                            expect(TxRecord.Add(alicesSignedTxns.values.first())),
                            // Alice gets Bob's proposed transaction and doesn't know his two cash states. She asks, Bob answers.
                            expect(TxRecord.Get(bobsFakeCash[1].id)),
                            expect(TxRecord.Get(bobsFakeCash[2].id)),
                            // Alice notices that Bob's cash txns depend on a third tx she also doesn't know. She asks, Bob answers.
                            expect(TxRecord.Get(bobsFakeCash[0].id))
                    )
                }

                // Bob has downloaded the attachment.
                bobNode.storage.attachments.openAttachment(attachmentID)!!.openAsJAR().use {
                    it.nextJarEntry
                    val contents = it.reader().readText()
                    assertTrue(contents.contains("Our commercial paper is top notch stuff"))
                }
            }

            // And from Alice's perspective ...
            run {
                val records = (aliceNode.storage.validatedTransactions as RecordingTransactionStorage).records
                records.expectEvents(isStrict = false) {
                    sequence(
                            // Seller Alice sends her seller info to Bob, who wants to check the asset for sale.
                            // He requests, Alice looks up in her DB to send the tx to Bob
                            expect(TxRecord.Get(alicesFakePaper[0].id)),
                            // Seller Alice gets a proposed tx which depends on Bob's two cash txns and her own tx.
                            expect(TxRecord.Get(bobsFakeCash[1].id)),
                            expect(TxRecord.Get(bobsFakeCash[2].id)),
                            expect(TxRecord.Get(alicesFakePaper[0].id)),
                            // Alice notices that Bob's cash txns depend on a third tx she also doesn't know.
                            expect(TxRecord.Get(bobsFakeCash[0].id)),
                            // Bob answers with the transactions that are now all verifiable, as Alice bottomed out.
                            // Bob's transactions are valid, so she commits to the database
                            expect(TxRecord.Add(bobsSignedTxns[bobsFakeCash[0].id]!!)),
                            expect(TxRecord.Get(bobsFakeCash[0].id)), // Verify
                            expect(TxRecord.Add(bobsSignedTxns[bobsFakeCash[2].id]!!)),
                            expect(TxRecord.Get(bobsFakeCash[0].id)), // Verify
                            expect(TxRecord.Add(bobsSignedTxns[bobsFakeCash[1].id]!!)),
                            // Now she verifies the transaction is contract-valid (not signature valid) which means
                            // looking up the states again.
                            expect(TxRecord.Get(bobsFakeCash[1].id)),
                            expect(TxRecord.Get(bobsFakeCash[2].id)),
                            expect(TxRecord.Get(alicesFakePaper[0].id)),
                            // Alice needs to look up the input states to find out which Notary they point to
                            expect(TxRecord.Get(bobsFakeCash[1].id)),
                            expect(TxRecord.Get(bobsFakeCash[2].id)),
                            expect(TxRecord.Get(alicesFakePaper[0].id))
                    )
                }
            }
        }
    }

    @Test
    fun `track() works`() {

        notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
        aliceNode = makeNodeWithTracking(notaryNode.info.address, ALICE.name, ALICE_KEY)
        bobNode = makeNodeWithTracking(notaryNode.info.address, BOB.name, BOB_KEY)
        val aliceKey = aliceNode.services.legalIdentityKey

        ledger(aliceNode.services) {

            // Insert a prospectus type attachment into the commercial paper transaction.
            val stream = ByteArrayOutputStream()
            JarOutputStream(stream).use {
                it.putNextEntry(ZipEntry("Prospectus.txt"))
                it.write("Our commercial paper is top notch stuff".toByteArray())
                it.closeEntry()
            }
            val attachmentID = attachment(ByteArrayInputStream(stream.toByteArray()))

            val bobsFakeCash = fillUpForBuyer(false, bobNode.keyManagement.freshKey().public.composite, notaryNode.info.notaryIdentity).second
            insertFakeTransactions(bobsFakeCash, bobNode)
            val alicesFakePaper = fillUpForSeller(false, aliceNode.info.legalIdentity.owningKey,
                    1200.DOLLARS `issued by` DUMMY_CASH_ISSUER, attachmentID, notaryNode.info.notaryIdentity).second
            insertFakeTransactions(alicesFakePaper, aliceNode, aliceKey)

            net.runNetwork() // Clear network map registration messages

            val aliceTxStream = aliceNode.storage.validatedTransactions.track().second
            // TODO: Had to put this temp val here to avoid compiler crash. Put back inside [databaseTransaction] if the compiler stops crashing.
            val aliceMappingsStorage = aliceNode.storage.stateMachineRecordedTransactionMapping
            val aliceTxMappings = databaseTransaction(aliceNode.database) {
                aliceMappingsStorage.track().second
            }
            val aliceSmId = runBuyerAndSeller("alice's paper".outputStateAndRef()).sellerId

            net.runNetwork()

            // We need to declare this here, if we do it inside [expectEvents] kotlin throws an internal compiler error(!).
            val aliceTxExpectations = sequence(
                    expect { tx: SignedTransaction ->
                        require(tx.id == bobsFakeCash[0].id)
                    },
                    expect { tx: SignedTransaction ->
                        require(tx.id == bobsFakeCash[2].id)
                    },
                    expect { tx: SignedTransaction ->
                        require(tx.id == bobsFakeCash[1].id)
                    }
            )
            aliceTxStream.expectEvents { aliceTxExpectations }
            val aliceMappingExpectations = sequence(
                    expect { mapping: StateMachineTransactionMapping ->
                        require(mapping.stateMachineRunId == aliceSmId)
                        require(mapping.transactionId == bobsFakeCash[0].id)
                    },
                    expect { mapping: StateMachineTransactionMapping ->
                        require(mapping.stateMachineRunId == aliceSmId)
                        require(mapping.transactionId == bobsFakeCash[2].id)
                    },
                    expect { mapping: StateMachineTransactionMapping ->
                        require(mapping.stateMachineRunId == aliceSmId)
                        require(mapping.transactionId == bobsFakeCash[1].id)
                    }
            )
            aliceTxMappings.expectEvents { aliceMappingExpectations }
        }
    }

    @Test
    fun `dependency with error on buyer side`() {
        ledger {
            runWithError(true, false, "at least one asset input")
        }
    }

    @Test
    fun `dependency with error on seller side`() {
        ledger {
            runWithError(false, true, "must be timestamped")
        }
    }

    private data class RunResult(
            // The buyer is not created immediately, only when the seller starts running
            val buyer: Future<FlowStateMachine<*>>,
            val sellerResult: Future<SignedTransaction>,
            val sellerId: StateMachineRunId
    )

    private fun runBuyerAndSeller(assetToSell: StateAndRef<OwnableState>): RunResult {
        val buyerFuture = bobNode.initiateSingleShotFlow(Seller::class) { otherParty ->
            Buyer(otherParty, notaryNode.info.notaryIdentity, 1000.DOLLARS, CommercialPaper.State::class.java)
        }.map { it.stateMachine }
        val seller = Seller(bobNode.info.legalIdentity, notaryNode.info, assetToSell, 1000.DOLLARS, ALICE_KEY)
        val sellerResultFuture = aliceNode.services.startFlow(seller).resultFuture
        return RunResult(buyerFuture, sellerResultFuture, seller.stateMachine.id)
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.runWithError(
            bobError: Boolean,
            aliceError: Boolean,
            expectedMessageSubstring: String
    ) {
        notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
        aliceNode = net.createPartyNode(notaryNode.info.address, ALICE.name, ALICE_KEY)
        bobNode = net.createPartyNode(notaryNode.info.address, BOB.name, BOB_KEY)
        val aliceKey = aliceNode.services.legalIdentityKey
        val bobKey = bobNode.services.legalIdentityKey
        val issuer = MEGA_CORP.ref(1, 2, 3)

        val bobsBadCash = fillUpForBuyer(bobError, bobKey.public.composite, notaryNode.info.notaryIdentity).second
        val alicesFakePaper = fillUpForSeller(aliceError, aliceNode.info.legalIdentity.owningKey,
                1200.DOLLARS `issued by` issuer, null, notaryNode.info.notaryIdentity).second

        insertFakeTransactions(bobsBadCash, bobNode, bobKey)
        insertFakeTransactions(alicesFakePaper, aliceNode, aliceKey)

        net.runNetwork() // Clear network map registration messages

        val (bobStateMachine, aliceResult) = runBuyerAndSeller("alice's paper".outputStateAndRef())

        net.runNetwork()

        val e = assertFailsWith<TransactionVerificationException> {
            if (bobError)
                aliceResult.getOrThrow()
            else
                bobStateMachine.getOrThrow().resultFuture.getOrThrow()
        }
        val underlyingMessage = e.rootCause.message!!
        if (expectedMessageSubstring !in underlyingMessage) {
            assertEquals(expectedMessageSubstring, underlyingMessage)
        }
    }

    private fun insertFakeTransactions(
            wtxToSign: List<WireTransaction>,
            node: AbstractNode,
            vararg extraKeys: KeyPair): Map<SecureHash, SignedTransaction> {
        return databaseTransaction(node.database) {
            val signed: List<SignedTransaction> = signAll(wtxToSign, extraKeys.toList() + DUMMY_CASH_ISSUER_KEY)
            node.services.recordTransactions(signed)
            val validatedTransactions = node.services.storageService.validatedTransactions
            if (validatedTransactions is RecordingTransactionStorage) {
                validatedTransactions.records.clear()
            }
            return@databaseTransaction signed.associateBy { it.id }
        }
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.fillUpForBuyer(
            withError: Boolean,
            owner: CompositeKey = BOB_PUBKEY,
            notary: Party): Pair<Vault, List<WireTransaction>> {
        val issuer = DUMMY_CASH_ISSUER
        // Bob (Buyer) has some cash he got from the Bank of Elbonia, Alice (Seller) has some commercial paper she
        // wants to sell to Bob.
        val eb1 = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            // Issued money to itself.
            output("elbonian money 1", notary = notary) { 800.DOLLARS.CASH `issued by` issuer `owned by` MEGA_CORP_PUBKEY }
            output("elbonian money 2", notary = notary) { 1000.DOLLARS.CASH `issued by` issuer `owned by` MEGA_CORP_PUBKEY }
            if (!withError) {
                command(DUMMY_CASH_ISSUER_KEY.public.composite) { Cash.Commands.Issue() }
            } else {
                // Put a broken command on so at least a signature is created
                command(DUMMY_CASH_ISSUER_KEY.public.composite) { Cash.Commands.Move() }
            }
            timestamp(TEST_TX_TIME)
            if (withError) {
                this.fails()
            } else {
                this.verifies()
            }
        }

        // Bob gets some cash onto the ledger from BoE
        val bc1 = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            input("elbonian money 1")
            output("bob cash 1", notary = notary) { 800.DOLLARS.CASH `issued by` issuer `owned by` owner }
            command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
            this.verifies()
        }

        val bc2 = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            input("elbonian money 2")
            output("bob cash 2", notary = notary) { 300.DOLLARS.CASH `issued by` issuer `owned by` owner }
            output(notary = notary) { 700.DOLLARS.CASH `issued by` issuer `owned by` MEGA_CORP_PUBKEY }   // Change output.
            command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
            this.verifies()
        }

        val vault = Vault(listOf("bob cash 1".outputStateAndRef(), "bob cash 2".outputStateAndRef()))
        return Pair(vault, listOf(eb1, bc1, bc2))
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.fillUpForSeller(
            withError: Boolean,
            owner: CompositeKey,
            amount: Amount<Issued<Currency>>,
            attachmentID: SecureHash?,
            notary: Party): Pair<Vault, List<WireTransaction>> {
        val ap = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            output("alice's paper", notary = notary) {
                CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), owner, amount, TEST_TX_TIME + 7.days)
            }
            command(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
            if (!withError)
                timestamp(time = TEST_TX_TIME)
            if (attachmentID != null)
                attachment(attachmentID)
            if (withError) {
                this.fails()
            } else {
                this.verifies()
            }
        }

        val vault = Vault(listOf("alice's paper".outputStateAndRef()))
        return Pair(vault, listOf(ap))
    }


    class RecordingTransactionStorage(val database: Database, val delegate: TransactionStorage) : TransactionStorage {
        override fun track(): Pair<List<SignedTransaction>, Observable<SignedTransaction>> {
            return databaseTransaction(database) {
                delegate.track()
            }
        }

        val records: MutableList<TxRecord> = Collections.synchronizedList(ArrayList<TxRecord>())
        override val updates: Observable<SignedTransaction>
            get() = delegate.updates

        override fun addTransaction(transaction: SignedTransaction): Boolean {
            databaseTransaction(database) {
                records.add(TxRecord.Add(transaction))
                delegate.addTransaction(transaction)
            }
            return true
        }

        override fun getTransaction(id: SecureHash): SignedTransaction? {
            return databaseTransaction(database) {
                records.add(TxRecord.Get(id))
                delegate.getTransaction(id)
            }
        }
    }

    interface TxRecord {
        data class Add(val transaction: SignedTransaction) : TxRecord
        data class Get(val id: SecureHash) : TxRecord
    }

}
