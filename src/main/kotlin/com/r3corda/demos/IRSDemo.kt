package com.r3corda.demos

import com.google.common.net.HostAndPort
import com.r3corda.contracts.InterestRateSwap
import com.r3corda.core.crypto.Party
import com.r3corda.core.logElapsedTime
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.utilities.BriefLogFormatter
import com.r3corda.demos.api.InterestRateSwapAPI
import com.r3corda.demos.protocols.AutoOfferProtocol
import com.r3corda.demos.protocols.ExitServerProtocol
import com.r3corda.demos.protocols.UpdateBusinessDayProtocol
import com.r3corda.node.internal.AbstractNode
import com.r3corda.node.internal.Node
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.node.services.FixingSessionInitiationHandler
import com.r3corda.node.services.clientapi.NodeInterestRates
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.config.NodeConfigurationFromConfig
import com.r3corda.node.services.messaging.ArtemisMessagingService
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.typesafe.config.ConfigFactory
import joptsimple.OptionParser
import joptsimple.OptionSet
import org.apache.commons.io.IOUtils
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.system.exitProcess

// IRS DEMO
//
// Please see docs/build/html/running-the-trading-demo.html

/**
 * Roles. There are 4 modes this demo can be run:
 *   - SetupNodeA/SetupNodeB: Creates and sets up the necessary directories for nodes
 *   - NodeA/NodeB: Starts the nodes themselves
 *   - Trade: Uploads an example trade
 *   - DateChange: Changes the demo's date
 */
enum class IRSDemoRole {
    SetupNodeA,
    SetupNodeB,
    NodeA,
    NodeB,
    Trade,
    Date
}

/**
 * Parsed command line parameters.
 */
sealed class CliParams {

    /**
     * Corresponds to roles 'SetupNodeA' and 'SetupNodeB'
     */
    class SetupNode(
            val node: IRSDemoNode,
            val dir: Path,
            val defaultLegalName: String
    ) : CliParams()

    /**
     * Corresponds to roles 'NodeA' and 'NodeB'
     */
    class RunNode(
            val node: IRSDemoNode,
            val dir: Path,
            val networkAddress : HostAndPort,
            val apiAddress: HostAndPort,
            val mapAddress: String,
            val identityFile: Path,
            val tradeWithAddrs: List<String>,
            val tradeWithIdentities: List<Path>,
            val uploadRates: Boolean,
            val defaultLegalName: String,
            val autoSetup: Boolean // Run Setup for both nodes automatically with default arguments
    ) : CliParams()

    /**
     * Corresponds to role 'Trade'
     */
    class Trade(
            val apiAddress: HostAndPort,
            val tradeId: String
    ) : CliParams()

    /**
     * Corresponds to role 'Date'
     */
    class DateChange(
            val apiAddress: HostAndPort,
            val dateString: String
    ) : CliParams()

