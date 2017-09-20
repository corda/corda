package net.corda.core.internal

import net.corda.core.crypto.generateKeyPair
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.junit.Test
import java.util.*
import kotlin.test.assertTrue

class X509EdDSAEngineTest {
    companion object {
        private const val SEED = 20170920L
        private const val TEST_DATA_SIZE = 128
    }

    /**
     * Put the X509EdDSA engine through basic tests to verify that the functions are hooked up correctly.
     */
    @Test
    fun `sign and verify`() {
        val engine = X509EdDSAEngine()
        val keyPair = generateKeyPair()
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
}