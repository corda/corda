/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node.services

import contracts.Cash
import core.DOLLARS
import core.Fix
import core.TransactionBuilder
import core.d
import core.node.MockNetwork
import core.testutils.*
import core.utilities.BriefLogFormatter
import org.junit.Test
import protocols.RatesFixProtocol
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NodeInterestRatesTest {
    val TEST_DATA = NodeInterestRates.parseFile("""
        LIBOR 2016-03-16 30 = 0.678
        LIBOR 2016-03-16 60 = 0.655
        EURIBOR 2016-03-15 30 = 0.123
        EURIBOR 2016-03-15 60 = 0.111
        """.trimIndent())

    val service = NodeInterestRates.Oracle(MEGA_CORP, MEGA_CORP_KEY).apply { knownFixes = TEST_DATA }

    @Test fun `query successfully`() {
        val q = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 30")
        val res = service.query(listOf(q))
        assertEquals(1, res.size)
        assertEquals("0.678".d, res[0].value)
        assertEquals(q, res[0].of)
    }

    @Test fun `query with one success and one missing`() {
        val q1 = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 30")
        val q2 = NodeInterestRates.parseFixOf("LIBOR 2016-03-19 30")
        val e = assertFailsWith<NodeInterestRates.UnknownFix> { service.query(listOf(q1, q2)) }
        assertEquals(e.fix, q2)
    }

    @Test fun `empty query`() {
        assertFailsWith<IllegalArgumentException> { service.query(emptyList()) }
    }

    @Test fun `refuse to sign with no relevant commands`() {
        val tx = makeTX()
        assertFailsWith<IllegalArgumentException> { service.sign(tx.toWireTransaction()) }
        tx.addCommand(Cash.Commands.Move(), ALICE)
        assertFailsWith<IllegalArgumentException> { service.sign(tx.toWireTransaction()) }
    }

    @Test fun `sign successfully`() {
        val tx = makeTX()
        val fix = service.query(listOf(NodeInterestRates.parseFixOf("LIBOR 2016-03-16 30"))).first()
        tx.addCommand(fix, service.identity.owningKey)

        // Sign successfully.
        val signature = service.sign(tx.toWireTransaction())
        tx.checkAndAddSignature(signature)
    }

    @Test fun `do not sign with unknown fix`() {
        val tx = makeTX()
        val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 30")
        val badFix = Fix(fixOf, "0.6789".d)
        tx.addCommand(badFix, service.identity.owningKey)

        val e1 = assertFailsWith<NodeInterestRates.UnknownFix> { service.sign(tx.toWireTransaction()) }
        assertEquals(fixOf, e1.fix)
    }

    @Test
    fun network() {
        val net = MockNetwork()
        val (n1, n2) = net.createTwoNodes()
        NodeInterestRates.Service(n2).oracle.knownFixes = TEST_DATA

        val tx = TransactionBuilder()
        val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 30")
        val protocol = RatesFixProtocol(tx, n2.legallyIdentifableAddress, fixOf, "0.675".d, "0.1".d)
        BriefLogFormatter.initVerbose("rates")
        val future = n1.smm.add("rates", protocol)

        net.runNetwork()
        future.get()

        // We should now have a valid signature over our tx from the oracle.
        val fix = tx.toSignedTransaction(true).tx.commands.map { it.data as Fix }.first()
        assertEquals(fixOf, fix.of)
        assertEquals("0.678".d, fix.value)
    }

    private fun makeTX() = TransactionBuilder(outputs = mutableListOf(1000.DOLLARS.CASH `owned by` ALICE))
}