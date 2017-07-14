package net.corda.node.services.network

import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import java.math.BigInteger
import java.security.KeyPair

/**
 * This class mirrors [InMemoryNetworkMapServiceTest] but switches in a [PersistentNetworkMapService] and
 * repeatedly replaces it with new instances to check that the service correctly restores the most recent state.
 */
class PersistentNetworkMapServiceTest : AbstractNetworkMapServiceTest<PersistentNetworkMapService>() {

    override val nodeFactory: MockNetwork.Factory get() = NodeFactory

    override val networkMapService: PersistentNetworkMapService
        get() = (mapServiceNode.inNodeNetworkMapService as SwizzleNetworkMapService).delegate

    override fun swizzle() {
        mapServiceNode.database.transaction {
            (mapServiceNode.inNodeNetworkMapService as SwizzleNetworkMapService).swizzle()
        }
    }

    private object NodeFactory : MockNetwork.Factory {
        override fun create(config: NodeConfiguration,
                            network: MockNetwork,
                            networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>,
                            id: Int,
                            overrideServices: Map<ServiceInfo, KeyPair>?,
                            entropyRoot: BigInteger): MockNode {
            return object : MockNode(config, network, networkMapAddr, advertisedServices, id, overrideServices, entropyRoot) {
                override fun makeNetworkMapService() {
                    inNodeNetworkMapService = SwizzleNetworkMapService(services)
                }
            }
        }
    }

    /**
     * We use a special [NetworkMapService] that allows us to switch in a new instance at any time to check that the
     * state within it is correctly restored.
     */
    private class SwizzleNetworkMapService(val services: ServiceHubInternal) : NetworkMapService {
        var delegate: PersistentNetworkMapService = PersistentNetworkMapService(services, 1)

        fun swizzle() {
            delegate.unregisterNetworkHandlers()
            delegate = PersistentNetworkMapService(services, 1)
        }
    }
}
