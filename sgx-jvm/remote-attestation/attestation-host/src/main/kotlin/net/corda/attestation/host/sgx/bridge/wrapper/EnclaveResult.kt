package net.corda.attestation.host.sgx.bridge.wrapper

import net.corda.attestation.host.sgx.enclave.EnclaveIdentifier

/**
 * The result of a call to [NativeWrapper.createEnclave].
 */
class EnclaveResult(
    /**
     * The identifier of the created enclave, if any.
     */
     val identifier: EnclaveIdentifier,

     /**
      * The launch token of the enclave.
      */
     val token: LaunchToken,

     /**
      * The output status code of the enclave creation (of type [SgxStatus]
      * downstream).
      */
     val result: Long
)
