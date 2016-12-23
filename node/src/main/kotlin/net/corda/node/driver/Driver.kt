@file:JvmName("Driver")

package net.corda.node.driver

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.core.*
import net.corda.core.crypto.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.utilities.loggerFor
import net.corda.node.services.User
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.config.NodeSSLConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.services.messaging.NodeMessagingClient
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import org.slf4j.Logger
import java.io.File
import java.net.*
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
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
     * Starts a [Node] in a separate process.
     *
     * @param providedName Optional name of the node, which will be its legal name in [Party]. Defaults to something
     *   random. Note that this must be unique as the driver uses it as a primary key!
     * @param advertisedServices The set of services to be advertised by the node. Defaults to empty set.
     * @param rpcUsers List of users who are authorised to use the RPC system. Defaults to empty list.
     * @return The [NodeInfo] of the started up node retrieved from the network map service.
     */
    fun startNode(providedName: String? = null,
                  advertisedServices: Set<ServiceInfo> = emptySet(),
                  rpcUsers: List<User> = emptyList(),
                  customOverrides: Map<String, Any?> = emptyMap()): ListenableFuture<NodeHandle>

    /**
     * Starts a distributed notary cluster.
     *
     * @param notaryName The legal name of the advertised distributed notary service.
     * @param clusterSize Number of nodes to create for the cluster.
     * @param type The advertised notary service type. Currently the only supported type is [RaftValidatingNotaryService.type].
     * @param rpcUsers List of users who are authorised to use the RPC system. Defaults to empty list.
     * @return The [Party] identity of the distributed notary service, and the [NodeInfo]s of the notaries in the cluster.
     */
    fun startNotaryCluster(
            notaryName: String,
            clusterSize: Int = 3,
            type: ServiceType = RaftValidatingNotaryService.type,
            rpcUsers: List<User> = emptyList()): Future<Pair<Party, List<NodeHandle>>>

    /**
     * Starts a web server for a node
     *
     * @param handle The handle for the node that this webserver connects to via RPC.
     */
    fun startWebserver(handle: NodeHandle): Future<HostAndPort>

    fun waitForAllNodesToFinish()
}

interface DriverDSLInternalInterface : DriverDSLExposedInterface {
    fun start()
    fun shutdown()
}

data class NodeHandle(
        val nodeInfo: NodeInfo,
        val configuration: FullNodeConfiguration,
        val process: Process
) {
    fun rpcClientToNode(): CordaRPCClient = CordaRPCClient(configuration.artemisAddress, configuration)
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
 *     val noService = startNode("NoService")
 *     val notary = startNode("Notary")
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
 * @param useTestClock If true the test clock will be used in Node.
 * @param isDebug Indicates whether the spawned nodes should start in jdwt debug mode.
 * @param dsl The dsl itself.
 * @return The value returned in the [dsl] closure.
 */
@JvmOverloads
fun <A> driver(
        isDebug: Boolean = false,
        driverDirectory: Path = Paths.get("build", getTimestampAsDirectoryName()),
        portAllocation: PortAllocation = PortAllocation.Incremental(10000),
        debugPortAllocation: PortAllocation = PortAllocation.Incremental(5005),
        useTestClock: Boolean = false,
        dsl: DriverDSLExposedInterface.() -> A
) = genericDriver(
        driverDsl = DriverDSL(
                portAllocation = portAllocation,
                debugPortAllocation = debugPortAllocation,
                driverDirectory = driverDirectory.toAbsolutePath(),
                useTestClock = useTestClock,
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
            Socket(hostAndPort.hostText, hostAndPort.port).close()
            Unit
        } catch (_exception: SocketException) {
            null
        }
    }
}

fun addressMustNotBeBound(executorService: ScheduledExecutorService, hostAndPort: HostAndPort): ListenableFuture<Unit> {
    return poll(executorService, "address $hostAndPort to unbind") {
        try {
            Socket(hostAndPort.hostText, hostAndPort.port).close()
            null
        } catch (_exception: SocketException) {
            Unit
        }
    }
}

