package com.r3corda.demos

import com.google.common.net.HostAndPort
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.node.internal.Node
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.network.InMemoryMessagingNetwork
import java.nio.file.Path
import java.time.Clock

val messageNetwork = InMemoryMessagingNetwork()

class DemoNode(messagingService: MessagingService, dir: Path, p2pAddr: HostAndPort, config: NodeConfiguration,
               networkMapAddress: NodeInfo?, advertisedServices: Set<ServiceType>,
               clock: Clock = NodeClock(), clientAPIs: List<Class<*>> = listOf())
: Node(dir, p2pAddr, config, networkMapAddress, advertisedServices, clock, clientAPIs) {

    val messagingService = messagingService
    override fun makeMessagingService(): MessagingService {
        return messagingService
    }

    override fun startMessagingService() = Unit
}