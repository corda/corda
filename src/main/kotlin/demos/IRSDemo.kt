package demos

import com.google.common.net.HostAndPort
import com.typesafe.config.ConfigFactory
import core.Party
import core.logElapsedTime
import core.node.Node
import core.node.NodeConfiguration
import core.node.NodeConfigurationFromConfig
import core.node.services.ArtemisMessagingService
import core.node.services.MockNetworkMapCache
import core.node.services.NodeInfo
import core.node.services.ServiceType
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

    // Temporary flags until network map and service discovery is fleshed out. The identity file does NOT contain the
    // network address because all this stuff is meant to come from a dynamic discovery service anyway, and the identity
    // is meant to be long-term stable. It could contain a domain name, but we may end up not routing messages directly
    // to DNS-identified endpoints anyway (e.g. consider onion routing as a possibility).
    val timestamperIdentityFile = parser.accepts("timestamper-identity-file").withRequiredArg().required()
    val timestamperNetAddr = parser.accepts("timestamper-address").requiredIf(timestamperIdentityFile).withRequiredArg()

    val rateOracleIdentityFile = parser.accepts("rates-oracle-identity-file").withRequiredArg().required()
    val rateOracleNetAddr = parser.accepts("rates-oracle-address").requiredIf(rateOracleIdentityFile).withRequiredArg()

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
    BriefLogFormatter.initVerbose("+demo.irsdemo", "-org.apache.activemq")

    val dir = Paths.get(options.valueOf(dirArg))
    val configFile = dir.resolve("config")

    if (!Files.exists(dir)) {
        Files.createDirectory(dir)
    }

    val config = loadConfigFile(configFile)

    val myNetAddr = HostAndPort.fromString(options.valueOf(networkAddressArg)).withDefaultPort(Node.DEFAULT_PORT)

    // The timestamping node runs in the same process as the one that passes null to Node constructor.
    val timestamperId = if (options.valueOf(timestamperNetAddr).equals(options.valueOf(networkAddressArg))) {
        null
    } else {
        try {
            nodeInfo(options.valueOf(timestamperNetAddr), options.valueOf(timestamperIdentityFile), setOf(ServiceType.Timestamping))
        } catch (e: Exception) {
            null
        }
    }

    // The timestamping node runs in the same process as the one that passes null to Node constructor.
    val rateOracleId = if (options.valueOf(rateOracleNetAddr).equals(options.valueOf(networkAddressArg))) {
        null
    } else {
        try {
            nodeInfo(options.valueOf(rateOracleNetAddr), options.valueOf(rateOracleIdentityFile), setOf(ServiceType.RatesOracle))
        } catch (e: Exception) {
            null
        }
    }

    val node = logElapsedTime("Node startup") { Node(dir, myNetAddr, config, timestamperId, DemoClock()).start() }

    // Add self to network map
    (node.services.networkMapCache as MockNetworkMapCache).partyNodes.add(node.info)

    // Add rates oracle to network map
    (node.services.networkMapCache as MockNetworkMapCache).ratesOracleNodes.add(rateOracleId)

    val hostAndPortStrings = options.valuesOf(fakeTradeWithAddr)
    val identityFiles = options.valuesOf(fakeTradeWithIdentityFile)
    if (hostAndPortStrings.size != identityFiles.size) {
        throw IllegalArgumentException("Different number of peer addresses (${hostAndPortStrings.size}) and identities (${identityFiles.size})")
    }
    for ((hostAndPortString, identityFile) in hostAndPortStrings.zip(identityFiles)) {
        try {
            val peerId = nodeInfo(hostAndPortString, identityFile)
            (node.services.networkMapCache as MockNetworkMapCache).partyNodes.add(peerId)
        } catch (e: Exception) {
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
        val party = Files.readAllBytes(path).deserialize<Party>(includeClassName = true)
        return NodeInfo(ArtemisMessagingService.makeRecipient(addr), party, advertisedServices = advertisedServices)
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
