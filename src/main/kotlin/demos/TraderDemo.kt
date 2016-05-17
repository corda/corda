package demos

import co.paralleluniverse.fibers.Suspendable
import com.google.common.net.HostAndPort
import com.typesafe.config.ConfigFactory
import contracts.CommercialPaper
import core.contracts.*
import core.crypto.Party
import core.crypto.SecureHash
import core.crypto.generateKeyPair
import core.days
import core.logElapsedTime
import core.messaging.SingleMessageRecipient
import core.messaging.StateMachineManager
import core.node.Node
import core.node.NodeConfigurationFromConfig
import core.node.NodeInfo
import core.node.services.NetworkMapService
import core.node.services.NodeAttachmentService
import core.node.services.NotaryService
import core.node.services.ServiceType
import core.node.subsystems.ArtemisMessagingService
import core.node.subsystems.NodeWalletService
import core.protocols.ProtocolLogic
import core.random63BitValue
import core.seconds
import core.serialization.deserialize
import core.utilities.ANSIProgressRenderer
import core.utilities.BriefLogFormatter
import core.utilities.Emoji
import core.utilities.ProgressTracker
import joptsimple.OptionParser
import protocols.NotaryProtocol
import protocols.TwoPartyTradeProtocol
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.time.Instant
import kotlin.system.exitProcess
import kotlin.test.assertEquals

// TRADING DEMO
//
// Please see docs/build/html/running-the-trading-demo.html

fun main(args: Array<String>) {
    val parser = OptionParser()

    val modeArg = parser.accepts("mode").withRequiredArg().required()
    val myNetworkAddress = parser.accepts("network-address").withRequiredArg().defaultsTo("localhost")
    val theirNetworkAddress = parser.accepts("other-network-address").withRequiredArg().defaultsTo("localhost")

    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        printHelp()
        exitProcess(1)
    }

    val mode = options.valueOf(modeArg)

    val DIRNAME = "trader-demo"
    val BUYER = "buyer"
    val SELLER = "seller"

    if (mode !in setOf(BUYER, SELLER)) {
        printHelp()
        exitProcess(1)
    }

    // Suppress the Artemis MQ noise, and activate the demo logging.
    BriefLogFormatter.initVerbose("+demo.buyer", "+demo.seller", "-org.apache.activemq")

    val dir = Paths.get(DIRNAME, mode)
    Files.createDirectories(dir)

    val advertisedServices: Set<ServiceType>
    val myNetAddr = HostAndPort.fromString(options.valueOf(myNetworkAddress)).withDefaultPort(if (mode == BUYER) Node.DEFAULT_PORT else 31340)
    val theirNetAddr = HostAndPort.fromString(options.valueOf(theirNetworkAddress)).withDefaultPort(if (mode == SELLER) Node.DEFAULT_PORT else 31340)

    val listening = mode == BUYER
    val config = run {
        val override = ConfigFactory.parseString("""myLegalName = ${ if (mode == BUYER) "Bank A" else "Bank B" }""")
        NodeConfigurationFromConfig(override.withFallback(ConfigFactory.load()))
    }

    val networkMapId = if (mode == SELLER) {
        val path = Paths.get(DIRNAME, BUYER, "identity-public")
        val party = Files.readAllBytes(path).deserialize<Party>()
        advertisedServices = emptySet()
        NodeInfo(ArtemisMessagingService.makeRecipient(theirNetAddr), party, setOf(NetworkMapService.Type))
    } else {
        // We must be the network map service
        advertisedServices = setOf(NetworkMapService.Type, NotaryService.Type)
        null
    }

    // TODO: Remove this once checkpoint resume works.
    StateMachineManager.restoreCheckpointsOnStart = false
    val node = logElapsedTime("Node startup") { Node(dir, myNetAddr, config, networkMapId, advertisedServices).start() }

    if (listening) {
        // For demo purposes just extract attachment jars when saved to disk, so the user can explore them.
        // Buyer will fetch the attachment from the seller.
        val attachmentsPath = (node.storage.attachments as NodeAttachmentService).let {
            it.automaticallyExtractAttachments = true
            it.storePath
        }

        val buyer = TraderDemoProtocolBuyer(attachmentsPath, node.info.identity)
        ANSIProgressRenderer.progressTracker = buyer.progressTracker
        node.smm.add("demo.buyer", buyer).get()   // This thread will halt forever here.
    } else {
        // Make sure we have the transaction prospectus attachment loaded into our store.
        if (node.storage.attachments.openAttachment(TraderDemoProtocolSeller.PROSPECTUS_HASH) == null) {
            TraderDemoProtocolSeller::class.java.getResourceAsStream("bank-of-london-cp.jar").use {
                val id = node.storage.attachments.importAttachment(it)
                assertEquals(TraderDemoProtocolSeller.PROSPECTUS_HASH, id)
            }
        }

        val otherSide = ArtemisMessagingService.makeRecipient(theirNetAddr)
        val seller = TraderDemoProtocolSeller(myNetAddr, otherSide)
        ANSIProgressRenderer.progressTracker = seller.progressTracker
        node.smm.add("demo.seller", seller).get()
        node.stop()
    }
}

