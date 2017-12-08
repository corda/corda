package net.corda.testing.node

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.createDirectories
import net.corda.core.internal.createDirectory
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.StartedNode
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.api.SchemaService
import net.corda.node.services.config.BFTSMaRtConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.BFTSMaRt
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.internal.testThreadFactory
import net.corda.testing.node.MockServices.Companion.MOCK_VERSION_INFO
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.setGlobalSerialization
import net.corda.testing.testNodeConfiguration
import org.apache.activemq.artemis.utils.ReusableLatch
import org.apache.sshd.common.util.security.SecurityUtils
import rx.internal.schedulers.CachedThreadScheduler
import java.math.BigInteger
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.time.Clock
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
        val defaultFactory: (MockNodeArgs) -> MockNetwork.MockNode = MockNetwork::MockNode,
        val initialiseSerialization: Boolean = true,
        val cordappPackages: Set<String> = emptySet()) {
    fun setNetworkSendManuallyPumped(networkSendManuallyPumped: Boolean) = copy(networkSendManuallyPumped = networkSendManuallyPumped)
    fun setThreadPerNode(threadPerNode: Boolean) = copy(threadPerNode = threadPerNode)
    fun setServicePeerAllocationStrategy(servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy) = copy(servicePeerAllocationStrategy = servicePeerAllocationStrategy)
    fun setDefaultFactory(defaultFactory: (MockNodeArgs) -> MockNetwork.MockNode) = copy(defaultFactory = defaultFactory)
    fun setInitialiseSerialization(initialiseSerialization: Boolean) = copy(initialiseSerialization = initialiseSerialization)
    fun setCordappPackages(cordappPackages: Set<String>) = copy(cordappPackages = cordappPackages)
}

/**
 * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
 * but can be overridden to cause nodes to have stable or colliding identity/service keys.
 * @param configOverrides add/override behaviour of the [NodeConfiguration] mock object.
 */
@Suppress("unused")
data class MockNodeParameters(
        val forcedID: Int? = null,
        val legalName: CordaX500Name? = null,
        val entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
        val configOverrides: (NodeConfiguration) -> Any? = {}) {
    fun setForcedID(forcedID: Int?) = copy(forcedID = forcedID)
    fun setLegalName(legalName: CordaX500Name?) = copy(legalName = legalName)
    fun setEntropyRoot(entropyRoot: BigInteger) = copy(entropyRoot = entropyRoot)
    fun setConfigOverrides(configOverrides: (NodeConfiguration) -> Any?) = copy(configOverrides = configOverrides)
}

data class MockNodeArgs(
        val config: NodeConfiguration,
        val network: MockNetwork,
        val id: Int,
        val entropyRoot: BigInteger
)

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
 *
 * By default a single notary node is automatically started, which forms part of the network parameters for all the nodes.
 * This node is available by calling [defaultNotaryNode].
 */