private fun <A> poll(
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

open class DriverDSL(
        val portAllocation: PortAllocation,
        val debugPortAllocation: PortAllocation,
        val driverDirectory: Path,
        val useTestClock: Boolean,
        val isDebug: Boolean
) : DriverDSLInternalInterface {
    private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val networkMapLegalName = "NetworkMapService"
    private val networkMapAddress = portAllocation.nextHostAndPort()

    class State {
        val registeredProcesses = LinkedList<ListenableFuture<Process>>()
        val clients = LinkedList<NodeMessagingClient>()
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

    fun registerProcess(process: ListenableFuture<Process>) = state.locked { registeredProcesses.push(process) }

    override fun waitForAllNodesToFinish() {
        state.locked {
            registeredProcesses.forEach {
                it.getOrThrow().waitFor()
            }
        }
    }

    override fun shutdown() {
        state.locked {
            clients.forEach(NodeMessagingClient::stop)
            registeredProcesses.forEach {
                it.get().destroy()
            }
        }
        /** Wait 5 seconds, then [Process.destroyForcibly] */
        val finishedFuture = executorService.submit {
            waitForAllNodesToFinish()
        }
        try {
            finishedFuture.get(5, SECONDS)
        } catch (exception: TimeoutException) {
            finishedFuture.cancel(true)
            state.locked {
                registeredProcesses.forEach {
                    it.get().destroyForcibly()
                }
            }
        }

        // Check that we shut down properly
        addressMustNotBeBound(executorService, networkMapAddress).get()
        executorService.shutdown()
    }

    private fun queryNodeInfo(nodeAddress: HostAndPort, sslConfig: NodeSSLConfiguration): NodeInfo? {
        var retries = 0
        while (retries < 5) try {
            val client = CordaRPCClient(nodeAddress, sslConfig)
            client.start(ArtemisMessagingComponent.NODE_USER, ArtemisMessagingComponent.NODE_USER)
            val rpcOps = client.proxy(timeout = Duration.of(15, ChronoUnit.SECONDS))
            return rpcOps.nodeIdentity()
        } catch(e: Exception) {
            log.error("Retrying query node info at $nodeAddress")
            retries++
        }

        log.error("Could not query node info after $retries retries")
        return null
    }

    override fun startNode(providedName: String?, advertisedServices: Set<ServiceInfo>,
                           rpcUsers: List<User>, customOverrides: Map<String, Any?>): ListenableFuture<NodeHandle> {
        val messagingAddress = portAllocation.nextHostAndPort()
        val apiAddress = portAllocation.nextHostAndPort()
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val name = providedName ?: "${pickA(name)}-${messagingAddress.port}"

        val baseDirectory = driverDirectory / name
        val configOverrides = mapOf(
                "myLegalName" to name,
                "artemisAddress" to messagingAddress.toString(),
                "webAddress" to apiAddress.toString(),
                "extraAdvertisedServiceIds" to advertisedServices.joinToString(","),
                "networkMapService" to mapOf(
                        "address" to networkMapAddress.toString(),
                        "legalName" to networkMapLegalName
                ),
                "useTestClock" to useTestClock,
                "rpcUsers" to rpcUsers.map {
                    mapOf(
                            "user" to it.username,
                            "password" to it.password,
                            "permissions" to it.permissions
                    )
                }
        ) + customOverrides

        val configuration = FullNodeConfiguration(
                baseDirectory,
                ConfigHelper.loadConfig(
                        baseDirectory = baseDirectory,
                        allowMissingConfig = true,
                        configOverrides = configOverrides
                )
        )

        val startNode = startNode(executorService, configuration, quasarJarPath, debugPort)
        registerProcess(startNode)
        return startNode.map {
            NodeHandle(queryNodeInfo(messagingAddress, configuration)!!, configuration, it)
        }
    }

    override fun startNotaryCluster(
            notaryName: String,
            clusterSize: Int,
            type: ServiceType,
            rpcUsers: List<User>
    ): ListenableFuture<Pair<Party, List<NodeHandle>>> {
        val nodeNames = (1..clusterSize).map { "Notary Node $it" }
        val paths = nodeNames.map { driverDirectory / it }
        ServiceIdentityGenerator.generateToDisk(paths, type.id, notaryName)

        val serviceInfo = ServiceInfo(type, notaryName)
        val advertisedService = setOf(serviceInfo)
        val notaryClusterAddress = portAllocation.nextHostAndPort()

        // Start the first node that will bootstrap the cluster
        val firstNotaryFuture = startNode(nodeNames.first(), advertisedService, rpcUsers, mapOf("notaryNodeAddress" to notaryClusterAddress.toString()))
        // All other nodes will join the cluster
        val restNotaryFutures = nodeNames.drop(1).map {
            val nodeAddress = portAllocation.nextHostAndPort()
            val configOverride = mapOf("notaryNodeAddress" to nodeAddress.toString(), "notaryClusterAddresses" to listOf(notaryClusterAddress.toString()))
            startNode(it, advertisedService, rpcUsers, configOverride)
        }

        return firstNotaryFuture.flatMap { firstNotary ->
            val notaryParty = firstNotary.nodeInfo.notaryIdentity
            Futures.allAsList(restNotaryFutures).map { restNotaries ->
                Pair(notaryParty, listOf(firstNotary) + restNotaries)
            }
        }
    }

    override fun startWebserver(handle: NodeHandle): Future<HostAndPort> {
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null

        return future {
            registerProcess(DriverDSL.startWebserver(executorService, handle.configuration, debugPort))
            handle.configuration.webAddress
        }
    }

    override fun start() {
        startNetworkMapService()
    }

    private fun startNetworkMapService(): ListenableFuture<Process> {
        val apiAddress = portAllocation.nextHostAndPort()
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null

        val baseDirectory = driverDirectory / networkMapLegalName
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory,
                allowMissingConfig = true,
                configOverrides = mapOf(
                        "myLegalName" to networkMapLegalName,
                        "artemisAddress" to networkMapAddress.toString(),
                        "webAddress" to apiAddress.toString(),
                        "extraAdvertisedServiceIds" to "",
                        "useTestClock" to useTestClock
                )
        )

        log.info("Starting network-map-service")
        val startNode = startNode(executorService, FullNodeConfiguration(baseDirectory, config), quasarJarPath, debugPort)
        registerProcess(startNode)
        return startNode
    }

    companion object {
        val name = arrayOf(
                "Alice",
                "Bob",
                "Bank"
        )

        fun <A> pickA(array: Array<A>): A = array[Math.abs(Random().nextInt()) % array.size]

        private fun startNode(
                executorService: ScheduledExecutorService,
                nodeConf: FullNodeConfiguration,
                quasarJarPath: String,
                debugPort: Int?
        ): ListenableFuture<Process> {
            // Write node.conf
            writeConfig(nodeConf.baseDirectory, "node.conf", nodeConf.config)

            val className = "net.corda.node.Corda" // cannot directly get class for this, so just use string
            val separator = System.getProperty("file.separator")
            val classpath = System.getProperty("java.class.path")
            val path = System.getProperty("java.home") + separator + "bin" + separator + "java"

            val debugPortArg = if (debugPort != null)
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort"
            else
                ""

            val javaArgs = listOf(
                    path,
                    "-Dname=${nodeConf.myLegalName}",
                    "-javaagent:$quasarJarPath",
                    debugPortArg,
                    "-Dvisualvm.display.name=Corda",
                    "-Xmx200m",
                    "-XX:+UseG1GC",
                    "-cp", classpath,
                    className,
                    "--base-directory=${nodeConf.baseDirectory}"
            ).filter(String::isNotEmpty)
            val builder = ProcessBuilder(javaArgs)
            builder.redirectError(Paths.get("error.$className.log").toFile())
            builder.inheritIO()
            builder.directory(nodeConf.baseDirectory.toFile())
            val process = builder.start()
            // TODO There is a race condition here. Even though the messaging address is bound it may be the case that
            // the handlers for the advertised services are not yet registered. Needs rethinking.
            return addressMustBeBound(executorService, nodeConf.artemisAddress).map { process }
        }

        private fun startWebserver(
                executorService: ScheduledExecutorService,
                nodeConf: FullNodeConfiguration,
                debugPort: Int?): ListenableFuture<Process> {
            val className = "net.corda.node.webserver.MainKt" // cannot directly get class for this, so just use string
            val separator = System.getProperty("file.separator")
            val classpath = System.getProperty("java.class.path")
            val path = System.getProperty("java.home") + separator + "bin" + separator + "java"

            val debugPortArg = if (debugPort != null)
                listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort")
            else
                emptyList()

            val javaArgs = listOf(path) +
                    listOf("-Dname=node-${nodeConf.artemisAddress}-webserver") + debugPortArg +
                    listOf(
                            "-cp", classpath, className,
                            "--base-directory", nodeConf.baseDirectory.toString(),
                            "--web-address", nodeConf.webAddress.toString())
            val builder = ProcessBuilder(javaArgs)
            builder.redirectError(Paths.get("error.$className.log").toFile())
            builder.inheritIO()
            builder.directory(nodeConf.baseDirectory.toFile())
            val process = builder.start()
            return addressMustBeBound(executorService, nodeConf.webAddress).map { process }
        }
    }
}

fun writeConfig(path: Path, filename: String, config: Config) {
    path.toFile().mkdirs()
    File("$path/$filename").writeText(config.root().render(ConfigRenderOptions.concise()))
}
