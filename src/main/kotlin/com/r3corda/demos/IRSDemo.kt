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
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.system.exitProcess
import com.r3corda.demos.utilities.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
     * Corresponds to roles 'SetupNodeA' and 'SetupNodeB'.
     */
    class SetupNode(
            val node: IRSDemoNode,
            val dir: Path,
            val defaultLegalName: String
    ) : CliParams()

    /**
     * Corresponds to roles 'NodeA' and 'NodeB'.
     */
    class RunNode(
            val node: IRSDemoNode,
            val dir: Path,
            val networkAddress : HostAndPort,
            val apiAddress: HostAndPort,
            val mapAddress: String,
            val identityFile: Path,
            val tradeWithIdentities: List<Path>,
            val uploadRates: Boolean,
            val defaultLegalName: String,
            val autoSetup: Boolean // Run Setup for both nodes automatically with default arguments
    ) : CliParams()

    /**
     * Corresponds to role 'Trade'.
     */
    class Trade(
            val apiAddress: HostAndPort,
            val tradeId: String
    ) : CliParams()

    /**
     * Corresponds to role 'Date'.
     */
    class DateChange(
            val apiAddress: HostAndPort,
            val dateString: String
    ) : CliParams()

    /**
     * Corresponds to --help.
     */
    object Help : CliParams()

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
            if (options.has(CliParamsSpec.help)) {
                return Help
            }
            val role: IRSDemoRole = options.valueOf(CliParamsSpec.roleArg) ?: throw IllegalArgumentException("Please provide a role")
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
    val roleArg = parser.accepts("role")
            .withRequiredArg().ofType(IRSDemoRole::class.java)
    val networkAddressArg =
            parser.accepts("network-address", "The p2p networking address to use")
            .withOptionalArg().ofType(String::class.java)
    val apiAddressArg =
            parser.accepts("api-address", "The address to expose the HTTP API on")
            .withOptionalArg().ofType(String::class.java)
    val baseDirectoryArg =
            parser.accepts("base-directory", "The directory to put all files under")
            .withOptionalArg().defaultsTo(CliParams.defaultBaseDirectory)
    val networkMapIdentityFile =
            parser.accepts("network-map-identity-file", "The file containing the Party info of the network map")
            .withOptionalArg()
    val networkMapNetAddr =
            parser.accepts("network-map-address", "The address of the network map")
            .withRequiredArg().defaultsTo("localhost")
    val fakeTradeWithIdentityFile =
            parser.accepts("fake-trade-with-identity-file", "Extra identities to be registered with the identity service")
            .withOptionalArg()
    val nonOptions = parser.nonOptions()
    val help = parser.accepts("help", "Prints this help").forHelp()
}

class IRSDemoPluginRegistry : CordaPluginRegistry {
    override val webApis: List<Class<*>> = listOf(InterestRateSwapAPI::class.java)
    override val staticServeDirs: Map<String, String> = mapOf("irsdemo" to javaClass.getResource("irswebdemo").toExternalForm())
    override val requiredProtocols: Map<String, Set<String>> = mapOf(
            Pair(AutoOfferProtocol.Requester::class.java.name, setOf(InterestRateSwap.State::class.java.name)),
            Pair(UpdateBusinessDayProtocol.Broadcast::class.java.name, setOf(java.time.LocalDate::class.java.name)),
            Pair(ExitServerProtocol.Broadcast::class.java.name, setOf(kotlin.Int::class.java.name)))
    override val servicePlugins: List<Class<*>> = emptyList()
}

private class NotSetupException: Throwable {
    constructor(message: String): super(message) {}
}

private val log: Logger = LoggerFactory.getLogger("IRSDemo")

fun main(args: Array<String>) {
    exitProcess(runIRSDemo(args))
}

