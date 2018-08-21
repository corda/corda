/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node.internal

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.DoNotImplement
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.*
import net.corda.core.internal.notary.NotaryService
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.hours
import net.corda.core.utilities.seconds
import net.corda.node.VersionInfo
import net.corda.node.cordapp.CordappLoader
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.config.*
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.node.services.keys.KeyManagementServiceInternal
import net.corda.node.services.messaging.Message
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.BFTSMaRt
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.TestCorDapp
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.setGlobalSerialization
import net.corda.testing.internal.testThreadFactory
import net.corda.testing.node.*
import org.apache.activemq.artemis.utils.ReusableLatch
import org.apache.sshd.common.util.security.SecurityUtils
import rx.Observable
import rx.Scheduler
import rx.internal.schedulers.CachedThreadScheduler
import java.math.BigInteger
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.security.PublicKey
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

val MOCK_VERSION_INFO = VersionInfo(1, "Mock release", "Mock revision", "Mock Vendor")

data class MockNodeArgs(
        val config: NodeConfiguration,
        val network: InternalMockNetwork,
        val id: Int,
        val entropyRoot: BigInteger,
        val version: VersionInfo = MOCK_VERSION_INFO
)

// TODO We don't need a parameters object as this is internal only
data class InternalMockNodeParameters(
        val forcedID: Int? = null,
        val legalName: CordaX500Name? = null,
        val entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
        val configOverrides: (NodeConfiguration) -> Any? = {},
        val version: VersionInfo = MOCK_VERSION_INFO,
        val additionalCordapps: Set<TestCorDapp>? = null) {
    constructor(mockNodeParameters: MockNodeParameters) : this(
            mockNodeParameters.forcedID,
            mockNodeParameters.legalName,
            mockNodeParameters.entropyRoot,
            mockNodeParameters.configOverrides,
            MOCK_VERSION_INFO,
            mockNodeParameters.additionalCordapps
    )
}

/**
 * A [StartedNode] which exposes its internal [InternalMockNetwork.MockNode] for testing.
 */
interface TestStartedNode {
    val internals: InternalMockNetwork.MockNode
    val info: NodeInfo
    val services: StartedNodeServices
    val smm: StateMachineManager
    val attachments: NodeAttachmentService
    val rpcOps: CordaRPCOps
    val network: MockNodeMessagingService
    val database: CordaPersistence
    val notaryService: NotaryService?

    fun dispose() = internals.stop()

    fun pumpReceive(block: Boolean = false): InMemoryMessagingNetwork.MessageTransfer? {
        return network.pumpReceive(block)
    }

    /**
     * Attach a [MessagingServiceSpy] to the [InternalMockNetwork.MockNode] allowing interception and modification of messages.
     */
    fun setMessagingServiceSpy(spy: MessagingServiceSpy) {
        internals.setMessagingServiceSpy(spy)
    }

    /**
     * Use this method to register your initiated flows in your tests. This is automatically done by the node when it
     * starts up for all [FlowLogic] classes it finds which are annotated with [InitiatedBy].
     * @return An [Observable] of the initiated flows started by counterparties.
     */
    fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>): Observable<T>

    fun <F : FlowLogic<*>> registerFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>,
                                               flowFactory: InitiatedFlowFactory<F>,
                                               initiatedFlowClass: Class<F>,
                                               track: Boolean): Observable<F>
}

