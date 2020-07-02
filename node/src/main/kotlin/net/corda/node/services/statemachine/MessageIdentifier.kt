package net.corda.node.services.statemachine

import net.corda.core.crypto.SecureHash

/**
 * @property sessionIdentifier the session identifier of the sink (the side where this message is destined to).
 */
data class MessageIdentifier(
    val prefix: String,
    val shardIdentifier: String,
    val sessionIdentifier: Long,
    val sessionSequenceNumber: Int
) {
    init {
        require(shardIdentifier.length == 8)
    }

    companion object {
        fun parse(id: String): MessageIdentifier {
            val prefix = id.substring(0, 2)
            val shardIdentifier = id.substring(3, 11)
            val sessionIdentifier = java.lang.Long.parseUnsignedLong(id.substring(12, 28), 16)
            val sessionSequenceNumber = java.lang.Integer.parseInt(id.substring(29), 16)
            return MessageIdentifier(prefix, shardIdentifier, sessionIdentifier, sessionSequenceNumber)
        }
    }

    override fun toString(): String {
        val sizeInCharacters = 16
        val encodedSessionIdentifier = String.format("%1$0${sizeInCharacters}X", sessionIdentifier);
        val encodedSequenceNumber = java.lang.Integer.toHexString(sessionSequenceNumber).toUpperCase()
        return "$prefix-$shardIdentifier-$encodedSessionIdentifier-$encodedSequenceNumber"
    }

    fun toDeduplicationId(): DeduplicationId {
        return DeduplicationId(toString())
    }
}

fun generateShard(flowIdentifier: String): String {
    return SecureHash.sha256(flowIdentifier).prefixChars(8)

}
