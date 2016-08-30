package com.r3corda.client.model

import com.r3corda.client.WalletMonitorClient
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.NodeInfo
import com.r3corda.node.services.monitor.ServiceToClientEvent
import rx.Observable
import rx.Observer
import rx.subjects.PublishSubject

/**
 * This model exposes raw event streams to and from the [WalletMonitorService] through a [WalletMonitorClient]
 */
class WalletMonitorModel {
    private val clientToServiceSource = PublishSubject.create<ClientToServiceCommand>()
    val clientToService: Observer<ClientToServiceCommand> = clientToServiceSource

    private val serviceToClientSource = PublishSubject.create<ServiceToClientEvent>()
    val serviceToClient: Observable<ServiceToClientEvent> = serviceToClientSource

    /**
     * Register for updates to/from a given wallet.
     * @param messagingService The messaging to use for communication.
     * @param walletMonitorNodeInfo the [Node] to connect to.
     * TODO provide an unsubscribe mechanism
     */
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
