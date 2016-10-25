package com.r3corda.node.driver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.common.net.HostAndPort
import com.r3corda.core.ThreadBox
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.NetworkMapCache
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.config.ConfigHelper
import com.r3corda.node.services.config.FullNodeConfiguration
import com.r3corda.node.services.messaging.ArtemisMessagingComponent
import com.r3corda.node.services.messaging.ArtemisMessagingServer
import com.r3corda.node.services.messaging.NodeMessagingClient
import com.r3corda.node.services.network.InMemoryNetworkMapCache
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.utilities.JsonSupport
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStreamReader
import java.net.*
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.*
import java.util.concurrent.*
import kotlin.concurrent.thread

/**
 * This file defines a small "Driver" DSL for starting up nodes that is only intended for development, demos and tests.
 *
 * The process the driver is run in behaves as an Artemis client and starts up other processes. Namely it first
 * bootstraps a network map service to allow the specified nodes to connect to, then starts up the actual nodes.
 *
 * TODO The driver actually starts up as an Artemis server now that may route traffic. Fix this once the client MessagingService is done.
 * TODO The nodes are started up sequentially which is quite slow. Either speed up node startup or make startup parallel somehow.
 * TODO The driver now polls the network map cache for info about newly started up nodes, this could be done asynchronously(?).
 * TODO The network map service bootstrap is hacky (needs to fake the service's public key in order to retrieve the true one), needs some thought.
 */

private val log: Logger = LoggerFactory.getLogger(DriverDSL::class.java)

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
     * @return The [NodeInfo] of the started up node retrieved from the network map service.
     */
    fun startNode(providedName: String? = null, advertisedServices: Set<ServiceInfo> = setOf()): Future<NodeInfoAndConfig>

    fun waitForAllNodesToFinish()
}

interface DriverDSLInternalInterface : DriverDSLExposedInterface {
    fun start()
    fun shutdown()
}

data class NodeInfoAndConfig(val nodeInfo: NodeInfo, val config: Config)

sealed class PortAllocation {
    abstract fun nextPort(): Int
    fun nextHostAndPort(): HostAndPort = HostAndPort.fromParts("localhost", nextPort())

    class Incremental(private var portCounter: Int) : PortAllocation() {
        override fun nextPort() = portCounter++
    }
    class RandomFree(): PortAllocation() {
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
 * The driver implicitly bootstraps a [NetworkMapService] that may be accessed through a local cache [DriverDSL.networkMapCache].
 *
 * @param baseDirectory The base directory node directories go into, defaults to "build/<timestamp>/". The node
 *   directories themselves are "<baseDirectory>/<legalName>/", where legalName defaults to "<randomName>-<messagingPort>"
 *   and may be specified in [DriverDSL.startNode].
 * @param portAllocation The port allocation strategy to use for the messaging and the web server addresses. Defaults to incremental.
 * @param debugPortAllocation The port allocation strategy to use for jvm debugging. Defaults to incremental.
 * @param useTestClock If true the test clock will be used in Node.
 * @param isDebug Indicates whether the spawned nodes should start in jdwt debug mode.
 * @param dsl The dsl itself.
 * @return The value returned in the [dsl] closure.
  */
fun <A> driver(
        baseDirectory: String = "build/${getTimestampAsDirectoryName()}",
        portAllocation: PortAllocation = PortAllocation.Incremental(10000),
        debugPortAllocation: PortAllocation = PortAllocation.Incremental(5005),
        useTestClock: Boolean = false,
        isDebug: Boolean = false,
        dsl: DriverDSLExposedInterface.() -> A
) = genericDriver(
        driverDsl = DriverDSL(
                portAllocation = portAllocation,
                debugPortAllocation = debugPortAllocation,
                baseDirectory = baseDirectory,
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
    } finally {
        driverDsl.shutdown()
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }
    }
}

private fun getTimestampAsDirectoryName(): String {
    val tz = TimeZone.getTimeZone("UTC")
    val df = SimpleDateFormat("yyyyMMddHHmmss")
    df.timeZone = tz
    return df.format(Date())
}

fun addressMustBeBound(hostAndPort: HostAndPort) {
    poll("address $hostAndPort to bind") {
        try {
            Socket(hostAndPort.hostText, hostAndPort.port).close()
            Unit
        } catch (_exception: SocketException) {
            null
        }
    }
}

fun addressMustNotBeBound(hostAndPort: HostAndPort) {
    poll("address $hostAndPort to unbind") {
        try {
            Socket(hostAndPort.hostText, hostAndPort.port).close()
            null
        } catch (_exception: SocketException) {
            Unit
        }
    }
}

fun <A> poll(pollName: String, pollIntervalMs: Long = 500, warnCount: Int = 120, f: () -> A?): A {
    var counter = 0
    var result = f()
    while (result == null) {
        if (counter == warnCount) {
            log.warn("Been polling $pollName for ${pollIntervalMs * warnCount / 1000.0} seconds...")
        }
        counter = (counter % warnCount) + 1
        Thread.sleep(pollIntervalMs)
        result = f()
    }
    return result
}

open class DriverDSL(
        val portAllocation: PortAllocation,
        val debugPortAllocation: PortAllocation,
        val baseDirectory: String,
        val useTestClock: Boolean,
        val isDebug: Boolean
) : DriverDSLInternalInterface {
    private val networkMapName = "NetworkMapService"
    private val networkMapAddress = portAllocation.nextHostAndPort()

    class State {
        val registeredProcesses = LinkedList<Process>()
        val clients = LinkedList<NodeMessagingClient>()
        var localServer: ArtemisMessagingServer? = null
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

    fun registerProcess(process: Process) = state.locked { registeredProcesses.push(process) }

    override fun waitForAllNodesToFinish() {
        state.locked {
            registeredProcesses.forEach {
                it.waitFor()
            }
        }
    }

    override fun shutdown() {
        state.locked {
            clients.forEach {
                it.stop()
            }
            localServer?.stop()
            registeredProcesses.forEach(Process::destroy)
        }
        /** Wait 5 seconds, then [Process.destroyForcibly] */
        val finishedFuture = Executors.newSingleThreadExecutor().submit {
            waitForAllNodesToFinish()
        }
        try {
            finishedFuture.get(5, TimeUnit.SECONDS)
        } catch (exception: TimeoutException) {
            finishedFuture.cancel(true)
            state.locked {
                registeredProcesses.forEach {
                    it.destroyForcibly()
                }
            }
        }

        // Check that we shut down properly
        state.locked {
            localServer?.run { addressMustNotBeBound(myHostPort) }
        }
        addressMustNotBeBound(networkMapAddress)
    }

    private fun queryNodeInfo(webAddress: HostAndPort): NodeInfo? {
        val url = URL("http://${webAddress.toString()}/api/info")
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) {
                log.error("Received response code ${conn.responseCode} from api/info during startup.")
                return null
            }
            // For now the NodeInfo is tunneled in its Kryo format over the Node's Web interface.
            val om = ObjectMapper()
            val module = SimpleModule("NodeInfo")
            module.addDeserializer(NodeInfo::class.java, JsonSupport.NodeInfoDeserializer)
            om.registerModule(module)
            return om.readValue(conn.inputStream, NodeInfo::class.java)
        } catch(e: Exception) {
            log.error("Could not query node info", e)
            return null
        }
    }

