/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.CordaSerializable

// DOCSTART FlowAsyncOperation
/**
 * Interface for arbitrary operations that can be invoked in a flow asynchronously - the flow will suspend until the
 * operation completes. Operation parameters are expected to be injected via constructor.
 */
@CordaSerializable
interface FlowAsyncOperation<R : Any> {
    /** Performs the operation in a non-blocking fashion. */
    fun execute(): CordaFuture<R>
}
// DOCEND FlowAsyncOperation

// DOCSTART executeAsync
/** Executes the specified [operation] and suspends until operation completion. */
@Suspendable
fun <T, R : Any> FlowLogic<T>.executeAsync(operation: FlowAsyncOperation<R>, maySkipCheckpoint: Boolean = false): R {
    val request = FlowIORequest.ExecuteAsyncOperation(operation)
    return stateMachine.suspend(request, maySkipCheckpoint)
}
// DOCEND executeAsync