package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.crypto.SignatureScheme
import net.corda.core.internal.times
import org.bouncycastle.operator.ContentSigner
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.security.PublicKey
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.test.expect

class CryptoServiceTest {

    private val TEST_TIMEOUT = Duration.ofMillis(500)
    private var sleepTime = TEST_TIMEOUT
    private lateinit var stub: CryptoService

    @Before
    fun setUp() {
        stub = CryptoServiceStub()
    }

    @After
    fun tearDown() {
        stub.close()
    }

    inner class CryptoServiceStub : CryptoService(TEST_TIMEOUT) {
        private fun sleeper() {
            Thread.sleep(sleepTime.toMillis())
        }
        override fun _generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
            throw NotImplementedError("Not needed for this test")
        }

        override fun _containsKey(alias: String): Boolean {
            sleeper()
            return true
        }

        override fun _getPublicKey(alias: String): PublicKey {
            throw NotImplementedError("Not needed for this test")
        }

        override fun _sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
            throw NotImplementedError("Not needed for this test")
        }

        override fun _getSigner(alias: String): ContentSigner {
            throw NotImplementedError("Not needed for this test")
        }
    }

    @Ignore("When ENT-3658 is revisited")
    @Test
    fun `if no timeout is reached then correct value is returned`() {
        sleepTime = Duration.ZERO
        expect(true) { stub.containsKey("Test") }
    }

    @Ignore("When ENT-3658 is revisited")
    @Test
    fun `when timeout is reached the correct exception is thrown`() {
        sleepTime = TEST_TIMEOUT.times(2)
        assertFailsWith(TimedCryptoServiceException::class) { stub.containsKey("Test") }
    }
}

