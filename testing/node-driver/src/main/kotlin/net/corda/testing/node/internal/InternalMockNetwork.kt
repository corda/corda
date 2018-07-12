package net.corda.testing.node.internal

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.DoNotImplement
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.createDirectories
import net.corda.core.internal.createDirectory
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.hours
import net.corda.core.utilities.seconds
import net.corda.node.VersionInfo
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.StartedNode
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.services.api.SchemaService
import net.corda.node.services.config.*
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.BFTSMaRt
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.setGlobalSerialization
import net.corda.testing.internal.testThreadFactory
import net.corda.testing.node.*
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
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

val MOCK_VERSION_INFO = VersionInfo(1, "Mock release", "Mock revision", "Mock Vendor")

fun StartedNode<InternalMockNetwork.MockNode>.pumpReceive(block: Boolean = false): InMemoryMessagingNetwork.MessageTransfer? {
    return (network as InternalMockMessagingService).pumpReceive(block)
}

data class MockNodeArgs(
        val config: NodeConfiguration,
        val network: InternalMockNetwork,
        val id: Int,
        val entropyRoot: BigInteger,
        val version: VersionInfo = MOCK_VERSION_INFO,
        val extraCordappPackages: List<String> = emptyList()
)

data class InternalMockNodeParameters(
        val forcedID: Int? = null,
        val legalName: CordaX500Name? = null,
        val entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
        val configOverrides: (NodeConfiguration) -> Any? = {},
        val version: VersionInfo = MOCK_VERSION_INFO,
        val extraCordappPackages: List<String> = emptyList()) {
    constructor(mockNodeParameters: MockNodeParameters) : this(
            mockNodeParameters.forcedID,
            mockNodeParameters.legalName,
            mockNodeParameters.entropyRoot,
            mockNodeParameters.configOverrides,
            MOCK_VERSION_INFO,
            mockNodeParameters.extraCordappPackages
    )
}

