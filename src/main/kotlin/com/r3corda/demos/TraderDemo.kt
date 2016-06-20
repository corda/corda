package com.r3corda.demos

import co.paralleluniverse.fibers.Suspendable
import com.google.common.net.HostAndPort
import com.r3corda.contracts.CommercialPaper
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.days
import com.r3corda.core.logElapsedTime
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.seconds
import com.r3corda.core.serialization.deserialize
import com.r3corda.node.internal.Node
import com.r3corda.node.internal.testing.WalletFiller
import com.r3corda.node.services.config.NodeConfigurationFromConfig
import com.r3corda.node.services.messaging.ArtemisMessagingService
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.persistence.NodeAttachmentService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.core.utilities.BriefLogFormatter
import com.r3corda.core.utilities.Emoji
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.protocols.NotaryProtocol
import com.r3corda.protocols.TwoPartyTradeProtocol
import com.typesafe.config.ConfigFactory
import joptsimple.OptionParser
import joptsimple.OptionSet
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
//
// This program is a simple driver for exercising the two party trading protocol. Until Corda has a unified node server
// programs like this are required to wire up the pieces and run a demo scenario end to end.
//
// If you are creating a new scenario, you can use this program as a template for creating your own driver. Make sure to
// copy/paste the right parts of the build.gradle file to make sure it gets a script to run it deposited in
// build/install/r3prototyping/bin
//
// In this scenario, a buyer wants to purchase some commercial paper by swapping his cash for the CP. The seller learns
// that the buyer exists, and sends them a message to kick off the trade. The seller, having obtained his CP, then quits
// and the buyer goes back to waiting. The buyer will sell as much CP as he can!
//
// The different roles in the scenario this program can adopt are:

enum class Role {
    BUYER,
    SELLER
}

// And this is the directory under the current working directory where each node will create its own server directory,
// which holds things like checkpoints, keys, databases, message logs etc.
val DIRNAME = "trader-demo"

fun main(args: Array<String>) {
    val parser = OptionParser()

    val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java).required()
    val myNetworkAddress = parser.accepts("network-address").withRequiredArg().defaultsTo("localhost")
    val theirNetworkAddress = parser.accepts("other-network-address").withRequiredArg().defaultsTo("localhost")

    val options = parseOptions(args, parser)
    val role = options.valueOf(roleArg)!!

    val myNetAddr = HostAndPort.fromString(options.valueOf(myNetworkAddress)).withDefaultPort(
            when (role) {
                Role.BUYER -> 31337
                Role.SELLER -> 31340
            }
    )
    val theirNetAddr = HostAndPort.fromString(options.valueOf(theirNetworkAddress)).withDefaultPort(
            when (role) {
                Role.BUYER -> 31340
                Role.SELLER -> 31337
            }
    )

    // Suppress the Artemis MQ noise, and activate the demo logging.
    //
    // The first two strings correspond to the first argument to StateMachineManager.add() but the way we handle logging
    // for protocols will change in future.
    BriefLogFormatter.initVerbose("+demo.buyer", "+demo.seller", "-org.apache.activemq")

    val directory = setupDirectory(role)

    // Override the default config file (which you can find in the file "reference.conf") to give each node a name.
    val config = run {
        val myLegalName = when (role) {
            Role.BUYER -> "Bank A"
            Role.SELLER -> "Bank B"
        }
        val override = ConfigFactory.parseString("myLegalName = $myLegalName")
        NodeConfigurationFromConfig(override.withFallback(ConfigFactory.load()))
    }

    // Which services will this instance of the node provide to the network?
    val advertisedServices: Set<ServiceType>

    // One of the two servers needs to run the network map and notary services. In such a trivial two-node network
    // the map is not very helpful, but we need one anyway. So just make the buyer side run the network map as it's
    // the side that sticks around waiting for the seller.
    var cashIssuer: Party? = null
    val networkMapId = if (role == Role.BUYER) {
        advertisedServices = setOf(NetworkMapService.Type, SimpleNotaryService.Type)
        null
    } else {
        // In a real system, the identity file of the network map would be shipped with the server software, and there'd
        // be a single shared map service  (this is analagous to the DNS seeds in Bitcoin).
        //
        // TODO: AbstractNode should write out the full NodeInfo object and we should just load it here.
        val path = Paths.get(DIRNAME, Role.BUYER.name.toLowerCase(), "identity-public")
        val party = Files.readAllBytes(path).deserialize<Party>()
        advertisedServices = emptySet()
        cashIssuer = party
        NodeInfo(ArtemisMessagingService.makeRecipient(theirNetAddr), party, setOf(NetworkMapService.Type))
    }

    // And now construct then start the node object. It takes a little while.
    val node = logElapsedTime("Node startup") {
        Node(directory, myNetAddr, config, networkMapId, advertisedServices).start()
    }

    // TODO: Replace with a separate trusted cash issuer
    if (cashIssuer == null) {
        cashIssuer = node.services.storageService.myLegalIdentity
    }

    // What happens next depends on the role. The buyer sits around waiting for a trade to start. The seller role
    // will contact the buyer and actually make something happen.
    val amount = 1000.DOLLARS `issued by` cashIssuer.ref(0) // Note: "0" has to match the reference used in the wallet filler
    if (role == Role.BUYER) {
        runBuyer(node, amount)
    } else {
        runSeller(myNetAddr, node, theirNetAddr, amount)
    }
}

