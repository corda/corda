package com.r3corda.demos

import com.google.common.net.HostAndPort
import com.typesafe.config.ConfigFactory
import com.r3corda.core.crypto.Party
import com.r3corda.core.logElapsedTime
import com.r3corda.node.internal.Node
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.config.NodeConfigurationFromConfig
import com.r3corda.core.node.NodeInfo
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.clientapi.NodeInterestRates
import com.r3corda.node.services.transactions.NotaryService
import com.r3corda.core.node.services.ServiceType
import com.r3corda.node.services.messaging.ArtemisMessagingService
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.utilities.BriefLogFormatter
import com.r3corda.demos.api.InterestRateSwapAPI
import com.r3corda.demos.protocols.AutoOfferProtocol
import com.r3corda.demos.protocols.ExitServerProtocol
import com.r3corda.demos.protocols.UpdateBusinessDayProtocol
import joptsimple.OptionParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

// IRS DEMO
//
// TODO: Please see TBD

enum class IRSDemoRole {
    NodeA,
    NodeB
}

class NodeParams() {
    public var dir : Path = Paths.get("")
    public var address : String = ""
    public var mapAddress: String = ""
    public var identityFile: Path = Paths.get("")
    public var tradeWithAddrs: List<String> = listOf()
    public var tradeWithIdentities: List<Path> = listOf()
}

fun main(args: Array<String>) {
    val parser = OptionParser()

    val roleArg = parser.accepts("role").withRequiredArg().ofType(IRSDemoRole::class.java).required()

    val networkAddressArg = parser.accepts("network-address").withOptionalArg()
    val dirArg = parser.accepts("directory").withOptionalArg()

    val networkMapIdentityFile = parser.accepts("network-map-identity-file").withOptionalArg()
    val networkMapNetAddr = parser.accepts("network-map-address").withRequiredArg().defaultsTo("localhost")

    // Use these to list one or more peers (again, will be superseded by discovery implementation)
    val fakeTradeWithAddr = parser.accepts("fake-trade-with-address").withOptionalArg()
    val fakeTradeWithIdentityFile = parser.accepts("fake-trade-with-identity-file").withOptionalArg()

    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        printHelp()
        exitProcess(1)
    }

    // Suppress the Artemis MQ noise, and activate the demo logging.
    BriefLogFormatter.initVerbose("+demo.irsdemo", "+api-call", "+platform.deal", "-org.apache.activemq")

    val role = options.valueOf(roleArg)!!
    val nodeParams = when(role) {
        IRSDemoRole.NodeA -> createNodeAParams()
        IRSDemoRole.NodeB -> createNodeBParams()
    }

    nodeParams.mapAddress = options.valueOf(networkMapNetAddr)
    if(options.has(dirArg)) {
        nodeParams.dir = Paths.get(options.valueOf(dirArg))
    }
    if(options.has(networkAddressArg)) {
        nodeParams.address = options.valueOf(networkAddressArg)
    }
    nodeParams.identityFile = if(options.has(networkMapIdentityFile)) {
       Paths.get(options.valueOf(networkMapIdentityFile))
    } else {
        nodeParams.dir.resolve("identity-public")
    }
    if(options.has(fakeTradeWithIdentityFile)) {
        nodeParams.tradeWithIdentities = options.valuesOf(fakeTradeWithIdentityFile).map { Paths.get(it) }
    }
    if(options.has(fakeTradeWithAddr)) {
        nodeParams.tradeWithAddrs = options.valuesOf(fakeTradeWithAddr)
    }

    runNode(nodeParams)
    exitProcess(0)
}

fun runNode(nodeParams : NodeParams) : Unit {
    val node = startNode(nodeParams)
    // Register handlers for the demo
    AutoOfferProtocol.Handler.register(node)
    UpdateBusinessDayProtocol.Handler.register(node)
    ExitServerProtocol.Handler.register(node)

    try {
        while (true) Thread.sleep(Long.MAX_VALUE)
    } catch(e: InterruptedException) {
        node.stop()
    }
}

fun createNodeAParams() : NodeParams {
    val params = NodeParams()
    params.dir = Paths.get("nodeA")
    params.address = "localhost"
    params.tradeWithAddrs = listOf("localhost:31340")
    params.tradeWithIdentities = listOf(getRoleDir(IRSDemoRole.NodeB).resolve("identity-public"))
    return params
}

