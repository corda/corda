package net.corda.core.crypto.internal

import net.corda.core.crypto.Crypto
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.Signature

/**
 * This is a collection of crypto related getInstance methods that tend to be quite inefficient and we want to be able to
 * optimise them en masse.
 */
object Instances {
    private val bcProviderName = BouncyCastleProvider().name

    fun getSignatureInstance(algorithm: String, provider: Provider?): Signature {
        println("getSignatureInstance: $algorithm, ${provider?.info}, entryCount=${provider?.count()}, contains($algorithm=${provider?.containsValue(algorithm)})")
        println("$bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")
        try {
            return Signature.getInstance(algorithm, provider)
        }
        catch  (e: Exception) {
            println("Signature.getInstance() failed to locate $algorithm for provider ${provider?.info}.")
            provider?.forEach { println("${it.key} = ${it.value}") }
            e.printStackTrace()
            throw e
        }
    }
}