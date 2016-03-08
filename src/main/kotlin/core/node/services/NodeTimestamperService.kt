/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node.services

import co.paralleluniverse.common.util.VisibleForTesting
import core.Party
import core.TimestampCommand
import core.crypto.DigitalSignature
import core.crypto.signWithECDSA
import core.messaging.MessagingService
import core.seconds
import core.serialization.deserialize
import core.serialization.serialize
import core.until
import org.slf4j.LoggerFactory
import protocols.TimestampingProtocol
import java.security.KeyPair
import java.time.Clock
import java.time.Duration
import javax.annotation.concurrent.ThreadSafe

/**
 * This class implements the server side of the timestamping protocol, using the local clock. A future version might
 * add features like checking against other NTP servers to make sure the clock hasn't drifted by too much.
 *
 * See the doc site to learn more about timestamping authorities (nodes) and the role they play in the data model.
 */
@ThreadSafe
class NodeTimestamperService(private val net: MessagingService,
                             val identity: Party,
                             val signingKey: KeyPair,
                             val clock: Clock = Clock.systemDefaultZone(),
                             val tolerance: Duration = 30.seconds) {
    companion object {
        val TIMESTAMPING_PROTOCOL_TOPIC = "platform.timestamping.request"

        private val logger = LoggerFactory.getLogger(NodeTimestamperService::class.java)
    }

    init {
        require(identity.owningKey == signingKey.public)
        net.addMessageHandler(TIMESTAMPING_PROTOCOL_TOPIC + ".0", null) { message, r ->
            try {
                val req = message.data.deserialize<TimestampingProtocol.Request>()
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
    fun processRequest(req: TimestampingProtocol.Request): DigitalSignature.LegallyIdentifiable {
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

