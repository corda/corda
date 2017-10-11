@file:JvmName("Driver")

package net.corda.testing.driver

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.client.rpc.CordaRPCClient
import net.corda.cordform.CordformContext
import net.corda.cordform.CordformNode
import net.corda.cordform.NodeDefinition
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.concurrent.firstOf
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.concurrent.*
import net.corda.core.internal.div
import net.corda.core.internal.times
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.*
import net.corda.node.internal.Node
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.StartedNode
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.config.*
import net.corda.node.services.network.NetworkMapService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.nodeapi.User
import net.corda.nodeapi.config.parseAs
import net.corda.nodeapi.config.toConfig
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.testing.*
import net.corda.testing.node.MockServices.Companion.MOCK_VERSION_INFO
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.Logger
import java.io.File
import java.net.*
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread


/**
 * This file defines a small "Driver" DSL for starting up nodes that is only intended for development, demos and tests.
 *
 * The process the driver is run in behaves as an Artemis client and starts up other processes. Namely it first
 * bootstraps a network map service to allow the specified nodes to connect to, then starts up the actual nodes.
 *
 * TODO this file is getting way too big, it should be split into several files.
 */

private val log: Logger = loggerFor<DriverDSL>()

/**
 * This is the interface that's exposed to DSL users.
 */
interface DriverDSLExposedInterface : CordformContext {
    /**
     * Starts a [net.corda.node.internal.Node] in a separate process.
     *
     * @param defaultParameters The default parameters for the node. Allows the node to be configured in builder style
     *   when called from Java code.
     * @param providedName Optional name of the node, which will be its legal name in [Party]. Defaults to something
     *     random. Note that this must be unique as the driver uses it as a primary key!
     * @param verifierType The type of transaction verifier to use. See: [VerifierType]
     * @param rpcUsers List of users who are authorised to use the RPC system. Defaults to empty list.
     * @param startInSameProcess Determines if the node should be started inside the same process the Driver is running
     *     in. If null the Driver-level value will be used.
     * @return The [NodeInfo] of the started up node retrieved from the network map service.
     */
    fun startNode(
            defaultParameters: NodeParameters = NodeParameters(),
            providedName: CordaX500Name? = defaultParameters.providedName,
            rpcUsers: List<User> = defaultParameters.rpcUsers,
            verifierType: VerifierType = defaultParameters.verifierType,
            customOverrides: Map<String, Any?> = defaultParameters.customOverrides,
            startInSameProcess: Boolean? = defaultParameters.startInSameProcess,
            maximumHeapSize: String = defaultParameters.maximumHeapSize): CordaFuture<NodeHandle>

    // TODO This method has been added temporarily, to be deleted once the set of notaries is defined at the network level.
    fun startNotaryNode(providedName: CordaX500Name,
                        rpcUsers: List<User> = emptyList(),
                        verifierType: VerifierType = VerifierType.InMemory,
                        customOverrides: Map<String, Any?> = emptyMap(),
                        //TODO Switch the default value
                        validating: Boolean = true): CordaFuture<NodeHandle>

    /**
     * Helper function for starting a [node] with custom parameters from Java.
     *
     * @param defaultParameters The default parameters for the driver.
     * @param dsl The dsl itself.
     * @return The value returned in the [dsl] closure.
     */
    fun <A> startNode(parameters: NodeParameters): CordaFuture<NodeHandle> {
        return startNode(defaultParameters = parameters)
    }

    fun startNodes(
            nodes: List<CordformNode>,
            startInSameProcess: Boolean? = null,
            maximumHeapSize: String = "200m"
    ): List<CordaFuture<NodeHandle>>

    /**
     * Starts a distributed notary cluster.
     *
     * @param notaryName The legal name of the advertised distributed notary service.
     * @param clusterSize Number of nodes to create for the cluster.
     * @param verifierType The type of transaction verifier to use. See: [VerifierType]
     * @param rpcUsers List of users who are authorised to use the RPC system. Defaults to empty list.
     * @param startInSameProcess Determines if the node should be started inside the same process the Driver is running
     *     in. If null the Driver-level value will be used.
     * @return The [Party] identity of the distributed notary service, and the [NodeInfo]s of the notaries in the cluster.
     */
    fun startNotaryCluster(
            notaryName: CordaX500Name,
            clusterSize: Int = 3,
            verifierType: VerifierType = VerifierType.InMemory,
            rpcUsers: List<User> = emptyList(),
            startInSameProcess: Boolean? = null): CordaFuture<Pair<Party, List<NodeHandle>>>

