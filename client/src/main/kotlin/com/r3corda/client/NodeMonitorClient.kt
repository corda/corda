package com.r3corda.client

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.map
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.createMessage
import com.r3corda.core.messaging.onNext
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.success
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.services.monitor.*
import com.r3corda.node.services.monitor.NodeMonitorService.Companion.IN_EVENT_TOPIC
import com.r3corda.node.services.monitor.NodeMonitorService.Companion.OUT_EVENT_TOPIC
import com.r3corda.node.services.monitor.NodeMonitorService.Companion.REGISTER_TOPIC
import com.r3corda.node.services.monitor.NodeMonitorService.Companion.STATE_TOPIC
import rx.Observable
import rx.Observer

/**
 * Worked example of a client which communicates with the wallet monitor service.
 */
class NodeMonitorClient(
        val net: MessagingService,
        val node: NodeInfo,
        val outEvents: Observable<ClientToServiceCommand>,
        val inEvents: Observer<ServiceToClientEvent>,
        val snapshot: Observer<StateSnapshotMessage>
) {

    companion object {
        private val log = loggerFor<NodeMonitorClient>()
    }

    fun register(): ListenableFuture<Boolean> {
        val sessionID = random63BitValue()

        log.info("Registering with ID $sessionID. I am ${net.myAddress}")
        val future = net.onNext<RegisterResponse>(REGISTER_TOPIC, sessionID).map { it.success }

        net.onNext<StateSnapshotMessage>(STATE_TOPIC, sessionID).success { snapshot.onNext(it) }

        net.addMessageHandler(IN_EVENT_TOPIC, sessionID) { msg, reg ->
            val event = msg.data.deserialize<ServiceToClientEvent>()
            inEvents.onNext(event)
        }

        val req = RegisterRequest(net.myAddress, sessionID)
        val registerMessage = net.createMessage(REGISTER_TOPIC, 0, req.serialize().bits)
        net.send(registerMessage, node.address)

        outEvents.subscribe { event ->
            val envelope = ClientToServiceCommandMessage(sessionID, net.myAddress, event)
            val message = net.createMessage(OUT_EVENT_TOPIC, 0, envelope.serialize().bits)
            net.send(message, node.address)
        }

        return future
    }
}
