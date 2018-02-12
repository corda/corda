package net.corda.testing.node

import com.google.common.jimfs.Jimfs
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.node.VersionInfo
import net.corda.node.internal.StartedNode
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.MessagingService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.setMessagingServiceSpy
import rx.Observable
import java.math.BigInteger
import java.nio.file.Path

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
    fun setForcedID(forcedID: Int?): MockNodeParameters = copy(forcedID = forcedID)
    fun setLegalName(legalName: CordaX500Name?): MockNodeParameters = copy(legalName = legalName)
    fun setEntropyRoot(entropyRoot: BigInteger): MockNodeParameters = copy(entropyRoot = entropyRoot)
    fun setConfigOverrides(configOverrides: (NodeConfiguration) -> Any?): MockNodeParameters = copy(configOverrides = configOverrides)
}

/** Helper builder for configuring a [InternalMockNetwork] from Java. */
@Suppress("unused")
data class MockNetworkParameters(
        val networkSendManuallyPumped: Boolean = false,
        val threadPerNode: Boolean = false,
        val servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
        val initialiseSerialization: Boolean = true,
        val notarySpecs: List<MockNetworkNotarySpec> = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME)),
        val maxTransactionSize: Int = Int.MAX_VALUE) {
    fun setNetworkSendManuallyPumped(networkSendManuallyPumped: Boolean): MockNetworkParameters = copy(networkSendManuallyPumped = networkSendManuallyPumped)
    fun setThreadPerNode(threadPerNode: Boolean): MockNetworkParameters = copy(threadPerNode = threadPerNode)
    fun setServicePeerAllocationStrategy(servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy): MockNetworkParameters = copy(servicePeerAllocationStrategy = servicePeerAllocationStrategy)
    fun setInitialiseSerialization(initialiseSerialization: Boolean): MockNetworkParameters = copy(initialiseSerialization = initialiseSerialization)
    fun setNotarySpecs(notarySpecs: List<MockNetworkNotarySpec>): MockNetworkParameters = copy(notarySpecs = notarySpecs)
    fun setMaxTransactionSize(maxTransactionSize: Int): MockNetworkParameters = copy(maxTransactionSize = maxTransactionSize)
}

/** Represents a node configuration for injection via [MockNetworkParameters] **/
data class MockNetworkNotarySpec(val name: CordaX500Name, val validating: Boolean = true) {
    constructor(name: CordaX500Name) : this(name, validating = true)
}

/** A class that represents an unstarted mock node for testing. **/
class UnstartedMockNode private constructor(private val node: InternalMockNetwork.MockNode) {
    companion object {
        internal fun create(node: InternalMockNetwork.MockNode): UnstartedMockNode {
            return UnstartedMockNode(node)
        }
    }

    val id get() : Int = node.id
    /** Start the node **/
    fun start() = StartedMockNode.create(node.start())
}

/** A class that represents a started mock node for testing. **/
class StartedMockNode private constructor(private val node: StartedNode<InternalMockNetwork.MockNode>) {
    companion object {
        internal fun create(node: StartedNode<InternalMockNetwork.MockNode>): StartedMockNode {
            return StartedMockNode(node)
        }
    }

    val services get() : StartedNodeServices = node.services
    val database get() : CordaPersistence = node.database
    val id get() : Int = node.internals.id
    val info get() : NodeInfo = node.services.myInfo
    val network get() : MessagingService = node.network
    /** Register a flow that is initiated by another flow **/
    fun <F : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<F>): Observable<F> = node.registerInitiatedFlow(initiatedFlowClass)

    /**
     * Attach a [MessagingServiceSpy] to the [InternalMockNetwork.MockNode] allowing
     * interception and modification of messages.
     */
    fun setMessagingServiceSpy(messagingServiceSpy: MessagingServiceSpy) = node.setMessagingServiceSpy(messagingServiceSpy)

    /** Stop the node **/
    fun stop() = node.internals.stop()

    /** Receive a message from the queue. */
    fun pumpReceive(block: Boolean = false): InMemoryMessagingNetwork.MessageTransfer? {
        return (services.networkService as InMemoryMessagingNetwork.TestMessagingService).pumpReceive(block)
    }

    /** Returns the currently live flows of type [flowClass], and their corresponding result future. */
    fun <F : FlowLogic<*>> findStateMachines(flowClass: Class<F>): List<Pair<F, CordaFuture<*>>> = node.smm.findStateMachines(flowClass)
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
 *
 * By default a single notary node is automatically started, which forms part of the network parameters for all the nodes.
 * This node is available by calling [defaultNotaryNode].
 */