    /** Call [startWebserver] with a default maximumHeapSize. */
    fun startWebserver(handle: NodeHandle): CordaFuture<WebserverHandle> = startWebserver(handle, "200m")

    /**
     * Starts a web server for a node
     * @param handle The handle for the node that this webserver connects to via RPC.
     * @param maximumHeapSize Argument for JVM -Xmx option e.g. "200m".
     */
    fun startWebserver(handle: NodeHandle, maximumHeapSize: String): CordaFuture<WebserverHandle>

    /**
     * Starts a network map service node. Note that only a single one should ever be running, so you will probably want
     * to set networkMapStartStrategy to Dedicated(false) in your [driver] call.
     * @param startInProcess Determines if the node should be started inside this process. If null the Driver-level
     *     value will be used.
     */
    fun startDedicatedNetworkMapService(startInProcess: Boolean? = null, maximumHeapSize: String = "200m"): CordaFuture<NodeHandle>

    fun waitForAllNodesToFinish()

    /**
     * Polls a function until it returns a non-null value. Note that there is no timeout on the polling.
     *
     * @param pollName A description of what is being polled.
     * @param pollInterval The interval of polling.
     * @param warnCount The number of polls after the Driver gives a warning.
     * @param check The function being polled.
     * @return A future that completes with the non-null value [check] has returned.
     */
    fun <A> pollUntilNonNull(pollName: String, pollInterval: Duration = 500.millis, warnCount: Int = 120, check: () -> A?): CordaFuture<A>

