@file:JvmName("Driver")

package net.corda.testing.driver

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.internal.times
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.*
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.VerifierType
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.nodeapi.internal.config.User
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.internal.DriverDSLImpl
import net.corda.testing.node.NotarySpec
import net.corda.testing.setGlobalSerialization
import org.slf4j.Logger
import java.net.*
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger

/**
 * This file defines a small "Driver" DSL for starting up nodes that is only intended for development, demos and tests.
 *
 * The process the driver is run in behaves as an Artemis client and starts up other processes.
 *
 * TODO this file is getting way too big, it should be split into several files.
 */
private val log: Logger = loggerFor<DriverDSLImpl>()

/**
 * Object ecapsulating a notary started automatically by the driver.
 */
data class NotaryHandle(val identity: Party, val validating: Boolean, val nodeHandles: CordaFuture<List<NodeHandle>>)

interface DriverDSLInternalInterface : DriverDSL {
    private companion object {
        private val DEFAULT_POLL_INTERVAL = 500.millis
        private const val DEFAULT_WARN_COUNT = 120
    }

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
 * Note that [DriverDSLImpl.startNode] does not wait for the node to start up synchronously, but rather returns a [CordaFuture]
 * of the [NodeInfo] that may be waited on, which completes when the new node registered with the network map service or
 * loaded node data from database.
 *
 * @param defaultParameters The default parameters for the driver. Allows the driver to be configured in builder style
 *   when called from Java code.
 * @param isDebug Indicates whether the spawned nodes should start in jdwt debug mode and have debug level logging.
 * @param driverDirectory The base directory node directories go into, defaults to "build/<timestamp>/". The node
 *   directories themselves are "<baseDirectory>/<legalName>/", where legalName defaults to "<randomName>-<messagingPort>"
 *   and may be specified in [DriverDSLImpl.startNode].
 * @param portAllocation The port allocation strategy to use for the messaging and the web server addresses. Defaults to incremental.
 * @param debugPortAllocation The port allocation strategy to use for jvm debugging. Defaults to incremental.
 * @param systemProperties A Map of extra system properties which will be given to each new node. Defaults to empty.
 * @param useTestClock If true the test clock will be used in Node.
 * @param startNodesInProcess Provides the default behaviour of whether new nodes should start inside this process or
 *     not. Note that this may be overridden in [DriverDSL.startNode].
 * @param notarySpecs The notaries advertised  for this network. These nodes will be started automatically and will be
 * available from [DriverDSL.notaryHandles]. Defaults to a simple validating notary.
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
        dsl: DriverDSL.() -> A
): A {
    return genericDriver(
            driverDsl = DriverDSLImpl(
                    portAllocation = portAllocation,
                    debugPortAllocation = debugPortAllocation,
                    systemProperties = systemProperties,
                    driverDirectory = driverDirectory.toAbsolutePath(),
                    useTestClock = useTestClock,
                    isDebug = isDebug,
                    startNodesInProcess = startNodesInProcess,
                    waitForNodesToFinish = waitForAllNodesToFinish,
                    notarySpecs = notarySpecs,
                    extraCordappPackagesToScan = extraCordappPackagesToScan,
                    compatibilityZone = null
            ),
            coerce = { it },
            dsl = dsl,
            initialiseSerialization = initialiseSerialization
    )
}

// TODO Move CompatibilityZoneParams and internalDriver into internal package

/**
 * @property url The base CZ URL for registration and network map updates
 * @property rootCert If specified then the node will register itself using [url] and expect the registration response
 * to be rooted at this cert.
 */
data class CompatibilityZoneParams(val url: URL, val rootCert: X509Certificate? = null)

