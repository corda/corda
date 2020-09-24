package net.corda.node.services.statemachine.sharding

import net.corda.core.crypto.SecureHash
import net.corda.node.services.messaging.MessageIdentifier

class ShardIdGenerator {
    companion object {
        fun generateShardId(flowIdentifier: String): String {
            return SecureHash.sha256(flowIdentifier).prefixChars(MessageIdentifier.SHARD_SIZE_IN_CHARS)
        }
    }
}