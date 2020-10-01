package net.corda.node.services.messaging

import net.corda.node.services.statemachine.MessageType
import net.corda.node.services.statemachine.SessionId
import net.corda.node.services.statemachine.sharding.ShardId
import java.time.Instant

/**
 * This represents the unique identifier for every message.
 * It's composed of multiple segments.
 *
 * @property messageType the type of the message.
 * @property shardIdentifier the shard identifier of the message.
 * @property sessionIdentifier the identifier of the session this message belongs to. This corresponds to the identifier of the session on the receiving side.
 * @property sessionSequenceNumber the sequence number of the message inside the session. This can be used to handle out-of-order delivery.
 * @property timestamp the time when the message was requested to be sent.
 *  This is expected to remain the same across replays of the same message and represent the moment in time when this message  was initially scheduled to be sent.
 */
data class MessageIdentifier(
        val messageType: MessageType,
        val shardIdentifier: ShardId,
        val sessionIdentifier: SessionId,
        val sessionSequenceNumber: Int,
        val timestamp: Instant
) {
    init {
        require(shardIdentifier.length == SHARD_SIZE_IN_CHARS) { "Shard identifier needs to be $SHARD_SIZE_IN_CHARS characters long, but it was $shardIdentifier" }
    }

    companion object {
        const val SHARD_SIZE_IN_CHARS = 8
        const val LONG_SIZE_IN_HEX = 16 // 64 / 4
        private const val HEX_RADIX = 16

        fun parse(id: String): MessageIdentifier {
            val prefix = id.substring(0, 2)
            val messageType = MessageType.fromPrefix(prefix)
            val timestamp = java.lang.Long.parseUnsignedLong(id.substring(3, 19), HEX_RADIX)
            val shardIdentifier = id.substring(20, 28)
            val sessionId = SessionId.fromHex(id.substring(29, 61))
            val sessionSequenceNumber = Integer.parseInt(id.substring(62), HEX_RADIX)
            return MessageIdentifier(messageType, shardIdentifier, sessionId, sessionSequenceNumber, Instant.ofEpochMilli(timestamp))
        }
    }

    override fun toString(): String {
        val prefix = messageType.prefix
        val encodedSessionIdentifier = sessionIdentifier.toHex()
        val encodedSequenceNumber = Integer.toHexString(sessionSequenceNumber).toUpperCase()
        val encodedTimestamp = String.format("%1$0${LONG_SIZE_IN_HEX}X", timestamp.toEpochMilli())
        return "$prefix-$encodedTimestamp-$shardIdentifier-$encodedSessionIdentifier-$encodedSequenceNumber"
    }

}

/**
 * A unique identifier for a sender that might be different across restarts.
 * It is used to help identify when messages are being sent continuously without errors or message are sent after the sender recovered from an error.
 */
typealias SenderUUID = String
/**
 * A global sequence number for all the messages sent by a sender.
 */
typealias SenderSequenceNumber = Long