    /**
     * Polls the given function until it returns true.
     * @see pollUntilNonNull
     */
    fun pollUntilTrue(pollName: String, pollInterval: Duration = 500.millis, warnCount: Int = 120, check: () -> Boolean): CordaFuture<Unit> {
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
    abstract val configuration: FullNodeConfiguration
    abstract val webAddress: NetworkHostAndPort

    data class OutOfProcess(
            override val nodeInfo: NodeInfo,
            override val rpc: CordaRPCOps,
            override val configuration: FullNodeConfiguration,
            override val webAddress: NetworkHostAndPort,
            val debugPort: Int?,
            val process: Process
    ) : NodeHandle() {
        override fun stop(): CordaFuture<Unit> {
            with(process) {
                destroy()
                waitFor()
            }
            return doneFuture(Unit)
        }
    }

    data class InProcess(
            override val nodeInfo: NodeInfo,
            override val rpc: CordaRPCOps,
            override val configuration: FullNodeConfiguration,
            override val webAddress: NetworkHostAndPort,
            val node: StartedNode<Node>,
            val nodeThread: Thread
    ) : NodeHandle() {
        override fun stop(): CordaFuture<Unit> {
            node.dispose()
            with(nodeThread) {
                interrupt()
                join()
            }
            return doneFuture(Unit)
        }
    }

    fun rpcClientToNode(): CordaRPCClient = CordaRPCClient(configuration.rpcAddress!!)

    /**
     * Stops the referenced node.
     */
    abstract fun stop(): CordaFuture<Unit>
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

/**
 * Helper builder for configuring a [Node] from Java.
 */
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
 * The driver implicitly bootstraps a [NetworkMapService].
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
 * @param networkMapStartStrategy Determines whether a network map node is started automatically.
 * @param startNodesInProcess Provides the default behaviour of whether new nodes should start inside this process or
 *     not. Note that this may be overridden in [DriverDSLExposedInterface.startNode].
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
        networkMapStartStrategy: NetworkMapStartStrategy = defaultParameters.networkMapStartStrategy,
        startNodesInProcess: Boolean = defaultParameters.startNodesInProcess,
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
                    networkMapStartStrategy = networkMapStartStrategy,
                    startNodesInProcess = startNodesInProcess,
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
 * @param defaultParameters The default parameters for the driver.
 * @param dsl The dsl itself.
 * @return The value returned in the [dsl] closure.
 */
fun <A> driver(
        parameters: DriverParameters,
        dsl: DriverDSLExposedInterface.() -> A
): A {
    return driver(defaultParameters = parameters, dsl = dsl)
}

/**
 * Helper builder for configuring a [driver] from Java.
 */
data class DriverParameters(
        val isDebug: Boolean = false,
        val driverDirectory: Path = Paths.get("build", getTimestampAsDirectoryName()),
        val portAllocation: PortAllocation = PortAllocation.Incremental(10000),
        val debugPortAllocation: PortAllocation = PortAllocation.Incremental(5005),
        val systemProperties: Map<String, String> = emptyMap(),
        val useTestClock: Boolean = false,
        val initialiseSerialization: Boolean = true,
        val networkMapStartStrategy: NetworkMapStartStrategy = NetworkMapStartStrategy.Dedicated(startAutomatically = true),
        val startNodesInProcess: Boolean = false,
        val extraCordappPackagesToScan: List<String> = emptyList()
) {
    fun setIsDebug(isDebug: Boolean) = copy(isDebug = isDebug)
    fun setDriverDirectory(driverDirectory: Path) = copy(driverDirectory = driverDirectory)
    fun setPortAllocation(portAllocation: PortAllocation) = copy(portAllocation = portAllocation)
    fun setDebugPortAllocation(debugPortAllocation: PortAllocation) = copy(debugPortAllocation = debugPortAllocation)
    fun setSystemProperties(systemProperties: Map<String, String>) = copy(systemProperties = systemProperties)
    fun setUseTestClock(useTestClock: Boolean) = copy(useTestClock = useTestClock)
    fun setInitialiseSerialization(initialiseSerialization: Boolean) = copy(initialiseSerialization = initialiseSerialization)
    fun setNetworkMapStartStrategy(networkMapStartStrategy: NetworkMapStartStrategy) = copy(networkMapStartStrategy = networkMapStartStrategy)
    fun setStartNodesInProcess(startNodesInProcess: Boolean) = copy(startNodesInProcess = startNodesInProcess)
    fun setExtraCordappPackagesToScan(extraCordappPackagesToScan: List<String>) = copy(extraCordappPackagesToScan = extraCordappPackagesToScan)
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
    if (initialiseSerialization) initialiseTestSerialization()
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
        if (initialiseSerialization) resetTestSerialization()
    }
}

fun getTimestampAsDirectoryName(): String {
    return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(UTC).format(Instant.now())
}

class ListenProcessDeathException(hostAndPort: NetworkHostAndPort, listenProcess: Process) : CordaException("The process that was expected to listen on $hostAndPort has died with status: ${listenProcess.exitValue()}")

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
 * It's been observed that nodes can take up to 30 seconds to shut down, so just to stay on the safe side the 40 seconds
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

class ShutdownManager(private val executorService: ExecutorService) {
    private class State {
        val registeredShutdowns = ArrayList<CordaFuture<() -> Unit>>()
        var isShutdown = false
    }

    private val state = ThreadBox(State())

    companion object {
        inline fun <A> run(providedExecutorService: ExecutorService? = null, block: ShutdownManager.() -> A): A {
            val executorService = providedExecutorService ?: Executors.newScheduledThreadPool(1)
            val shutdownManager = ShutdownManager(executorService)
            try {
                return block(shutdownManager)
            } finally {
                shutdownManager.shutdown()
                providedExecutorService ?: executorService.shutdown()
            }
        }
    }

    fun shutdown() {
        val shutdownActionFutures = state.locked {
            if (isShutdown) {
                emptyList<CordaFuture<() -> Unit>>()
            } else {
                isShutdown = true
                registeredShutdowns
            }
        }
        val shutdowns = shutdownActionFutures.map { Try.on { it.getOrThrow(1.seconds) } }
        shutdowns.reversed().forEach {
            when (it) {
                is Try.Success ->
                    try {
                        it.value()
                    } catch (t: Throwable) {
                        log.warn("Exception while shutting down", t)
                    }
                is Try.Failure -> log.warn("Exception while getting shutdown method, disregarding", it.exception)
            }
        }
    }

    fun registerShutdown(shutdown: CordaFuture<() -> Unit>) {
        state.locked {
            require(!isShutdown)
            registeredShutdowns.add(shutdown)
        }
    }

