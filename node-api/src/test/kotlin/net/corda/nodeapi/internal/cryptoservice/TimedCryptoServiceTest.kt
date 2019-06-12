package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.RSA_SHA256
import net.corda.core.crypto.SignatureScheme
import net.corda.core.utilities.toSHA256Bytes
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.junit.Test
import java.lang.Thread.sleep
import java.security.PublicKey
import java.util.concurrent.TimeUnit.SECONDS

class TimedCryptoServiceTest {
    class TestService(private val timeout: Long) : CryptoService {
        private val testKeyPair = Crypto.generateKeyPair()

        override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
            sleep(SECONDS.toMillis(timeout))
            return testKeyPair.public
        }

        override fun containsKey(alias: String): Boolean {
            sleep(SECONDS.toMillis(timeout))
            return true
        }

        override fun getPublicKey(alias: String): PublicKey? {
            sleep(SECONDS.toMillis(timeout))
            return testKeyPair.public
        }

        override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
            sleep(SECONDS.toMillis(timeout))
            return testKeyPair.public.toSHA256Bytes() // Just some random bytes...
        }

        override fun getSigner(alias: String): ContentSigner {
            sleep(SECONDS.toMillis(timeout))
            return ContentSignerBuilder.build(RSA_SHA256, testKeyPair.private, BouncyCastlePQCProvider())
        }
    }

    @Test
    fun `timeout causes exception to be thrown`() {
        val timeout = 1L
        val underlying = TestService(timeout * 2)
        val service = TimedCryptoService(underlying, timeout)

        assertThatExceptionOfType(TimedCryptoServiceException::class.java).isThrownBy {
            service.generateKeyPair("", RSA_SHA256)
        }.withMessage("Timed-out while waiting for $timeout seconds")
    }

    @Test
    fun `when no timeout no exception is thrown`() {
        val timeout = 2L
        val underlying = TestService(timeout / 2)
        val service = TimedCryptoService(underlying, timeout)

        service.generateKeyPair("", RSA_SHA256)
    }
}
