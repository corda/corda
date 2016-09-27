package com.r3corda.node.services.transactions

import com.r3corda.core.crypto.Party
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.node.services.TimestampChecker
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.NotaryProtocol
import com.r3corda.protocols.ValidatingNotaryProtocol

/** A Notary service that validates the transaction chain of he submitted transaction before committing it */
class ValidatingNotaryService(services: ServiceHubInternal,
                              val timestampChecker: TimestampChecker,
                              val uniquenessProvider: UniquenessProvider) : NotaryService(NotaryProtocol.ValidatingClient::class, services) {
    object Type : ServiceType("corda.notary.validating")

    override val logger = loggerFor<ValidatingNotaryService>()

    override fun createProtocol(otherParty: Party): ValidatingNotaryProtocol {
        return ValidatingNotaryProtocol(otherParty, timestampChecker, uniquenessProvider)
    }
}
