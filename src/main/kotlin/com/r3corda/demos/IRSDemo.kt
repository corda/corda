package com.r3corda.demos

import com.google.common.net.HostAndPort
import com.typesafe.config.ConfigFactory
import com.r3corda.core.crypto.Party
import com.r3corda.core.logElapsedTime
import com.r3corda.core.messaging.MessagingService
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
import com.r3corda.node.internal.AbstractNode
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.node.services.network.InMemoryMessagingNetwork
import com.r3corda.node.services.transactions.SimpleNotaryService
import joptsimple.OptionParser
import joptsimple.OptionSet
import joptsimple.OptionSpec
import joptsimple.OptionSpecBuilder
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.system.exitProcess
import org.apache.commons.io.IOUtils
import java.net.SocketTimeoutException

// IRS DEMO
//
// Please see docs/build/html/running-the-trading-demo.html
//
// TODO: TBD
//
// The different roles in the scenario this program can adopt are:

enum class IRSDemoRole {
    SetupNodeA,
    SetupNodeB,
    NodeA,
    NodeB,
    Trade,
    Date
}

private class NodeParams() {
    var id: Int = -1
    var dir : Path = Paths.get("")
    var address : String = ""
    var mapAddress: String = ""
    var identityFile: Path = Paths.get("")
    var tradeWithAddrs: List<String> = listOf()
    var tradeWithIdentities: List<Path> = listOf()
    var uploadRates: Boolean = false
    var defaultLegalName: String = ""
}

private class DemoArgs() {
    lateinit var roleArg: OptionSpec<IRSDemoRole>
    lateinit var networkAddressArg: OptionSpec<String>
    lateinit var dirArg: OptionSpec<String>
    lateinit var networkMapIdentityFile: OptionSpec<String>
    lateinit var networkMapNetAddr: OptionSpec<String>
    lateinit var fakeTradeWithAddr: OptionSpec<String>
    lateinit var fakeTradeWithIdentityFile: OptionSpec<String>
    lateinit var nonOptions: OptionSpec<String>
}

private class NotSetupException: Throwable {
    constructor(message: String): super(message) {}
}

val messageNetwork = InMemoryMessagingNetwork()

class DemoNode(messagingService: MessagingService, dir: Path, p2pAddr: HostAndPort, config: NodeConfiguration,
               networkMapAddress: NodeInfo?, advertisedServices: Set<ServiceType>,
               clock: Clock, clientAPIs: List<Class<*>> = listOf())
               : Node(dir, p2pAddr, config, networkMapAddress, advertisedServices, clock, clientAPIs) {

    val messagingService = messagingService
    override fun makeMessagingService(): MessagingService {
        return messagingService
    }

    override fun startMessagingService() = Unit
}

fun main(args: Array<String>) {
    exitProcess(runIRSDemo(args))
}

