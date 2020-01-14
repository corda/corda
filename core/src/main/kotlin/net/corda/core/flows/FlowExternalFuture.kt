package net.corda.core.flows

import java.util.concurrent.CompletableFuture

interface FlowExternalFuture<R : Any> {
    fun execute(deduplicationId: String): CompletableFuture<R>
}

interface FlowExternalResult<R : Any> {
    fun execute(deduplicationId: String): R
}