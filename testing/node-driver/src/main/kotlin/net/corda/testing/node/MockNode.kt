package net.corda.testing.node

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.cert
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.createDirectories
import net.corda.core.internal.createDirectory
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.utils.WorldMapLocation
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.StartedNode
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.api.SchemaService
import net.corda.node.services.config.BFTSMaRtConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.InMemoryNetworkMapService
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.BFTSMaRt
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.node.utilities.CertificateAndKeyPair
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.DUMMY_KEY_1
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
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun StartedNode<MockNetwork.MockNode>.pumpReceive(block: Boolean = false): InMemoryMessagingNetwork.MessageTransfer? {
    return (network as InMemoryMessagingNetwork.InMemoryMessaging).pumpReceive(block)
}

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
                  private val defaultFactory: Factory<*> = MockNetwork.DefaultFactory,
                  private val initialiseSerialization: Boolean = true) : Closeable {
    companion object {
        // TODO In future PR we're removing the concept of network map node so the details of this mock are not important.
        val MOCK_NET_MAP = Party(CordaX500Name(organisation = "Mock Network Map", locality = "Madrid", country = "ES"), DUMMY_KEY_1.public)
    }
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

    private var _networkMapNode: StartedNode<MockNode>? = null
    val networkMapNode: StartedNode<MockNode> get() = _networkMapNode ?: startNetworkMapNode()

    init {
        if (initialiseSerialization) initialiseTestSerialization()
        filesystem.getPath("/nodes").createDirectory()
    }

    /** Allows customisation of how nodes are created. */
    interface Factory<out N : MockNode> {
        /**
         * @param notaryIdentity is an additional override to use in place of the node's default notary service,
         * main usage is for when the node is part of a notary cluster.
         * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
         * but can be overriden to cause nodes to have stable or colliding identity/service keys.
         */
        fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                   id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?, entropyRoot: BigInteger): N
    }

    object DefaultFactory : Factory<MockNode> {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?, entropyRoot: BigInteger): MockNode {
            return MockNode(config, network, networkMapAddr, id, notaryIdentity, entropyRoot)
        }
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

    /**
     * @param notaryIdentity is an additional override to use in place of the node's default notary service,
     * main usage is for when the node is part of a notary cluster.
     * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overriden to cause nodes to have stable or colliding identity/service keys.
     */
    open class MockNode(config: NodeConfiguration,
                        val mockNet: MockNetwork,
                        override val networkMapAddress: SingleMessageRecipient?,
                        val id: Int,
                        internal val notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                        val entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue())) :
            AbstractNode(config, TestClock(), MOCK_VERSION_INFO, mockNet.busyLatch) {
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

        override fun makeIdentityService(trustRoot: X509Certificate,
                                         clientCa: CertificateAndKeyPair?,
                                         legalIdentity: PartyAndCertificate): IdentityService {
            val caCertificates: Array<X509Certificate> = listOf(legalIdentity.certificate, clientCa?.certificate?.cert)
                    .filterNotNull()
                    .toTypedArray()
            val identityService = PersistentIdentityService(info.legalIdentitiesAndCerts,
                    trustRoot = trustRoot, caCertificates = *caCertificates)
            services.networkMapCache.allNodes.forEach { it.legalIdentitiesAndCerts.forEach { identityService.verifyAndRegisterIdentity(it) } }
            services.networkMapCache.changed.subscribe { mapChange ->
                // TODO how should we handle network map removal
                if (mapChange is NetworkMapCache.MapChange.Added) {
                    mapChange.node.legalIdentitiesAndCerts.forEach {
                        identityService.verifyAndRegisterIdentity(it)
                    }
                }
            }
            return identityService
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

        // It's OK to not have a network map service in the mock network.
        override fun noNetworkMapConfigured() = doneFuture(Unit)

        // There is no need to slow down the unit tests by initialising CityDatabase
        open fun findMyLocation(): WorldMapLocation? = null // It's left only for NetworkVisualiserSimulation

        override fun makeTransactionVerifierService() = InMemoryTransactionVerifierService(1)

        override fun myAddresses() = emptyList<NetworkHostAndPort>()

        // Allow unit tests to modify the serialization whitelist list before the node start,
        // so they don't have to ServiceLoad test whitelists into all unit tests.
        val testSerializationWhitelists by lazy { super.serializationWhitelists.toMutableList() }
        override val serializationWhitelists: List<SerializationWhitelist>
            get() = testSerializationWhitelists

        // This does not indirect through the NodeInfo object so it can be called before the node is started.
        // It is used from the network visualiser tool.
        @Suppress("unused")
        val place: WorldMapLocation
            get() = findMyLocation()!!

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
                        val notaryService = it.started!!.smm.findServices { it is BFTNonValidatingNotaryService }.single() as BFTNonValidatingNotaryService
                        notaryService.waitUntilReplicaHasInitialized()
                    }
                }
            }
        }

        /**
         * Makes sure that the [MockNode] is correctly registered on the [MockNetwork]
         * Please note that [MockNetwork.runNetwork] should be invoked to ensure that all the pending registration requests
         * were duly processed
         */
        fun ensureRegistered() {
            _nodeReadyFuture.getOrThrow()
        }
    }

    fun <N : MockNode> startNetworkMapNode(nodeFactory: Factory<N>? = null): StartedNode<N> {
        check(_networkMapNode == null) { "Trying to start more than one network map node" }
        return uncheckedCast(createNodeImpl(networkMapAddress = null,
                forcedID = null,
                nodeFactory = nodeFactory ?: defaultFactory,
                legalName = MOCK_NET_MAP.name,
                notaryIdentity = null,
                entropyRoot = BigInteger.valueOf(random63BitValue()),
                configOverrides = {},
                start = true
        ).started!!.apply {
            _networkMapNode = this
        })
    }

    fun createUnstartedNode(forcedID: Int? = null,
                            legalName: CordaX500Name? = null, notaryIdentity: Pair<ServiceInfo, KeyPair>? = null,
                            entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                            configOverrides: (NodeConfiguration) -> Any? = {}): MockNode {
        return createUnstartedNode(forcedID, defaultFactory, legalName, notaryIdentity, entropyRoot, configOverrides = configOverrides)
    }

    fun <N : MockNode> createUnstartedNode(forcedID: Int? = null, nodeFactory: Factory<N>,
                                           legalName: CordaX500Name? = null, notaryIdentity: Pair<ServiceInfo, KeyPair>? = null,
                                           entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                                           configOverrides: (NodeConfiguration) -> Any? = {}): N {
        val networkMapAddress = networkMapNode.network.myAddress
        return createNodeImpl(networkMapAddress, forcedID, nodeFactory, false, legalName, notaryIdentity, entropyRoot, configOverrides)
    }

    /**
     * Returns a node, optionally created by the passed factory method.
     * @param notaryIdentity a set of service entries to use in place of the node's default service entries,
     * for example where a node's service is part of a cluster.
     * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overridden to cause nodes to have stable or colliding identity/service keys.
     * @param configOverrides add/override behaviour of the [NodeConfiguration] mock object.
     */
    fun createNode(forcedID: Int? = null,
                   legalName: CordaX500Name? = null, notaryIdentity: Pair<ServiceInfo, KeyPair>? = null,
                   entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                   configOverrides: (NodeConfiguration) -> Any? = {}): StartedNode<MockNode> {
        return createNode(forcedID, defaultFactory, legalName, notaryIdentity, entropyRoot, configOverrides = configOverrides)
    }

    /** Like the other [createNode] but takes a [Factory] and propagates its [MockNode] subtype. */
    fun <N : MockNode> createNode(forcedID: Int? = null, nodeFactory: Factory<N>,
                                  legalName: CordaX500Name? = null, notaryIdentity: Pair<ServiceInfo, KeyPair>? = null,
                                  entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                                  configOverrides: (NodeConfiguration) -> Any? = {}): StartedNode<N> {
        val networkMapAddress = networkMapNode.network.myAddress
        return uncheckedCast(createNodeImpl(networkMapAddress, forcedID, nodeFactory, true, legalName, notaryIdentity, entropyRoot, configOverrides).started)!!
    }

    private fun <N : MockNode> createNodeImpl(networkMapAddress: SingleMessageRecipient?, forcedID: Int?, nodeFactory: Factory<N>,
                                              start: Boolean, legalName: CordaX500Name?, notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                                              entropyRoot: BigInteger,
                                              configOverrides: (NodeConfiguration) -> Any?): N {
        val id = forcedID ?: nextNodeId++
        val config = testNodeConfiguration(
                baseDirectory = baseDirectory(id).createDirectories(),
                myLegalName = legalName ?: CordaX500Name(organisation = "Mock Company $id", locality = "London", country = "GB")).also {
            whenever(it.dataSourceProperties).thenReturn(makeTestDataSourceProperties("node_${id}_net_$networkId"))
            configOverrides(it)
        }
        return nodeFactory.create(config, this, networkMapAddress, id, notaryIdentity, entropyRoot).apply {
            if (start) {
                start()
                if (threadPerNode && networkMapAddress != null) nodeReadyFuture.getOrThrow() // XXX: What about manually-started nodes?
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
    fun createNotaryNode(legalName: CordaX500Name? = null, validating: Boolean = true): StartedNode<MockNode> {
        return createNode(legalName = legalName, configOverrides = {
            whenever(it.notary).thenReturn(NotaryConfig(validating))
        })
    }

    fun <N : MockNode> createNotaryNode(legalName: CordaX500Name? = null,
                                        validating: Boolean = true,
                                        nodeFactory: Factory<N>): StartedNode<N> {
        return createNode(legalName = legalName, nodeFactory = nodeFactory, configOverrides = {
            whenever(it.notary).thenReturn(NotaryConfig(validating))
        })
    }

    @JvmOverloads
    fun createPartyNode(legalName: CordaX500Name? = null,
                        notaryIdentity: Pair<ServiceInfo, KeyPair>? = null): StartedNode<MockNode> {
        return createNode(legalName = legalName, notaryIdentity = notaryIdentity)
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