    companion object {

        val defaultBaseDirectory = "./build/irs-demo"

        fun legalName(node: IRSDemoNode) =
                when (node) {
                    IRSDemoNode.NodeA -> "Bank A"
                    IRSDemoNode.NodeB -> "Bank B"
                }

        private fun nodeDirectory(options: OptionSet, node: IRSDemoNode) =
                Paths.get(options.valueOf(CliParamsSpec.baseDirectoryArg), node.name.decapitalize())

        private fun parseSetupNode(options: OptionSet, node: IRSDemoNode): SetupNode {
            return SetupNode(
                    node = node,
                    dir = nodeDirectory(options, node),
                    defaultLegalName = legalName(node)
            )
        }

        private fun defaultNetworkPort(node: IRSDemoNode) =
                when (node) {
                    IRSDemoNode.NodeA -> Node.DEFAULT_PORT
                    IRSDemoNode.NodeB -> Node.DEFAULT_PORT + 2
                }

        private fun defaultApiPort(node: IRSDemoNode) =
                when (node) {
                    IRSDemoNode.NodeA -> Node.DEFAULT_PORT + 1
                    IRSDemoNode.NodeB -> Node.DEFAULT_PORT + 3
                }

        private fun parseRunNode(options: OptionSet, node: IRSDemoNode): RunNode {
            val dir = nodeDirectory(options, node)

            return RunNode(
                    node = node,
                    dir = dir,
                    networkAddress = HostAndPort.fromString(options.valueOf(
                            CliParamsSpec.networkAddressArg.defaultsTo("localhost:${defaultNetworkPort(node)}")
                    )),
                    apiAddress = HostAndPort.fromString(options.valueOf(
                            CliParamsSpec.apiAddressArg.defaultsTo("localhost:${defaultApiPort(node)}")
                    )),
                    mapAddress = options.valueOf(CliParamsSpec.networkMapNetAddr),
                    identityFile = if (options.has(CliParamsSpec.networkMapIdentityFile)) {
                        Paths.get(options.valueOf(CliParamsSpec.networkMapIdentityFile))
                    } else {
                        dir.resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME)
                    },
                    tradeWithAddrs = if (options.has(CliParamsSpec.fakeTradeWithAddr)) {
                        options.valuesOf(CliParamsSpec.fakeTradeWithAddr)
                    } else  {
                        listOf("localhost:${defaultNetworkPort(node.other)}")
                    },
                    tradeWithIdentities = if (options.has(CliParamsSpec.fakeTradeWithIdentityFile)) {
                        options.valuesOf(CliParamsSpec.fakeTradeWithIdentityFile).map { Paths.get(it) }
                    } else {
                        listOf(nodeDirectory(options, node.other).resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))
                    },
                    uploadRates = node == IRSDemoNode.NodeB,
                    defaultLegalName = legalName(node),
                    autoSetup = !options.has(CliParamsSpec.baseDirectoryArg) && !options.has(CliParamsSpec.fakeTradeWithIdentityFile)
            )
        }

        private fun parseTrade(options: OptionSet): Trade {
            return Trade(
                    apiAddress = HostAndPort.fromString(options.valueOf(
                            CliParamsSpec.apiAddressArg.defaultsTo("localhost:${defaultApiPort(IRSDemoNode.NodeA)}")
                    )),
                    tradeId = options.valuesOf(CliParamsSpec.nonOptions).let {
                        if (it.size > 0) {
                            it[0]
                        } else {
                            throw IllegalArgumentException("Please provide a trade ID")
                        }
                    }
            )
        }

        private fun parseDateChange(options: OptionSet): DateChange {
            return DateChange(
                    apiAddress = HostAndPort.fromString(options.valueOf(
                            CliParamsSpec.apiAddressArg.defaultsTo("localhost:${defaultApiPort(IRSDemoNode.NodeA)}")
                    )),
                    dateString = options.valuesOf(CliParamsSpec.nonOptions).let {
                        if (it.size > 0) {
                            it[0]
                        } else {
                            throw IllegalArgumentException("Please provide a date string")
                        }
                    }
            )
        }

        fun parse(options: OptionSet): CliParams {
            val role = options.valueOf(CliParamsSpec.roleArg)!!
            return when (role) {
                IRSDemoRole.SetupNodeA -> parseSetupNode(options, IRSDemoNode.NodeA)
                IRSDemoRole.SetupNodeB -> parseSetupNode(options, IRSDemoNode.NodeB)
                IRSDemoRole.NodeA -> parseRunNode(options, IRSDemoNode.NodeA)
                IRSDemoRole.NodeB -> parseRunNode(options, IRSDemoNode.NodeB)
                IRSDemoRole.Trade -> parseTrade(options)
                IRSDemoRole.Date -> parseDateChange(options)
            }
        }
    }
}

enum class IRSDemoNode {
    NodeA,
    NodeB;

    val other: IRSDemoNode get() {
        return when (this) {
            NodeA -> NodeB
            NodeB -> NodeA
        }
    }
}

object CliParamsSpec {
    val parser = OptionParser()
    val roleArg = parser.accepts("role").withRequiredArg().ofType(IRSDemoRole::class.java)
    val networkAddressArg = parser.accepts("network-address").withOptionalArg().ofType(String::class.java)
    val apiAddressArg = parser.accepts("api-address").withOptionalArg().ofType(String::class.java)
    val baseDirectoryArg = parser.accepts("base-directory").withOptionalArg().defaultsTo(CliParams.defaultBaseDirectory)
    val networkMapIdentityFile = parser.accepts("network-map-identity-file").withOptionalArg()
    val networkMapNetAddr = parser.accepts("network-map-address").withRequiredArg().defaultsTo("localhost")
    val fakeTradeWithAddr = parser.accepts("fake-trade-with-address").withOptionalArg()
    val fakeTradeWithIdentityFile = parser.accepts("fake-trade-with-identity-file").withOptionalArg()
    val nonOptions = parser.nonOptions()
}

