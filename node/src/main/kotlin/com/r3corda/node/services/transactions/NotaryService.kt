package com.r3corda.node.services.transactions

import com.r3corda.core.messaging.Ack
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.node.services.TimestampChecker
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.protocols.NotaryProtocol

/**
 * A Notary service acts as the final signer of a transaction ensuring two things:
 * - The (optional) timestamp of the transaction is valid
 * - None of the referenced input states have previously been consumed by a transaction signed by this Notary
 *
 * A transaction has to be signed by a Notary to be considered valid (except for output-only transactions without a timestamp).
 *
 * This is the base implementation that can be customised with specific Notary transaction commit protocol
 */
abstract class NotaryService(val smm: StateMachineManager,
                             net: MessagingService,
                             val timestampChecker: TimestampChecker,
                             val uniquenessProvider: UniquenessProvider) : AbstractNodeService(net) {
    object Type : ServiceType("corda.notary")

    abstract val logger: org.slf4j.Logger

    /** Implement a factory that specifies the transaction commit protocol for the notary service to use */
    abstract val protocolFactory: NotaryProtocol.Factory

    init {
        addMessageHandler(NotaryProtocol.TOPIC_INITIATE,
                { req: NotaryProtocol.Handshake -> processRequest(req) }
        )
    }

    private fun processRequest(req: NotaryProtocol.Handshake): Ack {
        val protocol = protocolFactory.create(req.replyTo as SingleMessageRecipient,
                req.sessionID!!,
                req.sendSessionID,
                timestampChecker,
                uniquenessProvider)
        smm.add(NotaryProtocol.TOPIC, protocol)
        return Ack
    }
}
