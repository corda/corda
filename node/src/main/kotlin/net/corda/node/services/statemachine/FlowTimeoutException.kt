package net.corda.node.services.statemachine

import net.corda.core.CordaException

/**
 * This exception is fired once the retry timeout of a [TimedFlow] expires.
 * It will indicate to the flow hospital to restart the flow.
 */
data class FlowTimeoutException(val maxRetries: Int) : CordaException("replaying flow from the last checkpoint")