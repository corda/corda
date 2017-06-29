package net.corda.testing.node

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.*
import net.corda.core.crypto.CertificateAndKeyPair
import net.corda.core.crypto.cert
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.WorldMapLocation
import net.corda.core.node.ServiceEntry
import net.corda.core.node.services.*
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.getTestPartyAndCertificate
import net.corda.core.utilities.loggerFor
import net.corda.flows.TxKeyFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.InMemoryNetworkMapService
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.testing.MOCK_VERSION_INFO
import net.corda.testing.getTestX509Name
import net.corda.testing.testNodeConfiguration
import org.apache.activemq.artemis.utils.ReusableLatch
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.Logger
import java.math.BigInteger
import java.nio.file.FileSystem
import java.security.KeyPair
import java.security.cert.X509Certificate
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
    val nextNodeId
        get() = _nextNodeId
    private var _nextNodeId = 0
    val filesystem: FileSystem = Jimfs.newFileSystem(unix())
    private val busyLatch: ReusableLatch = ReusableLatch()
    val messagingNetwork = InMemoryMessagingNetwork(networkSendManuallyPumped, servicePeerAllocationStrategy, busyLatch)

    // A unique identifier for this network to segregate databases with the same nodeID but different networks.
    private val networkId = random63BitValue()

    val identities = ArrayList<PartyAndCertificate>()

    private val _nodes = ArrayList<MockNode>()
    /** A read only view of the current set of executing nodes. */
    val nodes: List<MockNode> = _nodes

    init {
        filesystem.getPath("/nodes").createDirectory()
    }

    /** Allows customisation of how nodes are created. */
    interface Factory {
        /**
         * @param overrideServices a set of service entries to use in place of the node's default service entries,
         * for example where a node's service is part of a cluster.
         * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
         * but can be overriden to cause nodes to have stable or colliding identity/service keys.
         */
        fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                   advertisedServices: Set<ServiceInfo>, id: Int, overrideServices: Map<ServiceInfo, KeyPair>?,
                   entropyRoot: BigInteger): MockNode
    }

    object DefaultFactory : Factory {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, overrideServices: Map<ServiceInfo, KeyPair>?,
                            entropyRoot: BigInteger): MockNode {
            return MockNode(config, network, networkMapAddr, advertisedServices, id, overrideServices, entropyRoot)
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
     * @param overrideServices a set of service entries to use in place of the node's default service entries,
     * for example where a node's service is part of a cluster.
     * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overriden to cause nodes to have stable or colliding identity/service keys.
     */
    open class MockNode(config: NodeConfiguration,
                        val mockNet: MockNetwork,
                        override val networkMapAddress: SingleMessageRecipient?,
                        advertisedServices: Set<ServiceInfo>,
                        val id: Int,
                        val overrideServices: Map<ServiceInfo, KeyPair>?,
                        val entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue())) :
            AbstractNode(config, advertisedServices, TestClock(), mockNet.busyLatch) {
        var counter = entropyRoot
        override val log: Logger = loggerFor<MockNode>()
        override val platformVersion: Int get() = MOCK_VERSION_INFO.platformVersion
        override val serverThread: AffinityExecutor =
                if (mockNet.threadPerNode)
                    ServiceAffinityExecutor("Mock node $id thread", 1)
                else {
                    mockNet.sharedUserCount.incrementAndGet()
                    mockNet.sharedServerThread
                }

        // We only need to override the messaging service here, as currently everything that hits disk does so
        // through the java.nio API which we are already mocking via Jimfs.
        override fun makeMessagingService(): MessagingService {
            require(id >= 0) { "Node ID must be zero or positive, was passed: " + id }
            return mockNet.messagingNetwork.createNodeWithID(
                    !mockNet.threadPerNode,
                    id,
                    serverThread,
                    makeServiceEntries(),
                    configuration.myLegalName,
                    database)
                    .start()
                    .getOrThrow()
        }

        override fun makeIdentityService(trustRoot: X509Certificate,
                                         clientCa: CertificateAndKeyPair?,
                                         legalIdentity: PartyAndCertificate): IdentityService {
            val caCertificates: Array<X509Certificate> = listOf(legalIdentity.certificate.cert, clientCa?.certificate?.cert)
                    .filterNotNull()
                    .toTypedArray()
            return InMemoryIdentityService((mockNet.identities + info.legalIdentityAndCert).toSet(),
                    trustRoot = trustRoot, caCertificates = *caCertificates)
        }

        override fun makeVaultService(dataSourceProperties: Properties): VaultService = NodeVaultService(services, dataSourceProperties)

        override fun makeKeyManagementService(identityService: IdentityService): KeyManagementService {
            return E2ETestKeyManagementService(identityService, partyKeys + (overrideServices?.values ?: emptySet()))
        }

        override fun startMessagingService(rpcOps: RPCOps) {
            // Nothing to do
        }

        override fun makeNetworkMapService() {
            inNodeNetworkMapService = InMemoryNetworkMapService(services, platformVersion)
        }

        override fun makeServiceEntries(): List<ServiceEntry> {
            val defaultEntries = super.makeServiceEntries()
            return if (overrideServices == null) {
                defaultEntries
            } else {
                defaultEntries.map {
                    val override = overrideServices[it.info]
                    if (override != null) {
                        // TODO: Store the key
                        ServiceEntry(it.info, getTestPartyAndCertificate(it.identity.name, override.public))
                    } else {
                        it
                    }
                }
            }
        }

        // This is not thread safe, but node construction is done on a single thread, so that should always be fine
        override fun generateKeyPair(): KeyPair {
            counter = counter.add(BigInteger.ONE)
            return entropyToKeyPair(counter)
        }

        // It's OK to not have a network map service in the mock network.
        override fun noNetworkMapConfigured(): ListenableFuture<Unit> = Futures.immediateFuture(Unit)

        // There is no need to slow down the unit tests by initialising CityDatabase
        override fun findMyLocation(): WorldMapLocation? = null

        override fun makeTransactionVerifierService() = InMemoryTransactionVerifierService(1)

        override fun myAddresses(): List<HostAndPort> = listOf(HostAndPort.fromHost("mockHost"))

        override fun start(): MockNode {
            super.start()
            mockNet.identities.add(info.legalIdentityAndCert)
            return this
        }

        // Allow unit tests to modify the plugin list before the node start,
        // so they don't have to ServiceLoad test plugins into all unit tests.
        val testPluginRegistries = super.pluginRegistries.toMutableList()
        override val pluginRegistries: List<CordaPluginRegistry>
            get() = testPluginRegistries

        // This does not indirect through the NodeInfo object so it can be called before the node is started.
        // It is used from the network visualiser tool.
        @Suppress("unused") val place: WorldMapLocation get() = findMyLocation()!!

        fun pumpReceive(block: Boolean = false): InMemoryMessagingNetwork.MessageTransfer? {
            return (network as InMemoryMessagingNetwork.InMemoryMessaging).pumpReceive(block)
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
    }

    /**
     * Returns a node, optionally created by the passed factory method.
     * @param overrideServices a set of service entries to use in place of the node's default service entries,
     * for example where a node's service is part of a cluster.
     */
    fun createNode(networkMapAddress: SingleMessageRecipient? = null, forcedID: Int = -1, nodeFactory: Factory = defaultFactory,
                   start: Boolean = true, legalName: X500Name? = null, overrideServices: Map<ServiceInfo, KeyPair>? = null,
                   vararg advertisedServices: ServiceInfo): MockNode
            = createNode(networkMapAddress, forcedID, nodeFactory, start, legalName, overrideServices, BigInteger.valueOf(random63BitValue()), *advertisedServices)

    /**
     * Returns a node, optionally created by the passed factory method.
     * @param overrideServices a set of service entries to use in place of the node's default service entries,
     * for example where a node's service is part of a cluster.
     * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overridden to cause nodes to have stable or colliding identity/service keys.
     * @param configOverrides add/override behaviour of the [NodeConfiguration] mock object.
     */
    fun createNode(networkMapAddress: SingleMessageRecipient? = null, forcedID: Int = -1, nodeFactory: Factory = defaultFactory,
                   start: Boolean = true, legalName: X500Name? = null, overrideServices: Map<ServiceInfo, KeyPair>? = null,
                   entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                   vararg advertisedServices: ServiceInfo,
                   configOverrides: (NodeConfiguration) -> Any? = {}): MockNode {
        val newNode = forcedID == -1
        val id = if (newNode) _nextNodeId++ else forcedID

        val path = baseDirectory(id)
        if (newNode)
            (path / "attachments").createDirectories()

        val config = testNodeConfiguration(
                baseDirectory = path,
                myLegalName = legalName ?: getTestX509Name("Mock Company $id")).also {
            whenever(it.dataSourceProperties).thenReturn(makeTestDataSourceProperties("node_${id}_net_$networkId"))
            configOverrides(it)
        }
        val node = nodeFactory.create(config, this, networkMapAddress, advertisedServices.toSet(), id, overrideServices, entropyRoot)
        if (start) {
            node.setup().start()
            // Register flows that are normally found via plugins
            node.registerInitiatedFlow(TxKeyFlow.Provider::class.java)
            if (threadPerNode && networkMapAddress != null)
                node.networkMapRegistrationFuture.getOrThrow()   // Block and wait for the node to register in the net map.
        }
        _nodes.add(node)
        return node
    }

    fun baseDirectory(nodeId: Int) = filesystem.getPath("/nodes/$nodeId")

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

    // TODO: Move this to using createSomeNodes which doesn't conflate network services with network users.
    /**
     * Sets up a two node network, in which the first node runs network map and notary services and the other
     * doesn't.
     */
    fun createTwoNodes(firstNodeName: X500Name? = null,
                       secondNodeName: X500Name? = null,
                       nodeFactory: Factory = defaultFactory,
                       notaryKeyPair: KeyPair? = null): Pair<MockNode, MockNode> {
        require(nodes.isEmpty())
        val notaryServiceInfo = ServiceInfo(SimpleNotaryService.type)
        val notaryOverride = if (notaryKeyPair != null)
            mapOf(Pair(notaryServiceInfo, notaryKeyPair))
        else
            null
        return Pair(
                createNode(null, -1, nodeFactory, true, firstNodeName, notaryOverride, BigInteger.valueOf(random63BitValue()), ServiceInfo(NetworkMapService.type), notaryServiceInfo),
                createNode(nodes[0].network.myAddress, -1, nodeFactory, true, secondNodeName)
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
    @JvmOverloads
    fun createSomeNodes(numPartyNodes: Int = 2, nodeFactory: Factory = defaultFactory, notaryKeyPair: KeyPair? = DUMMY_NOTARY_KEY): BasketOfNodes {
        require(nodes.isEmpty())
        val notaryServiceInfo = ServiceInfo(SimpleNotaryService.type)
        val notaryOverride = if (notaryKeyPair != null)
            mapOf(Pair(notaryServiceInfo, notaryKeyPair))
        else
            null
        val mapNode = createNode(null, nodeFactory = nodeFactory, advertisedServices = ServiceInfo(NetworkMapService.type))
        val mapAddress = mapNode.network.myAddress
        val notaryNode = createNode(mapAddress, nodeFactory = nodeFactory, overrideServices = notaryOverride,
                advertisedServices = notaryServiceInfo)
        val nodes = ArrayList<MockNode>()
        repeat(numPartyNodes) {
            nodes += createPartyNode(mapAddress)
        }
        nodes.forEach { itNode ->
            nodes.map { it.info.legalIdentityAndCert }.forEach(itNode.services.identityService::registerIdentity)
        }
        return BasketOfNodes(nodes, notaryNode, mapNode)
    }

    fun createNotaryNode(networkMapAddr: SingleMessageRecipient? = null,
                         legalName: X500Name? = null,
                         overrideServices: Map<ServiceInfo, KeyPair>? = null,
                         serviceName: X500Name? = null): MockNode {
        return createNode(networkMapAddr, -1, defaultFactory, true, legalName, overrideServices, BigInteger.valueOf(random63BitValue()),
                ServiceInfo(NetworkMapService.type), ServiceInfo(ValidatingNotaryService.type, serviceName))
    }

    fun createPartyNode(networkMapAddr: SingleMessageRecipient,
                        legalName: X500Name? = null,
                        overrideServices: Map<ServiceInfo, KeyPair>? = null): MockNode {
        return createNode(networkMapAddr, -1, defaultFactory, true, legalName, overrideServices)
    }

    @Suppress("unused") // This is used from the network visualiser tool.
    fun addressToNode(msgRecipient: MessageRecipients): MockNode {
        return when (msgRecipient) {
            is SingleMessageRecipient -> nodes.single { it.network.myAddress == msgRecipient }
            is InMemoryMessagingNetwork.ServiceHandle -> {
                nodes.filter { it.advertisedServices.any { it == msgRecipient.service.info } }.firstOrNull()
                        ?: throw IllegalArgumentException("Couldn't find node advertising service with info: ${msgRecipient.service.info} ")
            }
            else -> throw IllegalArgumentException("Method not implemented for different type of message recipients")
        }
    }

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
