/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import com.google.common.net.HostAndPort
import contracts.CommercialPaper
import contracts.protocols.TwoPartyTradeProtocol
import core.*
import core.crypto.SecureHash
import core.crypto.generateKeyPair
import core.messaging.LegallyIdentifiableNode
import core.messaging.SingleMessageRecipient
import core.messaging.runOnNextMessage
import core.messaging.send
import core.serialization.deserialize
import core.utilities.BriefLogFormatter
import core.utilities.Emoji
import joptsimple.OptionParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.time.Instant
import java.util.*
import kotlin.system.exitProcess

// TRADING DEMO
//
// Please see docs/build/html/running-the-trading-demo.html


fun main(args: Array<String>) {
    val parser = OptionParser()
    val networkAddressArg = parser.accepts("network-address").withRequiredArg().required()
    val dirArg = parser.accepts("directory").withRequiredArg().defaultsTo("nodedata")

    // Some dummy functionality that won't last long ...

    // Mode flags for the first demo.
    val serviceFakeTradesArg = parser.accepts("service-fake-trades")
    val fakeTradeWithArg = parser.accepts("fake-trade-with").requiredUnless(serviceFakeTradesArg).withRequiredArg()

    // Temporary flags until network map and service discovery is fleshed out. The identity file does NOT contain the
    // network address because all this stuff is meant to come from a dynamic discovery service anyway, and the identity
    // is meant to be long-term stable. It could contain a domain name, but we may end up not routing messages directly
    // to DNS-identified endpoints anyway (e.g. consider onion routing as a possibility).
    val timestamperIdentityFile = parser.accepts("timestamper-identity-file").requiredIf(fakeTradeWithArg).withRequiredArg()
    val timestamperNetAddr = parser.accepts("timestamper-address").requiredIf(timestamperIdentityFile).withRequiredArg()

    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        printHelp()
        exitProcess(1)
    }

    BriefLogFormatter.initVerbose("platform.trade")

    val dir = Paths.get(options.valueOf(dirArg))
    val configFile = dir.resolve("config")

    if (!Files.exists(dir)) {
        Files.createDirectory(dir)
    }

    val config = loadConfigFile(configFile)

    val myNetAddr = HostAndPort.fromString(options.valueOf(networkAddressArg)).withDefaultPort(Node.DEFAULT_PORT)
    val listening = options.has(serviceFakeTradesArg)

    val timestamperId = if (options.has(timestamperIdentityFile)) {
        val addr = HostAndPort.fromString(options.valueOf(timestamperNetAddr)).withDefaultPort(Node.DEFAULT_PORT)
        val path = Paths.get(options.valueOf(timestamperIdentityFile))
        val party = Files.readAllBytes(path).deserialize<Party>(includeClassName = true)
        LegallyIdentifiableNode(ArtemisMessagingService.makeRecipient(addr), party)
    } else null

    val node = logElapsedTime("Node startup") { Node(dir, myNetAddr, config, timestamperId) }

    // Now do some fake nonsense just to give us some activity.

    (node.services.walletService as E2ETestWalletService).fillWithSomeTestCash(1000.DOLLARS)

    val timestampingAuthority = node.services.networkMapService.timestampingNodes.first()
    if (listening) {
        // Wait around until a node asks to start a trade with us. In a real system, this part would happen out of band
        // via some other system like an exchange or maybe even a manual messaging system like Bloomberg. But for the
        // next stage in our building site, we will just auto-generate fake trades to give our nodes something to do.
        //
        // Note that currently, the two-party trade protocol doesn't actually resolve dependencies of transactions!
        // Thus, we can make up whatever junk we like and trade non-existent cash/assets: the other side won't notice.
        // Obviously, fixing that is the next step.
        //
        // As the seller initiates the DVP/two-party trade protocol, here, we will be the buyer.
        node.net.addMessageHandler("test.junktrade") { msg, handlerRegistration ->
            val replyTo = msg.data.deserialize<SingleMessageRecipient>(includeClassName = true)
            val buyerSessionID = random63BitValue()
            println("Got a new junk trade request, sending back session ID and starting buy protocol")
            val future = TwoPartyTradeProtocol.runBuyer(node.smm, timestampingAuthority, replyTo, 100.DOLLARS,
                    CommercialPaper.State::class.java, buyerSessionID)

            future.whenComplete {
                println()
                println("Purchase complete - we are a happy customer! Final transaction is:")
                println()
                println(Emoji.renderIfSupported(it.tx))
                println()
                println("Waiting for another seller to connect. Or press Ctrl-C to shut me down.")
            }

            node.net.send("test.junktrade.initiate", replyTo, buyerSessionID)
        }
        println()
        println("Waiting for a seller to connect to us (run the other node) ...")
        println()
    } else {
        // Grab a session ID for the fake trade from the other side, then kick off the seller and sell them some junk.
        if (!options.has(fakeTradeWithArg)) {
            println("Need the --fake-trade-with command line argument")
            exitProcess(1)
        }
        val peerAddr = HostAndPort.fromString(options.valuesOf(fakeTradeWithArg).single()).withDefaultPort(Node.DEFAULT_PORT)
        val otherSide = ArtemisMessagingService.makeRecipient(peerAddr)
        node.net.runOnNextMessage("test.junktrade.initiate") { msg ->
            val sessionID = msg.data.deserialize<Long>()

            println("Got session ID back, now starting the sell protocol")

            val cpOwnerKey = node.keyManagement.freshKey()
            val commercialPaper = makeFakeCommercialPaper(cpOwnerKey.public)

            val future = TwoPartyTradeProtocol.runSeller(node.smm, timestampingAuthority,
                    otherSide, commercialPaper, 100.DOLLARS, cpOwnerKey, sessionID)

            future.whenComplete {
                println()
                println("Sale completed - we have a happy customer!")
                println()
                println("Final transaction is")
                println()
                println(Emoji.renderIfSupported(it.tx))
                println()
                node.stop()
            }
        }
        println()
        println("Sending a message to the listening/buying node ...")
        println()
        node.net.send("test.junktrade", otherSide, node.net.myAddress, includeClassName = true)
    }
}

