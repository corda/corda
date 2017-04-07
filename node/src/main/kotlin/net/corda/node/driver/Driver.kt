@file:JvmName("Driver")

package net.corda.node.driver

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.ThreadBox
import net.corda.core.crypto.Party
import net.corda.core.div
import net.corda.core.flatMap
import net.corda.core.map
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.utilities.*
import net.corda.node.LOGS_DIRECTORY_NAME
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.config.VerifierType
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.nodeapi.ArtemisMessagingComponent
import net.corda.nodeapi.User
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.nodeapi.config.parseAs
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.Logger
import java.io.File
import java.net.*
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger


/**
 * This file defines a small "Driver" DSL for starting up nodes that is only intended for development, demos and tests.
 *
 * The process the driver is run in behaves as an Artemis client and starts up other processes. Namely it first
 * bootstraps a network map service to allow the specified nodes to connect to, then starts up the actual nodes.
 */

private val log: Logger = loggerFor<DriverDSL>()

/**
 * This is the interface that's exposed to DSL users.
 */
interface DriverDSLExposedInterface {
    /**
     * Starts a [net.corda.node.internal.Node] in a separate process.
     *
     * @param providedName Optional name of the node, which will be its legal name in [Party]. Defaults to something
     *   random. Note that this must be unique as the driver uses it as a primary key!
     * @param advertisedServices The set of services to be advertised by the node. Defaults to empty set.
     * @param verifierType The type of transaction verifier to use. See: [VerifierType]
     * @param rpcUsers List of users who are authorised to use the RPC system. Defaults to empty list.
     * @return The [NodeInfo] of the started up node retrieved from the network map service.
     */
    fun startNode(providedName: String? = null,
                  advertisedServices: Set<ServiceInfo> = emptySet(),
                  rpcUsers: List<User> = emptyList(),
                  verifierType: VerifierType = VerifierType.InMemory,
                  customOverrides: Map<String, Any?> = emptyMap()): ListenableFuture<NodeHandle>

    /**
     * Starts a distributed notary cluster.
     *
     * @param notaryName The legal name of the advertised distributed notary service.
     * @param clusterSize Number of nodes to create for the cluster.
     * @param type The advertised notary service type. Currently the only supported type is [RaftValidatingNotaryService.type].
     * @param verifierType The type of transaction verifier to use. See: [VerifierType]
     * @param rpcUsers List of users who are authorised to use the RPC system. Defaults to empty list.
     * @return The [Party] identity of the distributed notary service, and the [NodeInfo]s of the notaries in the cluster.
     */
    fun startNotaryCluster(
            notaryName: String,
            clusterSize: Int = 3,
            type: ServiceType = RaftValidatingNotaryService.type,
            verifierType: VerifierType = VerifierType.InMemory,
            rpcUsers: List<User> = emptyList()): Future<Pair<Party, List<NodeHandle>>>

    /**
     * Starts a web server for a node
     *
     * @param handle The handle for the node that this webserver connects to via RPC.
     */
    fun startWebserver(handle: NodeHandle): ListenableFuture<HostAndPort>

    /**
     * Starts a network map service node. Note that only a single one should ever be running, so you will probably want
     * to set automaticallyStartNetworkMap to false in your [driver] call.
     */
    fun startNetworkMapService()

    fun waitForAllNodesToFinish()
}

interface DriverDSLInternalInterface : DriverDSLExposedInterface {
    fun start()
    fun shutdown()
}

data class NodeHandle(
        val nodeInfo: NodeInfo,
        val rpc: CordaRPCOps,
        val configuration: FullNodeConfiguration,
        val webAddress: HostAndPort,
        val process: Process
) {
    fun rpcClientToNode(): CordaRPCClient = CordaRPCClient(configuration.rpcAddress!!)
}

