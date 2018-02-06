package net.corda.testing.node

import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.MessageRecipients
import net.corda.node.VersionInfo
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.MessagingService
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.setMessagingServiceSpy
import rx.Observable
import java.math.BigInteger

/**
 * Extend this class in order to intercept and modify messages passing through the [MessagingService] when using the [InMemoryMessagingNetwork].
 */
open class MessagingServiceSpy(val messagingService: MessagingService) : MessagingService by messagingService

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
        val configOverrides: (NodeConfiguration) -> Any? = {},
        val version: VersionInfo = MockServices.MOCK_VERSION_INFO) {
    fun setForcedID(forcedID: Int?) = copy(forcedID = forcedID)
    fun setLegalName(legalName: CordaX500Name?) = copy(legalName = legalName)
    fun setEntropyRoot(entropyRoot: BigInteger) = copy(entropyRoot = entropyRoot)
    fun setConfigOverrides(configOverrides: (NodeConfiguration) -> Any?) = copy(configOverrides = configOverrides)
}

class UnstartedMockNode internal constructor(private val node: InternalMockNetwork.MockNode) {
    val id get() = node.id
    val configuration get() = node.configuration
    fun start() = MockNode().initialise(node.start())
}

class MockNode {
    private lateinit var node: StartedNode<InternalMockNetwork.MockNode>
    val services get() = node.services
    val database get() = node.database
    val id get() = node.internals.id
    val configuration get() = node.internals.configuration
    val allStateMachines get() = node.smm.allStateMachines
    val checkpointStorage get() = node.checkpointStorage
    val smm get() = node.smm
    val info get() = node.services.myInfo
    val network get() = node.network

    /* Intialise via internal function rather than internal constructors, as internal constructors in Kotlin
     * are visible via the Api
     */
    internal fun initialise(node: StartedNode<InternalMockNetwork.MockNode>) : MockNode {
        this.node = node
        return this
    }

    fun setAcceptableLiveFiberCountOnStop(number: Int) {
        node.internals.acceptableLiveFiberCountOnStop = number
    }
    fun <F : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<F>) = node.registerInitiatedFlow(initiatedFlowClass)
    fun setMessagingServiceSpy(messagingServiceSpy: MessagingServiceSpy) = node.setMessagingServiceSpy(messagingServiceSpy)
    fun disableDBCloseOnStop() = node.internals.disableDBCloseOnStop()
    fun manuallyCloseDB() = node.internals.manuallyCloseDB()
    fun stop() = node.internals.stop()
    fun dispose() = node.dispose()
    fun pumpReceive(block: Boolean = false): InMemoryMessagingNetwork.MessageTransfer? {
        return (services.networkService as InMemoryMessagingNetwork.TestMessagingService).pumpReceive(block)
    }

    fun <F : FlowLogic<*>> findStateMachines(flowClass: Class<F>): List<Pair<F, CordaFuture<*>>> = node.smm.findStateMachines(flowClass)

    fun <F : FlowLogic<*>> internalRegisterFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>,
                                    flowFactory: InitiatedFlowFactory<F>,
                                    initiatedFlowClass: Class<F>,
                                    track: Boolean): Observable<F> = node.internalRegisterFlowFactory(initiatingFlowClass, flowFactory, initiatedFlowClass, track)
}

data class MockNetworkNotarySpec(val name: CordaX500Name, val validating: Boolean = true) {
    constructor(name: CordaX500Name) : this(name, validating = true)
}

/** Helper builder for configuring a [InternalMockNetwork] from Java. */
@Suppress("unused")
data class MockNetworkParameters(
        val networkSendManuallyPumped: Boolean = false,
        val threadPerNode: Boolean = false,
        val servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
        val initialiseSerialization: Boolean = true,
        val notarySpecs: List<MockNetworkNotarySpec> = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME))) {
    fun setNetworkSendManuallyPumped(networkSendManuallyPumped: Boolean) = copy(networkSendManuallyPumped = networkSendManuallyPumped)
    fun setThreadPerNode(threadPerNode: Boolean) = copy(threadPerNode = threadPerNode)
    fun setServicePeerAllocationStrategy(servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy) = copy(servicePeerAllocationStrategy = servicePeerAllocationStrategy)
    fun setInitialiseSerialization(initialiseSerialization: Boolean) = copy(initialiseSerialization = initialiseSerialization)
    fun setNotarySpecs(notarySpecs: List<MockNetworkNotarySpec>) = copy(notarySpecs = notarySpecs)
}

class MockNetwork(
        val cordappPackages: List<String>,
        val defaultParameters: MockNetworkParameters = MockNetworkParameters(),
        val networkSendManuallyPumped: Boolean = defaultParameters.networkSendManuallyPumped,
        val threadPerNode: Boolean = defaultParameters.threadPerNode,
        val servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = defaultParameters.servicePeerAllocationStrategy,
        val initialiseSerialization: Boolean = defaultParameters.initialiseSerialization,
        val notarySpecs: List<MockNetworkNotarySpec> = defaultParameters.notarySpecs) {
    @JvmOverloads
    constructor(cordappPackages: List<String>, parameters: MockNetworkParameters = MockNetworkParameters()) : this(cordappPackages, defaultParameters = parameters)

    private val internalMockNetwork: InternalMockNetwork = InternalMockNetwork(cordappPackages, defaultParameters, networkSendManuallyPumped, threadPerNode, servicePeerAllocationStrategy, initialiseSerialization, notarySpecs)
    val defaultNotaryNode get() = MockNode().initialise(internalMockNetwork.defaultNotaryNode)
    val defaultNotaryIdentity get() = internalMockNetwork.defaultNotaryIdentity
    val messagingNetwork get() = internalMockNetwork.messagingNetwork
    val notaryNodes get() = internalMockNetwork.notaryNodes.map { MockNode().initialise(it) }
    val nextNodeId get() = internalMockNetwork.nextNodeId

    fun createPartyNode(legalName: CordaX500Name? = null) = MockNode().initialise(internalMockNetwork.createPartyNode(legalName))
    fun createNode(parameters: MockNodeParameters = MockNodeParameters()) = MockNode().initialise(internalMockNetwork.createNode(parameters))
    fun createUnstartedNode(parameters: MockNodeParameters = MockNodeParameters()) = UnstartedMockNode(internalMockNetwork.createUnstartedNode(parameters))
    fun startNodes() = internalMockNetwork.startNodes()
    fun stopNodes() = internalMockNetwork.stopNodes()
    fun waitQuiescent() = internalMockNetwork.waitQuiescent()
    @JvmOverloads
    fun runNetwork(rounds: Int = -1) = internalMockNetwork.runNetwork(rounds)
    fun baseDirectory(nodeId: Int) = internalMockNetwork.baseDirectory(nodeId)
}