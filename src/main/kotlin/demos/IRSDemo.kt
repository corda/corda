package demos

import com.google.common.net.HostAndPort
import com.typesafe.config.ConfigFactory
import core.Party
import core.logElapsedTime
import core.node.Node
import core.node.NodeConfiguration
import core.node.NodeConfigurationFromConfig
import core.node.NodeInfo
import core.node.services.NetworkMapService
import core.node.services.NodeInterestRates
import core.node.services.NotaryService
import core.node.services.ServiceType
import core.node.subsystems.ArtemisMessagingService
import core.serialization.deserialize
import core.utilities.BriefLogFormatter
import demos.protocols.AutoOfferProtocol
import demos.protocols.ExitServerProtocol
import demos.protocols.UpdateBusinessDayProtocol
import joptsimple.OptionParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

// IRS DEMO
//
// TODO: Please see TBD

fun main(args: Array<String>) {
    val parser = OptionParser()
    val networkAddressArg = parser.accepts("network-address").withRequiredArg().required()
    val dirArg = parser.accepts("directory").withRequiredArg().defaultsTo("nodedata")

    val networkMapIdentityFile = parser.accepts("network-map-identity-file").withRequiredArg()
    val networkMapNetAddr = parser.accepts("network-map-address").requiredIf(networkMapIdentityFile).withRequiredArg()

    // Use these to list one or more peers (again, will be superseded by discovery implementation)
    val fakeTradeWithAddr = parser.accepts("fake-trade-with-address").withRequiredArg().required()
    val fakeTradeWithIdentityFile = parser.accepts("fake-trade-with-identity-file").withRequiredArg().required()

    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        printHelp()
        exitProcess(1)
    }

    // Suppress the Artemis MQ noise, and activate the demo logging.
    BriefLogFormatter.initVerbose("+demo.irsdemo", "+api-call", "+platform.deal", "-org.apache.activemq")

    val dir = Paths.get(options.valueOf(dirArg))
    val configFile = dir.resolve("config")

    if (!Files.exists(dir)) {
        Files.createDirectory(dir)
    }

    val config = loadConfigFile(configFile)
    val advertisedServices: Set<ServiceType>
    val myNetAddr = HostAndPort.fromString(options.valueOf(networkAddressArg)).withDefaultPort(Node.DEFAULT_PORT)

    val networkMapId = if (options.valueOf(networkMapNetAddr).equals(options.valueOf(networkAddressArg))) {
        // This node provides network map and notary services
        advertisedServices = setOf(NetworkMapService.Type, NotaryService.Type)
        null
    } else {
        advertisedServices = setOf(NodeInterestRates.Type)
        try {
            nodeInfo(options.valueOf(networkMapNetAddr), options.valueOf(networkMapIdentityFile), setOf(NetworkMapService.Type, NotaryService.Type))
        } catch (e: Exception) {
            null
        }
    }

    val node = logElapsedTime("Node startup") { Node(dir, myNetAddr, config, networkMapId, advertisedServices, DemoClock()).start() }

    // TODO: This should all be replaced by the identity service being updated
    // as the network map changes.
    val hostAndPortStrings = options.valuesOf(fakeTradeWithAddr)
    val identityFiles = options.valuesOf(fakeTradeWithIdentityFile)
    if (hostAndPortStrings.size != identityFiles.size) {
        throw IllegalArgumentException("Different number of peer addresses (${hostAndPortStrings.size}) and identities (${identityFiles.size})")
    }
    for ((hostAndPortString, identityFile) in hostAndPortStrings.zip(identityFiles)) {
        try {
            val peerId = nodeInfo(hostAndPortString, identityFile)
            node.services.identityService.registerIdentity(peerId.identity)
        } catch (e: Exception) {
            println("Could not load peer identity file \"$identityFile\".")
        }
    }

    // Register handlers for the demo
    AutoOfferProtocol.Handler.register(node)
    UpdateBusinessDayProtocol.Handler.register(node)
    ExitServerProtocol.Handler.register(node)

    try {
        while (true) Thread.sleep(Long.MAX_VALUE)
    } catch(e: InterruptedException) {
        node.stop()
    }
    exitProcess(0)
}

fun nodeInfo(hostAndPortString: String, identityFile: String, advertisedServices: Set<ServiceType> = emptySet()): NodeInfo {
    try {
        val addr = HostAndPort.fromString(hostAndPortString).withDefaultPort(Node.DEFAULT_PORT)
        val path = Paths.get(identityFile)
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
