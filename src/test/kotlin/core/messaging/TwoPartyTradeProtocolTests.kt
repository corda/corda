/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import contracts.Cash
import contracts.CommercialPaper
import contracts.protocols.TwoPartyTradeProtocol
import core.*
import core.testutils.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * In this example, Alessia wishes to sell her commercial paper to Boris in return for $1,000,000 and they wish to do
 * it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Alessia and Boris already found each other via some market, and have agreed the details already.
 */
class TwoPartyTradeProtocolTests : TestWithInMemoryNetwork() {
    @Before
    fun initLogging() {
        Logger.getLogger("").handlers[0].level = Level.ALL
        Logger.getLogger("").handlers[0].formatter = object : Formatter() {
            override fun format(record: LogRecord) = "${record.threadID} ${record.loggerName}: ${record.message}\n"
        }
        Logger.getLogger("com.r3cev.protocols.trade").level = Level.ALL
    }

    @After
    fun stopLogging() {
        Logger.getLogger("com.r3cev.protocols.trade").level = Level.INFO
    }

    @Test
    fun cashForCP() {
        val (addr1, node1) = makeNode(inBackground = true)
        val (addr2, node2) = makeNode(inBackground = true)

        val backgroundThread = Executors.newSingleThreadExecutor()
        val tpSeller = TwoPartyTradeProtocol.create(StateMachineManager(node1, backgroundThread))
        val tpBuyer = TwoPartyTradeProtocol.create(StateMachineManager(node2, backgroundThread))

        transactionGroupFor<ContractState> {
            // Bob (S) has some cash, Alice (P) has some commercial paper she wants to sell to Bob.
            roots {
                transaction(CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), ALICE, 1200.DOLLARS, TEST_TX_TIME + 7.days) label "alice's paper")
                transaction(800.DOLLARS.CASH `owned by` BOB label "bob cash1")
                transaction(300.DOLLARS.CASH `owned by` BOB label "bob cash2")
            }

            val bobsWallet = listOf<StateAndRef<Cash.State>>(lookup("bob cash1"), lookup("bob cash2"))

            val aliceResult = tpSeller.runSeller(
                    addr2,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    TEST_KEYS_TO_CORP_MAP,
                    DUMMY_TIMESTAMPER
            )
            val bobResult = tpBuyer.runBuyer(
                    addr1,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    bobsWallet,
                    mapOf(BOB to BOB_KEY.private),
                    DUMMY_TIMESTAMPER,
                    TEST_KEYS_TO_CORP_MAP
            )

            assertEquals(aliceResult.get(), bobResult.get())

            txns.add(aliceResult.get().second)
            verify()
        }
        backgroundThread.shutdown()
    }

    @Test
    fun serializeAndRestore() {
        val (addr1, node1) = makeNode(inBackground = false)
        var (addr2, node2) = makeNode(inBackground = false)

        val smmSeller = StateMachineManager(node1, MoreExecutors.directExecutor())
        val tpSeller = TwoPartyTradeProtocol.create(smmSeller)
        val smmBuyer = StateMachineManager(node2, MoreExecutors.directExecutor())
        val tpBuyer = TwoPartyTradeProtocol.create(smmBuyer)

        transactionGroupFor<ContractState> {
            // Buyer Bob has some cash, Seller Alice has some commercial paper she wants to sell to Bob.
            roots {
                transaction(CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), ALICE, 1200.DOLLARS, TEST_TX_TIME + 7.days) label "alice's paper")
                transaction(800.DOLLARS.CASH `owned by` BOB label "bob cash1")
                transaction(300.DOLLARS.CASH `owned by` BOB label "bob cash2")
            }

            val bobsWallet = listOf<StateAndRef<Cash.State>>(lookup("bob cash1"), lookup("bob cash2"))

            tpSeller.runSeller(
                    addr2,
                    lookup("alice's paper"),
                    1000.DOLLARS,
                    ALICE_KEY,
                    TEST_KEYS_TO_CORP_MAP,
                    DUMMY_TIMESTAMPER
            )
            tpBuyer.runBuyer(
                    addr1,
                    1000.DOLLARS,
                    CommercialPaper.State::class.java,
                    bobsWallet,
                    mapOf(BOB to BOB_KEY.private),
                    DUMMY_TIMESTAMPER,
                    TEST_KEYS_TO_CORP_MAP
            )

            // Everything is on this thread so we can now step through the protocol one step at a time.
            // Seller Alice already sent a message to Buyer Bob. Pump once:
            node2.pump(false)
            // OK, now Bob has sent the partial transaction back to Alice and is waiting for Alice's signature.
            val storageBob = smmBuyer.saveToBytes()
            // .. and let's imagine that Bob's computer has a power cut. He now has nothing now beyond what was on disk.
            node2.stop()

            // Alice doesn't know that and sends Bob the now finalised transaction. Alice sends a message to a node
            // that has gone offline.
            node1.pump(false)

            // ... bring the network back up ...
            node2 = network.createNodeWithID(true, addr2.id).start().get()

            // We must provide the state machines with all the stuff that couldn't be saved to disk.
            var bobFuture: ListenableFuture<Pair<TimestampedWireTransaction, LedgerTransaction>>? = null
            fun resumeStateMachine(forObj: ProtocolStateMachine<*,*>): Any {
                return when (forObj) {
                    is TwoPartyTradeProtocol.Buyer -> {
                        bobFuture = forObj.resultFuture
                        return TwoPartyTradeProtocol.BuyerContext(bobsWallet, mapOf(BOB to BOB_KEY.private), DUMMY_TIMESTAMPER, TEST_KEYS_TO_CORP_MAP, null)
                    }
                    else -> fail()
                }
            }
            // The act of constructing this object will re-register the message handlers that Bob was waiting on before
            // the reboot occurred.
            StateMachineManager(node2, MoreExecutors.directExecutor(), storageBob, ::resumeStateMachine)
            assertTrue(node2.pump(false))
            // Bob is now finished and has the same transaction as Alice.
            val tx: Pair<TimestampedWireTransaction, LedgerTransaction> = bobFuture!!.get()
            txns.add(tx.second)
            verify()
        }
    }
}
