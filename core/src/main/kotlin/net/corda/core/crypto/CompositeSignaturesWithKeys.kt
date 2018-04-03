package net.corda.core.crypto

import net.corda.annotations.serialization.Serializable

/**
 * Custom class for holding signature data. This exists for later extension work to provide a standardised cross-platform
 * serialization format.
 */
@Serializable
data class CompositeSignaturesWithKeys(val sigs: List<TransactionSignature>) {
    companion object {
        val EMPTY = CompositeSignaturesWithKeys(emptyList())
    }
}