    fun registerShutdown(shutdown: () -> Unit) = registerShutdown(doneFuture(shutdown))

    fun registerProcessShutdown(processFuture: CordaFuture<Process>) {
        val processShutdown = processFuture.map { process ->
            {
                process.destroy()
                /** Wait 5 seconds, then [Process.destroyForcibly] */
                val finishedFuture = executorService.submit {
                    process.waitFor()
                }
                try {
                    finishedFuture.get(5, SECONDS)
                } catch (exception: TimeoutException) {
                    finishedFuture.cancel(true)
                    process.destroyForcibly()
                }
                Unit
            }
        }
        registerShutdown(processShutdown)
    }

    interface Follower {
        fun unfollow()
        fun shutdown()
    }

    fun follower() = object : Follower {
        private val start = state.locked { registeredShutdowns.size }
        private val end = AtomicInteger(start - 1)
        override fun unfollow() = end.set(state.locked { registeredShutdowns.size })
        override fun shutdown() = end.get().let { end ->
            start > end && throw IllegalStateException("You haven't called unfollow.")
            state.locked {
                registeredShutdowns.subList(start, end).listIterator(end - start).run {
                    while (hasPrevious()) {
                        previous().getOrThrow().invoke()
                        set(doneFuture {}) // Don't break other followers by doing a remove.
                    }
                }
            }
        }
    }
}

class DriverDSL(
        val portAllocation: PortAllocation,
        val debugPortAllocation: PortAllocation,
        val systemProperties: Map<String, String>,
        val driverDirectory: Path,
        val useTestClock: Boolean,
        val isDebug: Boolean,
        val networkMapStartStrategy: NetworkMapStartStrategy,
        val startNodesInProcess: Boolean,
        extraCordappPackagesToScan: List<String>
) : DriverDSLInternalInterface {
    private val dedicatedNetworkMapAddress = portAllocation.nextHostAndPort()
    private var _executorService: ScheduledExecutorService? = null
    val executorService get() = _executorService!!
    private var _shutdownManager: ShutdownManager? = null
    override val shutdownManager get() = _shutdownManager!!
    private val cordappPackages = extraCordappPackagesToScan + getCallerPackage()

    class State {
        val processes = ArrayList<CordaFuture<Process>>()
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

    fun registerProcess(process: CordaFuture<Process>) {
        shutdownManager.registerProcessShutdown(process)
        state.locked {
            processes.add(process)
        }
    }

    override fun waitForAllNodesToFinish() = state.locked {
        processes.transpose().get().forEach {
            it.waitFor()
        }
    }

    override fun shutdown() {
        _shutdownManager?.shutdown()
        _executorService?.shutdownNow()
    }

    private fun establishRpc(config: FullNodeConfiguration, processDeathFuture: CordaFuture<out Process>): CordaFuture<CordaRPCOps> {
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

    private fun networkMapServiceConfigLookup(networkMapCandidates: List<NodeDefinition>): (CordaX500Name) -> Map<String, String>? {
        return networkMapStartStrategy.run {
            when (this) {
                is NetworkMapStartStrategy.Dedicated -> {
                    serviceConfig(dedicatedNetworkMapAddress).let {
                        { _: CordaX500Name -> it }
                    }
                }
                is NetworkMapStartStrategy.Nominated -> {
                    serviceConfig(networkMapCandidates.single {
                        it.name == legalName.toString()
                    }.config.getString("p2pAddress").let(NetworkHostAndPort.Companion::parse)).let {
                        { nodeName: CordaX500Name -> if (nodeName == legalName) null else it }
                    }
                }
            }
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
        val networkMapServiceConfigLookup = networkMapServiceConfigLookup(listOf(object : NodeDefinition {
            override fun getName() = name.toString()
            override fun getConfig() = configOf("p2pAddress" to p2pAddress.toString())
        }))
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory(name),
                allowMissingConfig = true,
                configOverrides = configOf(
                        "myLegalName" to name.toString(),
                        "p2pAddress" to p2pAddress.toString(),
                        "rpcAddress" to rpcAddress.toString(),
                        "webAddress" to webAddress.toString(),
                        "networkMapService" to networkMapServiceConfigLookup(name),
                        "useTestClock" to useTestClock,
                        "rpcUsers" to if (rpcUsers.isEmpty()) defaultRpcUserList else rpcUsers.map { it.toMap() },
                        "verifierType" to verifierType.name
                ) + customOverrides
        )
        return startNodeInternal(config, webAddress, startInSameProcess, maximumHeapSize)
    }

    override fun startNotaryNode(providedName: CordaX500Name,
                                 rpcUsers: List<User>,
                                 verifierType: VerifierType,
                                 customOverrides: Map<String, Any?>,
                                 validating: Boolean): CordaFuture<NodeHandle> {
        val config = customOverrides + NotaryConfig(validating).toConfigMap()
        return startNode(providedName = providedName, rpcUsers = rpcUsers, verifierType = verifierType, customOverrides = config)
    }

    override fun startNodes(nodes: List<CordformNode>, startInSameProcess: Boolean?, maximumHeapSize: String): List<CordaFuture<NodeHandle>> {
        val networkMapServiceConfigLookup = networkMapServiceConfigLookup(nodes)
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
                            "networkMapService" to networkMapServiceConfigLookup(name),
                            "rpcUsers" to if (rpcUsers.isEmpty()) defaultRpcUserList else rpcUsers
                    )
            )
            startNodeInternal(config, webAddress, startInSameProcess, maximumHeapSize)
        }
    }

