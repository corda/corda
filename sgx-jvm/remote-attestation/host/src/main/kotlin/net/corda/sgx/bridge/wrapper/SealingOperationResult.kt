package net.corda.sgx.bridge.wrapper

import net.corda.sgx.sealing.SealedSecret
import net.corda.sgx.sealing.SealingResult

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
