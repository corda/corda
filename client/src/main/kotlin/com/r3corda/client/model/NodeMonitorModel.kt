package com.r3corda.client.model

import com.r3corda.client.NodeMonitorClient
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.NodeInfo
import com.r3corda.node.services.monitor.ServiceToClientEvent
import com.r3corda.node.services.monitor.StateSnapshotMessage
import rx.Observable
import rx.Observer
import rx.subjects.PublishSubject

/**
 * This model exposes raw event streams to and from the [NodeMonitorService] through a [NodeMonitorClient]
 */
class NodeMonitorModel {
    private val clientToServiceSource = PublishSubject.create<ClientToServiceCommand>()
    val clientToService: Observer<ClientToServiceCommand> = clientToServiceSource

    private val serviceToClientSource = PublishSubject.create<ServiceToClientEvent>()
    val serviceToClient: Observable<ServiceToClientEvent> = serviceToClientSource

    private val snapshotSource = PublishSubject.create<StateSnapshotMessage>()
    val snapshot: Observable<StateSnapshotMessage> = snapshotSource

    /**
     * Register for updates to/from a given wallet.
     * @param messagingService The messaging to use for communication.
     * @param monitorNodeInfo the [Node] to connect to.
     * TODO provide an unsubscribe mechanism
     */
    fun register(messagingService: MessagingService, monitorNodeInfo: NodeInfo) {
        val monitorClient = NodeMonitorClient(
                messagingService,
                monitorNodeInfo,
                clientToServiceSource,
                serviceToClientSource,
                snapshotSource
        )
        require(monitorClient.register().get())
    }
}
