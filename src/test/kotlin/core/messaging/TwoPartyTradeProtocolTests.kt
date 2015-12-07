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
import core.testutils.*
import org.junit.Test
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.assertEquals

/**
 * In this example, Alessia wishes to sell her commercial paper to Boris in return for $1,000,000 and they wish to do
 * it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Alessia and Boris already found each other via some market, and have agreed the details already.
 */
class TwoPartyTradeProtocolTests : TestWithInMemoryNetwork() {
    init {
        Logger.getLogger("").handlers[0].level = Level.ALL
        Logger.getLogger("").handlers[0].formatter = object : Formatter() {
            override fun format(record: LogRecord) = "${record.threadID} ${record.loggerName}: ${record.message}\n"
        }
        Logger.getLogger("com.r3cev.protocols.trade").level = Level.ALL
    }

    @Test
    fun cashForCP() {
        val (addr1, node1) = makeNode()
        val (addr2, node2) = makeNode()

        val tp = TwoPartyTradeProtocol.create()

        transactionGroupFor<ContractState> {
            // Bob (S) has some cash, Alice (P) has some commercial paper she wants to sell to Bob.
            roots {
                transaction(CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), ALICE, 1200.DOLLARS, TEST_TX_TIME + 7.days) label "alice's paper")
                transaction(800.DOLLARS.CASH `owned by` BOB label "bob cash1")
                transaction(300.DOLLARS.CASH `owned by` BOB label "bob cash2")
            }

            val bobsWallet = listOf<StateAndRef<Cash.State>>(lookup("bob cash1"), lookup("bob cash2"))
            val (aliceFuture, bobFuture) = runNetwork {
                Pair(
                        tp.runSeller(node1, addr2, lookup("alice's paper"), 1000.DOLLARS, ALICE_KEY,
                                TEST_KEYS_TO_CORP_MAP, DUMMY_TIMESTAMPER),
                        tp.runBuyer(node2, addr1, 1000.DOLLARS, CommercialPaper.State::class.java, bobsWallet,
                                        mapOf(BOB to BOB_KEY.private), DUMMY_TIMESTAMPER, TEST_KEYS_TO_CORP_MAP)
                )
            }

            val aliceResult: Pair<TimestampedWireTransaction, LedgerTransaction> = aliceFuture.get()
            val bobResult: Pair<TimestampedWireTransaction, LedgerTransaction> = bobFuture.get()

            assertEquals(aliceResult, bobResult)

            txns.add(aliceResult.second)

            verify()
        }
    }
}
