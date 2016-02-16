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
import core.testutils.*
import core.utilities.BriefLogFormatter
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * In this example, Alice wishes to sell her commercial paper to Bob in return for $1,000,000 and they wish to do
 * it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Alice and Bob already found each other via some market, and have agreed the details already.
 */
class TwoPartyTradeProtocolTests : TestWithInMemoryNetwork() {
    lateinit var backgroundThread: ExecutorService

    @Before
    fun before() {
        backgroundThread = Executors.newSingleThreadExecutor()
        BriefLogFormatter.initVerbose("platform.trade")
    }

    @After
    fun after() {
        backgroundThread.shutdown()
    }

    @Test
    fun `trade cash for commercial paper`() {
        transactionGroupFor<ContractState> {
            val bobsWallet = fillUp(false).first

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
            loadFakeTxnsIntoStorage(bobsServices.storageService)

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
    }

    @Test
    fun `shut down and restore`() {
        transactionGroupFor<ContractState> {
            val wallet = fillUp(false).first

            val (alicesAddress, alicesNode) = makeNode(inBackground = false)
            var (bobsAddress, bobsNode) = makeNode(inBackground = false)
            val timestamper = network.setupTimestampingNode(true)

            val bobsStorage = MockStorageService()

            val alicesServices = MockServices(wallet = null, keyManagement = null, net = alicesNode)
            var bobsServices = MockServices(
                    wallet = MockWalletService(wallet.states),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode,
                    storage = bobsStorage
            )
            loadFakeTxnsIntoStorage(bobsStorage)

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

            // Alice doesn't know that and carries on: first timestamping and then sending Bob the now finalised
            // transaction. Alice sends a message to a node that has gone offline.
            assertTrue(alicesNode.pump(false))
            assertTrue(timestamper.second.pump(false))
            assertTrue(alicesNode.pump(false))

            // ... bring the node back up ... the act of constructing the SMM will re-register the message handlers
            // that Bob was waiting on before the reboot occurred.
            bobsNode = network.createNodeWithID(true, bobsAddress.id).start().get()
            val smm = StateMachineManager(
                    MockServices(wallet = null, keyManagement = null, net = bobsNode, storage = bobsStorage),
                    MoreExecutors.directExecutor()
            )

            // Find the future representing the result of this state machine again.
            assertEquals(1, smm.stateMachines.size)
            var bobFuture = smm.stateMachines.filterIsInstance<TwoPartyTradeProtocol.Buyer>().first().resultFuture

            // Let Bob process his mailbox.
            assertTrue(bobsNode.pump(false))

            // Bob is now finished and has the same transaction as Alice.
            val stx = bobFuture.get()
            txns.add(stx.tx)
            verify()

            assertTrue(smm.stateMachines.isEmpty())
        }
    }

    @Test
    fun `check dependencies of the sale asset are resolved`() {
        transactionGroupFor<ContractState> {
            val (bobsWallet, fakeTxns) = fillUp(false)

            val (alicesAddress, alicesNode) = makeNode(inBackground = true)
            val (bobsAddress, bobsNode) = makeNode(inBackground = true)
            val timestamper  = network.setupTimestampingNode(false).first

            val alicesServices = MockServices(net = alicesNode)
            val bobsServices = MockServices(
                    wallet = MockWalletService(bobsWallet.states),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode,
                    storage = MockStorageService(isRecording = true)
            )
            loadFakeTxnsIntoStorage(bobsServices.storageService)

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

            // This line forces the protocol to run to completion.
            assertEquals(aliceResult.get(), bobResult.get())

            val records = (bobsServices.storageService.validatedTransactions as RecordingMap).records
            val expected = listOf(
                    RecordingMap.Get(fakeTxns[1].id),
                    RecordingMap.Get(fakeTxns[2].id),
                    RecordingMap.Get(fakeTxns[3].id),
                    RecordingMap.Get(fakeTxns[0].id),
                    RecordingMap.Get(fakeTxns[0].id)
            )
            assertEquals(expected, records)
        }
    }

    @Test
    fun `dependency with error`() {
        transactionGroupFor<ContractState> {
            val (bobsWallet, fakeTxns) = fillUp(withError = true)

            val (alicesAddress, alicesNode) = makeNode(inBackground = true)
            val (bobsAddress, bobsNode) = makeNode(inBackground = true)
            val timestamper  = network.setupTimestampingNode(false).first

            val alicesServices = MockServices(net = alicesNode)
            val bobsServices = MockServices(
                    wallet = MockWalletService(bobsWallet.states),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode,
                    storage = MockStorageService(isRecording = true)
            )
            loadFakeTxnsIntoStorage(bobsServices.storageService)

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
            TwoPartyTradeProtocol.runBuyer(
                    StateMachineManager(bobsServices, backgroundThread),
                    timestamper,
                    alicesAddress,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    buyerSessionID
            )

            val e = assertFailsWith<ExecutionException> {
                aliceResult.get()
            }
            assertTrue(e.cause is TransactionVerificationException)
            assertTrue(e.cause!!.cause!!.message!!.contains("at least one cash input"))
        }
    }

    private fun TransactionGroupDSL<ContractState>.loadFakeTxnsIntoStorage(ss: StorageService) {
        val txStorage = ss.validatedTransactions
        val map = signAll().associateBy { it.id }
        if (txStorage is RecordingMap) {
            txStorage.putAllUnrecorded(map)
        } else
            txStorage.putAll(map)
    }

    private fun TransactionGroupDSL<ContractState>.fillUp(withError: Boolean): Pair<Wallet, List<WireTransaction>> {
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

        val ap = transaction {
            output("alice's paper") {
                CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), ALICE, 1200.DOLLARS, TEST_TX_TIME + 7.days)
            }
            arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
            timestamp(TEST_TX_TIME)
        }

        val wallet = Wallet(listOf<StateAndRef<Cash.State>>(lookup("bob cash 1"), lookup("bob cash 2")))
        return Pair(wallet, listOf(eb1, bc1, bc2, ap))
    }
}
