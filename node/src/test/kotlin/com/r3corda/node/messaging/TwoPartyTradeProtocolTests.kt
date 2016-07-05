package com.r3corda.node.messaging

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.contracts.CommercialPaper
import com.r3corda.contracts.asset.Cash
import com.r3corda.contracts.testing.CASH
import com.r3corda.contracts.testing.`issued by`
import com.r3corda.contracts.testing.`owned by`
import com.r3corda.contracts.testing.fillWithSomeTestCash
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.days
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.node.services.TransactionStorage
import com.r3corda.core.node.services.Wallet
import com.r3corda.core.random63BitValue
import com.r3corda.core.seconds
import com.r3corda.core.testing.*
import com.r3corda.core.utilities.BriefLogFormatter
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.network.InMemoryMessagingNetwork
import com.r3corda.node.services.persistence.NodeAttachmentService
import com.r3corda.node.services.persistence.PerFileTransactionStorage
import com.r3corda.node.services.persistence.StorageServiceImpl
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.protocols.TwoPartyTradeProtocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ExecutionException
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
class TwoPartyTradeProtocolTests {
    lateinit var net: MockNetwork

    private fun runSeller(smm: StateMachineManager, notary: NodeInfo,
                          otherSide: Party, assetToSell: StateAndRef<OwnableState>, price: Amount<Issued<Currency>>,
                          myKeyPair: KeyPair, buyerSessionID: Long): ListenableFuture<SignedTransaction> {
        val seller = TwoPartyTradeProtocol.Seller(otherSide, notary, assetToSell, price, myKeyPair, buyerSessionID)
        return smm.add("${TwoPartyTradeProtocol.TOPIC}.seller", seller)
    }

    private fun runBuyer(smm: StateMachineManager, notaryNode: NodeInfo,
                         otherSide: Party, acceptablePrice: Amount<Issued<Currency>>, typeToBuy: Class<out OwnableState>,
                         sessionID: Long): ListenableFuture<SignedTransaction> {
        val buyer = TwoPartyTradeProtocol.Buyer(otherSide, notaryNode.identity, acceptablePrice, typeToBuy, sessionID)
        return smm.add("${TwoPartyTradeProtocol.TOPIC}.buyer", buyer)
    }

    @Before
    fun before() {
        net = MockNetwork(false)
        net.identities += MOCK_IDENTITY_SERVICE.identities
        BriefLogFormatter.loggingOn("platform.trade", "core.contract.TransactionGroup", "recordingmap")
    }

    @After
    fun after() {
        BriefLogFormatter.loggingOff("platform.trade", "core.contract.TransactionGroup", "recordingmap")
    }

    @Test
    fun `trade cash for commercial paper`() {
        // We run this in parallel threads to help catch any race conditions that may exist. The other tests
        // we run in the unit test thread exclusively to speed things up, ensure deterministic results and
        // allow interruption half way through.
        net = MockNetwork(false, true)
        transactionGroupFor<ContractState> {
            val notaryNode = net.createNotaryNode(DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
            val aliceNode = net.createPartyNode(notaryNode.info, ALICE.name, ALICE_KEY)
            val bobNode = net.createPartyNode(notaryNode.info, BOB.name, BOB_KEY)

            bobNode.services.fillWithSomeTestCash(2000.DOLLARS)
            val issuer = bobNode.services.storageService.myLegalIdentity.ref(0)
            val alicesFakePaper = fillUpForSeller(false, aliceNode.storage.myLegalIdentity.owningKey,
                    1200.DOLLARS `issued by` issuer, notaryNode.info.identity, null).second

            insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey, notaryNode.storage.myLegalIdentityKey)

            val buyerSessionID = random63BitValue()

            // We start the Buyer first, as the Seller sends the first message
            val bobResult = runBuyer(
                    bobNode.smm,
                    notaryNode.info,
                    aliceNode.info.identity,
                    1000.DOLLARS `issued by` issuer,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )
            val aliceResult = runSeller(
                    aliceNode.smm,
                    notaryNode.info,
                    bobNode.info.identity,
                    lookup("alice's paper"),
                    1000.DOLLARS `issued by` issuer,
                    ALICE_KEY,
                    buyerSessionID
            )

            // TODO: Verify that the result was inserted into the transaction database.
            // assertEquals(bobResult.get(), aliceNode.storage.validatedTransactions[aliceResult.get().id])
            assertEquals(aliceResult.get(), bobResult.get())

            aliceNode.stop()
            bobNode.stop()

            assertThat(aliceNode.checkpointStorage.checkpoints).isEmpty()
            assertThat(bobNode.checkpointStorage.checkpoints).isEmpty()
        }
    }

