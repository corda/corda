package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.crypto.SignatureScheme
import net.corda.core.utilities.getOrThrow
import org.bouncycastle.operator.ContentSigner
import java.security.PublicKey
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

class TimedCryptoServiceException(message: String?, cause: Throwable? = null) : CryptoServiceException(message, cause)

/**
 * Timeout decorator implementation of a [CryptoService] that uses the [underlyingService] real service
 * wrapped with timeout functionality.  If the [CryptoService] call times out a [TimedCryptoServiceException]
 * is raised
 */
class TimedCryptoService(val underlyingService: CryptoService, private val timeout: Duration? = null) : CryptoService by underlyingService, AutoCloseable {

    private val executor = Executors.newCachedThreadPool()
    override fun close() {
        executor.shutdown()
    }

    /**
     * Adds a timeout for the given [func].
     * @param timeout The time to wait on the function completing (in milliseconds)
     * @param func The call that we're waiting on
     * @return the return value of the function call
     * @throws TimedCryptoServiceException if we reach the timeout
     */
    private fun <A> withTimeout(timeout: Duration?, func: () -> A) : A {
        try {
            return executor.submit(func).getOrThrow(timeout)
        } catch (e: TimeoutException) {
            throw TimedCryptoServiceException("Timed-out while waiting for $timeout milliseconds")
        }
    }

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey =
        withTimeout(timeout) { underlyingService.generateKeyPair(alias, scheme) }

    override fun containsKey(alias: String): Boolean =
        withTimeout(timeout) { underlyingService.containsKey(alias) }

    override fun getPublicKey(alias: String): PublicKey? =
        withTimeout(timeout) { underlyingService.getPublicKey(alias) }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray =
        withTimeout(timeout) { underlyingService.sign(alias, data, signAlgorithm) }

    override fun getSigner(alias: String): ContentSigner =
        withTimeout(timeout) { underlyingService.getSigner(alias) }
}
