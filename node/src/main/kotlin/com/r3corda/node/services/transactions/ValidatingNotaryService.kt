package com.r3corda.node.services.transactions

import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.services.NetworkMapCache
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.node.services.TimestampChecker
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.protocols.NotaryProtocol
import com.r3corda.protocols.ValidatingNotaryProtocol

/** A Notary service that validates the transaction chain of he submitted transaction before committing it */
class ValidatingNotaryService(
        smm: StateMachineManager,
        net: MessagingService,
        timestampChecker: TimestampChecker,
        uniquenessProvider: UniquenessProvider,
        networkMapCache: NetworkMapCache
) : NotaryService(smm, net, timestampChecker, uniquenessProvider, networkMapCache) {
    object Type : ServiceType("corda.notary.validating")

    override val logger = loggerFor<ValidatingNotaryService>()

    override val protocolFactory = object : NotaryProtocol.Factory {
        override fun create(otherSide: Party,
                            sendSessionID: Long,
                            receiveSessionID: Long,
                            timestampChecker: TimestampChecker,
                            uniquenessProvider: UniquenessProvider): NotaryProtocol.Service {
            return ValidatingNotaryProtocol(otherSide, sendSessionID, receiveSessionID, timestampChecker, uniquenessProvider)
        }
    }
}
