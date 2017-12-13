package net.corda.sgx.system

/**
 * Extended Intel EPID group identifier.
 *
 * @property value The identifier for the extended EPID group.
 */
enum class ExtendedGroupIdentifier(val value: Int) {

    /**
     * Indicates that we are using Intel's Attestation Service.
     */
    INTEL(0),

}