class IRSDemoPluginRegistry : CordaPluginRegistry {
    override val webApis: List<Class<*>> = listOf(InterestRateSwapAPI::class.java)
    override val protocolLogicClassNameWhitelist: Set<String> = setOf(AutoOfferProtocol.Requester::class.java.name,
                                                                      UpdateBusinessDayProtocol.Broadcast::class.java.name,
                                                                      ExitServerProtocol.Broadcast::class.java.name)
    override val protocolArgsClassNameWhitelist: Set<String> = setOf(InterestRateSwap.State::class.java.name,
                                                                    java.time.LocalDate::class.java.name,
                                                                    kotlin.Int::class.java.name)
}

private class NotSetupException: Throwable {
    constructor(message: String): super(message) {}
}

fun main(args: Array<String>) {
    exitProcess(runIRSDemo(args))
}

fun runIRSDemo(args: Array<String>): Int {
    val cliParams = try {
        CliParams.parse(CliParamsSpec.parser.parse(*args))
    } catch (e: Exception) {
        println(e)
        printHelp()
        return 1
    }

    // Suppress the Artemis MQ noise, and activate the demo logging.
    BriefLogFormatter.initVerbose("+demo.irsdemo", "+api-call", "+platform.deal", "-org.apache.activemq")

    return when (cliParams) {
        is CliParams.SetupNode -> setup(cliParams)
        is CliParams.RunNode -> runNode(cliParams)
        is CliParams.Trade -> runTrade(cliParams)
        is CliParams.DateChange -> runDateChange(cliParams)
    }
}

private fun setup(params: CliParams.SetupNode): Int {
    val dirFile = params.dir.toFile()
    if (!dirFile.exists()) {
        dirFile.mkdirs()
    }

    val configFile = params.dir.resolve("config").toFile()
    val config = loadConfigFile(configFile, params.defaultLegalName)
    if (!Files.exists(params.dir.resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))) {
        createIdentities(params, config)
    }
    return 0
}

private fun defaultNodeSetupParams(node: IRSDemoNode): CliParams.SetupNode =
        CliParams.SetupNode(
                node = node,
                dir = Paths.get(CliParams.defaultBaseDirectory, node.name.decapitalize()),
                defaultLegalName = CliParams.legalName(node)
        )

private fun runNode(cliParams: CliParams.RunNode): Int {
    if (cliParams.autoSetup) {
        setup(defaultNodeSetupParams(IRSDemoNode.NodeA))
        setup(defaultNodeSetupParams(IRSDemoNode.NodeB))
    }

    try {
        val networkMap = createRecipient(cliParams.mapAddress)
        val destinations = cliParams.tradeWithAddrs.map({
            createRecipient(it)
        })

        val node = startNode(cliParams, networkMap, destinations)
        // Register handlers for the demo
        AutoOfferProtocol.Handler.register(node)
        UpdateBusinessDayProtocol.Handler.register(node)
        ExitServerProtocol.Handler.register(node)
        FixingSessionInitiationHandler.register(node)

        if (cliParams.uploadRates) {
            runUploadRates(cliParams.apiAddress)
        }

        try {
            while (true) Thread.sleep(Long.MAX_VALUE)
        } catch(e: InterruptedException) {
            node.stop()
        }
    } catch (e: NotSetupException) {
        println(e.message)
        return 1
    }

    return 0
}

private fun runDateChange(cliParams: CliParams.DateChange): Int {
    println("Changing date to " + cliParams.dateString)
    val url = URL("http://${cliParams.apiAddress}/api/irs/demodate")
    if (putJson(url, "\"" + cliParams.dateString + "\"")) {
        println("Date changed")
        return 0
    } else {
        println("Date failed to change")
        return 1
    }
}

private fun runTrade(cliParams: CliParams.Trade): Int {
    println("Uploading tradeID " + cliParams.tradeId)
    // Note: the getResourceAsStream is an ugly hack to get the jvm to search in the right location
    val fileContents = IOUtils.toString(CliParams::class.java.getResourceAsStream("example-irs-trade.json"))
    val tradeFile = fileContents.replace("tradeXXX", cliParams.tradeId)
    val url = URL("http://${cliParams.apiAddress}/api/irs/deals")
    if (postJson(url, tradeFile)) {
        println("Trade sent")
        return 0
    } else {
        println("Trade failed to send")
        return 1
    }
}

private fun createRecipient(addr: String) : SingleMessageRecipient {
    val hostAndPort = HostAndPort.fromString(addr).withDefaultPort(Node.DEFAULT_PORT)
    return ArtemisMessagingService.makeRecipient(hostAndPort)
}