open class MockNetwork(
        val cordappPackages: List<String>,
        val defaultParameters: MockNetworkParameters = MockNetworkParameters(),
        val networkSendManuallyPumped: Boolean = defaultParameters.networkSendManuallyPumped,
        val threadPerNode: Boolean = defaultParameters.threadPerNode,
        val servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = defaultParameters.servicePeerAllocationStrategy,
        val initialiseSerialization: Boolean = defaultParameters.initialiseSerialization,
        val notarySpecs: List<MockNetworkNotarySpec> = defaultParameters.notarySpecs,
        val maxTransactionSize: Int = defaultParameters.maxTransactionSize) {
    @JvmOverloads
    constructor(cordappPackages: List<String>, parameters: MockNetworkParameters = MockNetworkParameters()) : this(cordappPackages, defaultParameters = parameters)

    private val internalMockNetwork: InternalMockNetwork = InternalMockNetwork(cordappPackages, defaultParameters, networkSendManuallyPumped, threadPerNode, servicePeerAllocationStrategy, initialiseSerialization, notarySpecs, maxTransactionSize)
    val defaultNotaryNode get() : StartedMockNode = StartedMockNode.create(internalMockNetwork.defaultNotaryNode)
    val defaultNotaryIdentity get() : Party = internalMockNetwork.defaultNotaryIdentity
    val notaryNodes get() : List<StartedMockNode> = internalMockNetwork.notaryNodes.map { StartedMockNode.create(it) }
    val nextNodeId get() : Int = internalMockNetwork.nextNodeId

    /** Create a started node with the given identity. **/
    fun createPartyNode(legalName: CordaX500Name? = null): StartedMockNode = StartedMockNode.create(internalMockNetwork.createPartyNode(legalName))

    /** Create a started node with the given parameters. **/
    fun createNode(parameters: MockNodeParameters = MockNodeParameters()): StartedMockNode = StartedMockNode.create(internalMockNetwork.createNode(parameters))

    /** Create a started node with the given parameters.
     * @param legalName the node's legal name.
     * @param forcedID a unique identifier for the node.
     * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overridden to cause nodes to have stable or colliding identity/service keys.
     * @param configOverrides add/override behaviour of the [NodeConfiguration] mock object.
     * @param version the mock node's platform, release, revision and vendor versions.
     */
    @JvmOverloads
    fun createNode(legalName: CordaX500Name? = null,
                   forcedID: Int? = null,
                   entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                   configOverrides: (NodeConfiguration) -> Any? = {},
                   version: VersionInfo = MockServices.MOCK_VERSION_INFO): StartedMockNode {
        val parameters = MockNodeParameters(forcedID, legalName, entropyRoot, configOverrides, version)
        return StartedMockNode.create(internalMockNetwork.createNode(parameters))
    }

    /** Create an unstarted node with the given parameters. **/
    fun createUnstartedNode(parameters: MockNodeParameters = MockNodeParameters()): UnstartedMockNode = UnstartedMockNode.create(internalMockNetwork.createUnstartedNode(parameters))

    /** Create an unstarted node with the given parameters.
     * @param legalName the node's legal name.
     * @param forcedID a unique identifier for the node.
     * @param entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overridden to cause nodes to have stable or colliding identity/service keys.
     * @param configOverrides add/override behaviour of the [NodeConfiguration] mock object.
     * @param version the mock node's platform, release, revision and vendor versions.
     */
    @JvmOverloads
    fun createUnstartedNode(legalName: CordaX500Name? = null,
                            forcedID: Int? = null,
                            entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                            configOverrides: (NodeConfiguration) -> Any? = {},
                            version: VersionInfo = MockServices.MOCK_VERSION_INFO): UnstartedMockNode {
        val parameters = MockNodeParameters(forcedID, legalName, entropyRoot, configOverrides, version)
        return UnstartedMockNode.create(internalMockNetwork.createUnstartedNode(parameters))
    }

    /** Start all nodes that aren't already started. **/
    fun startNodes() = internalMockNetwork.startNodes()

    /** Stop all nodes. **/
    fun stopNodes() = internalMockNetwork.stopNodes()

    /** Block until all scheduled activity, active flows and network activity has ceased. **/
    fun waitQuiescent() = internalMockNetwork.waitQuiescent()

    /**
     * Asks every node in order to process any queued up inbound messages. This may in turn result in nodes
     * sending more messages to each other, thus, a typical usage is to call runNetwork with the [rounds]
     * parameter set to -1 (the default) which simply runs as many rounds as necessary to result in network
     * stability (no nodes sent any messages in the last round).
     */
    @JvmOverloads
    fun runNetwork(rounds: Int = -1) = internalMockNetwork.runNetwork(rounds)

    /** Get the base directory for the given node id. **/
    fun baseDirectory(nodeId: Int): Path = internalMockNetwork.baseDirectory(nodeId)
}