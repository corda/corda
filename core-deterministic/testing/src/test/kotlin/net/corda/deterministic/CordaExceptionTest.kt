package net.corda.deterministic

import net.corda.core.CordaException
import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionVerificationException.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import org.junit.Assert.*
import org.junit.Test
import java.security.PublicKey
import kotlin.test.assertFailsWith

class CordaExceptionTest {
    companion object {
        const val CONTRACT_CLASS = "com.r3.corda.contracts.TestContract"
        val TEST_HASH = SecureHash.zeroHash
        val TX_ID = SecureHash.allOnesHash

        val ALICE_NAME = CordaX500Name("Alice Corp", "Madrid", "ES")
        val ALICE_KEY: PublicKey = object : PublicKey {
            override fun getAlgorithm(): String = "TEST-256"
            override fun getFormat(): String = "<none>"
            override fun getEncoded() = byteArrayOf()
        }
        val ALICE = Party(ALICE_NAME, ALICE_KEY)

        val BOB_NAME = CordaX500Name("Bob Plc", "Rome", "IT")
        val BOB_KEY: PublicKey = object : PublicKey {
            override fun getAlgorithm(): String = "TEST-512"
            override fun getFormat(): String = "<none>"
            override fun getEncoded() = byteArrayOf()
        }
        val BOB = Party(BOB_NAME, BOB_KEY)
    }

    @Test
    fun testCordaException() {
        val ex = assertFailsWith<CordaException> { throw CordaException("BAD THING") }
        assertEquals("BAD THING", ex.message)
    }

    @Test
    fun testAttachmentResolutionException() {
        val ex = assertFailsWith<AttachmentResolutionException> { throw AttachmentResolutionException(TEST_HASH) }
        assertEquals(TEST_HASH, ex.hash)
    }

    @Test
    fun testTransactionResolutionException() {
        val ex = assertFailsWith<TransactionResolutionException> { throw TransactionResolutionException(TEST_HASH) }
        assertEquals(TEST_HASH, ex.hash)
    }

    @Test
    fun testConflictingAttachmentsRejection() {
        val ex = assertFailsWith<ConflictingAttachmentsRejection> { throw ConflictingAttachmentsRejection(TX_ID, CONTRACT_CLASS) }
        assertEquals(TX_ID, ex.txId)
        assertEquals(CONTRACT_CLASS, ex.contractClass)
    }

    @Test
    fun testNotaryChangeInWrongTransactionType() {
        val ex = assertFailsWith<NotaryChangeInWrongTransactionType> { throw NotaryChangeInWrongTransactionType(TX_ID, ALICE, BOB) }
        assertEquals(TX_ID, ex.txId)
        assertEquals(ALICE, ex.txNotary)
        assertEquals(BOB, ex.outputNotary)
    }
}