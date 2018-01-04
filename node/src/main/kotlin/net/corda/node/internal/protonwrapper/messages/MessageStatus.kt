package net.corda.node.internal.protonwrapper.messages

/**
 * The processing state of a message.
 */
enum class MessageStatus {
    Unsent,
    Sent,
    Acknowledged,
    Rejected
}