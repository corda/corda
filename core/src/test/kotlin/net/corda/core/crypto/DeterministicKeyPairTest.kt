package net.corda.core.crypto

import net.i2p.crypto.eddsa.spec.EdDSAGenParameterSpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable.CURVE_ED25519_SHA512
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec
import kotlin.test.assertEquals

/**
 * Deterministic KeyPair Derivation tests
 */
class DeterministicTest {

    init {
        Security.addProvider(BouncyCastleProvider())
        Security.addProvider(BouncyCastlePQCProvider())
    }

    val testKey = "12345678901234567890123456789012".toByteArray()

    @Test
    fun `RSA deterministic`() {
        val gen = KeyPairGenerator.getInstance("RSA", "BC")

        gen.initialize(3072, DeterministicSecureRandom(testKey))
        val pair1 = gen.generateKeyPair()

        gen.initialize(3072, DeterministicSecureRandom(testKey))
        val pair2 = gen.generateKeyPair()

        assertEquals(pair1.private, pair2.private)
        assertEquals(pair1.public, pair2.public)

        // check inequality
        gen.initialize(3072, DeterministicSecureRandom(testKey.plus(testKey)))
        val pair3 = gen.generateKeyPair()

        assertNotEquals(pair1.private, pair3.private)
        assertNotEquals(pair1.public, pair3.public)
    }

    @Test
    fun `ecdsa secp256k1 deterministic`() {
        val gen = KeyPairGenerator.getInstance("EC")
        val kpgparams = ECGenParameterSpec("secp256k1")

        gen.initialize(kpgparams, DeterministicSecureRandom(testKey))
        val pair1 = gen.generateKeyPair()

        gen.initialize(kpgparams, DeterministicSecureRandom(testKey))
        val pair2 = gen.generateKeyPair()

        assertEquals(pair1.private, pair2.private)
        assertEquals(pair1.public, pair2.public)

        // check inequality
        gen.initialize(kpgparams, DeterministicSecureRandom(testKey.plus(testKey)))
        val pair3 = gen.generateKeyPair()

        assertNotEquals(pair1.private, pair3.private)
        assertNotEquals(pair1.public, pair3.public)
    }

    @Test
    fun `ecdsa secp256r1 deterministic`() {
        val gen = KeyPairGenerator.getInstance("EC")
        val kpgparams = ECGenParameterSpec("secp256r1")

        gen.initialize(kpgparams, DeterministicSecureRandom(testKey))
        val pair1 = gen.generateKeyPair()

        gen.initialize(kpgparams, DeterministicSecureRandom(testKey))
        val pair2 = gen.generateKeyPair()

        assertEquals(pair1.private, pair2.private)
        assertEquals(pair1.public, pair2.public)

        // check inequality
        gen.initialize(kpgparams, DeterministicSecureRandom(testKey.plus(testKey)))
        val pair3 = gen.generateKeyPair()

        assertNotEquals(pair1.private, pair3.private)
        assertNotEquals(pair1.public, pair3.public)
    }

    @Test
    fun `eddsa ed25519 deterministic`() {
        val gen = net.i2p.crypto.eddsa.KeyPairGenerator()

        gen.initialize(EdDSAGenParameterSpec(CURVE_ED25519_SHA512), DeterministicSecureRandom(testKey))
        val pair1 = gen.generateKeyPair()

        gen.initialize(EdDSAGenParameterSpec(CURVE_ED25519_SHA512), DeterministicSecureRandom(testKey))
        val pair2 = gen.generateKeyPair()

        assertEquals(pair1.private, pair2.private)
        assertEquals(pair1.public, pair2.public)

        // check inequality
        gen.initialize(EdDSAGenParameterSpec(CURVE_ED25519_SHA512), DeterministicSecureRandom(testKey.plus(testKey)))
        val pair3 = gen.generateKeyPair()

        assertNotEquals(pair1.private, pair3.private)
        assertNotEquals(pair1.public, pair3.public)
    }

    @Test
    fun `SPHINCS256 deterministic`() {
        val gen = KeyPairGenerator.getInstance("SPHINCS256", "BCPQC")

        gen.initialize(SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256), DeterministicSecureRandom(testKey))
        val pair1 = gen.generateKeyPair()

        gen.initialize(SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256), DeterministicSecureRandom(testKey))
        val pair2 = gen.generateKeyPair()

        assertEquals(pair1.private, pair2.private)
        assertEquals(pair1.public, pair2.public)

        // check inequality
        gen.initialize(SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256), DeterministicSecureRandom(testKey.plus(testKey)))
        val pair3 = gen.generateKeyPair()

        assertNotEquals(pair1.private, pair3.private)
        assertNotEquals(pair1.public, pair3.public)
    }

}
