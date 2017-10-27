package net.corda.testing.node

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.createDirectories
import net.corda.core.internal.createDirectory
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.StartedNode
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.api.SchemaService
import net.corda.node.services.config.BFTSMaRtConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.node.services.messaging.*
import net.corda.node.services.network.InMemoryNetworkMapService
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.BFTSMaRt
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.initialiseTestSerialization
import net.corda.testing.node.MockServices.Companion.MOCK_VERSION_INFO
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.resetTestSerialization
import net.corda.testing.testNodeConfiguration
import org.apache.activemq.artemis.utils.ReusableLatch
import org.slf4j.Logger
import java.io.Closeable
import java.math.BigInteger
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun StartedNode<MockNetwork.MockNode>.pumpReceive(block: Boolean = false): InMemoryMessagingNetwork.MessageTransfer? {
    return (network as InMemoryMessagingNetwork.InMemoryMessaging).pumpReceive(block)
}

/** Helper builder for configuring a [MockNetwork] from Java. */
@Suppress("unused")
data class MockNetworkParameters(
        val networkSendManuallyPumped: Boolean = false,
        val threadPerNode: Boolean = false,
        val servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
        val defaultFactory: MockNetwork.Factory<*> = MockNetwork.DefaultFactory,
        val initialiseSerialization: Boolean = true,
        val cordappPackages: List<String> = emptyList()) {
    fun setNetworkSendManuallyPumped(networkSendManuallyPumped: Boolean) = copy(networkSendManuallyPumped = networkSendManuallyPumped)
    fun setThreadPerNode(threadPerNode: Boolean) = copy(threadPerNode = threadPerNode)
    fun setServicePeerAllocationStrategy(servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy) = copy(servicePeerAllocationStrategy = servicePeerAllocationStrategy)
    fun setDefaultFactory(defaultFactory: MockNetwork.Factory<*>) = copy(defaultFactory = defaultFactory)
    fun setInitialiseSerialization(initialiseSerialization: Boolean) = copy(initialiseSerialization = initialiseSerialization)
    fun setCordappPackages(cordappPackages: List<String>) = copy(cordappPackages = cordappPackages)
}

/**
 * @param notaryIdentity a set of service entries to use in place of the node's default service entries,
 * for example where a node's service is part of a cluster.
 * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
 * but can be overridden to cause nodes to have stable or colliding identity/service keys.
 * @param configOverrides add/override behaviour of the [NodeConfiguration] mock object.
 */
@Suppress("unused")
data class MockNodeParameters(
        val forcedID: Int? = null,
        val legalName: CordaX500Name? = null,
        val notaryIdentity: Pair<ServiceInfo, KeyPair>? = null,
        val entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
        val configOverrides: (NodeConfiguration) -> Any? = {}) {
    fun setForcedID(forcedID: Int?) = copy(forcedID = forcedID)
    fun setLegalName(legalName: CordaX500Name?) = copy(legalName = legalName)
    fun setNotaryIdentity(notaryIdentity: Pair<ServiceInfo, KeyPair>?) = copy(notaryIdentity = notaryIdentity)
    fun setEntropyRoot(entropyRoot: BigInteger) = copy(entropyRoot = entropyRoot)
    fun setConfigOverrides(configOverrides: (NodeConfiguration) -> Any?) = copy(configOverrides = configOverrides)
}

