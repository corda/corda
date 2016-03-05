/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import contracts.Cash
import contracts.CommercialPaper
import contracts.protocols.TwoPartyTradeProtocol
import core.*
import core.crypto.SecureHash
import core.node.MockNetwork
import core.node.NodeAttachmentStorage
import core.node.NodeWalletService
import core.testutils.*
import core.utilities.BriefLogFormatter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
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

// TODO: Refactor this test to use the MockNode class, which will clean this file up significantly.

/**
 * In this example, Alice wishes to sell her commercial paper to Bob in return for $1,000,000 and they wish to do
 * it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Alice and Bob already found each other via some market, and have agreed the details already.
 */
class TwoPartyTradeProtocolTests : TestWithInMemoryNetwork() {
    lateinit var net: MockNetwork

    @Before
    fun before() {
        net = MockNetwork(false)
        BriefLogFormatter.loggingOn("platform.trade", "core.TransactionGroup", "recordingmap")
    }

    @After
    fun after() {
        BriefLogFormatter.loggingOff("platform.trade", "core.TransactionGroup", "recordingmap")
    }

    @Test
    fun `trade cash for commercial paper`() {
        // We run this in parallel threads to help catch any race conditions that may exist. The other tests
        // we run in the unit test thread exclusively to speed things up, ensure deterministic results and
        // allow interruption half way through.
        net = MockNetwork(true)
        transactionGroupFor<ContractState> {
            val (aliceNode, bobNode) = net.createTwoNodes()
            (bobNode.wallet as NodeWalletService).fillWithSomeTestCash(2000.DOLLARS)
            val alicesFakePaper = fillUpForSeller(false, aliceNode.legallyIdentifableAddress.identity, null).second

            insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

            val buyerSessionID = random63BitValue()

            val aliceResult = TwoPartyTradeProtocol.runSeller(
                    aliceNode.smm,
                    aliceNode.legallyIdentifableAddress,
                    bobNode.net.myAddress,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            val bobResult = TwoPartyTradeProtocol.runBuyer(
                    bobNode.smm,
                    aliceNode.legallyIdentifableAddress,
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
        }
    }

    @Test
    fun shutdownAndRestore() {
        transactionGroupFor<ContractState> {
            var (aliceNode, bobNode) = net.createTwoNodes()
            val aliceAddr = aliceNode.net.myAddress
            val bobAddr = bobNode.net.myAddress as InMemoryMessagingNetwork.Handle
            val timestamperAddr = aliceNode.legallyIdentifableAddress

            (bobNode.wallet as NodeWalletService).fillWithSomeTestCash(2000.DOLLARS)
            val alicesFakePaper = fillUpForSeller(false, timestamperAddr.identity, null).second

            insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

            // Horrible Gradle/Kryo/Quasar FUBAR workaround: just skip these tests when run under Gradle for now.
            // TODO: Fix this once Quasar issue 153 is resolved.
            if (!bobNode.smm.checkpointing)
                return

            val buyerSessionID = random63BitValue()

            val aliceFuture = TwoPartyTradeProtocol.runSeller(
                    aliceNode.smm,
                    timestamperAddr,
                    bobAddr,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            TwoPartyTradeProtocol.runBuyer(
                    bobNode.smm,
                    timestamperAddr,
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
            // Save the state machine to "disk" (i.e. a variable, here)
            val savedCheckpoints = HashMap(bobNode.storage.getMap<Any, Any>("state machines"))
            assertEquals(1, savedCheckpoints.size)

            // .. and let's imagine that Bob's computer has a power cut. He now has nothing now beyond what was on disk.
            bobNode.stop()

            // Alice doesn't know that and carries on: she wants to know about the cash transactions he's trying to use.
            // She will wait around until Bob comes back.
            assertTrue(pumpAlice())

            // ... bring the node back up ... the act of constructing the SMM will re-register the message handlers
            // that Bob was waiting on before the reboot occurred.
            bobNode = net.createNode(timestamperAddr, bobAddr.id) { path, nodeConfiguration, net, timestamper ->
                object : MockNetwork.MockNode(path, nodeConfiguration, net, timestamper, bobAddr.id) {
                    override fun initialiseStorageService(dir: Path): StorageService {
                        val ss = super.initialiseStorageService(dir)
                        val smMap = ss.getMap<Any, Any>("state machines")
                        smMap.putAll(savedCheckpoints)
                        return ss
                    }
                }
            }

            // Find the future representing the result of this state machine again.
            var bobFuture = bobNode.smm.findStateMachines(TwoPartyTradeProtocol.Buyer::class.java).single().second

            // And off we go again.
            net.runNetwork()

            // Bob is now finished and has the same transaction as Alice.
            assertEquals(bobFuture.get(), aliceFuture.get())


            assertTrue(bobNode.smm.findStateMachines(TwoPartyTradeProtocol.Buyer::class.java).isEmpty())
        }
    }

    // Creates a mock node with an overridden storage service that uses a RecordingMap, that lets us test the order
    // of gets and puts.
    private fun makeNodeWithTracking(name: String): MockNetwork.MockNode {
        // Create a node in the mock network ...
        return net.createNode(null) { path, config, net, tsNode ->
            object : MockNetwork.MockNode(path, config, net, tsNode) {
                // That constructs the storage service object in a customised way ...
                override fun constructStorageService(attachments: NodeAttachmentStorage, identity: Party, keypair: KeyPair): StorageServiceImpl {
                    // By tweaking the standard StorageServiceImpl class ...
                    return object : StorageServiceImpl(attachments, identity, keypair) {
                        // To use RecordingMaps instead of ordinary HashMaps.
                        @Suppress("UNCHECKED_CAST")
                        override fun <K, V> getMap(tableName: String): MutableMap<K, V> {
                            synchronized(tables) {
                                return tables.getOrPut(tableName) {
                                    val map = Collections.synchronizedMap(HashMap<Any, Any>())
                                    RecordingMap(map, LoggerFactory.getLogger("recordingmap.$name"))
                                } as MutableMap<K, V>
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun checkDependenciesOfSaleAssetAreResolved() {
        transactionGroupFor<ContractState> {
            val aliceNode = makeNodeWithTracking("alice")
            val timestamperAddr = aliceNode.legallyIdentifableAddress
            val bobNode = makeNodeWithTracking("bob")

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
            val alicesFakePaper = fillUpForSeller(false, timestamperAddr.identity, attachmentID).second
            val alicesSignedTxns = insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

            val buyerSessionID = random63BitValue()

            TwoPartyTradeProtocol.runSeller(
                    aliceNode.smm,
                    timestamperAddr,
                    bobNode.net.myAddress,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            TwoPartyTradeProtocol.runBuyer(
                    bobNode.smm,
                    timestamperAddr,
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
                        RecordingMap.Get(bobsFakeCash[0].id)
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
        var (aliceNode, bobNode) = net.createTwoNodes()
        val aliceAddr = aliceNode.net.myAddress
        val bobAddr = bobNode.net.myAddress as InMemoryMessagingNetwork.Handle
        val timestamperAddr = aliceNode.legallyIdentifableAddress

        val bobKey = bobNode.keyManagement.freshKey()
        val bobsBadCash = fillUpForBuyer(bobError, bobKey.public).second
        val alicesFakePaper = fillUpForSeller(aliceError, timestamperAddr.identity, null).second

        insertFakeTransactions(bobsBadCash, bobNode.services, bobNode.storage.myLegalIdentityKey, bobKey)
        insertFakeTransactions(alicesFakePaper, aliceNode.services, aliceNode.storage.myLegalIdentityKey)

        val buyerSessionID = random63BitValue()

        val aliceResult = TwoPartyTradeProtocol.runSeller(
                aliceNode.smm,
                timestamperAddr,
                bobAddr,
                lookup("alice's paper"),
                1000.DOLLARS,
                ALICE_KEY,
                buyerSessionID
        )
        val bobResult = TwoPartyTradeProtocol.runBuyer(
                bobNode.smm,
                timestamperAddr,
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
        val txStorage = services.storageService.validatedTransactions
        val signed = signAll(wtxToSign, *extraKeys).associateBy { it.id }
        if (txStorage is RecordingMap) {
            txStorage.putAllUnrecorded(signed)
        } else
            txStorage.putAll(signed)

        try {
            services.walletService.notifyAll(signed.map { it.value.tx })
        } catch(e: Throwable) {
            // TODO: Remove this hack once all the tests are converted to use MockNode.
        }

        return signed
    }

    private fun TransactionGroupDSL<ContractState>.fillUpForBuyer(withError: Boolean, bobKey: PublicKey = BOB): Pair<Wallet, List<WireTransaction>> {
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
            output("bob cash 1") { 800.DOLLARS.CASH `issued by` MEGA_CORP `owned by` bobKey }
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
        }

        val bc2 = transaction {
            input("elbonian money 2")
            output("bob cash 2") { 300.DOLLARS.CASH `issued by` MEGA_CORP `owned by` bobKey }
            output { 700.DOLLARS.CASH `issued by` MEGA_CORP `owned by` MEGA_CORP_PUBKEY }   // Change output.
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
        }

        val wallet = Wallet(listOf<StateAndRef<Cash.State>>(lookup("bob cash 1"), lookup("bob cash 2")))
        return Pair(wallet, listOf(eb1, bc1, bc2))
    }

    private fun TransactionGroupDSL<ContractState>.fillUpForSeller(withError: Boolean, timestamper: Party, attachmentID: SecureHash?): Pair<Wallet, List<WireTransaction>> {
        val ap = transaction {
            output("alice's paper") {
                CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), ALICE, 1200.DOLLARS, TEST_TX_TIME + 7.days)
            }
            arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
            if (!withError)
                arg(timestamper.owningKey) { TimestampCommand(TEST_TX_TIME, 30.seconds) }
            if (attachmentID != null)
                attachment(attachmentID)
        }

        val wallet = Wallet(listOf<StateAndRef<Cash.State>>(lookup("alice's paper")))
        return Pair(wallet, listOf(ap))
    }
}
