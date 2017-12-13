package net.corda.sgx.bridge.wrapper

import net.corda.sgx.enclave.SgxStatus
import net.corda.sgx.system.ExtendedGroupIdentifier

/**
 * The result of a call to [NativeWrapper.getExtendedGroupIdentifier].
 */
data class ExtendedGroupIdentifierResult(

        /**
         * The extended Intel EPID group identifier (of type
         * [ExtendedGroupIdentifier] downstream).
         */
        val extendedGroupIdentifier: Int,

        /**
         * The result of the operation (of type [SgxStatus] downstream).
         */
        val result: Long

)
