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
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.system.exitProcess

// IRS DEMO
//
// Please see docs/build/html/running-the-trading-demo.html
//
// TODO: TBD
//
// The different roles in the scenario this program can adopt are:

enum class IRSDemoRole {
    NodeA,
    NodeB,
    Trade,
    Date
}

class NodeParams() {
    var dir : Path = Paths.get("")
    var address : String = ""
    var mapAddress: String = ""
    var identityFile: Path = Paths.get("")
    var tradeWithAddrs: List<String> = listOf()
    var tradeWithIdentities: List<Path> = listOf()
    var uploadRates: Boolean = false
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

    val tradeIdArg = parser.nonOptions("Trade ID")
    val dateArg = parser.nonOptions("Date")

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
    if(role == IRSDemoRole.Trade) {
        val args : List<String> = options.valuesOf(tradeIdArg)
        if (args.size > 0) {
            val tradeId = args[0]
            if (runTrade(tradeId)) {
                exitProcess(0)
            } else {
                exitProcess(1)
            }
        } else {
            println("Please provide a trade ID")
            exitProcess(1)
        }
    } else if(role == IRSDemoRole.Date) {
        val args = options.valuesOf(dateArg)
        if (args.size > 0) {
            val dateStr = args[0]
            runDateChange(dateStr)
        } else {
            println("Please provide a date")
            exitProcess(1)
        }
    } else {
        val nodeParams = when (role) {
            IRSDemoRole.NodeA -> createNodeAParams()
            IRSDemoRole.NodeB -> createNodeBParams()
            else -> {
                throw IllegalArgumentException()
            }
        }

        nodeParams.mapAddress = options.valueOf(networkMapNetAddr)
        if (options.has(dirArg)) {
            nodeParams.dir = Paths.get(options.valueOf(dirArg))
        }
        if (options.has(networkAddressArg)) {
            nodeParams.address = options.valueOf(networkAddressArg)
        }
        nodeParams.identityFile = if (options.has(networkMapIdentityFile)) {
            Paths.get(options.valueOf(networkMapIdentityFile))
        } else {
            nodeParams.dir.resolve("identity-public")
        }
        if (options.has(fakeTradeWithIdentityFile)) {
            nodeParams.tradeWithIdentities = options.valuesOf(fakeTradeWithIdentityFile).map { Paths.get(it) }
        }
        if (options.has(fakeTradeWithAddr)) {
            nodeParams.tradeWithAddrs = options.valuesOf(fakeTradeWithAddr)
        }

        runNode(nodeParams)
        exitProcess(0)
    }
}

fun runDateChange(date: String) : Boolean{
    var url = URL("http://localhost:31338/api/irs/demodate")
    if(putJson(url, "\"" + date + "\"")) {
        println("Date changed")
        return true
    } else {
        println("Date failed to change")
        return false
    }
}

fun runTrade(tradeId: String) : Boolean {
    println("Uploading tradeID " + tradeId)
    val fileContents = Files.readAllBytes(Paths.get("scripts/example-irs-trade.json"))
    val tradeFile = String(fileContents).replace("tradeXXX", tradeId)
    var url = URL("http://localhost:31338/api/irs/deals")
    if(postJson(url, tradeFile)) {
        println("Trade sent")
        return true
    } else {
        println("Trade failed to send")
        return false
    }
}

fun runNode(nodeParams : NodeParams) : Unit {
    val node = startNode(nodeParams)
    // Register handlers for the demo
    AutoOfferProtocol.Handler.register(node)
    UpdateBusinessDayProtocol.Handler.register(node)
    ExitServerProtocol.Handler.register(node)

    if(nodeParams.uploadRates) {
        runUploadRates()
    }

    try {
        while (true) Thread.sleep(Long.MAX_VALUE)
    } catch(e: InterruptedException) {
        node.stop()
    }
}

fun runUploadRates() {
    val fileContents = Files.readAllBytes(Paths.get("scripts/example.rates.txt"))
    var timer : Timer? = null
    timer = fixedRateTimer("upload-rates", false, 0, 5000, {
        try {
            val url = URL("http://localhost:31341/upload/interest-rates")
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

fun sendJson(url: URL, data: String, method: String) : Boolean {
    val connection = url.openConnection() as HttpURLConnection
    connection.doOutput = true
    connection.useCaches = false
    connection.requestMethod = method
    connection.setRequestProperty("Connection", "Keep-Alive");
    connection.setRequestProperty("Cache-Control", "no-cache")
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Content-Length", data.length.toString())
    val outStream = DataOutputStream(connection.outputStream)
    outStream.writeBytes(data)
    outStream.close()

    if (connection.responseCode == 200) {
        return true
    } else {
        println("Failed to " + method + " data. Status Code: " + connection + ". Mesage: " + connection.responseMessage)
        return false
    }
}

fun putJson(url: URL, data: String) : Boolean {
    return sendJson(url, data, "PUT")
}

fun postJson(url: URL, data: String) : Boolean {
    return sendJson(url, data, "POST")
}

fun uploadFile(url: URL, file: ByteArray) : Boolean {
    val boundary = "===" + System.currentTimeMillis() + "===";
    val connection = url.openConnection() as HttpURLConnection
    connection.doOutput = true
    connection.doInput = true
    connection.useCaches = false
    connection.requestMethod = "POST"
    connection.setRequestProperty("Connection", "Keep-Alive");
    connection.setRequestProperty("Cache-Control", "no-cache")
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    val outStream = DataOutputStream(connection.outputStream)
    outStream.write(file)
    outStream.close()

    if (connection.responseCode == 200) {
        return true
    } else {
        println("Could not upload file. Status Code: " + connection + ". Mesage: " + connection.responseMessage)
        return false
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
    params.uploadRates = true
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
    Please refer to the documentation in docs/build/index.html to learn how to run the demo.
    """.trimIndent())
}
