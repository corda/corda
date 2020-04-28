package sandbox.net.corda.core.crypto

/**
 * This is a dummy class that implements just enough of [net.corda.core.crypto.SecureHash]
 * to allow us to compile [sandbox.net.corda.core.crypto.Crypto].
 */
@Suppress("unused_parameter")
sealed class SecureHash(bytes: ByteArray) : sandbox.java.lang.Object()
