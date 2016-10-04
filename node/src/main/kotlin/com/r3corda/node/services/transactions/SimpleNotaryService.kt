package com.r3corda.node.services.transactions

import com.r3corda.core.crypto.Party
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.node.services.TimestampChecker
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.NotaryProtocol

/** A simple Notary service that does not perform transaction validation */
class SimpleNotaryService(services: ServiceHubInternal,
                          val timestampChecker: TimestampChecker,
                          val uniquenessProvider: UniquenessProvider) : NotaryService(services) {
    object Type : ServiceType("corda.notary.simple")

    override fun createProtocol(otherParty: Party): NotaryProtocol.Service {
        return NotaryProtocol.Service(otherParty, timestampChecker, uniquenessProvider)
    }
}
