package net.corda.sgx.bridge.wrapper

import net.corda.sgx.enclave.SgxStatus
import net.corda.sgx.sealing.SealedSecret

/**
 * The result of a call to [NativeWrapper.verifyAttestationResponse].
 */
class VerificationResult(

        /**
         * The sealed secret returned if the attestation result was
         * successfully verified.
         */
        val secret: SealedSecret,

        /**
         * The outcome of the validation of the CMAC over the attestation
         * result message (of type [SgxStatus] downstream).
         */
        val cmacValidationStatus: Long,

        /**
         * The result of the operation (of type [SgxStatus] downstream).
         */
        val result: Long

)
