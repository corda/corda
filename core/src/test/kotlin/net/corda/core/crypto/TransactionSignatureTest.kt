package net.corda.core.crypto

import net.corda.testing.TestDependencyInjectionBase
import org.junit.Test
import java.security.SignatureException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Digital signature MetaData tests.
 */
class TransactionSignatureTest : TestDependencyInjectionBase() {

    val testBytes = "12345678901234567890123456789012".toByteArray()

    /** Valid sign and verify. */
    @Test
    fun `Signature metadata full sign and verify`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")

        val instant = Instant.now()
        // Create a SignableData object.
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID, instant))

        // Sign testBytes + the meta object.
        val transactionSignature: TransactionSignature = keyPair.sign(signableData)

        // Check auto-verification.
        assertTrue(transactionSignature.verify(testBytes.sha256()))

        // Check manual verification.
        assertTrue(Crypto.doVerify(testBytes.sha256(), transactionSignature))

        // Test attached timestamp in signature.
        assertEquals(instant, transactionSignature.signatureMetadata.timestamp)

        // Let's sign with a different timestamp.
        val newInstant = instant.plusMillis(1)
        assertNotEquals(instant, newInstant)

        val signableDataNewTimestamp = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID, newInstant))
        // Sign testBytes + the new meta object.
        val transactionSignatureNewTimestamp: TransactionSignature = keyPair.sign(signableDataNewTimestamp)
        assertTrue(Crypto.doVerify(testBytes.sha256(), transactionSignatureNewTimestamp))
        // Test attached timestamp in signature.
        assertEquals(newInstant, transactionSignatureNewTimestamp.signatureMetadata.timestamp)
    }

    /** Verification should fail; corrupted metadata - clearData (Merkle root) has changed. */
    @Test(expected = SignatureException::class)
    fun `Signature metadata full failure clearData has changed`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))
        val transactionSignature = keyPair.sign(signableData)
        Crypto.doVerify((testBytes + testBytes).sha256(), transactionSignature)
    }
}
