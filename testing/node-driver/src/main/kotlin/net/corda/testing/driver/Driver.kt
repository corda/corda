@file:JvmName("Driver")

package net.corda.testing.driver

import net.corda.core.DoNotImplement
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.Node
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.config.VerifierType
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.DriverDSLImpl
import net.corda.testing.node.internal.genericDriver
import net.corda.testing.node.internal.getTimestampAsDirectoryName
import rx.Observable
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

/**
 * Object ecapsulating a notary started automatically by the driver.
 */
data class NotaryHandle(val identity: Party, val validating: Boolean, val nodeHandles: CordaFuture<List<NodeHandle>>)

@DoNotImplement
interface NodeHandle : AutoCloseable {
    val nodeInfo: NodeInfo
    /**
     * Interface to the node's RPC system. The first RPC user will be used to login if are any, otherwise a default one
     * will be added and that will be used.
     */
    val rpc: CordaRPCOps
    val p2pAddress: NetworkHostAndPort
    val rpcAddress: NetworkHostAndPort
    val rpcUsers: List<User>
    val baseDirectory: Path
    /**
     * Stops the referenced node.
     */
    fun stop()
}


@DoNotImplement
interface OutOfProcess : NodeHandle {
    val process: Process
}

@DoNotImplement
interface InProcess : NodeHandle {
    val services: StartedNodeServices
    /**
     * Register a flow that is initiated by another flow
     */
    fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>): Observable<T>
}

data class WebserverHandle(
        val listenAddress: NetworkHostAndPort,
        val process: Process
)

@DoNotImplement
sealed class PortAllocation {
    abstract fun nextPort(): Int
    fun nextHostAndPort() = NetworkHostAndPort("localhost", nextPort())

    class Incremental(startingPort: Int) : PortAllocation() {
        val portCounter = AtomicInteger(startingPort)
        override fun nextPort() = portCounter.andIncrement
    }

    object RandomFree : PortAllocation() {
        override fun nextPort(): Int {
            return ServerSocket().use {
                it.bind(InetSocketAddress(0))
                it.localPort
            }
        }
    }
}

/** Helper builder for configuring a [Node] from Java. */
@Suppress("unused")
data class NodeParameters(
        val providedName: CordaX500Name? = null,
        val rpcUsers: List<User> = emptyList(),
        val verifierType: VerifierType = VerifierType.InMemory,
        val customOverrides: Map<String, Any?> = emptyMap(),
        val startInSameProcess: Boolean? = null,
        val maximumHeapSize: String = "200m"
) {
    fun withProvidedName(providedName: CordaX500Name?): NodeParameters = copy(providedName = providedName)
    fun withRpcUsers(rpcUsers: List<User>): NodeParameters = copy(rpcUsers = rpcUsers)
    fun withVerifierType(verifierType: VerifierType): NodeParameters = copy(verifierType = verifierType)
    fun withCustomerOverrides(customOverrides: Map<String, Any?>): NodeParameters = copy(customOverrides = customOverrides)
    fun withStartInSameProcess(startInSameProcess: Boolean?): NodeParameters = copy(startInSameProcess = startInSameProcess)
    fun withMaximumHeapSize(maximumHeapSize: String): NodeParameters = copy(maximumHeapSize = maximumHeapSize)
}

/**
 * @property startJmxHttpServer Indicates whether the spawned nodes should start with a Jolokia JMX agent to enable remote
 * JMX monitoring using HTTP/JSON
 * @property jmxHttpServerPortAllocation The port allocation strategy to use for remote Jolokia/JMX monitoring over HTTP.
 * Defaults to incremental.
 */
data class JmxPolicy(val startJmxHttpServer: Boolean = false,
                     val jmxHttpServerPortAllocation: PortAllocation? =
                     if (startJmxHttpServer) PortAllocation.Incremental(7005) else null)

/**
 * [driver] allows one to start up nodes like this:
 *   driver {
 *     val noService = startNode(providedName = DUMMY_BANK_A.name)
 *     val notary = startNode(providedName = DUMMY_NOTARY.name)
 *
 *     (...)
 *   }
 *
 * Note that [DriverDSL.startNode] does not wait for the node to start up synchronously, but rather returns a [CordaFuture]
 * of the [NodeInfo] that may be waited on, which completes when the new node registered with the network map service or
 * loaded node data from database.
 *
 * @param defaultParameters The default parameters for the driver. Allows the driver to be configured in builder style
 *   when called from Java code.
 * @param dsl The dsl itself.
 * @return The value returned in the [dsl] closure.
 */
