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

/**
 * In this example, Alice wishes to sell her commercial paper to Bob in return for $1,000,000 and they wish to do
 * it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Alice and Bob already found each other via some market, and have agreed the details already.
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
        val backgroundThread = Executors.newSingleThreadExecutor()

        transactionGroupFor<ContractState> {
            // Bob (Buyer) has some cash, Alice (Seller) has some commercial paper she wants to sell to Bob.
            roots {
                transaction(CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), ALICE, 1200.DOLLARS, TEST_TX_TIME + 7.days) label "alice's paper")
                transaction(800.DOLLARS.CASH `owned by` BOB label "bob cash1")
                transaction(300.DOLLARS.CASH `owned by` BOB label "bob cash2")
            }

            val bobsWallet = listOf<StateAndRef<Cash.State>>(lookup("bob cash1"), lookup("bob cash2"))

            val (alicesAddress, alicesNode) = makeNode(inBackground = true)
            val (bobsAddress, bobsNode) = makeNode(inBackground = true)
            val timestamper = network.setupTimestampingNode(false).first

            val alicesServices = MockServices(net = alicesNode)
            val bobsServices = MockServices(
                    wallet = MockWalletService(bobsWallet),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode
            )

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

            txns.add(aliceResult.get().second)
            verify()
        }
        backgroundThread.shutdown()
    }

    @Test
    fun serializeAndRestore() {
        transactionGroupFor<ContractState> {
            // Buyer Bob has some cash, Seller Alice has some commercial paper she wants to sell to Bob.
            roots {
                transaction(CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), ALICE, 1200.DOLLARS, TEST_TX_TIME + 7.days) label "alice's paper")
                transaction(800.DOLLARS.CASH `owned by` BOB label "bob cash1")
                transaction(300.DOLLARS.CASH `owned by` BOB label "bob cash2")
            }

            val bobsWallet = listOf<StateAndRef<Cash.State>>(lookup("bob cash1"), lookup("bob cash2"))

            val (alicesAddress, alicesNode) = makeNode(inBackground = false)
            var (bobsAddress, bobsNode) = makeNode(inBackground = false)
            val timestamper = network.setupTimestampingNode(true)

            val bobsStorage = MockStorageService()

            val alicesServices = MockServices(wallet = null, keyManagement = null, net = alicesNode)
            var bobsServices = MockServices(
                    wallet = MockWalletService(bobsWallet),
                    keyManagement = MockKeyManagementService(mapOf(BOB to BOB_KEY.private)),
                    net = bobsNode,
                    storage = bobsStorage
            )

            val smmBuyer = StateMachineManager(bobsServices, MoreExecutors.directExecutor())

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
            val tx = bobFuture.get()
            txns.add(tx.second)
            verify()

            assertTrue(smm.stateMachines.isEmpty())
        }
    }
}
