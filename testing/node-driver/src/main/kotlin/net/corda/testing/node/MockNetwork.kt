package net.corda.testing.node

import com.google.common.jimfs.Jimfs
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.NodeConfiguration
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.node.internal.InternalMockMessagingService
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.newContext
import rx.Observable
import java.math.BigInteger
import java.nio.file.Path

/**
 * Immutable builder for configuring a [StartedMockNode] or an [UnstartedMockNode] via [MockNetwork.createNode] and
 * [MockNetwork.createUnstartedNode]. Kotlin users can also use the named parameters overloads of those methods which
 * are more convenient.
 *
 * @property forcedID Override the ID to use for the node. By default node ID's are generated sequentially in a
 * [MockNetwork]. Specifying the same ID is required if a node is restarted.
 * @property legalName The [CordaX500Name] name to use for the node.
 * @property entropyRoot the initial entropy value to use when generating keys. Defaults to an (insecure) random value,
 * but can be overridden to cause nodes to have stable or colliding identity/service keys.
 * @property configOverrides Add/override behaviour of the [NodeConfiguration] mock object.
 * @property extraCordappPackages Extra CorDapp packages to include for this node.
 */
@Suppress("unused")
data class MockNodeParameters @JvmOverloads constructor(
        val forcedID: Int? = null,
        val legalName: CordaX500Name? = null,
        val entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
        val configOverrides: (NodeConfiguration) -> Any? = {},
        val extraCordappPackages: List<String> = emptyList()) {
    fun withForcedID(forcedID: Int?): MockNodeParameters = copy(forcedID = forcedID)
    fun withLegalName(legalName: CordaX500Name?): MockNodeParameters = copy(legalName = legalName)
    fun withEntropyRoot(entropyRoot: BigInteger): MockNodeParameters = copy(entropyRoot = entropyRoot)
    fun withConfigOverrides(configOverrides: (NodeConfiguration) -> Any?): MockNodeParameters = copy(configOverrides = configOverrides)
    fun withExtraCordappPackages(extraCordappPackages: List<String>): MockNodeParameters = copy(extraCordappPackages = extraCordappPackages)
    fun copy(forcedID: Int?, legalName: CordaX500Name?, entropyRoot: BigInteger, configOverrides: (NodeConfiguration) -> Any?): MockNodeParameters {
        return MockNodeParameters(forcedID, legalName, entropyRoot, configOverrides)
    }
}

/**
 * Immutable builder for configuring a [MockNetwork]. Kotlin users can also use named parameters to the constructor
 * of [MockNetwork], which is more convenient.
 *
 * @property networkSendManuallyPumped If true then messages will not be routed from sender to receiver until you use
 * the [MockNetwork.runNetwork] method. This is useful for writing single-threaded unit test code that can examine the
 * state of the mock network before and after a message is sent, without races and without the receiving node immediately
 * sending a response. The default is false, so you must call runNetwork.
 * @property threadPerNode If true then each node will be run in its own thread. This can result in race conditions in
 * your code if not carefully written, but is more realistic and may help if you have flows in your app that do long
 * blocking operations. The default is false.
 * @property servicePeerAllocationStrategy How messages are load balanced in the case where a single compound identity
 * is used by multiple nodes. You rarely if ever need to change that, it's primarily of interest to people testing
 * notary code.
 * @property notarySpecs The notaries to use in the mock network. By default you get one mock notary and that is usually sufficient.
 * @property networkParameters The network parameters to be used by all the nodes. [NetworkParameters.notaries] must be
 * empty as notaries are defined by [notarySpecs].
 */
@Suppress("unused")
data class MockNetworkParameters(
        val networkSendManuallyPumped: Boolean = false,
        val threadPerNode: Boolean = false,
        val servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
        val notarySpecs: List<MockNetworkNotarySpec> = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME)),
        val networkParameters: NetworkParameters = testNetworkParameters()
) {
    fun withNetworkParameters(networkParameters: NetworkParameters): MockNetworkParameters = copy(networkParameters = networkParameters)
    fun withNetworkSendManuallyPumped(networkSendManuallyPumped: Boolean): MockNetworkParameters = copy(networkSendManuallyPumped = networkSendManuallyPumped)
    fun withThreadPerNode(threadPerNode: Boolean): MockNetworkParameters = copy(threadPerNode = threadPerNode)
    fun withServicePeerAllocationStrategy(servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy): MockNetworkParameters = copy(servicePeerAllocationStrategy = servicePeerAllocationStrategy)
    fun withNotarySpecs(notarySpecs: List<MockNetworkNotarySpec>): MockNetworkParameters = copy(notarySpecs = notarySpecs)
}

/**
 * The spec for a notary which will used by the [MockNetwork] to automatically start a notary node. This notary will
 * become part of the network parameters used by all the nodes.
 *
 * @property name The name of the notary node.
 * @property validating Boolean for whether the notary is validating or non-validating.
 */
