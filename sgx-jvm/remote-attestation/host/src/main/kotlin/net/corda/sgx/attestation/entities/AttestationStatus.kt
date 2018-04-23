package net.corda.sgx.attestation.entities

/**
 * The status of the remote attestation process.
 *
 * @property message A human readable representation of the state.
 */
enum class AttestationStatus(val message: String) {

    /**
     * The remote attestation was successful.
     */
    SUCCESS("Remote attestation succeeded."),

    /**
     * The remote attestation failed.
     */
    FAIL("Remote attestation failed."),

}