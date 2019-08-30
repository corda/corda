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
 * Managed decorator implementation of a [CryptoService] that uses the [underlyingService] real service
 * wrapped with timeout and exception wrapping functionality.
 *   - If the [CryptoService] call times out a [TimedCryptoServiceException] is raised.
 *   - If the [underlyingService] throws a CryptoServiceException this decorator will rethrow it.
 *   - If the [underlyingService] throws any other exception this decorator will wrap it in a recoverable CryptoService exception.
 *
 * If an underlying exception is deemed unrecoverable then it should be wrapped in a ``CryptoServiceException``
 * with the flag ``isRecoverable=false``
 */
class ManagedCryptoService(val underlyingService: CryptoService, private val timeout: Duration? = null) : CryptoService by underlyingService, AutoCloseable {

    private val executor = Executors.newCachedThreadPool()
    override fun close() {
        executor.shutdown()
    }

    /**
     * Adds a timeout and exception wrapping for the given [func].
     * @param func The call that we're managing
     * @return the return value of the function call
     * @throws TimedCryptoServiceException if we reach the timeout
     * @throws CryptoServiceException if the underlying service throws any exception (underlying exception may be wrapped in [CryptoServiceException])
     */
    private fun <A> managedCall(func: () -> A) : A {
        try {
            return executor.submit(func).getOrThrow(timeout)
        } catch (e: TimeoutException) {
            throw TimedCryptoServiceException("Timed-out while waiting for ${timeout?.toMillis()} milliseconds")
        } catch (e: CryptoServiceException) {
            // Nothing to do, already the correct exception type
            throw e
        } catch (e: Exception) {
            throw CryptoServiceException("CryptoService operation failed", e, isRecoverable = true)
        }
    }

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey =
        managedCall { underlyingService.generateKeyPair(alias, scheme) }

    override fun containsKey(alias: String): Boolean =
        managedCall { underlyingService.containsKey(alias) }

    override fun getPublicKey(alias: String): PublicKey? =
        managedCall { underlyingService.getPublicKey(alias) }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray =
        managedCall { underlyingService.sign(alias, data, signAlgorithm) }

    override fun getSigner(alias: String): ContentSigner =
        managedCall { underlyingService.getSigner(alias) }

    // ******************************************************
    // ENTERPRISE ONLY CODE FOR WRAPPING KEYS API STARTS HERE
    // ******************************************************

    override fun createWrappingKey(alias: String, failIfExists: Boolean) =
        managedCall { underlyingService.createWrappingKey(alias, failIfExists) }

    override fun generateWrappedKeyPair(masterKeyAlias: String, childKeyScheme: SignatureScheme): Pair<PublicKey, WrappedPrivateKey> =
        managedCall { underlyingService.generateWrappedKeyPair(masterKeyAlias, childKeyScheme) }

    override fun sign(masterKeyAlias: String, wrappedPrivateKey: WrappedPrivateKey, payloadToSign: ByteArray): ByteArray =
        managedCall { underlyingService.sign(masterKeyAlias, wrappedPrivateKey, payloadToSign) }

    // *****************************************************
    // ENTERPRISE ONLY CODE FOR WRAPPING KEYS API ENDS HERE
    // *****************************************************
}