fun makeFakeCommercialPaper(ownedBy: PublicKey): StateAndRef<CommercialPaper.State> {
    // Make a fake company that's issued its own paper.
    val party = Party("MegaCorp, Inc", generateKeyPair().public)
    // ownedBy here is the random key that gives us control over it.
    val paper = CommercialPaper.State(party.ref(1,2,3), ownedBy, 1100.DOLLARS, Instant.now() + 10.days)
    val randomRef = StateRef(SecureHash.randomSHA256(), 0)
    return StateAndRef(paper, randomRef)
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

    val configProps = configFile.toFile().reader().use {
        Properties().apply { load(it) }
    }

    val config = NodeConfiguration(configProps)

    // Make sure admin did actually edit at least the legal name.
    if (config.myLegalName == defaultLegalName)
        askAdminToEditConfig(configFile)

    return config
}

private fun createDefaultConfigFile(configFile: Path?, defaultLegalName: String) {
    Files.write(configFile,
            """
        # Node configuration: adjust below as needed, then delete this comment.
        myLegalName = $defaultLegalName
        """.trimIndent().toByteArray())
}

private fun printHelp() {
    println("""

    To run the listening node, alias "alpha" to "localhost" in your
    /etc/hosts file and then try a command line like this:

      --dir=alpha --service-fake-trades --network-address=alpha

    To run the node that initiates a trade, alias "beta" to "localhost"
    in your /etc/hosts file and then try a command line like this:

      --dir=beta --fake-trade-with=alpha --network-address=beta:31338 --timestamper-identity-file=alpha/identity-public --timestamper-address=alpha
    """.trimIndent())
}
