package net.corda.node.services.transactions

import net.corda.core.crypto.Party
import net.corda.core.node.services.ServiceType
import net.corda.core.node.services.TimestampChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.utilities.loggerFor
import net.corda.node.services.api.ServiceHubInternal
import net.corda.protocols.NotaryProtocol

/** A simple Notary service that does not perform transaction validation */
class SimpleNotaryService(services: ServiceHubInternal,
                          val timestampChecker: TimestampChecker,
                          val uniquenessProvider: UniquenessProvider) : NotaryService(services) {
    companion object {
        val type = ServiceType.notary.getSubType("simple")
    }

    override fun createProtocol(otherParty: Party): NotaryProtocol.Service {
        return NotaryProtocol.Service(otherParty, timestampChecker, uniquenessProvider)
    }
}
