/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import co.paralleluniverse.common.util.VisibleForTesting
import co.paralleluniverse.fibers.Suspendable
import core.*
import core.crypto.DigitalSignature
import core.crypto.signWithECDSA
import core.messaging.*
import core.protocols.ProtocolLogic
import core.serialization.SerializedBytes
import core.serialization.deserialize
import core.serialization.serialize
import org.slf4j.LoggerFactory
import java.security.KeyPair
import java.time.Clock
import java.time.Duration
import javax.annotation.concurrent.ThreadSafe

class TimestampingMessages {
    // TODO: Improve the messaging api to have a notion of sender+replyTo topic (optional?)
    data class Request(val tx: SerializedBytes<WireTransaction>, val replyTo: MessageRecipients, val replyToTopic: String)
}

/**
 * This class implements the server side of the timestamping protocol, using the local clock. A future version might
 * add features like checking against other NTP servers to make sure the clock hasn't drifted by too much.
 *
 * See the doc site to learn more about timestamping authorities (nodes) and the role they play in the data model.
 */
@ThreadSafe
class TimestamperNodeService(private val net: MessagingService,
                             val identity: Party,
                             val signingKey: KeyPair,
                             val clock: Clock = Clock.systemDefaultZone(),
                             val tolerance: Duration = 30.seconds) {
    companion object {
        val TIMESTAMPING_PROTOCOL_TOPIC = "platform.timestamping.request"

        private val logger = LoggerFactory.getLogger(TimestamperNodeService::class.java)
    }

    init {
        require(identity.owningKey == signingKey.public)
        net.addMessageHandler(TIMESTAMPING_PROTOCOL_TOPIC + ".0", null) { message, r ->
            try {
                val req = message.data.deserialize<TimestampingMessages.Request>()
                val signature = processRequest(req)
                val msg = net.createMessage(req.replyToTopic, signature.serialize().bits)
                net.send(msg, req.replyTo)
            } catch(e: TimestampingError) {
                logger.warn("Failure during timestamping request due to bad request: ${e.javaClass.name}")
            } catch(e: Exception) {
                logger.error("Exception during timestamping", e)
            }
        }
    }

    @VisibleForTesting
    fun processRequest(req: TimestampingMessages.Request): DigitalSignature.LegallyIdentifiable {
        // We don't bother verifying signatures anything about the transaction here: we simply don't need to see anything
        // except the relevant command, and a future privacy upgrade should ensure we only get a torn-off command
        // rather than the full transaction.
        val tx = req.tx.deserialize()
        val cmd = tx.commands.filter { it.data is TimestampCommand }.singleOrNull()
        if (cmd == null)
            throw TimestampingError.RequiresExactlyOneCommand()
        if (!cmd.pubkeys.contains(identity.owningKey))
            throw TimestampingError.NotForMe()
        val tsCommand = cmd.data as TimestampCommand

        val before = tsCommand.before
        val after = tsCommand.after

        val now = clock.instant()

        // We don't need to test for (before == null && after == null) or backwards bounds because the TimestampCommand
        // constructor already checks that.

        if (before != null && before until now > tolerance)
            throw TimestampingError.NotOnTimeException()
        if (after != null && now until after > tolerance)
            throw TimestampingError.NotOnTimeException()

        return signingKey.signWithECDSA(req.tx.bits, identity)
    }
}

/**
 * The TimestampingProtocol class is the client code that talks to a [TimestamperNodeService] on some remote node. It is a
 * [ProtocolLogic], meaning it can either be a sub-protocol of some other protocol, or be driven independently.
 *
 * If you are not yourself authoring a protocol and want to timestamp something, the [TimestampingProtocol.Client] class
 * implements the [TimestamperService] interface, meaning it can be passed to [TransactionBuilder.timestamp] to timestamp
 * the built transaction. Please be aware that this will block, meaning it should not be used on a thread that is handling
 * a network message: use it only from spare application threads that don't have to respond to anything.
 */
class TimestampingProtocol(private val node: LegallyIdentifiableNode,
                           private val wtxBytes: SerializedBytes<WireTransaction>) : ProtocolLogic<DigitalSignature.LegallyIdentifiable>() {

    class Client(private val stateMachineManager: StateMachineManager, private val node: LegallyIdentifiableNode) : TimestamperService {
        override val identity: Party = node.identity

        override fun timestamp(wtxBytes: SerializedBytes<WireTransaction>): DigitalSignature.LegallyIdentifiable {
            return stateMachineManager.add("platform.timestamping", TimestampingProtocol(node, wtxBytes)).get()
        }
    }


    @Suspendable
    override fun call(): DigitalSignature.LegallyIdentifiable {
        val sessionID = random63BitValue()
        val replyTopic = "${TimestamperNodeService.TIMESTAMPING_PROTOCOL_TOPIC}.$sessionID"
        val req = TimestampingMessages.Request(wtxBytes, serviceHub.networkService.myAddress, replyTopic)

        val maybeSignature = sendAndReceive<DigitalSignature.LegallyIdentifiable>(
                TimestamperNodeService.TIMESTAMPING_PROTOCOL_TOPIC, node.address, 0, sessionID, req)

        // Check that the timestamping authority gave us back a valid signature and didn't break somehow
        maybeSignature.validate { sig ->
            sig.verifyWithECDSA(wtxBytes)
            return sig
        }
    }
}