fun runIRSDemo(args: Array<String>): Int {
    val cliParams = try {
        CliParams.parse(CliParamsSpec.parser.parse(*args))
    } catch (e: Exception) {
        log.error(e.message)
        printHelp(CliParamsSpec.parser)
        return 1
    }

    // Suppress the Artemis MQ noise, and activate the demo logging.
    BriefLogFormatter.initVerbose("+demo.irsdemo", "+api-call", "+platform.deal", "-org.apache.activemq")

    return when (cliParams) {
        is CliParams.SetupNode -> setup(cliParams)
        is CliParams.RunNode -> runNode(cliParams)
        is CliParams.Trade -> runTrade(cliParams)
        is CliParams.DateChange -> runDateChange(cliParams)
        is CliParams.Help -> {
            printHelp(CliParamsSpec.parser)
            0
        }
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

        val node = startNode(cliParams, networkMap)

        if (cliParams.uploadRates) {
            runUploadRates(cliParams.apiAddress)
        }

        try {
            while (true) Thread.sleep(Long.MAX_VALUE)
        } catch(e: InterruptedException) {
            node.stop()
        }
    } catch (e: NotSetupException) {
        log.error(e.message)
        return 1
    }

    return 0
}

private fun runDateChange(cliParams: CliParams.DateChange): Int {
    log.info("Changing date to " + cliParams.dateString)
    val url = URL("http://${cliParams.apiAddress}/api/irs/demodate")
    if (putJson(url, "\"" + cliParams.dateString + "\"")) {
        log.info("Date changed")
        return 0
    } else {
        log.error("Date failed to change")
        return 1
    }
}

private fun runTrade(cliParams: CliParams.Trade): Int {
    log.info("Uploading tradeID " + cliParams.tradeId)
    // Note: the getResourceAsStream is an ugly hack to get the jvm to search in the right location
    val fileContents = IOUtils.toString(CliParams::class.java.getResourceAsStream("example-irs-trade.json"))
    val tradeFile = fileContents.replace("tradeXXX", cliParams.tradeId)
    val url = URL("http://${cliParams.apiAddress}/api/irs/deals")
    if (postJson(url, tradeFile)) {
        log.info("Trade sent")
        return 0
    } else {
        log.error("Trade failed to send")
        return 1
    }
}

private fun createRecipient(addr: String) : SingleMessageRecipient {
    val hostAndPort = HostAndPort.fromString(addr).withDefaultPort(Node.DEFAULT_PORT)
    return ArtemisMessagingService.makeRecipient(hostAndPort)
}

private fun startNode(params: CliParams.RunNode, networkMap: SingleMessageRecipient) : Node {
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

    val node = logElapsedTime("Node startup", log) {
        Node(params.dir, params.networkAddress, params.apiAddress, config, networkMapId, advertisedServices, DemoClock()).start()
    }
    // TODO: This should all be replaced by the identity service being updated
    // as the network map changes.
    for (identityFile in params.tradeWithIdentities) {
        node.services.identityService.registerIdentity(parsePartyFromFile(identityFile))
    }

    return node
}

private fun parsePartyFromFile(path: Path) = Files.readAllBytes(path).deserialize<Party>()

private fun nodeInfo(recipient: SingleMessageRecipient, identityFile: Path, advertisedServices: Set<ServiceType> = emptySet()): NodeInfo {
    try {
        val party = parsePartyFromFile(identityFile)
        return NodeInfo(recipient, party, advertisedServices)
    } catch (e: Exception) {
        log.error("Could not find identify file $identityFile.")
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
                log.info("Rates uploaded successfully")
            } else {
                log.error("Could not upload rates. Retrying in 5 seconds. ")
            }
        } catch (e: Exception) {
            log.error("Could not upload rates due to exception. Retrying in 5 seconds")
        }
    })
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
        log.warn("Default config created at $configFile.")
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

private fun printHelp(parser: OptionParser) {
    val roleList = IRSDemoRole.values().joinToString(separator = "|") { it.toString() }
    println("""
    Usage: irsdemo --role $roleList [<TradeName>|<DateValue>] [options]
    Please refer to the documentation in docs/build/index.html for more info.

    """.trimIndent())
    parser.printHelpOn(System.out)
}
