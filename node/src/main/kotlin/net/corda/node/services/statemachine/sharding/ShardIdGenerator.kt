package net.corda.node.services.statemachine.sharding

import net.corda.core.crypto.SecureHash
import net.corda.node.services.messaging.MessageIdentifier

class ShardIdGenerator {
    companion object {
        fun generate(flowIdentifier: String): ShardId {
            return SecureHash.sha256(flowIdentifier).prefixChars(MessageIdentifier.SHARD_SIZE_IN_CHARS)
        }
    }
}

/**
 * This is an identifier that can be used to partition messages into groups for sharding purposes.
 * It is supposed to have the same value for messages that correspond to the same business-level flow.
 *
 */
typealias ShardId = String