open class InternalMockNetwork(private val cordappPackages: List<String> = emptyList(),
                               defaultParameters: MockNetworkParameters = MockNetworkParameters(),
                               val networkSendManuallyPumped: Boolean = defaultParameters.networkSendManuallyPumped,
                               val threadPerNode: Boolean = defaultParameters.threadPerNode,
                               servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = defaultParameters.servicePeerAllocationStrategy,
                               val notarySpecs: List<MockNetworkNotarySpec> = defaultParameters.notarySpecs,
                               networkParameters: NetworkParameters = testNetworkParameters(),
                               val defaultFactory: (MockNodeArgs) -> MockNode = InternalMockNetwork::MockNode) {
    init {
        // Apache SSHD for whatever reason registers a SFTP FileSystemProvider - which gets loaded by JimFS.
        // This SFTP support loads BouncyCastle, which we want to avoid.
        // Please see https://issues.apache.org/jira/browse/SSHD-736 - it's easier then to create our own fork of SSHD
        SecurityUtils.setAPrioriDisabledProvider("BC", true) // XXX: Why isn't this static?
        require(networkParameters.notaries.isEmpty()) { "Define notaries using notarySpecs" }
    }

    var nextNodeId = 0
        private set
    private val filesystem = Jimfs.newFileSystem(unix())
    private val busyLatch = ReusableLatch()
    val messagingNetwork = InMemoryMessagingNetwork.create(networkSendManuallyPumped, servicePeerAllocationStrategy, busyLatch)
    // A unique identifier for this network to segregate databases with the same nodeID but different networks.
    private val networkId = random63BitValue()
    private val networkParametersCopier: NetworkParametersCopier
    private val _nodes = mutableListOf<MockNode>()
    private val serializationEnv = try {
        setGlobalSerialization(true)
    } catch (e: IllegalStateException) {
        throw IllegalStateException("Using more than one InternalMockNetwork simultaneously is not supported.", e)
    }
    private val sharedUserCount = AtomicInteger(0)

    /** A read only view of the current set of nodes. */
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
    val defaultNotaryNode: StartedNode<MockNode>
        get() {
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
    val defaultNotaryIdentity: Party
        get() {
            return defaultNotaryNode.info.legalIdentities.singleOrNull() ?: throw IllegalStateException("Default notary has multiple identities")
        }

    /**
     * Return the identity of the default notary node.
     * @see defaultNotaryNode
     */
    val defaultNotaryIdentityAndCert: PartyAndCertificate
        get() {
            return defaultNotaryNode.info.legalIdentitiesAndCerts.singleOrNull() ?: throw IllegalStateException("Default notary has multiple identities")
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
        try {
            filesystem.getPath("/nodes").createDirectory()
            val notaryInfos = generateNotaryIdentities()
            // The network parameters must be serialised before starting any of the nodes
            networkParametersCopier = NetworkParametersCopier(networkParameters.copy(notaries = notaryInfos))
            @Suppress("LeakingThis")
            notaryNodes = createNotaries()
        } catch (t: Throwable) {
            stopNodes()
            throw t
        }
    }

    private fun generateNotaryIdentities(): List<NotaryInfo> {
        return notarySpecs.mapIndexed { index, (name, validating) ->
            val identity = DevIdentityGenerator.installKeyStoreWithNodeIdentity(baseDirectory(nextNodeId + index), name)
            NotaryInfo(identity, validating)
        }
    }

    @VisibleForTesting
    internal open fun createNotaries(): List<StartedNode<MockNode>> {
        return notarySpecs.map { (name, validating) ->
            createNode(InternalMockNodeParameters(legalName = name, configOverrides = {
                doReturn(NotaryConfig(validating)).whenever(it).notary
            }))
        }
    }

    open class MockNode(args: MockNodeArgs) : AbstractNode(
            args.config,
            TestClock(Clock.systemUTC()),
            args.version,
            // Add the specified additional CorDapps.
            CordappLoader.createDefaultWithTestPackages(args.config, args.network.cordappPackages + args.extraCordappPackages),
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
        override val serverThread: AffinityExecutor.ServiceAffinityExecutor =
                if (mockNet.threadPerNode) {
                    ServiceAffinityExecutor("Mock node $id thread", 1)
                } else {
                    mockNet.sharedUserCount.incrementAndGet()
                    mockNet.sharedServerThread
                }

        override val started: StartedNode<MockNode>? get() = uncheckedCast(super.started)

        override fun start(): StartedNode<MockNode> {
            mockNet.networkParametersCopier.install(configuration.baseDirectory)
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
        override fun makeMessagingService(database: CordaPersistence, info: NodeInfo, nodeProperties: NodePropertiesStore, networkParameters: NetworkParameters): MessagingService {
            require(id >= 0) { "Node ID must be zero or positive, was passed: $id" }
            return mockNet.messagingNetwork.createNodeWithID(
                    !mockNet.threadPerNode,
                    id,
                    serverThread,
                    myNotaryIdentity,
                    configuration.myLegalName).also { runOnStop += it::stop }
        }

        fun setMessagingServiceSpy(messagingServiceSpy: MessagingServiceSpy) {
            network = messagingServiceSpy
        }

        override fun makeKeyManagementService(identityService: IdentityService, keyPairs: Set<KeyPair>, database: CordaPersistence): KeyManagementService {
            return E2ETestKeyManagementService(identityService, keyPairs)
        }

        override fun startShell() {
            //No mock shell
        }

        override fun startMessagingService(rpcOps: RPCOps) {
            // Nothing to do
        }

        // This is not thread safe, but node construction is done on a single thread, so that should always be fine
        override fun generateKeyPair(): KeyPair {
            counter = counter.add(BigInteger.ONE)
            // The StartedMockNode specifically uses EdDSA keys as they are fixed and stored in json files for some tests (e.g IRSSimulation).
            return Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, counter)
        }

        /**
         * InternalMockNetwork will ensure nodes are connected to each other. The nodes themselves
         * won't be able to tell if that happened already or not.
         */
        override fun checkNetworkMapIsInitialized() = Unit

        override fun makeTransactionVerifierService() = InMemoryTransactionVerifierService(1)

        // NodeInfo requires a non-empty addresses list and so we give it a dummy value for mock nodes.
        // The non-empty addresses check is important to have and so we tolerate the ugliness here.
        override fun myAddresses(): List<NetworkHostAndPort> = listOf(NetworkHostAndPort("mock.node", 1000))

        // Allow unit tests to modify the serialization whitelist list before the node start,
        // so they don't have to ServiceLoad test whitelists into all unit tests.
        private val _serializationWhitelists by lazy { super.serializationWhitelists.toMutableList() }
        override val serializationWhitelists: List<SerializationWhitelist>
            get() = _serializationWhitelists
        private var dbCloser: (() -> Any?)? = null
        override fun initialiseDatabasePersistence(schemaService: SchemaService,
                                                   wellKnownPartyFromX500Name: (CordaX500Name) -> Party?,
                                                   wellKnownPartyFromAnonymous: (AbstractParty) -> Party?): CordaPersistence {
            return super.initialiseDatabasePersistence(schemaService, wellKnownPartyFromX500Name, wellKnownPartyFromAnonymous).also { dbCloser = it::close }
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

    fun createUnstartedNode(parameters: InternalMockNodeParameters = InternalMockNodeParameters()): MockNode {
        return createUnstartedNode(parameters, defaultFactory)
    }

    fun <N : MockNode> createUnstartedNode(parameters: InternalMockNodeParameters = InternalMockNodeParameters(), nodeFactory: (MockNodeArgs) -> N): N {
        return createNodeImpl(parameters, nodeFactory, false)
    }

    fun createNode(parameters: InternalMockNodeParameters = InternalMockNodeParameters()): StartedNode<MockNode> {
        return createNode(parameters, defaultFactory)
    }

    /** Like the other [createNode] but takes a [nodeFactory] and propagates its [MockNode] subtype. */
    fun <N : MockNode> createNode(parameters: InternalMockNodeParameters = InternalMockNodeParameters(), nodeFactory: (MockNodeArgs) -> N): StartedNode<N> {
        return uncheckedCast(createNodeImpl(parameters, nodeFactory, true).started)!!
    }

    private fun <N : MockNode> createNodeImpl(parameters: InternalMockNodeParameters, nodeFactory: (MockNodeArgs) -> N, start: Boolean): N {
        val id = parameters.forcedID ?: nextNodeId++
        val config = mockNodeConfiguration().also {
            doReturn(baseDirectory(id).createDirectories()).whenever(it).baseDirectory
            doReturn(parameters.legalName ?: CordaX500Name("Mock Company $id", "London", "GB")).whenever(it).myLegalName
            doReturn(makeTestDataSourceProperties("node_${id}_net_$networkId")).whenever(it).dataSourceProperties
            doReturn(emptyList<SecureHash>()).whenever(it).extraNetworkMapKeys
            parameters.configOverrides(it)
        }
        val node = nodeFactory(MockNodeArgs(config, this, id, parameters.entropyRoot, parameters.version, parameters.extraCordappPackages))
        _nodes += node
        if (start) {
            node.start()
        }
        return node
    }

    fun <N : MockNode> restartNode(node: StartedNode<N>, nodeFactory: (MockNodeArgs) -> N): StartedNode<N> {
        node.internals.disableDBCloseOnStop()
        node.dispose()
        return createNode(
                InternalMockNodeParameters(legalName = node.internals.configuration.myLegalName, forcedID = node.internals.id),
                nodeFactory
        )
    }

    fun restartNode(node: StartedNode<MockNode>): StartedNode<MockNode> = restartNode(node, defaultFactory)

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
        return createNode(InternalMockNodeParameters(legalName = legalName))
    }

    @Suppress("unused") // This is used from the network visualiser tool.
    fun addressToNode(msgRecipient: MessageRecipients): MockNode {
        return when (msgRecipient) {
            is SingleMessageRecipient -> nodes.single { it.started!!.network.myAddress == msgRecipient }
            is InMemoryMessagingNetwork.DistributedServiceHandle -> {
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
        try {
            nodes.forEach { it.started?.dispose() }
        } finally {
            serializationEnv.unset() // Must execute even if other parts of this method fail.
        }
        messagingNetwork.stop()
    }

    /** Block until all scheduled activity, active flows and network activity has ceased. */
    fun waitQuiescent() {
        busyLatch.await()
    }

}

open class MessagingServiceSpy(val messagingService: MessagingService) : MessagingService by messagingService

/**
 * Attach a [MessagingServiceSpy] to the [InternalMockNetwork.MockNode] allowing interception and modification of messages.
 */
fun StartedNode<InternalMockNetwork.MockNode>.setMessagingServiceSpy(messagingServiceSpy: MessagingServiceSpy) {
    internals.setMessagingServiceSpy(messagingServiceSpy)
}

private fun mockNodeConfiguration(): NodeConfiguration {
    @DoNotImplement
    abstract class AbstractNodeConfiguration : NodeConfiguration
    return rigorousMock<AbstractNodeConfiguration>().also {
        doReturn("cordacadevpass").whenever(it).keyStorePassword
        doReturn("trustpass").whenever(it).trustStorePassword
        doReturn(emptyList<User>()).whenever(it).rpcUsers
        doReturn(null).whenever(it).notary
        doReturn(DatabaseConfig()).whenever(it).database
        doReturn("").whenever(it).emailAddress
        doReturn(null).whenever(it).jmxMonitoringHttpPort
        doReturn(true).whenever(it).devMode
        doReturn(null).whenever(it).compatibilityZoneURL
        doReturn(null).whenever(it).networkServices
        doReturn(VerifierType.InMemory).whenever(it).verifierType
        // Set to be long enough so retries don't trigger unless we override it
        doReturn(FlowTimeoutConfiguration(1.hours, 3, backoffBase = 1.0)).whenever(it).flowTimeout
        doReturn(5.seconds.toMillis()).whenever(it).additionalNodeInfoPollingFrequencyMsec
        doReturn(null).whenever(it).devModeOptions
    }
}
