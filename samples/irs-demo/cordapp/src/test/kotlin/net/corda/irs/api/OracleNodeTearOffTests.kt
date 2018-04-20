package net.corda.irs.api

import com.google.common.collect.testing.Helpers.assertContains
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.Fix
import net.corda.finance.contracts.FixOf
import net.corda.finance.contracts.asset.CASH
import net.corda.finance.contracts.asset.Cash
import net.corda.irs.flows.RatesFixFlow
import net.corda.testing.core.*
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class OracleNodeTearOffTests {
    private val TEST_DATA = NodeInterestRates.parseFile("""
        LIBOR 2016-03-16 1M = 0.678
        LIBOR 2016-03-16 2M = 0.685
        LIBOR 2016-03-16 1Y = 0.890
        LIBOR 2016-03-16 2Y = 0.962
        EURIBOR 2016-03-15 1M = 0.123
        EURIBOR 2016-03-15 2M = 0.111
        """.trimIndent())

    private val dummyCashIssuer = TestIdentity(CordaX500Name("Cash issuer", "London", "GB"))

    val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
    val alice = TestIdentity(ALICE_NAME, 70)
    private lateinit var mockNet: MockNetwork
    private lateinit var aliceNode: StartedMockNode
    private lateinit var oracleNode: StartedMockNode
    private val oracle get() = oracleNode.services.myInfo.singleIdentity()

    @Before
    // DOCSTART 1
    fun setUp() {
        mockNet = MockNetwork(cordappPackages = listOf(Cash::class.packageName, NodeInterestRates::class.packageName))
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        oracleNode = mockNet.createNode(MockNodeParameters(legalName = BOB_NAME)).apply {
            transaction {
                services.cordaService(NodeInterestRates.Oracle::class.java).knownFixes = TEST_DATA
            }
        }
    }
    // DOCEND 1

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    // DOCSTART 2
    @Test
    fun `verify that the oracle signs the transaction if the interest rate within allowed limit`() {
        // Create a partial transaction
        val tx = TransactionBuilder(DUMMY_NOTARY)
                .withItems(TransactionState(1000.DOLLARS.CASH issuedBy dummyCashIssuer.party ownedBy alice.party, Cash.PROGRAM_ID, DUMMY_NOTARY))
        // Specify the rate we wish to get verified by the oracle
        val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")

        // Create a new flow for the fix
        val flow = FilteredRatesFlow(tx, oracle, fixOf, BigDecimal("0.675"), BigDecimal("0.1"))
        // Run the mock network and wait for a result
        mockNet.runNetwork()
        val future = aliceNode.startFlow(flow)
        mockNet.runNetwork()
        future.getOrThrow()

        // We should now have a valid rate on our tx from the oracle.
        val fix = tx.toWireTransaction(aliceNode.services).commands.map { it  }.first()
        assertEquals(fixOf, (fix.value as Fix).of)
        // Check that the response contains the valid rate, which is within the supplied tolerance
        assertEquals(BigDecimal("0.678"), (fix.value as Fix).value)
        // Check that the transaction has been signed by the oracle
        assertContains(fix.signers, oracle.owningKey)
    }
    // DOCEND 2

    @Test
    fun `verify that the oracle rejects the transaction if the interest rate is outside the allowed limit`() {
        val tx = makePartialTX()
        val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
        val flow = FilteredRatesFlow(tx, oracle, fixOf, BigDecimal("0.695"), BigDecimal("0.01"))
        LogHelper.setLevel("rates")

        mockNet.runNetwork()
        val future = aliceNode.startFlow(flow)
        mockNet.runNetwork()
        assertThatThrownBy{
            future.getOrThrow()
        }.isInstanceOf(RatesFixFlow.FixOutOfRange::class.java).hasMessage("Fix out of range by 0.017")
    }

    @Test
    fun `verify that the oracle rejects the transaction if there is a privacy leak`() {
        val tx = makePartialTX()
        val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")
        val flow = OverFilteredRatesFlow(tx, oracle, fixOf, BigDecimal("0.675"), BigDecimal("0.1"))
        LogHelper.setLevel("rates")

        mockNet.runNetwork()
        val future = aliceNode.startFlow(flow)
        mockNet.runNetwork()
        //The oracle
        assertThatThrownBy{
            future.getOrThrow()
        }.isInstanceOf(UnexpectedFlowEndException::class.java)
    }

    // Creates a version of [RatesFixFlow] that makes the command
    class FilteredRatesFlow(tx: TransactionBuilder,
                            oracle: Party,
                            fixOf: FixOf,
                            expectedRate: BigDecimal,
                            rateTolerance: BigDecimal,
                            progressTracker: ProgressTracker = tracker(fixOf.name))
        : RatesFixFlow(tx, oracle, fixOf, expectedRate, rateTolerance, progressTracker) {
        override fun filtering(elem: Any): Boolean {
            return when (elem) {
                is Command<*> -> oracle.owningKey in elem.signers && elem.value is Fix
                else -> false
            }
        }
    }

    // Creates a version of [RatesFixFlow] that makes the command
    class OverFilteredRatesFlow(tx: TransactionBuilder,
                                oracle: Party,
                                fixOf: FixOf,
                                expectedRate: BigDecimal,
                                rateTolerance: BigDecimal,
                                progressTracker: ProgressTracker = tracker(fixOf.name))
        : RatesFixFlow(tx, oracle, fixOf, expectedRate, rateTolerance, progressTracker) {
        override fun filtering(elem: Any): Boolean = true
    }

    private fun makePartialTX() = TransactionBuilder(DUMMY_NOTARY).withItems(
            TransactionState(1000.DOLLARS.CASH issuedBy dummyCashIssuer.party ownedBy alice.party, Cash.PROGRAM_ID, DUMMY_NOTARY))
}