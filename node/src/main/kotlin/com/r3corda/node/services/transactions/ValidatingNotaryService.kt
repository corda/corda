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
                              timestampChecker: TimestampChecker,
                              uniquenessProvider: UniquenessProvider) : NotaryService(services, timestampChecker, uniquenessProvider) {
    object Type : ServiceType("corda.notary.validating")

    override val logger = loggerFor<ValidatingNotaryService>()

    override val protocolFactory = object : NotaryProtocol.Factory {
        override fun create(otherSide: Party,
                            timestampChecker: TimestampChecker,
                            uniquenessProvider: UniquenessProvider): NotaryProtocol.Service {
            return ValidatingNotaryProtocol(otherSide, timestampChecker, uniquenessProvider)
        }
    }
}
