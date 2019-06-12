package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.crypto.SignatureScheme
import org.bouncycastle.operator.ContentSigner
import java.security.PublicKey
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

open class TimedCryptoServiceException(message: String?, cause: Throwable? = null) : Exception(message, cause)

/**
 * Provides the mechanism for running code with a timeout.  Uses a single-threaded [ExecutorService]
 */
interface Timeout {
    companion object {
        private val executor = Executors.newCachedThreadPool()
    }

    /**
     * Adds a timeout for the given [func].
     * @param timeout The time to wait on the function completing (in seconds)
     * @param func The call that we're waiting on
     * @return the return value of the function call
     * @throws TimedCryptoServiceException if we reach the timeout
     */
    fun <A> withTimeout(timeout: Long, func: () -> A) : A {
        try {
            return executor.invokeAny(listOf( Callable { func.invoke() } ), timeout, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            throw TimedCryptoServiceException("Timed-out while waiting for $timeout seconds")
        }
    }
}

/**
 * Timeout decorator implementation of a [CryptoService] that uses the [underlying] real service
 * wrapped with timeout functionality.  If the [CryptoService] call times out a [TimedCryptoServiceException]
 * is raised
 */
class TimedCryptoService<C : CryptoService>(private val underlying: C, private val timeout: Long) : CryptoService, Timeout {

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey =
        withTimeout(timeout) { underlying.generateKeyPair(alias, scheme) }

    override fun containsKey(alias: String): Boolean =
        withTimeout(timeout) { underlying.containsKey(alias) }

    override fun getPublicKey(alias: String): PublicKey? =
        withTimeout(timeout) { underlying.getPublicKey(alias) }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray =
        withTimeout(timeout) { underlying.sign(alias, data, signAlgorithm) }

    override fun getSigner(alias: String): ContentSigner =
        withTimeout(timeout) { underlying.getSigner(alias) }

    override fun defaultIdentitySignatureScheme(): SignatureScheme =
        withTimeout(timeout) { underlying.defaultIdentitySignatureScheme() }

    override fun defaultTLSSignatureScheme(): SignatureScheme {
        return withTimeout(timeout) { underlying.defaultTLSSignatureScheme() }
    }
}
