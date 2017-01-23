package net.corda.node.services

import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceInfo
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.network.AbstractNetworkMapService
import net.corda.node.services.network.InMemoryNetworkMapService
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.network.PersistentNetworkMapService
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGeneratorSpi

/**
 * This class mirrors [InMemoryNetworkMapServiceTest] but switches in a [PersistentNetworkMapService] and
 * repeatedly replaces it with new instances to check that the service correctly restores the most recent state.
 */
class PersistentNetworkMapServiceTest : AbstractNetworkMapServiceTest() {
    lateinit var network: MockNetwork

    @Before
    fun setup() {
        network = MockNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    /**
     * We use a special [NetworkMapService] that allows us to switch in a new instance at any time to check that the
     * state within it is correctly restored.
     */
    private class SwizzleNetworkMapService(services: ServiceHubInternal) : NetworkMapService {
        var delegate: AbstractNetworkMapService = InMemoryNetworkMapService(services)

        override val nodes: List<NodeInfo>
            get() = delegate.nodes

        fun swizzle() {
            delegate.unregisterNetworkHandlers()
            delegate = makeNetworkMapService(delegate.services)
        }

        private fun makeNetworkMapService(services: ServiceHubInternal): AbstractNetworkMapService {
            return PersistentNetworkMapService(services)
        }
    }

    private object NodeFactory : MockNetwork.Factory {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int,
                            overrideServices: Map<ServiceInfo, KeyPair>?,
                            entropyRoot: BigInteger): MockNetwork.MockNode {
            return object : MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, id, overrideServices, entropyRoot) {

                override fun makeNetworkMapService() {
                    inNodeNetworkMapService = SwizzleNetworkMapService(services)
                }
            }
        }
    }

    /**
     * Perform basic tests of registering, de-registering and fetching the full network map.
     *
     * TODO: make the names of these and those in [AbstractNetworkMapServiceTest] and [InMemoryNetworkMapServiceTest] more
     *       meaningful.
     */
    @Test
    fun success() {
        val (mapServiceNode, registerNode) = network.createTwoNodes(NodeFactory)
        val service = mapServiceNode.inNodeNetworkMapService!! as SwizzleNetworkMapService

        databaseTransaction(mapServiceNode.database) {
            success(mapServiceNode, registerNode, { service.delegate }, { service.swizzle() })
        }
    }

    @Test
    fun `success with network`() {
        val (mapServiceNode, registerNode) = network.createTwoNodes(NodeFactory)

        // Confirm there's a network map service on node 0
        val service = mapServiceNode.inNodeNetworkMapService!! as SwizzleNetworkMapService

        databaseTransaction(mapServiceNode.database) {
            `success with network`(network, mapServiceNode, registerNode, { service.swizzle() })
        }
    }

    @Test
    fun `subscribe with network`() {
        val (mapServiceNode, registerNode) = network.createTwoNodes(NodeFactory)

        // Confirm there's a network map service on node 0
        val service = mapServiceNode.inNodeNetworkMapService!! as SwizzleNetworkMapService

        databaseTransaction(mapServiceNode.database) {
            `subscribe with network`(network, mapServiceNode, registerNode, { service.delegate }, { service.swizzle() })
        }
    }
}
