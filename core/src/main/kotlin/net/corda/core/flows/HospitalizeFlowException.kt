package net.corda.core.flows

import net.corda.core.CordaRuntimeException

/**
 * Thrown when the user needs to pause a flow. It will send the flow throwing it directly to the hospital.
 */
open class HospitalizeFlowException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause) {
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}