package net.corda.core.crypto

import net.corda.testing.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import java.security.SignatureException
import kotlin.test.assertTrue

/**
 * Digital signature MetaData tests.
 */
class TransactionSignatureTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    val testBytes = "12345678901234567890123456789012".toByteArray()

    /** Valid sign and verify. */
    @Test
    fun `Signature metadata full sign and verify`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")

        // Create a SignableData object.
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))

        // Sign the meta object.
        val transactionSignature: TransactionSignature = keyPair.sign(signableData)

        // Check auto-verification.
        assertTrue(transactionSignature.verify(testBytes.sha256()))

        // Check manual verification.
        assertTrue(Crypto.doVerify(testBytes.sha256(), transactionSignature))
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
