package net.corda.nodeapi.internal.cryptoservice

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.RSA_SHA256
import net.corda.core.internal.times
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.lang.Thread.sleep
import java.time.Duration

class TimedCryptoServiceTest {
    private val testKeyPair = Crypto.generateKeyPair()
    private val timeout = Duration.ofMillis(500)

    @Test
    fun `timeout causes exception to be thrown`() {
        val underlying = rigorousMock<BCCryptoService>().also {
            doAnswer { sleep((timeout * 2).toMillis()); return@doAnswer testKeyPair.public }.whenever(it).generateKeyPair(any(), any())
        }
        val service = TimedCryptoService(underlying, timeout)

        assertThatExceptionOfType(TimedCryptoServiceException::class.java).isThrownBy {
            service.generateKeyPair("", RSA_SHA256)
        }.withMessage("Timed-out while waiting for $timeout milliseconds")
    }

    @Test
    fun `when no timeout no exception is thrown`() {
        val underlying = rigorousMock<BCCryptoService>().also {
            doAnswer { return@doAnswer testKeyPair.public }.whenever(it).generateKeyPair(any(), any())
        }
        val service = TimedCryptoService(underlying, timeout)

        service.generateKeyPair("", RSA_SHA256)
    }
}