fun runIRSDemo(args: Array<String>, useInMemoryMessaging: Boolean = false): Int {
    val parser = OptionParser()
    val demoArgs = setupArgs(parser)
    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        printHelp()
        return 1
    }

    // Suppress the Artemis MQ noise, and activate the demo logging.
    BriefLogFormatter.initVerbose("+demo.irsdemo", "+api-call", "+platform.deal", "-org.apache.activemq")

    val role = options.valueOf(demoArgs.roleArg)!!
    if(role == IRSDemoRole.SetupNodeA) {
        val nodeParams = configureNodeParams(IRSDemoRole.NodeA, demoArgs, options)
        setup(nodeParams)
    } else if(role == IRSDemoRole.SetupNodeB) {
        val nodeParams = configureNodeParams(IRSDemoRole.NodeB, demoArgs, options)
        setup(nodeParams)
    } else if(role == IRSDemoRole.Trade) {
        val tradeIdArgs = options.valuesOf(demoArgs.nonOptions)
        if (tradeIdArgs.size > 0) {
            val tradeId = tradeIdArgs[0]
            val host = if (options.has(demoArgs.networkAddressArg)) {
                options.valueOf(demoArgs.networkAddressArg)
            } else {
                "http://localhost:" + (Node.DEFAULT_PORT + 1)
            }

            if (!runTrade(tradeId, host)) {
                return 1
            }
        } else {
            println("Please provide a trade ID")
            return 1
        }
    } else if(role == IRSDemoRole.Date) {
        val dateStrArgs = options.valuesOf(demoArgs.nonOptions)
        if (dateStrArgs.size > 0) {
            val dateStr = dateStrArgs[0]
            val host = if (options.has(demoArgs.networkAddressArg)) {
                options.valueOf(demoArgs.networkAddressArg)
            } else {
                "http://localhost:" + (Node.DEFAULT_PORT + 1)
            }

            if(!runDateChange(dateStr, host)) {
                return 1
            }
        } else {
            println("Please provide a date")
            return 1
        }
    } else {
        // If these directory and identity file arguments aren't specified then we can assume a default setup and
        // create everything that is needed without needing to run setup.
        if(!options.has(demoArgs.dirArg) && !options.has(demoArgs.fakeTradeWithIdentityFile)) {
            createNodeConfig(createNodeAParams());
            createNodeConfig(createNodeBParams());
        }

        try {
            runNode(configureNodeParams(role, demoArgs, options), useInMemoryMessaging)
        } catch (e: NotSetupException) {
            println(e.message)
            return 1
        }
    }

    return 0
}

private fun setupArgs(parser: OptionParser): DemoArgs {
    val args = DemoArgs()

    args.roleArg = parser.accepts("role").withRequiredArg().ofType(IRSDemoRole::class.java).required()
    args.networkAddressArg = parser.accepts("network-address").withOptionalArg()
    args.dirArg = parser.accepts("directory").withOptionalArg()
    args.networkMapIdentityFile = parser.accepts("network-map-identity-file").withOptionalArg()
    args.networkMapNetAddr = parser.accepts("network-map-address").withRequiredArg().defaultsTo("localhost")
    // Use these to list one or more peers (again, will be superseded by discovery implementation)
    args.fakeTradeWithAddr = parser.accepts("fake-trade-with-address").withOptionalArg()
    args.fakeTradeWithIdentityFile = parser.accepts("fake-trade-with-identity-file").withOptionalArg()
    args.nonOptions = parser.nonOptions().ofType(String::class.java)

    return args
}

private fun setup(params: NodeParams) {
    createNodeConfig(params)
}

private fun runDateChange(date: String, host: String) : Boolean {
    println("Changing date to " + date)
    val url = URL(host + "/api/irs/demodate")
    if(putJson(url, "\"" + date + "\"")) {
        println("Date changed")
        return true
    } else {
        println("Date failed to change")
        return false
    }
}

private fun runTrade(tradeId: String, host: String) : Boolean {
    println("Uploading tradeID " + tradeId)
    val fileContents = IOUtils.toString(NodeParams::class.java.getResourceAsStream("example-irs-trade.json"))
    val tradeFile = fileContents.replace("tradeXXX", tradeId)
    val url = URL(host + "/api/irs/deals")
    if(postJson(url, tradeFile)) {
        println("Trade sent")
        return true
    } else {
        println("Trade failed to send")
        return false
    }
}

