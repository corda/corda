@file:JvmName("Driver")

package net.corda.testing.driver

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.client.rpc.CordaRPCClient
import net.corda.cordform.CordformContext
import net.corda.cordform.CordformNode
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.concurrent.firstOf
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.*
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NotaryService
import net.corda.core.toFuture
import net.corda.core.utilities.*
import net.corda.node.internal.Node
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.StartedNode
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.config.*
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.nodeapi.NodeInfoFilesCopier
import net.corda.nodeapi.User
import net.corda.nodeapi.config.toConfig
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.testing.*
import net.corda.testing.internal.ProcessUtilities
import net.corda.testing.node.ClusterSpec
import net.corda.testing.node.MockServices.Companion.MOCK_VERSION_INFO
import net.corda.testing.node.NotarySpec
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.Logger
import rx.Observable
import rx.observables.ConnectableObservable
import java.net.*
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * This file defines a small "Driver" DSL for starting up nodes that is only intended for development, demos and tests.
 *
 * The process the driver is run in behaves as an Artemis client and starts up other processes.
 *
 * TODO this file is getting way too big, it should be split into several files.
 */
private val log: Logger = loggerFor<DriverDSL>()

private val DEFAULT_POLL_INTERVAL = 500.millis

private const val DEFAULT_WARN_COUNT = 120

private val DRIVER_REQUIRED_PERMISSIONS = setOf(
        invokeRpc(CordaRPCOps::nodeInfo),
        invokeRpc(CordaRPCOps::networkMapFeed),
        invokeRpc(CordaRPCOps::networkMapSnapshot)
)

/**
 * Object ecapsulating a notary started automatically by the driver.
 */
data class NotaryHandle(val identity: Party, val validating: Boolean, val nodeHandles: CordaFuture<List<NodeHandle>>)

/**
 * This is the interface that's exposed to DSL users.
 */
interface DriverDSLExposedInterface : CordformContext {
    /** Returns a list of [NotaryHandle]s matching the list of [NotarySpec]s passed into [driver]. */
    val notaryHandles: List<NotaryHandle>

    /**
     * Returns the [NotaryHandle] for the single notary on the network. Throws if there are none or more than one.
     * @see notaryHandles
     */
    val defaultNotaryHandle: NotaryHandle get() {
        return when (notaryHandles.size) {
            0 -> throw IllegalStateException("There are no notaries defined on the network")
            1 -> notaryHandles[0]
            else -> throw IllegalStateException("There is more than one notary defined on the network")
        }
    }

    /**
     * Returns the identity of the single notary on the network. Throws if there are none or more than one.
     * @see defaultNotaryHandle
     */
    val defaultNotaryIdentity: Party get() = defaultNotaryHandle.identity

    /**
     * Returns a [CordaFuture] on the [NodeHandle] for the single-node notary on the network. Throws if there
     * are no notaries or more than one, or if the notary is a distributed cluster.
     * @see defaultNotaryHandle
     * @see notaryHandles
     */
    val defaultNotaryNode: CordaFuture<NodeHandle> get() {
        return defaultNotaryHandle.nodeHandles.map {
            it.singleOrNull() ?: throw IllegalStateException("Default notary is not a single node")
        }
    }

    /**
     * Start a node.
     *
     * @param defaultParameters The default parameters for the node. Allows the node to be configured in builder style
     *   when called from Java code.
     * @param providedName Optional name of the node, which will be its legal name in [Party]. Defaults to something
     *     random. Note that this must be unique as the driver uses it as a primary key!
     * @param verifierType The type of transaction verifier to use. See: [VerifierType]
     * @param rpcUsers List of users who are authorised to use the RPC system. Defaults to empty list.
     * @param startInSameProcess Determines if the node should be started inside the same process the Driver is running
     *     in. If null the Driver-level value will be used.
     * @return A [CordaFuture] on the [NodeHandle] to the node. The future will complete when the node is available.
     */
    fun startNode(
            defaultParameters: NodeParameters = NodeParameters(),
            providedName: CordaX500Name? = defaultParameters.providedName,
            rpcUsers: List<User> = defaultParameters.rpcUsers,
            verifierType: VerifierType = defaultParameters.verifierType,
            customOverrides: Map<String, Any?> = defaultParameters.customOverrides,
            startInSameProcess: Boolean? = defaultParameters.startInSameProcess,
            maximumHeapSize: String = defaultParameters.maximumHeapSize): CordaFuture<NodeHandle>

    /**
     * Helper function for starting a [Node] with custom parameters from Java.
     *
     * @param parameters The default parameters for the driver.
     * @return The value returned in the [dsl] closure.
     */
    fun startNode(parameters: NodeParameters): CordaFuture<NodeHandle> = startNode(defaultParameters = parameters)

    fun startNodes(
            nodes: List<CordformNode>,
            startInSameProcess: Boolean? = null,
            maximumHeapSize: String = "200m"
    ): List<CordaFuture<NodeHandle>>

    /** Call [startWebserver] with a default maximumHeapSize. */
    fun startWebserver(handle: NodeHandle): CordaFuture<WebserverHandle> = startWebserver(handle, "200m")

    /**
     * Starts a web server for a node
     * @param handle The handle for the node that this webserver connects to via RPC.
     * @param maximumHeapSize Argument for JVM -Xmx option e.g. "200m".
     */
    fun startWebserver(handle: NodeHandle, maximumHeapSize: String): CordaFuture<WebserverHandle>

