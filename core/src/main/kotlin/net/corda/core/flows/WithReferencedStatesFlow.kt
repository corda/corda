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
 * Financial instruments are reference states and reference states are linear states.
 * ReferenceState : LinearState
 * FinancialInstrument : ReferenceState
 * Can reference states be ownable states? If they cannot be ownable then, we cannot reference ownable things in
 * transactions.
 * How do we specify reference states or query for them when we are building transactions:
 * - by stateref? PRobably not! We don't typically query by state ref.
 * - by some other field?
 */
class WithReferencedStatesFlow<T : Any>(val flowLogic: FlowLogic<T>) : FlowLogic<Any>() {

    sealed class FlowResult {
        data class Success<T : Any>(val value: T) : FlowResult()
        data class Conflict(val stateRefs: Set<StateRef>) : FlowResult()
    }

    private class WaitForStatesToUpdate(val stateRefs: Set<StateRef>, val services: ServiceHub) : FlowAsyncOperation<Unit> {
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
        // 1. We don't care about anything other than NotaryExceptions.
        // 2. It is NotaryExceptions but not a Conflict, then just rethrow.
        // 3. If the flow completes successfully then return the result.
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

        val processedResult = handleResult(result)

        return when (processedResult) {
            is FlowResult.Success<*> -> processedResult
            is FlowResult.Conflict -> {
                executeAsync(WaitForStatesToUpdate(processedResult.stateRefs, serviceHub))
                // The flow has now woken up because the conflicting reference states have been updated.
                logger.info("Starting flow again!")
                subFlow(flowLogic)
            }
        }
    }

}