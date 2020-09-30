package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowExternalAsyncOperation
import net.corda.core.flows.FlowExternalOperation
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.node.ServiceHub
import java.util.function.Supplier
import net.corda.core.serialization.CordaSerializable
import java.util.concurrent.CompletableFuture

/**
 * Interface for arbitrary operations that can be invoked in a flow asynchronously - the flow will suspend until the
 * operation completes. Operation parameters are expected to be injected via constructor.
 */
@CordaSerializable
interface FlowAsyncOperation<R : Any> {

    val collectErrorsFromSessions: Boolean
        get() = false

    /**
     * Performs the operation in a non-blocking fashion.
     * @param deduplicationId  If the flow restarts from a checkpoint (due to node restart, or via a visit to the flow
     * hospital following an error) the execute method might be called more than once by the Corda flow state machine.
     * For each duplicate call, the deduplicationId is guaranteed to be the same allowing duplicate requests to be
     * de-duplicated if necessary inside the execute method.
     */
    fun execute(deduplicationId: String): CordaFuture<R>
}

/** Executes the specified [operation] and suspends until operation completion. */
@Deprecated(
    "This has been replaced by [FlowLogic.await] that provides an improved and public API",
    ReplaceWith("net.corda.core.flows.FlowLogic.await")
)
@Suspendable
fun <T, R : Any> FlowLogic<T>.executeAsync(operation: FlowAsyncOperation<R>, maySkipCheckpoint: Boolean = false): R {
    val request = FlowIORequest.ExecuteAsyncOperation(operation)
    return stateMachine.suspend(request, maySkipCheckpoint)
}

/**
 * [WrappedFlowExternalAsyncOperation] is added to allow jackson to properly reference the data stored within the wrapped
 * [FlowExternalAsyncOperation].
 */
class WrappedFlowExternalAsyncOperation<R : Any>(val operation: FlowExternalAsyncOperation<R>) : FlowAsyncOperation<R> {
    override fun execute(deduplicationId: String): CordaFuture<R> {
        return operation.execute(deduplicationId).asCordaFuture()
    }
}

/**
 * [WrappedFlowExternalOperation] is added to allow jackson to properly reference the data stored within the wrapped
 * [FlowExternalOperation].
 *
 * The reference to [ServiceHub] is also needed by Kryo to properly keep a reference to [ServiceHub] so that
 * [FlowExternalOperation] can be run from the [ServiceHubCoreInternal.externalOperationExecutor] without causing errors when retrying a
 * flow. A [NullPointerException] is thrown if [FlowLogic.serviceHub] is accessed from [FlowLogic.await] when retrying a flow.
 */
class WrappedFlowExternalOperation<R : Any>(
    val serviceHub: ServiceHubCoreInternal,
    val operation: FlowExternalOperation<R>
) : FlowAsyncOperation<R> {
    override fun execute(deduplicationId: String): CordaFuture<R> {
        // Using a [CompletableFuture] allows unhandled exceptions to be thrown inside the background operation
        // the exceptions will be set on the future by [CompletableFuture.AsyncSupply.run]
        return CompletableFuture.supplyAsync(
            Supplier { this.operation.execute(deduplicationId) },
            serviceHub.externalOperationExecutor
        ).asCordaFuture()
    }
}

/**
 * Returns the name of the external operation implementation considering that it can be wrapped
 * by [WrappedFlowExternalAsyncOperation] or [WrappedFlowExternalOperation].
 */
val FlowAsyncOperation<*>.externalOperationImplName: String
    get() = when (this) {
        is WrappedFlowExternalAsyncOperation<*> -> operation.javaClass.canonicalName
        is WrappedFlowExternalOperation<*> -> operation.javaClass.canonicalName
        else -> javaClass.canonicalName
    }