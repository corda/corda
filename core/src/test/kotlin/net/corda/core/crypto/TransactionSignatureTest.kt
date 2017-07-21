package net.corda.core.crypto

import net.corda.testing.TestDependencyInjectionBase
import org.junit.Test
import java.security.SignatureException
import java.time.Instant
import kotlin.test.assertTrue

/**
 * Digital signature MetaData tests
 */
class TransactionSignatureTest : TestDependencyInjectionBase() {

    val testBytes = "12345678901234567890123456789012".toByteArray()

    /** valid sign and verify. */
    @Test
    fun `MetaData Full sign and verify`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")

        // create a MetaData.Full object
        val meta = MetaData("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL, Instant.now(), null, null, testBytes, keyPair.public)

        // sign the message
        val transactionSignature: TransactionSignature = keyPair.private.sign(meta)

        // check auto-verification
        assertTrue(transactionSignature.verify())

        // check manual verification
        assertTrue(keyPair.public.verify(transactionSignature))
    }

    /** Signing should fail, as I sign with a secpK1 key, but set schemeCodeName is set to secpR1. */
    @Test(expected = IllegalArgumentException::class)
    fun `MetaData Full failure wrong scheme`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val meta = MetaData("ECDSA_SECP256R1_SHA256", "M9", SignatureType.FULL, Instant.now(), null, null, testBytes, keyPair.public)
        keyPair.private.sign(meta)
    }

    /** Verification should fail; corrupted metadata - public key has changed. */
    @Test(expected = SignatureException::class)
    fun `MetaData Full failure public key has changed`() {
        val keyPair1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val keyPair2 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val meta = MetaData("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL, Instant.now(), null, null, testBytes, keyPair2.public)
        val transactionSignature = keyPair1.private.sign(meta)
        transactionSignature.verify()
    }

    /** Verification should fail; corrupted metadata - clearData has changed. */
    @Test(expected = SignatureException::class)
    fun `MetaData Full failure clearData has changed`() {
        val keyPair1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val meta = MetaData("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL, Instant.now(), null, null, testBytes, keyPair1.public)
        val transactionSignature = keyPair1.private.sign(meta)

        val meta2 = MetaData("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL, Instant.now(), null, null, testBytes.plus(testBytes), keyPair1.public)
        val transactionSignature2 = TransactionSignature(transactionSignature.signatureData, meta2)
        keyPair1.public.verify(transactionSignature2)
    }

    /** Verification should fail; corrupted metadata - schemeCodeName has changed from K1 to R1. */
    @Test(expected = SignatureException::class)
    fun `MetaData Wrong schemeCodeName has changed`() {
        val keyPair1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val meta = MetaData("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL, Instant.now(), null, null, testBytes, keyPair1.public)
        val transactionSignature = keyPair1.private.sign(meta)

        val meta2 = MetaData("ECDSA_SECP256R1_SHA256", "M9", SignatureType.FULL, Instant.now(), null, null, testBytes.plus(testBytes), keyPair1.public)
        val transactionSignature2 = TransactionSignature(transactionSignature.signatureData, meta2)
        keyPair1.public.verify(transactionSignature2)
    }
}
