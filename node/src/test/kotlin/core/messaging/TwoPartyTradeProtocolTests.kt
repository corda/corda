package core.messaging

import contracts.Cash
import contracts.CommercialPaper
import core.*
import core.contracts.*
import core.crypto.Party
import core.crypto.SecureHash
import core.node.NodeConfiguration
import core.node.NodeInfo
import core.node.ServiceHub
import core.node.services.NodeAttachmentService
import core.node.services.ServiceType
import core.node.storage.CheckpointStorage
import core.node.subsystems.NodeWalletService
import core.node.subsystems.StorageServiceImpl
import core.node.subsystems.Wallet
import core.node.subsystems.WalletImpl
import core.testing.InMemoryMessagingNetwork
import core.testing.MockNetwork
import core.testutils.*
import core.utilities.BriefLogFormatter
import core.utilities.RecordingMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import protocols.TwoPartyTradeProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.util.concurrent.ExecutionException
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * In this example, Alice wishes to sell her commercial paper to Bob in return for $1,000,000 and they wish to do
 * it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Alice and Bob already found each other via some market, and have agreed the details already.
 */
class TwoPartyTradeProtocolTests {
    lateinit var net: MockNetwork

    @Before
    fun before() {
        net = MockNetwork(false)
        net.identities += MockIdentityService.identities
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
        net = MockNetwork(true)
        transactionGroupFor<ContractState> {
            val notaryNode = net.createNotaryNode(DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
            val aliceNode = net.createPartyNode(notaryNode.info, ALICE.name, ALICE_KEY)
            val bobNode = net.createPartyNode(notaryNode.info, BOB.name, BOB_KEY)

            (bobNode.wallet as NodeWalletService).fillWithSomeTestCash(DUMMY_NOTARY, 2000.DOLLARS)
            val alicesFakePaper = fillUpForSeller(false, aliceNode.storage.myLegalIdentity.owningKey,
                    notaryNode.info.identity, null).second

            insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey, notaryNode.storage.myLegalIdentityKey)

            val buyerSessionID = random63BitValue()

            val aliceResult = TwoPartyTradeProtocol.runSeller(
                    aliceNode.smm,
                    notaryNode.info,
                    bobNode.net.myAddress,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            val bobResult = TwoPartyTradeProtocol.runBuyer(
                    bobNode.smm,
                    notaryNode.info,
                    aliceNode.net.myAddress,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            // TODO: Verify that the result was inserted into the transaction database.
            // assertEquals(bobResult.get(), aliceNode.storage.validatedTransactions[aliceResult.get().id])
            assertEquals(aliceResult.get(), bobResult.get())

            aliceNode.stop()
            bobNode.stop()

            assertThat(aliceNode.storage.checkpointStorage.checkpoints).isEmpty()
            assertThat(bobNode.storage.checkpointStorage.checkpoints).isEmpty()
        }
    }

    @Test
    fun `shutdown and restore`() {
        transactionGroupFor<ContractState> {
            val notaryNode = net.createNotaryNode(DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
            val aliceNode = net.createPartyNode(notaryNode.info, ALICE.name, ALICE_KEY)
            var bobNode = net.createPartyNode(notaryNode.info, BOB.name, BOB_KEY)

            val aliceAddr = aliceNode.net.myAddress
            val bobAddr = bobNode.net.myAddress as InMemoryMessagingNetwork.Handle
            val networkMapAddr = notaryNode.info

            net.runNetwork() // Clear network map registration messages

            (bobNode.wallet as NodeWalletService).fillWithSomeTestCash(DUMMY_NOTARY, 2000.DOLLARS)
            val alicesFakePaper = fillUpForSeller(false, aliceNode.storage.myLegalIdentity.owningKey,
                    notaryNode.info.identity, null).second
            insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

            val buyerSessionID = random63BitValue()

            val aliceFuture = TwoPartyTradeProtocol.runSeller(
                    aliceNode.smm,
                    notaryNode.info,
                    bobAddr,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            TwoPartyTradeProtocol.runBuyer(
                    bobNode.smm,
                    notaryNode.info,
                    aliceAddr,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            // Everything is on this thread so we can now step through the protocol one step at a time.
            // Seller Alice already sent a message to Buyer Bob. Pump once:
            fun pumpAlice() = (aliceNode.net as InMemoryMessagingNetwork.InMemoryMessaging).pump(false)

            fun pumpBob() = (bobNode.net as InMemoryMessagingNetwork.InMemoryMessaging).pump(false)

            pumpBob()

            // Bob sends a couple of queries for the dependencies back to Alice. Alice reponds.
            pumpAlice()
            pumpBob()
            pumpAlice()
            pumpBob()

            // OK, now Bob has sent the partial transaction back to Alice and is waiting for Alice's signature.
            assertThat(bobNode.storage.checkpointStorage.checkpoints).hasSize(1)

            // TODO: remove once validated transactions are persisted to disk
            val recordedTransactions = bobNode.storage.validatedTransactions

            // .. and let's imagine that Bob's computer has a power cut. He now has nothing now beyond what was on disk.
            bobNode.stop()

            // Alice doesn't know that and carries on: she wants to know about the cash transactions he's trying to use.
            // She will wait around until Bob comes back.
            assertNotNull(pumpAlice())

            // ... bring the node back up ... the act of constructing the SMM will re-register the message handlers
            // that Bob was waiting on before the reboot occurred.
            bobNode = net.createNode(networkMapAddr, bobAddr.id, object : MockNetwork.Factory {
                override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, networkMapAddr: NodeInfo?,
                                    advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
                    return MockNetwork.MockNode(dir, config, network, networkMapAddr, advertisedServices, bobAddr.id, BOB_KEY)
                }
            }, true, BOB.name, BOB_KEY)

            // TODO: remove once validated transactions are persisted to disk
            bobNode.storage.validatedTransactions.putAll(recordedTransactions)

            // Find the future representing the result of this state machine again.
            val bobFuture = bobNode.smm.findStateMachines(TwoPartyTradeProtocol.Buyer::class.java).single().second

            // And off we go again.
            net.runNetwork()

            // Bob is now finished and has the same transaction as Alice.
            assertEquals(bobFuture.get(), aliceFuture.get())

            assertThat(bobNode.smm.findStateMachines(TwoPartyTradeProtocol.Buyer::class.java)).isEmpty()
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
                    override fun constructStorageService(attachments: NodeAttachmentService, checkpointStorage: CheckpointStorage, keypair: KeyPair, identity: Party): StorageServiceImpl {
                        // To use RecordingMaps instead of ordinary HashMaps.
                        return StorageServiceImpl(attachments, checkpointStorage, keypair, identity, { tableName -> name })
                    }
                }
            }
        }, true, name, keyPair)
    }

    @Test
    fun checkDependenciesOfSaleAssetAreResolved() {
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

            val bobsFakeCash = fillUpForBuyer(false, bobNode.keyManagement.freshKey().public).second
            val bobsSignedTxns = insertFakeTransactions(bobsFakeCash, bobNode.services)
            val alicesFakePaper = fillUpForSeller(false, aliceNode.storage.myLegalIdentity.owningKey,
                    notaryNode.info.identity, attachmentID).second
            val alicesSignedTxns = insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

            val buyerSessionID = random63BitValue()

            net.runNetwork() // Clear network map registration messages

            TwoPartyTradeProtocol.runSeller(
                    aliceNode.smm,
                    notaryNode.info,
                    bobNode.net.myAddress,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            TwoPartyTradeProtocol.runBuyer(
                    bobNode.smm,
                    notaryNode.info,
                    aliceNode.net.myAddress,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            net.runNetwork()

            run {
                val records = (bobNode.storage.validatedTransactions as RecordingMap).records
                // Check Bobs's database accesses as Bob's cash transactions are downloaded by Alice.
                val expected = listOf(
                        // Buyer Bob is told about Alice's commercial paper, but doesn't know it ..
                        RecordingMap.Get(alicesFakePaper[0].id),
                        // He asks and gets the tx, validates it, sees it's a self issue with no dependencies, stores.
                        RecordingMap.Put(alicesFakePaper[0].id, alicesSignedTxns.values.first()),
                        // Alice gets Bob's proposed transaction and doesn't know his two cash states. She asks, Bob answers.
                        RecordingMap.Get(bobsFakeCash[1].id),
                        RecordingMap.Get(bobsFakeCash[2].id),
                        // Alice notices that Bob's cash txns depend on a third tx she also doesn't know. She asks, Bob answers.
                        RecordingMap.Get(bobsFakeCash[0].id),
                        // Bob wants to verify that the tx has been signed by the correct Notary, which requires looking up an input state
                        RecordingMap.Get(bobsFakeCash[1].id)
                )
                assertEquals(expected, records)

                // Bob has downloaded the attachment.
                bobNode.storage.attachments.openAttachment(attachmentID)!!.openAsJAR().use {
                    it.nextJarEntry
                    val contents = it.reader().readText()
                    assertTrue(contents.contains("Our commercial paper is top notch stuff"))
                }
            }

            // And from Alice's perspective ...
            run {
                val records = (aliceNode.storage.validatedTransactions as RecordingMap).records
                val expected = listOf(
                        // Seller Alice sends her seller info to Bob, who wants to check the asset for sale.
                        // He requests, Alice looks up in her DB to send the tx to Bob
                        RecordingMap.Get(alicesFakePaper[0].id),
                        // Seller Alice gets a proposed tx which depends on Bob's two cash txns and her own tx.
                        RecordingMap.Get(bobsFakeCash[1].id),
                        RecordingMap.Get(bobsFakeCash[2].id),
                        RecordingMap.Get(alicesFakePaper[0].id),
                        // Alice notices that Bob's cash txns depend on a third tx she also doesn't know.
                        RecordingMap.Get(bobsFakeCash[0].id),
                        // Bob answers with the transactions that are now all verifiable, as Alice bottomed out.
                        // Bob's transactions are valid, so she commits to the database
                        RecordingMap.Put(bobsFakeCash[1].id, bobsSignedTxns[bobsFakeCash[1].id]),
                        RecordingMap.Put(bobsFakeCash[2].id, bobsSignedTxns[bobsFakeCash[2].id]),
                        RecordingMap.Put(bobsFakeCash[0].id, bobsSignedTxns[bobsFakeCash[0].id]),
                        // Now she verifies the transaction is contract-valid (not signature valid) which means
                        // looking up the states again.
                        RecordingMap.Get(bobsFakeCash[1].id),
                        RecordingMap.Get(bobsFakeCash[2].id),
                        RecordingMap.Get(alicesFakePaper[0].id),
                        // Alice needs to look up the input states to find out which Notary they point to
                        RecordingMap.Get(bobsFakeCash[1].id),
                        RecordingMap.Get(bobsFakeCash[2].id),
                        RecordingMap.Get(alicesFakePaper[0].id)
                )
                assertEquals(expected, records)
            }
        }
    }

    @Test
    fun `dependency with error on buyer side`() {
        transactionGroupFor<ContractState> {
            runWithError(true, false, "at least one cash input")
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

        val aliceAddr = aliceNode.net.myAddress
        val bobAddr = bobNode.net.myAddress as InMemoryMessagingNetwork.Handle

        val bobKey = bobNode.keyManagement.freshKey()
        val bobsBadCash = fillUpForBuyer(bobError, bobKey.public).second
        val alicesFakePaper = fillUpForSeller(aliceError, aliceNode.storage.myLegalIdentity.owningKey, notaryNode.info.identity, null).second

        insertFakeTransactions(bobsBadCash, bobNode.services, bobNode.storage.myLegalIdentityKey, bobNode.storage.myLegalIdentityKey)
        insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

        val buyerSessionID = random63BitValue()

        net.runNetwork() // Clear network map registration messages

        val aliceResult = TwoPartyTradeProtocol.runSeller(
                aliceNode.smm,
                notaryNode.info,
                bobAddr,
                lookup("alice's paper"),
                1000.DOLLARS,
                ALICE_KEY,
                buyerSessionID
        )
        val bobResult = TwoPartyTradeProtocol.runBuyer(
                bobNode.smm,
                notaryNode.info,
                aliceAddr,
                1000.DOLLARS,
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
        services.recordTransactions(signed, skipRecordingMap = true)
        return signed.associateBy { it.id }
    }

    private fun TransactionGroupDSL<ContractState>.fillUpForBuyer(withError: Boolean, owner: PublicKey = BOB_PUBKEY): Pair<Wallet, List<WireTransaction>> {
        // Bob (Buyer) has some cash he got from the Bank of Elbonia, Alice (Seller) has some commercial paper she
        // wants to sell to Bob.

        val eb1 = transaction {
            // Issued money to itself.
            output("elbonian money 1") { 800.DOLLARS.CASH `issued by` MEGA_CORP `owned by` MEGA_CORP_PUBKEY }
            output("elbonian money 2") { 1000.DOLLARS.CASH `issued by` MEGA_CORP `owned by` MEGA_CORP_PUBKEY }
            if (!withError)
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Issue() }
            timestamp(TEST_TX_TIME)
        }

        // Bob gets some cash onto the ledger from BoE
        val bc1 = transaction {
            input("elbonian money 1")
            output("bob cash 1") { 800.DOLLARS.CASH `issued by` MEGA_CORP `owned by` owner }
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
        }

        val bc2 = transaction {
            input("elbonian money 2")
            output("bob cash 2") { 300.DOLLARS.CASH `issued by` MEGA_CORP `owned by` owner }
            output { 700.DOLLARS.CASH `issued by` MEGA_CORP `owned by` MEGA_CORP_PUBKEY }   // Change output.
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
        }

        val wallet = WalletImpl(listOf<StateAndRef<Cash.State>>(lookup("bob cash 1"), lookup("bob cash 2")))
        return Pair(wallet, listOf(eb1, bc1, bc2))
    }

    private fun TransactionGroupDSL<ContractState>.fillUpForSeller(withError: Boolean, owner: PublicKey, notary: Party, attachmentID: SecureHash?): Pair<Wallet, List<WireTransaction>> {
        val ap = transaction {
            output("alice's paper") {
                CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), owner, 1200.DOLLARS, TEST_TX_TIME + 7.days, notary)
            }
            arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
            if (!withError)
                arg(notary.owningKey) { TimestampCommand(TEST_TX_TIME, 30.seconds) }
            if (attachmentID != null)
                attachment(attachmentID)
        }

        val wallet = WalletImpl(listOf<StateAndRef<Cash.State>>(lookup("alice's paper")))
        return Pair(wallet, listOf(ap))
    }
}