data class MockNodeArgs(
        val config: NodeConfiguration,
        val network: MockNetwork,
        val id: Int,
        val notaryIdentity: Pair<ServiceInfo, KeyPair>?,
        val entropyRoot: BigInteger)

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
class MockNetwork(defaultParameters: MockNetworkParameters = MockNetworkParameters(),
                  private val networkSendManuallyPumped: Boolean = defaultParameters.networkSendManuallyPumped,
                  private val threadPerNode: Boolean = defaultParameters.threadPerNode,
                  servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = defaultParameters.servicePeerAllocationStrategy,
                  private val defaultFactory: Factory<*> = defaultParameters.defaultFactory,
                  private val initialiseSerialization: Boolean = defaultParameters.initialiseSerialization,
                  private val cordappPackages: List<String> = defaultParameters.cordappPackages) : Closeable {
    /** Helper constructor for creating a [MockNetwork] with custom parameters from Java. */
    constructor(parameters: MockNetworkParameters) : this(defaultParameters = parameters)

    var nextNodeId = 0
        private set
    private val filesystem = Jimfs.newFileSystem(unix())
    private val busyLatch = ReusableLatch()
    val messagingNetwork = InMemoryMessagingNetwork(networkSendManuallyPumped, servicePeerAllocationStrategy, busyLatch)
    // A unique identifier for this network to segregate databases with the same nodeID but different networks.
    private val networkId = random63BitValue()
    private val _nodes = mutableListOf<MockNode>()
    /** A read only view of the current set of executing nodes. */
    val nodes: List<MockNode> get() = _nodes

    init {
        if (initialiseSerialization) initialiseTestSerialization()
        filesystem.getPath("/nodes").createDirectory()
    }

    /** Allows customisation of how nodes are created. */
    interface Factory<out N : MockNode> {
        fun create(args: MockNodeArgs): N
    }

    object DefaultFactory : Factory<MockNode> {
        override fun create(args: MockNodeArgs) = MockNode(args)
    }

    /**
     * Because this executor is shared, we need to be careful about nodes shutting it down.
     */
    private val sharedUserCount = AtomicInteger(0)
    private val sharedServerThread = object : ServiceAffinityExecutor("Mock network", 1) {
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

    open class MockNode(args: MockNodeArgs) : AbstractNode(
            args.config,
            TestClock(),
            MOCK_VERSION_INFO,
            CordappLoader.createDefaultWithTestPackages(args.config, args.network.cordappPackages),
            args.network.busyLatch) {
        val mockNet = args.network
        val id = args.id
        internal val notaryIdentity = args.notaryIdentity
        val entropyRoot = args.entropyRoot
        var counter = entropyRoot
        override val log: Logger = loggerFor<MockNode>()
        override val serverThread: AffinityExecutor =
                if (mockNet.threadPerNode)
                    ServiceAffinityExecutor("Mock node $id thread", 1)
                else {
                    mockNet.sharedUserCount.incrementAndGet()
                    mockNet.sharedServerThread
                }
        override val started: StartedNode<MockNode>? get() = uncheckedCast(super.started)
        override fun start(): StartedNode<MockNode> = uncheckedCast(super.start())

        // We only need to override the messaging service here, as currently everything that hits disk does so
        // through the java.nio API which we are already mocking via Jimfs.
        override fun makeMessagingService(legalIdentity: PartyAndCertificate): MessagingService {
            require(id >= 0) { "Node ID must be zero or positive, was passed: " + id }
            return mockNet.messagingNetwork.createNodeWithID(
                    !mockNet.threadPerNode,
                    id,
                    serverThread,
                    getNotaryIdentity(),
                    myLegalName,
                    database)
                    .start()
                    .getOrThrow()
        }

        fun setMessagingServiceSpy(messagingServiceSpy: MessagingServiceSpy) {
            network = messagingServiceSpy
        }

        override fun makeKeyManagementService(identityService: IdentityService): KeyManagementService {
            return E2ETestKeyManagementService(identityService, partyKeys + (notaryIdentity?.let { setOf(it.second) } ?: emptySet()))
        }

        override fun startMessagingService(rpcOps: RPCOps) {
            // Nothing to do
        }

        override fun makeNetworkMapService(network: MessagingService, networkMapCache: NetworkMapCacheInternal): NetworkMapService {
            return InMemoryNetworkMapService(network, networkMapCache, 1)
        }

        // This is not thread safe, but node construction is done on a single thread, so that should always be fine
        override fun generateKeyPair(): KeyPair {
            counter = counter.add(BigInteger.ONE)
            return entropyToKeyPair(counter)
        }

        /**
         * MockNetwork will ensure nodes are connected to each other. The nodes themselves
         * won't be able to tell if that happened already or not.
         */
        override fun checkNetworkMapIsInitialized() = Unit

        override fun makeTransactionVerifierService() = InMemoryTransactionVerifierService(1)

        override fun myAddresses() = emptyList<NetworkHostAndPort>()

        // Allow unit tests to modify the serialization whitelist list before the node start,
        // so they don't have to ServiceLoad test whitelists into all unit tests.
        val testSerializationWhitelists by lazy { super.serializationWhitelists.toMutableList() }
        override val serializationWhitelists: List<SerializationWhitelist>
            get() = testSerializationWhitelists
        private var dbCloser: (() -> Any?)? = null
        override fun <T> initialiseDatabasePersistence(schemaService: SchemaService, insideTransaction: () -> T) = super.initialiseDatabasePersistence(schemaService) {
            dbCloser = database::close
            insideTransaction()
        }

        fun disableDBCloseOnStop() {
            runOnStop.remove(dbCloser)
        }

        fun manuallyCloseDB() {
            dbCloser?.invoke()
            dbCloser = null
        }

        fun hasDBConnection() = dbCloser != null

        // You can change this from zero if you have custom [FlowLogic] that park themselves.  e.g. [StateMachineManagerTests]
        var acceptableLiveFiberCountOnStop: Int = 0

        override fun acceptableLiveFiberCountOnStop(): Int = acceptableLiveFiberCountOnStop

        override fun makeBFTCluster(notaryKey: PublicKey, bftSMaRtConfig: BFTSMaRtConfiguration): BFTSMaRt.Cluster {
            return object : BFTSMaRt.Cluster {
                override fun waitUntilAllReplicasHaveInitialized() {
                    val clusterNodes = mockNet.nodes.filter { notaryKey in it.started!!.info.legalIdentities.map { it.owningKey } }
                    if (clusterNodes.size != bftSMaRtConfig.clusterAddresses.size) {
                        throw IllegalStateException("Unable to enumerate all nodes in BFT cluster.")
                    }
                    clusterNodes.forEach {
                        val notaryService = it.findTokenizableService(BFTNonValidatingNotaryService::class.java)!!
                        notaryService.waitUntilReplicaHasInitialized()
                    }
                }
            }
        }
    }

    fun createUnstartedNode(parameters: MockNodeParameters = MockNodeParameters()) = createUnstartedNode(parameters, defaultFactory)
    fun <N : MockNode> createUnstartedNode(parameters: MockNodeParameters = MockNodeParameters(), nodeFactory: Factory<N>): N {
        return createNodeImpl(parameters, nodeFactory, false)
    }

    fun createNode(parameters: MockNodeParameters = MockNodeParameters()): StartedNode<MockNode> = createNode(parameters, defaultFactory)
    /** Like the other [createNode] but takes a [Factory] and propagates its [MockNode] subtype. */
    fun <N : MockNode> createNode(parameters: MockNodeParameters = MockNodeParameters(), nodeFactory: Factory<N>): StartedNode<N> {
        val node: StartedNode<N> = uncheckedCast(createNodeImpl(parameters, nodeFactory, true).started)!!
        ensureAllNetworkMapCachesHaveAllNodeInfos()
        return node
    }

    private fun <N : MockNode> createNodeImpl(parameters: MockNodeParameters, nodeFactory: Factory<N>, start: Boolean): N {
        val id = parameters.forcedID ?: nextNodeId++
        val config = testNodeConfiguration(
                baseDirectory = baseDirectory(id).createDirectories(),
                myLegalName = parameters.legalName ?: CordaX500Name(organisation = "Mock Company $id", locality = "London", country = "GB")).also {
            doReturn(makeTestDataSourceProperties("node_${id}_net_$networkId")).whenever(it).dataSourceProperties
            parameters.configOverrides(it)
        }
        return nodeFactory.create(MockNodeArgs(config, this, id, parameters.notaryIdentity, parameters.entropyRoot)).apply {
            if (start) {
                start()
                if (threadPerNode) nodeReadyFuture.getOrThrow() // XXX: What about manually-started nodes?
                ensureAllNetworkMapCachesHaveAllNodeInfos()
            }
            _nodes.add(this)
        }
    }

    fun baseDirectory(nodeId: Int): Path = filesystem.getPath("/nodes/$nodeId")

    /**
     * Asks every node in order to process any queued up inbound messages. This may in turn result in nodes
     * sending more messages to each other, thus, a typical usage is to call runNetwork with the [rounds]
     * parameter set to -1 (the default) which simply runs as many rounds as necessary to result in network
     * stability (no nodes sent any messages in the last round).
     */
    @JvmOverloads
    fun runNetwork(rounds: Int = -1) {
        ensureAllNetworkMapCachesHaveAllNodeInfos()
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

    @JvmOverloads
    fun createNotaryNode(legalName: CordaX500Name = DUMMY_NOTARY.name, validating: Boolean = true): StartedNode<MockNode> {
        return createNode(MockNodeParameters(legalName = legalName, configOverrides = {
            doReturn(NotaryConfig(validating)).whenever(it).notary
        }))
    }

    fun <N : MockNode> createNotaryNode(parameters: MockNodeParameters = MockNodeParameters(legalName = DUMMY_NOTARY.name),
                                        validating: Boolean = true,
                                        nodeFactory: Factory<N>): StartedNode<N> {
        return createNode(parameters.copy(configOverrides = {
            doReturn(NotaryConfig(validating)).whenever(it).notary
            parameters.configOverrides(it)
        }), nodeFactory)
    }

    @JvmOverloads
    fun createPartyNode(legalName: CordaX500Name? = null,
                        notaryIdentity: Pair<ServiceInfo, KeyPair>? = null): StartedNode<MockNode> {
        return createNode(MockNodeParameters(legalName = legalName, notaryIdentity = notaryIdentity))
    }

    @Suppress("unused") // This is used from the network visualiser tool.
    fun addressToNode(msgRecipient: MessageRecipients): MockNode {
        return when (msgRecipient) {
            is SingleMessageRecipient -> nodes.single { it.started!!.network.myAddress == msgRecipient }
            is InMemoryMessagingNetwork.ServiceHandle -> {
                nodes.firstOrNull { it.started!!.info.isLegalIdentity(msgRecipient.party) }
                        ?: throw IllegalArgumentException("Couldn't find node advertising service with owning party name: ${msgRecipient.party.name} ")
            }
            else -> throw IllegalArgumentException("Method not implemented for different type of message recipients")
        }
    }

    private fun ensureAllNetworkMapCachesHaveAllNodeInfos() {
        val infos = nodes.mapNotNull { it.started?.info }
        nodes.filter { it.hasDBConnection() }
                .mapNotNull { it.started?.services?.networkMapCache }
                .forEach {
                    for (nodeInfo in infos) {
                        it.addNode(nodeInfo)
                    }
                }
    }

    fun startNodes() {
        require(nodes.isNotEmpty())
        nodes.forEach { it.started ?: it.start() }
        ensureAllNetworkMapCachesHaveAllNodeInfos()
    }

    fun stopNodes() {
        nodes.forEach { it.started?.dispose() }
        if (initialiseSerialization) resetTestSerialization()
    }

    // Test method to block until all scheduled activity, active flows
    // and network activity has ceased.
    fun waitQuiescent() {
        busyLatch.await()
    }

    override fun close() {
        stopNodes()
    }
}

fun network(nodesCount: Int, action: MockNetwork.(nodes: List<StartedNode<MockNetwork.MockNode>>, notary: StartedNode<MockNetwork.MockNode>) -> Unit) {
    MockNetwork().use {
        it.runNetwork()
        val notary = it.createNotaryNode()
        val nodes = (1..nodesCount).map { _ -> it.createPartyNode() }
        action(it, nodes, notary)
    }
}

/**
 * Extend this class in order to intercept and modify messages passing through the [MessagingService] when using the [InMemoryMessagingNetwork].
 */
open class MessagingServiceSpy(val messagingService: MessagingService) : MessagingService by messagingService

/**
 * Attach a [MessagingServiceSpy] to the [MockNetwork.MockNode] allowing interception and modification of messages.
 */
fun StartedNode<MockNetwork.MockNode>.setMessagingServiceSpy(messagingServiceSpy: MessagingServiceSpy) {
    internals.setMessagingServiceSpy(messagingServiceSpy)
}