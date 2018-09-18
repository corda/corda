package net.corda.flows.overrides

import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

@InitiatedBy(Initiator::class)
class SubResponder(otherSideSession: FlowSession) : BaseResponder(otherSideSession) {
    override fun getMessage(): String {
        return "This is the sub responder"
    }
}