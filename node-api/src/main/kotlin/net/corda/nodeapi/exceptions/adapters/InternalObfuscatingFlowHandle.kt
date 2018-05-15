package net.corda.nodeapi.exceptions.adapters

import net.corda.core.internal.concurrent.mapError
import net.corda.core.messaging.FlowHandle
import net.corda.core.serialization.CordaSerializable
import net.corda.nodeapi.exceptions.InternalNodeException

/**
 * Adapter able to mask errors within a Corda node for RPC clients.
 */
@CordaSerializable
data class InternalObfuscatingFlowHandle<RESULT>(val wrapped: FlowHandle<RESULT>) : FlowHandle<RESULT> by wrapped {

    override val returnValue = wrapped.returnValue.mapError(InternalNodeException.Companion::obfuscate)
}