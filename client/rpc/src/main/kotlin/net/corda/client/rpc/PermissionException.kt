package net.corda.client.rpc

import net.corda.core.CordaRuntimeException
import net.corda.core.flows.ClientRelevantError

/**
 * Thrown to indicate that the calling user does not have permission for something they have requested (for example
 * calling a method).
 */
class PermissionException(message: String) : CordaRuntimeException(message), ClientRelevantError