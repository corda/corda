package net.corda.core.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.junit.Test
import java.security.Security
import java.security.SignatureException
import java.time.Instant
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Digital signature MetaData tests
 */
class DSMetaDataTest {

    init {
        Security.addProvider(BouncyCastleProvider())
        Security.addProvider(BouncyCastlePQCProvider())
    }

    val testBytes = "12345678901234567890123456789012".toByteArray()

    /** valid sign and verify. */
    @Test
    fun `MetaData Full sign and verify`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")

        // create a MetaData.Full object
        val meta = MetaData.Full("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL_MERKLE, Instant.now(), null, null, testBytes, keyPair.public)

        // sign the message
        val dsWithMetaDataFull: DSWithMetaDataFull = keyPair.private.sign(meta)

        // check auto-verification
        assertTrue(dsWithMetaDataFull.verify())

        // check manual verification
        assertTrue(keyPair.public.verify(dsWithMetaDataFull))
    }

    /** Signing should fail, as I sign with a secpK1 key, but set schemeCodeName is set to secpR1. */
    @Test(expected = IllegalArgumentException::class)
    fun `MetaData Full failure wrong scheme`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val meta = MetaData.Full("ECDSA_SECP256R1_SHA256", "M9", SignatureType.FULL_MERKLE, Instant.now(), null, null, testBytes, keyPair.public)
        keyPair.private.sign(meta)
    }

    /** Verification should fail; corrupted metadata - public key has changed. */
    @Test(expected = SignatureException::class)
    fun `MetaData Full failure public key has changed`() {
        val keyPair1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val keyPair2 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val meta = MetaData.Full("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL_MERKLE, Instant.now(), null, null, testBytes, keyPair2.public)
        val dsWithMetaDataFull = keyPair1.private.sign(meta)
        dsWithMetaDataFull.verify()
    }

    /** Verification should fail; corrupted metadata - clearData has changed. */
    @Test(expected = SignatureException::class)
    fun `MetaData Full failure clearData has changed`() {
        val keyPair1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val meta = MetaData.Full("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL_MERKLE, Instant.now(), null, null, testBytes, keyPair1.public)
        val dsWithMetaDataFull = keyPair1.private.sign(meta)

        val meta2 = MetaData.Full("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL_MERKLE, Instant.now(), null, null, testBytes.plus(testBytes), keyPair1.public)
        val dsWithMetaDataFull2 = DSWithMetaDataFull(dsWithMetaDataFull.signatureData, meta2)
        keyPair1.public.verify(dsWithMetaDataFull2)
    }

    /** Verification should fail; corrupted metadata - schemeCodeName has changed. */
    @Test(expected = SignatureException::class)
    fun `MetaData Wrong schemeCodeName has changed`() {
        val keyPair1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val meta = MetaData.Full("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL_MERKLE, Instant.now(), null, null, testBytes, keyPair1.public)
        val dsWithMetaDataFull = keyPair1.private.sign(meta)

        val meta2 = MetaData.Full("ECDSA_SECP256R1_SHA256", "M9", SignatureType.FULL_MERKLE, Instant.now(), null, null, testBytes.plus(testBytes), keyPair1.public)
        val dsWithMetaDataFull2 = DSWithMetaDataFull(dsWithMetaDataFull.signatureData, meta2)
        keyPair1.public.verify(dsWithMetaDataFull2)
    }
}
