package net.corda.attestation.host.sgx.system

enum class ExtendedGroupIdentifier(val value: Int) {

    /**
     * Indicates that we are using Intel's Attestation Service.
     */
    INTEL(0),

}