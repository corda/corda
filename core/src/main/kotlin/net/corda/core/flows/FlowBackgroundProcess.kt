package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.concurrent.CordaFutureImpl
import net.corda.core.node.ServiceHub
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Supplier

// new executor pool
// configurable
// help them create the future
// both versions will suspend the flow
// blocking call runs on another thread while the flow unsuspends????
// keep the existing async operation and use it inside the statemachine as it works correctly (Extend it in new api)
// just need to wire everything above it together
// allow user to pass in a normal function that we convert into a future (blocking)
// allow a user to pass in a call to a future themselves
// rename to FlowBackgroundProcess
// provide an API that helps them create a CordaFuture or have docs that show them how to do so
// inside of a test, pull in an external library that returns futures and hook those into these functions (convert to CordaFuture)
// need to figure out better naming for these functions
// something about checking for a threadpool in service hub
// what happens when the background process fails?
// might need to remove [AsyncOperationTransitionException] so i can throw exceptions from inside the background process without always failing the flow

// create extension functions to be able to call the same code from within a service??

// does this need to be @CordaSerializable?
interface FlowBackgroundProcess<R : Any> : FlowAsyncOperation<R>

private abstract class FlowBackgroundProcessImpl<R : Any>(internal val serviceHub: ServiceHub) : FlowBackgroundProcess<R>

// Need to provide a way to configure the size of this thread pool
val executorService = Executors.newFixedThreadPool(8)

/** Executes the specified [operation] and suspends until operation completion. */
@Suspendable
fun <T, R : Any> FlowLogic<T>.await(operation: FlowBackgroundProcess<R>): R {
    val request = FlowIORequest.ExecuteAsyncOperation(operation)
    return stateMachine.suspend(request, false)
}

@Suspendable
// probably shouldn't allow this as the deduplication id is never used
// instead, we can add our own deduplication handling here
fun <T, R : Any> FlowLogic<T>.awaitFuture(operation: CordaFuture<R>): R {
    val process = object : FlowBackgroundProcess<R> {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            // return the future that the user created themselves
            return operation
        }
    }
    return await(process)
}

// Requires specifying the servicehub as it is lost from the transient values when replaying a flow
// the transient values get set to null which causes the flow to blow up when accessing servicehub
// I am not sure why it is set to null, it seems to be set correctly and used by other parts of the flow but not from inside the background process
// this means you cannot access any transient values or transient state from inside function passed into here
@Suspendable
fun <T, R : Any> FlowLogic<T>.awaitFuture(operation: (serviceHub: ServiceHub, deduplicationId: String) -> CordaFuture<R>): R {
    val process = object : FlowBackgroundProcessImpl<R>(serviceHub) {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            return operation(serviceHub, deduplicationId)
        }
    }
    return await(process)
}

@Suspendable
fun <T, R : Any> FlowLogic<T>.await(operation: (serviceHub: ServiceHub, deduplicationId: String) -> R): R {
    val process = object : FlowBackgroundProcessImpl<R>(serviceHub) {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            // Using a [CompletableFuture] allows unhandled exceptions to be thrown inside the background operation
            // the exceptions will be set on the future by [CompletableFuture.AsyncSupply.run]
            return CordaFutureImpl(CompletableFuture.supplyAsync(Supplier { operation(serviceHub, deduplicationId) }, executorService))
        }
    }
    return await(process)
}

// provide an overload that handled deduplication for the user
// probably wont actually provide this
@Suspendable
fun <T, R : Any> FlowLogic<T>.await(operation: (serviceHub: ServiceHub) -> R): R {
    val process = object : FlowBackgroundProcessImpl<R>(serviceHub) {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            val supplier = Supplier {
                // add deduplication code
                // persist the deduplication id to a table (this is an extra commit though)
                // check the id here for the user
                operation(serviceHub)
            }
            return CordaFutureImpl(CompletableFuture.supplyAsync(supplier, executorService))
        }
    }
    return await(process)
}

@Suspendable
// probably shouldn't allow this as the deduplication id is never used
// instead, we can add our own deduplication handling here
// probably wont actually provide this
fun <T, R : Any> FlowLogic<T>.await(operation: R): R {
    val process = object : FlowBackgroundProcess<R> {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            // take the input function
            // run it on a new thread (using the threadpool and return a future
            return CordaFutureImpl(CompletableFuture.supplyAsync(Supplier { operation }, executorService))
        }
    }
    return await(process)
}

// all a developer should need to do is set the completed result or the exceptional result
// the deduplication id needs to be passed into the function

// can the async operation declaration be done inline inside of the flow (like a normal future?)