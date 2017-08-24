package net.corda.core.node.flows

import net.corda.core.flows.FlowException

open class StateReplacementException @JvmOverloads constructor(message: String? = null, cause: Throwable? = null)
    : FlowException(message, cause)