sealed class PortAllocation {
    abstract fun nextPort(): Int
    fun nextHostAndPort(): HostAndPort = HostAndPort.fromParts("localhost", nextPort())

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
 * [driver] allows one to start up nodes like this:
 *   driver {
 *     val noService = startNode(DUMMY_BANK_A.name)
 *     val notary = startNode(DUMMY_NOTARY.name)
 *
 *     (...)
 *   }
 *
 * Note that [DriverDSL.startNode] does not wait for the node to start up synchronously, but rather returns a [Future]
 * of the [NodeInfo] that may be waited on, which completes when the new node registered with the network map service.
 *
 * The driver implicitly bootstraps a [NetworkMapService].
 *
 * @param driverDirectory The base directory node directories go into, defaults to "build/<timestamp>/". The node
 *   directories themselves are "<baseDirectory>/<legalName>/", where legalName defaults to "<randomName>-<messagingPort>"
 *   and may be specified in [DriverDSL.startNode].
 * @param portAllocation The port allocation strategy to use for the messaging and the web server addresses. Defaults to incremental.
 * @param debugPortAllocation The port allocation strategy to use for jvm debugging. Defaults to incremental.
 * @param systemProperties A Map of extra system properties which will be given to each new node. Defaults to empty.
 * @param useTestClock If true the test clock will be used in Node.
 * @param isDebug Indicates whether the spawned nodes should start in jdwt debug mode and have debug level logging.
 * @param dsl The dsl itself.
 * @return The value returned in the [dsl] closure.
 */
@JvmOverloads
fun <A> driver(
        isDebug: Boolean = false,
        driverDirectory: Path = Paths.get("build", getTimestampAsDirectoryName()),
        portAllocation: PortAllocation = PortAllocation.Incremental(10000),
        debugPortAllocation: PortAllocation = PortAllocation.Incremental(5005),
        systemProperties: Map<String, String> = emptyMap(),
        useTestClock: Boolean = false,
        automaticallyStartNetworkMap: Boolean = true,
        dsl: DriverDSLExposedInterface.() -> A
) = genericDriver(
        driverDsl = DriverDSL(
                portAllocation = portAllocation,
                debugPortAllocation = debugPortAllocation,
                systemProperties = systemProperties,
                driverDirectory = driverDirectory.toAbsolutePath(),
                useTestClock = useTestClock,
                automaticallyStartNetworkMap = automaticallyStartNetworkMap,
                isDebug = isDebug
        ),
        coerce = { it },
        dsl = dsl
)

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
        coerce: (D) -> DI,
        dsl: DI.() -> A
): A {
    var shutdownHook: Thread? = null
    try {
        driverDsl.start()
        val returnValue = dsl(coerce(driverDsl))
        shutdownHook = Thread({
            driverDsl.shutdown()
        })
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        return returnValue
    } catch (exception: Throwable) {
        println("Driver shutting down because of exception $exception")
        exception.printStackTrace()
        throw exception
    } finally {
        driverDsl.shutdown()
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }
    }
}

fun getTimestampAsDirectoryName(): String {
    return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(UTC).format(Instant.now())
}

fun addressMustBeBound(executorService: ScheduledExecutorService, hostAndPort: HostAndPort): ListenableFuture<Unit> {
    return poll(executorService, "address $hostAndPort to bind") {
        try {
            Socket(hostAndPort.host, hostAndPort.port).close()
            Unit
        } catch (_exception: SocketException) {
            null
        }
    }
}

fun addressMustNotBeBound(executorService: ScheduledExecutorService, hostAndPort: HostAndPort): ListenableFuture<Unit> {
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
        pollIntervalMs: Long = 500,
        warnCount: Int = 120,
        check: () -> A?
): ListenableFuture<A> {
    val initialResult = check()
    val resultFuture = SettableFuture.create<A>()
    if (initialResult != null) {
        resultFuture.set(initialResult)
        return resultFuture
    }
    var counter = 0
    fun schedulePoll() {
        executorService.schedule({
            counter++
            if (counter == warnCount) {
                log.warn("Been polling $pollName for ${pollIntervalMs * warnCount / 1000.0} seconds...")
            }
            val result = check()
            if (result == null) {
                schedulePoll()
            } else {
                resultFuture.set(result)
            }
        }, pollIntervalMs, MILLISECONDS)
    }
    schedulePoll()
    return resultFuture
}

class ShutdownManager(private val executorService: ExecutorService) {
    private class State {
        val registeredShutdowns = ArrayList<ListenableFuture<() -> Unit>>()
        var isShutdown = false
    }

    private val state = ThreadBox(State())

    fun shutdown() {
        val shutdownFutures = state.locked {
            require(!isShutdown)
            isShutdown = true
            registeredShutdowns
        }
        val shutdownsFuture = Futures.allAsList(shutdownFutures)
        val shutdowns = try {
            shutdownsFuture.get(1, SECONDS)
        } catch (exception: TimeoutException) {
            /** Could not get all of them, collect what we have */
            shutdownFutures.filter { it.isDone }.map { it.get() }
        }
        shutdowns.reversed().forEach { it() }
    }

    fun registerShutdown(shutdown: ListenableFuture<() -> Unit>) {
        state.locked {
            require(!isShutdown)
            registeredShutdowns.add(shutdown)
        }
    }

    fun registerProcessShutdown(processFuture: ListenableFuture<Process>) {
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
}

class DriverDSL(
        val portAllocation: PortAllocation,
        val debugPortAllocation: PortAllocation,
        val systemProperties: Map<String, String>,
        val driverDirectory: Path,
        val useTestClock: Boolean,
        val isDebug: Boolean,
        val automaticallyStartNetworkMap: Boolean
) : DriverDSLInternalInterface {
    private val networkMapLegalName = DUMMY_MAP.name
    private val networkMapAddress = portAllocation.nextHostAndPort()
    val executorService: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(2))
    val shutdownManager = ShutdownManager(executorService)

