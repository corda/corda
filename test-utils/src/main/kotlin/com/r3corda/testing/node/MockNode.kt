package com.r3corda.testing.node

import com.google.common.jimfs.Jimfs
import com.google.common.util.concurrent.Futures
import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.PhysicalLocation
import com.r3corda.core.node.services.KeyManagementService
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.node.services.WalletService
import com.r3corda.core.testing.InMemoryWalletService
import com.r3corda.core.utilities.DUMMY_NOTARY_KEY
import com.r3corda.core.utilities.loggerFor
import com.r3corda.testing.node.TestTransactionManager
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.keys.E2ETestKeyManagementService
import com.r3corda.node.services.network.InMemoryNetworkMapService
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.network.NodeRegistration
import com.r3corda.node.utilities.AddOrRemove
import com.r3corda.node.services.transactions.InMemoryUniquenessProvider
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.util.*

/**
 * A mock node brings up a suite of in-memory services in a fast manner suitable for unit testing.
 * Components that do IO are either swapped out for mocks, or pointed to a [Jimfs] in memory filesystem.
 *
 * Mock network nodes require manual pumping by default: they will not run asynchronous. This means that
 * for message exchanges to take place (and associated handlers to run), you must call the [runNetwork]
 * method.
 *
 * You can get a printout of every message sent by using code like:
 *
 *    LogHelper.setLevel("+messages")
 */
