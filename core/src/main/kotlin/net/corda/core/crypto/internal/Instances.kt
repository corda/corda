package net.corda.core.crypto.internal

import java.security.Provider
import java.security.Signature

/**
 * This is a collection of crypto related getInstance methods that tend to be quite inefficient and we want to be able to
 * optimise them en masse.
 */
object Instances {
    fun getSignatureInstance(algorithm: String, provider: Provider?) = Signature.getInstance(algorithm, provider)
}