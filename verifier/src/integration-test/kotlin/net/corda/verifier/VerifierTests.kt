package net.corda.verifier

import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.VerifierType
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.node.NotarySpec
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VerifierTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private fun generateTransactions(number: Int): List<LedgerTransaction> {
        var currentLedger = GeneratedLedger.empty
        val transactions = arrayListOf<WireTransaction>()
        val random = SplittableRandom()
        for (i in 0 until number) {
            val (tx, ledger) = currentLedger.transactionGenerator.generateOrFail(random)
            transactions.add(tx)
            currentLedger = ledger
        }
        return transactions.map { currentLedger.resolveWireTransaction(it) }
    }

    @Ignore
    @Test
    fun `single verifier works with requestor`() {
        verifierDriver(extraCordappPackagesToScan = listOf("net.corda.finance.contracts")) {
            val aliceFuture = startVerificationRequestor(ALICE_NAME)
            val transactions = generateTransactions(100)
            val alice = aliceFuture.get()
            startVerifier(alice)
            alice.waitUntilNumberOfVerifiers(1)
            val results = transactions.map { alice.verifyTransaction(it) }.transpose().get()
            results.forEach {
                if (it != null) {
                    throw it
                }
            }
        }
    }

    @Test
    fun `single verification fails`() {
        verifierDriver(extraCordappPackagesToScan = listOf("net.corda.finance.contracts")) {
            val aliceFuture = startVerificationRequestor(ALICE_NAME)
            // Generate transactions as per usual, but then remove attachments making transaction invalid.
            val transactions = generateTransactions(1).map { it.copy(attachments = emptyList()) }
            val alice = aliceFuture.get()
            startVerifier(alice)
            alice.waitUntilNumberOfVerifiers(1)
            val verificationRejection = transactions.map { alice.verifyTransaction(it) }.transpose().get().single()
            assertTrue { verificationRejection is TransactionVerificationException.MissingAttachmentRejection}
            assertTrue { verificationRejection!!.message!!.contains("Contract constraints failed, could not find attachment") }
        }
    }

    @Ignore
    @Test
    fun `multiple verifiers work with requestor`() {
        verifierDriver {
            val aliceFuture = startVerificationRequestor(ALICE_NAME)
            val transactions = generateTransactions(100)
            val alice = aliceFuture.get()
            val numberOfVerifiers = 4
            for (i in 1..numberOfVerifiers) {
                startVerifier(alice)
            }
            alice.waitUntilNumberOfVerifiers(numberOfVerifiers)
            val results = transactions.map { alice.verifyTransaction(it) }.transpose().get()
            results.forEach {
                if (it != null) {
                    throw it
                }
            }
        }
    }

    @Test
    fun `verification redistributes on verifier death`() {
        verifierDriver {
            val aliceFuture = startVerificationRequestor(ALICE_NAME)
            val numberOfTransactions = 100
            val transactions = generateTransactions(numberOfTransactions)
            val alice = aliceFuture.get()
            val verifier1 = startVerifier(alice)
            val verifier2 = startVerifier(alice)
            startVerifier(alice)
            alice.waitUntilNumberOfVerifiers(3)
            val remainingTransactionsCount = AtomicInteger(numberOfTransactions)
            val futures = transactions.map { transaction ->
                val future = alice.verifyTransaction(transaction)
                // Kill verifiers as results are coming in, forcing artemis to redistribute.
                future.map {
                    val remaining = remainingTransactionsCount.decrementAndGet()
                    when (remaining) {
                        33 -> verifier1.get().process.destroy()
                        66 -> verifier2.get().process.destroy()
                    }
                    it
                }
            }
            futures.transpose().get()
        }
    }

    @Test
    fun `verification request waits until verifier comes online`() {
        verifierDriver {
            val aliceFuture = startVerificationRequestor(ALICE_NAME)
            val transactions = generateTransactions(100)
            val alice = aliceFuture.get()
            val futures = transactions.map { alice.verifyTransaction(it) }
            startVerifier(alice)
            futures.transpose().get()
        }
    }

    @Ignore("CORDA-1022")
    @Test
    fun `single verifier works with a node`() {
        verifierDriver(
                extraCordappPackagesToScan = listOf("net.corda.finance.contracts"),
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, verifierType = VerifierType.OutOfProcess))
        ) {
            val aliceNode = startNode(providedName = ALICE_NAME).getOrThrow()
            val notaryNode = defaultNotaryNode.getOrThrow() as NodeHandleInternal
            val alice = aliceNode.rpc.wellKnownPartyFromX500Name(ALICE_NAME)!!
            startVerifier(notaryNode)
            aliceNode.rpc.startFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()
            notaryNode.waitUntilNumberOfVerifiers(1)
            for (i in 1..10) {
                val cashFlowResult = aliceNode.rpc.startFlow(::CashPaymentFlow, 10.DOLLARS, alice).returnValue.get()
                assertNotNull(cashFlowResult)
            }
        }
    }
}