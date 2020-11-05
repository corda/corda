package net.corda.core.crypto

import net.corda.core.crypto.internal.DigestAlgorithmFactory
import java.util.function.Supplier

@Suppress("unused")
private class DigestSupplier(private val algorithm: String) : Supplier<DigestAlgorithm> {
    override fun get(): DigestAlgorithm = DigestAlgorithmFactory.create(algorithm)
    val digestLength: Int by lazy { get().digestLength }
}
