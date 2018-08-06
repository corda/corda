package net.corda.testing.node

import com.google.common.jimfs.Jimfs
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.toFuture
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.services.config.NodeConfiguration
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.TestCorDapp
import net.corda.testing.node.internal.*
import rx.Observable
import java.math.BigInteger
import java.nio.file.Path
import java.util.concurrent.Future

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
 * @property additionalCordapps [TestCorDapp]s that will be added to this node in addition to the ones shared by all nodes, which get specified at [MockNetwork] level.
 */
@Suppress("unused")
data class MockNodeParameters constructor(
        val forcedID: Int? = null,
        val legalName: CordaX500Name? = null,
        val entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
        val configOverrides: (NodeConfiguration) -> Any? = {},
        val additionalCordapps: Set<TestCorDapp>) {

    @JvmOverloads
    constructor(
            forcedID: Int? = null,
            legalName: CordaX500Name? = null,
            entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
            configOverrides: (NodeConfiguration) -> Any? = {},
            extraCordappPackages: List<String> = emptyList()
    ) : this(forcedID, legalName, entropyRoot, configOverrides, additionalCordapps = cordappsForPackages(extraCordappPackages))

    fun withForcedID(forcedID: Int?): MockNodeParameters = copy(forcedID = forcedID)
    fun withLegalName(legalName: CordaX500Name?): MockNodeParameters = copy(legalName = legalName)
    fun withEntropyRoot(entropyRoot: BigInteger): MockNodeParameters = copy(entropyRoot = entropyRoot)
    fun withConfigOverrides(configOverrides: (NodeConfiguration) -> Any?): MockNodeParameters = copy(configOverrides = configOverrides)
    fun withExtraCordappPackages(extraCordappPackages: List<String>): MockNodeParameters = copy(forcedID = forcedID, legalName = legalName, entropyRoot = entropyRoot, configOverrides = configOverrides, extraCordappPackages = extraCordappPackages)
    fun withAdditionalCordapps(additionalCordapps: Set<TestCorDapp>): MockNodeParameters = copy(additionalCordapps = additionalCordapps)
    fun copy(forcedID: Int?, legalName: CordaX500Name?, entropyRoot: BigInteger, configOverrides: (NodeConfiguration) -> Any?): MockNodeParameters {
        return MockNodeParameters(forcedID, legalName, entropyRoot, configOverrides, additionalCordapps = emptySet())
    }
    fun copy(forcedID: Int?, legalName: CordaX500Name?, entropyRoot: BigInteger, configOverrides: (NodeConfiguration) -> Any?, extraCordappPackages: List<String> = emptyList()): MockNodeParameters {
        return MockNodeParameters(forcedID, legalName, entropyRoot, configOverrides, extraCordappPackages)
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

    /**
     * A [StartedMockNode] object for this running node.
     * @throws [IllegalStateException] if the node is not running yet.
     */
    val started: StartedMockNode
        get() = StartedMockNode.create(node.started ?: throw IllegalStateException("Node ID=$id is not running"))

    /**
     * Whether this node has been started yet.
     */
    val isStarted: Boolean get() = node.started != null
}

/** A class that represents a started mock node for testing. */
class StartedMockNode private constructor(private val node: TestStartedNode) {
    companion object {
        internal fun create(node: TestStartedNode): StartedMockNode {
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
        return node.network.pumpReceive(block)
    }

    /** Returns the currently live flows of type [flowClass], and their corresponding result future. */
    fun <F : FlowLogic<*>> findStateMachines(flowClass: Class<F>): List<Pair<F, CordaFuture<*>>> = node.smm.findStateMachines(flowClass)

    /**
     * Executes given statement in the scope of transaction.
     *
     * @param statement to be executed in the scope of this transaction.
     */
    fun <T> transaction(statement: () -> T): T {
        return node.database.transaction {
            statement()
        }
    }

    /**
     * Register an [InitiatedFlowFactory], to control relationship between initiating and receiving flow classes
     * explicitly on a node-by-node basis. This is used when we want to manually specify that a particular initiating
     * flow class will have a particular responder.
     *
     * An [ResponderFlowFactory] is responsible for converting a [FlowSession] into the [FlowLogic] that will respond
     * to the initiated flow. The registry records one responder type, and hence one factory, for each initiator flow
     * type. If a factory is already registered for the type, it is overwritten in the registry when a new factory is
     * registered.
     *
     * Note that this change affects _only_ the node on which this method is called, and not the entire network.
     *
     * @property initiatingFlowClass The [FlowLogic]-inheriting class to register a new responder for.
     * @property flowFactory The flow factory that will create the responding flow.
     * @property responderFlowClass The class of the responder flow.
     * @return A [CordaFuture] that will complete the first time the responding flow is created.
     */
    fun <F : FlowLogic<*>> registerResponderFlow(initiatingFlowClass: Class<out FlowLogic<*>>,
                                                 flowFactory: ResponderFlowFactory<F>,
                                                 responderFlowClass: Class<F>): CordaFuture<F> =
            node.registerFlowFactory(
                    initiatingFlowClass,
                    InitiatedFlowFactory.CorDapp(flowVersion = 0, appName = "", factory = flowFactory::invoke),
                    responderFlowClass, true)
                    .toFuture()
}

/**
 * Responsible for converting a [FlowSession] into the [FlowLogic] that will respond to an initiated flow.
 *
 * @param F The [FlowLogic]-inherited type of the responder class this factory creates.
 */
@FunctionalInterface
interface ResponderFlowFactory<F : FlowLogic<*>> {
    /**
     * Given the provided [FlowSession], create a responder [FlowLogic] of the desired type.
     *
     * @param flowSession The [FlowSession] to use to create the responder flow object.
     * @return The constructed responder flow object.
     */
    fun invoke(flowSession: FlowSession): F
}

/**
 * Kotlin-only utility function using a reified type parameter and a lambda parameter to simplify the
 * [InitiatedFlowFactory.registerFlowFactory] function.
 *
 * @param F The [FlowLogic]-inherited type of the responder to register.
 * @property initiatingFlowClass The [FlowLogic]-inheriting class to register a new responder for.
 * @property flowFactory A lambda converting a [FlowSession] into an instance of the responder class [F].
 * @return A [CordaFuture] that will complete the first time the responding flow is created.
 */
inline fun <reified F : FlowLogic<*>> StartedMockNode.registerResponderFlow(
        initiatingFlowClass: Class<out FlowLogic<*>>,
        noinline flowFactory: (FlowSession) -> F): Future<F> =
        registerResponderFlow(
                initiatingFlowClass,
                object : ResponderFlowFactory<F> {
                    override fun invoke(flowSession: FlowSession) = flowFactory(flowSession)
                },
                F::class.java)

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
 * @property cordappsForAllNodes [TestCorDapp]s that will be added to each node started by the [MockNetwork].
 */
@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
open class MockNetwork(
        val cordappPackages: List<String>,
        val defaultParameters: MockNetworkParameters = MockNetworkParameters(),
        val networkSendManuallyPumped: Boolean = defaultParameters.networkSendManuallyPumped,
        val threadPerNode: Boolean = defaultParameters.threadPerNode,
        val servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = defaultParameters.servicePeerAllocationStrategy,
        val notarySpecs: List<MockNetworkNotarySpec> = defaultParameters.notarySpecs,
        val networkParameters: NetworkParameters = defaultParameters.networkParameters,
        val cordappsForAllNodes: Set<TestCorDapp> = cordappsForPackages(cordappPackages)) {

    @JvmOverloads
    constructor(cordappPackages: List<String>, parameters: MockNetworkParameters = MockNetworkParameters()) : this(cordappPackages, defaultParameters = parameters)

    constructor(
             cordappPackages: List<String>,
             defaultParameters: MockNetworkParameters = MockNetworkParameters(),
             networkSendManuallyPumped: Boolean = defaultParameters.networkSendManuallyPumped,
             threadPerNode: Boolean = defaultParameters.threadPerNode,
             servicePeerAllocationStrategy: InMemoryMessagingNetwork.ServicePeerAllocationStrategy = defaultParameters.servicePeerAllocationStrategy,
             notarySpecs: List<MockNetworkNotarySpec> = defaultParameters.notarySpecs,
             networkParameters: NetworkParameters = defaultParameters.networkParameters
    ) : this(emptyList(), defaultParameters, networkSendManuallyPumped, threadPerNode, servicePeerAllocationStrategy, notarySpecs, networkParameters, cordappsForAllNodes = cordappsForPackages(cordappPackages))

    private val internalMockNetwork: InternalMockNetwork = InternalMockNetwork(defaultParameters, networkSendManuallyPumped, threadPerNode, servicePeerAllocationStrategy, notarySpecs, networkParameters = networkParameters, cordappsForAllNodes = cordappsForAllNodes)

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

        return createNode(legalName, forcedID, entropyRoot, configOverrides, cordappsForPackages(extraCordappPackages))
    }

    /**
     * Create a started node with the given parameters.
     *
     * @param legalName The node's legal name.
     * @param forcedID A unique identifier for the node.
     * @param entropyRoot The initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overridden to cause nodes to have stable or colliding identity/service keys.
     * @param configOverrides Add/override behaviour of the [NodeConfiguration] mock object.
     * @param additionalCordapps Additional [TestCorDapp]s that this node will have available, in addition to the ones common to all nodes managed by the [MockNetwork].
     */
    fun createNode(legalName: CordaX500Name? = null,
                   forcedID: Int? = null,
                   entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                   configOverrides: (NodeConfiguration) -> Any? = {},
                   additionalCordapps: Set<TestCorDapp>): StartedMockNode {
        val parameters = MockNodeParameters(forcedID, legalName, entropyRoot, configOverrides, additionalCordapps)
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

        return createUnstartedNode(legalName, forcedID, entropyRoot, configOverrides, cordappsForPackages(extraCordappPackages))
    }

    /**
     * Create an unstarted node with the given parameters.
     *
     * @param legalName The node's legal name.
     * @param forcedID A unique identifier for the node.
     * @param entropyRoot The initial entropy value to use when generating keys. Defaults to an (insecure) random value,
     * but can be overridden to cause nodes to have stable or colliding identity/service keys.
     * @param configOverrides Add/override behaviour of the [NodeConfiguration] mock object.
     * @param additionalCordapps Additional [TestCorDapp]s that this node will have available, in addition to the ones common to all nodes managed by the [MockNetwork].
     */
    fun createUnstartedNode(legalName: CordaX500Name? = null,
                            forcedID: Int? = null,
                            entropyRoot: BigInteger = BigInteger.valueOf(random63BitValue()),
                            configOverrides: (NodeConfiguration) -> Any? = {},
                            additionalCordapps: Set<TestCorDapp>): UnstartedMockNode {
        val parameters = MockNodeParameters(forcedID, legalName, entropyRoot, configOverrides, additionalCordapps)
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