    /**
     * Polls a function until it returns a non-null value. Note that there is no timeout on the polling.
     *
     * @param pollName A description of what is being polled.
     * @param pollInterval The interval of polling.
     * @param warnCount The number of polls after the Driver gives a warning.
     * @param check The function being polled.
     * @return A future that completes with the non-null value [check] has returned.
     */
    fun <A> pollUntilNonNull(pollName: String, pollInterval: Duration = DEFAULT_POLL_INTERVAL, warnCount: Int = DEFAULT_WARN_COUNT, check: () -> A?): CordaFuture<A>

    /**
     * Polls the given function until it returns true.
     * @see pollUntilNonNull
     */
    fun pollUntilTrue(pollName: String, pollInterval: Duration = DEFAULT_POLL_INTERVAL, warnCount: Int = DEFAULT_WARN_COUNT, check: () -> Boolean): CordaFuture<Unit> {
        return pollUntilNonNull(pollName, pollInterval, warnCount) { if (check()) Unit else null }
    }

    val shutdownManager: ShutdownManager
}

interface DriverDSLInternalInterface : DriverDSLExposedInterface {
    fun start()
    fun shutdown()
}

sealed class NodeHandle {
    abstract val nodeInfo: NodeInfo
    /**
     * Interface to the node's RPC system. The first RPC user will be used to login if are any, otherwise a default one
     * will be added and that will be used.
     */
    abstract val rpc: CordaRPCOps
    abstract val configuration: NodeConfiguration
    abstract val webAddress: NetworkHostAndPort

    /**
     * Stops the referenced node.
     */
    abstract fun stop()

    data class OutOfProcess(
            override val nodeInfo: NodeInfo,
            override val rpc: CordaRPCOps,
            override val configuration: NodeConfiguration,
            override val webAddress: NetworkHostAndPort,
            val debugPort: Int?,
            val process: Process,
            private val onStopCallback: () -> Unit
    ) : NodeHandle() {
        override fun stop() {
            with(process) {
                destroy()
                waitFor()
            }
            onStopCallback()
        }
    }

    data class InProcess(
            override val nodeInfo: NodeInfo,
            override val rpc: CordaRPCOps,
            override val configuration: NodeConfiguration,
            override val webAddress: NetworkHostAndPort,
            val node: StartedNode<Node>,
            val nodeThread: Thread,
            private val onStopCallback: () -> Unit
    ) : NodeHandle() {
        override fun stop() {
            node.dispose()
            with(nodeThread) {
                interrupt()
                join()
            }
            onStopCallback()
        }
    }

    fun rpcClientToNode(): CordaRPCClient = CordaRPCClient(configuration.rpcAddress!!)
}

data class WebserverHandle(
        val listenAddress: NetworkHostAndPort,
        val process: Process
)

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
    fun setProvidedName(providedName: CordaX500Name?) = copy(providedName = providedName)
    fun setRpcUsers(rpcUsers: List<User>) = copy(rpcUsers = rpcUsers)
    fun setVerifierType(verifierType: VerifierType) = copy(verifierType = verifierType)
    fun setCustomerOverrides(customOverrides: Map<String, Any?>) = copy(customOverrides = customOverrides)
    fun setStartInSameProcess(startInSameProcess: Boolean?) = copy(startInSameProcess = startInSameProcess)
    fun setMaximumHeapSize(maximumHeapSize: String) = copy(maximumHeapSize = maximumHeapSize)
}

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
 * @param isDebug Indicates whether the spawned nodes should start in jdwt debug mode and have debug level logging.
 * @param driverDirectory The base directory node directories go into, defaults to "build/<timestamp>/". The node
 *   directories themselves are "<baseDirectory>/<legalName>/", where legalName defaults to "<randomName>-<messagingPort>"
 *   and may be specified in [DriverDSL.startNode].
 * @param portAllocation The port allocation strategy to use for the messaging and the web server addresses. Defaults to incremental.
 * @param debugPortAllocation The port allocation strategy to use for jvm debugging. Defaults to incremental.
 * @param systemProperties A Map of extra system properties which will be given to each new node. Defaults to empty.
 * @param useTestClock If true the test clock will be used in Node.
 * @param startNodesInProcess Provides the default behaviour of whether new nodes should start inside this process or
 *     not. Note that this may be overridden in [DriverDSLExposedInterface.startNode].
 * @param notarySpecs The notaries advertised in the [NetworkParameters] for this network. These nodes will be started
 * automatically and will be available from [DriverDSLExposedInterface.notaryHandles]. Defaults to a simple validating notary.
 * @param dsl The dsl itself.
 * @return The value returned in the [dsl] closure.
 */
