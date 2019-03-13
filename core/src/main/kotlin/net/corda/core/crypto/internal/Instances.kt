package net.corda.core.crypto.internal

import io.netty.util.concurrent.FastThreadLocal
import net.corda.core.DeleteForDJVM
import net.corda.core.StubOutForDJVM
import java.lang.UnsupportedOperationException
import java.security.Provider
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

/**
 * This is a collection of crypto related getInstance methods that tend to be quite inefficient and we want to be able to
 * optimise them en masse.
 */
object Instances {
    // Modified for performance in Enterprise.
    fun getSignatureInstance(algorithm: String, provider: Provider?) = signatureFactory(algorithm, provider)

    // ********************************************
    // ENTERPRISE ONLY PERFORMANCE CODE BEGINS HERE
    // ********************************************

    // Used to work around banning of ConcurrentHashMap in DJVM
    private val signatureFactory: SignatureFactory = try {
        makeCachingFactory()
    } catch (e: UnsupportedOperationException) {
        // Thrown by DJVM for method stubbed out below.
        makeFactory()
    }

    // The provider itself is a very bad key class as hashCode() is expensive and contended.  So use name and version instead.
    private data class SignatureKey(val algorithm: String, val providerName: String?, val providerVersion: Double?) {
        constructor(algorithm: String, provider: Provider?) : this(algorithm, provider?.name, provider?.version)
    }

    @StubOutForDJVM
    private fun makeCachingFactory(): SignatureFactory {
        return CachingSignatureFactory()
    }

    @DeleteForDJVM
    private class CachingSignatureFactory : SignatureFactory {
        private val signatureInstances = ConcurrentHashMap<SignatureKey, FastThreadLocal<Signature>>()

        override fun invoke(algorithm: String, provider: Provider?): Signature {
            return signatureInstances.getOrPut(SignatureKey(algorithm, provider)) {
                object : FastThreadLocal<Signature>() {
                    override fun initialValue(): Signature = Signature.getInstance(algorithm, provider)
                }
            }.get()
        }
    }

    private fun makeFactory(): SignatureFactory {
        return object : SignatureFactory {
            override fun invoke(algorithm: String, provider: Provider?): Signature {
                return Signature.getInstance(algorithm, provider)
            }
        }
    }
}

typealias SignatureFactory = Function2<String, Provider?, Signature>
// ********************************************
// ENTERPRISE ONLY PERFORMANCE CODE ENDS HERE
// ********************************************