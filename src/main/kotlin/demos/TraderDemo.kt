/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package demos

import co.paralleluniverse.fibers.Suspendable
import com.google.common.net.HostAndPort
import contracts.CommercialPaper
import core.*
import core.crypto.DigitalSignature
import core.crypto.SecureHash
import core.crypto.generateKeyPair
import core.messaging.LegallyIdentifiableNode
import core.messaging.SingleMessageRecipient
import core.node.Node
import core.node.NodeConfiguration
import core.node.NodeConfigurationFromProperties
import core.node.services.ArtemisMessagingService
import core.node.services.NodeAttachmentService
import core.node.services.NodeWalletService
import core.protocols.ProtocolLogic
import core.serialization.deserialize
import core.utilities.ANSIProgressRenderer
import core.utilities.BriefLogFormatter
import core.utilities.Emoji
import core.utilities.ProgressTracker
import joptsimple.OptionParser
import protocols.TimestampingProtocol
import protocols.TwoPartyTradeProtocol
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.time.Instant
import java.util.*
import kotlin.system.exitProcess
import kotlin.test.assertEquals

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

    // Suppress the Artemis MQ noise, and activate the demo logging.
    BriefLogFormatter.initVerbose("+demo.buyer", "+demo.seller", "-org.apache.activemq")

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

    val node = logElapsedTime("Node startup") { Node(dir, myNetAddr, config, timestamperId).start() }

    if (listening) {
        // For demo purposes just extract attachment jars when saved to disk, so the user can explore them.
        // Buyer will fetch the attachment from the seller.
        val attachmentsPath = (node.storage.attachments as NodeAttachmentService).let {
            it.automaticallyExtractAttachments = true
            it.storePath
        }

        val buyer = TraderDemoProtocolBuyer(attachmentsPath)
        ANSIProgressRenderer.progressTracker = buyer.progressTracker
        node.smm.add("demo.buyer", buyer).get()   // This thread will halt forever here.
    } else {
        if (!options.has(fakeTradeWithArg)) {
            println("Need the --fake-trade-with command line argument")
            exitProcess(1)
        }

        // Make sure we have the transaction prospectus attachment loaded into our store.
        if (node.storage.attachments.openAttachment(TraderDemoProtocolSeller.PROSPECTUS_HASH) == null) {
            TraderDemoProtocolSeller::class.java.getResourceAsStream("bank-of-london-cp.jar").use {
                val id = node.storage.attachments.importAttachment(it)
                assertEquals(TraderDemoProtocolSeller.PROSPECTUS_HASH, id)
            }
        }

        val peerAddr = HostAndPort.fromString(options.valuesOf(fakeTradeWithArg).single()).withDefaultPort(Node.DEFAULT_PORT)
        val otherSide = ArtemisMessagingService.makeRecipient(peerAddr)
        val seller = TraderDemoProtocolSeller(myNetAddr, otherSide)
        ANSIProgressRenderer.progressTracker = seller.progressTracker
        node.smm.add("demo.seller", seller).get()
        node.stop()
    }
}

// We create a couple of ad-hoc test protocols that wrap the two party trade protocol, to give us the demo logic.

class TraderDemoProtocolBuyer(private val attachmentsPath: Path) : ProtocolLogic<Unit>() {
    companion object {
        object WAITING_FOR_SELLER_TO_CONNECT : ProgressTracker.Step("Waiting for seller to connect to us")
        object STARTING_BUY : ProgressTracker.Step("Seller connected, purchasing commercial paper asset")
    }
    override val progressTracker = ProgressTracker(WAITING_FOR_SELLER_TO_CONNECT, STARTING_BUY)

    @Suspendable
    override fun call() {
        // Give us some cash. Note that as nodes do not currently track forward pointers, we can spend the same cash over
        // and over again and the double spends will never be detected! Fixing that is the next step.
        (serviceHub.walletService as NodeWalletService).fillWithSomeTestCash(1500.DOLLARS)

        while (true) {
            // Wait around until a node asks to start a trade with us. In a real system, this part would happen out of band
            // via some other system like an exchange or maybe even a manual messaging system like Bloomberg. But for the
            // next stage in our building site, we will just auto-generate fake trades to give our nodes something to do.
            //
            // As the seller initiates the DVP/two-party trade protocol, here, we will be the buyer.
            try {
                progressTracker.currentStep = WAITING_FOR_SELLER_TO_CONNECT
                val hostname = receive<HostAndPort>("test.junktrade", 0).validate { it.withDefaultPort(Node.DEFAULT_PORT) }
                val newPartnerAddr = ArtemisMessagingService.makeRecipient(hostname)
                val sessionID = random63BitValue()
                progressTracker.currentStep = STARTING_BUY
                send("test.junktrade", newPartnerAddr, 0, sessionID)

                val tsa = serviceHub.networkMapService.timestampingNodes[0]
                val buyer = TwoPartyTradeProtocol.Buyer(newPartnerAddr, tsa.identity, 1000.DOLLARS,
                        CommercialPaper.State::class.java, sessionID)
                val tradeTX: SignedTransaction = subProtocol(buyer)

                logger.info("Purchase complete - we are a happy customer! Final transaction is: " +
                        "\n\n${Emoji.renderIfSupported(tradeTX.tx)}")

                logIssuanceAttachment(tradeTX)
            } catch(e: Exception) {
                logger.error("Something went wrong whilst trading!", e)
            }
        }
    }

