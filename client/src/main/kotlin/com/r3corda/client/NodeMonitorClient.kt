package com.r3corda.client

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.createMessage
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.node.services.monitor.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Observer

/**
 * Worked example of a client which communicates with the wallet monitor service.
 */

private val log: Logger = LoggerFactory.getLogger(NodeMonitorClient::class.java)

class NodeMonitorClient(
        val net: MessagingService,
        val node: NodeInfo,
        val outEvents: Observable<ClientToServiceCommand>,
        val inEvents: Observer<ServiceToClientEvent>,
        val snapshot: Observer<StateSnapshotMessage>
) {
    private val sessionID = random63BitValue()

    fun register(): ListenableFuture<Boolean> {

        val future = SettableFuture.create<Boolean>()
        log.info("Registering with ID $sessionID. I am ${net.myAddress}")
        net.addMessageHandler(NodeMonitorService.REGISTER_TOPIC, sessionID) { msg, reg ->
            val resp = msg.data.deserialize<RegisterResponse>()
            net.removeMessageHandler(reg)
            future.set(resp.success)
        }
        net.addMessageHandler(NodeMonitorService.STATE_TOPIC, sessionID) { msg, reg ->
            val snapshotMessage = msg.data.deserialize<StateSnapshotMessage>()
            net.removeMessageHandler(reg)
            snapshot.onNext(snapshotMessage)
        }

        net.addMessageHandler(NodeMonitorService.IN_EVENT_TOPIC, sessionID) { msg, reg ->
            val event = msg.data.deserialize<ServiceToClientEvent>()
            inEvents.onNext(event)
        }

        val req = RegisterRequest(net.myAddress, sessionID)
        val registerMessage = net.createMessage(NodeMonitorService.REGISTER_TOPIC, 0, req.serialize().bits)
        net.send(registerMessage, node.address)

        outEvents.subscribe { event ->
            val envelope = ClientToServiceCommandMessage(sessionID, net.myAddress, event)
            val message = net.createMessage(NodeMonitorService.OUT_EVENT_TOPIC, 0, envelope.serialize().bits)
            net.send(message, node.address)
        }

        return future
    }
}