    @Test
    fun `shutdown and restore`() {
        transactionGroupFor<ContractState> {
            val notaryNode = net.createNotaryNode(DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
            val aliceNode = net.createPartyNode(notaryNode.info, ALICE.name, ALICE_KEY)
            var bobNode = net.createPartyNode(notaryNode.info, BOB.name, BOB_KEY)

            val bobAddr = bobNode.net.myAddress as InMemoryMessagingNetwork.Handle
            val networkMapAddr = notaryNode.info
            val issuer = bobNode.services.storageService.myLegalIdentity.ref(0)

            net.runNetwork() // Clear network map registration messages

            bobNode.services.fillWithSomeTestCash(2000.DOLLARS)
            val alicesFakePaper = fillUpForSeller(false, aliceNode.storage.myLegalIdentity.owningKey,
                    1200.DOLLARS `issued by` issuer, notaryNode.info.identity, null).second
            insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

            val buyerSessionID = random63BitValue()

            val aliceFuture = runSeller(
                    aliceNode.smm,
                    notaryNode.info,
                    bobNode.info.identity,
                    lookup("alice's paper"),
                    1000.DOLLARS `issued by` issuer,
                    ALICE_KEY,
                    buyerSessionID
            )
            runBuyer(
                    bobNode.smm,
                    notaryNode.info,
                    aliceNode.info.identity,
                    1000.DOLLARS `issued by` issuer,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            // Everything is on this thread so we can now step through the protocol one step at a time.
            // Seller Alice already sent a message to Buyer Bob. Pump once:
            fun pumpAlice() = (aliceNode.net as InMemoryMessagingNetwork.InMemoryMessaging).pumpReceive(false)

            fun pumpBob() = (bobNode.net as InMemoryMessagingNetwork.InMemoryMessaging).pumpReceive(false)

            pumpBob()

            // Bob sends a couple of queries for the dependencies back to Alice. Alice reponds.
            pumpAlice()
            pumpBob()
            pumpAlice()
            pumpBob()

            // OK, now Bob has sent the partial transaction back to Alice and is waiting for Alice's signature.
            assertThat(bobNode.checkpointStorage.checkpoints).hasSize(1)

            val bobTransactionsBeforeCrash = (bobNode.storage.validatedTransactions as PerFileTransactionStorage).transactions
            assertThat(bobTransactionsBeforeCrash).isNotEmpty()

            // .. and let's imagine that Bob's computer has a power cut. He now has nothing now beyond what was on disk.
            bobNode.stop()

            // Alice doesn't know that and carries on: she wants to know about the cash transactions he's trying to use.
            // She will wait around until Bob comes back.
            assertThat(pumpAlice()).isNotNull()

            // ... bring the node back up ... the act of constructing the SMM will re-register the message handlers
            // that Bob was waiting on before the reboot occurred.
            bobNode = net.createNode(networkMapAddr, bobAddr.id, object : MockNetwork.Factory {
                override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, networkMapAddr: NodeInfo?,
                                    advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
                    return MockNetwork.MockNode(dir, config, network, networkMapAddr, advertisedServices, bobAddr.id, BOB_KEY)
                }
            }, true, BOB.name, BOB_KEY)

            // Find the future representing the result of this state machine again.
            val bobFuture = bobNode.smm.findStateMachines(TwoPartyTradeProtocol.Buyer::class.java).single().second

            // And off we go again.
            net.runNetwork()

            // Bob is now finished and has the same transaction as Alice.
            assertThat(bobFuture.get()).isEqualTo(aliceFuture.get())

            assertThat(bobNode.smm.findStateMachines(TwoPartyTradeProtocol.Buyer::class.java)).isEmpty()

            assertThat(bobNode.checkpointStorage.checkpoints).isEmpty()
            assertThat(aliceNode.checkpointStorage.checkpoints).isEmpty()

            val restoredBobTransactions = bobTransactionsBeforeCrash.filter { bobNode.storage.validatedTransactions.getTransaction(it.id) != null }
            assertThat(restoredBobTransactions).containsAll(bobTransactionsBeforeCrash)
        }
    }

    // Creates a mock node with an overridden storage service that uses a RecordingMap, that lets us test the order
    // of gets and puts.
    private fun makeNodeWithTracking(networkMapAddr: NodeInfo?, name: String, keyPair: KeyPair): MockNetwork.MockNode {
        // Create a node in the mock network ...
        return net.createNode(networkMapAddr, -1, object : MockNetwork.Factory {
            override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, networkMapAddr: NodeInfo?,
                                advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
                return object : MockNetwork.MockNode(dir, config, network, networkMapAddr, advertisedServices, id, keyPair) {
                    // That constructs the storage service object in a customised way ...
                    override fun constructStorageService(attachments: NodeAttachmentService,
                                                         transactionStorage: TransactionStorage,
                                                         keypair: KeyPair,
                                                         identity: Party): StorageServiceImpl {
                        return StorageServiceImpl(attachments, RecordingTransactionStorage(transactionStorage), keypair, identity)
                    }
                }
            }
        }, true, name, keyPair)
    }