private fun configureNodeParams(role: IRSDemoRole, args: DemoArgs, options: OptionSet): NodeParams {
    val nodeParams = when (role) {
        IRSDemoRole.NodeA -> createNodeAParams()
        IRSDemoRole.NodeB -> createNodeBParams()
        else -> {
            throw IllegalArgumentException()
        }
    }

    nodeParams.mapAddress = options.valueOf(args.networkMapNetAddr)
    if (options.has(args.dirArg)) {
        nodeParams.dir = Paths.get(options.valueOf(args.dirArg))
    }
    if (options.has(args.networkAddressArg)) {
        nodeParams.address = options.valueOf(args.networkAddressArg)
    }
    nodeParams.identityFile = if (options.has(args.networkMapIdentityFile)) {
        Paths.get(options.valueOf(args.networkMapIdentityFile))
    } else {
        nodeParams.dir.resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME)
    }
    if (options.has(args.fakeTradeWithIdentityFile)) {
        nodeParams.tradeWithIdentities = options.valuesOf(args.fakeTradeWithIdentityFile).map { Paths.get(it) }
    }
    if (options.has(args.fakeTradeWithAddr)) {
        nodeParams.tradeWithAddrs = options.valuesOf(args.fakeTradeWithAddr)
    }

    return nodeParams
}

private fun runNode(nodeParams : NodeParams, useInMemoryMessaging: Boolean) : Unit {
    val node = when(useInMemoryMessaging) {
        true -> startDemoNode(nodeParams)
        false -> startNode(nodeParams)
    }
    // Register handlers for the demo
    AutoOfferProtocol.Handler.register(node)
    UpdateBusinessDayProtocol.Handler.register(node)
    ExitServerProtocol.Handler.register(node)

    if(nodeParams.uploadRates) {
        runUploadRates("http://localhost:31341")
    }

    try {
        while (true) Thread.sleep(Long.MAX_VALUE)
    } catch(e: InterruptedException) {
        node.stop()
    }
}

private fun runUploadRates(host: String) {
    val fileContents = IOUtils.toString(NodeParams::class.java.getResource("example.rates.txt"))
    var timer : Timer? = null
    timer = fixedRateTimer("upload-rates", false, 0, 5000, {
        try {
            val url = URL(host + "/upload/interest-rates")
            if(uploadFile(url, fileContents)) {
                timer!!.cancel()
                println("Rates uploaded successfully")
            } else {
                print("Could not upload rates. Retrying in 5 seconds. ")
            }
        } catch (e: Exception) {
            println("Could not upload rates due to exception. Retrying in 5 seconds")
        }
    })
}

// Todo: Use a simpler library function for this and handle timeout exceptions
private fun sendJson(url: URL, data: String, method: String) : Boolean {
    val connection = url.openConnection() as HttpURLConnection
    connection.doOutput = true
    connection.useCaches = false
    connection.requestMethod = method
    connection.connectTimeout = 5000
    connection.readTimeout = 10000
    connection.setRequestProperty("Connection", "Keep-Alive")
    connection.setRequestProperty("Cache-Control", "no-cache")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Content-Length", data.length.toString())

    try {
        val outStream = DataOutputStream(connection.outputStream)
        outStream.writeBytes(data)
        outStream.close()

        return when (connection.responseCode) {
            200 -> true
            201 -> true
            else -> {
                println("Failed to " + method + " data. Status Code: " + connection.responseCode + ". Mesage: " + connection.responseMessage)
                false
            }
        }
    } catch(e: SocketTimeoutException) {
        println("Server took too long to respond")
        return false
    }
}

private fun putJson(url: URL, data: String) : Boolean {
    return sendJson(url, data, "PUT")
}

private fun postJson(url: URL, data: String) : Boolean {
    return sendJson(url, data, "POST")
}

