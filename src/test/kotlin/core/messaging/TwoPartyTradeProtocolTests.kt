/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import com.google.common.util.concurrent.MoreExecutors
import contracts.Cash
import contracts.CommercialPaper
import contracts.protocols.TwoPartyTradeProtocol
import core.*
import core.crypto.SecureHash
import core.testutils.*
import core.utilities.BriefLogFormatter
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// TODO: Refactor this test some more to cut down on messy setup duplication.

/**
 * In this example, Alice wishes to sell her commercial paper to Bob in return for $1,000,000 and they wish to do
 * it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Alice and Bob already found each other via some market, and have agreed the details already.
 */
class TwoPartyTradeProtocolTests : TestWithInMemoryNetwork() {
    @Before
    fun before() {
        BriefLogFormatter.initVerbose("platform.trade", "core.TransactionGroup", "recordingmap")
    }

    @Test
    fun `trade cash for commercial paper`() {
        // We run this in parallel threads to help catch any race conditions that may exist. The other tests
        // we run in the unit test thread exclusively to speed things up, ensure deterministic results and
        // allow interruption half way through.
        val backgroundThread = Executors.newSingleThreadExecutor()
        transactionGroupFor<ContractState> {
            val (bobsWallet, bobsFakeCash) = fillUpForBuyer(false)
            val alicesFakePaper = fillUpForSeller(false).second

            val (alicesAddress, alicesNode) = makeNode(inBackground = true)
            val (bobsAddress, bobsNode) = makeNode(inBackground = true)
            val timestamper = network.setupTimestampingNode(false).first

            val alicesServices = MockServices(net = alicesNode)
            val bobsServices = MockServices(
                    wallet = MockWalletService(bobsWallet.states),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode,
                    storage = MockStorageService()
            )
            loadFakeTxnsIntoStorage(bobsFakeCash, bobsServices.storageService)
            loadFakeTxnsIntoStorage(alicesFakePaper, alicesServices.storageService)

            val buyerSessionID = random63BitValue()

            val aliceResult = TwoPartyTradeProtocol.runSeller(
                    StateMachineManager(alicesServices, backgroundThread),
                    timestamper,
                    bobsAddress,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            val bobResult = TwoPartyTradeProtocol.runBuyer(
                    StateMachineManager(bobsServices, backgroundThread),
                    timestamper,
                    alicesAddress,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            assertEquals(aliceResult.get(), bobResult.get())

            txns.add(aliceResult.get().tx)
            verify()
        }
        backgroundThread.shutdown()
    }

    @Test
    fun `shut down and restore`() {
        transactionGroupFor<ContractState> {
            val (wallet, bobsFakeCash) = fillUpForBuyer(false)
            val alicesFakePaper = fillUpForSeller(false).second

            val (alicesAddress, alicesNode) = makeNode()
            var (bobsAddress, bobsNode) = makeNode()
            val timestamper = network.setupTimestampingNode(true)

            val bobsStorage = MockStorageService()

            val alicesServices = MockServices(wallet = null, keyManagement = null, net = alicesNode)
            var bobsServices = MockServices(
                    wallet = MockWalletService(wallet.states),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode,
                    storage = bobsStorage
            )
            loadFakeTxnsIntoStorage(bobsFakeCash, bobsStorage)
            loadFakeTxnsIntoStorage(alicesFakePaper, alicesServices.storageService)

            val smmBuyer = StateMachineManager(bobsServices, MoreExecutors.directExecutor())

            // Horrible Gradle/Kryo/Quasar FUBAR workaround: just skip these tests when run under Gradle for now.
            if (!smmBuyer.checkpointing)
                return

            val buyerSessionID = random63BitValue()

            TwoPartyTradeProtocol.runSeller(
                    StateMachineManager(alicesServices, MoreExecutors.directExecutor()),
                    timestamper.first,
                    bobsAddress,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            TwoPartyTradeProtocol.runBuyer(
                    smmBuyer,
                    timestamper.first,
                    alicesAddress,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            // Everything is on this thread so we can now step through the protocol one step at a time.
            // Seller Alice already sent a message to Buyer Bob. Pump once:
            bobsNode.pump(false)

            // Bob sends a couple of queries for the dependencies back to Alice. Alice reponds.
            alicesNode.pump(false)
            bobsNode.pump(false)
            alicesNode.pump(false)
            bobsNode.pump(false)

            // OK, now Bob has sent the partial transaction back to Alice and is waiting for Alice's signature.
            // Save the state machine to "disk" (i.e. a variable, here)
            assertEquals(1, bobsStorage.getMap<Any, Any>("state machines").size)

            // .. and let's imagine that Bob's computer has a power cut. He now has nothing now beyond what was on disk.
            bobsNode.stop()

            // Alice doesn't know that and carries on: she wants to know about the cash transactions he's trying to use.
            // She will wait around until Bob comes back.
            assertTrue(alicesNode.pump(false))

            // ... bring the node back up ... the act of constructing the SMM will re-register the message handlers
            // that Bob was waiting on before the reboot occurred.
            bobsNode = network.createNodeWithID(true, bobsAddress.id).start().get()
            val smm = StateMachineManager(
                    MockServices(wallet = null, keyManagement = null, net = bobsNode, storage = bobsStorage),
                    MoreExecutors.directExecutor()
            )

            // Find the future representing the result of this state machine again.
            var bobFuture = smm.findStateMachines(TwoPartyTradeProtocol.Buyer::class.java).single().second

            // And off we go again.
            runNetwork()

            // Bob is now finished and has the same transaction as Alice.
            val stx = bobFuture.get()
            txns.add(stx.tx)
            verify()

            assertTrue(smm.findStateMachines(TwoPartyTradeProtocol.Buyer::class.java).isEmpty())
        }
    }

    @Test
    fun `check dependencies of the sale asset are resolved`() {
        transactionGroupFor<ContractState> {
            val (bobsWallet, bobsFakeCash) = fillUpForBuyer(false)
            val alicesFakePaper = fillUpForSeller(false).second

            val (alicesAddress, alicesNode) = makeNode()
            val (bobsAddress, bobsNode) = makeNode()
            val timestamper  = network.setupTimestampingNode(true).first

            val alicesServices = MockServices(
                    net = alicesNode,
                    storage = MockStorageService(mapOf("validated-transactions" to "alice"))
            )
            val bobsServices = MockServices(
                    wallet = MockWalletService(bobsWallet.states),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode,
                    storage = MockStorageService(mapOf("validated-transactions" to "bob"))
            )
            val bobsSignedTxns = loadFakeTxnsIntoStorage(bobsFakeCash, bobsServices.storageService)
            val alicesSignedTxns = loadFakeTxnsIntoStorage(alicesFakePaper, alicesServices.storageService)

            val buyerSessionID = random63BitValue()

            TwoPartyTradeProtocol.runSeller(
                    StateMachineManager(alicesServices, RunOnCallerThread),
                    timestamper,
                    bobsAddress,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            TwoPartyTradeProtocol.runBuyer(
                    StateMachineManager(bobsServices, RunOnCallerThread),
                    timestamper,
                    alicesAddress,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            runNetwork()

            run {
                val records = (bobsServices.storageService.validatedTransactions as RecordingMap).records
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
            }

            // And from Alice's perspective ...
            run {
                val records = (alicesServices.storageService.validatedTransactions as RecordingMap).records
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
            val (bobsWallet, fakeBobCash) = fillUpForBuyer(withError = true)
            val fakeAlicePaper = fillUpForSeller(false).second

            val (alicesAddress, alicesNode) = makeNode()
            val (bobsAddress, bobsNode) = makeNode()
            val timestamper  = network.setupTimestampingNode(true).first

            val alicesServices = MockServices(net = alicesNode)
            val bobsServices = MockServices(
                    wallet = MockWalletService(bobsWallet.states),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode,
                    storage = MockStorageService(mapOf("validated-transactions" to "bob"))
            )
            loadFakeTxnsIntoStorage(fakeBobCash, bobsServices.storageService)
            loadFakeTxnsIntoStorage(fakeAlicePaper, alicesServices.storageService)

            val buyerSessionID = random63BitValue()

            val aliceResult = TwoPartyTradeProtocol.runSeller(
                    StateMachineManager(alicesServices, RunOnCallerThread),
                    timestamper,
                    bobsAddress,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            TwoPartyTradeProtocol.runBuyer(
                    StateMachineManager(bobsServices, RunOnCallerThread),
                    timestamper,
                    alicesAddress,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            runNetwork()

            val e = assertFailsWith<ExecutionException> {
                aliceResult.get()
            }
            assertTrue(e.cause is TransactionVerificationException)
            assertTrue(e.cause!!.cause!!.message!!.contains("at least one cash input"))
        }
    }

    @Test
    fun `dependency with error on seller side`() {
        transactionGroupFor<ContractState> {
            val (bobsWallet, fakeBobCash) = fillUpForBuyer(withError = false)
            val fakeAlicePaper = fillUpForSeller(withError = true).second

            val (alicesAddress, alicesNode) = makeNode()
            val (bobsAddress, bobsNode) = makeNode()
            val timestamper  = network.setupTimestampingNode(true).first

            val alicesServices = MockServices(net = alicesNode)
            val bobsServices = MockServices(
                    wallet = MockWalletService(bobsWallet.states),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode,
                    storage = MockStorageService(mapOf("validated-transactions" to "bob"))
            )
            loadFakeTxnsIntoStorage(fakeBobCash, bobsServices.storageService)
            loadFakeTxnsIntoStorage(fakeAlicePaper, alicesServices.storageService)

            val buyerSessionID = random63BitValue()

            TwoPartyTradeProtocol.runSeller(
                    StateMachineManager(alicesServices, RunOnCallerThread),
                    timestamper,
                    bobsAddress,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    buyerSessionID
            )
            val bobResult = TwoPartyTradeProtocol.runBuyer(
                    StateMachineManager(bobsServices, RunOnCallerThread),
                    timestamper,
                    alicesAddress,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            runNetwork()

            val e = assertFailsWith<ExecutionException> {
                bobResult.get()
            }
            assertTrue(e.cause is TransactionVerificationException)
            assertTrue(e.cause!!.cause!!.message!!.contains("must be timestamped"))
        }
    }

    private fun TransactionGroupDSL<ContractState>.loadFakeTxnsIntoStorage(wtxToSign: List<WireTransaction>,
                                                                           ss: StorageService): Map<SecureHash, SignedTransaction> {
        val txStorage = ss.validatedTransactions
        val map = signAll(wtxToSign).associateBy { it.id }
        if (txStorage is RecordingMap) {
            txStorage.putAllUnrecorded(map)
        } else
            txStorage.putAll(map)
        return map
    }

    private fun TransactionGroupDSL<ContractState>.fillUpForBuyer(withError: Boolean): Pair<Wallet, List<WireTransaction>> {
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
            output("bob cash 1") { 800.DOLLARS.CASH `issued by` MEGA_CORP `owned by` BOB }
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
        }

        val bc2 = transaction {
            input("elbonian money 2")
            output("bob cash 2") { 300.DOLLARS.CASH `issued by` MEGA_CORP `owned by` BOB }
            output { 700.DOLLARS.CASH `issued by` MEGA_CORP `owned by` MEGA_CORP_PUBKEY }   // Change output.
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
        }

        val wallet = Wallet(listOf<StateAndRef<Cash.State>>(lookup("bob cash 1"), lookup("bob cash 2")))
        return Pair(wallet, listOf(eb1, bc1, bc2))
    }

    private fun TransactionGroupDSL<ContractState>.fillUpForSeller(withError: Boolean): Pair<Wallet, List<WireTransaction>> {
        val ap = transaction {
            output("alice's paper") {
                CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), ALICE, 1200.DOLLARS, TEST_TX_TIME + 7.days)
            }
            arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
            if (!withError)
                timestamp(TEST_TX_TIME)
        }

        val wallet = Wallet(listOf<StateAndRef<Cash.State>>(lookup("alice's paper")))
        return Pair(wallet, listOf(ap))
    }
}