    override fun startNode(providedName: String?, advertisedServices: Set<ServiceInfo>): Future<NodeInfoAndConfig> {
        val messagingAddress = portAllocation.nextHostAndPort()
        val apiAddress = portAllocation.nextHostAndPort()
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val name = providedName ?: "${pickA(name)}-${messagingAddress.port}"

        val nodeDirectory = "$baseDirectory/$name"

        val config = ConfigHelper.loadConfig(
                baseDirectoryPath = Paths.get(nodeDirectory),
                allowMissingConfig = true,
                configOverrides = mapOf(
                        "myLegalName" to name,
                        "basedir" to Paths.get(nodeDirectory).normalize().toString(),
                        "artemisAddress" to messagingAddress.toString(),
                        "webAddress" to apiAddress.toString(),
                        "extraAdvertisedServiceIds" to advertisedServices.joinToString(","),
                        "networkMapAddress" to networkMapAddress.toString(),
                        "useTestClock" to useTestClock
                )
        )

        return Executors.newSingleThreadExecutor().submit(Callable<NodeInfoAndConfig> {
            registerProcess(DriverDSL.startNode(config, quasarJarPath, debugPort))
            NodeInfoAndConfig(queryNodeInfo(apiAddress)!!, config)
        })
    }

    override fun start() {
        startNetworkMapService()
    }

    private fun startNetworkMapService() {
        val apiAddress = portAllocation.nextHostAndPort()
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null

        val nodeDirectory = "$baseDirectory/$networkMapName"
        val config = ConfigHelper.loadConfig(
                baseDirectoryPath = Paths.get(nodeDirectory),
                allowMissingConfig = true,
                configOverrides = mapOf(
                        "myLegalName" to networkMapName,
                        "basedir" to Paths.get(nodeDirectory).normalize().toString(),
                        "artemisAddress" to networkMapAddress.toString(),
                        "webAddress" to apiAddress.toString(),
                        "extraAdvertisedServiceIds" to "",
                        "useTestClock" to useTestClock
                )
        )

        log.info("Starting network-map-service")
        registerProcess(startNode(config, quasarJarPath, debugPort))
    }

    companion object {

        val name = arrayOf(
                "Alice",
                "Bob",
                "EvilBank",
                "NotSoEvilBank"
        )
        fun <A> pickA(array: Array<A>): A = array[Math.abs(Random().nextInt()) % array.size]

        private fun startNode(
                config: Config,
                quasarJarPath: String,
                debugPort: Int?
        ): Process {
            val nodeConf = FullNodeConfiguration(config)
            // Write node.conf
            writeConfig(nodeConf.basedir, "node.conf", config)

            val className = "com.r3corda.node.MainKt" // cannot directly get class for this, so just use string
            val separator = System.getProperty("file.separator")
            val classpath = System.getProperty("java.class.path")
            val path = System.getProperty("java.home") + separator + "bin" + separator + "java"

            val debugPortArg = if(debugPort != null)
                listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort")
            else
                emptyList()

            val javaArgs = listOf(path) +
                    listOf("-Dname=${nodeConf.myLegalName}", "-javaagent:$quasarJarPath") + debugPortArg +
                    listOf("-cp", classpath, className) +
                    "--base-directory=${nodeConf.basedir}"
            val builder = ProcessBuilder(javaArgs)
            builder.redirectError(Paths.get("error.$className.log").toFile())
            builder.inheritIO()
            builder.directory(nodeConf.basedir.toFile())
            val process = builder.start()
            addressMustBeBound(nodeConf.artemisAddress)
            // TODO There is a race condition here. Even though the messaging address is bound it may be the case that
            // the handlers for the advertised services are not yet registered. A hacky workaround is that we wait for
            // the web api address to be bound as well, as that starts after the services. Needs rethinking.
            addressMustBeBound(nodeConf.webAddress)

            return process
        }
    }
}

fun writeConfig(path: Path, filename: String, config: Config) {
    path.toFile().mkdirs()
    File("$path/$filename").writeText(config.root().render(ConfigRenderOptions.concise()))
}

