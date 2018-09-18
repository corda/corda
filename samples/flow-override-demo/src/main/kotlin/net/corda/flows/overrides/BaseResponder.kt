package net.corda.flows.overrides

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy


@InitiatedBy(Initiator::class)
open class BaseResponder(internal val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        otherSideSession.send(getMessage())
    }

    protected open fun getMessage() = "This Is the Legacy Responder"

}