package net.corda.verifier

import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.config.VerifierType
import net.corda.testing.ALICE
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.chooseIdentity
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.*
import net.corda.testing.driver.NetworkMapStartStrategy
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class VerifierTests {
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

    @Test
    fun `single verifier works with requestor`() {
        verifierDriver(extraCordappPackagesToScan = listOf("net.corda.finance.contracts")) {
            val aliceFuture = startVerificationRequestor(ALICE.name)
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
    fun `multiple verifiers work with requestor`() {
        verifierDriver {
            val aliceFuture = startVerificationRequestor(ALICE.name)
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
            val aliceFuture = startVerificationRequestor(ALICE.name)
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
            val aliceFuture = startVerificationRequestor(ALICE.name)
            val transactions = generateTransactions(100)
            val alice = aliceFuture.get()
            val futures = transactions.map { alice.verifyTransaction(it) }
            startVerifier(alice)
            futures.transpose().get()
        }
    }

    @Test
    fun `single verifier works with a node`() {
        verifierDriver(
                networkMapStartStrategy = NetworkMapStartStrategy.Dedicated(startAutomatically = true),
                extraCordappPackagesToScan = listOf("net.corda.finance.contracts")
        ) {
            val aliceFuture = startNode(providedName = ALICE.name)
            val notaryFuture = startNotaryNode(DUMMY_NOTARY.name, verifierType = VerifierType.OutOfProcess)
            val aliceNode = aliceFuture.get()
            val notaryNode = notaryFuture.get()
            val alice = notaryNode.rpc.wellKnownPartyFromX500Name(ALICE_NAME)!!
            val notary = notaryNode.rpc.notaryPartyFromX500Name(DUMMY_NOTARY_SERVICE_NAME)!!
            startVerifier(notaryNode)
            aliceNode.rpc.startFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), notary).returnValue.get()
            notaryNode.waitUntilNumberOfVerifiers(1)
            for (i in 1..10) {
                aliceNode.rpc.startFlow(::CashPaymentFlow, 10.DOLLARS, alice).returnValue.get()
            }
        }
    }
}