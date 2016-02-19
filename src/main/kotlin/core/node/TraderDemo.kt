/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import co.paralleluniverse.fibers.Suspendable
import com.google.common.net.HostAndPort
import contracts.CommercialPaper
import contracts.protocols.TwoPartyTradeProtocol
import core.*
import core.crypto.generateKeyPair
import core.messaging.LegallyIdentifiableNode
import core.protocols.ProtocolLogic
import core.messaging.SingleMessageRecipient
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

    if (listening && config.myLegalName != "Bank of Zurich") {
        println("The buyer node must have a legal name of 'Bank of Zurich'. Please edit the config file.")
        exitProcess(1)
    }

    // The timestamping node runs in the same process as the buyer protocol is run.
    val timestamperId = if (options.has(timestamperIdentityFile)) {
        val addr = HostAndPort.fromString(options.valueOf(timestamperNetAddr)).withDefaultPort(Node.DEFAULT_PORT)
        val path = Paths.get(options.valueOf(timestamperIdentityFile))
        val party = Files.readAllBytes(path).deserialize<Party>(includeClassName = true)
        LegallyIdentifiableNode(ArtemisMessagingService.makeRecipient(addr), party)
    } else null

    val node = logElapsedTime("Node startup") { Node(dir, myNetAddr, config, timestamperId) }

    if (listening) {
        node.smm.add("demo.buyer", TraderDemoProtocolBuyer()).get()   // This thread will halt forever here.
    } else {
        if (!options.has(fakeTradeWithArg)) {
            println("Need the --fake-trade-with command line argument")
            exitProcess(1)
        }
        val peerAddr = HostAndPort.fromString(options.valuesOf(fakeTradeWithArg).single()).withDefaultPort(Node.DEFAULT_PORT)
        val otherSide = ArtemisMessagingService.makeRecipient(peerAddr)
        node.smm.add("demo.seller", TraderDemoProtocolSeller(myNetAddr, otherSide)).get()
        node.stop()
    }
}

// We create a couple of ad-hoc test protocols that wrap the two party trade protocol, to give us the demo logic.

class TraderDemoProtocolBuyer() : ProtocolLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Give us some cash. Note that as nodes do not currently track forward pointers, we can spend the same cash over
        // and over again and the double spends will never be detected! Fixing that is the next step.
        (serviceHub.walletService as E2ETestWalletService).fillWithSomeTestCash(1500.DOLLARS)

        while (true) {
            // Wait around until a node asks to start a trade with us. In a real system, this part would happen out of band
            // via some other system like an exchange or maybe even a manual messaging system like Bloomberg. But for the
            // next stage in our building site, we will just auto-generate fake trades to give our nodes something to do.
            //
            // As the seller initiates the DVP/two-party trade protocol, here, we will be the buyer.
            try {
                println()
                println("Waiting for a seller to connect to us!")

                val hostname = receive<HostAndPort>("test.junktrade", 0).validate { it.withDefaultPort(Node.DEFAULT_PORT) }
                val newPartnerAddr = ArtemisMessagingService.makeRecipient(hostname)
                val sessionID = random63BitValue()
                println()
                println("Got a new junk trade request from $newPartnerAddr, sending back a fresh session ID and starting buy protocol")
                println()
                send("test.junktrade", newPartnerAddr, 0, sessionID)

                val tsa = serviceHub.networkMapService.timestampingNodes[0]
                val buyer = TwoPartyTradeProtocol.Buyer(newPartnerAddr, tsa.identity, 1000.DOLLARS,
                        CommercialPaper.State::class.java, sessionID)
                val tradeTX: SignedTransaction = subProtocol(buyer)

                println()
                println("Purchase complete - we are a happy customer! Final transaction is:")
                println()
                println(Emoji.renderIfSupported(tradeTX.tx))
                println()
                println("Waiting for another seller to connect. Or press Ctrl-C to shut me down.")
            } catch(e: Exception) {
                println()
                println("Something went wrong whilst trading!")
                println()
                e.printStackTrace()
            }
        }
    }
}

class TraderDemoProtocolSeller(val myAddress: HostAndPort,
                               val otherSide: SingleMessageRecipient) : ProtocolLogic<Unit>() {
    @Suspendable
    override fun call() {
        println()
        println("Announcing ourselves to the buyer node!")
        println()

        val sessionID = sendAndReceive<Long>("test.junktrade", otherSide, 0, 0, myAddress).validate { it }

        println()
        println("Got session ID back, issuing and timestamping some commercial paper")

        val tsa = serviceHub.networkMapService.timestampingNodes[0]
        val cpOwnerKey = serviceHub.keyManagementService.freshKey()
        val commercialPaper = makeFakeCommercialPaper(cpOwnerKey.public, tsa)

        println()
        println("Timestamped my commercial paper issuance, starting the trade protocol.")

        val seller = TwoPartyTradeProtocol.Seller(otherSide, tsa, commercialPaper, 1000.DOLLARS, cpOwnerKey, sessionID)
        val tradeTX: SignedTransaction = subProtocol(seller)

        println()
        println("Sale completed - we have a happy customer!")
        println()
        println("Final transaction is")
        println()
        println(Emoji.renderIfSupported(tradeTX.tx))
        println()
    }

    @Suspendable
    fun makeFakeCommercialPaper(ownedBy: PublicKey, tsa: LegallyIdentifiableNode): StateAndRef<CommercialPaper.State> {
        // Make a fake company that's issued its own paper.
        val keyPair = generateKeyPair()
        val party = Party("MegaCorp, Inc", keyPair.public)

        val issuance = run {
            val tx = CommercialPaper().generateIssue(party.ref(1,2,3), 1100.DOLLARS, Instant.now() + 10.days)

            tx.setTime(Instant.now(), tsa.identity, 30.seconds)
            val tsaSig = subProtocol(TimestampingProtocol(tsa, tx.toWireTransaction().serialized))
            tx.checkAndAddSignature(tsaSig)

            tx.signWith(keyPair)
            tx.toSignedTransaction(true)
        }

        val move = run {
            val tx = TransactionBuilder()
            CommercialPaper().generateMove(tx, issuance.tx.outRef(0), ownedBy)
            tx.signWith(keyPair)
            tx.toSignedTransaction(true)
        }

        with(serviceHub.storageService) {
            validatedTransactions[issuance.id] = issuance
            validatedTransactions[move.id] = move
        }

        return move.tx.outRef(0)
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
