package net.corda.irs.testing

import net.corda.contracts.asset.CASH
import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.`issued by`
import net.corda.contracts.asset.`owned by`
import net.corda.core.bd
import net.corda.core.contracts.*
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.crypto.Party
import net.corda.core.crypto.generateKeyPair
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.transactions.FilterFuns
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.LogHelper
import net.corda.irs.api.NodeInterestRates
import net.corda.irs.flows.RatesFixFlow
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.ALICE_PUBKEY
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.makeTestDataSourceProperties
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class NodeInterestRatesTest {
    val TEST_DATA = NodeInterestRates.parseFile("""
        LIBOR 2016-03-16 1M = 0.678
        LIBOR 2016-03-16 2M = 0.685
        LIBOR 2016-03-16 1Y = 0.890
        LIBOR 2016-03-16 2Y = 0.962
        EURIBOR 2016-03-15 1M = 0.123
        EURIBOR 2016-03-15 2M = 0.111
        """.trimIndent())

    val DUMMY_CASH_ISSUER_KEY = generateKeyPair()
    val DUMMY_CASH_ISSUER = Party.Full("Cash issuer", DUMMY_CASH_ISSUER_KEY.public)

    val clock = Clock.systemUTC()
    lateinit var oracle: NodeInterestRates.Oracle
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        databaseTransaction(database) {
            oracle = NodeInterestRates.Oracle(MEGA_CORP, MEGA_CORP_KEY, clock).apply { knownFixes = TEST_DATA }
        }
    }

    @After
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `query successfully`() {
        databaseTransaction(database) {
            val q = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
            val res = oracle.query(listOf(q), clock.instant())
            assertEquals(1, res.size)
            assertEquals("0.678".bd, res[0].value)
            assertEquals(q, res[0].of)
        }
    }

    @Test
    fun `query with one success and one missing`() {
        databaseTransaction(database) {
            val q1 = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
            val q2 = NodeInterestRates.parseFixOf("LIBOR 2016-03-15 1M")
            val e = assertFailsWith<NodeInterestRates.UnknownFix> { oracle.query(listOf(q1, q2), clock.instant()) }
            assertEquals(e.fix, q2)
        }
    }

    @Test
    fun `query successfully with interpolated rate`() {
        databaseTransaction(database) {
            val q = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 5M")
            val res = oracle.query(listOf(q), clock.instant())
            assertEquals(1, res.size)
            Assert.assertEquals(0.7316228, res[0].value.toDouble(), 0.0000001)
            assertEquals(q, res[0].of)
        }
    }

    @Test
    fun `rate missing and unable to interpolate`() {
        databaseTransaction(database) {
            val q = NodeInterestRates.parseFixOf("EURIBOR 2016-03-15 3M")
            assertFailsWith<NodeInterestRates.UnknownFix> { oracle.query(listOf(q), clock.instant()) }
        }
    }

    @Test
    fun `empty query`() {
        databaseTransaction(database) {
            assertFailsWith<IllegalArgumentException> { oracle.query(emptyList(), clock.instant()) }
        }
    }

    @Test
    fun `refuse to sign with no relevant commands`() {
        databaseTransaction(database) {
            val tx = makeTX()
            val wtx1 = tx.toWireTransaction()
            val ftx1 = FilteredTransaction.buildMerkleTransaction(wtx1, FilterFuns(filterOutputs = { true }))
            assertFailsWith<IllegalArgumentException> { oracle.sign(ftx1, wtx1.id) }
            tx.addCommand(Cash.Commands.Move(), ALICE_PUBKEY)
            val wtx2 = tx.toWireTransaction()
            val ftx2 = FilteredTransaction.buildMerkleTransaction(wtx2, FilterFuns(filterCommands = { true }))
            assertFalse(wtx1.id == wtx2.id)
            assertFailsWith<IllegalArgumentException> { oracle.sign(ftx2, wtx2.id) }
        }
    }

    @Test
    fun `sign successfully`() {
        databaseTransaction(database) {
            val tx = makeTX()
            val fix = oracle.query(listOf(NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")), clock.instant()).first()
            tx.addCommand(fix, oracle.identity.owningKey)
            // Sign successfully.
            val wtx = tx.toWireTransaction()
            fun filterCommands(c: Command) = oracle.identity.owningKey in c.signers && c.value is Fix
            val filterFuns = FilterFuns(filterCommands = ::filterCommands)
            val ftx = FilteredTransaction.buildMerkleTransaction(wtx, filterFuns)
            val signature = oracle.sign(ftx, wtx.id)
            tx.checkAndAddSignature(signature)
        }
    }

    @Test
    fun `do not sign with unknown fix`() {
        databaseTransaction(database) {
            val tx = makeTX()
            val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
            val badFix = Fix(fixOf, "0.6789".bd)
            tx.addCommand(badFix, oracle.identity.owningKey)
            val wtx = tx.toWireTransaction()
            fun filterCommands(c: Command) = oracle.identity.owningKey in c.signers && c.value is Fix
            val filterFuns = FilterFuns(filterCommands = ::filterCommands)
            val ftx = FilteredTransaction.buildMerkleTransaction(wtx, filterFuns)
            val e1 = assertFailsWith<NodeInterestRates.UnknownFix> { oracle.sign(ftx, wtx.id) }
            assertEquals(fixOf, e1.fix)
        }
    }

    @Test
    fun `do not sign too many leaves`() {
        databaseTransaction(database) {
            val tx = makeTX()
            val fix = oracle.query(listOf(NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")), clock.instant()).first()
            tx.addCommand(fix, oracle.identity.owningKey)
            val wtx = tx.toWireTransaction()
            fun filterCommands(c: Command) = oracle.identity.owningKey in c.signers && c.value is Fix
            val filterFuns = FilterFuns(filterCommands = ::filterCommands, filterOutputs = { true })
            val ftx = FilteredTransaction.buildMerkleTransaction(wtx, filterFuns)
            assertFailsWith<IllegalArgumentException> { oracle.sign(ftx, wtx.id) }
        }
    }

    @Test
    fun `partial tree verification exception`() {
        databaseTransaction(database) {
            val tx = makeTX()
            val wtx1 = tx.toWireTransaction()
            tx.addCommand(Cash.Commands.Move(), ALICE_PUBKEY)
            val wtx2 = tx.toWireTransaction()
            val ftx2 = FilteredTransaction.buildMerkleTransaction(wtx2, FilterFuns(filterCommands = { true }))
            assertFalse(wtx1.id == wtx2.id)
            assertFailsWith<MerkleTreeException> { oracle.sign(ftx2, wtx1.id) }
        }
    }

    @Test
    fun `network tearoff`() {
        val net = MockNetwork()
        val n1 = net.createNotaryNode()
        val n2 = net.createNode(n1.info.address, advertisedServices = ServiceInfo(NodeInterestRates.type))
        databaseTransaction(n2.database) {
            n2.findService<NodeInterestRates.Service>().oracle.knownFixes = TEST_DATA
        }
        val tx = TransactionType.General.Builder(null)
        val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
        val oracle = n2.info.serviceIdentities(NodeInterestRates.type).first()
        fun filterCommands(c: Command) = oracle.owningKey in c.signers && c.value is Fix
        val filterFuns = FilterFuns(filterCommands = ::filterCommands)
        val flow = RatesFixFlow(tx, filterFuns, oracle, fixOf, "0.675".bd, "0.1".bd)
        LogHelper.setLevel("rates")
        net.runNetwork()
        val future = n1.services.startFlow(flow).resultFuture
        net.runNetwork()
        future.getOrThrow()
        // We should now have a valid signature over our tx from the oracle.
        val fix = tx.toSignedTransaction(true).tx.commands.map { it.value as Fix }.first()
        assertEquals(fixOf, fix.of)
        assertEquals("0.678".bd, fix.value)
    }

    private fun makeTX() = TransactionType.General.Builder(DUMMY_NOTARY).withItems(1000.DOLLARS.CASH `issued by` DUMMY_CASH_ISSUER `owned by` ALICE_PUBKEY `with notary` DUMMY_NOTARY)
}
