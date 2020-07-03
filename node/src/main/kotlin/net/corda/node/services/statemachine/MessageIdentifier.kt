package net.corda.node.services.statemachine

import net.corda.core.crypto.SecureHash
import java.time.Instant

/**
 * @property sessionIdentifier the session identifier of the sink (the side where this message is destined to).
 */
data class MessageIdentifier(
    val prefix: String,
    val shardIdentifier: String,
    val sessionIdentifier: Long,
    val sessionSequenceNumber: Int,
    val timestamp: Instant
) {
    init {
        require(shardIdentifier.length == 8)
    }

    companion object {
        fun parse(id: String): MessageIdentifier {
            val prefix = id.substring(0, 2)
            val timestamp = java.lang.Long.parseUnsignedLong(id.substring(3, 19), 16)
            val shardIdentifier = id.substring(20, 28)
            val sessionIdentifier = java.lang.Long.parseUnsignedLong(id.substring(29, 45), 16)
            val sessionSequenceNumber = Integer.parseInt(id.substring(46), 16)
            return MessageIdentifier(prefix, shardIdentifier, sessionIdentifier, sessionSequenceNumber, Instant.ofEpochMilli(timestamp))
        }
    }

    override fun toString(): String {
        val sizeInCharacters = 16
        val encodedSessionIdentifier = String.format("%1$0${sizeInCharacters}X", sessionIdentifier)
        val encodedSequenceNumber = Integer.toHexString(sessionSequenceNumber).toUpperCase()
        val encodedTimestamp = String.format("%1$0${sizeInCharacters}X", timestamp.toEpochMilli());
        return "$prefix-$encodedTimestamp-$shardIdentifier-$encodedSessionIdentifier-$encodedSequenceNumber"
    }

    fun toDeduplicationId(): DeduplicationId {
        return DeduplicationId(toString())
    }
}

fun generateShard(flowIdentifier: String): String {
    return SecureHash.sha256(flowIdentifier).prefixChars(8)

}
