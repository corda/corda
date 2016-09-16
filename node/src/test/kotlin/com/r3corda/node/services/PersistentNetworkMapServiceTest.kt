package com.r3corda.node.services

import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.network.AbstractNetworkMapService
import com.r3corda.node.services.network.InMemoryNetworkMapService
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.network.PersistentNetworkMapService
import com.r3corda.node.utilities.configureDatabase
import com.r3corda.node.utilities.databaseTransaction
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.makeTestDataSourceProperties
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.nio.file.Path
import java.security.KeyPair

/**
 * This class mirrors [InMemoryNetworkMapServiceTest] but switches in a [PersistentNetworkMapService] and
 * repeatedly replaces it with new instances to check that the service correctly restores the most recent state.
 */
class PersistentNetworkMapServiceTest : AbstractNetworkMapServiceTest() {
    lateinit var network: MockNetwork
    lateinit var dataSource: Closeable

    @Before
    fun setup() {
        network = MockNetwork()
    }

    @After
    fun tearDown() {
        dataSource.close()
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
            delegate=makeNetworkMapService(delegate.services)
        }

        private fun makeNetworkMapService(services: ServiceHubInternal): AbstractNetworkMapService {
            return PersistentNetworkMapService(services)
        }
    }

    private object NodeFactory : MockNetwork.Factory {
        override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            return object : MockNetwork.MockNode(dir, config, network, networkMapAddr, advertisedServices, id, keyPair) {

                override fun makeNetworkMapService() {
                    inNodeNetworkMapService = SwizzleNetworkMapService(services)
                }
            }
        }
    }

    /**
     * Perform basic tests of registering, de-registering and fetching the full network map.
     */
    @Test
    fun success() {
        val (mapServiceNode, registerNode) = network.createTwoNodes(NodeFactory)
        val service = mapServiceNode.inNodeNetworkMapService!! as SwizzleNetworkMapService

        // We have to set this up after the non-persistent nodes as they install a dummy transaction manager.
        dataSource = configureDatabase(makeTestDataSourceProperties()).first

        databaseTransaction {
            success(mapServiceNode, registerNode, { service.delegate }, {service.swizzle()})
        }
    }

    @Test
    fun `success with network`() {
        val (mapServiceNode, registerNode) = network.createTwoNodes(NodeFactory)

        // Confirm there's a network map service on node 0
        val service = mapServiceNode.inNodeNetworkMapService!! as SwizzleNetworkMapService

        // We have to set this up after the non-persistent nodes as they install a dummy transaction manager.
        dataSource = configureDatabase(makeTestDataSourceProperties()).first

        databaseTransaction {
            `success with network`(network, mapServiceNode, registerNode, { service.swizzle() })
        }
    }

    @Test
    fun `subscribe with network`() {
        val (mapServiceNode, registerNode) = network.createTwoNodes(NodeFactory)

        // Confirm there's a network map service on node 0
        val service = mapServiceNode.inNodeNetworkMapService!! as SwizzleNetworkMapService

        // We have to set this up after the non-persistent nodes as they install a dummy transaction manager.
        dataSource = configureDatabase(makeTestDataSourceProperties()).first

        databaseTransaction {
            `subscribe with network`(network, mapServiceNode, registerNode, { service.delegate }, { service.swizzle() })
        }
    }
}
