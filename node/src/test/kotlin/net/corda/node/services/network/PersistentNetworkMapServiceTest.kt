package net.corda.node.services.network

import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.messaging.MessagingService
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.node.MockNodeArgs

/**
 * This class mirrors [InMemoryNetworkMapServiceTest] but switches in a [PersistentNetworkMapService] and
 * repeatedly replaces it with new instances to check that the service correctly restores the most recent state.
 */
class PersistentNetworkMapServiceTest : AbstractNetworkMapServiceTest<PersistentNetworkMapService>() {

    override val nodeFactory: MockNetwork.Factory<*> get() = NodeFactory

    override val networkMapService: PersistentNetworkMapService
        get() = (mapServiceNode.inNodeNetworkMapService as SwizzleNetworkMapService).delegate

    override fun swizzle() {
        mapServiceNode.database.transaction {
            (mapServiceNode.inNodeNetworkMapService as SwizzleNetworkMapService).swizzle()
        }
    }

    private object NodeFactory : MockNetwork.Factory<MockNode> {
        override fun create(args: MockNodeArgs): MockNode {
            return object : MockNode(args) {
                override fun makeNetworkMapService(network: MessagingService, networkMapCache: NetworkMapCacheInternal) = SwizzleNetworkMapService(network, networkMapCache)
            }
        }
    }

    /**
     * We use a special [NetworkMapService] that allows us to switch in a new instance at any time to check that the
     * state within it is correctly restored.
     */
    private class SwizzleNetworkMapService(private val delegateFactory: () -> PersistentNetworkMapService) : NetworkMapService {
        constructor(network: MessagingService, networkMapCache: NetworkMapCacheInternal) : this({ PersistentNetworkMapService(network, networkMapCache, 1) })

        var delegate = delegateFactory()
        fun swizzle() {
            delegate.unregisterNetworkHandlers()
            delegate = delegateFactory()
        }
    }
}
