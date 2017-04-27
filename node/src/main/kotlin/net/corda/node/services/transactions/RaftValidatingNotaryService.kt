package net.corda.node.services.transactions

import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.TimestampChecker
import net.corda.flows.ValidatingNotaryFlow

/** A validating notary service operated by a group of mutually trusting parties, uses the Raft algorithm to achieve consensus. */
class RaftValidatingNotaryService(val timestampChecker: TimestampChecker,
                                  val uniquenessProvider: RaftUniquenessProvider) : NotaryService {
    companion object {
        val type = ValidatingNotaryService.type.getSubType("raft")
    }

    override val serviceFlowFactory: (Party, Int) -> FlowLogic<Void?> = { otherParty, _ ->
        ValidatingNotaryFlow(otherParty, timestampChecker, uniquenessProvider)
    }
}
