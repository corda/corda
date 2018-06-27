package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef

class WithReferencedStatesFlow<T : Any>(val stateRefs: List<StateRef>, val flowLogic: FlowLogic<T>) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

    }

}