fun <A> driver(
        defaultParameters: DriverParameters = DriverParameters(),
        isDebug: Boolean = defaultParameters.isDebug,
        driverDirectory: Path = defaultParameters.driverDirectory,
        portAllocation: PortAllocation = defaultParameters.portAllocation,
        debugPortAllocation: PortAllocation = defaultParameters.debugPortAllocation,
        systemProperties: Map<String, String> = defaultParameters.systemProperties,
        useTestClock: Boolean = defaultParameters.useTestClock,
        initialiseSerialization: Boolean = defaultParameters.initialiseSerialization,
        startNodesInProcess: Boolean = defaultParameters.startNodesInProcess,
        waitForAllNodesToFinish: Boolean = defaultParameters.waitForNodesToFinish,
        notarySpecs: List<NotarySpec> = defaultParameters.notarySpecs,
        extraCordappPackagesToScan: List<String> = defaultParameters.extraCordappPackagesToScan,
        dsl: DriverDSLExposedInterface.() -> A
): A {
    return genericDriver(
            driverDsl = DriverDSL(
                    portAllocation = portAllocation,
                    debugPortAllocation = debugPortAllocation,
                    systemProperties = systemProperties,
                    driverDirectory = driverDirectory.toAbsolutePath(),
                    useTestClock = useTestClock,
                    isDebug = isDebug,
                    startNodesInProcess = startNodesInProcess,
                    waitForNodesToFinish = waitForAllNodesToFinish,
                    notarySpecs = notarySpecs,
                    extraCordappPackagesToScan = extraCordappPackagesToScan
            ),
            coerce = { it },
            dsl = dsl,
            initialiseSerialization = initialiseSerialization
    )
}

/**
 * Helper function for starting a [driver] with custom parameters from Java.
 *
 * @param parameters The default parameters for the driver.
 * @param dsl The dsl itself.
 * @return The value returned in the [dsl] closure.
 */
fun <A> driver(
        parameters: DriverParameters,
        dsl: DriverDSLExposedInterface.() -> A
): A {
    return driver(defaultParameters = parameters, dsl = dsl)
}

/** Helper builder for configuring a [driver] from Java. */
@Suppress("unused")
data class DriverParameters(
        val isDebug: Boolean = false,
        val driverDirectory: Path = Paths.get("build", getTimestampAsDirectoryName()),
        val portAllocation: PortAllocation = PortAllocation.Incremental(10000),
        val debugPortAllocation: PortAllocation = PortAllocation.Incremental(5005),
        val systemProperties: Map<String, String> = emptyMap(),
        val useTestClock: Boolean = false,
        val initialiseSerialization: Boolean = true,
        val startNodesInProcess: Boolean = false,
        val waitForNodesToFinish: Boolean = false,
        val notarySpecs: List<NotarySpec> = listOf(NotarySpec(DUMMY_NOTARY.name)),
        val extraCordappPackagesToScan: List<String> = emptyList()
) {
    fun setIsDebug(isDebug: Boolean) = copy(isDebug = isDebug)
    fun setDriverDirectory(driverDirectory: Path) = copy(driverDirectory = driverDirectory)
    fun setPortAllocation(portAllocation: PortAllocation) = copy(portAllocation = portAllocation)
    fun setDebugPortAllocation(debugPortAllocation: PortAllocation) = copy(debugPortAllocation = debugPortAllocation)
    fun setSystemProperties(systemProperties: Map<String, String>) = copy(systemProperties = systemProperties)
    fun setUseTestClock(useTestClock: Boolean) = copy(useTestClock = useTestClock)
    fun setInitialiseSerialization(initialiseSerialization: Boolean) = copy(initialiseSerialization = initialiseSerialization)
    fun setStartNodesInProcess(startNodesInProcess: Boolean) = copy(startNodesInProcess = startNodesInProcess)
    fun setTerminateNodesOnShutdown(terminateNodesOnShutdown: Boolean) = copy(waitForNodesToFinish = terminateNodesOnShutdown)
    fun setExtraCordappPackagesToScan(extraCordappPackagesToScan: List<String>) = copy(extraCordappPackagesToScan = extraCordappPackagesToScan)
    fun setNotarySpecs(notarySpecs: List<NotarySpec>) = copy(notarySpecs = notarySpecs)
}

/**
 * This is a helper method to allow extending of the DSL, along the lines of
 *   interface SomeOtherExposedDSLInterface : DriverDSLExposedInterface
 *   interface SomeOtherInternalDSLInterface : DriverDSLInternalInterface, SomeOtherExposedDSLInterface
 *   class SomeOtherDSL(val driverDSL : DriverDSL) : DriverDSLInternalInterface by driverDSL, SomeOtherInternalDSLInterface
 *
 * @param coerce We need this explicit coercion witness because we can't put an extra DI : D bound in a `where` clause.
 */
fun <DI : DriverDSLExposedInterface, D : DriverDSLInternalInterface, A> genericDriver(
        driverDsl: D,
        initialiseSerialization: Boolean = true,
        coerce: (D) -> DI,
        dsl: DI.() -> A
): A {
    val serializationEnv = setGlobalSerialization(initialiseSerialization)
    val shutdownHook = addShutdownHook(driverDsl::shutdown)
    try {
        driverDsl.start()
        return dsl(coerce(driverDsl))
    } catch (exception: Throwable) {
        log.error("Driver shutting down because of exception", exception)
        throw exception
    } finally {
        driverDsl.shutdown()
        shutdownHook.cancel()
        serializationEnv.unset()
    }
}

/**
 * This is a helper method to allow extending of the DSL, along the lines of
 *   interface SomeOtherExposedDSLInterface : DriverDSLExposedInterface
 *   interface SomeOtherInternalDSLInterface : DriverDSLInternalInterface, SomeOtherExposedDSLInterface
 *   class SomeOtherDSL(val driverDSL : DriverDSL) : DriverDSLInternalInterface by driverDSL, SomeOtherInternalDSLInterface
 *
 * @param coerce We need this explicit coercion witness because we can't put an extra DI : D bound in a `where` clause.
 */
