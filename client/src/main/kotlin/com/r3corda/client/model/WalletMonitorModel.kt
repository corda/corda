package com.r3corda.client.model

import com.r3corda.client.WalletMonitorClient
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.NodeInfo
import com.r3corda.node.services.monitor.ServiceToClientEvent
import javafx.beans.property.SimpleObjectProperty
import org.reactfx.EventSink
import org.reactfx.EventSource
import org.reactfx.EventStream
import java.util.*

class WalletMonitorModel {
    private val clientToServiceSource = EventSource<ClientToServiceCommand>()
    val clientToService: EventSink<ClientToServiceCommand> = clientToServiceSource

    private val serviceToClientSource = EventSource<ServiceToClientEvent>()
    val serviceToClient: EventStream<ServiceToClientEvent> = serviceToClientSource

    // TODO provide an unsubscribe mechanism
    fun register(messagingService: MessagingService, walletMonitorNodeInfo: NodeInfo) {
        val monitorClient = WalletMonitorClient(
                messagingService,
                walletMonitorNodeInfo,
                clientToServiceSource,
                serviceToClientSource
        )
        require(monitorClient.register().get())
    }
}
