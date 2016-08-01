package com.r3corda.node.driver

import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.messaging.ArtemisMessagingService
import com.r3corda.node.services.network.InMemoryNetworkMapCache
import com.r3corda.node.services.network.NetworkMapService
import java.net.Socket
import java.net.SocketException
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * This file defines a small "Driver" DSL for starting up nodes.
 *
 * The process the driver is run behaves as an Artemis client and starts up other processes. Namely it first
 * bootstraps a network map service to allow the specified nodes to connect to, then starts up the actual nodes/explorers.
 *
 * Usage:
 *   driver {
 *     startNode(setOf(NotaryService.Type), "Notary")
 *     val aliceMonitor = startNode(setOf(WalletMonitorService.Type), "Alice")
 *   }
 *
 * The base directory node directories go into may be specified in [driver] and defaults to "build/<timestamp>/".
 * The node directories themselves are "<baseDirectory>/<legalName>/", where legalName defaults to
 * "<randomName>-<messagingPort>" and may be specified in [DriverDSL.startNode].
 *
 * TODO The driver actually starts up as an Artemis server now that may route traffic. Fix this once the client MessagingService is done.
 * TODO The nodes are started up sequentially which is quite slow. Either speed up node startup or make startup parallel somehow.
 * TODO The driver now polls the network map cache for info about newly started up nodes, this could be done asynchronously(?).
 * TODO The network map service bootstrap is hacky (needs to fake the service's public key in order to retrieve the true one), needs some thought.
 */

interface DriverDSLInterface {
    fun startNode(advertisedServices: Set<ServiceType>, providedName: String? = null): NodeInfo
}

fun <A> driver(baseDirectory: String? = null, quasarPath: String? = null, dsl: DriverDSL.() -> A): Pair<DriverHandle, A> {
    val driverDsl = DriverDSL(
            portCounter = 10000,
            baseDirectory = baseDirectory ?: "build/${getTimestampAsDirectoryName()}",
            quasarPath = quasarPath ?: "lib/quasar.jar"
    )
    driverDsl.start()
    val returnValue = dsl(driverDsl)
    val shutdownHook = Thread({
        driverDsl.shutdown()
    })
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    return Pair(DriverHandle(driverDsl, shutdownHook), returnValue)
}

private fun getTimestampAsDirectoryName(): String {
    val tz = TimeZone.getTimeZone("UTC")
    val df = SimpleDateFormat("yyyyMMddHHmmss")
    df.timeZone = tz
    return df.format(Date())
}

class DriverHandle(private val driverDsl: DriverDSL, private val shutdownHook: Thread) {
    val messagingService = driverDsl.messagingService
    val networkMapCache = driverDsl.networkMapCache

    fun waitForAllNodesToFinish() {
        driverDsl.waitForAllNodesToFinish()
    }

    fun shutdown() {
        driverDsl.shutdown()
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
    }
}

fun <A> poll(f: () -> A?): A {
    var counter = 0
    var result = f()
    while (result == null && counter < 30) {
        counter++
        Thread.sleep(500)
        result = f()
    }
    if (result == null) {
        throw Exception("Poll timed out")
    }
    return result
}

class DriverDSL(private var portCounter: Int, val baseDirectory: String, val quasarPath: String) : DriverDSLInterface {

    fun nextLocalHostAndPort() = HostAndPort.fromParts("localhost", nextPort())

    val messagingService = ArtemisMessagingService(
            Paths.get(baseDirectory, "driver-artemis"),
            nextLocalHostAndPort(),
            object : NodeConfiguration {
                override val myLegalName = "driver-artemis"
                override val exportJMXto = ""
                override val nearestCity = "Zion"
                override val keyStorePassword = "keypass"
                override val trustStorePassword = "trustpass"
            }
    )

    val networkMapCache = InMemoryNetworkMapCache(null)
    private val networkMapName = "NetworkMapService"
    private val networkMapAddress = nextLocalHostAndPort()
    private lateinit var networkMapNodeInfo: NodeInfo
    private val registeredProcesses = LinkedList<Process>()

    private fun nextPort(): Int {
        val nextPort = portCounter
        portCounter++
        return nextPort
    }

    fun registerProcess(process: Process) = registeredProcesses.push(process)

    internal fun waitForAllNodesToFinish() {
        registeredProcesses.forEach {
            it.waitFor()
        }
    }

    internal fun shutdown() {
        registeredProcesses.forEach {
            it.destroy()
        }
        /** Wait 5 seconds, then [Process.destroyForcibly] */
        val finishedFuture = Executors.newSingleThreadExecutor().submit {
            waitForAllNodesToFinish()
        }
        try {
            finishedFuture.get(5, TimeUnit.SECONDS)
        } catch (exception: TimeoutException) {
            finishedFuture.cancel(true)
            registeredProcesses.forEach {
                it.destroyForcibly()
            }
        }
    }

    override fun startNode(advertisedServices: Set<ServiceType>, providedName: String?): NodeInfo {
        val messagingAddress = nextLocalHostAndPort()
        val name = providedName ?: "${pickA(name)}-${messagingAddress.port}"
        val nearestCity = pickA(city)

        val driverCliParams = NodeRunner.CliParams(
                services = advertisedServices,
                networkMapName = networkMapNodeInfo.identity.name,
                networkMapPublicKey = networkMapNodeInfo.identity.owningKey,
                networkMapAddress = networkMapAddress,
                messagingAddress = messagingAddress,
                apiAddress = nextLocalHostAndPort(),
                baseDirectory = baseDirectory,
                nearestCity = nearestCity,
                legalName = name
        )
        registerProcess(startNode(driverCliParams, quasarPath))

        return poll {
            networkMapCache.partyNodes.forEach {
                if (it.identity.name == name) {
                    return@poll it
                }
            }
            null
        }
    }

    internal fun start() {
        messagingService.configureWithDevSSLCertificate()
        messagingService.start()
        startNetworkMapService()
    }

    private fun startNetworkMapService() {
        val driverCliParams = NodeRunner.CliParams(
                services = setOf(NetworkMapService.Type),
                networkMapName = null,
                networkMapPublicKey = null,
                networkMapAddress = null,
                messagingAddress = networkMapAddress,
                apiAddress = nextLocalHostAndPort(),
                baseDirectory = baseDirectory,
                nearestCity = pickA(city),
                legalName = networkMapName
        )
        println("Starting network-map-service")
        registerProcess(startNode(driverCliParams, quasarPath))
        // We fake the network map's NodeInfo with a random public key in order to retrieve the correct NodeInfo from
        // the network map service itself
        val nodeInfo = NodeInfo(
                address = ArtemisMessagingService.makeRecipient(networkMapAddress),
                identity = Party(
                        name = networkMapName,
                        owningKey = generateKeyPair().public
                ),
                advertisedServices = setOf(NetworkMapService.Type)
        )
        networkMapCache.addMapService(messagingService, nodeInfo, true)
        networkMapNodeInfo = poll {
            networkMapCache.partyNodes.forEach {
                if (it.identity.name == networkMapName) {
                    return@poll it
                }
            }
            null
        }
    }

    companion object {

        val city = arrayOf(
                "London",
                "Paris",
                "New York",
                "Tokyo"
        )
        val name = arrayOf(
                "Alice",
                "Bob",
                "EvilBank",
                "NotSoEvilBank"
        )
        fun <A> pickA(array: Array<A>): A = array[Math.abs(Random().nextInt()) % array.size]

        private fun startNode(cliParams: NodeRunner.CliParams, quasarPath: String): Process {
            val className = NodeRunner::class.java.canonicalName
            val separator = System.getProperty("file.separator")
            val classpath = System.getProperty("java.class.path")
            val path = System.getProperty("java.home") + separator + "bin" + separator + "java"
            val javaArgs = listOf(path) +
                    listOf("-Dname=${cliParams.legalName}", "-javaagent:$quasarPath", "-cp", classpath, className) +
                    cliParams.toCliArguments()
            val builder = ProcessBuilder(javaArgs)
            builder.redirectError(Paths.get("error.$className.log").toFile())
            builder.inheritIO()
            val process = builder.start()
            poll {
                try {
                    Socket(cliParams.messagingAddress.hostText, cliParams.messagingAddress.port).close()
                    Unit
                } catch (_exception: SocketException) {
                    null
                }
            }

            return process
        }
    }
}
