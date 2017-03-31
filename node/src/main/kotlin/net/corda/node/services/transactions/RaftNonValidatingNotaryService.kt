package net.corda.node.services.transactions

import net.corda.core.crypto.Party
import net.corda.core.node.services.TimestampChecker
import net.corda.flows.NonValidatingNotaryFlow
import net.corda.node.services.api.ServiceHubInternal

/** A non-validating notary service operated by a group of mutually trusting parties, uses the Raft algorithm to achieve consensus. */
class RaftNonValidatingNotaryService(services: ServiceHubInternal,
                                     val timestampChecker: TimestampChecker,
                                     val uniquenessProvider: RaftUniquenessProvider) : NotaryService(services) {
    companion object {
        val type = SimpleNotaryService.type.getSubType("raft")
    }

    override fun createFlow(otherParty: Party): NonValidatingNotaryFlow {
        return NonValidatingNotaryFlow(otherParty, timestampChecker, uniquenessProvider)
    }
}
