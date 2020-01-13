package net.corda.core.flows

import net.corda.core.internal.FlowAsyncOperation

interface FlowExternalFuture<R : Any> : FlowAsyncOperation<R>

interface FlowExternalResult<R : Any> {
    fun execute(deduplicationId: String): R
}