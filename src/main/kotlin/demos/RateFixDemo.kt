/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package demos

import contracts.Cash
import core.*
import core.messaging.LegallyIdentifiableNode
import core.node.Node
import core.node.NodeConfiguration
import core.node.services.ArtemisMessagingService
import core.node.services.NodeInterestRates
import core.serialization.deserialize
import core.utilities.ANSIProgressRenderer
import core.utilities.BriefLogFormatter
import core.utilities.Emoji
import joptsimple.OptionParser
import protocols.RatesFixProtocol
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
    val oracleAddrArg = parser.accepts("oracle").withRequiredArg().required()
    val oracleIdentityArg = parser.accepts("oracle-identity-file").withRequiredArg().required()

    val fixOfArg = parser.accepts("fix-of").withRequiredArg().defaultsTo("LIBOR 2016-03-16 30")
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

    // Load oracle stuff (in lieu of having a network map service)
    val oracleAddr = ArtemisMessagingService.makeRecipient(options.valueOf(oracleAddrArg))
    val oracleIdentity = Files.readAllBytes(Paths.get(options.valueOf(oracleIdentityArg))).deserialize<Party>(includeClassName = true)
    val oracleNode = LegallyIdentifiableNode(oracleAddr, oracleIdentity)

    val fixOf: FixOf = NodeInterestRates.parseFixOf(options.valueOf(fixOfArg))
    val expectedRate = BigDecimal(options.valueOf(expectedRateArg))
    val rateTolerance = BigDecimal(options.valueOf(rateToleranceArg))

    // Bring up node.
    val myNetAddr = ArtemisMessagingService.toHostAndPort(options.valueOf(networkAddressArg))
    val config = object : NodeConfiguration {
        override val myLegalName: String = "Rate fix demo node"
    }
    val node = logElapsedTime("Node startup") { Node(dir, myNetAddr, config, null).start() }

    // Make a garbage transaction that includes a rate fix.
    val tx = TransactionBuilder()
    tx.addOutputState(Cash.State(node.storage.myLegalIdentity.ref(1), 1500.DOLLARS, node.keyManagement.freshKey().public))
    val protocol = RatesFixProtocol(tx, oracleNode, fixOf, expectedRate, rateTolerance)
    ANSIProgressRenderer.progressTracker = protocol.progressTracker
    node.smm.add("demo.ratefix", protocol).get()

    // Show the user the output.
    println("Got rate fix")
    println()
    print(Emoji.renderIfSupported(tx.toWireTransaction()))
    println(tx.toSignedTransaction().sigs)

    node.stop()
}