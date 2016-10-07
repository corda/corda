package com.r3corda.demos.attachment

import com.google.common.net.HostAndPort
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.failure
import com.r3corda.core.logElapsedTime
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.core.success
import com.r3corda.core.utilities.Emoji
import com.r3corda.core.utilities.LogHelper
import com.r3corda.node.internal.Node
import com.r3corda.node.services.config.ConfigHelper
import com.r3corda.node.services.config.FullNodeConfiguration
import com.r3corda.node.services.messaging.NodeMessagingClient
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.protocols.FinalityProtocol
import com.r3corda.testing.ALICE_KEY
import joptsimple.OptionParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.test.assertEquals

// ATTACHMENT DEMO
//
// Please see docs/build/html/running-the-demos.html and docs/build/html/tutorial-attachments.html
//
// This program is a simple demonstration of sending a transaction with an attachment from one node to another, and
// then accessing the attachment on the remote node.
//
// The different roles in the scenario this program can adopt are:

enum class Role(val legalName: String, val port: Int) {
    SENDER("Bank A", 31337),
    RECIPIENT("Bank B", 31340);

    val other: Role
        get() = when (this) {
            SENDER -> RECIPIENT
            RECIPIENT -> SENDER
        }
}

// And this is the directory under the current working directory where each node will create its own server directory,
// which holds things like checkpoints, keys, databases, message logs etc.
val DEFAULT_BASE_DIRECTORY = "./build/attachment-demo"

val PROSPECTUS_HASH = SecureHash.parse("decd098666b9657314870e192ced0c3519c2c9d395507a238338f8d003929de9")

private val log: Logger = LoggerFactory.getLogger("AttachmentDemo")

fun main(args: Array<String>) {
    val parser = OptionParser()

    val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java).required()
    val myNetworkAddress = parser.accepts("network-address").withRequiredArg().defaultsTo("localhost")
    val theirNetworkAddress = parser.accepts("other-network-address").withRequiredArg().defaultsTo("localhost")
    val apiNetworkAddress = parser.accepts("api-address").withRequiredArg().defaultsTo("localhost")
    val baseDirectoryArg = parser.accepts("base-directory").withRequiredArg().defaultsTo(DEFAULT_BASE_DIRECTORY)

    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        log.error(e.message)
        printHelp(parser)
        exitProcess(1)
    }

    val role = options.valueOf(roleArg)!!

    val myNetAddr = HostAndPort.fromString(options.valueOf(myNetworkAddress)).withDefaultPort(role.port)
    val theirNetAddr = HostAndPort.fromString(options.valueOf(theirNetworkAddress)).withDefaultPort(role.other.port)
    val apiNetAddr = HostAndPort.fromString(options.valueOf(apiNetworkAddress)).withDefaultPort(myNetAddr.port + 1)

    val baseDirectory = options.valueOf(baseDirectoryArg)!!

    // Suppress the Artemis MQ noise, and activate the demo logging.
    //
    // The first two strings correspond to the first argument to StateMachineManager.add() but the way we handle logging
    // for protocols will change in future.
    LogHelper.setLevel("-org.apache.activemq")

    val directory = Paths.get(baseDirectory, role.name.toLowerCase())
    log.info("Using base demo directory $directory")



    // Override the default config file (which you can find in the file "reference.conf") to give each node a name.
    val config = run {
        val myLegalName = role.legalName
        val configOverrides = mapOf(
                "myLegalName" to myLegalName,
                "artemisAddress" to myNetAddr.toString(),
                "webAddress" to apiNetAddr.toString()
        )
        FullNodeConfiguration(ConfigHelper.loadConfig(directory, allowMissingConfig = true, configOverrides = configOverrides))
    }

    // Which services will this instance of the node provide to the network?
    val advertisedServices: Set<ServiceInfo>

    // One of the two servers needs to run the network map and notary services. In such a trivial two-node network
    // the map is not very helpful, but we need one anyway. So just make the recipient side run the network map as it's
    // the side that sticks around waiting for the sender.
    val networkMapId = if (role == Role.SENDER) {
        advertisedServices = setOf(ServiceInfo(NetworkMapService.type), ServiceInfo(SimpleNotaryService.type))
        null
    } else {
        advertisedServices = emptySet()
        NodeMessagingClient.makeNetworkMapAddress(theirNetAddr)
    }

    // And now construct then start the node object. It takes a little while.
    val node = logElapsedTime("Node startup", log) {
        Node(config, networkMapId, advertisedServices).setup().start()
    }

    // What happens next depends on the role. The recipient sits around waiting for a transaction. The sender role
    // will contact the recipient and actually make something happen.
    when (role) {
        Role.RECIPIENT -> runRecipient(node)
        Role.SENDER -> {
            node.networkMapRegistrationFuture.success {
                // Pause a moment to give the network map time to update
                Thread.sleep(100L)
                val party = node.netMapCache.getNodeByLegalName(Role.RECIPIENT.legalName)?.legalIdentity ?: throw IllegalStateException("Cannot find other node?!")
                runSender(node, party)
            }
        }
    }

    node.run()
}

private fun runRecipient(node: Node) {
    val serviceHub = node.services

    // Normally we would receive the transaction from a more specific protocol, but in this case we let [FinalityProtocol]
    // handle receiving it for us.
    serviceHub.storageService.validatedTransactions.updates.subscribe { event ->
        // When the transaction is received, it's passed through [ResolveTransactionsProtocol], which first fetches any
        // attachments for us, then verifies the transaction. As such, by the time it hits the validated transaction store,
        // we have a copy of the attachment.
        val tx = event.tx
        if (tx.attachments.isNotEmpty()) {
            val attachment = serviceHub.storageService.attachments.openAttachment(tx.attachments.first())
            assertEquals(PROSPECTUS_HASH, attachment?.id)

            println("File received - we're happy!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(event.tx)}")
            thread {
                node.stop()
            }
        }
    }
}

private fun runSender(node: Node, otherSide: Party) {
    val serviceHub = node.services
    // Make sure we have the file in storage
    // TODO: We should have our own demo file, not share the trader demo file
    if (serviceHub.storageService.attachments.openAttachment(PROSPECTUS_HASH) == null) {
        com.r3corda.demos.Role::class.java.getResourceAsStream("bank-of-london-cp.jar").use {
            val id = node.storage.attachments.importAttachment(it)
            assertEquals(PROSPECTUS_HASH, id)
        }
    }

    // Create a trivial transaction that just passes across the attachment - in normal cases there would be
    // inputs, outputs and commands that refer to this attachment.
    val ptx = TransactionType.General.Builder(notary = null)
    ptx.addAttachment(serviceHub.storageService.attachments.openAttachment(PROSPECTUS_HASH)!!.id)

    // Despite not having any states, we have to have at least one signature on the transaction
    ptx.signWith(ALICE_KEY)

    // Send the transaction to the other recipient
    val tx = ptx.toSignedTransaction()
    serviceHub.startProtocol(FinalityProtocol(tx, emptySet(), setOf(otherSide))).success {
        thread {
            Thread.sleep(1000L) // Give the other side time to request the attachment
            node.stop()
        }
    }.failure {
        println("Failed to relay message ")
    }
}

private fun printHelp(parser: OptionParser) {
    println("""
    Usage: attachment-demo --role [RECIPIENT|SENDER] [options]
    Please refer to the documentation in docs/build/index.html for more info.

    """.trimIndent())
    parser.printHelpOn(System.out)
}
