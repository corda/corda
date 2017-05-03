package net.corda.node.services.transactions

import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.ServiceType
import net.corda.core.node.services.TimestampChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.flows.ValidatingNotaryFlow

/** A Notary service that validates the transaction chain of the submitted transaction before committing it */
class ValidatingNotaryService(val timestampChecker: TimestampChecker,
                              val uniquenessProvider: UniquenessProvider) : NotaryService {
    companion object {
        val type = ServiceType.notary.getSubType("validating")
    }

    override val serviceFlowFactory: (Party, Int) -> FlowLogic<Void?> = { otherParty, _ ->
        ValidatingNotaryFlow(otherParty, timestampChecker, uniquenessProvider)
    }
}
