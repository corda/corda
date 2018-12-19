package net.corda.finance.flows.test

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.finance.flows.CashException

@StartableByRPC
class CashExceptionThrowingFlow : FlowLogic<Unit>() {
    override fun call() {
        throw CashException("BOOM!", IllegalStateException("Nope dude!"))
    }
}
