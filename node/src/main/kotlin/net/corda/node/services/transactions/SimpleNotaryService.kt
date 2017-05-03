package net.corda.node.services.transactions

import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.ServiceType
import net.corda.core.node.services.TimestampChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.flows.NonValidatingNotaryFlow

/** A simple Notary service that does not perform transaction validation */
class SimpleNotaryService(val timestampChecker: TimestampChecker,
                          val uniquenessProvider: UniquenessProvider) : NotaryService {
    companion object {
        val type = ServiceType.notary.getSubType("simple")
    }

    override val serviceFlowFactory: (Party, Int) -> FlowLogic<Void?> = { otherParty, _ ->
        NonValidatingNotaryFlow(otherParty, timestampChecker, uniquenessProvider)
    }
}
