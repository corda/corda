package net.corda.docs.kotlin.tutorial.flowstatemachines

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowExternalAsyncOperation
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import java.util.concurrent.CompletableFuture

class SummingOperation(val a: Int, val b: Int) : FlowExternalAsyncOperation<Int> {
    override fun execute(deduplicationId: String): CompletableFuture<Int> {
        return CompletableFuture.completedFuture(a + b)
    }
}

// DOCSTART SummingOperationThrowing
class SummingOperationThrowing(val a: Int, val b: Int) : FlowExternalAsyncOperation<Int> {
    override fun execute(deduplicationId: String): CompletableFuture<Int> {
        throw IllegalStateException("You shouldn't be calling me")
    }
}
// DOCEND SummingOperationThrowing

@StartableByRPC
class ExampleSummingFlow : FlowLogic<Int>() {
    @Suspendable
    override fun call(): Int {
        val answer = await(SummingOperation(1, 2))
        return answer // hopefully 3
    }
}

