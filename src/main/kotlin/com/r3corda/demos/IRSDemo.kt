package com.r3corda.demos

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.contracts.InterestRateSwap
import com.r3corda.core.crypto.Party
import com.r3corda.core.logElapsedTime
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.utilities.LogHelper
import com.r3corda.demos.api.InterestRateSwapAPI
import com.r3corda.demos.api.NodeInterestRates
import com.r3corda.demos.protocols.AutoOfferProtocol
import com.r3corda.demos.protocols.ExitServerProtocol
import com.r3corda.demos.protocols.UpdateBusinessDayProtocol
import com.r3corda.demos.utilities.postJson
import com.r3corda.demos.utilities.putJson
import com.r3corda.demos.utilities.uploadFile
import com.r3corda.node.internal.AbstractNode
import com.r3corda.node.internal.Node
import com.r3corda.node.services.config.ConfigHelper
import com.r3corda.node.services.config.FullNodeConfiguration
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.messaging.NodeMessagingClient
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.testing.node.MockNetwork
import joptsimple.OptionParser
import joptsimple.OptionSet
import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    Date,
    Rates
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
            val networkAddress: HostAndPort,
            val apiAddress: HostAndPort,
            val mapAddress: String,
            val tradeWithIdentities: List<Path>,
            val uploadRates: Boolean,
            val defaultLegalName: String,
            val autoSetup: Boolean, // Run Setup for both nodes automatically with default arguments
            val h2Port: Int
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
     * Corresponds to role 'Rates'.
     */
    class UploadRates(
            val apiAddress: HostAndPort
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

        private fun defaultH2Port(node: IRSDemoNode) =
                when (node) {
                    IRSDemoNode.NodeA -> Node.DEFAULT_PORT + 4
                    IRSDemoNode.NodeB -> Node.DEFAULT_PORT + 5
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
                    tradeWithIdentities = if (options.has(CliParamsSpec.fakeTradeWithIdentityFile)) {
                        options.valuesOf(CliParamsSpec.fakeTradeWithIdentityFile).map { Paths.get(it) }
                    } else {
                        listOf(nodeDirectory(options, node.other).resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))
                    },
                    uploadRates = node == IRSDemoNode.NodeB,
                    defaultLegalName = legalName(node),
                    autoSetup = !options.has(CliParamsSpec.baseDirectoryArg) && !options.has(CliParamsSpec.fakeTradeWithIdentityFile),
                    h2Port = options.valueOf(CliParamsSpec.h2PortArg.defaultsTo(defaultH2Port(node)))
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

        private fun parseRatesUpload(options: OptionSet): UploadRates {
            return UploadRates(
                    apiAddress = HostAndPort.fromString(options.valueOf(
                            CliParamsSpec.apiAddressArg.defaultsTo("localhost:${defaultApiPort(IRSDemoNode.NodeB)}")
                    ))

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
                IRSDemoRole.Rates -> parseRatesUpload(options)
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
    val networkMapNetAddr =
            parser.accepts("network-map-address", "The address of the network map")
            .withRequiredArg().defaultsTo("localhost")
    val fakeTradeWithIdentityFile =
            parser.accepts("fake-trade-with-identity-file", "Extra identities to be registered with the identity service")
            .withOptionalArg()
    val h2PortArg = parser.accepts("h2-port").withRequiredArg().ofType(Int::class.java)
    val nonOptions = parser.nonOptions()
    val help = parser.accepts("help", "Prints this help").forHelp()
}

class IRSDemoPluginRegistry : CordaPluginRegistry() {
    override val webApis: List<Class<*>> = listOf(InterestRateSwapAPI::class.java)
    override val staticServeDirs: Map<String, String> = mapOf("irsdemo" to javaClass.getResource("irswebdemo").toExternalForm())
    override val requiredProtocols: Map<String, Set<String>> = mapOf(
            Pair(AutoOfferProtocol.Requester::class.java.name, setOf(InterestRateSwap.State::class.java.name)),
            Pair(UpdateBusinessDayProtocol.Broadcast::class.java.name, setOf(java.time.LocalDate::class.java.name)),
            Pair(ExitServerProtocol.Broadcast::class.java.name, setOf(kotlin.Int::class.java.name)))
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

    // Suppress the Artemis MQ noise, and activate the demo logging
    LogHelper.setLevel("+IRSDemo", "+api-call", "+platform.deal", "-org.apache.activemq")

    return when (cliParams) {
        is CliParams.SetupNode -> setup(cliParams)
        is CliParams.RunNode -> runNode(cliParams)
        is CliParams.Trade -> runTrade(cliParams)
        is CliParams.DateChange -> runDateChange(cliParams)
        is CliParams.UploadRates -> runUploadRates(cliParams)
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

    val configFile = params.dir.resolve("config")
    val config = loadConfigFile(params.dir, configFile, emptyMap(), params.defaultLegalName)
    if (!Files.exists(params.dir.resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))) {
        createIdentities(config)
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

        node.run()
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

fun runUploadRates(cliParams: CliParams.UploadRates) = runUploadRates(cliParams.apiAddress).get()

private fun createRecipient(addr: String): SingleMessageRecipient {
    val hostAndPort = HostAndPort.fromString(addr).withDefaultPort(Node.DEFAULT_PORT)
    return NodeMessagingClient.makeNetworkMapAddress(hostAndPort)
}

private fun startNode(params: CliParams.RunNode, networkMap: SingleMessageRecipient): Node {
    val config = getNodeConfig(params)
    val advertisedServices: Set<ServiceInfo>
    val networkMapId =
            when (params.node) {
                IRSDemoNode.NodeA -> {
                    advertisedServices = setOf(ServiceInfo(NetworkMapService.Type), ServiceInfo(SimpleNotaryService.Type))
                    null
                }
                IRSDemoNode.NodeB -> {
                    advertisedServices = setOf(ServiceInfo(NodeInterestRates.Type))
                    networkMap
                }
            }

    val node = logElapsedTime("Node startup", log) {
        Node(config, networkMapId, advertisedServices, DemoClock()).setup().start()
    }

    return node
}

private fun parsePartyFromFile(path: Path) = Files.readAllBytes(path).deserialize<Party>()

private fun runUploadRates(host: HostAndPort): ListenableFuture<Int> {
    // Note: the getResourceAsStream is an ugly hack to get the jvm to search in the right location
    val fileContents = IOUtils.toString(CliParams::class.java.getResourceAsStream("example.rates.txt"))
    var timer: Timer? = null
    val result = SettableFuture.create<Int>()
    timer = fixedRateTimer("upload-rates", false, 0, 5000, {
        try {
            val url = URL("http://${host.toString()}/upload/interest-rates")
            if (uploadFile(url, fileContents)) {
                timer!!.cancel()
                log.info("Rates uploaded successfully")
                result.set(0)
            } else {
                log.error("Could not upload rates. Retrying in 5 seconds. ")
                result.set(1)
            }
        } catch (e: Exception) {
            log.error("Could not upload rates due to exception. Retrying in 5 seconds")
        }
    })
    return result
}

private fun getNodeConfig(cliParams: CliParams.RunNode): FullNodeConfiguration {
    if (!Files.exists(cliParams.dir)) {
        throw NotSetupException("Missing config directory. Please run node setup before running the node")
    }

    if (!Files.exists(cliParams.dir.resolve(AbstractNode.PUBLIC_IDENTITY_FILE_NAME))) {
        throw NotSetupException("Missing identity file. Please run node setup before running the node")
    }

    val configFile = cliParams.dir.resolve("config")
    val configOverrides = mapOf(
            "artemisAddress" to cliParams.networkAddress.toString(),
            "webAddress" to cliParams.apiAddress.toString(),
            "h2port" to cliParams.h2Port.toString()
    )
    return loadConfigFile(cliParams.dir, configFile, configOverrides, cliParams.defaultLegalName)
}

private fun loadConfigFile(baseDir: Path, configFile: Path, configOverrides: Map<String, String>, defaultLegalName: String): FullNodeConfiguration {
    if (!Files.exists(configFile)) {
        createDefaultConfigFile(configFile, defaultLegalName)
        log.warn("Default config created at $configFile.")
    }
    return FullNodeConfiguration(ConfigHelper.loadConfig(baseDir, configFileOverride = configFile, configOverrides = configOverrides))
}

private fun createIdentities(nodeConf: NodeConfiguration) {
    val mockNetwork = MockNetwork(false)
    val node = MockNetwork.MockNode(nodeConf, mockNetwork, null, setOf(ServiceInfo(NetworkMapService.Type), ServiceInfo(SimpleNotaryService.Type)), 0, null)
    node.start()
    node.stop()
}

private fun createDefaultConfigFile(configFile: Path, legalName: String) {
    Files.write(configFile,
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
