package net.corda.attestation.host.sgx.bridge.wrapper

import net.corda.attestation.host.sgx.sealing.SealedSecret

/**
 * The result of a call to [NativeWrapper.sealSecret].
 */
class SealingOperationResult(
    /**
     * The sealed secret, if any.
     */
    val sealedSecret: SealedSecret,

    /**
     * The output result of the operation (of type [SealingResult]
     * downstream).
     */
    val result: Long
)
