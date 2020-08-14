package net.corda.node.services.messaging

/**
 * This is a combination of a message's unique identifier along with a unique identifier for the sender of the message.
 * The former can be used independently for deduplication purposes when receiving a message, but enriching it with the latter helps us
 * optimise some paths and perform smarter deduplication logic per sender.
 *
 * The [senderUUID] property might be null if the flow is trying to replay messages and doesn't want an optimisation to ignore the message identifier
 * because it could lead to false negatives (messages that are deemed duplicates, but are not).
 */
data class SenderDeduplicationInfo(val messageIdentifier: MessageIdentifier, val senderUUID: String?)