    @Test
    fun `check dependencies of sale asset are resolved`() {
        transactionGroupFor<ContractState> {
            val notaryNode = net.createNotaryNode(DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
            val aliceNode = makeNodeWithTracking(notaryNode.info, ALICE.name, ALICE_KEY)
            val bobNode = makeNodeWithTracking(notaryNode.info, BOB.name, BOB_KEY)

            // Insert a prospectus type attachment into the commercial paper transaction.
            val stream = ByteArrayOutputStream()
            JarOutputStream(stream).use {
                it.putNextEntry(ZipEntry("Prospectus.txt"))
                it.write("Our commercial paper is top notch stuff".toByteArray())
                it.closeEntry()
            }
            val attachmentID = aliceNode.storage.attachments.importAttachment(ByteArrayInputStream(stream.toByteArray()))

            val issuer = MEGA_CORP.ref(1)
            val bobsFakeCash = fillUpForBuyer(false, bobNode.keyManagement.freshKey().public, issuer).second
            val bobsSignedTxns = insertFakeTransactions(bobsFakeCash, bobNode.services)
            val alicesFakePaper = fillUpForSeller(false, aliceNode.storage.myLegalIdentity.owningKey,
                    1200.DOLLARS `issued by` issuer, notaryNode.info.identity, attachmentID).second
            val alicesSignedTxns = insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

            val buyerSessionID = random63BitValue()

            net.runNetwork() // Clear network map registration messages

            runSeller(
                    aliceNode.smm,
                    notaryNode.info,
                    bobNode.info.identity,
                    lookup("alice's paper"),
                    1000.DOLLARS `issued by` issuer,
                    ALICE_KEY,
                    buyerSessionID
            )
            runBuyer(
                    bobNode.smm,
                    notaryNode.info,
                    aliceNode.info.identity,
                    1000.DOLLARS `issued by` issuer,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            net.runNetwork()

            run {
                val records = (bobNode.storage.validatedTransactions as RecordingTransactionStorage).records
                // Check Bobs's database accesses as Bob's cash transactions are downloaded by Alice.
                assertThat(records).containsExactly(
                        // Buyer Bob is told about Alice's commercial paper, but doesn't know it ..
                        TxRecord.Get(alicesFakePaper[0].id),
                        // He asks and gets the tx, validates it, sees it's a self issue with no dependencies, stores.
                        TxRecord.Add(alicesSignedTxns.values.first()),
                        // Alice gets Bob's proposed transaction and doesn't know his two cash states. She asks, Bob answers.
                        TxRecord.Get(bobsFakeCash[1].id),
                        TxRecord.Get(bobsFakeCash[2].id),
                        // Alice notices that Bob's cash txns depend on a third tx she also doesn't know. She asks, Bob answers.
                        TxRecord.Get(bobsFakeCash[0].id),
                        // Bob wants to verify that the tx has been signed by the correct Notary, which requires looking up an input state
                        TxRecord.Get(bobsFakeCash[1].id)
                )

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
                assertThat(records).containsExactly(
                        // Seller Alice sends her seller info to Bob, who wants to check the asset for sale.
                        // He requests, Alice looks up in her DB to send the tx to Bob
                        TxRecord.Get(alicesFakePaper[0].id),
                        // Seller Alice gets a proposed tx which depends on Bob's two cash txns and her own tx.
                        TxRecord.Get(bobsFakeCash[1].id),
                        TxRecord.Get(bobsFakeCash[2].id),
                        TxRecord.Get(alicesFakePaper[0].id),
                        // Alice notices that Bob's cash txns depend on a third tx she also doesn't know.
                        TxRecord.Get(bobsFakeCash[0].id),
                        // Bob answers with the transactions that are now all verifiable, as Alice bottomed out.
                        // Bob's transactions are valid, so she commits to the database
                        TxRecord.Add(bobsSignedTxns[bobsFakeCash[1].id]!!),
                        TxRecord.Add(bobsSignedTxns[bobsFakeCash[2].id]!!),
                        TxRecord.Add(bobsSignedTxns[bobsFakeCash[0].id]!!),
                        // Now she verifies the transaction is contract-valid (not signature valid) which means
                        // looking up the states again.
                        TxRecord.Get(bobsFakeCash[1].id),
                        TxRecord.Get(bobsFakeCash[2].id),
                        TxRecord.Get(alicesFakePaper[0].id),
                        // Alice needs to look up the input states to find out which Notary they point to
                        TxRecord.Get(bobsFakeCash[1].id),
                        TxRecord.Get(bobsFakeCash[2].id),
                        TxRecord.Get(alicesFakePaper[0].id)
                )
            }
        }
    }

    @Test
    fun `dependency with error on buyer side`() {
        transactionGroupFor<ContractState> {
            runWithError(true, false, "at least one asset input")
        }
    }

    @Test
    fun `dependency with error on seller side`() {
        transactionGroupFor<ContractState> {
            runWithError(false, true, "must be timestamped")
        }
    }

    private fun TransactionGroupDSL<ContractState>.runWithError(bobError: Boolean, aliceError: Boolean,
                                                                expectedMessageSubstring: String) {
        val notaryNode = net.createNotaryNode(DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
        val aliceNode = net.createPartyNode(notaryNode.info, ALICE.name, ALICE_KEY)
        val bobNode = net.createPartyNode(notaryNode.info, BOB.name, BOB_KEY)
        val issuer = MEGA_CORP.ref(1, 2, 3)

        val bobKey = bobNode.keyManagement.freshKey()
        val bobsBadCash = fillUpForBuyer(bobError, bobKey.public).second
        val alicesFakePaper = fillUpForSeller(aliceError, aliceNode.storage.myLegalIdentity.owningKey,
                1200.DOLLARS `issued by` issuer, notaryNode.info.identity, null).second

        insertFakeTransactions(bobsBadCash, bobNode.services, bobNode.storage.myLegalIdentityKey, bobNode.storage.myLegalIdentityKey)
        insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

        val buyerSessionID = random63BitValue()

        net.runNetwork() // Clear network map registration messages

        val aliceResult = runSeller(
                aliceNode.smm,
                notaryNode.info,
                bobNode.info.identity,
                lookup("alice's paper"),
                1000.DOLLARS `issued by` issuer,
                ALICE_KEY,
                buyerSessionID
        )
        val bobResult = runBuyer(
                bobNode.smm,
                notaryNode.info,
                aliceNode.info.identity,
                1000.DOLLARS `issued by` issuer,
                CommercialPaper.State::class.java,
                buyerSessionID
        )

        net.runNetwork()

        val e = assertFailsWith<ExecutionException> {
            if (bobError)
                aliceResult.get()
            else
                bobResult.get()
        }
        assertTrue(e.cause is TransactionVerificationException)
        assertTrue(e.cause!!.cause!!.message!!.contains(expectedMessageSubstring))
    }

    private fun TransactionGroupDSL<ContractState>.insertFakeTransactions(wtxToSign: List<WireTransaction>,
                                                                          services: ServiceHub,
                                                                          vararg extraKeys: KeyPair): Map<SecureHash, SignedTransaction> {
        val signed: List<SignedTransaction> = signAll(wtxToSign, *extraKeys)
        services.recordTransactions(signed)
        val validatedTransactions = services.storageService.validatedTransactions
        if (validatedTransactions is RecordingTransactionStorage) {
            validatedTransactions.records.clear()
        }
        return signed.associateBy { it.id }
    }

    private fun TransactionGroupDSL<ContractState>.fillUpForBuyer(withError: Boolean,
                                                                  owner: PublicKey = BOB_PUBKEY,
                                                                  issuer: PartyAndReference = MEGA_CORP.ref(1)): Pair<Wallet, List<WireTransaction>> {
        // Bob (Buyer) has some cash he got from the Bank of Elbonia, Alice (Seller) has some commercial paper she
        // wants to sell to Bob.

        val eb1 = transaction {
            // Issued money to itself.
            output("elbonian money 1") { 800.DOLLARS.CASH `issued by` issuer `owned by` MEGA_CORP_PUBKEY }
            output("elbonian money 2") { 1000.DOLLARS.CASH `issued by` issuer `owned by` MEGA_CORP_PUBKEY }
            if (!withError)
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Issue() }
            timestamp(TEST_TX_TIME)
        }

        // Bob gets some cash onto the ledger from BoE
        val bc1 = transaction {
            input("elbonian money 1")
            output("bob cash 1") { 800.DOLLARS.CASH `issued by` issuer `owned by` owner }
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
        }

        val bc2 = transaction {
            input("elbonian money 2")
            output("bob cash 2") { 300.DOLLARS.CASH `issued by` issuer `owned by` owner }
            output { 700.DOLLARS.CASH `issued by` issuer `owned by` MEGA_CORP_PUBKEY }   // Change output.
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
        }

        val wallet = Wallet(listOf<StateAndRef<Cash.State>>(lookup("bob cash 1"), lookup("bob cash 2")))
        return Pair(wallet, listOf(eb1, bc1, bc2))
    }

    private fun TransactionGroupDSL<ContractState>.fillUpForSeller(withError: Boolean,
                                                                   owner: PublicKey,
                                                                   amount: Amount<Issued<Currency>>,
                                                                   notary: Party,
                                                                   attachmentID: SecureHash?): Pair<Wallet, List<WireTransaction>> {
        val ap = transaction {
            output("alice's paper") {
                CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), owner, amount, TEST_TX_TIME + 7.days)
            }
            arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
            if (!withError)
                arg(notary.owningKey) { TimestampCommand(TEST_TX_TIME, 30.seconds) }
            if (attachmentID != null)
                attachment(attachmentID)
        }

        val wallet = Wallet(listOf<StateAndRef<Cash.State>>(lookup("alice's paper")))
        return Pair(wallet, listOf(ap))
    }


    class RecordingTransactionStorage(val delegate: TransactionStorage) : TransactionStorage {

        val records = Collections.synchronizedList(ArrayList<TxRecord>())

        override fun addTransaction(transaction: SignedTransaction) {
            records.add(TxRecord.Add(transaction))
            delegate.addTransaction(transaction)
        }

        override fun getTransaction(id: SecureHash): SignedTransaction? {
            records.add(TxRecord.Get(id))
            return delegate.getTransaction(id)
        }
    }

    interface TxRecord {
        data class Add(val transaction: SignedTransaction) : TxRecord
        data class Get(val id: SecureHash): TxRecord
    }


}
