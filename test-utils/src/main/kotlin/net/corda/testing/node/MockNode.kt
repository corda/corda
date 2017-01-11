package net.corda.testing.node

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.*
import net.corda.core.crypto.Party
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PhysicalLocation
import net.corda.core.node.services.*
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.AbstractNode
import net.corda.node.services.api.MessagingServiceInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.node.services.network.InMemoryNetworkMapService
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.InMemoryUniquenessProvider
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.testing.TestNodeConfiguration
import org.apache.activemq.artemis.utils.ReusableLatch
import org.slf4j.Logger
import java.nio.file.FileSystem
import java.security.KeyPair
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A mock node brings up a suite of in-memory services in a fast manner suitable for unit testing.
 * Components that do IO are either swapped out for mocks, or pointed to a [Jimfs] in memory filesystem or an in
 * memory H2 database instance.
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
                  servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy =
                      InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
                  private val defaultFactory: Factory = MockNetwork.DefaultFactory) {
    private var nextNodeId = 0
    val filesystem: FileSystem = Jimfs.newFileSystem(unix())
    private val busyLatch: ReusableLatch = ReusableLatch()
    val messagingNetwork = InMemoryMessagingNetwork(networkSendManuallyPumped, servicePeerAllocationStrategy, busyLatch)

    // A unique identifier for this network to segregate databases with the same nodeID but different networks.
    private val networkId = random63BitValue()

    val identities = ArrayList<Party>()

    private val _nodes = ArrayList<MockNode>()
    /** A read only view of the current set of executing nodes. */
    val nodes: List<MockNode> = _nodes

    init {
        filesystem.getPath("/nodes").createDirectory()
    }

    /** Allows customisation of how nodes are created. */
    interface Factory {
        fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                   advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?): MockNode
    }

    object DefaultFactory : Factory {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?): MockNode {
            return MockNode(config, network, networkMapAddr, advertisedServices, id, keyPair)
        }
    }

    /**
     * Because this executor is shared, we need to be careful about nodes shutting it down.
     */
    private val sharedUserCount = AtomicInteger(0)
    private val sharedServerThread =
            object : ServiceAffinityExecutor("Mock network shared node thread", 1) {

                override fun shutdown() {
                    // We don't actually allow the shutdown of the network-wide shared thread pool until all references to
                    // it have been shutdown.
                    if (sharedUserCount.decrementAndGet() == 0) {
                        super.shutdown()
                    }
                }

                override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
                    if (!isShutdown) {
                        flush()
                        return true
                    } else {
                        return super.awaitTermination(timeout, unit)
                    }
                }
            }

    open class MockNode(config: NodeConfiguration,
                        val mockNet: MockNetwork,
                        override val networkMapAddress: SingleMessageRecipient?,
                        advertisedServices: Set<ServiceInfo>,
                        val id: Int,
                        val keyPair: KeyPair?) : AbstractNode(config, advertisedServices, TestClock(), mockNet.busyLatch) {
        override val log: Logger = loggerFor<MockNode>()
        override val serverThread: AffinityExecutor =
                if (mockNet.threadPerNode)
                    ServiceAffinityExecutor("Mock node $id thread", 1)
                else {
                    mockNet.sharedUserCount.incrementAndGet()
                    mockNet.sharedServerThread
                }

        // We only need to override the messaging service here, as currently everything that hits disk does so
        // through the java.nio API which we are already mocking via Jimfs.
        override fun makeMessagingService(): MessagingServiceInternal {
            require(id >= 0) { "Node ID must be zero or positive, was passed: " + id }
            return mockNet.messagingNetwork.createNodeWithID(!mockNet.threadPerNode, id, serverThread, makeServiceEntries(), configuration.myLegalName, database).start().getOrThrow()
        }

        override fun makeIdentityService() = MockIdentityService(mockNet.identities)

        override fun makeVaultService(): VaultService = NodeVaultService(services)

        override fun makeKeyManagementService(): KeyManagementService = E2ETestKeyManagementService(partyKeys)

        override fun startMessagingService(rpcOps: RPCOps) {
            // Nothing to do
        }

        override fun makeNetworkMapService() {
            inNodeNetworkMapService = InMemoryNetworkMapService(services)
        }

        override fun generateKeyPair(): KeyPair = keyPair ?: super.generateKeyPair()

        // It's OK to not have a network map service in the mock network.
        override fun noNetworkMapConfigured(): ListenableFuture<Unit> = Futures.immediateFuture(Unit)

        // There is no need to slow down the unit tests by initialising CityDatabase
        override fun findMyLocation(): PhysicalLocation? = null

        override fun makeUniquenessProvider(type: ServiceType): UniquenessProvider = InMemoryUniquenessProvider()

        override fun start(): MockNode {
            super.start()
            mockNet.identities.add(info.legalIdentity)
            return this
        }

        // Allow unit tests to modify the plugin list before the node start,
        // so they don't have to ServiceLoad test plugins into all unit tests.
        val testPluginRegistries = super.pluginRegistries.toMutableList()
        override val pluginRegistries: List<CordaPluginRegistry>
            get() = testPluginRegistries

        // This does not indirect through the NodeInfo object so it can be called before the node is started.
        // It is used from the network visualiser tool.
        @Suppress("unused") val place: PhysicalLocation get() = findMyLocation()!!

        fun pumpReceive(block: Boolean = false): InMemoryMessagingNetwork.MessageTransfer? {
            return (net as InMemoryMessagingNetwork.InMemoryMessaging).pumpReceive(block)
        }

        fun disableDBCloseOnStop() {
            runOnStop.remove(dbCloser)
        }

        fun manuallyCloseDB() {
            dbCloser?.run()
            dbCloser = null
        }

        // You can change this from zero if you have custom [FlowLogic] that park themselves.  e.g. [StateMachineManagerTests]
        var acceptableLiveFiberCountOnStop: Int = 0

        override fun acceptableLiveFiberCountOnStop(): Int = acceptableLiveFiberCountOnStop
    }

    /** Returns a node, optionally created by the passed factory method. */
    fun createNode(networkMapAddress: SingleMessageRecipient? = null, forcedID: Int = -1, nodeFactory: Factory = defaultFactory,
                   start: Boolean = true, legalName: String? = null, keyPair: KeyPair? = null,
                   vararg advertisedServices: ServiceInfo): MockNode {
        val newNode = forcedID == -1
        val id = if (newNode) nextNodeId++ else forcedID

        val path = filesystem.getPath("/nodes/$id")
        if (newNode)
            (path / "attachments").createDirectories()

        val config = TestNodeConfiguration(
                baseDirectory = path,
                myLegalName = legalName ?: "Mock Company $id",
                networkMapService = null,
                dataSourceProperties = makeTestDataSourceProperties("node_${id}_net_$networkId"))
        val node = nodeFactory.create(config, this, networkMapAddress, advertisedServices.toSet(), id, keyPair)
        if (start) {
            node.setup().start()
            if (threadPerNode && networkMapAddress != null)
                node.networkMapRegistrationFuture.getOrThrow()   // Block and wait for the node to register in the net map.
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
                createNode(null, -1, nodeFactory, true, null, notaryKeyPair, ServiceInfo(NetworkMapService.type), ServiceInfo(SimpleNotaryService.type)),
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
        val mapNode = createNode(null, nodeFactory = nodeFactory, advertisedServices = ServiceInfo(NetworkMapService.type))
        val notaryNode = createNode(mapNode.info.address, nodeFactory = nodeFactory, keyPair = notaryKeyPair,
                advertisedServices = ServiceInfo(SimpleNotaryService.type))
        val nodes = ArrayList<MockNode>()
        repeat(numPartyNodes) {
            nodes += createPartyNode(mapNode.info.address)
        }
        return BasketOfNodes(nodes, notaryNode, mapNode)
    }

    fun createNotaryNode(networkMapAddr: SingleMessageRecipient? = null, legalName: String? = null, keyPair: KeyPair? = null, serviceName: String? = null): MockNode {
        return createNode(networkMapAddr, -1, defaultFactory, true, legalName, keyPair, ServiceInfo(NetworkMapService.type), ServiceInfo(ValidatingNotaryService.type, serviceName))
    }

    fun createPartyNode(networkMapAddr: SingleMessageRecipient, legalName: String? = null, keyPair: KeyPair? = null): MockNode {
        return createNode(networkMapAddr, -1, defaultFactory, true, legalName, keyPair)
    }

    @Suppress("unused") // This is used from the network visualiser tool.
    fun addressToNode(address: SingleMessageRecipient): MockNode = nodes.single { it.net.myAddress == address }

    fun startNodes() {
        require(nodes.isNotEmpty())
        nodes.forEach { if (!it.started) it.start() }
    }

    fun stopNodes() {
        require(nodes.isNotEmpty())
        nodes.forEach { if (it.started) it.stop() }
    }

    // Test method to block until all scheduled activity, active flows
    // and network activity has ceased.
    fun waitQuiescent() {
        busyLatch.await()
    }
}
