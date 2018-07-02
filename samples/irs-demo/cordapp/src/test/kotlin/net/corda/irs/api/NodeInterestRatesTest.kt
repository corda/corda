package net.corda.irs.api

import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.Fix
import net.corda.finance.contracts.asset.CASH
import net.corda.finance.contracts.asset.Cash
import net.corda.node.internal.configureDatabase
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.*
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.util.function.Predicate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class NodeInterestRatesTest {
    private companion object {
        val alice = TestIdentity(ALICE_NAME, 70)
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val MEGA_CORP_KEY = generateKeyPair()
        val ALICE get() = alice.party
        val ALICE_PUBKEY get() = alice.publicKey
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val TEST_DATA = NodeInterestRates.parseFile("""
        LIBOR 2016-03-16 1M = 0.678
        LIBOR 2016-03-16 2M = 0.685
        LIBOR 2016-03-16 1Y = 0.890
        LIBOR 2016-03-16 2Y = 0.962
        EURIBOR 2016-03-15 1M = 0.123
        EURIBOR 2016-03-15 2M = 0.111
        """.trimIndent())
    private val dummyCashIssuer = TestIdentity(CordaX500Name("Cash issuer", "London", "GB"))
    private val services = MockServices(listOf("net.corda.finance.contracts.asset"), dummyCashIssuer, rigorousMock(), MEGA_CORP_KEY)
    // This is safe because MockServices only ever have a single identity
    private val identity = services.myInfo.singleIdentity()

    private lateinit var oracle: NodeInterestRates.Oracle
    private lateinit var database: CordaPersistence

    private fun fixCmdFilter(elem: Any): Boolean {
        return when (elem) {
            is Command<*> -> identity.owningKey in elem.signers && elem.value is Fix
            else -> false
        }
    }

    private fun filterCmds(elem: Any): Boolean = elem is Command<*>

    @Before
    fun setUp() {
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null })
        database.transaction {
            oracle = createMockCordaService(services, NodeInterestRates::Oracle)
            oracle.knownFixes = TEST_DATA
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `query successfully`() {
        database.transaction {
            val q = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
            val res = oracle.query(listOf(q))
            assertEquals(1, res.size)
            assertEquals(BigDecimal("0.678"), res[0].value)
            assertEquals(q, res[0].of)
        }
    }

    @Test
    fun `query with one success and one missing`() {
        database.transaction {
            val q1 = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
            val q2 = NodeInterestRates.parseFixOf("LIBOR 2016-03-15 1M")
            val e = assertFailsWith<NodeInterestRates.UnknownFix> { oracle.query(listOf(q1, q2)) }
            assertEquals(e.fix, q2)
        }
    }

    @Test
    fun `query successfully with interpolated rate`() {
        database.transaction {
            val q = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 5M")
            val res = oracle.query(listOf(q))
            assertEquals(1, res.size)
            assertEquals(0.7316228, res[0].value.toDouble(), 0.0000001)
            assertEquals(q, res[0].of)
        }
    }

    @Test
    fun `rate missing and unable to interpolate`() {
        database.transaction {
            val q = NodeInterestRates.parseFixOf("EURIBOR 2016-03-15 3M")
            assertFailsWith<NodeInterestRates.UnknownFix> { oracle.query(listOf(q)) }
        }
    }

    @Test
    fun `empty query`() {
        database.transaction {
            assertFailsWith<IllegalArgumentException> { oracle.query(emptyList()) }
        }
    }

    @Test
    fun `refuse to sign with no relevant commands`() {
        database.transaction {
            val tx = makeFullTx()
            val wtx1 = tx.toWireTransactionNew(services)
            fun filterAllOutputs(elem: Any): Boolean {
                return when (elem) {
                    is TransactionState<ContractState> -> true
                    else -> false
                }
            }

            val ftx1 = wtx1.buildFilteredTransaction(Predicate(::filterAllOutputs))
            assertFailsWith<IllegalArgumentException> { oracle.sign(ftx1) }
            tx.addCommand(Cash.Commands.Move(), ALICE_PUBKEY)
            val wtx2 = tx.toWireTransactionNew(services)
            val ftx2 = wtx2.buildFilteredTransaction(Predicate { x -> filterCmds(x) })
            assertFalse(wtx1.id == wtx2.id)
            assertFailsWith<IllegalArgumentException> { oracle.sign(ftx2) }
        }
    }

    @Test
    fun `sign successfully`() {
        database.transaction {
            val tx = makePartialTX()
            val fix = oracle.query(listOf(NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M"))).first()
            tx.addCommand(fix, identity.owningKey)
            // Sign successfully.
            val wtx = tx.toWireTransactionNew(services)
            val ftx = wtx.buildFilteredTransaction(Predicate { fixCmdFilter(it) })
            val signature = oracle.sign(ftx)
            wtx.checkSignature(signature)
        }
    }

    @Test
    fun `do not sign with unknown fix`() {
        database.transaction {
            val tx = makePartialTX()
            val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
            val badFix = Fix(fixOf, BigDecimal("0.6789"))
            tx.addCommand(badFix, identity.owningKey)
            val wtx = tx.toWireTransactionNew(services)
            val ftx = wtx.buildFilteredTransaction(Predicate { fixCmdFilter(it) })
            val e1 = assertFailsWith<NodeInterestRates.UnknownFix> { oracle.sign(ftx) }
            assertEquals(fixOf, e1.fix)
        }
    }

    @Test
    fun `do not sign too many leaves`() {
        database.transaction {
            val tx = makePartialTX()
            val fix = oracle.query(listOf(NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M"))).first()
            fun filtering(elem: Any): Boolean {
                return when (elem) {
                    is Command<*> -> identity.owningKey in elem.signers && elem.value is Fix
                    is TransactionState<ContractState> -> true
                    else -> false
                }
            }
            tx.addCommand(fix, identity.owningKey)
            val wtx = tx.toWireTransactionNew(services)
            val ftx = wtx.buildFilteredTransaction(Predicate(::filtering))
            assertFailsWith<IllegalArgumentException> { oracle.sign(ftx) }
        }
    }

    @Test
    fun `empty partial transaction to sign`() {
        val tx = makeFullTx()
        val wtx = tx.toWireTransactionNew(services)
        val ftx = wtx.buildFilteredTransaction(Predicate { false })
        assertFailsWith<IllegalArgumentException> { oracle.sign(ftx) } // It throws failed requirement (as it is empty there is no command to check and sign).
    }

    private fun makePartialTX() = TransactionBuilder(DUMMY_NOTARY).withItems(
            TransactionState(1000.DOLLARS.CASH issuedBy dummyCashIssuer.party ownedBy ALICE, Cash.PROGRAM_ID, DUMMY_NOTARY))

    private fun makeFullTx() = makePartialTX().withItems(dummyCommand())
}


