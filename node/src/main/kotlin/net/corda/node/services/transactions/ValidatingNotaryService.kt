package net.corda.node.services.transactions

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.ServiceType
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.UniquenessProvider

/** A Notary service that validates the transaction chain of the submitted transaction before committing it */
class ValidatingNotaryService(val timeWindowChecker: TimeWindowChecker,
                              val uniquenessProvider: UniquenessProvider) : NotaryService {
    companion object {
        val type = ServiceType.notary.getSubType("validating")
    }

    override val serviceFlowFactory: (Party, Int) -> FlowLogic<Void?> = { otherParty, _ ->
        ValidatingNotaryFlow(otherParty, timeWindowChecker, uniquenessProvider)
    }
}