    // TODO This mapping is done is several plaecs including the gradle plugin. In general we need a better way of
    // generating the configs for the nodes, probably making use of Any.toConfig()
    private fun NotaryConfig.toConfigMap(): Map<String, Any> = mapOf("notary" to toConfig().root().unwrapped())

    override fun startNotaryCluster(
            notaryName: CordaX500Name,
            clusterSize: Int,
            verifierType: VerifierType,
            rpcUsers: List<User>,
            startInSameProcess: Boolean?
    ): CordaFuture<Pair<Party, List<NodeHandle>>> {
        fun notaryConfig(nodeAddress: NetworkHostAndPort, clusterAddress: NetworkHostAndPort? = null): Map<String, Any> {
            val clusterAddresses = if (clusterAddress != null) listOf(clusterAddress) else emptyList()
            val config = NotaryConfig(validating = true, raft = RaftConfig(nodeAddress = nodeAddress, clusterAddresses = clusterAddresses))
            return config.toConfigMap()
        }

        val nodeNames = (0 until clusterSize).map { CordaX500Name("Notary Service $it", "Zurich", "CH") }
        val paths = nodeNames.map { baseDirectory(it) }
        ServiceIdentityGenerator.generateToDisk(paths, notaryName)
        val clusterAddress = portAllocation.nextHostAndPort()

        // Start the first node that will bootstrap the cluster
        val firstNotaryFuture = startNode(
                providedName = nodeNames.first(),
                rpcUsers = rpcUsers,
                verifierType = verifierType,
                customOverrides = notaryConfig(clusterAddress) + mapOf(
                        "database.serverNameTablePrefix" to if (nodeNames.isNotEmpty()) nodeNames.first().toString().replace(Regex("[^0-9A-Za-z]+"), "") else ""
                ),
                startInSameProcess = startInSameProcess
        )
        // All other nodes will join the cluster
        val restNotaryFutures = nodeNames.drop(1).map {
            val nodeAddress = portAllocation.nextHostAndPort()
            startNode(
                    providedName = it,
                    rpcUsers = rpcUsers,
                    verifierType = verifierType,
                    customOverrides = notaryConfig(nodeAddress, clusterAddress) + mapOf(
                            "database.serverNameTablePrefix" to it.toString().replace(Regex("[^0-9A-Za-z]+"), "")
                    ))
        }

        return firstNotaryFuture.flatMap { firstNotary ->
            val notaryParty = firstNotary.nodeInfo.legalIdentities[1] // TODO For now the second identity is notary identity.
            restNotaryFutures.transpose().map { restNotaries ->
                Pair(notaryParty, listOf(firstNotary) + restNotaries)
            }
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
        val processFuture = DriverDSL.startWebserver(executorService, handle, debugPort, maximumHeapSize)
        registerProcess(processFuture)
        return processFuture.map { queryWebserver(handle, it) }
    }

    override fun start() {
        _executorService = Executors.newScheduledThreadPool(2, ThreadFactoryBuilder().setNameFormat("driver-pool-thread-%d").build())
        _shutdownManager = ShutdownManager(executorService)
        if (networkMapStartStrategy.startDedicated) {
            startDedicatedNetworkMapService().andForget(log) // Allow it to start concurrently with other nodes.
        }
    }

    fun baseDirectory(nodeName: CordaX500Name): Path {
        val nodeDirectoryName = String(nodeName.organisation.filter { !it.isWhitespace() }.toCharArray())
        return driverDirectory / nodeDirectoryName

    }

    override fun baseDirectory(nodeName: String): Path = baseDirectory(CordaX500Name.parse(nodeName))

    override fun startDedicatedNetworkMapService(startInProcess: Boolean?, maximumHeapSize: String): CordaFuture<NodeHandle> {
        val webAddress = portAllocation.nextHostAndPort()
        val rpcAddress = portAllocation.nextHostAndPort()
        val networkMapLegalName = networkMapStartStrategy.legalName
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory(networkMapLegalName),
                allowMissingConfig = true,
                configOverrides = configOf(
                        "myLegalName" to networkMapLegalName.toString(),
                        // TODO: remove the webAddress as NMS doesn't need to run a web server. This will cause all
                        //       node port numbers to be shifted, so all demos and docs need to be updated accordingly.
                        "webAddress" to webAddress.toString(),
                        "rpcAddress" to rpcAddress.toString(),
                        "rpcUsers" to defaultRpcUserList,
                        "p2pAddress" to dedicatedNetworkMapAddress.toString(),
                        "useTestClock" to useTestClock)
        )
        return startNodeInternal(config, webAddress, startInProcess, maximumHeapSize)
    }

