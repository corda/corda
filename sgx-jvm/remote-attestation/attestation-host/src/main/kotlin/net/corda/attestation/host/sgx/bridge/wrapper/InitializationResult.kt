package net.corda.attestation.host.sgx.bridge.wrapper

import net.corda.attestation.host.sgx.AttestationContext

/**
 * The result of a call to [NativeWrapper.initializeRemoteAttestation].
 */
data class InitializationResult(
    /**
     * The context returned from the call to sgx_ra_init().
     */
    val context: AttestationContext,

    /**
     * The result of the operation (of type [SgxStatus] downstream).
     */
    val result: Long
)