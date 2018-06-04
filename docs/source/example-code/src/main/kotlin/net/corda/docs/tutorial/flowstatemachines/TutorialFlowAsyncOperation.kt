package net.corda.docs.tutorial.flowstatemachines

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.executeAsync

// DOCSTART SummingOperation
class SummingOperation(val a: Int, val b: Int) : FlowAsyncOperation<Int> {
    override fun execute(): CordaFuture<Int> {
        return doneFuture(a + b)
    }
}
// DOCEND SummingOperation

// DOCSTART SummingOperationThrowing
class SummingOperationThrowing(val a: Int, val b: Int) : FlowAsyncOperation<Int> {
    override fun execute(): CordaFuture<Int> {
        throw IllegalStateException("You shouldn't be calling me")
    }
}
// DOCEND SummingOperationThrowing

// DOCSTART ExampleSummingFlow
@StartableByRPC
class ExampleSummingFlow : FlowLogic<Int>() {
    @Suspendable
    override fun call(): Int {
        val answer = executeAsync(SummingOperation(1, 2))
        return answer // hopefully 3
    }
}
// DOCEND ExampleSummingFlow

