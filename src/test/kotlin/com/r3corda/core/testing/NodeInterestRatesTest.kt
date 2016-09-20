package com.r3corda.core.testing

import com.r3corda.contracts.asset.CASH
import com.r3corda.contracts.asset.Cash
import com.r3corda.contracts.asset.`issued by`
import com.r3corda.contracts.asset.`owned by`
import com.r3corda.core.bd
import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.Fix
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.contracts.`with notary`
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.core.utilities.LogHelper
import com.r3corda.testing.node.MockNetwork
import com.r3corda.demos.api.NodeInterestRates
import com.r3corda.node.services.transactions.PersistentUniquenessProvider
import com.r3corda.node.utilities.configureDatabase
import com.r3corda.node.utilities.databaseTransaction
import com.r3corda.protocols.RatesFixProtocol
import com.r3corda.testing.ALICE_PUBKEY
import com.r3corda.testing.MEGA_CORP
import com.r3corda.testing.MEGA_CORP_KEY
import com.r3corda.testing.node.makeTestDataSourceProperties
import org.jetbrains.exposed.sql.Database
import org.junit.*
import java.io.Closeable
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    val DUMMY_CASH_ISSUER = Party("Cash issuer", DUMMY_CASH_ISSUER_KEY.public)

    val clock = Clock.systemUTC()
    lateinit var oracle: NodeInterestRates.Oracle
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource=dataSourceAndDatabase.first
        database=dataSourceAndDatabase.second
        databaseTransaction(database) {
            oracle = NodeInterestRates.Oracle(MEGA_CORP, MEGA_CORP_KEY, clock).apply { knownFixes = TEST_DATA }
        }
    }

    @After
    fun tearDown() {
        dataSource.close()
    }

    @Test fun `query successfully`() {
        databaseTransaction(database) {
            val q = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
            val res = oracle.query(listOf(q), clock.instant())
            assertEquals(1, res.size)
            assertEquals("0.678".bd, res[0].value)
            assertEquals(q, res[0].of)
        }
    }

    @Test fun `query with one success and one missing`() {
        databaseTransaction(database) {
            val q1 = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
            val q2 = NodeInterestRates.parseFixOf("LIBOR 2016-03-15 1M")
            val e = assertFailsWith<NodeInterestRates.UnknownFix> { oracle.query(listOf(q1, q2), clock.instant()) }
            assertEquals(e.fix, q2)
        }
    }

    @Test fun `query successfully with interpolated rate`() {
        databaseTransaction(database) {
            val q = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 5M")
            val res = oracle.query(listOf(q), clock.instant())
            assertEquals(1, res.size)
            Assert.assertEquals(0.7316228, res[0].value.toDouble(), 0.0000001)
            assertEquals(q, res[0].of)
        }
    }

    @Test fun `rate missing and unable to interpolate`() {
        databaseTransaction(database) {
            val q = NodeInterestRates.parseFixOf("EURIBOR 2016-03-15 3M")
            assertFailsWith<NodeInterestRates.UnknownFix> { oracle.query(listOf(q), clock.instant()) }
        }
    }

    @Test fun `empty query`() {
        databaseTransaction(database) {
            assertFailsWith<IllegalArgumentException> { oracle.query(emptyList(), clock.instant()) }
        }
    }

    @Test fun `refuse to sign with no relevant commands`() {
        databaseTransaction(database) {
            val tx = makeTX()
            assertFailsWith<IllegalArgumentException> { oracle.sign(tx.toWireTransaction()) }
            tx.addCommand(Cash.Commands.Move(), ALICE_PUBKEY)
            assertFailsWith<IllegalArgumentException> { oracle.sign(tx.toWireTransaction()) }
        }
    }

    @Test fun `sign successfully`() {
        databaseTransaction(database) {
            val tx = makeTX()
            val fix = oracle.query(listOf(NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")), clock.instant()).first()
            tx.addCommand(fix, oracle.identity.owningKey)

            // Sign successfully.
            val signature = oracle.sign(tx.toWireTransaction())
            tx.checkAndAddSignature(signature)
        }
    }

    @Test fun `do not sign with unknown fix`() {
        databaseTransaction(database) {
            val tx = makeTX()
            val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
            val badFix = Fix(fixOf, "0.6789".bd)
            tx.addCommand(badFix, oracle.identity.owningKey)

            val e1 = assertFailsWith<NodeInterestRates.UnknownFix> { oracle.sign(tx.toWireTransaction()) }
            assertEquals(fixOf, e1.fix)
        }
    }

    @Test
    fun network() {
        val net = MockNetwork()
        val (n1, n2) = net.createTwoNodes()
        databaseTransaction(n2.database) {
            n2.findService<NodeInterestRates.Service>().oracle.knownFixes = TEST_DATA
        }
        val tx = TransactionType.General.Builder(null)
        val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
        val protocol = RatesFixProtocol(tx, n2.info.identity, fixOf, "0.675".bd, "0.1".bd)
        LogHelper.setLevel("rates")
        net.runNetwork()
        val future = n1.services.startProtocol("rates", protocol)

        net.runNetwork()
        future.get()

        // We should now have a valid signature over our tx from the oracle.
        val fix = tx.toSignedTransaction(true).tx.commands.map { it.value as Fix }.first()
        assertEquals(fixOf, fix.of)
        assertEquals("0.678".bd, fix.value)
    }

    private fun makeTX() = TransactionType.General.Builder(DUMMY_NOTARY).withItems(1000.DOLLARS.CASH `issued by` DUMMY_CASH_ISSUER `owned by` ALICE_PUBKEY `with notary` DUMMY_NOTARY)
}
