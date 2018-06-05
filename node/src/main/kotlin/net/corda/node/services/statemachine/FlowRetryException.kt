package net.corda.node.services.statemachine

/** This exception is fired once the retry timeout expires. It will indicate to the flow hospital to restart the flow. */
data class FlowRetryException(val maxRetries: Int) : Exception()