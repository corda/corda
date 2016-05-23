package com.r3corda.demos

import com.r3corda.contracts.Cash
import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.FixOf
import com.r3corda.core.contracts.TransactionBuilder
import com.r3corda.core.crypto.Party
import com.r3corda.core.logElapsedTime
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.utilities.BriefLogFormatter
import com.r3corda.core.utilities.Emoji
import com.r3corda.demos.api.InterestRateSwapAPI
import joptsimple.OptionParser
import com.r3corda.node.internal.Node
import com.r3corda.node.services.clientapi.NodeInterestRates
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.messaging.ArtemisMessagingService
import com.r3corda.node.utilities.*
import com.r3corda.protocols.RatesFixProtocol
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Creates a dummy transaction that requires a rate fix within a certain range, and gets it signed by an oracle
 * service.
 */
fun main(args: Array<String>) {
    val parser = OptionParser()
    val networkAddressArg = parser.accepts("network-address").withRequiredArg().required()
    val dirArg = parser.accepts("directory").withRequiredArg().defaultsTo("rate-fix-demo-data")
    val networkMapAddrArg = parser.accepts("network-map").withRequiredArg().required()
    val networkMapIdentityArg = parser.accepts("network-map-identity-file").withRequiredArg().required()
    val oracleAddrArg = parser.accepts("oracle").withRequiredArg().required()
    val oracleIdentityArg = parser.accepts("oracle-identity-file").withRequiredArg().required()

    val fixOfArg = parser.accepts("fix-of").withRequiredArg().defaultsTo("LIBOR 2016-03-16 1M")
    val expectedRateArg = parser.accepts("expected-rate").withRequiredArg().defaultsTo("0.67")
    val rateToleranceArg = parser.accepts("rate-tolerance").withRequiredArg().defaultsTo("0.1")

    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        exitProcess(1)
    }

    // Suppress the Artemis MQ noise, and activate the demo logging.
    BriefLogFormatter.initVerbose("+demo.ratefix", "-org.apache.activemq")

    // TODO: Move this into the AbstractNode class.
    val dir = Paths.get(options.valueOf(dirArg))
    if (!Files.exists(dir)) {
        Files.createDirectory(dir)
    }

    val networkMapAddr = ArtemisMessagingService.makeRecipient(options.valueOf(networkMapAddrArg))
    val networkMapIdentity = Files.readAllBytes(Paths.get(options.valueOf(networkMapIdentityArg))).deserialize<Party>()
    val networkMapAddress = NodeInfo(networkMapAddr, networkMapIdentity)

    // Load oracle stuff (in lieu of having a network map service)
    val oracleAddr = ArtemisMessagingService.makeRecipient(options.valueOf(oracleAddrArg))
    val oracleIdentity = Files.readAllBytes(Paths.get(options.valueOf(oracleIdentityArg))).deserialize<Party>()
    val oracleNode = NodeInfo(oracleAddr, oracleIdentity)

    val fixOf: FixOf = NodeInterestRates.parseFixOf(options.valueOf(fixOfArg))
    val expectedRate = BigDecimal(options.valueOf(expectedRateArg))
    val rateTolerance = BigDecimal(options.valueOf(rateToleranceArg))

    // Bring up node.
    var advertisedServices: Set<ServiceType> = emptySet()
    val myNetAddr = ArtemisMessagingService.toHostAndPort(options.valueOf(networkAddressArg))
    val config = object : NodeConfiguration {
        override val myLegalName: String = "Rate fix demo node"
        override val exportJMXto: String = "http"
        override val nearestCity: String = "Atlantis"
    }

    val node = logElapsedTime("Node startup") { Node(dir, myNetAddr, config, networkMapAddress,
            advertisedServices, DemoClock(),
            listOf(InterestRateSwapAPI::class.java)).start() }

    val notary = node.services.networkMapCache.notaryNodes[0]

    // Make a garbage transaction that includes a rate fix.
    val tx = TransactionBuilder()
    tx.addOutputState(Cash.State(node.storage.myLegalIdentity.ref(1), 1500.DOLLARS, node.keyManagement.freshKey().public, notary.identity))
    val protocol = RatesFixProtocol(tx, oracleNode, fixOf, expectedRate, rateTolerance)
    ANSIProgressRenderer.progressTracker = protocol.progressTracker
    node.smm.add("demo.ratefix", protocol).get()
    node.stop()

    // Show the user the output.
    println("Got rate fix")
    println()
    print(Emoji.renderIfSupported(tx.toWireTransaction()))
    println(tx.toSignedTransaction().sigs)
}