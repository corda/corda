package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.concurrent.CordaFutureImpl
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
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
interface FlowBackgroundProcess<R : Any> : FlowAsyncOperation<R> /* extending this interface just to get things to compile */ {
    /**
     * Performs the operation in a non-blocking fashion.
     * @param deduplicationId  If the flow restarts from a checkpoint (due to node restart, or via a visit to the flow
     * hospital following an error) the execute method might be called more than once by the Corda flow state machine.
     * For each duplicate call, the deduplicationId is guaranteed to be the same allowing duplicate requests to be
     * de-duplicated if necessary inside the execute method.
     */
    // commented out due to extending interface
//    fun execute(deduplicationId: String): CordaFuture<R>
}
// DOCEND FlowAsyncOperation

// Need to provide a way to configure the size of this thread pool
val executorService = Executors.newFixedThreadPool(8)

// DOCSTART executeAsync
/** Executes the specified [operation] and suspends until operation completion. */
@Suspendable
fun <T, R : Any> FlowLogic<T>.async(operation: FlowBackgroundProcess<R>): R {
    val request = FlowIORequest.ExecuteAsyncOperation(operation)
    return stateMachine.suspend(request, false)
}

@Suspendable
// probably shouldn't allow this as the deduplication id is never used
// instead, we can add our own deduplication handling here
fun <T, R : Any> FlowLogic<T>.async(operation: CordaFuture<R>): R {
    val asyncOperation = object : FlowBackgroundProcess<R> {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            // return the future that the user created themselves
            return operation
        }
    }
    return async(asyncOperation)
}

@Suspendable
fun <T, R : Any> FlowLogic<T>.async(operation: (deduplicationId: String) -> CordaFuture<R>): R {
    val asyncOperation = object : FlowBackgroundProcess<R> {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            // return the future that the user created themselves
            return operation(deduplicationId)
        }
    }
    return async(asyncOperation)
}

@Suspendable
fun <T, R : Any> FlowLogic<T>.blockingAsync(operation: (deduplicationId: String) -> R): R {
    val asyncOperation = object : FlowBackgroundProcess<R> {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            // Using a [CompletableFuture] allows unhandled exceptions to be thrown inside the background operation
            // the exceptions will be set on the future by [CompletableFuture.AsyncSupply.run]
            return CordaFutureImpl(CompletableFuture.supplyAsync(Supplier { operation(deduplicationId) }, executorService))
        }
    }
    return async(asyncOperation)
}

// provide an overload that handled deduplication for the user
// probably wont actually provide this
@Suspendable
fun <T, R : Any> FlowLogic<T>.blockingAsync(operation: () -> R): R {
    val asyncOperation = object : FlowBackgroundProcess<R> {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            val supplier = Supplier {
                // add deduplication code
                // persist the deduplication id to a table (this is an extra commit though)
                // check the id here for the user
                operation()
            }
            return CordaFutureImpl(CompletableFuture.supplyAsync(supplier, executorService))
        }
    }
    return async(asyncOperation)
}

@Suspendable
// probably shouldn't allow this as the deduplication id is never used
// instead, we can add our own deduplication handling here
// probably wont actually provide this
fun <T, R : Any> FlowLogic<T>.blockingAsync(operation: R): R {
    val asyncOperation = object : FlowBackgroundProcess<R> {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            // take the input function
            // run it on a new thread (using the threadpool and return a future
            return CordaFutureImpl(CompletableFuture.supplyAsync(Supplier { operation }, executorService))
        }
    }
    return async(asyncOperation)
}

//fun <T, R : Any> FlowLogic<T>.blockingAsync(operation: (deduplicationId: String) -> CordaFuture<R>): R {
//    val asyncOperation = object : FlowAsync<R> {
//        override fun execute(deduplicationId: String): CordaFuture<R> {
//            return operation(deduplicationId)
//        }
//    }
//    val request = FlowIORequest.ExecuteAsyncOperation(asyncOperation)
//    return stateMachine.suspend(request, false)
//}

// do i need to do this one?
// will require changes to the statemachine? (in action executor)
//fun <T, R : Any> FlowLogic<T>.nonBlockingAsync(operation: (deduplicationId: String) -> CordaFuture<R>): CordaFuture<R> {
//    val asyncOperation = object : FlowAsync<R> {
//        override fun execute(deduplicationId: String): CordaFuture<R> {
//            return operation(deduplicationId)
//        }
//    }
//    val request = FlowIORequest.ExecuteAsyncOperation(asyncOperation)
//    return stateMachine.suspend(request, false)
//}

// all a developer should need to do is set the completed result or the exceptional result
// the deduplication id needs to be passed into the function

// can the async operation declaration be done inline inside of the flow (like a normal future?)