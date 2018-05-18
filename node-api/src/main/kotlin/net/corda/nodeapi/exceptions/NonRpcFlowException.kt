package net.corda.nodeapi.exceptions

import net.corda.core.flows.ClientRelevantError

/**
 * Thrown to indicate that a flow was not designed for RPC and should be started from an RPC client.
 */
class NonRpcFlowException(logicType: Class<*>) : IllegalArgumentException("${logicType.name} was not designed for RPC"), ClientRelevantError