fun setupDirectory(mode: Role): Path {
    val directory = Paths.get(DIRNAME, mode.name.toLowerCase())
    Files.createDirectories(directory)
    return directory
}

fun parseOptions(args: Array<String>, parser: OptionParser): OptionSet {
    try {
        return parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        println("Please refer to the documentation in docs/build/index.html to learn how to run the demo.")
        exitProcess(1)
    }
}

fun runSeller(myNetAddr: HostAndPort, node: Node, theirNetAddr: HostAndPort, amount: Amount<Issued<Currency>>) {
    // The seller will sell some commercial paper to the buyer, who will pay with (self issued) cash.
    //
    // The CP sale transaction comes with a prospectus PDF, which will tag along for the ride in an
    // attachment. Make sure we have the transaction prospectus attachment loaded into our store.
    //
    // This can also be done via an HTTP upload, but here we short-circuit and do it from code.
    if (node.storage.attachments.openAttachment(TraderDemoProtocolSeller.PROSPECTUS_HASH) == null) {
        TraderDemoProtocolSeller::class.java.getResourceAsStream("bank-of-london-cp.jar").use {
            val id = node.storage.attachments.importAttachment(it)
            assertEquals(TraderDemoProtocolSeller.PROSPECTUS_HASH, id)
        }
    }

    if (node.isPreviousCheckpointsPresent) {
        node.smm.findStateMachines(TraderDemoProtocolSeller::class.java).forEach {
            it.second.get()
        }
    } else {
        val otherSide = ArtemisMessagingService.makeRecipient(theirNetAddr)
        val seller = TraderDemoProtocolSeller(myNetAddr, otherSide, amount)
        node.smm.add("demo.seller", seller).get()
    }

    node.stop()
}

fun runBuyer(node: Node, amount: Amount<Issued<Currency>>) {
    // Buyer will fetch the attachment from the seller automatically when it resolves the transaction.
    // For demo purposes just extract attachment jars when saved to disk, so the user can explore them.
    val attachmentsPath = (node.storage.attachments as NodeAttachmentService).let {
        it.automaticallyExtractAttachments = true
        it.storePath
    }

    val future = if (node.isPreviousCheckpointsPresent) {
        val (@Suppress("UNUSED_VARIABLE") buyer, future) = node.smm.findStateMachines(TraderDemoProtocolBuyer::class.java).single()
        future
    } else {
        // We use a simple scenario-specific wrapper protocol to make things happen.
        val buyer = TraderDemoProtocolBuyer(attachmentsPath, node.info.identity, amount)
        node.smm.add("demo.buyer", buyer)
    }

    future.get()   // This thread will halt forever here.
}

// We create a couple of ad-hoc test protocols that wrap the two party trade protocol, to give us the demo logic.

val DEMO_TOPIC = "initiate.demo.trade"

