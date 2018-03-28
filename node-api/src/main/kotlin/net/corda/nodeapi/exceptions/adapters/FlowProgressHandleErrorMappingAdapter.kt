package net.corda.nodeapi.exceptions.adapters

import net.corda.core.internal.concurrent.mapError
import net.corda.core.mapErrors
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.serialization.CordaSerializable
import net.corda.nodeapi.exceptions.InternalNodeException

/**
 * Adapter able to mask errors within a Corda node for RPC clients.
 */
@CordaSerializable
data class FlowProgressHandleErrorMappingAdapter<RESULT>(private val wrapped: FlowProgressHandle<RESULT>) : FlowProgressHandle<RESULT> by wrapped {

    override val returnValue = wrapped.returnValue.mapError(InternalNodeException.Companion::wrap)

    override val progress = wrapped.progress.mapErrors(InternalNodeException.Companion::wrap)

    override val stepsTreeIndexFeed = wrapped.stepsTreeIndexFeed?.mapErrors(InternalNodeException.Companion::wrap)

    override val stepsTreeFeed = wrapped.stepsTreeFeed?.mapErrors(InternalNodeException.Companion::wrap)
}