    class State {
        val processes = ArrayList<ListenableFuture<Process>>()
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

    fun registerProcess(process: ListenableFuture<Process>) {
        shutdownManager.registerProcessShutdown(process)
        state.locked {
            processes.add(process)
        }
    }

    override fun waitForAllNodesToFinish() = state.locked {
        Futures.allAsList(processes).get().forEach {
            it.waitFor()
        }
    }

    override fun shutdown() {
        shutdownManager.shutdown()

        // Check that we shut down properly
        addressMustNotBeBound(executorService, networkMapAddress).get()
        executorService.shutdown()
    }

    private fun establishRpc(nodeAddress: HostAndPort, sslConfig: SSLConfiguration): ListenableFuture<CordaRPCOps> {
        val client = CordaRPCClient(nodeAddress, sslConfig)
        return poll(executorService, "for RPC connection") {
            try {
                client.start(ArtemisMessagingComponent.NODE_USER, ArtemisMessagingComponent.NODE_USER)
                return@poll client.proxy()
            } catch(e: Exception) {
                log.error("Exception $e, Retrying RPC connection at $nodeAddress")
                null
            }
        }
    }

    override fun startNode(
            providedName: String?,
            advertisedServices: Set<ServiceInfo>,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>
    ): ListenableFuture<NodeHandle> {
        val p2pAddress = portAllocation.nextHostAndPort()
        val rpcAddress = portAllocation.nextHostAndPort()
        val webAddress = portAllocation.nextHostAndPort()
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val name = providedName ?: "${pickA(name)}-${p2pAddress.port}"

        val baseDirectory = driverDirectory / name
        val configOverrides = mapOf(
                "myLegalName" to name,
                "p2pAddress" to p2pAddress.toString(),
                "rpcAddress" to rpcAddress.toString(),
                "webAddress" to webAddress.toString(),
                "extraAdvertisedServiceIds" to advertisedServices.map { it.toString() },
                "networkMapService" to mapOf(
                        "address" to networkMapAddress.toString(),
                        "legalName" to networkMapLegalName
                ),
                "useTestClock" to useTestClock,
                "rpcUsers" to rpcUsers.map {
                    mapOf(
                            "username" to it.username,
                            "password" to it.password,
                            "permissions" to it.permissions
                    )
                },
                "verifierType" to verifierType.name
        ) + customOverrides

        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory,
                allowMissingConfig = true,
                configOverrides = configOverrides)
        val configuration = config.parseAs<FullNodeConfiguration>()

        val processFuture = startNode(executorService, configuration, config, quasarJarPath, debugPort, systemProperties)
        registerProcess(processFuture)
        return processFuture.flatMap { process ->
            // We continue to use SSL enabled port for RPC when its for node user.
            establishRpc(p2pAddress, configuration).flatMap { rpc ->
                rpc.waitUntilRegisteredWithNetworkMap().map {
                    NodeHandle(rpc.nodeIdentity(), rpc, configuration, webAddress, process)
                }
            }
        }
    }

    override fun startNotaryCluster(
            notaryName: String,
            clusterSize: Int,
            type: ServiceType,
            verifierType: VerifierType,
            rpcUsers: List<User>
    ): ListenableFuture<Pair<Party, List<NodeHandle>>> {
        val nodeNames = (1..clusterSize).map { "${DUMMY_NOTARY.name} $it" }
        val paths = nodeNames.map { driverDirectory / it }
        ServiceIdentityGenerator.generateToDisk(paths, type.id, notaryName)

        val serviceInfo = ServiceInfo(type, notaryName)
        val advertisedService = setOf(serviceInfo)
        val notaryClusterAddress = portAllocation.nextHostAndPort()

        // Start the first node that will bootstrap the cluster
        val firstNotaryFuture = startNode(nodeNames.first(), advertisedService, rpcUsers, verifierType, mapOf("notaryNodeAddress" to notaryClusterAddress.toString()))
        // All other nodes will join the cluster
        val restNotaryFutures = nodeNames.drop(1).map {
            val nodeAddress = portAllocation.nextHostAndPort()
            val configOverride = mapOf("notaryNodeAddress" to nodeAddress.toString(), "notaryClusterAddresses" to listOf(notaryClusterAddress.toString()))
            startNode(it, advertisedService, rpcUsers, verifierType, configOverride)
        }

        return firstNotaryFuture.flatMap { firstNotary ->
            val notaryParty = firstNotary.nodeInfo.notaryIdentity
            Futures.allAsList(restNotaryFutures).map { restNotaries ->
                Pair(notaryParty, listOf(firstNotary) + restNotaries)
            }
        }
    }