class MockNetwork(private val networkSendManuallyPumped: Boolean = false,
                  private val threadPerNode: Boolean = false,
                  private val defaultFactory: Factory = MockNetwork.DefaultFactory) {
    private var counter = 0
    val filesystem = com.google.common.jimfs.Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix())
    val messagingNetwork = InMemoryMessagingNetwork(networkSendManuallyPumped)

    val identities = ArrayList<Party>()

    private val _nodes = ArrayList<MockNode>()
    /** A read only view of the current set of executing nodes. */
    val nodes: List<MockNode> = _nodes

    init {
        Files.createDirectory(filesystem.getPath("/nodes"))
    }

    /** Allows customisation of how nodes are created. */
    interface Factory {
        fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                   advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNode
    }

    object DefaultFactory : Factory {
        override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNode {
            return MockNode(dir, config, network, networkMapAddr, advertisedServices, id, keyPair)
        }
    }

    open class MockNode(dir: Path, config: NodeConfiguration, val mockNet: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                        advertisedServices: Set<ServiceType>, val id: Int, val keyPair: KeyPair?) : com.r3corda.node.internal.AbstractNode(dir, config, networkMapAddr, advertisedServices, TestClock()) {
        override val log: Logger = loggerFor<MockNode>()
        override val serverThread: com.r3corda.node.utilities.AffinityExecutor =
                if (mockNet.threadPerNode)
                    com.r3corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor("Mock node thread", 1)
                else
                    com.r3corda.node.utilities.AffinityExecutor.Companion.SAME_THREAD

        // We only need to override the messaging service here, as currently everything that hits disk does so
        // through the java.nio API which we are already mocking via Jimfs.

        override fun makeMessagingService(): com.r3corda.node.services.api.MessagingServiceInternal {
            require(id >= 0) { "Node ID must be zero or positive, was passed: " + id }
            return mockNet.messagingNetwork.createNodeWithID(!mockNet.threadPerNode, id, configuration.myLegalName).start().get()
        }

        override fun makeIdentityService() = MockIdentityService(mockNet.identities)

        override fun makeWalletService(): WalletService = InMemoryWalletService(services)

        override fun makeKeyManagementService(): KeyManagementService = E2ETestKeyManagementService(setOf(storage.myLegalIdentityKey))

        override fun startMessagingService() {
            // Nothing to do
        }

        // If the in-memory H2 instance is configured, use that, otherwise mock out the transaction manager.
        override fun initialiseDatabasePersistence(insideTransaction: () -> Unit) {
            try {
                super.initialiseDatabasePersistence(insideTransaction)
            } catch(fallback: DatabaseConfigurationException) {
                log.info("Using mocked database features.")
                TransactionManager.manager = TestTransactionManager()
                insideTransaction()
            }
        }

        override fun makeNetworkMapService() {
            val expires = platformClock.instant() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
            val reg = NodeRegistration(info, Long.MAX_VALUE, AddOrRemove.ADD, expires)
            inNodeNetworkMapService = InMemoryNetworkMapService(net, reg, services.networkMapCache)
        }

        override fun generateKeyPair(): KeyPair = keyPair ?: super.generateKeyPair()

        // It's OK to not have a network map service in the mock network.
        override fun noNetworkMapConfigured() = Futures.immediateFuture(Unit)

        // There is no need to slow down the unit tests by initialising CityDatabase
        override fun findMyLocation(): PhysicalLocation? = null

        override fun makeUniquenessProvider() = InMemoryUniquenessProvider()

        override fun start(): MockNode {
            super.start()
            mockNet.identities.add(storage.myLegalIdentity)
            return this
        }

        // This does not indirect through the NodeInfo object so it can be called before the node is started.
        // It is used from the network visualiser tool.
        @Suppress("unused") val place: PhysicalLocation get() = findMyLocation()!!
    }

    /** Returns a node, optionally created by the passed factory method. */
    fun createNode(networkMapAddress: SingleMessageRecipient? = null, forcedID: Int = -1, nodeFactory: Factory = defaultFactory,
                   start: Boolean = true, legalName: String? = null, keyPair: KeyPair? = null,
                   databasePersistence: Boolean = false, vararg advertisedServices: ServiceType): MockNode {
        val newNode = forcedID == -1
        val id = if (newNode) counter++ else forcedID

        val path = filesystem.getPath("/nodes/$id")
        if (newNode)
            Files.createDirectories(path.resolve("attachments"))
        val config = object : NodeConfiguration {
            override val myLegalName: String = legalName ?: "Mock Company $id"
            override val exportJMXto: String = ""
            override val nearestCity: String = "Atlantis"
            override val keyStorePassword: String = "dummy"
            override val trustStorePassword: String = "trustpass"
            override val dataSourceProperties: Properties get() = if (databasePersistence) makeTestDataSourceProperties("node_$id") else Properties()
        }
        val node = nodeFactory.create(path, config, this, networkMapAddress, advertisedServices.toSet(), id, keyPair)
        if (start) {
            node.setup().start()
            if (threadPerNode && networkMapAddress != null)
                node.networkMapRegistrationFuture.get()   // Block and wait for the node to register in the net map.
        }
        _nodes.add(node)
        return node
    }

    /**
     * Asks every node in order to process any queued up inbound messages. This may in turn result in nodes
     * sending more messages to each other, thus, a typical usage is to call runNetwork with the [rounds]
     * parameter set to -1 (the default) which simply runs as many rounds as necessary to result in network
     * stability (no nodes sent any messages in the last round).
     */
    fun runNetwork(rounds: Int = -1) {
        check(!networkSendManuallyPumped)
        fun pumpAll() = messagingNetwork.endpoints.map { it.pumpReceive(false) }

        if (rounds == -1) {
            while (pumpAll().any { it != null }) {
            }
        } else {
            repeat(rounds) {
                pumpAll()
            }
        }
    }

    // TODO: Move this to using createSomeNodes which doesn't conflate network services with network users.
    /**
     * Sets up a two node network, in which the first node runs network map and notary services and the other
     * doesn't.
     */
    fun createTwoNodes(nodeFactory: Factory = defaultFactory, notaryKeyPair: KeyPair? = null): Pair<MockNode, MockNode> {
        require(nodes.isEmpty())
        return Pair(
                createNode(null, -1, nodeFactory, true, null, notaryKeyPair, false, com.r3corda.node.services.network.NetworkMapService.Type, com.r3corda.node.services.transactions.SimpleNotaryService.Type),
                createNode(nodes[0].info.address, -1, nodeFactory, true, null)
        )
    }

    /**
     * A bundle that separates the generic user nodes and service-providing nodes. A real network might not be so
     * clearly separated, but this is convenient for testing.
     */
    data class BasketOfNodes(val partyNodes: List<MockNode>, val notaryNode: MockNode, val mapNode: MockNode)

    /**
     * Sets up a network with the requested number of nodes (defaulting to two), with one or more service nodes that
     * run a notary, network map, any oracles etc. Can't be combined with [createTwoNodes].
     */
    fun createSomeNodes(numPartyNodes: Int = 2, nodeFactory: Factory = defaultFactory, notaryKeyPair: KeyPair? = DUMMY_NOTARY_KEY): BasketOfNodes {
        require(nodes.isEmpty())
        val mapNode = createNode(null, nodeFactory = nodeFactory, advertisedServices = com.r3corda.node.services.network.NetworkMapService.Type)
        val notaryNode = createNode(mapNode.info.address, nodeFactory = nodeFactory, keyPair = notaryKeyPair,
                advertisedServices = com.r3corda.node.services.transactions.SimpleNotaryService.Type)
        val nodes = ArrayList<MockNode>()
        repeat(numPartyNodes) {
            nodes += createPartyNode(mapNode.info.address)
        }
        return BasketOfNodes(nodes, notaryNode, mapNode)
    }

    fun createNotaryNode(legalName: String? = null, keyPair: KeyPair? = null): MockNode {
        return createNode(null, -1, defaultFactory, true, legalName, keyPair, false, com.r3corda.node.services.network.NetworkMapService.Type, com.r3corda.node.services.transactions.SimpleNotaryService.Type)
    }

    fun createPartyNode(networkMapAddr: SingleMessageRecipient, legalName: String? = null, keyPair: KeyPair? = null): MockNode {
        return createNode(networkMapAddr, -1, defaultFactory, true, legalName, keyPair)
    }

    @Suppress("unused")  // This is used from the network visualiser tool.
    fun addressToNode(address: SingleMessageRecipient): MockNode = nodes.single { it.net.myAddress == address }

    fun startNodes() {
        require(nodes.isNotEmpty())
        nodes.forEach { if (!it.started) it.start() }
    }

    fun stopNodes() {
        require(nodes.isNotEmpty())
        nodes.forEach { if (it.started) it.stop() }
    }
}
