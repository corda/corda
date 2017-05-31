package net.corda.node.services.transactions

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.TimeWindowChecker

/** A non-validating notary service operated by a group of mutually trusting parties, uses the Raft algorithm to achieve consensus. */
class RaftNonValidatingNotaryService(val timeWindowChecker: TimeWindowChecker,
                                     val uniquenessProvider: RaftUniquenessProvider) : NotaryService {
    companion object {
        val type = SimpleNotaryService.type.getSubType("raft")
    }

    override val serviceFlowFactory: (PartyAndCertificate, Int) -> FlowLogic<Void?> = { otherParty, _ ->
        NonValidatingNotaryFlow(otherParty, timeWindowChecker, uniquenessProvider)
    }
}