data class MockNetworkNotarySpec(val name: CordaX500Name, val validating: Boolean = true) {
    constructor(name: CordaX500Name) : this(name, validating = true)
}

/** A class that represents an unstarted mock node for testing. */
class UnstartedMockNode private constructor(private val node: InternalMockNetwork.MockNode) {
    companion object {
        internal fun create(node: InternalMockNetwork.MockNode): UnstartedMockNode {
            return UnstartedMockNode(node)
        }
    }

    /** An identifier for the node. By default this is allocated sequentially in a [MockNetwork] **/
    val id get() : Int = node.id

    /**
     * Start the node
     *
     * @return A [StartedMockNode] object.
     */
    fun start(): StartedMockNode = StartedMockNode.create(node.start())
}

/** A class that represents a started mock node for testing. */
class StartedMockNode private constructor(private val node: StartedNode<InternalMockNetwork.MockNode>) {
    companion object {
        internal fun create(node: StartedNode<InternalMockNetwork.MockNode>): StartedMockNode {
            return StartedMockNode(node)
        }
    }

    /** The [ServiceHub] for the underlying node. **/
    val services get(): ServiceHub = node.services
    /** An identifier for the node. By default this is allocated sequentially in a [MockNetwork]. **/
    val id get(): Int = node.internals.id
    /** The [NodeInfo] for the underlying node. **/
    val info get(): NodeInfo = node.services.myInfo

    /**
     * Starts an already constructed flow. Note that you must be on the server thread to call this method.
     * @param context indicates who started the flow, see: [InvocationContext].
     */
    fun <T> startFlow(logic: FlowLogic<T>): CordaFuture<T> = node.services.startFlow(logic, node.services.newContext()).getOrThrow().resultFuture

    /** Register a flow that is initiated by another flow .**/
    fun <F : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<F>): Observable<F> = node.registerInitiatedFlow(initiatedFlowClass)

    /** Stop the node. **/
    fun stop() = node.internals.stop()

    /**
     * Delivers a single message from the internal queue. If there are no messages waiting to be delivered and block
     * is true, waits until one has been provided on a different thread via send. If block is false, the return
     * result indicates whether a message was delivered or not.
     *
     * @return the message that was processed, if any in this round.
     */
    fun pumpReceive(block: Boolean = false): InMemoryMessagingNetwork.MessageTransfer? {
        return (node.network as InternalMockMessagingService).pumpReceive(block)
    }

    /** Returns the currently live flows of type [flowClass], and their corresponding result future. */
    fun <F : FlowLogic<*>> findStateMachines(flowClass: Class<F>): List<Pair<F, CordaFuture<*>>> = node.smm.findStateMachines(flowClass)

    /**
     * Executes given statement in the scope of transaction.
     *
     * @param statement to be executed in the scope of this transaction.
     */
    // TODO sollecitom maybe remove
    fun <T> transaction(statement: () -> T): T {
        return node.database.transaction {
            statement()
        }
    }
}

/**
 * A mock node brings up a suite of in-memory services in a fast manner suitable for unit testing.
 * Components that do IO are either swapped out for mocks, or pointed to a [Jimfs] in memory filesystem or an in
 * memory H2 database instance.
 *
 * Java users can use the constructor that takes an (optional) [MockNetworkParameters] builder, which may be more
 * convenient than specifying all the defaults by hand. Please see [MockNetworkParameters] for the documentation
 * of each parameter.
 *
 * Mock network nodes require manual pumping by default: they will not run asynchronous. This means that
 * for message exchanges to take place (and associated handlers to run), you must call the [runNetwork]
 * method. If you want messages to flow automatically, use automatic pumping with a thread per node but watch out
 * for code running parallel to your unit tests: you will need to use futures correctly to ensure race-free results.
 *
 * You can get a printout of every message sent by using code like:
 *
 *    LogHelper.setLevel("+messages")
 *
 * By default a single notary node is automatically started, which forms part of the network parameters for all the nodes.
 * This node is available by calling [defaultNotaryNode].
 *
 * @property cordappPackages A [List] of cordapp packages to scan for any cordapp code, e.g. contract verification code, flows and services.
 * @property defaultParameters A [MockNetworkParameters] object which contains the same parameters as the constructor, provided
 * as a convenience for Java users.
 * @property networkSendManuallyPumped If true then messages will not be routed from sender to receiver until you use
 * the [MockNetwork.runNetwork] method. This is useful for writing single-threaded unit test code that can examine the
 * state of the mock network before and after a message is sent, without races and without the receiving node immediately
 * sending a response. The default is false, so you must call runNetwork.
 * @property threadPerNode If true then each node will be run in its own thread. This can result in race conditions in
 * your code if not carefully written, but is more realistic and may help if you have flows in your app that do long
 * blocking operations. The default is false.
 * @property servicePeerAllocationStrategy How messages are load balanced in the case where a single compound identity
 * is used by multiple nodes. You rarely if ever need to change that, it's primarily of interest to people testing
 * notary code.
 * @property notarySpecs The notaries to use in the mock network. By default you get one mock notary and that is usually sufficient.
 * @property networkParameters The network parameters to be used by all the nodes. [NetworkParameters.notaries] must be
 * empty as notaries are defined by [notarySpecs].
 */
