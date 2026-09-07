package net.corda.core.crypto.internal

import net.corda.core.crypto.SignatureScheme
import net.corda.core.internal.LazyPool
import java.security.Provider
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

/**
 * This is a collection of crypto related getInstance methods that tend to be quite inefficient and we want to be able to
 * optimise them en masse.
 */
object Instances {
    fun <A> withSignature(signatureScheme: SignatureScheme, func: (signature: Signature) -> A): A {
        val signature = getSignatureInstance(signatureScheme.signatureName, providerMap[signatureScheme.providerName])
        try {
            return func(signature)
        } finally {
            releaseSignatureInstance(signature)
        }
    }

    fun getSignatureInstance(algorithm: String, provider: Provider?) = signatureFactory.borrow(algorithm, provider)
    fun releaseSignatureInstance(sig: Signature) = signatureFactory.release(sig)

    private val signatureFactory: SignatureFactory = CachingSignatureFactory()

    // The provider itself is a very bad key class as hashCode() is expensive and contended.  So use name and version instead.
    private data class SignatureKey(val algorithm: String, val providerName: String?, val providerVersion: String?) {
        constructor(algorithm: String, provider: Provider?) : this(algorithm, provider?.name, provider?.versionStr)
    }

    private class CachingSignatureFactory : SignatureFactory {
        private val signatureInstances = ConcurrentHashMap<SignatureKey, LazyPool<Signature>>()

        override fun borrow(algorithm: String, provider: Provider?): Signature {
            return signatureInstances.getOrPut(SignatureKey(algorithm, provider)) {
                LazyPool(newInstance = { Signature.getInstance(algorithm, provider) })
            }.borrow()
        }

        override fun release(sig: Signature): Unit =
                signatureInstances[SignatureKey(sig.algorithm, sig.provider)]?.release(sig)!!
    }

    private fun makeFactory(): SignatureFactory {
        return object : SignatureFactory {
            override fun borrow(algorithm: String, provider: Provider?): Signature {
                return Signature.getInstance(algorithm, provider)
            }
        }
    }
}

interface SignatureFactory {
    fun borrow(algorithm: String, provider: Provider?): Signature
    fun release(sig: Signature) {}
}
