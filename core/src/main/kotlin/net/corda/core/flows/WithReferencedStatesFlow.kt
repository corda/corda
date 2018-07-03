package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.internal.executeAsync
import net.corda.core.node.ServiceHub
import java.util.concurrent.CompletableFuture

/**
 * Given a flow which uses [ReferenceState]s, the [WithReferencedStatesFlow] will execute the the flow as a subFlow.
 * If the flow fails due to a [NotaryError.Conflict] for a [ReferenceState], then it will be suspended until the
 * state refs for the [ReferenceState]s are updated.
 *
 * @param flowLogic a flow which uses reference states.
 */
class WithReferencedStatesFlow<T : Any>(val flowLogic: FlowLogic<T>) : FlowLogic<Any>() {

    private sealed class FlowResult {
        data class Success<T : Any>(val value: T) : FlowResult()
        data class Conflict(val stateRefs: Set<StateRef>) : FlowResult()
    }

    /** An [FlowAsyncOperation] which suspends a flow until the provided [StateRef]s have been updated. */
    private class WaitForStatesToUpdate(
            val stateRefs: Set<StateRef>,
            val services: ServiceHub
    ) : FlowAsyncOperation<Unit> {
        override fun execute(): CordaFuture<Unit> {
            val futures = stateRefs.map { stateRef ->
                services.vaultService.whenConsumed(stateRef).toCompletableFuture()
            }
            return CompletableFuture.allOf(*futures.toTypedArray()).thenApply { Unit }.asCordaFuture()
        }
    }

    /**
     * If the subFlow completes successfully then return the result. Alternatively, if it throws an exception then
     * determine if the exception is a [NotaryError.Conflict] and if so, then return a set of conflicting [StateRef]s.
     */
    private fun handleResult(result: Any): FlowResult {
        // Handle the result of the flow.
        // * We don't care about anything other than NotaryExceptions.
        // * It is a NotaryException but not a Conflict, then just rethrow.
        // * If the flow completes successfully then return the result.
        return when (result) {
            is NotaryException -> {
                val error = result.error
                if (error is NotaryError.Conflict) {
                    val conflictingReferenceStateRefs = error.consumedStates.filter {
                        it.value.type == ConsumedStateType.REFERENCE_INPUT_STATE
                    }.map { it.key }.toSet()
                    FlowResult.Conflict(conflictingReferenceStateRefs)
                } else {
                    throw result
                }
            }
            is FlowException -> throw result
            else -> FlowResult.Success(result)
        }
    }

    @Suspendable
    override fun call(): Any {
        // 1. Try starting the flow (as a subFlow) which uses the reference states.
        val result = try {
            subFlow(flowLogic)
        } catch (e: FlowException) {
            e
        }

        // 2. Process the flow result.
        val processedResult = handleResult(result)

        // 3. Return the flow result or wait for the StateRefs to be updated and try agian.
        return when (processedResult) {
            is FlowResult.Success<*> -> processedResult
            is FlowResult.Conflict -> {
                executeAsync(WaitForStatesToUpdate(processedResult.stateRefs, serviceHub))
                // The flow has now woken up because the conflicting
                // reference states have been updated.
                subFlow(flowLogic)
            }
        }
    }

}