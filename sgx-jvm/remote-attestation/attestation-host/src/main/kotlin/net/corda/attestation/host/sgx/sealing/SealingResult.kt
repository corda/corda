package net.corda.attestation.host.sgx.sealing

/**
 * The outcome of a performed sealing operation.
 *
 * @property code The underlying status code.
 * @property message A human readable representation of the state.
 */
enum class SealingResult(val code: Long, val message: String) {

    /**
     * Sealing was successful.
     */
    SUCCESS(0, "Sealing was successful."),

    /**
     * Sealing was unsuccessful.
     */
    FAIL(1, "Failed to seal secret."),

}