    private fun queryWebserver(handle: NodeHandle, process: Process): HostAndPort {
        val protocol = if (handle.configuration.useHTTPS) "https://" else "http://"
        val url = URL("$protocol${handle.webAddress}/api/status")
        val client = OkHttpClient.Builder().connectTimeout(5, SECONDS).readTimeout(60, SECONDS).build()

        while (process.isAlive) try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.isSuccessful && (response.body().string() == "started")) {
                return handle.webAddress
            }
        } catch(e: ConnectException) {
            log.debug("Retrying webserver info at ${handle.webAddress}")
        }

        throw IllegalStateException("Webserver at ${handle.webAddress} has died")
    }

    override fun startWebserver(handle: NodeHandle): ListenableFuture<HostAndPort> {
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val process = DriverDSL.startWebserver(executorService, handle, debugPort)
        registerProcess(process)
        return process.map {
            queryWebserver(handle, it)
        }
    }

    override fun start() {
        if (automaticallyStartNetworkMap) {
            startNetworkMapService()
        }
    }

    override fun startNetworkMapService() {
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val apiAddress = portAllocation.nextHostAndPort().toString()
        val baseDirectory = driverDirectory / networkMapLegalName
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory,
                allowMissingConfig = true,
                configOverrides = mapOf(
                        "myLegalName" to networkMapLegalName,
                        // TODO: remove the webAddress as NMS doesn't need to run a web server. This will cause all
                        //       node port numbers to be shifted, so all demos and docs need to be updated accordingly.
                        "webAddress" to apiAddress,
                        "p2pAddress" to networkMapAddress.toString(),
                        "useTestClock" to useTestClock
                )
        )

        log.info("Starting network-map-service")
        val startNode = startNode(executorService, config.parseAs<FullNodeConfiguration>(), config, quasarJarPath, debugPort, systemProperties)
        registerProcess(startNode)
    }

    companion object {
        val name = arrayOf(
                ALICE.name,
                BOB.name,
                DUMMY_BANK_A.name
        )

        fun <A> pickA(array: Array<A>): A = array[Math.abs(Random().nextInt()) % array.size]

        private fun startNode(
                executorService: ListeningScheduledExecutorService,
                nodeConf: FullNodeConfiguration,
                config: Config,
                quasarJarPath: String,
                debugPort: Int?,
                overriddenSystemProperties: Map<String, String>
        ): ListenableFuture<Process> {
            return executorService.submit<Process> {
                // Write node.conf
                writeConfig(nodeConf.baseDirectory, "node.conf", config)

                val systemProperties = mapOf(
                        "name" to nodeConf.myLegalName,
                        "visualvm.display.name" to "corda-${nodeConf.myLegalName}"
                ) + overriddenSystemProperties
                val extraJvmArguments = systemProperties.map { "-D${it.key}=${it.value}" } +
                        "-javaagent:$quasarJarPath"
                val loggingLevel = if (debugPort == null) "INFO" else "DEBUG"

                ProcessUtilities.startJavaProcess(
                        className = "net.corda.node.Corda", // cannot directly get class for this, so just use string
                        arguments = listOf(
                                "--base-directory=${nodeConf.baseDirectory}",
                                "--logging-level=$loggingLevel",
                                "--no-local-shell"
                        ),
                        jdwpPort = debugPort,
                        extraJvmArguments = extraJvmArguments,
                        errorLogPath = nodeConf.baseDirectory / LOGS_DIRECTORY_NAME / "error.log",
                        workingDirectory = nodeConf.baseDirectory
                )
            }.flatMap { process -> addressMustBeBound(executorService, nodeConf.p2pAddress).map { process } }
        }

        private fun startWebserver(
                executorService: ListeningScheduledExecutorService,
                handle: NodeHandle,
                debugPort: Int?
        ): ListenableFuture<Process> {
            return executorService.submit<Process> {
                val className = "net.corda.webserver.WebServer"
                ProcessUtilities.startJavaProcess(
                        className = className, // cannot directly get class for this, so just use string
                        arguments = listOf("--base-directory", handle.configuration.baseDirectory.toString()),
                        jdwpPort = debugPort,
                        extraJvmArguments = listOf("-Dname=node-${handle.configuration.p2pAddress}-webserver"),
                        errorLogPath = Paths.get("error.$className.log")
                )
            }.flatMap { process -> addressMustBeBound(executorService, handle.webAddress).map { process } }
        }
    }
}

fun writeConfig(path: Path, filename: String, config: Config) {
    path.toFile().mkdirs()
    File("$path/$filename").writeText(config.root().render(ConfigRenderOptions.defaults()))
}
