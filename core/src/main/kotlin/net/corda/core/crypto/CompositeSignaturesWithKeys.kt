package net.corda.core.crypto

import net.corda.annotations.serialization.CordaSerializable

/**
 * Custom class for holding signature data. This exists for later extension work to provide a standardised cross-platform
 * serialization format.
 */
@CordaSerializable
data class CompositeSignaturesWithKeys(val sigs: List<TransactionSignature>) {
    companion object {
        val EMPTY = CompositeSignaturesWithKeys(emptyList())
    }
}