fun <A> driver(defaultParameters: DriverParameters = DriverParameters(), dsl: DriverDSL.() -> A): A {
    return genericDriver(
            driverDsl = DriverDSLImpl(
                    portAllocation = defaultParameters.portAllocation,
                    debugPortAllocation = defaultParameters.debugPortAllocation,
                    systemProperties = defaultParameters.systemProperties,
                    driverDirectory = defaultParameters.driverDirectory.toAbsolutePath(),
                    useTestClock = defaultParameters.useTestClock,
                    isDebug = defaultParameters.isDebug,
                    startNodesInProcess = defaultParameters.startNodesInProcess,
                    waitForAllNodesToFinish = defaultParameters.waitForAllNodesToFinish,
                    notarySpecs = defaultParameters.notarySpecs,
                    extraCordappPackagesToScan = defaultParameters.extraCordappPackagesToScan,
                    jmxPolicy = defaultParameters.jmxPolicy,
                    compatibilityZone = null,
                    networkParameters = defaultParameters.networkParameters
            ),
            coerce = { it },
            dsl = dsl,
            initialiseSerialization = true
    )
}

/**
 * Builder for configuring a [driver].
 * @property isDebug Indicates whether the spawned nodes should start in jdwt debug mode and have debug level logging.
 * @property driverDirectory The base directory node directories go into, defaults to "build/<timestamp>/". The node
 *   directories themselves are "<baseDirectory>/<legalName>/", where legalName defaults to "<randomName>-<messagingPort>"
 *   and may be specified in [DriverDSL.startNode].
 * @property portAllocation The port allocation strategy to use for the messaging and the web server addresses. Defaults
 * to incremental.
 * @property debugPortAllocation The port allocation strategy to use for jvm debugging. Defaults to incremental.
 * @property systemProperties A Map of extra system properties which will be given to each new node. Defaults to empty.
 * @property useTestClock If true the test clock will be used in Node.
 * @property startNodesInProcess Provides the default behaviour of whether new nodes should start inside this process or
 *     not. Note that this may be overridden in [DriverDSL.startNode].
 * @property waitForAllNodesToFinish If true, the nodes will not shut down automatically after executing the code in the
 * driver DSL block. It will wait for them to be shut down externally instead.
 * @property notarySpecs The notaries advertised for this network. These nodes will be started automatically and will be
 * available from [DriverDSL.notaryHandles]. Defaults to a simple validating notary.
 * @property jmxPolicy Used to specify whether to expose JMX metrics via Jolokia HHTP/JSON.
 * @property networkParameters The network parameters to be used by all the nodes. [NetworkParameters.notaries] must be
 * empty as notaries are defined by [notarySpecs].
 */
@Suppress("unused")
data class DriverParameters(
        val isDebug: Boolean = false,
        val driverDirectory: Path = Paths.get("build", getTimestampAsDirectoryName()),
        val portAllocation: PortAllocation = PortAllocation.Incremental(10000),
        val debugPortAllocation: PortAllocation = PortAllocation.Incremental(5005),
        val systemProperties: Map<String, String> = emptyMap(),
        val useTestClock: Boolean = false,
        val startNodesInProcess: Boolean = false,
        val waitForAllNodesToFinish: Boolean = false,
        val notarySpecs: List<NotarySpec> = listOf(NotarySpec(DUMMY_NOTARY_NAME)),
        val extraCordappPackagesToScan: List<String> = emptyList(),
        val jmxPolicy: JmxPolicy = JmxPolicy(),
        val networkParameters: NetworkParameters = testNetworkParameters()
) {
    fun withIsDebug(isDebug: Boolean): DriverParameters = copy(isDebug = isDebug)
    fun withDriverDirectory(driverDirectory: Path): DriverParameters = copy(driverDirectory = driverDirectory)
    fun withPortAllocation(portAllocation: PortAllocation): DriverParameters = copy(portAllocation = portAllocation)
    fun withDebugPortAllocation(debugPortAllocation: PortAllocation): DriverParameters = copy(debugPortAllocation = debugPortAllocation)
    fun withSystemProperties(systemProperties: Map<String, String>): DriverParameters = copy(systemProperties = systemProperties)
    fun withUseTestClock(useTestClock: Boolean): DriverParameters = copy(useTestClock = useTestClock)
    fun withStartNodesInProcess(startNodesInProcess: Boolean): DriverParameters = copy(startNodesInProcess = startNodesInProcess)
    fun withWaitForAllNodesToFinish(waitForAllNodesToFinish: Boolean): DriverParameters = copy(waitForAllNodesToFinish = waitForAllNodesToFinish)
    fun withNotarySpecs(notarySpecs: List<NotarySpec>): DriverParameters = copy(notarySpecs = notarySpecs)
    fun withExtraCordappPackagesToScan(extraCordappPackagesToScan: List<String>): DriverParameters = copy(extraCordappPackagesToScan = extraCordappPackagesToScan)
    fun withJmxPolicy(jmxPolicy: JmxPolicy): DriverParameters = copy(jmxPolicy = jmxPolicy)
    fun withNetworkParameters(networkParameters: NetworkParameters): DriverParameters = copy(networkParameters = networkParameters)
}
