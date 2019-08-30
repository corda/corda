package net.corda.nodeapi.internal.cryptoservice

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doThrow
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

class ManagedCryptoServiceTest {
    private val testKeyPair = Crypto.generateKeyPair()
    private val timeout = Duration.ofMillis(500)

    @Test
    fun `timeout causes exception to be thrown`() {
        val underlying = rigorousMock<BCCryptoService>().also {
            doAnswer { sleep((timeout * 2).toMillis()); return@doAnswer testKeyPair.public }.whenever(it).generateKeyPair(any(), any())
            doAnswer { sleep((timeout * 2).toMillis()); }.whenever(it).createWrappingKey(any(), any())
        }
        val service = ManagedCryptoService(underlying, timeout)

        assertThatExceptionOfType(TimedCryptoServiceException::class.java).isThrownBy {
            service.generateKeyPair("", RSA_SHA256)
        }.withMessage("Timed-out while waiting for ${timeout.toMillis()} milliseconds").matches { it.isRecoverable }

        assertThatExceptionOfType(TimedCryptoServiceException::class.java).isThrownBy {
            service.createWrappingKey("", true)
        }.withMessage("Timed-out while waiting for ${timeout.toMillis()} milliseconds").matches { it.isRecoverable }
    }

    @Test
    fun `when no timeout no exception is thrown`() {
        val underlying = rigorousMock<BCCryptoService>().also {
            doAnswer { return@doAnswer testKeyPair.public }.whenever(it).generateKeyPair(any(), any())
            doAnswer { return@doAnswer }.whenever(it).createWrappingKey(any(), any())
        }
        val service = ManagedCryptoService(underlying, timeout)

        service.generateKeyPair("", RSA_SHA256)
        service.createWrappingKey("", true)
    }

    @Test
    fun `exception is wrapped in CryptoServiceException`() {
        val underlying = rigorousMock<BCCryptoService>().also {
            doThrow(IllegalArgumentException::class).whenever(it).generateKeyPair(any(), any())
        }
        val service = ManagedCryptoService(underlying, timeout)

        assertThatExceptionOfType(CryptoServiceException::class.java).isThrownBy {
            service.generateKeyPair("", RSA_SHA256)
        }.withMessage("CryptoService operation failed").matches { it.isRecoverable }
    }

    @Test
    fun `non recoverable exception is untouched`() {
        val underlying = rigorousMock<BCCryptoService>().also {
            doAnswer {
                throw CryptoServiceException("Unrecoverable CryptoServiceException", isRecoverable = false)
            }.whenever(it).generateKeyPair(any(), any())
        }
        val service = ManagedCryptoService(underlying, timeout)

        assertThatExceptionOfType(CryptoServiceException::class.java).isThrownBy {
            service.generateKeyPair("", RSA_SHA256)
        }.withMessage("Unrecoverable CryptoServiceException").matches { !it.isRecoverable }
    }

}
