package net.corda.attestation.host.sgx.sealing

import net.corda.attestation.host.sgx.AttestationEnclave
import net.corda.attestation.host.sgx.enclave.SgxStatus

/**
 * Representation of a provisioned secret.
 */
typealias ProvisionedSecret = ByteArray

/**
 * Representation of a sealed secret.
 */
typealias SealedSecret = ByteArray

/**
 * Manager for storing and managing secrets.
 *
 * @property enclave The facilitating attestation enclave.
 */
open class SecretManager(
    private val enclave: AttestationEnclave
) {

    /**
     * Check that an existing secret (if available) is valid and hasn't
     * expired.
     */
    fun isValid(): Boolean {
        // First off, check whether we actually have access to a secret
        if (!hasSecret()) {
            return false
        }

        // Then, ensure that we can unseal the secret, that the lease has not
        // expired, etc.
        val result = unsealSecret()
        return result == SgxStatus.SUCCESS
    }

    /**
     * Retrieve sealed secret, or null if not available.
     */
    open fun getSealedSecret(): SealedSecret? = null

    /**
     * Persist the sealed secret to disk or similar, for future use.
     *
     * @param sealedSecret The secret sealed to the enclave's context.
     */
    open fun persistSealedSecret(sealedSecret: SealedSecret) { }

    /**
     * Check whether we have a secret persisted already.
     */
    private fun hasSecret(): Boolean {
        return getSealedSecret() != null
    }

    /**
     * Check if we can unseal an existing secret.
     */
    private fun unsealSecret(): SgxStatus {
        val sealedSecret = getSealedSecret()
                ?: return SgxStatus.ERROR_INVALID_PARAMETER
        return enclave.unseal(sealedSecret)
    }

}