// We create a couple of ad-hoc test protocols that wrap the two party trade protocol, to give us the demo logic.

class TraderDemoProtocolBuyer(private val attachmentsPath: Path, val notary: Party) : ProtocolLogic<Unit>() {
    companion object {
        object WAITING_FOR_SELLER_TO_CONNECT : ProgressTracker.Step("Waiting for seller to connect to us")

        object STARTING_BUY : ProgressTracker.Step("Seller connected, purchasing commercial paper asset")
    }

    override val progressTracker = ProgressTracker(WAITING_FOR_SELLER_TO_CONNECT, STARTING_BUY)

    @Suspendable
    override fun call() {
        // Give us some cash. Note that as nodes do not currently track forward pointers, we can spend the same cash over
        // and over again and the double spends will never be detected! Fixing that is the next step.
        (serviceHub.walletService as NodeWalletService).fillWithSomeTestCash(notary, 1500.DOLLARS)

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

                val notary = serviceHub.networkMapCache.notaryNodes[0]
                val buyer = TwoPartyTradeProtocol.Buyer(newPartnerAddr, notary.identity, 1000.DOLLARS,
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

        val notary = serviceHub.networkMapCache.notaryNodes[0]
        val cpOwnerKey = serviceHub.keyManagementService.freshKey()
        val commercialPaper = selfIssueSomeCommercialPaper(cpOwnerKey.public, notary)

        progressTracker.currentStep = TRADING

        val seller = TwoPartyTradeProtocol.Seller(otherSide, notary, commercialPaper, 1000.DOLLARS, cpOwnerKey,
                sessionID, progressTracker.childrenFor[TRADING]!!)
        val tradeTX: SignedTransaction = subProtocol(seller)

        logger.info("Sale completed - we have a happy customer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(tradeTX.tx)}")
    }

    @Suspendable
    fun selfIssueSomeCommercialPaper(ownedBy: PublicKey, notaryNode: NodeInfo): StateAndRef<CommercialPaper.State> {
        // Make a fake company that's issued its own paper.
        val keyPair = generateKeyPair()
        val party = Party("Bank of London", keyPair.public)

        val issuance = run {
            val tx = CommercialPaper().generateIssue(party.ref(1, 2, 3), 1100.DOLLARS, Instant.now() + 10.days, notaryNode.identity)

            // TODO: Consider moving these two steps below into generateIssue.

            // Attach the prospectus.
            tx.addAttachment(serviceHub.storageService.attachments.openAttachment(PROSPECTUS_HASH)!!)

            // Timestamp it, all CP must be timestamped.
            tx.setTime(Instant.now(), notaryNode.identity, 30.seconds)
            tx.signWith(keyPair)
            val notarySig = subProtocol(NotaryProtocol(tx.toWireTransaction()))
            tx.addSignatureUnchecked(notarySig)
            tx.toSignedTransaction(true)
        }

        serviceHub.recordTransactions(listOf(issuance))

        val move = run {
            val tx = TransactionBuilder()
            CommercialPaper().generateMove(tx, issuance.tx.outRef(0), ownedBy)
            tx.signWith(keyPair)
            val notarySig = subProtocol(NotaryProtocol(tx.toWireTransaction()))
            tx.addSignatureUnchecked(notarySig)
            tx.toSignedTransaction(true)
        }

        serviceHub.recordTransactions(listOf(move))

        return move.tx.outRef(0)
    }

}

private fun printHelp() {
    println("Please refer to the documentation in docs/build/index.html to learn how to run the demo.")
}
