package net.corda.core.internal

import net.corda.core.crypto.Crypto
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.junit.Test
import sun.security.util.BitArray
import sun.security.util.ObjectIdentifier
import sun.security.x509.AlgorithmId
import sun.security.x509.X509Key
import java.math.BigInteger
import java.security.InvalidKeyException
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestX509Key(algorithmId: AlgorithmId, key: BitArray) : X509Key() {
    init {
        this.algid = algorithmId
        this.setKey(key)
        this.encode()
    }
}

class X509EdDSAEngineTest {
    companion object {
        private const val SEED = 20170920L
        private const val TEST_DATA_SIZE = 2000

        // offset into an EdDSA header indicating where the key header and actual key start
        // in the underlying byte array
        private const val keyHeaderStart = 9
        private const val keyStart = 12

        private fun toX509Key(publicKey: EdDSAPublicKey): X509Key {
            val internals = publicKey.encoded

            // key size in the header includes the count unused bits at the end of the key
            // [keyHeaderStart + 2] but NOT the key header ID [keyHeaderStart] so the
            // actual length of the key blob is size - 1
            val keySize = (internals[keyHeaderStart + 1].toInt()) - 1

            val key = ByteArray(keySize)
            System.arraycopy(internals, keyStart, key, 0, keySize)

            // 1.3.101.102 is the EdDSA OID
            return TestX509Key(AlgorithmId(ObjectIdentifier("1.3.101.112")), BitArray(keySize * 8, key))
        }
    }

    /**
     * Put the X509EdDSA engine through basic tests to verify that the functions are hooked up correctly.
     */
    @Test
    fun `sign and verify`() {
        val engine = X509EdDSAEngine()
        val keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger.valueOf(SEED))
        val publicKey = keyPair.public as EdDSAPublicKey
        val randomBytes = ByteArray(TEST_DATA_SIZE)
        Random(SEED).nextBytes(randomBytes)
        engine.initSign(keyPair.private)
        engine.update(randomBytes[0])
        engine.update(randomBytes, 1, randomBytes.size - 1)

        // Now verify the signature
        val signature = engine.sign()

        engine.initVerify(publicKey)
        engine.update(randomBytes)
        assertTrue { engine.verify(signature) }
    }

    /**
     * Verify that signing with an X509Key wrapped EdDSA key works.
     */
    @Test
    fun `sign and verify with X509Key`() {
        val engine = X509EdDSAEngine()
        val keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger.valueOf(SEED + 1))
        val publicKey = toX509Key(keyPair.public as EdDSAPublicKey)
        val randomBytes = ByteArray(TEST_DATA_SIZE)
        Random(SEED + 1).nextBytes(randomBytes)
        engine.initSign(keyPair.private)
        engine.update(randomBytes[0])
        engine.update(randomBytes, 1, randomBytes.size - 1)

        // Now verify the signature
        val signature = engine.sign()

        engine.initVerify(publicKey)
        engine.update(randomBytes)
        assertTrue { engine.verify(signature) }
    }

    /**
     * Verify that signing with an X509Key wrapped EdDSA key succeeds when using the underlying EdDSAEngine.
     */
    @Test
    fun `sign and verify with X509Key and old engine fails`() {
        val engine = EdDSAEngine()
        val keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger.valueOf(SEED + 1))
        val publicKey = toX509Key(keyPair.public as EdDSAPublicKey)
        val randomBytes = ByteArray(TEST_DATA_SIZE)
        Random(SEED + 1).nextBytes(randomBytes)
        engine.initSign(keyPair.private)
        engine.update(randomBytes[0])
        engine.update(randomBytes, 1, randomBytes.size - 1)

        // Now verify the signature
        val signature = engine.sign()
        engine.initVerify(publicKey)
        engine.update(randomBytes)
        engine.verify(signature)
    }

    /** Verify will fail if the input public key cannot be converted to EdDSA public key. */
    @Test
    fun `verify with non-supported key type fails`() {
        val engine = EdDSAEngine()
        val keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger.valueOf(SEED))
        assertFailsWith<InvalidKeyException> { engine.initVerify(keyPair.public) }
    }
}