fun createNodeBParams() : NodeParams {
    val params = NodeParams()
    params.dir = Paths.get("nodeB")
    params.address = "localhost:31340"
    params.tradeWithAddrs = listOf("localhost")
    params.tradeWithIdentities = listOf(getRoleDir(IRSDemoRole.NodeA).resolve("identity-public"))
    return params
}

fun startNode(params : NodeParams) : Node {
    if (!Files.exists(params.dir)) {
        Files.createDirectory(params.dir)
    }

    val configFile = params.dir.resolve("config")
    val config = loadConfigFile(configFile)
    val advertisedServices: Set<ServiceType>
    val myNetAddr = HostAndPort.fromString(params.address).withDefaultPort(Node.DEFAULT_PORT)
    val networkMapId = if (params.mapAddress.equals(params.address)) {
        // This node provides network map and notary services
        advertisedServices = setOf(NetworkMapService.Type, NotaryService.Type)
        null
    } else {
        advertisedServices = setOf(NodeInterestRates.Type)

        try {
            nodeInfo(params.mapAddress, params.identityFile, setOf(NetworkMapService.Type, SimpleNotaryService.Type))
        } catch (e: Exception) {
            null
        }
    }

    val node = logElapsedTime("Node startup") { Node(params.dir, myNetAddr, config, networkMapId,
            advertisedServices, DemoClock(),
            listOf(InterestRateSwapAPI::class.java)).start() }

    // TODO: This should all be replaced by the identity service being updated
    // as the network map changes.
    if (params.tradeWithAddrs.size != params.tradeWithIdentities.size) {
        throw IllegalArgumentException("Different number of peer addresses (${params.tradeWithAddrs.size}) and identities (${params.tradeWithIdentities.size})")
    }
    for ((hostAndPortString, identityFile) in params.tradeWithAddrs.zip(params.tradeWithIdentities)) {
        try {
            val peerId = nodeInfo(hostAndPortString, identityFile)
            node.services.identityService.registerIdentity(peerId.identity)
        } catch (e: Exception) {
            println("Could not load peer identity file \"$identityFile\".")
        }
    }

    return node
}

fun getRoleDir(role: IRSDemoRole) : Path {
    when(role) {
        IRSDemoRole.NodeA -> return Paths.get("nodeA")
        IRSDemoRole.NodeB -> return Paths.get("nodeB")
        else -> {
            return Paths.get("nodedata")
        }
    }
}

fun nodeInfo(hostAndPortString: String, identityFile: Path, advertisedServices: Set<ServiceType> = emptySet()): NodeInfo {
    try {
        val addr = HostAndPort.fromString(hostAndPortString).withDefaultPort(Node.DEFAULT_PORT)
        val path = identityFile
        val party = Files.readAllBytes(path).deserialize<Party>()
        return NodeInfo(ArtemisMessagingService.makeRecipient(addr), party, advertisedServices)
    } catch (e: Exception) {
        println("Could not find identify file $identityFile.  If the file has just been created as part of starting the demo, please restart this node")
        throw e
    }
}

private fun loadConfigFile(configFile: Path): NodeConfiguration {
    fun askAdminToEditConfig(configFile: Path?) {
        println()
        println("This is the first run, so you should edit the config file in $configFile and then start the node again.")
        println()
        exitProcess(1)
    }

    val defaultLegalName = "Global MegaCorp, Ltd."

    if (!Files.exists(configFile)) {
        createDefaultConfigFile(configFile, defaultLegalName)
        askAdminToEditConfig(configFile)
    }

    System.setProperty("config.file", configFile.toAbsolutePath().toString())
    val config = NodeConfigurationFromConfig(ConfigFactory.load())

    // Make sure admin did actually edit at least the legal name.
    if (config.myLegalName == defaultLegalName)
        askAdminToEditConfig(configFile)

    return config
}

private fun createDefaultConfigFile(configFile: Path?, defaultLegalName: String) {
    Files.write(configFile,
            """
        # Node configuration: give the buyer node the name 'Bank of Zurich' (no quotes)
        # The seller node can be named whatever you like.

        myLegalName = $defaultLegalName
        """.trimIndent().toByteArray())
}

private fun printHelp() {
    println("""
    Please refer to the documentation that doesn't yet exist to learn how to run the demo.
    """.trimIndent())
}