open class InternalMockNetwork(defaultParameters: MockNetworkParameters = MockNetworkParameters(),
                               val networkSendManuallyPumped: Boolean = defaultParameters.networkSendManuallyPumped,
                               val threadPerNode: Boolean = defaultParameters.threadPerNode,
                               servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = defaultParameters.servicePeerAllocationStrategy,
                               val notarySpecs: List<MockNetworkNotarySpec> = defaultParameters.notarySpecs,
                               val testDirectory: Path = Paths.get("build", getTimestampAsDirectoryName()),
                               val networkParameters: NetworkParameters = testNetworkParameters(),
                               val defaultFactory: (MockNodeArgs, CordappLoader?) -> MockNode = { args, cordappLoader -> cordappLoader?.let { MockNode(args, it) } ?: MockNode(args) },
                               val cordappsForAllNodes: Set<TestCorDapp> = emptySet(),
                               val autoVisibleNodes: Boolean = true) : AutoCloseable {
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

    private val sharedCorDappsDirectories: Iterable<Path> by lazy {
        TestCordappDirectories.cached(cordappsForAllNodes)
    }

    /** A read only view of the current set of nodes. */
    val nodes: List<MockNode> get() = _nodes

    /**
     * Returns the list of nodes started by the network. Each notary specified when the network is constructed ([notarySpecs]
     * parameter) maps 1:1 to the notaries returned by this list.
     */
    val notaryNodes: List<TestStartedNode>

    /**
     * Returns the single notary node on the network. Throws if there are none or more than one.
     * @see notaryNodes
     */
    val defaultNotaryNode: TestStartedNode
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
            // Notary nodes need a platform version >= network min platform version.
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
    internal open fun createNotaries(): List<TestStartedNode> {
        val version = VersionInfo(networkParameters.minimumPlatformVersion, "Mock release", "Mock revision", "Mock Vendor")
        return notarySpecs.map { (name, validating) ->
            createNode(InternalMockNodeParameters(
                    legalName = name,
                    configOverrides = { doReturn(NotaryConfig(validating)).whenever(it).notary },
                    version = version
            ))
        }
    }

    private fun getServerThread(id: Int): ServiceAffinityExecutor {
        return if (threadPerNode) {
            ServiceAffinityExecutor("Mock node $id thread", 1)
        } else {
            sharedUserCount.incrementAndGet()
            sharedServerThread
        }
    }

    open class MockNode(args: MockNodeArgs, cordappLoader: CordappLoader = JarScanningCordappLoader.fromDirectories(args.config.cordappDirectories)) : AbstractNode<TestStartedNode>(
            args.config,
            TestClock(Clock.systemUTC()),
            args.version,
            cordappLoader,
            args.network.getServerThread(args.id),
            args.network.busyLatch
    ) {
        companion object {
            private val staticLog = contextLogger()
        }

        /** The actual [TestStartedNode] implementation created by this node */
        private class TestStartedNodeImpl(
                override val internals: MockNode,
                override val attachments: NodeAttachmentService,
                override val network: MockNodeMessagingService,
                override val services: StartedNodeServices,
                override val info: NodeInfo,
                override val smm: StateMachineManager,
                override val database: CordaPersistence,
                override val rpcOps: CordaRPCOps,
                override val notaryService: NotaryService?) : TestStartedNode {

            override fun <F : FlowLogic<*>> registerFlowFactory(
                    initiatingFlowClass: Class<out FlowLogic<*>>,
                    flowFactory: InitiatedFlowFactory<F>,
                    initiatedFlowClass: Class<F>,
                    track: Boolean): Observable<F> =
                    internals.internalRegisterFlowFactory(smm, initiatingFlowClass, flowFactory, initiatedFlowClass, track)

            override fun dispose() = internals.stop()

            override fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>): Observable<T> =
                    internals.registerInitiatedFlow(smm, initiatedFlowClass)
        }

        val mockNet = args.network
        val id = args.id
        init {
            require(id >= 0) { "Node ID must be zero or positive, was passed: $id" }
        }
        private val entropyRoot = args.entropyRoot
        var counter = entropyRoot
        override val log get() = staticLog
        override val transactionVerifierWorkerCount: Int get() = 1

        private var _rxIoScheduler: Scheduler? = null
        override val rxIoScheduler: Scheduler
            get() {
                return _rxIoScheduler ?: CachedThreadScheduler(testThreadFactory()).also {
                    runOnStop += it::shutdown
                    _rxIoScheduler = it
                }
            }

        override val started: TestStartedNode? get() = super.started

        override fun createStartedNode(nodeInfo: NodeInfo, rpcOps: CordaRPCOps, notaryService: NotaryService?): TestStartedNode {
            return TestStartedNodeImpl(
                    this,
                    attachments,
                    network as MockNodeMessagingService,
                    object : StartedNodeServices, ServiceHubInternal by services, FlowStarter by flowStarter { },
                    nodeInfo,
                    smm,
                    database,
                    rpcOps,
                    notaryService
            )
        }

        override fun start(): TestStartedNode {
            mockNet.networkParametersCopier.install(configuration.baseDirectory)
            return super.start().also(::advertiseNodeToNetwork)
        }

        private fun advertiseNodeToNetwork(newNode: TestStartedNode) {
            if (!mockNet.autoVisibleNodes) return
            mockNet.nodes
                    .mapNotNull { it.started }
                    .forEach { existingNode ->
                        newNode.services.networkMapCache.addNode(existingNode.info)
                        existingNode.services.networkMapCache.addNode(newNode.info)
                    }
        }

        override fun makeMessagingService(): MockNodeMessagingService {
            return MockNodeMessagingService(configuration, serverThread).closeOnStop()
        }

        override fun startMessagingService(rpcOps: RPCOps,
                                           nodeInfo: NodeInfo,
                                           myNotaryIdentity: PartyAndCertificate?,
                                           networkParameters: NetworkParameters) {
            (network as MockNodeMessagingService).start(mockNet.messagingNetwork, !mockNet.threadPerNode, id, myNotaryIdentity)
        }

        fun setMessagingServiceSpy(spy: MessagingServiceSpy) {
            spy._messagingService = network
            (network as MockNodeMessagingService).spy = spy
        }

        override fun makeKeyManagementService(identityService: PersistentIdentityService): KeyManagementServiceInternal {
            return E2ETestKeyManagementService(identityService)
        }

        override fun startShell() {
            //No mock shell
        }

        // This is not thread safe, but node construction is done on a single thread, so that should always be fine
        override fun generateKeyPair(): KeyPair {
            counter = counter.add(BigInteger.ONE)
            // The StartedMockNode specifically uses EdDSA keys as they are fixed and stored in json files for some tests (e.g IRSSimulation).
            return Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, counter)
        }

        // NodeInfo requires a non-empty addresses list and so we give it a dummy value for mock nodes.
        // The non-empty addresses check is important to have and so we tolerate the ugliness here.
        override fun myAddresses(): List<NetworkHostAndPort> = listOf(NetworkHostAndPort("mock.node", 1000))

        // Allow unit tests to modify the serialization whitelist list before the node start,
        // so they don't have to ServiceLoad test whitelists into all unit tests.
        private val _serializationWhitelists by lazy { super.serializationWhitelists.toMutableList() }
        override val serializationWhitelists: List<SerializationWhitelist>
            get() = _serializationWhitelists
        private var dbCloser: (() -> Any?)? = null

        override fun startDatabase() {
            super.startDatabase()
            dbCloser = database::close
            runOnStop += dbCloser!!
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

    fun createUnstartedNode(parameters: InternalMockNodeParameters = InternalMockNodeParameters(), nodeFactory: (MockNodeArgs, CordappLoader?) -> MockNode): MockNode {
        return createNodeImpl(parameters, nodeFactory, false)
    }

    fun createNode(parameters: InternalMockNodeParameters = InternalMockNodeParameters()): TestStartedNode {
        return createNode(parameters, defaultFactory)
    }

    /** Like the other [createNode] but takes a [nodeFactory] and propagates its [MockNode] subtype. */
    fun createNode(parameters: InternalMockNodeParameters = InternalMockNodeParameters(), nodeFactory: (MockNodeArgs, CordappLoader?) -> MockNode): TestStartedNode {
        return uncheckedCast(createNodeImpl(parameters, nodeFactory, true).started)!!
    }

    private fun createNodeImpl(parameters: InternalMockNodeParameters, nodeFactory: (MockNodeArgs, CordappLoader?) -> MockNode, start: Boolean): MockNode {
        val id = parameters.forcedID ?: nextNodeId++
        val config = mockNodeConfiguration().also {
            doReturn(baseDirectory(id).createDirectories()).whenever(it).baseDirectory
            doReturn(parameters.legalName ?: CordaX500Name("Mock Company $id", "London", "GB")).whenever(it).myLegalName
            doReturn(makeInternalTestDataSourceProperties("node_$id", "net_$networkId")).whenever(it).dataSourceProperties
            doReturn(makeTestDatabaseProperties("node_$id")).whenever(it).database
            doReturn(emptyList<SecureHash>()).whenever(it).extraNetworkMapKeys
            parameters.configOverrides(it)
        }

        val cordapps: Set<TestCorDapp> = parameters.additionalCordapps ?: emptySet()
        val cordappDirectories = sharedCorDappsDirectories + TestCordappDirectories.cached(cordapps)
        doReturn(cordappDirectories).whenever(config).cordappDirectories

        val node = nodeFactory(MockNodeArgs(config, this, id, parameters.entropyRoot, parameters.version), JarScanningCordappLoader.fromDirectories(cordappDirectories))
        _nodes += node
        if (start) {
            node.start()
        }
        return node
    }

    fun restartNode(node: TestStartedNode, nodeFactory: (MockNodeArgs, CordappLoader?) -> MockNode): TestStartedNode {
        node.internals.disableDBCloseOnStop()
        node.dispose()
        return createNode(
                InternalMockNodeParameters(legalName = node.internals.configuration.myLegalName, forcedID = node.internals.id),
                nodeFactory
        )
    }

    fun restartNode(node: TestStartedNode): TestStartedNode = restartNode(node, defaultFactory)

    fun baseDirectory(nodeId: Int): Path = testDirectory / "nodes/$nodeId"

    /**
     * Asks every node in order to process any queued up inbound messages. This may in turn result in nodes
     * sending more messages to each other, thus, a typical usage is to call runNetwork with the [rounds]
     * parameter set to -1 (the default) which simply runs as many rounds as necessary to result in network
     * stability (no nodes sent any messages in the last round).
     */
    @JvmOverloads
    fun runNetwork(rounds: Int = -1) {
        check(!networkSendManuallyPumped) { "MockNetwork.runNetwork() should only be used when networkSendManuallyPumped == false. " +
                "You can use MockNetwork.waitQuiescent() to wait for all the nodes to process all the messages on their queues instead." }
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
    fun createPartyNode(legalName: CordaX500Name? = null): TestStartedNode {
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

    override fun close() = stopNodes()
}

abstract class MessagingServiceSpy {
    internal var _messagingService: MessagingService? = null
        set(value) {
            check(field == null) { "Spy has already been attached to a node" }
            field = value
        }
    val messagingService: MessagingService get() = checkNotNull(_messagingService) { "Spy has not been attached to a node" }

    abstract fun send(message: Message, target: MessageRecipients, sequenceKey: Any)
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
        doReturn(EnterpriseConfiguration(
                mutualExclusionConfiguration = MutualExclusionConfiguration(false, "", 20000, 40000),
                useMultiThreadedSMM = false
        )).whenever(it).enterpriseConfiguration
    }
}