// Todo: Use a simpler library function for this and handle timeout exceptions
private fun uploadFile(url: URL, file: String) : Boolean {
    val boundary = "===" + System.currentTimeMillis() + "==="
    val hyphens = "--"
    val clrf = "\r\n"

    val connection = url.openConnection() as HttpURLConnection
    connection.doOutput = true
    connection.doInput = true
    connection.useCaches = false
    connection.requestMethod = "POST"
    connection.connectTimeout = 5000
    connection.readTimeout = 5000
    connection.setRequestProperty("Connection", "Keep-Alive")
    connection.setRequestProperty("Cache-Control", "no-cache")
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)

    val request = DataOutputStream(connection.outputStream)
    request.writeBytes(hyphens + boundary + clrf)
    request.writeBytes("Content-Disposition: form-data; name=\"rates\" filename=\"example.rates.txt\"" + clrf)
    request.writeBytes(clrf)
    request.writeBytes(file)
    request.writeBytes(clrf)
    request.writeBytes(hyphens + boundary + hyphens + clrf)

    if (connection.responseCode == 200) {
        return true
    } else {
        println("Could not upload file. Status Code: " + connection + ". Mesage: " + connection.responseMessage)
        return false
    }
}

private fun createNodeAParams() : NodeParams {
    val params = NodeParams()
    params.id = 0
    params.dir = Paths.get("nodeA")
    params.address = "localhost"
    params.tradeWithAddrs = listOf("localhost:31340")
    params.tradeWithIdentities = listOf(getRoleDir(IRSDemoRole.NodeB).resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))
    params.defaultLegalName = "Bank A"
    return params
}

private fun createNodeBParams() : NodeParams {
    val params = NodeParams()
    params.id = 1
    params.dir = Paths.get("nodeB")
    params.address = "localhost:31340"
    params.tradeWithAddrs = listOf("localhost")
    params.tradeWithIdentities = listOf(getRoleDir(IRSDemoRole.NodeA).resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))
    params.defaultLegalName = "Bank B"
    params.uploadRates = true
    return params
}

private fun createNodeConfig(params: NodeParams) : NodeConfiguration {
    if (!Files.exists(params.dir)) {
        Files.createDirectory(params.dir)
    }

    val configFile = params.dir.resolve("config").toFile()
    val config = loadConfigFile(configFile, params.defaultLegalName)
    if(!Files.exists(params.dir.resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))) {
        createIdentities(params, config)
    }

    return config
}

private fun getNodeConfig(params: NodeParams): NodeConfiguration {
    if(!Files.exists(params.dir)) {
        throw NotSetupException("Missing config directory. Please run node setup before running the node")
    }

    if(!Files.exists(params.dir.resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))) {
        throw NotSetupException("Missing identity file. Please run node setup before running the node")
    }

    val configFile = params.dir.resolve("config").toFile()
    return loadConfigFile(configFile, params.defaultLegalName)
}

private fun startNode(params : NodeParams) : Node {
    val config = getNodeConfig(params)
    val advertisedServices: Set<ServiceType>
    val myNetAddr = HostAndPort.fromString(params.address).withDefaultPort(Node.DEFAULT_PORT)
    val networkMapId = if (params.mapAddress.equals(params.address)) {
        // This node provides network map and notary services
        advertisedServices = setOf(NetworkMapService.Type, SimpleNotaryService.Type)
        null
    } else {
        advertisedServices = setOf(NodeInterestRates.Type)
        nodeInfo(params.mapAddress, params.identityFile, setOf(NetworkMapService.Type, SimpleNotaryService.Type))
    }

    val node = logElapsedTime("Node startup") { Node(params.dir, myNetAddr, config, networkMapId,
            advertisedServices, DemoClock(),
            listOf(InterestRateSwapAPI::class.java)).setup().start() }

    // TODO: This should all be replaced by the identity service being updated
    // as the network map changes.
    if (params.tradeWithAddrs.size != params.tradeWithIdentities.size) {
        throw IllegalArgumentException("Different number of peer addresses (${params.tradeWithAddrs.size}) and identities (${params.tradeWithIdentities.size})")
    }
    for ((hostAndPortString, identityFile) in params.tradeWithAddrs.zip(params.tradeWithIdentities)) {
        val peerId = nodeInfo(hostAndPortString, identityFile)
        node.services.identityService.registerIdentity(peerId.identity)
    }

    return node
}

