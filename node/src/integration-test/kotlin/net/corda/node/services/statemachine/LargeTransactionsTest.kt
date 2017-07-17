package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.InputStreamAndHash
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.aliceBobAndNotary
import net.corda.testing.contracts.DummyState
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Check that we can add lots of large attachments to a transaction and that it works OK, e.g. does not hit the
 * transaction size limit (which should only consider the hashes).
 */
class LargeTransactionsTest {
    @StartableByRPC @InitiatingFlow
    class SendLargeTransactionFlow(val hash1: SecureHash, val hash2: SecureHash, val hash3: SecureHash, val hash4: SecureHash) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val tx = TransactionBuilder(notary = DUMMY_NOTARY)
                    .addOutputState(DummyState())
                    .addAttachment(hash1)
                    .addAttachment(hash2)
                    .addAttachment(hash3)
                    .addAttachment(hash4)
            val stx = serviceHub.signInitialTransaction(tx, serviceHub.legalIdentityKey)
            // Send to the other side and wait for it to trigger resolution from us.
            sendAndReceive<Unit>(serviceHub.networkMapCache.getNodeByLegalName(BOB.name)!!.legalIdentity, stx)
        }
    }

    @InitiatedBy(SendLargeTransactionFlow::class) @Suppress("UNUSED")
    class ReceiveLargeTransactionFlow(private val counterParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val stx = receive<SignedTransaction>(counterParty).unwrap { it }
            subFlow(ResolveTransactionsFlow(stx, counterParty))
            // Unblock the other side by sending some dummy object (Unit is fine here as it's a singleton).
            send(counterParty, Unit)
        }
    }

    @Test
    fun checkCanSendLargeTransactions() {
        // These 4 attachments yield a transaction that's got >10mb attached, so it'd push us over the Artemis
        // max message size.
        val bigFile1 = InputStreamAndHash.createInMemoryTestZip(1024*1024*3, 0)
        val bigFile2 = InputStreamAndHash.createInMemoryTestZip(1024*1024*3, 1)
        val bigFile3 = InputStreamAndHash.createInMemoryTestZip(1024*1024*3, 2)
        val bigFile4 = InputStreamAndHash.createInMemoryTestZip(1024*1024*3, 3)
        driver(startNodesInProcess = true) {
            val (alice, _, _) = aliceBobAndNotary()
            alice.useRPC {
                val hash1 = it.uploadAttachment(bigFile1.inputStream)
                val hash2 = it.uploadAttachment(bigFile2.inputStream)
                val hash3 = it.uploadAttachment(bigFile3.inputStream)
                val hash4 = it.uploadAttachment(bigFile4.inputStream)
                assertEquals(hash1, bigFile1.sha256)
                // Should not throw any exceptions.
                it.startFlow(::SendLargeTransactionFlow, hash1, hash2, hash3, hash4).returnValue.get()
            }
        }
    }
}