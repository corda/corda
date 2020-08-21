package net.corda.core.flows

import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.internal.ServiceHubCoreInternal
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.node.ServiceHub
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

/**
 * [FlowExternalAsyncOperation] represents an external future that blocks a flow from continuing until the future returned by
 * [FlowExternalAsyncOperation.execute] has completed. Examples of external processes where [FlowExternalAsyncOperation] would be useful
 * include, triggering a long running process on an external system or retrieving information from a service that might be down.
 *
 * The flow will suspend while it is blocked to free up a flow worker thread, which allows other flows to continue processing while waiting
 * for the result of this process.
 *
 * Implementations of [FlowExternalAsyncOperation] should ideally hold references to any external values required by [execute]. These
 * references should be passed into the implementation's constructor. For example, an amount or a reference to a Corda Service could be
 * passed in.
 *
 * It is discouraged to insert into the node's database from a [FlowExternalAsyncOperation], except for keeping track of [deduplicationId]s
 * that have been processed. It is possible to interact with the database from inside a [FlowExternalAsyncOperation] but, for most
 * operations, is not currently supported.
 */
interface FlowExternalAsyncOperation<R : Any> {

    /**
     * Executes a future.
     *
     * The future created and returned from [execute] must handle its own threads. If a new thread is not spawned or taken from a thread
     * pool, then the flow worker thread will be used. This removes any benefit from using an [FlowExternalAsyncOperation].
     *
     * @param deduplicationId  If the flow restarts from a checkpoint (due to node restart, or via a visit to the flow
     * hospital following an error) the execute method might be called more than once by the Corda flow state machine.
     * For each duplicate call, the deduplicationId is guaranteed to be the same allowing duplicate requests to be
     * de-duplicated if necessary inside the execute method.
     */
    fun execute(deduplicationId: String): CompletableFuture<R>
}

/**
 * [FlowExternalOperation] represents an external process that blocks a flow from continuing until the result of [execute]
 * has been retrieved. Examples of external processes where [FlowExternalOperation] would be useful include, triggering a long running
 * process on an external system or retrieving information from a service that might be down.
 *
 * The flow will suspend while it is blocked to free up a flow worker thread, which allows other flows to continue processing while waiting
 * for the result of this process.
 *
 * Implementations of [FlowExternalOperation] should ideally hold references to any external values required by [execute]. These references
 * should be passed into the implementation's constructor. For example, an amount or a reference to a Corda Service could be passed in.
 *
 * It is discouraged to insert into the node's database from a [FlowExternalOperation], except for keeping track of [deduplicationId]s that
 * have been processed. It is possible to interact with the database from inside a [FlowExternalOperation] but, for most operations, is not
 * currently supported.
 */
interface FlowExternalOperation<R : Any> {

    /**
     * Executes a blocking operation.
     *
     * The execution of [execute] will be run on a thread from the node's external process thread pool when called by [FlowLogic.await].
     *
     * @param deduplicationId  If the flow restarts from a checkpoint (due to node restart, or via a visit to the flow
     * hospital following an error) the execute method might be called more than once by the Corda flow state machine.
     * For each duplicate call, the deduplicationId is guaranteed to be the same allowing duplicate requests to be
     * de-duplicated if necessary inside the execute method.
     */
    fun execute(deduplicationId: String): R
}

/**
 * [WrappedFlowExternalAsyncOperation] is added to allow jackson to properly reference the data stored within the wrapped
 * [FlowExternalAsyncOperation].
 */
internal class WrappedFlowExternalAsyncOperation<R : Any>(val operation: FlowExternalAsyncOperation<R>) : FlowAsyncOperation<R> {
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
internal class WrappedFlowExternalOperation<R : Any>(
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
