package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException

/**
 * Thrown to indicate that something went bad on the node side.
 */
open class CordaClientException(message: String) : CordaRuntimeException(message), WithClientRelevantMessage