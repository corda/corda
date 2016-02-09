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
import core.messaging.LegallyIdentifiableNode
import core.messaging.MessageRecipients
import core.messaging.MessagingService
import core.messaging.ProtocolStateMachine
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

sealed class TimestampingError : Exception() {
    class RequiresExactlyOneCommand : TimestampingError()
    /**
     * Thrown if an attempt is made to timestamp a transaction using a trusted timestamper, but the time on the
     * transaction is too far in the past or future relative to the local clock and thus the timestamper would reject
     * it.
     */
    class NotOnTimeException : TimestampingError()

    /** Thrown if the command in the transaction doesn't list this timestamping authorities public key as a signer */
    class NotForMe : TimestampingError()
}

/**
 * This class implements the server side of the timestamping protocol, using the local clock. A future version might
 * add features like checking against other NTP servers to make sure the clock hasn't drifted by too much.
 *
 * See the doc site to learn more about timestamping authorities (nodes) and the role they play in the data model.
 */
@ThreadSafe
class TimestamperNodeService(private val net: MessagingService,
                             private val identity: Party,
                             private val signingKey: KeyPair,
                             private val clock: Clock = Clock.systemDefaultZone(),
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

@ThreadSafe
class TimestamperClient(private val psm: ProtocolStateMachine<*>, private val node: LegallyIdentifiableNode) : TimestamperService {
    override val identity: Party = node.identity

    @Suspendable
    override fun timestamp(wtxBytes: SerializedBytes<WireTransaction>): DigitalSignature.LegallyIdentifiable {
        val sessionID = random63BitValue()
        val replyTopic = "${TimestamperNodeService.TIMESTAMPING_PROTOCOL_TOPIC}.$sessionID"
        val req = TimestampingMessages.Request(wtxBytes, psm.serviceHub.networkService.myAddress, replyTopic)

        val maybeSignature = psm.sendAndReceive(TimestamperNodeService.TIMESTAMPING_PROTOCOL_TOPIC, node.address, 0,
                sessionID, req, DigitalSignature.LegallyIdentifiable::class.java)

        // Check that the timestamping authority gave us back a valid signature and didn't break somehow
        val signature = maybeSignature.validate { it.verifyWithECDSA(wtxBytes) }

        return signature
    }
}