@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
open class MockNetwork(
        val cordappPackages: List<String>,
        val defaultParameters: MockNetworkParameters = MockNetworkParameters(),
        val networkSendManuallyPumped: Boolean = defaultParameters.networkSendManuallyPumped,
        val threadPerNode: Boolean = defaultParameters.threadPerNode,
        val servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = defaultParameters.servicePeerAllocationStrategy,
        val notarySpecs: List<MockNetworkNotarySpec> = defaultParameters.notarySpecs,
        val networkParameters: NetworkParameters = defaultParameters.networkParameters) {
    @JvmOverloads
    constructor(cordappPackages: List<String>, parameters: MockNetworkParameters = MockNetworkParameters()) : this(cordappPackages, defaultParameters = parameters)

    private val internalMockNetwork: InternalMockNetwork = InternalMockNetwork(cordappPackages, defaultParameters, networkSendManuallyPumped, threadPerNode, servicePeerAllocationStrategy, notarySpecs, networkParameters)

    /** In a mock network, nodes have an incrementing integer ID. Real networks do not have this. Returns the next ID that will be used. */
    val nextNodeId get(): Int = internalMockNetwork.nextNodeId

    /**
     * Returns the single notary node on the network. Throws an exception if there are none or more than one.
     * @see notaryNodes
     */
    val defaultNotaryNode get(): StartedMockNode = StartedMockNode.create(internalMockNetwork.defaultNotaryNode)

    /**
     * Return the identity of the default notary node.
     * @see defaultNotaryNode
     */
    val defaultNotaryIdentity get(): Party = internalMockNetwork.defaultNotaryIdentity

    /**
     * Returns the list of notary nodes started by the network.
     */
    val notaryNodes get(): List<StartedMockNode> = internalMockNetwork.notaryNodes.map { StartedMockNode.create(it) }

    /** Create a started node with the given identity. **/
    fun createPartyNode(legalName: CordaX500Name? = null): StartedMockNode = StartedMockNode.create(internalMockNetwork.createPartyNode(legalName))

    /** Create a started node with the given parameters. **/
    fun createNode(parameters: MockNodeParameters = MockNodeParameters()): StartedMockNode = StartedMockNode.create(internalMockNetwork.createNode(InternalMockNodeParameters(parameters)))

    /**
     * Create a started node with the given parameters.
     *
     * @param legalName The node's legal name.
     * @param forcedID A unique identifier for the node.
     * @param entropyRoot The initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overridden to cause nodes to have stable or colliding identity/service keys.
     * @param configOverrides Add/override behaviour of the [NodeConfiguration] mock object.
     * @param extraCordappPackages Extra CorDapp packages to add for this node.
     */
    @JvmOverloads
    fun createNode(legalName: CordaX500Name? = null,
                   forcedID: Int? = null,
                   entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                   configOverrides: (NodeConfiguration) -> Any? = {},
                   extraCordappPackages: List<String> = emptyList()): StartedMockNode {
        val parameters = MockNodeParameters(forcedID, legalName, entropyRoot, configOverrides, extraCordappPackages)
        return StartedMockNode.create(internalMockNetwork.createNode(InternalMockNodeParameters(parameters)))
    }

    /** Create an unstarted node with the given parameters. **/
    fun createUnstartedNode(parameters: MockNodeParameters = MockNodeParameters()): UnstartedMockNode = UnstartedMockNode.create(internalMockNetwork.createUnstartedNode(InternalMockNodeParameters(parameters)))

    /**
     * Create an unstarted node with the given parameters.
     *
     * @param legalName The node's legal name.
     * @param forcedID A unique identifier for the node.
     * @param entropyRoot The initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overridden to cause nodes to have stable or colliding identity/service keys.
     * @param configOverrides Add/override behaviour of the [NodeConfiguration] mock object.
     * @param extraCordappPackages Extra CorDapp packages to add for this node.
     */
    @JvmOverloads
    fun createUnstartedNode(legalName: CordaX500Name? = null,
                            forcedID: Int? = null,
                            entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                            configOverrides: (NodeConfiguration) -> Any? = {},
                            extraCordappPackages: List<String> = emptyList()): UnstartedMockNode {
        val parameters = MockNodeParameters(forcedID, legalName, entropyRoot, configOverrides, extraCordappPackages)
        return UnstartedMockNode.create(internalMockNetwork.createUnstartedNode(InternalMockNodeParameters(parameters)))
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