fun <DI : DriverDSLExposedInterface, D : DriverDSLInternalInterface, A> genericDriver(
        defaultParameters: DriverParameters = DriverParameters(),
        isDebug: Boolean = defaultParameters.isDebug,
        driverDirectory: Path = defaultParameters.driverDirectory,
        portAllocation: PortAllocation = defaultParameters.portAllocation,
        debugPortAllocation: PortAllocation = defaultParameters.debugPortAllocation,
        systemProperties: Map<String, String> = defaultParameters.systemProperties,
        useTestClock: Boolean = defaultParameters.useTestClock,
        initialiseSerialization: Boolean = defaultParameters.initialiseSerialization,
        waitForNodesToFinish: Boolean = defaultParameters.waitForNodesToFinish,
        startNodesInProcess: Boolean = defaultParameters.startNodesInProcess,
        notarySpecs: List<NotarySpec>,
        extraCordappPackagesToScan: List<String> = defaultParameters.extraCordappPackagesToScan,
        driverDslWrapper: (DriverDSL) -> D,
        coerce: (D) -> DI, dsl: DI.() -> A
): A {
    val serializationEnv = setGlobalSerialization(initialiseSerialization)
    val driverDsl = driverDslWrapper(
            DriverDSL(
                    portAllocation = portAllocation,
                    debugPortAllocation = debugPortAllocation,
                    systemProperties = systemProperties,
                    driverDirectory = driverDirectory.toAbsolutePath(),
                    useTestClock = useTestClock,
                    isDebug = isDebug,
                    startNodesInProcess = startNodesInProcess,
                    waitForNodesToFinish = waitForNodesToFinish,
                    extraCordappPackagesToScan = extraCordappPackagesToScan,
                    notarySpecs = notarySpecs
            )
    )
    val shutdownHook = addShutdownHook(driverDsl::shutdown)
    try {
        driverDsl.start()
        return dsl(coerce(driverDsl))
    } catch (exception: Throwable) {
        log.error("Driver shutting down because of exception", exception)
        throw exception
    } finally {
        driverDsl.shutdown()
        shutdownHook.cancel()
        serializationEnv.unset()
    }
}

fun getTimestampAsDirectoryName(): String {
    return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(UTC).format(Instant.now())
}

class ListenProcessDeathException(hostAndPort: NetworkHostAndPort, listenProcess: Process) :
        CordaException("The process that was expected to listen on $hostAndPort has died with status: ${listenProcess.exitValue()}")

/**
 * @throws ListenProcessDeathException if [listenProcess] dies before the check succeeds, i.e. the check can't succeed as intended.
 */
fun addressMustBeBound(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort, listenProcess: Process? = null) {
    addressMustBeBoundFuture(executorService, hostAndPort, listenProcess).getOrThrow()
}

fun addressMustBeBoundFuture(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort, listenProcess: Process? = null): CordaFuture<Unit> {
    return poll(executorService, "address $hostAndPort to bind") {
        if (listenProcess != null && !listenProcess.isAlive) {
            throw ListenProcessDeathException(hostAndPort, listenProcess)
        }
        try {
            Socket(hostAndPort.host, hostAndPort.port).close()
            Unit
        } catch (_exception: SocketException) {
            null
        }
    }
}

/*
 * The default timeout value of 40 seconds have been chosen based on previous node shutdown time estimate.
 * It's been observed that nodes can take up to 30 seconds to shut down, so just to stay on the safe side the 60 seconds
 * timeout has been chosen.
 */
fun addressMustNotBeBound(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort, timeout: Duration = 40.seconds) {
    addressMustNotBeBoundFuture(executorService, hostAndPort).getOrThrow(timeout)
}

fun addressMustNotBeBoundFuture(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort): CordaFuture<Unit> {
    return poll(executorService, "address $hostAndPort to unbind") {
        try {
            Socket(hostAndPort.host, hostAndPort.port).close()
            null
        } catch (_exception: SocketException) {
            Unit
        }
    }
}

fun <A> poll(
        executorService: ScheduledExecutorService,
        pollName: String,
        pollInterval: Duration = 500.millis,
        warnCount: Int = 120,
        check: () -> A?
): CordaFuture<A> {
    val resultFuture = openFuture<A>()
    val task = object : Runnable {
        var counter = -1
        override fun run() {
            if (resultFuture.isCancelled) return // Give up, caller can no longer get the result.
            if (++counter == warnCount) {
                log.warn("Been polling $pollName for ${(pollInterval * warnCount.toLong()).seconds} seconds...")
            }
            try {
                val checkResult = check()
                if (checkResult != null) {
                    resultFuture.set(checkResult)
                } else {
                    executorService.schedule(this, pollInterval.toMillis(), MILLISECONDS)
                }
            } catch (t: Throwable) {
                resultFuture.setException(t)
            }
        }
    }
    executorService.submit(task) // The check may be expensive, so always run it in the background even the first time.
    return resultFuture
}