    private fun logIssuanceAttachment(tradeTX: SignedTransaction) {
        // Find the original CP issuance.
        val search = TransactionGraphSearch(serviceHub.storageService.validatedTransactions, listOf(tradeTX.tx))
        search.query = TransactionGraphSearch.Query(withCommandOfType = CommercialPaper.Commands.Issue::class.java)
        val cpIssuance = search.call().single()

        cpIssuance.attachments.first().let {
            val p = attachmentsPath.toAbsolutePath().resolve("$it.jar")
            logger.info("""

The issuance of the commercial paper came with an attachment. You can find it expanded in this directory:
$p

${Emoji.renderIfSupported(cpIssuance)}""")
        }
    }
}

class TraderDemoProtocolSeller(val myAddress: HostAndPort,
                               val otherSide: SingleMessageRecipient,
                               override val progressTracker: ProgressTracker = TraderDemoProtocolSeller.tracker()) : ProtocolLogic<Unit>() {
    companion object {
        val PROSPECTUS_HASH = SecureHash.parse("decd098666b9657314870e192ced0c3519c2c9d395507a238338f8d003929de9")

        object ANNOUNCING : ProgressTracker.Step("Announcing to the buyer node")
        object SELF_ISSUING : ProgressTracker.Step("Got session ID back, issuing and timestamping some commercial paper")
        object TRADING : ProgressTracker.Step("Starting the trade protocol")

        // We vend a progress tracker that already knows there's going to be a TwoPartyTradingProtocol involved at some
        // point: by setting up the tracker in advance, the user can see what's coming in more detail, instead of being
        // surprised when it appears as a new set of tasks below the current one.
        fun tracker() = ProgressTracker(ANNOUNCING, SELF_ISSUING, TRADING).apply {
            childrenFor[TRADING] = TwoPartyTradeProtocol.Seller.tracker()
        }
    }

    @Suspendable
    override fun call() {
        progressTracker.currentStep = ANNOUNCING

        val sessionID = sendAndReceive<Long>("test.junktrade", otherSide, 0, 0, myAddress).validate { it }

        progressTracker.currentStep = SELF_ISSUING

        val tsa = serviceHub.networkMapService.timestampingNodes[0]
        val cpOwnerKey = serviceHub.keyManagementService.freshKey()
        val commercialPaper = selfIssueSomeCommercialPaper(cpOwnerKey.public, tsa)

        progressTracker.currentStep = TRADING

        val seller = object : TwoPartyTradeProtocol.Seller(otherSide, tsa, commercialPaper, 1000.DOLLARS,
                                                           cpOwnerKey, sessionID, progressTracker.childrenFor[TRADING]!!) {
            override fun signWithOurKey(partialTX: SignedTransaction): DigitalSignature.WithKey {
                val s = super.signWithOurKey(partialTX)
                // Fake delay to make it look like we're doing something more intensive than we really are, to show
                // the progress tracking framework.
                Thread.sleep(2000)
                return s
            }
        }
        val tradeTX: SignedTransaction = subProtocol(seller)

        logger.info("Sale completed - we have a happy customer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(tradeTX.tx)}")
    }

    @Suspendable
    fun selfIssueSomeCommercialPaper(ownedBy: PublicKey, tsa: LegallyIdentifiableNode): StateAndRef<CommercialPaper.State> {
        // Make a fake company that's issued its own paper.
        val keyPair = generateKeyPair()
        val party = Party("Bank of London", keyPair.public)

        val issuance = run {
            val tx = CommercialPaper().generateIssue(party.ref(1,2,3), 1100.DOLLARS, Instant.now() + 10.days)

            // TODO: Consider moving these two steps below into generateIssue.

            // Attach the prospectus.
            tx.addAttachment(serviceHub.storageService.attachments.openAttachment(PROSPECTUS_HASH)!!)

            // Timestamp it, all CP must be timestamped.
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

    val config = NodeConfigurationFromProperties(configProps)

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