class MockNetwork(defaultParameters: MockNetworkParameters = MockNetworkParameters(),
                  private val networkSendManuallyPumped: Boolean = defaultParameters.networkSendManuallyPumped,
                  private val threadPerNode: Boolean = defaultParameters.threadPerNode,
                  servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = defaultParameters.servicePeerAllocationStrategy,
                  private val defaultFactory: (MockNodeArgs) -> MockNode = defaultParameters.defaultFactory,
                  initialiseSerialization: Boolean = defaultParameters.initialiseSerialization,
                  private val notarySpecs: List<NotarySpec> = listOf(NotarySpec(DUMMY_NOTARY.name)),
                  private val cordappPackages: Set<String> = defaultParameters.cordappPackages) {
    /** Helper constructor for creating a [MockNetwork] with custom parameters from Java. */
    constructor(parameters: MockNetworkParameters) : this(defaultParameters = parameters)

    init {
        // Apache SSHD for whatever reason registers a SFTP FileSystemProvider - which gets loaded by JimFS.
        // This SFTP support loads BouncyCastle, which we want to avoid.
        // Please see https://issues.apache.org/jira/browse/SSHD-736 - it's easier then to create our own fork of SSHD
        SecurityUtils.setAPrioriDisabledProvider("BC", true)
    }

    var nextNodeId = 0
        private set
    private val filesystem = Jimfs.newFileSystem(unix())
    private val busyLatch = ReusableLatch()
    val messagingNetwork = InMemoryMessagingNetwork(networkSendManuallyPumped, servicePeerAllocationStrategy, busyLatch)
    // A unique identifier for this network to segregate databases with the same nodeID but different networks.
    private val networkId = random63BitValue()
    private val _nodes = mutableListOf<MockNode>()
    private val serializationEnv = setGlobalSerialization(initialiseSerialization)
    private val sharedUserCount = AtomicInteger(0)
    /** A read only view of the current set of executing nodes. */
    val nodes: List<MockNode> get() = _nodes

    /**
     * Returns the list of nodes started by the network. Each notary specified when the network is constructed ([notarySpecs]
     * parameter) maps 1:1 to the notaries returned by this list.
     */
    val notaryNodes: List<StartedNode<MockNode>>

    /**
     * Returns the single notary node on the network. Throws if there are none or more than one.
     * @see notaryNodes
     */
    val defaultNotaryNode: StartedNode<MockNode> get() {
        return when (notaryNodes.size) {
            0 -> throw IllegalStateException("There are no notaries defined on the network")
            1 -> notaryNodes[0]
            else -> throw IllegalStateException("There is more than one notary defined on the network")
        }
    }

    /**
     * Return the identity of the default notary node.
     * @see defaultNotaryNode
     */
    val defaultNotaryIdentity: Party get() {
        return defaultNotaryNode.info.legalIdentities[1] // TODO Resolve once network parameters is merged back in
    }

    /**
     * Return the identity of the default notary node.
     * @see defaultNotaryNode
     */
    val defaultNotaryIdentityAndCert: PartyAndCertificate get() {
        return defaultNotaryNode.info.legalIdentitiesAndCerts[1] // TODO Resolve once network parameters is merged back in
    }

    /**
     * Because this executor is shared, we need to be careful about nodes shutting it down.
     */
    private val sharedServerThread = object : ServiceAffinityExecutor("Mock network", 1) {
        override fun shutdown() {
            // We don't actually allow the shutdown of the network-wide shared thread pool until all references to
            // it have been shutdown.
            if (sharedUserCount.decrementAndGet() == 0) {
                super.shutdown()
            }
        }

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
            return if (!isShutdown) {
                flush()
                true
            } else {
                super.awaitTermination(timeout, unit)
            }
        }
    }

    init {
        filesystem.getPath("/nodes").createDirectory()
        notaryNodes = createNotaries()
    }

    private fun createNotaries(): List<StartedNode<MockNode>> {
        return notarySpecs.map { spec ->
            createNode(MockNodeParameters(legalName = spec.name, configOverrides = {
                doReturn(NotaryConfig(spec.validating)).whenever(it).notary
            }))
        }
    }

    open class MockNode(args: MockNodeArgs) : AbstractNode(
            args.config,
            TestClock(Clock.systemUTC()),
            MOCK_VERSION_INFO,
            CordappLoader.createDefaultWithTestPackages(args.config, args.network.cordappPackages),
            args.network.busyLatch
    ) {
        companion object {
            private val staticLog = contextLogger()
        }

        val mockNet = args.network
        val id = args.id
        private val entropyRoot = args.entropyRoot
        var counter = entropyRoot
        override val log get() = staticLog
        override val serverThread: AffinityExecutor =
                if (mockNet.threadPerNode) {
                    ServiceAffinityExecutor("Mock node $id thread", 1)
                } else {
                    mockNet.sharedUserCount.incrementAndGet()
                    mockNet.sharedServerThread
                }

        override val started: StartedNode<MockNode>? get() = uncheckedCast(super.started)

        override fun start(): StartedNode<MockNode> {
            val started: StartedNode<MockNode> = uncheckedCast(super.start())
            advertiseNodeToNetwork(started)
            return started
        }

        override fun getRxIoScheduler() = CachedThreadScheduler(testThreadFactory()).also { runOnStop += it::shutdown }
        private fun advertiseNodeToNetwork(newNode: StartedNode<MockNode>) {
            mockNet.nodes
                    .mapNotNull { it.started }
                    .forEach { existingNode ->
                        newNode.services.networkMapCache.addNode(existingNode.info)
                        existingNode.services.networkMapCache.addNode(newNode.info)
                    }
        }

        // We only need to override the messaging service here, as currently everything that hits disk does so
        // through the java.nio API which we are already mocking via Jimfs.
        override fun makeMessagingService(database: CordaPersistence, info: NodeInfo): MessagingService {
            require(id >= 0) { "Node ID must be zero or positive, was passed: " + id }
            return mockNet.messagingNetwork.createNodeWithID(
                    !mockNet.threadPerNode,
                    id,
                    serverThread,
                    myNotaryIdentity,
                    myLegalName,
                    database).also { runOnStop += it::stop }
        }

        fun setMessagingServiceSpy(messagingServiceSpy: MessagingServiceSpy) {
            network = messagingServiceSpy
        }

        override fun makeKeyManagementService(identityService: IdentityServiceInternal, keyPairs: Set<KeyPair>): KeyManagementService {
            return E2ETestKeyManagementService(identityService, keyPairs)
        }

        override fun startShell(rpcOps: CordaRPCOps) {
            //No mock shell
        }

        override fun startMessagingService(rpcOps: RPCOps) {
            // Nothing to do
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

        override fun myAddresses(): List<NetworkHostAndPort> = emptyList()

        // Allow unit tests to modify the serialization whitelist list before the node start,
        // so they don't have to ServiceLoad test whitelists into all unit tests.
        val testSerializationWhitelists by lazy { super.serializationWhitelists.toMutableList() }
        override val serializationWhitelists: List<SerializationWhitelist>
            get() = testSerializationWhitelists
        private var dbCloser: (() -> Any?)? = null
        override fun <T> initialiseDatabasePersistence(schemaService: SchemaService, identityService: IdentityService, insideTransaction: (CordaPersistence) -> T): T {
            return super.initialiseDatabasePersistence(schemaService, identityService) { database ->
                dbCloser = database::close
                insideTransaction(database)
            }
        }

        fun disableDBCloseOnStop() {
            runOnStop.remove(dbCloser)
        }

        fun manuallyCloseDB() {
            dbCloser?.invoke()
            dbCloser = null
        }

        var acceptableLiveFiberCountOnStop: Int = 0

        override fun acceptableLiveFiberCountOnStop(): Int = acceptableLiveFiberCountOnStop

        override fun makeBFTCluster(notaryKey: PublicKey, bftSMaRtConfig: BFTSMaRtConfiguration): BFTSMaRt.Cluster {
            return object : BFTSMaRt.Cluster {
                override fun waitUntilAllReplicasHaveInitialized() {
                    val clusterNodes = mockNet.nodes.map { it.started!! }.filter { notaryKey in it.info.legalIdentities.map { it.owningKey } }
                    if (clusterNodes.size != bftSMaRtConfig.clusterAddresses.size) {
                        throw IllegalStateException("Unable to enumerate all nodes in BFT cluster.")
                    }
                    clusterNodes.forEach {
                        (it.notaryService as BFTNonValidatingNotaryService).waitUntilReplicaHasInitialized()
                    }
                }
            }
        }
    }

    fun createUnstartedNode(parameters: MockNodeParameters = MockNodeParameters()): MockNode {
        return createUnstartedNode(parameters, defaultFactory)
    }

    fun <N : MockNode> createUnstartedNode(parameters: MockNodeParameters = MockNodeParameters(), nodeFactory: (MockNodeArgs) -> N): N {
        return createNodeImpl(parameters, nodeFactory, false)
    }

    fun createNode(parameters: MockNodeParameters = MockNodeParameters()): StartedNode<MockNode> {
        return createNode(parameters, defaultFactory)
    }

    /** Like the other [createNode] but takes a [nodeFactory] and propagates its [MockNode] subtype. */
    fun <N : MockNode> createNode(parameters: MockNodeParameters = MockNodeParameters(), nodeFactory: (MockNodeArgs) -> N): StartedNode<N> {
        return uncheckedCast(createNodeImpl(parameters, nodeFactory, true).started)!!
    }

    private fun <N : MockNode> createNodeImpl(parameters: MockNodeParameters, nodeFactory: (MockNodeArgs) -> N, start: Boolean): N {
        val id = parameters.forcedID ?: nextNodeId++
        val config = testNodeConfiguration(
                baseDirectory = baseDirectory(id).createDirectories(),
                myLegalName = parameters.legalName ?: CordaX500Name(organisation = "Mock Company $id", locality = "London", country = "GB")).also {
            doReturn(makeTestDataSourceProperties("node_${id}_net_$networkId")).whenever(it).dataSourceProperties
            parameters.configOverrides(it)
        }
        val node = nodeFactory(MockNodeArgs(config, this, id, parameters.entropyRoot))
        _nodes += node
        if (start) {
            node.start()
        }
        return node
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

    fun createPartyNode(legalName: CordaX500Name? = null): StartedNode<MockNode> {
        return createNode(MockNodeParameters(legalName = legalName))
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

    fun startNodes() {
        require(nodes.isNotEmpty())
        nodes.forEach { it.started ?: it.start() }
    }

    fun stopNodes() {
        nodes.forEach { it.started?.dispose() }
        serializationEnv.unset()
        messagingNetwork.stop()
    }

    // Test method to block until all scheduled activity, active flows
    // and network activity has ceased.
    fun waitQuiescent() {
        busyLatch.await()
    }

    data class NotarySpec(val name: CordaX500Name, val validating: Boolean = true) {
        constructor(name: CordaX500Name) : this(name, validating = true)
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
