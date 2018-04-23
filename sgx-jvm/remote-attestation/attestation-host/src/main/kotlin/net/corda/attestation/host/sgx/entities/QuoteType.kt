package net.corda.attestation.host.sgx.entities

/**
 * The type of quote used in the attestation.
 *
 * @property value The native value of the quote type.
 */
enum class QuoteType(val value: Short) {
    /**
     * Unlinkable is random value-based, meaning that having two quotes you
     * cannot identify whether they are from the same source or not.
     */
    UNLINKABLE(0),

    /**
     * Linkable is name-based, meaning that having two quotes you can identify
     * if they come from the same enclave or not. Note that you can not
     * determine which enclave it is though.
     */
    LINKABLE(1);

    companion object {
        fun forValue(value: Short): QuoteType = when(value) {
            0.toShort() -> UNLINKABLE
            1.toShort() -> LINKABLE
            else -> throw IllegalArgumentException("Unknown QuoteType '$value'")
        }
    }
}
