package net.corda.nodeapi.exceptions

/**
 * Indicates that an [Exception] contains a message to be shown to RPC clients.
 */
interface WithClientRelevantMessage {

    val messageForClient: String
}