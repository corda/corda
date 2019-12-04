package net.corda.core.flows

import net.corda.core.CordaRuntimeException

// DOCSTART 1
/**
 * This exception allows a flow to pass itself to the flow hospital. Once the flow reaches
 * the hospital it will determine how to progress depending on what [cause]s the exception wraps.
 * Assuming there are no important wrapped exceptions, throwing a [HospitalizeFlowException]
 * will place the flow in overnight observation, where it will be replayed at a later time.
 */
open class HospitalizeFlowException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause) {
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}
// DOCEND 1