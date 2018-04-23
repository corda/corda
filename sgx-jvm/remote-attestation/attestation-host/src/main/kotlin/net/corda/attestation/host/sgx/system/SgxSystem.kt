package net.corda.attestation.host.sgx.system

import net.corda.attestation.host.sgx.enclave.SgxStatus
import net.corda.attestation.host.sgx.entities.AttestationException
import net.corda.attestation.host.sgx.entities.QuoteStatus

/**
 * Query system properties of an SGX-enabled environment.
 */
interface SgxSystem {

    companion object {

        /**
         * Get [SgxDeviceStatus] from numeric code.
         */
        fun deviceStatusFromCode(code: Int): SgxDeviceStatus =
                enumValues<SgxDeviceStatus>().first { it.code == code }

        /**
         * Get [SgxStatus] from numeric code.
         */
        fun statusFromCode(code: Long): SgxStatus =
                enumValues<SgxStatus>().first { it.code == code }

        /**
         * Get [ExtendedGroupIdentifier] from a numeric identifier.
         */
        fun extendedGroupIdentifier(id: Int): ExtendedGroupIdentifier? =
                enumValues<ExtendedGroupIdentifier>().
                        firstOrNull { it.value == id }

        /**
         * Get [QuoteStatus] from string.
         */
        fun quoteStatusFromString(
                code: String
        ): QuoteStatus {
            return enumValues<QuoteStatus>()
                    .firstOrNull { it.name == code }
                    ?: throw AttestationException(
                    "Invalid quote status code '$code'")
        }

    }

    /**
     * Check if the client platform is enabled for Intel SGX. The application
     * must be run with administrator privileges to get the status
     * successfully.
     *
     * @return The current status of the SGX device.
     */
    fun getDeviceStatus(): SgxDeviceStatus

    /**
     * Get the extended Intel EPID Group the client uses by default. The key
     * used to sign a quote will be a member of the this group.
     */
    fun getExtendedGroupIdentifier(): ExtendedGroupIdentifier

    /**
     * Check if SGX is available and enabled in the current runtime
     * environment.
     *
     * @throws SgxUnavailableException If SGX is unavailable or for some reason
     * disabled.
     */
    fun ensureAvailable() {
        val status = getDeviceStatus()
        if (status != SgxDeviceStatus.ENABLED) {
            throw SgxUnavailableException(status)
        }
    }

}