fun <A> internalDriver(
        isDebug: Boolean = DriverParameters().isDebug,
        driverDirectory: Path = DriverParameters().driverDirectory,
        portAllocation: PortAllocation = DriverParameters().portAllocation,
        debugPortAllocation: PortAllocation = DriverParameters().debugPortAllocation,
        systemProperties: Map<String, String> = DriverParameters().systemProperties,
        useTestClock: Boolean = DriverParameters().useTestClock,
        initialiseSerialization: Boolean = DriverParameters().initialiseSerialization,
        startNodesInProcess: Boolean = DriverParameters().startNodesInProcess,
        waitForAllNodesToFinish: Boolean = DriverParameters().waitForNodesToFinish,
        notarySpecs: List<NotarySpec> = DriverParameters().notarySpecs,
        extraCordappPackagesToScan: List<String> = DriverParameters().extraCordappPackagesToScan,
        compatibilityZone: CompatibilityZoneParams? = null,
        dsl: DriverDSLImpl.() -> A
): A {
    return genericDriver(
            driverDsl = DriverDSLImpl(
                    portAllocation = portAllocation,
                    debugPortAllocation = debugPortAllocation,
                    systemProperties = systemProperties,
                    driverDirectory = driverDirectory.toAbsolutePath(),
                    useTestClock = useTestClock,
                    isDebug = isDebug,
                    startNodesInProcess = startNodesInProcess,
                    waitForNodesToFinish = waitForAllNodesToFinish,
                    notarySpecs = notarySpecs,
                    extraCordappPackagesToScan = extraCordappPackagesToScan,
                    compatibilityZone = compatibilityZone
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
        dsl: DriverDSL.() -> A
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
    fun setNotarySpecs(notarySpecs: List<NotarySpec>) = copy(notarySpecs = notarySpecs)
    fun setExtraCordappPackagesToScan(extraCordappPackagesToScan: List<String>) = copy(extraCordappPackagesToScan = extraCordappPackagesToScan)
}

/**
 * This is a helper method to allow extending of the DSL, along the lines of
 *   interface SomeOtherExposedDSLInterface : DriverDSL
 *   interface SomeOtherInternalDSLInterface : DriverDSLInternalInterface, SomeOtherExposedDSLInterface
 *   class SomeOtherDSL(val driverDSL : DriverDSLImpl) : DriverDSLInternalInterface by driverDSL, SomeOtherInternalDSLInterface
 *
 * @param coerce We need this explicit coercion witness because we can't put an extra DI : D bound in a `where` clause.
 */
fun <DI : DriverDSL, D : DriverDSLInternalInterface, A> genericDriver(
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
 *   interface SomeOtherExposedDSLInterface : DriverDSL
 *   interface SomeOtherInternalDSLInterface : DriverDSLInternalInterface, SomeOtherExposedDSLInterface
 *   class SomeOtherDSL(val driverDSL : DriverDSLImpl) : DriverDSLInternalInterface by driverDSL, SomeOtherInternalDSLInterface
 *
 * @param coerce We need this explicit coercion witness because we can't put an extra DI : D bound in a `where` clause.
 */
fun <DI : DriverDSL, D : DriverDSLInternalInterface, A> genericDriver(
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
        driverDslWrapper: (DriverDSLImpl) -> D,
        coerce: (D) -> DI,
        dsl: DI.() -> A
): A {
    val serializationEnv = setGlobalSerialization(initialiseSerialization)
    val driverDsl = driverDslWrapper(
            DriverDSLImpl(
                    portAllocation = portAllocation,
                    debugPortAllocation = debugPortAllocation,
                    systemProperties = systemProperties,
                    driverDirectory = driverDirectory.toAbsolutePath(),
                    useTestClock = useTestClock,
                    isDebug = isDebug,
                    startNodesInProcess = startNodesInProcess,
                    waitForNodesToFinish = waitForNodesToFinish,
                    extraCordappPackagesToScan = extraCordappPackagesToScan,
                    notarySpecs = notarySpecs,
                    compatibilityZone = null
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

fun writeConfig(path: Path, filename: String, config: Config) {
    val configString = config.root().render(ConfigRenderOptions.defaults())
    configString.byteInputStream().copyTo(path / filename, REPLACE_EXISTING)
}

