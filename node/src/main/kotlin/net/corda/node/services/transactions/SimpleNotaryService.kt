package net.corda.node.services.transactions

import net.corda.core.crypto.Party
import net.corda.core.node.services.ServiceType
import net.corda.core.node.services.TimestampChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.flows.NotaryFlow
import net.corda.node.services.api.ServiceHubInternal

/** A simple Notary service that does not perform transaction validation */
class SimpleNotaryService(services: ServiceHubInternal,
                          val timestampChecker: TimestampChecker,
                          val uniquenessProvider: UniquenessProvider) : NotaryService(services) {
    companion object {
        val type = ServiceType.notary.getSubType("simple")
    }

    override fun createFlow(otherParty: Party.Full): NotaryFlow.Service {
        return NotaryFlow.Service(otherParty, timestampChecker, uniquenessProvider)
    }
}
