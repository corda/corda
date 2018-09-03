package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

@StartableByRPC
class EmptyFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
    }
}