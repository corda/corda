package net.corda.flows.overrides

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

@InitiatedBy(Initiator::class)
class OverridenResponder(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        otherSideSession.send("This is a totally different class hierarchy")
    }
}