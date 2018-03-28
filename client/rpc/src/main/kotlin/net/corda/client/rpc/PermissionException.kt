package net.corda.client.rpc

import net.corda.core.CordaRuntimeException
import net.corda.nodeapi.exceptions.WithClientRelevantMessage

/**
 * Thrown to indicate that the calling user does not have permission for something they have requested (for example
 * calling a method).
 */
class PermissionException(override val messageForClient: String) : CordaRuntimeException(messageForClient), WithClientRelevantMessage