class TraderDemoProtocolBuyer(private val attachmentsPath: Path,
                              val notary: Party,
                              val amount: Amount<Issued<Currency>>) : ProtocolLogic<Unit>() {
    companion object {
        object WAITING_FOR_SELLER_TO_CONNECT : ProgressTracker.Step("Waiting for seller to connect to us")

        object STARTING_BUY : ProgressTracker.Step("Seller connected, purchasing commercial paper asset")
    }

    override val progressTracker = ProgressTracker(WAITING_FOR_SELLER_TO_CONNECT, STARTING_BUY)

    @Suspendable
    override fun call() {
        // Self issue some cash.
        //
        // TODO: At some point this demo should be extended to have a central bank node.
        WalletFiller.fillWithSomeTestCash(serviceHub, notary, 3000.DOLLARS)

        while (true) {
            // Wait around until a node asks to start a trade with us. In a real system, this part would happen out of band
            // via some other system like an exchange or maybe even a manual messaging system like Bloomberg. But for the
            // next stage in our building site, we will just auto-generate fake trades to give our nodes something to do.
            //
            // As the seller initiates the two-party trade protocol, here, we will be the buyer.
            try {
                progressTracker.currentStep = WAITING_FOR_SELLER_TO_CONNECT
                val hostname = receive<HostAndPort>(DEMO_TOPIC, 0).validate { it.withDefaultPort(Node.DEFAULT_PORT) }
                val newPartnerAddr = ArtemisMessagingService.makeRecipient(hostname)

                // The session ID disambiguates the test trade.
                val sessionID = random63BitValue()
                progressTracker.currentStep = STARTING_BUY
                send(DEMO_TOPIC, newPartnerAddr, 0, sessionID)

                val notary = serviceHub.networkMapCache.notaryNodes[0]
                val buyer = TwoPartyTradeProtocol.Buyer(newPartnerAddr, notary.identity, amount,
                        CommercialPaper.State::class.java, sessionID)

                // This invokes the trading protocol and out pops our finished transaction.
                val tradeTX: SignedTransaction = subProtocol(buyer)
                serviceHub.recordTransactions(listOf(tradeTX))

                logger.info("Purchase complete - we are a happy customer! Final transaction is: " +
                        "\n\n${Emoji.renderIfSupported(tradeTX.tx)}")

                logIssuanceAttachment(tradeTX)
                logBalance()

            } catch(e: Exception) {
                logger.error("Something went wrong whilst trading!", e)
            }
        }
    }

    private fun logBalance() {
        val balances = serviceHub.walletService.cashBalances.entries.map { "${it.key.currencyCode} ${it.value}" }
        logger.info("Remaining balance: ${balances.joinToString()}")
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
                               val amount: Amount<Issued<Currency>>,
                               override val progressTracker: ProgressTracker = TraderDemoProtocolSeller.tracker()) : ProtocolLogic<Unit>() {
    companion object {
        val PROSPECTUS_HASH = SecureHash.parse("decd098666b9657314870e192ced0c3519c2c9d395507a238338f8d003929de9")

        object ANNOUNCING : ProgressTracker.Step("Announcing to the buyer node")

        object SELF_ISSUING : ProgressTracker.Step("Got session ID back, issuing and timestamping some commercial paper")

        object TRADING : ProgressTracker.Step("Starting the trade protocol") {
            override fun childProgressTracker(): ProgressTracker = TwoPartyTradeProtocol.Seller.tracker()
        }

        // We vend a progress tracker that already knows there's going to be a TwoPartyTradingProtocol involved at some
        // point: by setting up the tracker in advance, the user can see what's coming in more detail, instead of being
        // surprised when it appears as a new set of tasks below the current one.
        fun tracker() = ProgressTracker(ANNOUNCING, SELF_ISSUING, TRADING)
    }

    @Suspendable
    override fun call() {
        progressTracker.currentStep = ANNOUNCING

        val sessionID = sendAndReceive<Long>(DEMO_TOPIC, otherSide, 0, 0, myAddress).validate { it }

        progressTracker.currentStep = SELF_ISSUING

        val notary = serviceHub.networkMapCache.notaryNodes[0]
        val cpOwnerKey = serviceHub.keyManagementService.freshKey()
        val commercialPaper = selfIssueSomeCommercialPaper(cpOwnerKey.public, notary)

        progressTracker.currentStep = TRADING

        val seller = TwoPartyTradeProtocol.Seller(otherSide, notary, commercialPaper, amount, cpOwnerKey,
                sessionID, progressTracker.getChildProgressTracker(TRADING)!!)
        val tradeTX: SignedTransaction = subProtocol(seller)
        serviceHub.recordTransactions(listOf(tradeTX))

        logger.info("Sale completed - we have a happy customer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(tradeTX.tx)}")
    }

    @Suspendable
    fun selfIssueSomeCommercialPaper(ownedBy: PublicKey, notaryNode: NodeInfo): StateAndRef<CommercialPaper.State> {
        // Make a fake company that's issued its own paper.
        val keyPair = generateKeyPair()
        val party = Party("Bank of London", keyPair.public)

        val issuance: SignedTransaction = run {
            val tx = CommercialPaper().generateIssue(1100.DOLLARS `issued by` party.ref(1, 2, 3), Instant.now() + 10.days, notaryNode.identity)

            // TODO: Consider moving these two steps below into generateIssue.

            // Attach the prospectus.
            tx.addAttachment(serviceHub.storageService.attachments.openAttachment(PROSPECTUS_HASH)!!)

            // Requesting timestamping, all CP must be timestamped.
            tx.setTime(Instant.now(), notaryNode.identity, 30.seconds)

            // Sign it as ourselves.
            tx.signWith(keyPair)

            // Get the notary to sign the timestamp
            val notarySig = subProtocol(NotaryProtocol.Client(tx.toWireTransaction()))
            tx.addSignatureUnchecked(notarySig)

            // Commit it to local storage.
            val stx = tx.toSignedTransaction(true)
            serviceHub.recordTransactions(listOf(stx))

            stx
        }

        // Now make a dummy transaction that moves it to a new key, just to show that resolving dependencies works.
        val move: SignedTransaction = run {
            val builder = TransactionType.General.Builder()
            CommercialPaper().generateMove(builder, issuance.tx.outRef(0), ownedBy)
            builder.signWith(keyPair)
            builder.addSignatureUnchecked(subProtocol(NotaryProtocol.Client(builder.toWireTransaction())))
            val tx = builder.toSignedTransaction(true)
            serviceHub.recordTransactions(listOf(tx))
            tx
        }

        return move.tx.outRef(0)
    }

}