private fun startDemoNode(params : NodeParams) : Node {
    val config = createNodeConfig(params)
    val advertisedServices: Set<ServiceType>
    val myNetAddr = HostAndPort.fromString(params.address).withDefaultPort(Node.DEFAULT_PORT)
    val networkMapId = if (params.mapAddress.equals(params.address)) {
        // This node provides network map and notary services
        advertisedServices = setOf(NetworkMapService.Type, NotaryService.Type)
        null
    } else {
        advertisedServices = setOf(NodeInterestRates.Type)

        val handle = InMemoryMessagingNetwork.Handle(createNodeAParams().id, params.defaultLegalName)
        nodeInfo(handle, params.identityFile, setOf(NetworkMapService.Type, NotaryService.Type))
    }

    val messageService = messageNetwork.createNodeWithID(false, params.id).start().get()
    val node = logElapsedTime("Node startup") { DemoNode(messageService, params.dir, myNetAddr, config, networkMapId,
            advertisedServices, DemoClock(),
            listOf(InterestRateSwapAPI::class.java)).setup().start() }

    // TODO: This should all be replaced by the identity service being updated
    // as the network map changes.
    val identityFile = params.tradeWithIdentities[0]
    // Since in integration tests there are only two nodes with IDs 0 and 1, this hack will work
    // TODO: Get Artemis working with two nodes in the same process or come up with a better solution
    val handle = InMemoryMessagingNetwork.Handle(1 - params.id, "Other Node")
    val peerId = nodeInfo(handle, identityFile)
    node.services.identityService.registerIdentity(peerId.identity)

    return node
}

private fun getRoleDir(role: IRSDemoRole) : Path {
    when(role) {
        IRSDemoRole.NodeA -> return Paths.get("nodeA")
        IRSDemoRole.NodeB -> return Paths.get("nodeB")
        else -> {
            throw IllegalArgumentException()
        }
    }
}

private fun nodeInfo(hostAndPortString: String, identityFile: Path, advertisedServices: Set<ServiceType> = emptySet()): NodeInfo {
    try {
        val addr = HostAndPort.fromString(hostAndPortString).withDefaultPort(Node.DEFAULT_PORT)
        val path = identityFile
        val party = Files.readAllBytes(path).deserialize<Party>()
        return NodeInfo(ArtemisMessagingService.makeRecipient(addr), party, advertisedServices)
    } catch (e: Exception) {
        println("Could not find identify file $identityFile.")
        throw e
    }
}

private fun nodeInfo(handle: InMemoryMessagingNetwork.Handle, identityFile: Path, advertisedServices: Set<ServiceType> = emptySet()): NodeInfo {
    try {
        val path = identityFile
        val party = Files.readAllBytes(path).deserialize<Party>()
        return NodeInfo(handle, party, advertisedServices)
    } catch (e: Exception) {
        println("Could not find identify file $identityFile.")
        throw e
    }
}

private fun loadConfigFile(configFile: File, defaultLegalName: String): NodeConfiguration {
    if (!configFile.exists()) {
        createDefaultConfigFile(configFile, defaultLegalName)
        println("Default config created at $configFile.")
    }

    val config = ConfigFactory.parseFile(configFile).withFallback(ConfigFactory.load())
    return NodeConfigurationFromConfig(config)
}

private fun createIdentities(params: NodeParams, nodeConf: NodeConfiguration) {
    val mockNetwork = MockNetwork(false)
    val node = MockNetwork.MockNode(params.dir, nodeConf, mockNetwork, null, setOf(NetworkMapService.Type, SimpleNotaryService.Type), params.id, null)
    node.start()
    node.stop()
}

private fun createDefaultConfigFile(configFile: File, legalName: String) {
    configFile.writeBytes(
            """
        myLegalName = $legalName
        """.trimIndent().toByteArray())
}

private fun printHelp() {
    println("""
    Please refer to the documentation in docs/build/index.html to learn how to run the demo.
    """.trimIndent())
}
