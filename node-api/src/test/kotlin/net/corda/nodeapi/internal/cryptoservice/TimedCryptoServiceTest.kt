package net.corda.nodeapi.internal.cryptoservice

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.RSA_SHA256
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit.SECONDS

class TimedCryptoServiceTest {
    private val testKeyPair = Crypto.generateKeyPair()

    @Test
    fun `timeout causes exception to be thrown`() {
        val timeout = 1L
        val underlying = rigorousMock<BCCryptoService>().also {
            doAnswer { sleep(SECONDS.toMillis(timeout * 2)); return@doAnswer testKeyPair.public }.whenever(it).generateKeyPair(any(), any())
        }
        val service = TimedCryptoService(underlying, timeout)

        assertThatExceptionOfType(TimedCryptoServiceException::class.java).isThrownBy {
            service.generateKeyPair("", RSA_SHA256)
        }.withMessage("Timed-out while waiting for $timeout seconds")
    }

    @Test
    fun `when no timeout no exception is thrown`() {
        val timeout = 2L
        val underlying = rigorousMock<BCCryptoService>().also {
            doAnswer { sleep(SECONDS.toMillis(timeout / 2)); return@doAnswer testKeyPair.public }.whenever(it).generateKeyPair(any(), any())
        }
        val service = TimedCryptoService(underlying, timeout)

        service.generateKeyPair("", RSA_SHA256)
    }
}
