package com.r3corda.node.services

import com.r3corda.core.messaging.Ack
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.statemachine.StateMachineManager
import protocols.AbstractStateReplacementProtocol
import protocols.NotaryChangeProtocol

/**
 * A service that monitors the network for requests for changing the notary of a state,
 * and immediately runs the [NotaryChangeProtocol] if the auto-accept criteria are met.
 */
class NotaryChangeService(net: MessagingService, val smm: StateMachineManager) : AbstractNodeService(net) {
    init {
        addMessageHandler(NotaryChangeProtocol.TOPIC_INITIATE,
                { req: AbstractStateReplacementProtocol.Handshake -> handleChangeNotaryRequest(req) }
        )
    }

    private fun handleChangeNotaryRequest(req: AbstractStateReplacementProtocol.Handshake): Ack {
        val protocol = NotaryChangeProtocol.Acceptor(
                req.replyTo as SingleMessageRecipient,
                req.sessionID!!,
                req.sessionIdForSend)
        smm.add(NotaryChangeProtocol.TOPIC_CHANGE, protocol)
        return Ack
    }
}
