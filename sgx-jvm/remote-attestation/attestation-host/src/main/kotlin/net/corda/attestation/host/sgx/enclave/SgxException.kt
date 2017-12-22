package net.corda.attestation.host.sgx.enclave

import net.corda.attestation.host.HostException
import net.corda.attestation.host.sgx.AttestationContext

/**
 * Exception raised whenever there's a problem creating, destroying or
 * interacting with an enclave or SGX.
 *
 * @property status The status or outcome of an SGX operation.
 * @property enclaveIdentifier The identifier of the enclave, if available.
 * @property context The established remote attestation context, if available.
 */
class SgxException @JvmOverloads constructor(
    val status: SgxStatus,
    private val enclaveIdentifier: EnclaveIdentifier? = null,
    private val context: AttestationContext? = null
) : HostException(status.message) {
    /**
     * Human readable representation of the exception.
     */
    override fun toString(): String {
        val message = super.toString()
        val identifierString = if (enclaveIdentifier != null) {
            "0x${java.lang.Long.toHexString(enclaveIdentifier)}"
        } else {
            "null"
        }
        val contextString = if (context != null) {
            "0x${java.lang.Integer.toHexString(context)}"
        } else {
            "null"
        }
        return "$message (enclave=$identifierString, " +
                "context=$contextString, status=${status.name})"
    }
}