private fun startNode(params: CliParams.RunNode, networkMap: SingleMessageRecipient, recipients: List<SingleMessageRecipient>) : Node {
    val config = getNodeConfig(params)
    val advertisedServices: Set<ServiceType>
    val networkMapId =
            when (params.node) {
                IRSDemoNode.NodeA -> {
                    advertisedServices = setOf(NetworkMapService.Type, SimpleNotaryService.Type)
                    null
                }
                IRSDemoNode.NodeB -> {
                    advertisedServices = setOf(NodeInterestRates.Type)
                    nodeInfo(networkMap, params.identityFile, setOf(NetworkMapService.Type, SimpleNotaryService.Type))
                }
            }

    val node = logElapsedTime("Node startup") {
        Node(params.dir, params.networkAddress, params.apiAddress, config, networkMapId, advertisedServices, DemoClock()).start()
    }

    // TODO: This should all be replaced by the identity service being updated
    // as the network map changes.
    if (params.tradeWithAddrs.size != params.tradeWithIdentities.size) {
        throw IllegalArgumentException("Different number of peer addresses (${params.tradeWithAddrs.size}) and identities (${params.tradeWithIdentities.size})")
    }
    for ((recipient, identityFile) in recipients.zip(params.tradeWithIdentities)) {
        val peerId = nodeInfo(recipient, identityFile)
        node.services.identityService.registerIdentity(peerId.identity)
    }

    return node
}

private fun nodeInfo(recipient: SingleMessageRecipient, identityFile: Path, advertisedServices: Set<ServiceType> = emptySet()): NodeInfo {
    try {
        val path = identityFile
        val party = Files.readAllBytes(path).deserialize<Party>()
        return NodeInfo(recipient, party, advertisedServices)
    } catch (e: Exception) {
        println("Could not find identify file $identityFile.")
        throw e
    }
}

private fun runUploadRates(host: HostAndPort) {
    // Note: the getResourceAsStream is an ugly hack to get the jvm to search in the right location
    val fileContents = IOUtils.toString(CliParams::class.java.getResourceAsStream("example.rates.txt"))
    var timer : Timer? = null
    timer = fixedRateTimer("upload-rates", false, 0, 5000, {
        try {
            val url = URL("http://${host.toString()}/upload/interest-rates")
            if (uploadFile(url, fileContents)) {
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
    connection.readTimeout = 60000
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
                println("Failed to " + method + " data. Status Code: " + connection.responseCode + ". Message: " + connection.responseMessage)
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
    connection.readTimeout = 60000
    connection.setRequestProperty("Connection", "Keep-Alive")
    connection.setRequestProperty("Cache-Control", "no-cache")
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)

    val request = DataOutputStream(connection.outputStream)
    request.writeBytes(hyphens + boundary + clrf)
    request.writeBytes("Content-Disposition: form-data; name=\"rates\" filename=\"example.rates.txt\"$clrf")
    request.writeBytes(clrf)
    request.writeBytes(file)
    request.writeBytes(clrf)
    request.writeBytes(hyphens + boundary + hyphens + clrf)

    if (connection.responseCode == 200) {
        return true
    } else {
        println("Could not upload file. Status Code: " + connection + ". Message: " + connection.responseMessage)
        return false
    }
}

private fun getNodeConfig(cliParams: CliParams.RunNode): NodeConfiguration {
    if (!Files.exists(cliParams.dir)) {
        throw NotSetupException("Missing config directory. Please run node setup before running the node")
    }

    if (!Files.exists(cliParams.dir.resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))) {
        throw NotSetupException("Missing identity file. Please run node setup before running the node")
    }

    val configFile = cliParams.dir.resolve("config").toFile()
    return loadConfigFile(configFile, cliParams.defaultLegalName)
}

private fun loadConfigFile(configFile: File, defaultLegalName: String): NodeConfiguration {
    if (!configFile.exists()) {
        createDefaultConfigFile(configFile, defaultLegalName)
        println("Default config created at $configFile.")
    }

    val config = ConfigFactory.parseFile(configFile).withFallback(ConfigFactory.load())
    return NodeConfigurationFromConfig(config)
}

private fun createIdentities(params: CliParams.SetupNode, nodeConf: NodeConfiguration) {
    val mockNetwork = MockNetwork(false)
    val node = MockNetwork.MockNode(params.dir, nodeConf, mockNetwork, null, setOf(NetworkMapService.Type, SimpleNotaryService.Type), 0, null)
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