class DriverDSL(
        val portAllocation: PortAllocation,
        val debugPortAllocation: PortAllocation,
        val systemProperties: Map<String, String>,
        val driverDirectory: Path,
        val useTestClock: Boolean,
        val isDebug: Boolean,
        val startNodesInProcess: Boolean,
        val waitForNodesToFinish: Boolean,
        extraCordappPackagesToScan: List<String>,
        val notarySpecs: List<NotarySpec>
) : DriverDSLInternalInterface {
    private var _executorService: ScheduledExecutorService? = null
    val executorService get() = _executorService!!
    private var _shutdownManager: ShutdownManager? = null
    override val shutdownManager get() = _shutdownManager!!
    private val cordappPackages = extraCordappPackagesToScan + getCallerPackage()
    // TODO: this object will copy NodeInfo files from started nodes to other nodes additional-node-infos/
    // This uses the FileSystem and adds a delay (~5 seconds) given by the time we wait before polling the file system.
    // Investigate whether we can avoid that.
    private val nodeInfoFilesCopier = NodeInfoFilesCopier()
    // Map from a nodes legal name to an observable emitting the number of nodes in its network map.
    private val countObservables = mutableMapOf<CordaX500Name, Observable<Int>>()
    private lateinit var _notaries: List<NotaryHandle>
    override val notaryHandles: List<NotaryHandle> get() = _notaries

    class State {
        val processes = ArrayList<Process>()
    }

    private val state = ThreadBox(State())

    //TODO: remove this once we can bundle quasar properly.
    private val quasarJarPath: String by lazy {
        val cl = ClassLoader.getSystemClassLoader()
        val urls = (cl as URLClassLoader).urLs
        val quasarPattern = ".*quasar.*\\.jar$".toRegex()
        val quasarFileUrl = urls.first { quasarPattern.matches(it.path) }
        Paths.get(quasarFileUrl.toURI()).toString()
    }

    override fun shutdown() {
        if (waitForNodesToFinish) {
            state.locked {
                processes.forEach { it.waitFor() }
            }
        }
        _shutdownManager?.shutdown()
        _executorService?.shutdownNow()
    }

    private fun establishRpc(config: NodeConfiguration, processDeathFuture: CordaFuture<out Process>): CordaFuture<CordaRPCOps> {
        val rpcAddress = config.rpcAddress!!
        val client = CordaRPCClient(rpcAddress)
        val connectionFuture = poll(executorService, "RPC connection") {
            try {
                client.start(config.rpcUsers[0].username, config.rpcUsers[0].password)
            } catch (e: Exception) {
                if (processDeathFuture.isDone) throw e
                log.error("Exception $e, Retrying RPC connection at $rpcAddress")
                null
            }
        }
        return firstOf(connectionFuture, processDeathFuture) {
            if (it == processDeathFuture) {
                throw ListenProcessDeathException(rpcAddress, processDeathFuture.getOrThrow())
            }
            val connection = connectionFuture.getOrThrow()
            shutdownManager.registerShutdown(connection::close)
            connection.proxy
        }
    }

    override fun startNode(
            defaultParameters: NodeParameters,
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String
    ): CordaFuture<NodeHandle> {
        val p2pAddress = portAllocation.nextHostAndPort()
        val rpcAddress = portAllocation.nextHostAndPort()
        val webAddress = portAllocation.nextHostAndPort()
        // TODO: Derive name from the full picked name, don't just wrap the common name
        val name = providedName ?: CordaX500Name(organisation = "${oneOf(names).organisation}-${p2pAddress.port}", locality = "London", country = "GB")
        val users = rpcUsers.map { it.copy(permissions = it.permissions + DRIVER_REQUIRED_PERMISSIONS) }
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory(name),
                allowMissingConfig = true,
                configOverrides = configOf(
                        "myLegalName" to name.toString(),
                        "p2pAddress" to p2pAddress.toString(),
                        "rpcAddress" to rpcAddress.toString(),
                        "webAddress" to webAddress.toString(),
                        "useTestClock" to useTestClock,
                        "rpcUsers" to if (users.isEmpty()) defaultRpcUserList else users.map { it.toConfig().root().unwrapped() },
                        "verifierType" to verifierType.name
                ) + customOverrides
        )
        return startNodeInternal(config, webAddress, startInSameProcess, maximumHeapSize)
    }

    override fun startNodes(nodes: List<CordformNode>, startInSameProcess: Boolean?, maximumHeapSize: String): List<CordaFuture<NodeHandle>> {
        return nodes.map { node ->
            portAllocation.nextHostAndPort() // rpcAddress
            val webAddress = portAllocation.nextHostAndPort()
            val name = CordaX500Name.parse(node.name)
            val rpcUsers = node.rpcUsers
            val notary = if (node.notary != null) mapOf("notary" to node.notary) else emptyMap()
            val config = ConfigHelper.loadConfig(
                    baseDirectory = baseDirectory(name),
                    allowMissingConfig = true,
                    configOverrides = node.config + notary + mapOf(
                            "rpcUsers" to if (rpcUsers.isEmpty()) defaultRpcUserList else rpcUsers
                    )
            )
            startNodeInternal(config, webAddress, startInSameProcess, maximumHeapSize)
        }
    }

    private fun queryWebserver(handle: NodeHandle, process: Process): WebserverHandle {
        val protocol = if (handle.configuration.useHTTPS) "https://" else "http://"
        val url = URL("$protocol${handle.webAddress}/api/status")
        val client = OkHttpClient.Builder().connectTimeout(5, SECONDS).readTimeout(60, SECONDS).build()

        while (process.isAlive) try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.isSuccessful && (response.body().string() == "started")) {
                return WebserverHandle(handle.webAddress, process)
            }
        } catch (e: ConnectException) {
            log.debug("Retrying webserver info at ${handle.webAddress}")
        }

        throw IllegalStateException("Webserver at ${handle.webAddress} has died")
    }

    override fun startWebserver(handle: NodeHandle, maximumHeapSize: String): CordaFuture<WebserverHandle> {
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val process = DriverDSL.startWebserver(handle, debugPort, maximumHeapSize)
        shutdownManager.registerProcessShutdown(process)
        val webReadyFuture = addressMustBeBoundFuture(executorService, handle.webAddress, process)
        return webReadyFuture.map { queryWebserver(handle, process) }
    }

    override fun start() {
        _executorService = Executors.newScheduledThreadPool(2, ThreadFactoryBuilder().setNameFormat("driver-pool-thread-%d").build())
        _shutdownManager = ShutdownManager(executorService)
        shutdownManager.registerShutdown { nodeInfoFilesCopier.close() }
        val notaryInfos = generateNotaryIdentities()
        val nodeHandles = startNotaries()
        _notaries = notaryInfos.zip(nodeHandles) { (identity, validating), nodes -> NotaryHandle(identity, validating, nodes) }
    }

    private fun generateNotaryIdentities(): List<Pair<Party, Boolean>> {
        return notarySpecs.map { spec ->
            val identity = if (spec.cluster == null) {
                ServiceIdentityGenerator.generateToDisk(
                        dirs = listOf(baseDirectory(spec.name)),
                        serviceName = spec.name.copy(commonName = NotaryService.constructId(validating = spec.validating))
                )
            } else {
                ServiceIdentityGenerator.generateToDisk(
                        dirs = generateNodeNames(spec).map { baseDirectory(it) },
                        serviceName = spec.name
                )
            }
            Pair(identity, spec.validating)
        }
    }

    private fun generateNodeNames(spec: NotarySpec): List<CordaX500Name> {
        return (0 until spec.cluster!!.clusterSize).map { spec.name.copy(commonName = null, organisation = "${spec.name.organisation}-$it") }
    }

    private fun startNotaries(): List<CordaFuture<List<NodeHandle>>> {
        return notarySpecs.map {
            when {
                it.cluster == null -> startSingleNotary(it)
                it.cluster is ClusterSpec.Raft -> startRaftNotaryCluster(it)
                else -> throw IllegalArgumentException("BFT-SMaRt not supported")
            }
        }
    }

    // TODO This mapping is done is several places including the gradle plugin. In general we need a better way of
    // generating the configs for the nodes, probably making use of Any.toConfig()
    private fun NotaryConfig.toConfigMap(): Map<String, Any> = mapOf("notary" to toConfig().root().unwrapped())

    private fun startSingleNotary(spec: NotarySpec): CordaFuture<List<NodeHandle>> {
        return startNode(
                providedName = spec.name,
                rpcUsers = spec.rpcUsers,
                verifierType = spec.verifierType,
                customOverrides = NotaryConfig(spec.validating).toConfigMap()
        ).map { listOf(it) }
    }

    private fun startRaftNotaryCluster(spec: NotarySpec): CordaFuture<List<NodeHandle>> {
        fun notaryConfig(nodeAddress: NetworkHostAndPort, clusterAddress: NetworkHostAndPort? = null): Map<String, Any> {
            val clusterAddresses = if (clusterAddress != null) listOf(clusterAddress) else emptyList()
            val config = NotaryConfig(
                    validating = spec.validating,
                    raft = RaftConfig(nodeAddress = nodeAddress, clusterAddresses = clusterAddresses))
            return config.toConfigMap()
        }

        val nodeNames = generateNodeNames(spec)
        val clusterAddress = portAllocation.nextHostAndPort()

        // Start the first node that will bootstrap the cluster
        val firstNodeFuture = startNode(
                providedName = nodeNames[0],
                rpcUsers = spec.rpcUsers,
                verifierType = spec.verifierType,
                customOverrides = notaryConfig(clusterAddress) + mapOf(
                        "database.serverNameTablePrefix" to nodeNames[0].toString().replace(Regex("[^0-9A-Za-z]+"), "")
                )
        )

        // All other nodes will join the cluster
        val restNodeFutures = nodeNames.drop(1).map {
            val nodeAddress = portAllocation.nextHostAndPort()
            startNode(
                    providedName = it,
                    rpcUsers = spec.rpcUsers,
                    verifierType = spec.verifierType,
                    customOverrides = notaryConfig(nodeAddress, clusterAddress) + mapOf(
                            "database.serverNameTablePrefix" to it.toString().replace(Regex("[^0-9A-Za-z]+"), "")
                    )
            )
        }

        return firstNodeFuture.flatMap { first ->
            restNodeFutures.transpose().map { rest -> listOf(first) + rest }
        }
    }

    fun baseDirectory(nodeName: CordaX500Name): Path {
        val nodeDirectoryName = nodeName.organisation.filter { !it.isWhitespace() }
        return driverDirectory / nodeDirectoryName
    }

    override fun baseDirectory(nodeName: String): Path = baseDirectory(CordaX500Name.parse(nodeName))

    /**
     * @param initial number of nodes currently in the network map of a running node.
     * @param networkMapCacheChangeObservable an observable returning the updates to the node network map.
     * @return a [ConnectableObservable] which emits a new [Int] every time the number of registered nodes changes
     *   the initial value emitted is always [initial]
     */
    private fun nodeCountObservable(initial: Int, networkMapCacheChangeObservable: Observable<NetworkMapCache.MapChange>):
            ConnectableObservable<Int> {
        val count = AtomicInteger(initial)
        return networkMapCacheChangeObservable.map { it ->
            when (it) {
                is NetworkMapCache.MapChange.Added -> count.incrementAndGet()
                is NetworkMapCache.MapChange.Removed -> count.decrementAndGet()
                is NetworkMapCache.MapChange.Modified -> count.get()
            }
        }.startWith(initial).replay()
    }

    /**
     * @param rpc the [CordaRPCOps] of a newly started node.
     * @return a [CordaFuture] which resolves when every node started by driver has in its network map a number of nodes
     *   equal to the number of running nodes. The future will yield the number of connected nodes.
     */
    private fun allNodesConnected(rpc: CordaRPCOps): CordaFuture<Int> {
        val (snapshot, updates) = rpc.networkMapFeed()
        val counterObservable = nodeCountObservable(snapshot.size, updates)
        countObservables[rpc.nodeInfo().legalIdentities[0].name] = counterObservable
        /* TODO: this might not always be the exact number of nodes one has to wait for,
         * for example in the following sequence
         * 1 start 3 nodes in order, A, B, C.
         * 2 before the future returned by this function resolves, kill B
         * At that point this future won't ever resolve as it will wait for nodes to know 3 other nodes.
         */
        val requiredNodes = countObservables.size

        // This is an observable which yield the minimum number of nodes in each node network map.
        val smallestSeenNetworkMapSize = Observable.combineLatest(countObservables.values.toList()) { args: Array<Any> ->
            args.map { it as Int }.min() ?: 0
        }
        val future = smallestSeenNetworkMapSize.filter { it >= requiredNodes }.toFuture()
        counterObservable.connect()
        return future
    }

    private fun startNodeInternal(config: Config,
                                  webAddress: NetworkHostAndPort,
                                  startInProcess: Boolean?,
                                  maximumHeapSize: String): CordaFuture<NodeHandle> {
        val configuration = config.parseAsNodeConfiguration()
        val baseDirectory = configuration.baseDirectory.createDirectories()
        nodeInfoFilesCopier.addConfig(baseDirectory)
        val onNodeExit: () -> Unit = {
            nodeInfoFilesCopier.removeConfig(baseDirectory)
            countObservables.remove(configuration.myLegalName)
        }
        if (startInProcess ?: startNodesInProcess) {
            val nodeAndThreadFuture = startInProcessNode(executorService, configuration, config, cordappPackages)
            shutdownManager.registerShutdown(
                    nodeAndThreadFuture.map { (node, thread) ->
                        {
                            node.dispose()
                            thread.interrupt()
                        }
                    }
            )
            return nodeAndThreadFuture.flatMap { (node, thread) ->
                establishRpc(configuration, openFuture()).flatMap { rpc ->
                    allNodesConnected(rpc).map {
                        NodeHandle.InProcess(rpc.nodeInfo(), rpc, configuration, webAddress, node, thread, onNodeExit)
                    }
                }
            }
        } else {
            val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
            val process = startOutOfProcessNode(configuration, config, quasarJarPath, debugPort, systemProperties, cordappPackages, maximumHeapSize)
            if (waitForNodesToFinish) {
                state.locked {
                    processes += process
                }
            } else {
                shutdownManager.registerProcessShutdown(process)
            }
            val p2pReadyFuture = addressMustBeBoundFuture(executorService, configuration.p2pAddress, process)
            return p2pReadyFuture.flatMap {
                val processDeathFuture = poll(executorService, "process death") {
                    if (process.isAlive) null else process
                }
                establishRpc(configuration, processDeathFuture).flatMap { rpc ->
                    // Check for all nodes to have all other nodes in background in case RPC is failing over:
                    val networkMapFuture = executorService.fork { allNodesConnected(rpc) }.flatMap { it }
                    firstOf(processDeathFuture, networkMapFuture) {
                        if (it == processDeathFuture) {
                            throw ListenProcessDeathException(configuration.p2pAddress, process)
                        }
                        processDeathFuture.cancel(false)
                        log.info("Node handle is ready. NodeInfo: ${rpc.nodeInfo()}, WebAddress: $webAddress")
                        NodeHandle.OutOfProcess(rpc.nodeInfo(), rpc, configuration, webAddress, debugPort, process,
                                onNodeExit)
                    }
                }
            }
        }
    }

    override fun <A> pollUntilNonNull(pollName: String, pollInterval: Duration, warnCount: Int, check: () -> A?): CordaFuture<A> {
        val pollFuture = poll(executorService, pollName, pollInterval, warnCount, check)
        shutdownManager.registerShutdown { pollFuture.cancel(true) }
        return pollFuture
    }

    companion object {
        private val defaultRpcUserList = listOf(User("default", "default", setOf("ALL")).toConfig().root().unwrapped())

        private val names = arrayOf(
                ALICE.name,
                BOB.name,
                DUMMY_BANK_A.name
        )

        private fun <A> oneOf(array: Array<A>) = array[Random().nextInt(array.size)]

        private fun startInProcessNode(
                executorService: ScheduledExecutorService,
                nodeConf: NodeConfiguration,
                config: Config,
                cordappPackages: List<String>
        ): CordaFuture<Pair<StartedNode<Node>, Thread>> {
            return executorService.fork {
                log.info("Starting in-process Node ${nodeConf.myLegalName.organisation}")
                // Write node.conf
                writeConfig(nodeConf.baseDirectory, "node.conf", config)
                // TODO pass the version in?
                val node = Node(
                        nodeConf,
                        MOCK_VERSION_INFO,
                        initialiseSerialization = false,
                        cordappLoader = CordappLoader.createDefaultWithTestPackages(nodeConf, cordappPackages))
                        .start()
                val nodeThread = thread(name = nodeConf.myLegalName.organisation) {
                    node.internals.run()
                }
                node to nodeThread
            }.flatMap {
                nodeAndThread -> addressMustBeBoundFuture(executorService, nodeConf.p2pAddress).map { nodeAndThread }
            }
        }

        private fun startOutOfProcessNode(
                nodeConf: NodeConfiguration,
                config: Config,
                quasarJarPath: String,
                debugPort: Int?,
                overriddenSystemProperties: Map<String, String>,
                cordappPackages: List<String>,
                maximumHeapSize: String
        ): Process {
            log.info("Starting out-of-process Node ${nodeConf.myLegalName.organisation}, debug port is " + (debugPort ?: "not enabled"))
            // Write node.conf
            writeConfig(nodeConf.baseDirectory, "node.conf", config)

            val systemProperties = overriddenSystemProperties + mapOf(
                    "name" to nodeConf.myLegalName,
                    "visualvm.display.name" to "corda-${nodeConf.myLegalName}",
                    Node.scanPackagesSystemProperty to cordappPackages.joinToString(Node.scanPackagesSeparator),
                    "java.io.tmpdir" to System.getProperty("java.io.tmpdir") // Inherit from parent process
            )
            // See experimental/quasar-hook/README.md for how to generate.
            val excludePattern = "x(antlr**;bftsmart**;ch**;co.paralleluniverse**;com.codahale**;com.esotericsoftware**;" +
                    "com.fasterxml**;com.google**;com.ibm**;com.intellij**;com.jcabi**;com.nhaarman**;com.opengamma**;" +
                    "com.typesafe**;com.zaxxer**;de.javakaffee**;groovy**;groovyjarjarantlr**;groovyjarjarasm**;io.atomix**;" +
                    "io.github**;io.netty**;jdk**;joptsimple**;junit**;kotlin**;net.bytebuddy**;net.i2p**;org.apache**;" +
                    "org.assertj**;org.bouncycastle**;org.codehaus**;org.crsh**;org.dom4j**;org.fusesource**;org.h2**;" +
                    "org.hamcrest**;org.hibernate**;org.jboss**;org.jcp**;org.joda**;org.junit**;org.mockito**;org.objectweb**;" +
                    "org.objenesis**;org.slf4j**;org.w3c**;org.xml**;org.yaml**;reflectasm**;rx**)"
            val extraJvmArguments = systemProperties.removeResolvedClasspath().map { "-D${it.key}=${it.value}" } +
                    "-javaagent:$quasarJarPath=$excludePattern"
            val loggingLevel = if (debugPort == null) "INFO" else "DEBUG"

            return ProcessUtilities.startCordaProcess(
                    className = "net.corda.node.Corda", // cannot directly get class for this, so just use string
                    arguments = listOf(
                            "--base-directory=${nodeConf.baseDirectory}",
                            "--logging-level=$loggingLevel",
                            "--no-local-shell"
                    ),
                    jdwpPort = debugPort,
                    extraJvmArguments = extraJvmArguments,
                    errorLogPath = nodeConf.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME / "error.log",
                    workingDirectory = nodeConf.baseDirectory,
                    maximumHeapSize = maximumHeapSize
            )
        }

        private fun startWebserver(handle: NodeHandle, debugPort: Int?, maximumHeapSize: String): Process {
            val className = "net.corda.webserver.WebServer"
            return ProcessUtilities.startCordaProcess(
                    className = className, // cannot directly get class for this, so just use string
                    arguments = listOf("--base-directory", handle.configuration.baseDirectory.toString()),
                    jdwpPort = debugPort,
                    extraJvmArguments = listOf(
                            "-Dname=node-${handle.configuration.p2pAddress}-webserver",
                            "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}" // Inherit from parent process
                    ),
                    errorLogPath = Paths.get("error.$className.log"),
                    workingDirectory = null,
                    maximumHeapSize = maximumHeapSize
            )
        }

        private fun getCallerPackage(): String {
            return Exception()
                    .stackTrace
                    .first { it.fileName != "Driver.kt" }
                    .let { Class.forName(it.className).`package`?.name }
                    ?: throw IllegalStateException("Function instantiating driver must be defined in a package.")
        }

        /**
         * We have an alternative way of specifying classpath for spawned process: by using "-cp" option. So duplicating the setting of this
         * rather long string is un-necessary and can be harmful on Windows.
         */
        private fun Map<String, Any>.removeResolvedClasspath(): Map<String, Any> {
            return filterNot { it.key == "java.class.path" }
        }
    }
}

fun writeConfig(path: Path, filename: String, config: Config) {
    val configString = config.root().render(ConfigRenderOptions.defaults())
    configString.byteInputStream().copyTo(path / filename, REPLACE_EXISTING)
}