    private fun startNodeInternal(config: Config, webAddress: NetworkHostAndPort, startInProcess: Boolean?, maximumHeapSize: String): CordaFuture<NodeHandle> {
        val nodeConfiguration = config.parseAs<FullNodeConfiguration>()
        if (startInProcess ?: startNodesInProcess) {
            val nodeAndThreadFuture = startInProcessNode(executorService, nodeConfiguration, config, cordappPackages)
            shutdownManager.registerShutdown(
                    nodeAndThreadFuture.map { (node, thread) ->
                        {
                            node.dispose()
                            thread.interrupt()
                        }
                    }
            )
            return nodeAndThreadFuture.flatMap { (node, thread) ->
                establishRpc(nodeConfiguration, openFuture()).flatMap { rpc ->
                    rpc.waitUntilNetworkReady().map {
                        NodeHandle.InProcess(rpc.nodeInfo(), rpc, nodeConfiguration, webAddress, node, thread)
                    }
                }
            }
        } else {
            val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
            val processFuture = startOutOfProcessNode(executorService, nodeConfiguration, config, quasarJarPath, debugPort, systemProperties, cordappPackages, maximumHeapSize)
            registerProcess(processFuture)
            return processFuture.flatMap { process ->
                val processDeathFuture = poll(executorService, "process death") {
                    if (process.isAlive) null else process
                }
                establishRpc(nodeConfiguration, processDeathFuture).flatMap { rpc ->
                    // Call waitUntilNetworkReady in background in case RPC is failing over:
                    val networkMapFuture = executorService.fork {
                        rpc.waitUntilNetworkReady()
                    }.flatMap { it }
                    firstOf(processDeathFuture, networkMapFuture) {
                        if (it == processDeathFuture) {
                            throw ListenProcessDeathException(nodeConfiguration.p2pAddress, process)
                        }
                        processDeathFuture.cancel(false)
                        NodeHandle.OutOfProcess(rpc.nodeInfo(), rpc, nodeConfiguration, webAddress, debugPort, process)
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
        private val defaultRpcUserList = listOf(User("default", "default", setOf("ALL")).toMap())

        private val names = arrayOf(
                ALICE.name,
                BOB.name,
                DUMMY_BANK_A.name
        )

        private fun <A> oneOf(array: Array<A>) = array[Random().nextInt(array.size)]

        private fun startInProcessNode(
                executorService: ScheduledExecutorService,
                nodeConf: FullNodeConfiguration,
                config: Config,
                cordappPackages: List<String>
        ): CordaFuture<Pair<StartedNode<Node>, Thread>> {
            return executorService.fork {
                log.info("Starting in-process Node ${nodeConf.myLegalName.organisation}")
                // Write node.conf
                writeConfig(nodeConf.baseDirectory, "node.conf", config)
                // TODO pass the version in?
                val node = Node(nodeConf, MOCK_VERSION_INFO, initialiseSerialization = false, cordappLoader = CordappLoader.createDefaultWithTestPackages(nodeConf, cordappPackages)).start()
                val nodeThread = thread(name = nodeConf.myLegalName.organisation) {
                    node.internals.run()
                }
                node to nodeThread
            }.flatMap { nodeAndThread -> addressMustBeBoundFuture(executorService, nodeConf.p2pAddress).map { nodeAndThread } }
        }

        private fun startOutOfProcessNode(
                executorService: ScheduledExecutorService,
                nodeConf: FullNodeConfiguration,
                config: Config,
                quasarJarPath: String,
                debugPort: Int?,
                overriddenSystemProperties: Map<String, String>,
                cordappPackages: List<String>,
                maximumHeapSize: String
        ): CordaFuture<Process> {
            val processFuture = executorService.fork {
                log.info("Starting out-of-process Node ${nodeConf.myLegalName.organisation}")
                // Write node.conf
                writeConfig(nodeConf.baseDirectory, "node.conf", config)

                val systemProperties = overriddenSystemProperties + mapOf(
                        "name" to nodeConf.myLegalName,
                        "visualvm.display.name" to "corda-${nodeConf.myLegalName}",
                        Node.scanPackagesSystemProperty to cordappPackages.joinToString(Node.scanPackagesSeparator),
                        "java.io.tmpdir" to System.getProperty("java.io.tmpdir") // Inherit from parent process
                )
                // See experimental/quasar-hook/README.md for how to generate.
                val excludePattern = "x(antlr**;bftsmart**;ch**;co.paralleluniverse**;com.codahale**;com.esotericsoftware**;com.fasterxml**;com.google**;com.ibm**;com.intellij**;com.jcabi**;com.nhaarman**;com.opengamma**;com.typesafe**;com.zaxxer**;de.javakaffee**;groovy**;groovyjarjarantlr**;groovyjarjarasm**;io.atomix**;io.github**;io.netty**;jdk**;joptsimple**;junit**;kotlin**;net.bytebuddy**;net.i2p**;org.apache**;org.assertj**;org.bouncycastle**;org.codehaus**;org.crsh**;org.dom4j**;org.fusesource**;org.h2**;org.hamcrest**;org.hibernate**;org.jboss**;org.jcp**;org.joda**;org.junit**;org.mockito**;org.objectweb**;org.objenesis**;org.slf4j**;org.w3c**;org.xml**;org.yaml**;reflectasm**;rx**)"
                val extraJvmArguments = systemProperties.map { "-D${it.key}=${it.value}" } +
                        "-javaagent:$quasarJarPath=$excludePattern"
                val loggingLevel = if (debugPort == null) "INFO" else "DEBUG"

                ProcessUtilities.startCordaProcess(
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
            return processFuture.flatMap { process ->
                addressMustBeBoundFuture(executorService, nodeConf.p2pAddress, process).map { process }
            }
        }

        private fun startWebserver(
                executorService: ScheduledExecutorService,
                handle: NodeHandle,
                debugPort: Int?,
                maximumHeapSize: String
        ): CordaFuture<Process> {
            return executorService.fork {
                val className = "net.corda.webserver.WebServer"
                ProcessUtilities.startCordaProcess(
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
            }.flatMap { process -> addressMustBeBoundFuture(executorService, handle.webAddress, process).map { process } }
        }

        private fun getCallerPackage(): String {
            return Exception()
                    .stackTrace
                    .first { it.fileName != "Driver.kt" }
                    .let { Class.forName(it.className).`package`?.name }
                    ?: throw IllegalStateException("Function instantiating driver must be defined in a package.")
        }
    }
}

fun writeConfig(path: Path, filename: String, config: Config) {
    path.toFile().mkdirs()
    File("$path/$filename").writeText(config.root().render(ConfigRenderOptions.defaults()))
}

