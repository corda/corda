package net.corda.attestation.host.sgx.enclave

import net.corda.attestation.host.sgx.system.SgxSystem

/**
 * The identifier of an enclave.
 */
typealias EnclaveIdentifier = Long

/**
 * Representation an enclave.
 */
interface Enclave {
    /**
     * The SGX-enabled system on which this enclave is running.
     */
    val system: SgxSystem

    /**
     * The enclave identifier.
     */
    val identifier: EnclaveIdentifier

    /**
     * Create enclave used for remote attestation, and consequently for secret
     * sealing and unsealing.
     */
    fun create(): SgxStatus

    /**
     * Destroy enclave if running.
     */
    fun destroy(): Boolean

    /**
     * Destroy and re-create the enclave. This is normally done if the enclave
     * is lost due to a power transition or similar events.
     */
    fun recreate(): SgxStatus {
        destroy()
        return create()
    }

    /**
     * Check whether the enclave has been run before or not.
     */
    fun isFresh(): Boolean

    /**
     * Check whether an enclave has already been created and initialized.
     * Otherwise, try to create required enclave or re-create one in the cases
     * where an older one has been lost due to a power transition or similar.
     *
     * @throws SgxException If unable to create enclave.
     * @throws SgxUnavailableException If SGX is unavailable or for some reason
     * disabled.
     */
    fun activate() {
        // First, make sure SGX is available and that it is enabled. Under some
        // circumstances, a reboot may be required to enable SGX. In either
        // case, as long as the extensions aren't enabled, an
        // [SgxUnavailableException] will be thrown.
        system.ensureAvailable()

        // If the enclave has already been created and is active, we are good
        // to proceed.
        var status = create()
        if (status == SgxStatus.SUCCESS) {
            return
        }

        // Check if an attestation enclave was previously created. If it was
        // and it is no longer available, recreate one to the same
        // specification. Note: Losing an enclave is normally the result of a
        // power transition.
        if (status == SgxStatus.ERROR_ENCLAVE_LOST) {
            status = recreate()
            if (status != SgxStatus.SUCCESS) {
                throw SgxException(status, identifier)
            }
            return
        }

        // Some other error occurred, let's abort
        throw SgxException(status, identifier)
    }
}
